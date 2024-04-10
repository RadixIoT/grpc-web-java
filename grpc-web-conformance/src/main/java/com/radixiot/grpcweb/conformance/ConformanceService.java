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

package com.radixiot.grpcweb.conformance;

import java.util.List;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.radixiot.grpcweb.DefaultHeaderConverter;
import com.radixiot.grpcweb.HeaderConverter;

import build.buf.gen.connectrpc.conformance.v1.ConformancePayload;
import build.buf.gen.connectrpc.conformance.v1.ConformancePayload.RequestInfo;
import build.buf.gen.connectrpc.conformance.v1.ConformanceServiceGrpc;
import build.buf.gen.connectrpc.conformance.v1.Error;
import build.buf.gen.connectrpc.conformance.v1.Header;
import build.buf.gen.connectrpc.conformance.v1.ServerStreamRequest;
import build.buf.gen.connectrpc.conformance.v1.ServerStreamResponse;
import build.buf.gen.connectrpc.conformance.v1.UnaryRequest;
import build.buf.gen.connectrpc.conformance.v1.UnaryResponse;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;

/**
 * @author Jared Wiltshire
 */
public class ConformanceService extends ConformanceServiceGrpc.ConformanceServiceImplBase {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final HeaderConverter headerConverter = new DefaultHeaderConverter();

    @Override
    public void unary(UnaryRequest request, StreamObserver<UnaryResponse> responseObserver) {
        var requestInfo = getRequestInfo(request);

        if (request.hasResponseDefinition()) {
            var responseDefinition = request.getResponseDefinition();
            var responseHeaders = toMetadata(responseDefinition.getResponseHeadersList());
            var responseTrailers = toMetadata(responseDefinition.getResponseTrailersList());

            var setHeaders = HeaderSendingInterceptor.SET_HEADERS.get();
            setHeaders.accept(responseHeaders);

            sleepForDelay(responseDefinition.getResponseDelayMs());

            if (responseDefinition.hasError()) {
                var errorResponse = getErrorResponse(requestInfo, responseDefinition.getError());
                responseObserver.onError(StatusProto.toStatusRuntimeException(errorResponse, responseTrailers));
            } else {
                var response = getUnaryResponse(requestInfo, responseDefinition.getResponseData());
                responseObserver.onNext(response);
                responseObserver.onError(new StatusRuntimeException(Status.OK, responseTrailers));
            }
        } else {
            var response = getUnaryResponse(requestInfo, null);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void serverStream(ServerStreamRequest request, StreamObserver<ServerStreamResponse> responseObserver) {
        var requestInfo = getRequestInfo(request);

        if (request.hasResponseDefinition()) {
            var responseDefinition = request.getResponseDefinition();
            var responseHeaders = toMetadata(responseDefinition.getResponseHeadersList());
            var responseTrailers = toMetadata(responseDefinition.getResponseTrailersList());

            var setHeaders = HeaderSendingInterceptor.SET_HEADERS.get();
            setHeaders.accept(responseHeaders);

            boolean requestInfoSent = false;
            for (var responseData : responseDefinition.getResponseDataList()) {
                ServerStreamResponse response;
                if (!requestInfoSent) {
                    requestInfoSent = true;
                    response = getServerStreamResponse(requestInfo, responseData);
                } else {
                    response = getServerStreamResponse(null, responseData);
                }

                sleepForDelay(responseDefinition.getResponseDelayMs());
                responseObserver.onNext(response);
            }

            if (responseDefinition.hasError()) {
                var errorResponse = getErrorResponse(requestInfoSent ? null : requestInfo, responseDefinition.getError());
                responseObserver.onError(StatusProto.toStatusRuntimeException(errorResponse, responseTrailers));
            } else {
                responseObserver.onError(new StatusRuntimeException(Status.OK, responseTrailers));
            }
        } else {
            responseObserver.onCompleted();
        }
    }

    private void sleepForDelay(int responseDelayMs) {
        if (responseDelayMs > 0) {
            try {
                Thread.sleep(responseDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw Status.UNAVAILABLE.asRuntimeException();
            }
        }
    }

    private com.google.rpc.Status getErrorResponse(@Nullable RequestInfo requestInfo, Error error) {
        var builder = com.google.rpc.Status.newBuilder()
                .setCode(error.getCode().getNumber())
                .setMessage(error.getMessage());
        if (requestInfo != null) {
            builder.addDetails(Any.pack(requestInfo));
        }
        return builder.build();
    }

    private ConformancePayload getPayload(@Nullable RequestInfo requestInfo, @Nullable ByteString responseData) {
        var payload = ConformancePayload.newBuilder();
        if (requestInfo != null) {
            payload.setRequestInfo(requestInfo);
        }
        if (responseData != null) {
            payload.setData(responseData);
        }
        return payload.build();
    }

    private UnaryResponse getUnaryResponse(RequestInfo requestInfo, @Nullable ByteString responseData) {
        var payload = getPayload(requestInfo, responseData);
        return UnaryResponse.newBuilder()
                .setPayload(payload)
                .build();
    }

    private ServerStreamResponse getServerStreamResponse(@Nullable RequestInfo requestInfo, @Nullable ByteString responseData) {
        var payload = getPayload(requestInfo, responseData);
        return ServerStreamResponse.newBuilder()
                .setPayload(payload)
                .build();
    }

    private RequestInfo getRequestInfo(Message request) {
        var requestHeadersMeta = HeaderCapturingInterceptor.HEADERS.get();
        log.debug("Received request, headers: {}\n{}", requestHeadersMeta, request);

        var requestInfoBuilder = RequestInfo.newBuilder()
                .addAllRequestHeaders(toHeadersList(requestHeadersMeta))
                .addAllRequests(List.of(Any.pack(request)));

        var timeoutKey = Metadata.Key.of("grpc-timeout", Metadata.ASCII_STRING_MARSHALLER);
        @Nullable String timeoutValue = requestHeadersMeta.get(timeoutKey);
        if (timeoutValue != null) {
            requestInfoBuilder.setTimeoutMs(headerConverter.parseTimeout(timeoutValue).toMillis());
        }

        return requestInfoBuilder.build();
    }

    private List<Header> toHeadersList(Metadata metadata) {
        var map = headerConverter.toHeaders(metadata)
                .collect(Collectors.groupingBy(com.radixiot.grpcweb.Header::name));
        return map.entrySet().stream().map(e -> {
            var b = Header.newBuilder().setName(e.getKey());
            for (var header : e.getValue()) {
                b.addValue(header.value());
            }
            return b.build();
        }).toList();
    }

    private Metadata toMetadata(List<Header> headers) {
        var headerStream = headers.stream()
                .flatMap(header -> header.getValueList().stream()
                        .map(v -> new com.radixiot.grpcweb.Header(header.getName(), v)));
        return headerConverter.toMetadata(headerStream);
    }

}
