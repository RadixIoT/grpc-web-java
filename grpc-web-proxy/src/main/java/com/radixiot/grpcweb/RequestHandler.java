/*
 * Copyright 2024 Radix IoT, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.radixiot.grpcweb;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

public class RequestHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ChannelManager channelManager;
    private final ServiceLocator serviceLocator;
    private final ContentTypeLookup contentTypeLookup;
    private final HeaderConverter headerConverter;
    private final int maxFrameSize;

    public static RequestHandlerBuilder builder() {
        return new RequestHandlerBuilder();
    }

    RequestHandler(ChannelManager channelManager,
            ServiceLocator serviceLocator,
            ContentTypeLookup contentTypeLookup,
            HeaderConverter headerConverter,
            int maxFrameSize) {
        this.channelManager = channelManager;
        this.serviceLocator = serviceLocator;
        this.contentTypeLookup = contentTypeLookup;
        this.headerConverter = headerConverter;
        this.maxFrameSize = maxFrameSize;
    }

    CompletableFuture<@Nullable Void> handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        GrpcWebContentType contentType = contentTypeLookup.lookup(request.getContentType());
        ResponseWriter writer = new DefaultResponseWriter(contentType, response, headerConverter);
        try {
            String pathInfo = request.getPathInfo();
            // pathInfo starts with "/". ignore that first char.
            String fullMethodName = pathInfo.substring(1);
            MethodDescriptor<?, ?> method = getMethodDescriptor(fullMethodName);
            return handleMethod(request, method, writer);
        } catch (IOException e) {
            throw e;
        } catch (StatusRuntimeException e) {
            writer.writeTrailers(e.getStatus(), e.getTrailers() == null ? new Metadata() : e.getTrailers());
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            writer.writeTrailers(Status.UNKNOWN);
            return CompletableFuture.failedFuture(e);
        }
    }

    private MethodDescriptor<?, ?> getMethodDescriptor(String fullMethodName) {
        MethodDescriptor<?, ?> method;
        try {
            method = serviceLocator.locateMethod(fullMethodName);
        } catch (NoSuchElementException e) {
            log.debug("Unable to locate method: {}", fullMethodName);
            throw new StatusRuntimeException(Status.UNIMPLEMENTED);
        }
        return method;
    }

    private <T, R> CompletableFuture<@Nullable Void> handleMethod(HttpServletRequest request,
            MethodDescriptor<T, R> method, ResponseWriter writer) throws IOException {

        @Nullable String encoding = request.getHeader("grpc-encoding");
        if (encoding != null && !encoding.equals("identity")) {
            throw Status.UNIMPLEMENTED.withDescription("Unsupported encoding")
                    .asRuntimeException();
        }

        GrpcWebContentType contentType = contentTypeLookup.lookup(request.getContentType());
        Channel channel = channelManager.getChannel(method);

        @Nullable String timeout = request.getHeader("grpc-timeout");
        if (timeout != null) {
            channel = addTimeout(timeout, channel);
        }

        CompletableFuture<@Nullable Void> future = new CompletableFuture<>();
        channel = ClientInterceptors.intercept(channel, new GrpcWebClientInterceptor(writer, future));

        Metadata headers = headerConverter.toMetadata(request);
        if (!headers.keys().isEmpty()) {
            channel = ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers));
        }

        // Read the request message from the input stream
        ServletInputStream inputStream = request.getInputStream();
        InputStream decodedInput = contentType.base64Encoded() ? Base64.getDecoder().wrap(inputStream) : inputStream;
        FrameDecoder<T> frameDecoder = new FrameDecoder<>(decodedInput, method.getRequestMarshaller(), maxFrameSize);

        @Nullable T requestMessage = frameDecoder.readDataFrame();
        if (requestMessage == null || frameDecoder.hasFrame()) {
            // zero requests, or multiple requests should return UNIMPLEMENTED
            throw Status.UNIMPLEMENTED.withDescription("Only unary requests are supported")
                    .asRuntimeException();
        }

        // Invoke the rpc call
        call(channel, method, requestMessage);

        return future;
    }

    private Channel addTimeout(String timeoutStr, Channel channel) {
        Duration timeout;
        try {
            timeout = headerConverter.parseTimeout(timeoutStr);
        } catch (IllegalArgumentException e) {
            throw Status.UNIMPLEMENTED.withDescription("Unknown timeout value")
                    .asRuntimeException();
        }
        return ClientInterceptors.intercept(channel, (CallOptionsClientInterceptor) callOptions ->
                callOptions.withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS));
    }

    private <T, R> void call(Channel channel, MethodDescriptor<T, R> methodDescriptor, T request) {
        var callOptions = CallOptions.DEFAULT;
        var responseObserver = new NoopObserver<R>();
        switch (methodDescriptor.getType()) {
            case UNARY -> ClientCalls.asyncUnaryCall(channel.newCall(methodDescriptor, callOptions), request, responseObserver);
            case SERVER_STREAMING -> ClientCalls.asyncServerStreamingCall(channel.newCall(methodDescriptor, callOptions), request, responseObserver);
            default -> throw new IllegalArgumentException();
        }
    }

    private record NoopObserver<T>() implements StreamObserver<T> {
        @Override
        public void onNext(T value) {
            // no-op
        }

        @Override
        public void onError(Throwable t) {
            // no-op
        }

        @Override
        public void onCompleted() {
            // no-op
        }
    }

}
