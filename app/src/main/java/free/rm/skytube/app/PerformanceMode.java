/*
 * SkyTube
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package free.rm.skytube.app;

import androidx.annotation.Nullable;

/** A one-shot preset for playback quality preferences. */
public enum PerformanceMode {
    STANDARD("standard"),
    LOW("low");

    private final String value;

    PerformanceMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public static PerformanceMode fromValue(@Nullable String value) {
        for (PerformanceMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return null;
    }
}
