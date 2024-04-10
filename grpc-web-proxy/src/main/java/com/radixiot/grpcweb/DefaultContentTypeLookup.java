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

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * @author Jared Wiltshire
 */
public class DefaultContentTypeLookup implements ContentTypeLookup {

    private static final Map<String, GrpcWebContentType> GRPC_WEB_CONTENT_TYPES = Stream.of(
            new GrpcWebContentType("application/grpc-web", false),
            new GrpcWebContentType("application/grpc-web+proto", false),
            new GrpcWebContentType("application/grpc-web-text", true),
            new GrpcWebContentType("application/grpc-web-text+proto", true)
    ).collect(Collectors.toMap(GrpcWebContentType::contentType, Function.identity()));

    @Override
    public GrpcWebContentType lookup(@Nullable String contentTypeStr) {
        @Nullable GrpcWebContentType contentType = GRPC_WEB_CONTENT_TYPES.get(contentTypeStr);
        if (contentType == null) {
            throw new NoSuchElementException("Not a valid gRPC-Web content type: " + contentTypeStr);
        }
        return contentType;
    }
}
