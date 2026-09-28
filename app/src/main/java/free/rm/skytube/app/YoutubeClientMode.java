/*
 * SkyTube
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */

package free.rm.skytube.app;

/**
 * Defines which InnerTube client is used to retrieve playable YouTube streams.
 */
public enum YoutubeClientMode {
    VISION_OS_WITH_FALLBACK("vision_os_with_fallback"),
    VISION_OS_ONLY("vision_os_only"),
    STANDARD_ANDROID("standard_android");

    private final String value;

    YoutubeClientMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static YoutubeClientMode fromValue(String value) {
        for (YoutubeClientMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return VISION_OS_WITH_FALLBACK;
    }
}
