/*
 * SkyTube
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */

package free.rm.skytube.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.YouTube.VideoStream.VideoQuality;
import free.rm.skytube.businessobjects.YouTube.VideoStream.VideoResolution;

class SettingsTest {

    private SkyTubeApp app;
    private InMemorySharedPreferences preferences;
    private Settings settings;

    @BeforeEach
    void setUp() {
        app = mock(SkyTubeApp.class);
        when(app.getString(any(Integer.class))).thenAnswer(invocation -> key(invocation.getArgument(0)));
        preferences = new InMemorySharedPreferences();
        settings = new Settings(app, preferences);
    }

    @Test
    void freshInstallKeepsPerformanceModeAbsentAndEnablesMix() {
        settings.migrate();

        assertNull(settings.getPerformanceMode());
        assertTrue(settings.isContinuousMixPlaybackEnabled());
        assertEquals(YoutubeClientMode.VISION_OS_WITH_FALLBACK,
                settings.getYoutubeClientMode());
        assertFalse(settings.hasYoutubeClientMode());
    }

    @Test
    void storedYoutubeClientModeIsUsed() {
        preferences.edit()
                .putString(key(R.string.pref_key_youtube_client_mode),
                        YoutubeClientMode.STANDARD_ANDROID.getValue())
                .apply();

        assertEquals(YoutubeClientMode.STANDARD_ANDROID, settings.getYoutubeClientMode());
        assertTrue(settings.hasYoutubeClientMode());
    }

    @Test
    void invalidYoutubeClientModeFallsBackToRecommendedMode() {
        preferences.edit()
                .putString(key(R.string.pref_key_youtube_client_mode), "removed_mode")
                .apply();

        assertEquals(YoutubeClientMode.VISION_OS_WITH_FALLBACK,
                settings.getYoutubeClientMode());
    }

    @Test
    void youtubeClientSelectionIsPersisted() {
        settings.setYoutubeClientMode(YoutubeClientMode.VISION_OS_ONLY);

        assertTrue(settings.hasYoutubeClientMode());
        assertEquals(YoutubeClientMode.VISION_OS_ONLY, settings.getYoutubeClientMode());
    }

    @Test
    void existingInstallGetsInformationalStandardModeWithoutOverwritingQuality() {
        preferences.edit()
                .putString(key(R.string.pref_key_video_quality), VideoQuality.LEAST_BANDWIDTH.name())
                .putString(key(R.string.pref_key_maximum_res), "3")
                .apply();

        settings.migrate();

        assertEquals(PerformanceMode.STANDARD, settings.getPerformanceMode());
        assertEquals(VideoQuality.LEAST_BANDWIDTH.name(),
                preferences.getString(key(R.string.pref_key_video_quality), null));
        assertEquals("3", preferences.getString(key(R.string.pref_key_maximum_res), null));
    }

    @Test
    void existingDisabledMixPreferenceIsPreserved() {
        preferences.edit()
                .putBoolean(key(R.string.pref_key_continuous_mix_playback), false)
                .apply();

        settings.migrate();

        assertFalse(settings.isContinuousMixPlaybackEnabled());
    }

    @Test
    void lowPresetUsesPhysicalDisplayAndLeavesMinimumAndDownloadPreferencesUntouched() {
        configureDisplay(1024, 600);
        seedUntouchedPreferences();

        VideoResolution selected = settings.applyPerformanceMode(PerformanceMode.LOW);

        assertEquals(VideoResolution.RES_480P, selected);
        assertEquals(PerformanceMode.LOW, settings.getPerformanceMode());
        assertEquals(VideoQuality.LEAST_BANDWIDTH.name(),
                preferences.getString(key(R.string.pref_key_video_quality), null));
        assertEquals(VideoQuality.LEAST_BANDWIDTH.name(),
                preferences.getString(key(R.string.pref_key_video_quality_on_mobile), null));
        assertEquals("3", preferences.getString(key(R.string.pref_key_maximum_res), null));
        assertEquals("3", preferences.getString(key(R.string.pref_key_maximum_res_mobile), null));
        assertUntouchedPreferences();
    }

