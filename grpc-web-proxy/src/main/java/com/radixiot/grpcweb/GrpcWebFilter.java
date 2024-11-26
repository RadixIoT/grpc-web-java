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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GrpcWebFilter implements Filter {

    public static final String DEFAULT_FORWARD_DESTINATION = "/grpc-web";

    private final ContentTypeLookup contentTypeLookup;
    private final String forwardDestination;

    public GrpcWebFilter() {
        this(new DefaultContentTypeLookup());
    }

    public GrpcWebFilter(ContentTypeLookup contentTypeLookup) {
        this(contentTypeLookup, DEFAULT_FORWARD_DESTINATION);
    }

    public GrpcWebFilter(ContentTypeLookup contentTypeLookup, String forwardDestination) {
        if (!forwardDestination.startsWith("/")) {
            throw new IllegalArgumentException("Forward path must start with '/'");
        }
        if (forwardDestination.endsWith("/")) {
            throw new IllegalArgumentException("Forward path must not end with '/'");
        }
        this.contentTypeLookup = contentTypeLookup;
        this.forwardDestination = forwardDestination;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        if (!uri.startsWith(forwardDestination + "/") && contentTypeLookup.isGrpcWebRequest(httpRequest)) {
            httpRequest.getRequestDispatcher(forwardDestination + uri).forward(httpRequest, httpResponse);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }
}
