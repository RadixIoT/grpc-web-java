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

import java.util.NoSuchElementException;

import javax.servlet.http.HttpServletRequest;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * @author Jared Wiltshire
 */
public interface ContentTypeLookup {

    /**
     * @param contentType the content type from an HTTP header (Content-Type)
     * @return a {@link GrpcWebContentType} indicating if the content type requires base64 encoding/decoding
     * @throws NoSuchElementException if the content type is not a gRPC-Web content type
     */
    GrpcWebContentType lookup(@Nullable String contentType);

    /**
     * @param request a HTTP request
     * @return true if the request is a gRPC-Web request
     */
    default boolean isGrpcWebRequest(HttpServletRequest request) {
        @Nullable String contentType = request.getContentType();
        try {
            lookup(contentType);
        } catch (NoSuchElementException e) {
            return false;
        }
        return true;
    }

}
