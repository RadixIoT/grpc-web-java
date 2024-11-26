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

import static com.radixiot.grpcweb.GrpcWebFilter.DEFAULT_FORWARD_DESTINATION;

import java.util.EnumSet;

import jakarta.servlet.DispatcherType;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import com.radixiot.grpcweb.ClassServiceLocator;
import com.radixiot.grpcweb.DefaultChannelManager;
import com.radixiot.grpcweb.GrpcWebFilter;
import com.radixiot.grpcweb.GrpcWebServlet;
import com.radixiot.grpcweb.RequestHandler;

import io.grpc.inprocess.InProcessServerBuilder;

/**
 * @author Jared Wiltshire
 */
public class ConformanceServer {

    public static void main(String[] args) throws Exception {
        var server = new ConformanceServer(204800);
        server.init(Integer.parseInt(args[0]));
    }

    private final int messageReceiveLimit;

    public ConformanceServer(int messageReceiveLimit) {
        this.messageReceiveLimit = messageReceiveLimit;
    }

    int init(int port) throws Exception {
        InProcessServerBuilder.forName(DefaultChannelManager.DEFAULT_CHANNEL_NAME)
                .addService(new ConformanceService())
                .intercept(new HeaderSendingInterceptor())
                .intercept(new HeaderCapturingInterceptor())
                .build()
                .start();

        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addFilterWithMapping(GrpcWebFilter.class, "/*",
                EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC));

        var requestHandler = RequestHandler.builder()
                .setServiceLocator((ClassServiceLocator) serviceName -> "build.buf.gen." + serviceName + "Grpc")
                .setMaxFrameSize(messageReceiveLimit)
                .build();
        var servlet = new GrpcWebServlet(requestHandler);
        servletHandler.addServletWithMapping(new ServletHolder(servlet), DEFAULT_FORWARD_DESTINATION + "/*");

        ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.setServletHandler(servletHandler);

        Server jettyServer = new Server(port);
        jettyServer.setHandler(servletContextHandler);
        jettyServer.start();

        return ((ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();
    }

}
