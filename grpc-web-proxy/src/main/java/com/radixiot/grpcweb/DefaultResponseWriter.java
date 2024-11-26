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
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Metadata;
import io.grpc.Status;

class DefaultResponseWriter implements ResponseWriter {
    private static final String CRLF = "\r\n";
    private static final String TRAILERS_HAVE_BEEN_WRITTEN = "Trailers have been written";
    private static final String HEADERS_HAVE_BEEN_WRITTEN = "Headers have been written";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final GrpcWebContentType contentType;
    private final HttpServletResponse response;
    private final HeaderConverter headerConverter;
    private boolean headersWritten = false;
    private boolean trailersWritten = false;

    DefaultResponseWriter(GrpcWebContentType contentType, HttpServletResponse response, HeaderConverter headerConverter) {
        this.contentType = contentType;
        this.response = response;
        this.headerConverter = headerConverter;
    }

    @Override
    public synchronized void writeHeaders(Metadata headers) throws IOException {
        if (trailersWritten) throw new IllegalStateException(TRAILERS_HAVE_BEEN_WRITTEN);
        if (headersWritten) throw new IllegalStateException(HEADERS_HAVE_BEEN_WRITTEN);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(contentType.contentType());
        response.setHeader("transfer-encoding", "chunked");

        Stream<Header> httpHeaders = headerConverter.toHeaders(headers);
        httpHeaders.forEach(header -> response.addHeader(header.name(), header.value()));

        response.flushBuffer();
        headersWritten = true;
    }

    @Override
    public synchronized void writeTrailers(Status status, Metadata trailer) throws IOException {
        if (trailersWritten) throw new IllegalStateException(TRAILERS_HAVE_BEEN_WRITTEN);
        if (!headersWritten) writeHeaders();

        StringBuilder sb = new StringBuilder();
        Stream<Header> trailers = headerConverter.toHeaders(trailer);
        trailers.forEach(header -> appendTrailer(sb, header.name(), header.value()));
        appendTrailer(sb, "grpc-status", String.valueOf(status.getCode().value()));
        if (status.getDescription() != null && !status.getDescription().isEmpty()) {
            appendTrailer(sb, "grpc-message", percentEncode(status.getDescription()));
        }

        log.debug("writing trailer: {}", sb);
        byte[] trailerBytes = sb.toString().getBytes(StandardCharsets.US_ASCII);
        writeFrame(EnumSet.of(FrameFlag.TRAILERS), trailerBytes);

        response.getOutputStream().close();
        this.trailersWritten = true;
    }

    private String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public synchronized void writeFrame(Set<FrameFlag> flags, byte[] frameBytes) throws IOException {
        OutputStream outputStream = response.getOutputStream();
        if (contentType.base64Encoded()) {
            outputStream = Base64.getEncoder().wrap(outputStream);
        }
        outputStream.write(FrameFlag.encode(flags));
        ByteBuffer lengthBytes = ByteBuffer.allocate(4).putInt(frameBytes.length);
        outputStream.write(lengthBytes.array());
        outputStream.write(frameBytes);
        outputStream.flush();
    }

    private void appendTrailer(StringBuilder sb, String key, String value) {
        sb.append(key.toLowerCase(Locale.ROOT))
                .append(": ")
                .append(value)
                .append(CRLF);
    }

}
