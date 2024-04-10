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

import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;

/**
 * @author Jared Wiltshire
 */
public class DefaultChannelManager implements ChannelManager {

    public static final String DEFAULT_CHANNEL_NAME = "default";

    private final Channel channel;

    public DefaultChannelManager() {
        this(DEFAULT_CHANNEL_NAME);
    }

    public DefaultChannelManager(String channelName) {
        this(InProcessChannelBuilder.forName(channelName).build());
    }

    public DefaultChannelManager(Channel channel) {
        this.channel = channel;
    }

    @Override
    public Channel getChannel(String serviceName, String methodName) {
        return channel;
    }
}
