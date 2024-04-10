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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.StatusRuntimeException;

/**
 * @author Jared Wiltshire
 */
public class GrpcWebServlet extends HttpServlet {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final RequestHandler requestHandler;

    public GrpcWebServlet() {
        this(RequestHandler.builder().build());
    }

    public GrpcWebServlet(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        @Nullable CompletableFuture<@Nullable Void> future = null;
        try {
            future = requestHandler.handle(request, response);
            if (request.isAsyncSupported()) {
                AsyncContext context = request.startAsync();
                context.setTimeout(0);
                context.addListener(new FutureCancellingListener(future));
                future.whenComplete((result, error) -> {
                    if (error != null) {
                        logError(error);
                    }
                    context.complete();
                });
            } else {
                future.get();
            }
        } catch (ExecutionException e) {
            logError(e.getCause());
        } catch (Exception e) {
            log.error("Error handling gRPC-Web request", e);
            if (future != null) {
                future.cancel(false);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void logError(Throwable error) {
        if (error instanceof StatusRuntimeException) {
            log.warn("Returned gRPC-Web error response", error);
        } else {
            log.error("Error handling gRPC-Web request", error);
        }
    }

    private record FutureCancellingListener(CompletableFuture<@Nullable Void> future) implements AsyncListener {
        @Override
        public void onComplete(AsyncEvent event) {
            future.cancel(false);
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            future.cancel(false);
        }

        @Override
        public void onError(AsyncEvent event) {
            @Nullable Throwable error = event.getThrowable();
            if (error != null) {
                future.completeExceptionally(error);
            }
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            // no-op
        }
    }
}
