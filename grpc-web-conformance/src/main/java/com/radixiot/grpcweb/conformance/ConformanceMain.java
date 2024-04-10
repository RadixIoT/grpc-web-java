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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import org.checkerframework.checker.nullness.qual.Nullable;

import build.buf.gen.connectrpc.conformance.v1.HTTPVersion;
import build.buf.gen.connectrpc.conformance.v1.Protocol;
import build.buf.gen.connectrpc.conformance.v1.ServerCompatRequest;
import build.buf.gen.connectrpc.conformance.v1.ServerCompatResponse;

/**
 * @author Jared Wiltshire
 */
public class ConformanceMain {

    public static void main(String[] args) throws Exception {
        @Nullable String host = null;
        int port = 0;
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        var conformance = new ConformanceMain(host, port);
        conformance.init();

        while (!Thread.currentThread().isInterrupted()) {
            LockSupport.park();
        }
    }

    private final @Nullable String host;
    private final int port;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    ConformanceMain(@Nullable String host, int port) {
        this(host, port, System.in, System.out);
    }

    ConformanceMain(@Nullable String host, int port, InputStream inputStream, OutputStream outputStream) {
        this.host = host;
        this.port = port;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    void init() throws Exception {
        var request = readRequest();
        if (request.getUseTls() || request.getHttpVersion() != HTTPVersion.HTTP_VERSION_1 || request.getProtocol() != Protocol.PROTOCOL_GRPC_WEB) {
            throw new IllegalStateException("Unsupported option");
        }

        ServerCompatResponse response;
        if (host == null) {
            var server = new ConformanceServer(request.getMessageReceiveLimit());
            int jettyPort = server.init(0);
            response = ServerCompatResponse.newBuilder()
                    .setHost("localhost")
                    .setPort(jettyPort)
                    .build();
        } else {
            response = ServerCompatResponse.newBuilder()
                    .setHost(host)
                    .setPort(port)
                    .build();
        }
        writeResponse(response);
    }

    ServerCompatRequest readRequest() throws IOException {
        var sizeBuffer = ByteBuffer.allocate(4);
        int bytesRead = inputStream.read(sizeBuffer.array());
        if (bytesRead < 4) {
            throw new IllegalStateException(String.format("Only %d bytes read, expected 4", bytesRead));
        }
        long size = Integer.toUnsignedLong(sizeBuffer.getInt());
        int integerSize = Math.toIntExact(size);
        byte[] messageBytes = inputStream.readNBytes(integerSize);
        if (messageBytes.length < integerSize) {
            throw new IllegalStateException(String.format("Only %d bytes read, expected %d", messageBytes.length, integerSize));
        }
        return ServerCompatRequest.parseFrom(messageBytes);
    }

    void writeResponse(ServerCompatResponse response) throws IOException {
        byte[] messageBytes = response.toByteArray();
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).putInt(messageBytes.length);
        outputStream.write(sizeBuffer.array());
        outputStream.write(messageBytes);
        outputStream.flush();
    }

}
