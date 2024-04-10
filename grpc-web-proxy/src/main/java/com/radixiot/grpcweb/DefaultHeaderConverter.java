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

import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.grpc.Metadata;

public class DefaultHeaderConverter implements HeaderConverter {

    @Override
    public Metadata toMetadata(Stream<Header> headers) {
        Metadata httpHeaders = new Metadata();
        headers.forEach(h -> {
            String headerName = h.name();
            String headerValue = h.value();
            if (headerName.toLowerCase(Locale.ROOT).endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                httpHeaders.put(Metadata.Key.of(headerName, Metadata.BINARY_BYTE_MARSHALLER), Base64.getDecoder().decode(headerValue));
            } else {
                httpHeaders.put(Metadata.Key.of(headerName, Metadata.ASCII_STRING_MARSHALLER), headerValue);
            }
        });
        return httpHeaders;
    }

    @Override
    public Stream<Header> toHeaders(Metadata metadata) {
        var encoder = Base64.getEncoder().withoutPadding();
        return metadata.keys().stream().flatMap(key -> {
            if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                var metaDataKey = Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER);
                var iterable = Objects.requireNonNull(metadata.getAll(metaDataKey));
                return StreamSupport.stream(iterable.spliterator(), false).map(encoder::encodeToString)
                        .map(value -> new Header(key, value));
            } else {
                var metaDataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                var iterable = Objects.requireNonNull(metadata.getAll(metaDataKey));
                return StreamSupport.stream(iterable.spliterator(), false).map(value -> new Header(key, value));
            }
        });
    }

}
