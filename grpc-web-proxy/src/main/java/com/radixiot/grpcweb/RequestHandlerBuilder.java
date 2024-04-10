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

import org.checkerframework.checker.nullness.qual.Nullable;

public class RequestHandlerBuilder {
    private @Nullable ChannelManager channelManager = null;
    private @Nullable ServiceLocator serviceLocator = null;
    private @Nullable ContentTypeLookup contentTypeLookup = null;
    private @Nullable HeaderConverter headerConverter = null;
    private @Nullable Integer maxFrameSize = null;

    RequestHandlerBuilder() {
    }

    public RequestHandlerBuilder setChannelManager(ChannelManager channelManager) {
        this.channelManager = channelManager;
        return this;
    }

    public RequestHandlerBuilder setServiceLocator(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
        return this;
    }

    public RequestHandlerBuilder setContentTypeLookup(ContentTypeLookup contentTypeLookup) {
        this.contentTypeLookup = contentTypeLookup;
        return this;
    }

    public RequestHandlerBuilder setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
        return this;
    }

    public RequestHandlerBuilder setHeaderConverter(HeaderConverter headerConverter) {
        this.headerConverter = headerConverter;
        return this;
    }

    public RequestHandler build() {
        return new RequestHandler(
                channelManager != null ? channelManager : new DefaultChannelManager(),
                serviceLocator != null ? serviceLocator : new DefaultServiceLocator(),
                contentTypeLookup != null ? contentTypeLookup : new DefaultContentTypeLookup(),
                headerConverter != null ? headerConverter : new DefaultHeaderConverter(),
                maxFrameSize != null ? maxFrameSize : 200 * 1024
        );
    }
}
