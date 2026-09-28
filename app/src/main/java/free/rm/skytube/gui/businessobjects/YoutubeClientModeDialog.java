/*
 * SkyTube
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */

package free.rm.skytube.gui.businessobjects;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import free.rm.skytube.R;
import free.rm.skytube.app.Settings;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.app.YoutubeClientMode;
import free.rm.skytube.databinding.DialogYoutubeClientModeBinding;

/** First-run selector for the YouTube stream client compatibility mode. */
public class YoutubeClientModeDialog {
    private final Context context;
    @Nullable
    private final Runnable onDismiss;

    public YoutubeClientModeDialog(@NonNull Context context, @Nullable Runnable onDismiss) {
        this.context = context;
        this.onDismiss = onDismiss;
    }

    public void show() {
        final DialogYoutubeClientModeBinding binding =
                DialogYoutubeClientModeBinding.inflate(LayoutInflater.from(context));
        final Settings settings = SkyTubeApp.getSettings();
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.youtube_client_mode_dialog_title)
                .setView(binding.getRoot())
                .create();

        binding.youtubeClientModeChoices.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.youtube_client_mode_vision_fallback) {
                settings.setYoutubeClientMode(YoutubeClientMode.VISION_OS_WITH_FALLBACK);
            } else if (checkedId == R.id.youtube_client_mode_vision_only) {
                settings.setYoutubeClientMode(YoutubeClientMode.VISION_OS_ONLY);
            } else if (checkedId == R.id.youtube_client_mode_standard_android) {
                settings.setYoutubeClientMode(YoutubeClientMode.STANDARD_ANDROID);
            } else {
                return;
            }
            dialog.dismiss();
        });
        dialog.setOnDismissListener(ignored -> {
            if (!settings.hasYoutubeClientMode()) {
                settings.setYoutubeClientMode(YoutubeClientMode.VISION_OS_WITH_FALLBACK);
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
        dialog.show();
    }
}
