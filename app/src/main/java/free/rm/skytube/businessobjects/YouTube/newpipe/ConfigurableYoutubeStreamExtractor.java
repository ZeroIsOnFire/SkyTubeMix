/*
 * SkyTube
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */

package free.rm.skytube.businessobjects.YouTube.newpipe;

import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import free.rm.skytube.app.YoutubeClientMode;
import free.rm.skytube.businessobjects.Logger;

/**
 * Adds a selectable Android-client fallback to the pinned NewPipe extractor.
 *
 * <p>The dependency currently fixes stream extraction to visionOS and keeps the relevant state
 * private. This subclass deliberately confines the compatibility bridge to one class. The Android
 * request matches the no-token reel request used by SkyTube's previous extractor.</p>
 */
final class ConfigurableYoutubeStreamExtractor extends YoutubeStreamExtractor {
    private static final String ANDROID_CLIENT_NAME = "ANDROID";
    private static final String ANDROID_CLIENT_VERSION = "21.03.36";
    private static final String ANDROID_USER_AGENT_VERSION = "Android 15";
    private static final String PRETTY_PRINT_DISABLED = "prettyPrint=false";
    private static final String PLAYER_RESPONSE = "playerResponse";
    private static final String STREAMING_DATA = "streamingData";
    private static final String PLAYABILITY_STATUS = "playabilityStatus";

    private static final Field PLAYER_RESPONSE_FIELD = field("playerResponse");
    private static final Field STREAMING_DATA_FIELD = field("visionOsStreamingData");
    private static final Field CPN_FIELD = field("visionOsCpn");
    private static final Field CAPTIONS_FIELD = field("playerCaptionsTracklistRenderer");
    private static final Field NEXT_RESPONSE_FIELD = field("nextResponse");
    private static final Method CHECK_PLAYABILITY_METHOD = method(
            "checkPlayabilityStatus", JsonObject.class);
    private static final Method SET_STREAM_TYPE_METHOD = method("setStreamType");
    private static final Method FETCH_WEB_METADATA_METHOD = method(
            "fetchWebClientMetadataAndSetThumbnails",
            Localization.class, ContentCountry.class, String.class);

    private final YoutubeClientMode clientMode;

    ConfigurableYoutubeStreamExtractor(StreamingService service, LinkHandler linkHandler,
                                       YoutubeClientMode clientMode) {
        super(service, linkHandler);
        this.clientMode = clientMode;
    }

    @Override
    public void onFetchPage(Downloader downloader) throws IOException, ExtractionException {
        if (clientMode == YoutubeClientMode.VISION_OS_ONLY) {
            Logger.i(this, "YouTube stream client: visionOS without fallback");
            super.onFetchPage(downloader);
            return;
        }

        if (clientMode == YoutubeClientMode.STANDARD_ANDROID) {
            Logger.i(this, "YouTube stream client: standard Android");
            fetchWithStandardAndroidClient(downloader);
            return;
        }

        Logger.i(this, "YouTube stream client: visionOS with Android fallback");
        try {
            super.onFetchPage(downloader);
        } catch (IOException | ExtractionException visionOsFailure) {
            Logger.i(this, "visionOS extraction failed; trying the standard Android client: %s",
                    visionOsFailure.getMessage());
            try {
                fetchWithStandardAndroidClient(downloader);
            } catch (IOException | ExtractionException androidFailure) {
                androidFailure.addSuppressed(visionOsFailure);
                throw androidFailure;
            }
        }
    }

    private void fetchWithStandardAndroidClient(Downloader downloader)
            throws IOException, ExtractionException {
        final String videoId = getId();
        final Localization localization = getExtractorLocalization();
        final ContentCountry contentCountry = getExtractorContentCountry();
        final String cpn = YoutubeParsingHelper.generateContentPlaybackNonce();
        final JsonObject response = getAndroidReelPlayerResponse(
                downloader, localization, contentCountry, videoId, cpn);

        invoke(CHECK_PLAYABILITY_METHOD, null, response.getObject(PLAYABILITY_STATUS));
        if (!videoId.equals(response.getObject("videoDetails").getString("videoId"))) {
            throw new ExtractionException("ANDROID player response is not valid");
        }

        set(PLAYER_RESPONSE_FIELD, response);
        set(STREAMING_DATA_FIELD, response.getObject(STREAMING_DATA));
        set(CPN_FIELD, cpn);
        set(CAPTIONS_FIELD, response.getObject("captions")
                .getObject("playerCaptionsTracklistRenderer"));

        invoke(SET_STREAM_TYPE_METHOD, this);
        invoke(FETCH_WEB_METADATA_METHOD, this, localization, contentCountry, videoId);

        final byte[] nextBody = JsonWriter.string(
                YoutubeParsingHelper.prepareDesktopJsonBuilder(localization, contentCountry)
                        .value(YoutubeParsingHelper.VIDEO_ID, videoId)
                        .value(YoutubeParsingHelper.CONTENT_CHECK_OK, true)
                        .value(YoutubeParsingHelper.RACY_CHECK_OK, true)
                        .done())
                .getBytes(StandardCharsets.UTF_8);
        set(NEXT_RESPONSE_FIELD, YoutubeParsingHelper.getJsonPostResponse(
                "next", nextBody, localization));
    }

