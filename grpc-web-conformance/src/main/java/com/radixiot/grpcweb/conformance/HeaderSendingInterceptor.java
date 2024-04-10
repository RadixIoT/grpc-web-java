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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * @author Jared Wiltshire
 */
public class HeaderSendingInterceptor implements ServerInterceptor {

    public static final Context.Key<Consumer<Metadata>> SET_HEADERS = Context.key(
            HeaderCapturingInterceptor.class.getName() + ".SET_HEADERS");

    @Override
    public <T, R> ServerCall.Listener<T> interceptCall(ServerCall<T, R> call, Metadata headers,
            ServerCallHandler<T, R> next) {

        AtomicReference<Metadata> headersReference = new AtomicReference<>(new Metadata());
        Context context = Context.current().withValue(SET_HEADERS, headersReference::set);

        return Contexts.interceptCall(context, new HeaderSendingServerCall<>(call, headersReference), headers, next);
    }

    private static class HeaderSendingServerCall<T, R> extends SimpleForwardingServerCall<T, R> {

        private final AtomicBoolean headersSent;
        private final AtomicReference<Metadata> appendHeaders;

        public HeaderSendingServerCall(ServerCall<T, R> call, AtomicReference<Metadata> appendHeaders) {
            super(call);
            this.appendHeaders = appendHeaders;
            headersSent = new AtomicBoolean();
        }

        @Override
        public void sendHeaders(Metadata responseHeaders) {
            headersSent.set(true);
            responseHeaders.merge(appendHeaders.get());
            super.sendHeaders(responseHeaders);
        }

        @Override
        public void close(Status status, Metadata trailers) {
            if (!headersSent.get()) {
                sendHeaders(new Metadata());
            }
            super.close(status, trailers);
        }

    }
}
