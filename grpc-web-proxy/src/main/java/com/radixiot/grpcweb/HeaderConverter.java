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

import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

import org.checkerframework.checker.nullness.qual.Nullable;

import io.grpc.Metadata;

public interface HeaderConverter {

    /**
     * Regex pattern used to extract the timeout from the grpc-timeout header. The gRPC specification says it must be
     * max 8 digits.
     */
    Pattern TIMEOUT_PATTERN = Pattern.compile("(\\d{1,8})([HMSmun])");

    /**
     * Set of header names which should be stripped when propagating requests from HTTP/1.1 gRPC-Web protocol to
     * a HTTP/2 gRPC server.
     */
    Set<String> STRIP_HTTP_HEADERS = Set.of(
            // sent by some gRPC-Web clients
            "x-grpc-web",
            // this header is prohibited in HTTP/2
            "connection",
            // the frame encoding/compression of gateway <-> server should be independent of client <-> gateway
            "grpc-accept-encoding",
            "grpc-encoding",
            // this will be set to a gRPC-Web specific type like application/grpc-web
            "content-type",
            "accept",
            // the gateway reads this header and sets a deadline on the outgoing client call options
            "grpc-timeout",
            // size of received message will not correspond to size of outgoing message
            "content-length"
    );

    private <T> Stream<T> toStream(@Nullable Enumeration<T> enumeration) {
        return enumeration == null ? Stream.of() : Collections.list(enumeration).stream();
    }

    default Set<String> stripHttpHeaders() {
        return STRIP_HTTP_HEADERS;
    }

    default Metadata toMetadata(HttpServletRequest req) {
        var stripHttpHeaders = stripHttpHeaders();
        var stream = toStream(req.getHeaderNames())
                .filter(name -> !stripHttpHeaders.contains(name.toLowerCase(Locale.ROOT)))
                .flatMap(name -> toStream(req.getHeaders(name))
                        .map(value -> new Header(name, value)));
        return toMetadata(stream);
    }

    Metadata toMetadata(Stream<Header> headers);

    Stream<Header> toHeaders(Metadata metadata);

    default Duration parseTimeout(String timeout) {
        var matcher = TIMEOUT_PATTERN.matcher(timeout);
        if (!matcher.matches()) {
            throw new IllegalArgumentException();
        }

        int timeoutValue = Integer.parseInt(matcher.group(1));
        TimeUnit timeoutUnit = switch (matcher.group(2)) {
            case "H" -> TimeUnit.HOURS;
            case "M" -> TimeUnit.MINUTES;
            case "S" -> TimeUnit.SECONDS;
            case "m" -> TimeUnit.MINUTES;
            case "u" -> TimeUnit.MICROSECONDS;
            case "n" -> TimeUnit.NANOSECONDS;
            default -> throw new IllegalArgumentException();
        };

        return Duration.of(timeoutValue, timeoutUnit.toChronoUnit());
    }

}
