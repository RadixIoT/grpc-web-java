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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.checkerframework.checker.nullness.qual.Nullable;

import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.Status;

/**
 * Reads frames from the input bytes and returns a single message.
 */
class FrameDecoder<T> {
    private final Marshaller<T> marshaller;
    private final InputStream inputStream;
    private final int maxFrameSize;

    FrameDecoder(InputStream inputStream, Marshaller<T> marshaller, int maxFrameSize) {
        this.inputStream = inputStream;
        this.marshaller = marshaller;
        this.maxFrameSize = maxFrameSize;
    }

    @Nullable T readDataFrame() throws IOException {
        int flagsByte = inputStream.read();
        if (flagsByte == -1) return null;

        var flags = FrameFlag.decode(flagsByte);
        if (flags.contains(FrameFlag.TRAILERS)) {
            throw Status.UNIMPLEMENTED
                    .withDescription("No message received")
                    .asRuntimeException();
        }
        if (flags.contains(FrameFlag.COMPRESSED)) {
            throw Status.INTERNAL
                    .withDescription("Compressed frames are not supported")
                    .asRuntimeException();
        }

        ByteBuffer lengthBytes = ByteBuffer.allocate(4);
        if (inputStream.read(lengthBytes.array()) < 4) {
            throw new IllegalStateException("Couldn't read length bytes");
        }

        int frameSize = getFrameSize(lengthBytes);
        byte[] messageBytes = inputStream.readNBytes(frameSize);
        if (messageBytes.length != frameSize) {
            throw new IllegalStateException("Couldn't read message bytes");
        }
        return marshaller.parse(new ByteArrayInputStream(messageBytes));
    }

    int getFrameSize(ByteBuffer sizeBytes) {
        long size = Integer.toUnsignedLong(sizeBytes.getInt());
        if (size > maxFrameSize) {
            throw Status.RESOURCE_EXHAUSTED.withDescription("Frame size of exceeds limit")
                    .asRuntimeException();
        }

        int sizeInt;
        try {
            sizeInt = Math.toIntExact(size);
        } catch (ArithmeticException e) {
            throw Status.RESOURCE_EXHAUSTED.withDescription("Frame size of exceeds limit")
                    .asRuntimeException();
        }
        return sizeInt;
    }

    boolean hasFrame() throws IOException {
        int firstByte = inputStream.read();
        return firstByte != -1;
    }
}
