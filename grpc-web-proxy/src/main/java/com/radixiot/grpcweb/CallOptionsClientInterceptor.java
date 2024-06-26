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

import java.util.function.UnaryOperator;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

/**
 * @author Jared Wiltshire
 */
@FunctionalInterface
public interface CallOptionsClientInterceptor extends ClientInterceptor, UnaryOperator<CallOptions> {

    @Override
    default <T, R> ClientCall<T, R> interceptCall(MethodDescriptor<T, R> method, CallOptions callOptions,
            Channel next) {

        return next.newCall(method, apply(callOptions));
    }

}
