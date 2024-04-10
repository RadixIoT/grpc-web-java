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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.IndefiniteWaitOneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Jared Wiltshire
 */
class RunConformanceTests {

    private final DockerImageName imageName;

    RunConformanceTests() throws IOException {
        String imageName = loadStringResource("META-INF/docker/com.radixiot.grpcweb/grpc-web-conformance/image-name");
        this.imageName = DockerImageName.parse(imageName);
    }

    private String loadStringResource(String resourceName) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = Objects.requireNonNull(classLoader.getResourceAsStream(resourceName));
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.readLine();
        }
    }

    @Test
    void runConformanceTests() {
        try (GenericContainer<?> container = new GenericContainer<>(imageName)
                .withStartupCheckStrategy(new IndefiniteWaitOneShotStartupCheckStrategy())) {

            container.start();

            @Nullable String destFile = System.getProperty("jacoco.destFile");
            container.copyFileFromContainer("/root/jacoco.exec", destFile != null ? destFile : "jacoco.exec");

            // one shot startup strategy waits until the container exits with a successful exit code
            assertThat(container.isRunning()).isFalse();
        }
    }

}
