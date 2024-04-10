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
import java.util.Set;

import io.grpc.Metadata;
import io.grpc.Status;

/**
 * @author Jared Wiltshire
 */
interface ResponseWriter {
    default void writeHeaders() throws IOException {
        writeHeaders(new Metadata());
    }

    void writeHeaders(Metadata headers) throws IOException;

    default void writeTrailers(Status status) throws IOException {
        writeTrailers(status, new Metadata());
    }

    void writeTrailers(Status status, Metadata trailer) throws IOException;

    void writeFrame(Set<FrameFlag> flags, byte[] frameBytes) throws IOException;
}
