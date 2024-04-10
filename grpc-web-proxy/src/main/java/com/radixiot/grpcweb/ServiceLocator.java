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

import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;

/**
 * @author Jared Wiltshire
 */
@FunctionalInterface
public interface ServiceLocator {

    /**
     * Locate a service descriptor using its name.
     *
     * @param serviceName name of the service to locate
     * @throws NoSuchElementException if the service descriptor could not be found
     * @return a service descriptor
     */
    ServiceDescriptor locate(String serviceName);

    /**
     * Locate a method descriptor using its full method name (qualified by a service name, separated by a slash).
     *
     * @param fullMethodName name of the method to locate
     * @throws NoSuchElementException if the method descriptor could not be found
     * @return a method descriptor
     */
    default MethodDescriptor<?, ?> locateMethod(String fullMethodName) {
        String serviceName = fullMethodName.substring(0, fullMethodName.indexOf("/"));

        ServiceDescriptor serviceDescriptor = locate(serviceName);
        return serviceDescriptor.getMethods().stream()
                .filter(m -> fullMethodName.equals(m.getFullMethodName()))
                .findFirst()
                .orElseThrow();
    }

}
