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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;

import io.grpc.ServiceDescriptor;

/**
 * @author Jared Wiltshire
 */
@FunctionalInterface
public interface ClassServiceLocator extends ServiceLocator {

    String getClassName(String serviceName);

    @Override
    default ServiceDescriptor locate(String serviceName) {
        try {
            Class<?> rpcClass = Class.forName(getClassName(serviceName));
            Method method = rpcClass.getDeclaredMethod("getServiceDescriptor");
            return (ServiceDescriptor) method.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new NoSuchElementException(e);
        }
    }

}
