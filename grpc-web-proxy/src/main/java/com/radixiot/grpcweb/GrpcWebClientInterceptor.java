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

import java.util.concurrent.CompletableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

class GrpcWebClientInterceptor implements ClientInterceptor {

    private final ResponseWriter writer;
    private final CompletableFuture<@Nullable Void> future;

    GrpcWebClientInterceptor(ResponseWriter writer, CompletableFuture<@Nullable Void> future) {
        this.writer = writer;
        this.future = future;
    }

    @Override
    public <T, R> ClientCall<T, R> interceptCall(MethodDescriptor<T, R> method,
            CallOptions callOptions, Channel channel) {

        var marshaller = method.getResponseMarshaller();
        var messageWriter = new MessageResponseWriter<>(writer, marshaller);
        return new GrpcWebClientCall<>(channel.newCall(method, callOptions), messageWriter, future);
    }

    private static class GrpcWebClientCall<T, R> extends SimpleForwardingClientCall<T, R> {

        private final MessageResponseWriter<R> messageWriter;
        private final CompletableFuture<@Nullable Void> future;

        private GrpcWebClientCall(ClientCall<T, R> delegate, MessageResponseWriter<R> messageWriter,
                CompletableFuture<@Nullable Void> future) {
            super(delegate);
            this.messageWriter = messageWriter;
            this.future = future;
        }

        @Override
        public void start(Listener<R> responseListener, Metadata headers) {
            // listen for cancellation of the incoming HTTP server call, e.g. client closed the connection, a timeout occurred
            // if this occurs, we should also cancel the outgoing client request
            future.whenComplete((result, error) -> cancel("Cancelled", error));
            super.start(new GrpcWebClientCallListener(responseListener), headers);
        }

        private class GrpcWebClientCallListener extends SimpleForwardingClientCallListener<R> {

            private final Logger log = LoggerFactory.getLogger(getClass());

            private GrpcWebClientCallListener(Listener<R> responseListener) {
                super(responseListener);
            }

            @Override
            public void onHeaders(Metadata headers) {
                try {
                    messageWriter.writeHeaders(headers);
                } catch (Exception e) {
                    log.debug("Error writing headers to HTTP", e);
                    cancel("Error writing headers to HTTP", e);
                } finally {
                    super.onHeaders(headers);
                }
            }

            @Override
            public void onMessage(R message) {
                try {
                    messageWriter.writeMessage(message);
                } catch (Exception e) {
                    log.debug("Error writing message to HTTP", e);
                    cancel("Error writing message to HTTP", e);
                } finally {
                    super.onMessage(message);
                }
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
                try {
                    messageWriter.writeTrailers(status, trailers);
                    if (status.isOk()) {
                        future.complete(null);
                    } else {
                        future.completeExceptionally(status.asRuntimeException(trailers));
                    }
                } catch (Exception e) {
                    log.debug("Error writing trailers to HTTP", e);
                    future.completeExceptionally(e);
                } finally {
                    super.onClose(status, trailers);
                }
            }
        }
    }

}
