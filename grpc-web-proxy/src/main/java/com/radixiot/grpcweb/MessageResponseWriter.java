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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.Status;

class MessageResponseWriter<R> implements ResponseWriter {

    private final ResponseWriter delegate;
    private final Marshaller<R> marshaller;

    MessageResponseWriter(ResponseWriter delegate, Marshaller<R> marshaller) {
        this.delegate = delegate;
        this.marshaller = marshaller;
    }

    void writeMessage(R message) throws IOException {
        var outputStream = new ByteArrayOutputStream();
        try (var inputStream = marshaller.stream(message)) {
            inputStream.transferTo(outputStream);
        }
        byte[] messageBytes = outputStream.toByteArray();
        delegate.writeFrame(EnumSet.noneOf(FrameFlag.class), messageBytes);
    }

    @Override
    public void writeHeaders(Metadata headers) throws IOException {
        delegate.writeHeaders(headers);
    }

    @Override
    public void writeTrailers(Status status, Metadata trailer) throws IOException {
        delegate.writeTrailers(status, trailer);

    }

    @Override
    public void writeFrame(Set<FrameFlag> flags, byte[] frameBytes) throws IOException {
        delegate.writeFrame(flags, frameBytes);
    }
}
