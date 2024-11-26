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

import static com.radixiot.grpcweb.GrpcWebFilter.DEFAULT_FORWARD_DESTINATION;
import static kotlin.jvm.JvmClassMappingKt.getKotlinClass;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import jakarta.servlet.DispatcherType;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.connectrpc.Idempotency;
import com.connectrpc.MethodSpec;
import com.connectrpc.ProtocolClientConfig;
import com.connectrpc.ResponseMessage;
import com.connectrpc.StreamType;
import com.connectrpc.extensions.GoogleJavaProtobufStrategy;
import com.connectrpc.http.HTTPClientInterface;
import com.connectrpc.impl.ProtocolClient;
import com.connectrpc.okhttp.ConnectOkHttpClient;
import com.connectrpc.protocols.NetworkProtocol;

import io.grpc.MethodDescriptor;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import okhttp3.OkHttpClient;

/**
  * @author Jared Wiltshire
  */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrpcWebServletTest {

    private Server jettyServer;
    private ProtocolClient client;
    private io.grpc.@Nullable Server grpcServer;

    @BeforeAll
    void beforeAll() throws IOException {
        HealthStatusManager healthManager = new HealthStatusManager();
        this.grpcServer = InProcessServerBuilder.forName(DefaultChannelManager.DEFAULT_CHANNEL_NAME)
                .addService(healthManager.getHealthService())
                .build()
                .start();
    }

    @AfterAll
    void afterAll() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdown();
            grpcServer.awaitTermination();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addFilterWithMapping(GrpcWebFilter.class, "/*",
                EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC));

        var requestHandler = RequestHandler.builder()
                .setServiceLocator((ClassServiceLocator) serviceName -> "io." + serviceName + "Grpc")
                .build();
        var servlet = new GrpcWebServlet(requestHandler);
        servletHandler.addServletWithMapping(new ServletHolder(servlet), DEFAULT_FORWARD_DESTINATION + "/*");

        ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.setServletHandler(servletHandler);

        this.jettyServer = new Server(0);
        jettyServer.setHandler(servletContextHandler);
        jettyServer.start();

        int jettyPort = ((ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();
        HTTPClientInterface httpClient = new ConnectOkHttpClient(new OkHttpClient());
        ProtocolClientConfig config = new ProtocolClientConfig(
                "http://localhost:" + jettyPort, new GoogleJavaProtobufStrategy(), NetworkProtocol.GRPC_WEB);
        this.client = new ProtocolClient(httpClient, config);
    }

    @AfterEach
    void tearDown() throws Exception {
        jettyServer.stop();
    }

    @Test
    void unary() throws ExecutionException, InterruptedException {
        var future = new CompletableFuture<HealthCheckResponse>();
        MethodSpec<HealthCheckRequest, HealthCheckResponse> healthCheck = getMethodSpec(HealthGrpc.getCheckMethod());
        client.unary(HealthCheckRequest.getDefaultInstance(), Map.of(), healthCheck, new FutureAdapter<>(future));

        assertThat(future)
                .succeedsWithin(Duration.ofSeconds(5))
                .isNotNull();

        var responseMessage = future.get();
        assertThat(responseMessage).isNotNull();
    }

    private <T, R> MethodSpec<T, R> getMethodSpec(MethodDescriptor<T, R> method) {
        return new MethodSpec<>(
                method.getFullMethodName(),
                getKotlinClass(getMessageClass(method.getRequestMarshaller())),
                getKotlinClass(getMessageClass(method.getResponseMarshaller())),
                switch (method.getType()) {
                    case UNARY -> StreamType.UNARY;
                    case CLIENT_STREAMING -> StreamType.CLIENT;
                    case SERVER_STREAMING -> StreamType.SERVER;
                    case BIDI_STREAMING -> StreamType.BIDI;
                    case UNKNOWN -> throw new IllegalArgumentException();
                },
                method.isIdempotent() ? Idempotency.IDEMPOTENT : Idempotency.UNKNOWN
        );
    }

    private <T> Class<T> getMessageClass(MethodDescriptor.Marshaller<T> marshaller) {
        if (marshaller instanceof MethodDescriptor.ReflectableMarshaller<T> reflectableMarshaller) {
            return reflectableMarshaller.getMessageClass();
        }
        throw new IllegalStateException();
    }

    public record FutureAdapter<T> (CompletableFuture<T> future) implements Function1<ResponseMessage<T>, Unit> {
        @Override
        public Unit invoke(ResponseMessage<T> responseMessage) {
            responseMessage.success(s -> future.complete(s.getMessage()));
            responseMessage.failure(f -> future.completeExceptionally(f.getCause()));
            return Unit.INSTANCE;
        }
    }

}
