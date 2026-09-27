/*
 * SkyTube
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */

package free.rm.skytube.businessobjects.YouTube.VideoStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VideoResolutionTest {

    @ParameterizedTest
    @CsvSource({
            "0, RES_144P",
            "143, RES_144P",
            "144, RES_144P",
            "239, RES_144P",
            "240, RES_240P",
            "600, RES_480P",
            "720, RES_720P",
            "1080, RES_1080P",
            "2160, RES_2160P",
            "4320, RES_2160P"
    })
    void choosesHighestSupportedResolutionWithinDisplayCapability(
            int capability, VideoResolution expected) {
        assertEquals(expected, VideoResolution.highestSupportedAtMost(capability));
    }
}
