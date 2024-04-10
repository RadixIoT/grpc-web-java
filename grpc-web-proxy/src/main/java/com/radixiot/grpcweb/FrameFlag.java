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

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Jared Wiltshire
 */
enum FrameFlag {
    COMPRESSED(0x01),
    TRAILERS(0x80);

    private final int bitmask;

    FrameFlag(int bitmask) {
        this.bitmask = bitmask;
    }

    static Set<FrameFlag> decode(int flags) {
        var flagsSet = EnumSet.noneOf(FrameFlag.class);
        for (var flag : FrameFlag.values()) {
            if ((flags & flag.bitmask) != 0) {
                flagsSet.add(flag);
            }
        }
        return flagsSet;
    }

    static int encode(Set<FrameFlag> flagsSet) {
        int flags = 0x00;
        for (var flag : flagsSet) {
            flags |= flag.bitmask;
        }
        return flags;
    }
}