    private JsonObject getAndroidReelPlayerResponse(Downloader downloader,
                                                     Localization localization,
                                                     ContentCountry contentCountry,
                                                     String videoId,
                                                     String cpn)
            throws IOException, ExtractionException {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(
                "com.google.android.youtube/" + ANDROID_CLIENT_VERSION
                        + " (Linux; U; " + ANDROID_USER_AGENT_VERSION + "; "
                        + localization.getCountryCode() + ") gzip"));
        headers.put("X-Goog-Api-Format-Version", Collections.singletonList("2"));

        final JsonBuilder<JsonObject> visitorBuilder = createAndroidContext(
                localization, contentCountry, null);
        final String visitorUrl = YoutubeParsingHelper.YOUTUBEI_V1_GAPIS_URL
                + "visitor_id?" + PRETTY_PRINT_DISABLED;
        final JsonObject visitorResponse = parseResponse(downloader, visitorUrl, headers,
                JsonWriter.string(visitorBuilder.done()).getBytes(StandardCharsets.UTF_8),
                localization);
        final String visitorData = visitorResponse.getObject("responseContext")
                .getString("visitorData");
        if (visitorData == null || visitorData.isEmpty()) {
            throw new ExtractionException("Could not get Android visitorData");
        }

        final JsonBuilder<JsonObject> playerBuilder = createAndroidContext(
                localization, contentCountry, visitorData);
        playerBuilder.object("playerRequest")
                .value(YoutubeParsingHelper.VIDEO_ID, videoId)
                .value(YoutubeParsingHelper.CPN, cpn)
                .value(YoutubeParsingHelper.CONTENT_CHECK_OK, true)
                .value(YoutubeParsingHelper.RACY_CHECK_OK, true)
                .end()
                .value("disablePlayerResponse", false);

        final String playerUrl = YoutubeParsingHelper.YOUTUBEI_V1_GAPIS_URL
                + "reel/reel_item_watch?" + PRETTY_PRINT_DISABLED
                + "&t=" + YoutubeParsingHelper.generateTParameter()
                + "&id=" + videoId + "&$fields=" + PLAYER_RESPONSE;
        return parseResponse(downloader, playerUrl, headers,
                JsonWriter.string(playerBuilder.done()).getBytes(StandardCharsets.UTF_8),
                localization).getObject(PLAYER_RESPONSE);
    }

    private JsonBuilder<JsonObject> createAndroidContext(Localization localization,
                                                          ContentCountry contentCountry,
                                                          String visitorData) {
        final JsonBuilder<JsonObject> builder = JsonObject.builder()
                .object("context")
                .object("client")
                .value("clientName", ANDROID_CLIENT_NAME)
                .value("clientVersion", ANDROID_CLIENT_VERSION)
                .value("clientScreen", "WATCH")
                .value("platform", "MOBILE");
        if (visitorData != null) {
            builder.value("visitorData", visitorData);
        }
        builder.value("osName", "Android")
                .value("osVersion", "16")
                .value("androidSdkVersion", 36)
                .value("hl", localization.getLocalizationCode())
                .value("gl", contentCountry.getCountryCode())
                .value("utcOffsetMinutes", 0)
                .end()
                .object("request")
                .array("internalExperimentFlags")
                .end()
                .value("useSsl", true)
                .end()
                .object("user")
                .value("lockedSafetyMode", false)
                .end()
                .end();
        return builder;
    }

    private JsonObject parseResponse(Downloader downloader,
                                     String url,
                                     Map<String, List<String>> headers,
                                     byte[] body,
                                     Localization localization)
            throws IOException, ExtractionException {
        return JsonUtils.toJsonObject(YoutubeParsingHelper.getValidJsonResponseBody(
                downloader.postWithContentTypeJson(url, headers, body, localization)));
    }

    private static Field field(String name) {
        try {
            final Field field = YoutubeStreamExtractor.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            final Method method = YoutubeStreamExtractor.class.getDeclaredMethod(name,
                    parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private void set(Field field, Object value) throws ExtractionException {
        try {
            field.set(this, value);
        } catch (IllegalAccessException e) {
            throw new ExtractionException("Could not initialize YouTube extractor fallback", e);
        }
    }

    private void invoke(Method method, Object receiver, Object... arguments)
            throws IOException, ExtractionException {
        try {
            method.invoke(receiver, arguments);
        } catch (IllegalAccessException e) {
            throw new ExtractionException("Could not invoke YouTube extractor fallback", e);
        } catch (InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof ExtractionException) {
                throw (ExtractionException) cause;
            }
            throw new ExtractionException("YouTube extractor fallback failed", cause);
        }
    }
}