    @Test
    void standardPresetRestoresProjectDefaultsAndRemovesMeteredMaximum() {
        preferences.edit()
                .putString(key(R.string.pref_key_maximum_res_mobile), "2")
                .apply();
        seedUntouchedPreferences();

        settings.applyPerformanceMode(PerformanceMode.STANDARD);

        assertEquals(PerformanceMode.STANDARD, settings.getPerformanceMode());
        assertEquals(VideoQuality.BEST_QUALITY.name(),
                preferences.getString(key(R.string.pref_key_video_quality), null));
        assertEquals(VideoQuality.LEAST_BANDWIDTH.name(),
                preferences.getString(key(R.string.pref_key_video_quality_on_mobile), null));
        assertEquals("5", preferences.getString(key(R.string.pref_key_maximum_res), null));
        assertFalse(preferences.contains(key(R.string.pref_key_maximum_res_mobile)));
        assertUntouchedPreferences();
    }

    @Test
    void initialStandardChoiceOnlyStoresMode() {
        preferences.edit()
                .putString(key(R.string.pref_key_video_quality), VideoQuality.LEAST_BANDWIDTH.name())
                .putString(key(R.string.pref_key_maximum_res), "3")
                .apply();

        settings.selectInitialPerformanceMode(PerformanceMode.STANDARD);

        assertEquals(PerformanceMode.STANDARD, settings.getPerformanceMode());
        assertEquals(VideoQuality.LEAST_BANDWIDTH.name(),
                preferences.getString(key(R.string.pref_key_video_quality), null));
        assertEquals("3", preferences.getString(key(R.string.pref_key_maximum_res), null));
    }

    @Test
    void storedModeDoesNotReapplyPresetDuringMigration() {
        preferences.edit()
                .putString(key(R.string.pref_key_performance_mode), PerformanceMode.LOW.getValue())
                .putString(key(R.string.pref_key_video_quality), VideoQuality.BEST_QUALITY.name())
                .putString(key(R.string.pref_key_maximum_res), "4")
                .apply();

        settings.migrate();

        assertEquals(PerformanceMode.LOW, settings.getPerformanceMode());
        assertEquals(VideoQuality.BEST_QUALITY.name(),
                preferences.getString(key(R.string.pref_key_video_quality), null));
        assertEquals("4", preferences.getString(key(R.string.pref_key_maximum_res), null));
    }

    private void configureDisplay(int width, int height) {
        WindowManager windowManager = mock(WindowManager.class);
        Display display = mock(Display.class);
        when(app.getSystemService(Context.WINDOW_SERVICE)).thenReturn(windowManager);
        when(windowManager.getDefaultDisplay()).thenReturn(display);
        doAnswer(invocation -> {
            Point point = invocation.getArgument(0);
            point.x = width;
            point.y = height;
            return null;
        }).when(display).getRealSize(any(Point.class));
    }

    private void seedUntouchedPreferences() {
        preferences.edit()
                .putString(key(R.string.pref_key_minimum_res), "1")
                .putString(key(R.string.pref_key_minimum_res_mobile), "2")
                .putString(key(R.string.pref_key_video_download_minimum_resolution), "0")
                .putString(key(R.string.pref_key_video_download_maximum_resolution), "4")
                .putString(key(R.string.pref_key_video_quality_for_downloads), VideoQuality.BEST_QUALITY.name())
                .apply();
    }

    private void assertUntouchedPreferences() {
        assertEquals("1", preferences.getString(key(R.string.pref_key_minimum_res), null));
        assertEquals("2", preferences.getString(key(R.string.pref_key_minimum_res_mobile), null));
        assertEquals("0", preferences.getString(key(R.string.pref_key_video_download_minimum_resolution), null));
        assertEquals("4", preferences.getString(key(R.string.pref_key_video_download_maximum_resolution), null));
        assertEquals(VideoQuality.BEST_QUALITY.name(),
                preferences.getString(key(R.string.pref_key_video_quality_for_downloads), null));
    }

    private static String key(int resourceId) {
        return "resource-" + resourceId;
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(values);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<String> getStringSet(String key, Set<String> defaultValues) {
            Object value = values.get(key);
            return value instanceof Set ? new HashSet<>((Set<String>) value) : defaultValues;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new InMemoryEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        private final class InMemoryEditor implements Editor {
            private final Map<String, Object> updates = new HashMap<>();
            private final Set<String> removals = new HashSet<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> value) {
                updates.put(key, new HashSet<>(value));
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                removals.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clear) {
                    values.clear();
                }
                for (String key : removals) {
                    values.remove(key);
                }
                values.putAll(updates);
            }
        }
    }
}
