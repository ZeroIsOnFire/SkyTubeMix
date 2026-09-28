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

package free.rm.skytube.gui.businessobjects;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import free.rm.skytube.R;
import free.rm.skytube.app.PerformanceMode;
import free.rm.skytube.app.Settings;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.databinding.DialogPerformanceModeBinding;

/** First-run selector for the one-shot playback quality presets. */
public class PerformanceModeDialog {

    private final Context context;
    @Nullable
    private final Runnable onDismiss;

    public PerformanceModeDialog(@NonNull Context context, @Nullable Runnable onDismiss) {
        this.context = context;
        this.onDismiss = onDismiss;
    }

    public void show() {
        final DialogPerformanceModeBinding binding =
                DialogPerformanceModeBinding.inflate(LayoutInflater.from(context));
        final Settings settings = SkyTubeApp.getSettings();
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.performance_mode_dialog_title)
                .setView(binding.getRoot())
                .create();

        binding.performanceModeChoices.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.performance_mode_low) {
                settings.selectInitialPerformanceMode(PerformanceMode.LOW);
            } else if (checkedId == R.id.performance_mode_standard) {
                settings.selectInitialPerformanceMode(PerformanceMode.STANDARD);
            } else {
                return;
            }
            dialog.dismiss();
        });
        dialog.setOnDismissListener(ignored -> {
            if (!settings.hasPerformanceMode()) {
                settings.selectInitialPerformanceMode(PerformanceMode.STANDARD);
            }
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
        dialog.show();
    }
}
