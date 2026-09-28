# General Fixes and Fork Updates

## 1. Purpose

This document defines the next set of fixes, defaults, UX changes, documentation updates, and performance-oriented improvements for this SkyTube fork after the Mix-based continuous playback feature is implemented.

The fork should prioritize:
- simple continuous playback;
- older and low-performance Android devices;
- predictable playback quality behavior;
- easier first-run setup;
- clear fork identity and documentation.

Preserve the current Android minimum supported by the project. Avoid dependency upgrades and unrelated refactors unless strictly required.

### Feasibility audit (2026-09-27)

This audit is documentation-only. It does not authorize implementation yet.

| Section | Result | Current implementation point / constraint |
|---|---|---|
| 2. Mix enabled by default | Feasible | Reuse `pref_key_continuous_mix_playback`; change both the `Settings` fallback and XML default, without writing over an existing value. |
| 3. Resolution fallback | **DENIED as specified** | The app exposes minimum/maximum bounds, not an exact target. Crossing the configured maximum contradicts the existing preference contract and the low-performance ceiling. See section 3. |
| 4. New application icon | Feasible, artwork approval required | Legacy PNG launchers exist in `mipmap-mdpi` through `mipmap-xxxhdpi`; adaptive resources also exist for API 26+. API 19 can remain supported. |
| 5. Translation review | Feasible | Limit changes to base English and `values-pt-rBR` fork strings. |
| 6. README update | Feasible | Preserve the existing upstream description, attribution, download information, and GPL section. Do not advertise the denied fallback. |
| 7. Performance review | Feasible as an evidence-gathering task | Use targeted code inspection plus ADB measurements on the reference device. Any optimization remains conditional on evidence. |
| 8–12. Performance modes and presets | Feasible | Reuse the existing playback quality and resolution keys. The mode is a one-shot preset and must not continuously enforce values. |
| 13–15. Mix interaction, preference model, first-run flow | Feasible | Mix remains independent. Coordinate the new first-run dialog with the existing privacy dialog so dialogs are not stacked. |
| 16. Resolution fallback interaction | **DENIED as specified** | It depends on the rejected exact-target fallback model. The preset can still set a maximum resolution and use the existing range-aware selector. |
| 17–18. UX and logging | Feasible | Logging must describe preset application and the existing range-based stream selection only. |
| 19. Tests | Partially feasible | Tests 1–2 and 7–13 are feasible. Tests 3–6 encode the denied resolution semantics. Existing range-selection tests remain authoritative. |
| 20. Performance validation | Feasible | Cold-start timing, `dumpsys meminfo`, `gfxinfo`, logcat, playback timing, and observation can be collected through ADB; Android Studio is not required for the initial review. |
| 21–24. README, non-goals, workflow, definition of done | Feasible after the denied fallback requirements are excluded | Keep ExoPlayer, NewPipe, dependencies, and API 19 unchanged. |

Continuous Mix playback is supported only by the local ExoPlayer path. The Legacy `VideoView` player is explicitly unsupported and must not resolve queues, continue playlists/Mixes, or expose the Mix toggle. The official YouTube player and Chromecast retain their existing behavior.

---

## 2. Mix enabled by default

The existing feature:

`Reprodução contínua com Mix`

must be enabled by default on fresh installs.

Existing preference key:

`pref_key_continuous_mix_playback`

Behavior:

```text
preference does not exist
    -> default ON

preference already exists
    -> preserve stored value
```

Do not overwrite an existing user's choice during application updates.

---

## 3. Resolution fallback — DENIED

### Problem

The reported failure in `resolution-error.png` was reproduced with the unsupported Legacy `VideoView` player. It was not reproduced with the supported ExoPlayer path.

The current app does not request an exact resolution. `pref_maximum_resolution` and `pref_minimum_resolution` define an allowed range, and `StreamSelectionPolicy` selects within that range:

- `Best Quality` selects the highest playable stream inside the configured range;
- `Least Bandwidth` selects the lowest playable stream inside the configured range;
- ExoPlayer can include video-only streams when newer formats are enabled;
- Legacy forces progressive streams and is outside the scope of this fork feature.

The algorithm below is denied because it treats the maximum-resolution preference as an exact target. For example, selecting 1080p when the configured maximum is 720p violates the preference contract. It also conflicts with section 9, where low-performance mode requires a real resolution ceiling.

No stream-selection code should be changed for this item. A future, separately approved product change may redefine the quality controls, but it must not be bundled with the performance preset work.

Rejected behaviors include crossing a 720p maximum to select 1080p, treating `Least Bandwidth` as “nearest below maximum,” and overriding explicit minimum/maximum limits merely to force playback.

---

## 4. New application icon

Add a new launcher icon based on the premise of this fork.

The icon should communicate:

- SkyTube lineage;
- continuous playback;
- lightweight / legacy-device-friendly focus.

Requirements:

- preserve Android launcher compatibility;
- preserve API 19 support;
- provide density-specific assets as required by the existing project;
- do not rely only on adaptive icons;
- remain recognizable at small sizes.

The visual artwork may be produced separately, but the project must be prepared to integrate the new icon resources cleanly.

---

## 5. Translation review

Review all strings added by this fork.

At minimum review:

- Continuous playback with Mix;
- Low performance mode;
- Standard mode;
- first-run mode selector;
- menu labels;
- Settings descriptions.

Requirements:

- no hard-coded user-facing strings;
- correct base English strings;
- correct Brazilian Portuguese translations;
- consistent naming between Settings and video menus.

Suggested terminology:

English:

```text
Continuous playback with Mix
Low performance mode
Standard mode
Least Bandwidth
Best Quality
```

Portuguese:

```text
Reprodução contínua com Mix
Modo de baixo desempenho
Modo padrão
Menor consumo de dados
Melhor qualidade
```

Avoid unrelated translation cleanup.

---

## 6. README update

Add a separate section explaining the premise of this fork.

Suggested heading:

```markdown
## About this fork
```

It should explain that this fork focuses on:

- Mix-based continuous playback for standalone videos;
- better behavior on old Android devices;
- a low-performance preset;
- the existing range-based video-quality selection;
- lightweight operation.

State that Continuous Mix playback is available only with the local ExoPlayer. Do not present it as a Legacy, official-player, or Chromecast feature.

Clearly state that this is an independent fork of SkyTube.

Preserve upstream attribution and license information.

Suggested README organization:

```text
Original SkyTube description
About this fork
Features added by this fork
Low-performance mode
Compatibility goals
Build instructions
Original project attribution
```

---

## 7. Performance review for older devices

Perform a targeted performance review focused on old and low-resource Android devices.

Reference device:

```text
Samsung Galaxy Tab 3 7.0
SM-T210
Android 4.4.x
1 GB RAM class device
```

This is a reference target, not the only supported device.

Inspect:

- feed/list rendering;
- thumbnail loading;
- image-cache size;
- background tasks;
- startup initialization;
- player initialization;
- playlist/Mix prefetching;
- database access;
- network calls;
- memory allocations;
- large in-memory lists;
- animations;
- background services.

Prioritize improvements that reduce memory use, UI jank, unnecessary background work, startup cost, and excessive thumbnail decoding.

Do not perform large rewrites just to optimize performance.

### Initial code-audit findings

These are investigation targets, not pre-approved changes:

- `MainActivity.onCreate()` immediately starts the update check and a scan for missing downloaded files. Measure whether either task competes with first rendering before considering delayed execution.
- `GridViewHolder` requests thumbnail URLs through Glide without an explicit decode size, while `SkyTubeGlideModule` has no cache configuration. Measure decoded bitmap sizes and cache pressure on the reference device before adding a low-performance override.
- `VideosGridFragment.onDestroyView()` calls `Glide.clearMemory()`, which evicts the process-wide memory cache and may cause repeated decoding when navigating between screens. This is a strong candidate for targeted validation.
- `RecyclerViewAdapterEx` uses full `notifyDataSetChanged()` updates after list changes. More precise notifications may reduce rebinding, but only after checking every adapter mutation path.
- ExoPlayer uses the library's `DefaultLoadControl`. Buffer reduction is possible without upgrading ExoPlayer, but is high-risk and must be validated against rebuffering on slow networks.
- `ContinuousPlaybackManager` buffers one extractor page at a time and the Activity prepares only the next normal-playlist result. This is already conservative; do not reduce it without measurements.
- NewPipe controls playlist/search page sizes; the current `Pager` exposes continuation but no page-size setting. Do not add a second pagination implementation merely to request smaller pages.
- The application manifest enables `largeHeap`. Do not remove it as a speculative optimization; first collect normal/large heap usage and out-of-memory evidence.
- Most database work already uses RxJava I/O schedulers. Focus on the startup scan and any synchronous call sites rather than rewriting the database layer.

### Candidate optimizations to investigate

Do not implement blindly:

- smaller thumbnails in low-performance mode;
- reduced image cache size;
- less aggressive prefetching;
- reduced/disabled animations;
- smaller page sizes where supported;
- reduced Mix metadata prefetch;
- delayed nonessential initialization;
- lower player buffer sizes if safe;
- avoiding duplicate extractor requests;
- reusing already loaded metadata.

Each optimization must have a clear reason, expected benefit, and acceptable risk.

---

## 8. First-run performance-mode popup

Add a first-run dialog with two options:

```text
Low performance mode
Standard mode
```

Show it only if the user has never selected a performance mode.

Use one persistent state key; absence means that first-run selection has not occurred:

```text
pref_key_performance_mode = standard | low
```

Suggested title:

English:

`Choose performance mode`

Portuguese:

`Escolha o modo de desempenho`

Suggested description:

English:

`Low performance mode adjusts video-quality defaults for older or slower devices. Standard mode keeps SkyTube's normal behavior.`

Portuguese:

`O modo de baixo desempenho ajusta as configurações padrão de qualidade de vídeo para dispositivos antigos ou mais lentos. O modo padrão mantém o comportamento normal do SkyTube.`

---

## 9. Low performance mode

Low performance mode is a preset that updates existing SkyTube quality settings.

It must not create a second playback or quality subsystem.

When selected:

1. Set quality mode to `Least Bandwidth`.
2. Set the playback maximum resolution to the closest supported resolution less than or equal to the device's effective display capability.

Example:

```text
Device display height/capability: ~600px
Supported app resolutions:
144p
240p
360p
480p
720p

Result:
480p
```

Do not hard-code Galaxy Tab 3 values.

Detect the current device capability using Android APIs compatible with API 19.

Base the preset on the device's physical display capability, not on the current rotation. On API 19, obtain the real display size with `Display.getRealSize(Point)` and use the smaller physical dimension as the video-height ceiling. Do not recalculate on every orientation change.

Apply the preset only to playback settings:

- `pref_key_video_quality` = `LEAST_BANDWIDTH`;
- `pref_key_video_quality_on_mobile` = `LEAST_BANDWIDTH`;
- `pref_maximum_resolution` = calculated resolution ID;
- `pref_maximum_resolution_mobile` = calculated resolution ID.

Do not change download quality/resolution preferences or minimum-resolution preferences.

---

## 10. Standard mode

On first run, Standard mode should preserve the normal SkyTube defaults.

When the user switches from Low performance mode back to Standard mode through Settings, restore the current project playback defaults:

- Wi-Fi/unmetered quality = `BEST_QUALITY`;
- maximum resolution = `RES_1080P` (ID `5`);
- metered quality = `LEAST_BANDWIDTH`;
- remove the explicit metered maximum so it inherits the normal maximum;
- leave minimum-resolution and download preferences unchanged;
- do not change unrelated preferences.

Do not guess these values. Inspect the current project defaults first.

---

## 11. Performance mode in Settings

Add a persistent Settings option allowing the user to reapply either preset later.

Example:

```text
Performance mode

( ) Standard
( ) Low performance
```

Changing the mode should immediately apply the corresponding preset.

Example:

```text
Current:
Best Quality
1080p

User selects:
Low performance

Result:
Least Bandwidth
resolution ceiling recalculated from device
```

---

## 12. Manual changes after preset application

Performance mode acts as a preset, not a permanent enforcement layer.

Correct behavior:

```text
User selects Low performance
    ↓
preset is applied
    ↓
user manually changes 480p -> 720p
    ↓
720p remains after restart
```

Do not silently reapply Low performance settings on every launch.

Reapply only when the user explicitly selects/reselects that mode, or during first-run setup.

---

## 13. Interaction with Mix

Performance mode and Mix are independent.

Fresh install expected behavior:

```text
Continuous Mix playback = ON
```

If the user chooses Low performance:

```text
Mix = ON
Quality mode = Least Bandwidth
Preferred/default resolution <= device capability
```

If the user chooses Standard:

```text
Mix = ON
Normal SkyTube quality defaults
```

---

## 14. Suggested preference model

Conceptually:

```text
pref_key_continuous_mix_playback = true

pref_key_performance_mode = absent | standard | low
```

Use string resources for preference keys, following the existing project convention.

Quality preferences modified by performance presets must reuse existing SkyTube preference keys.

---

## 15. First-run flow

Recommended flow:

```text
Application starts
    ↓
Has performance mode been selected before?
    │
    ├─ YES
    │    -> continue normally
    │
    └─ NO
         ↓
   show performance mode dialog
         ↓
   user selects mode
         ↓
   apply preset
         ↓
   persist selection
         ↓
   continue
```

Prefer a clear explicit choice.

If the dialog can be dismissed, use Standard mode as the safe fallback.

The first-run performance dialog belongs in `MainActivity`, alongside the existing one-time privacy dialog. Sequence the dialogs rather than showing both at once. Persist the chosen/fallback mode before continuing so rotation or activity recreation cannot show the choice repeatedly.

---

## 16. Resolution fallback and low performance mode — DENIED

The error shown in `resolution-error.png` came from Legacy mode. Legacy is unsupported and Continuous Mix is disabled for it, so no fallback work is authorized from that report.

Low-performance mode must instead reuse the current range-based behavior:

```text
quality = Least Bandwidth
maximum resolution = highest supported enum value <= effective display capability
minimum resolution = preserve the normal project default unless explicitly changed by the preset specification
```

If no stream satisfies the configured range, keep the current supported-player error behavior. Do not silently cross the user's explicit maximum or minimum.

---

## 17. UX rules

Avoid confusing duplicate controls.

The app should expose:

```text
Continuous playback with Mix
Performance mode
Existing video-quality settings
```

Relationship:

```text
Performance mode
    -> applies a group of quality settings

Existing video-quality settings
    -> remain individually editable

Mix
    -> independent playback behavior
```

Suggested low-performance summary:

English:

`Applies video-quality defaults optimized for older or slower devices.`

Portuguese:

`Aplica configurações padrão de qualidade de vídeo otimizadas para dispositivos antigos ou mais lentos.`

---

## 18. Logging

Use the project's existing logging facility.

Useful events:

```text
Performance mode selected: standard
Performance mode selected: low
Low-performance preset applied
Detected display size: <width>x<height>
Selected low-performance max resolution: <value>
Performance preset left download preferences unchanged
```

Avoid noisy per-frame or per-buffer logging.

---

## 19. Tests

### Test 1 — Mix default on fresh install

```text
No stored Mix preference
```

Expected:

```text
Mix enabled
```

### Test 2 — Existing Mix preference preserved

```text
Stored Mix preference = OFF
App update
```

Expected:

```text
Mix remains OFF
```

### Test 3 — Best Quality fallback upward — DENIED

Rejected because 720p is a maximum bound, so selecting 1080p would violate the stored preference.

```text
Mode: Best Quality
Requested: 720p
Available: 360p, 480p, 1080p
```

Expected:

```text
1080p
```

### Test 4 — Best Quality fallback downward — DENIED

Covered instead by the existing range-selection behavior: Best Quality already selects the highest playable stream at or below the maximum and at or above the minimum.

```text
Mode: Best Quality
Requested: 1080p
Available: 360p, 480p, 720p
```

Expected:

```text
720p
```

### Test 5 — Least Bandwidth fallback downward — DENIED

Rejected because Least Bandwidth currently selects the lowest playable stream inside the configured range, not the stream nearest to the maximum.

```text
Mode: Least Bandwidth
Requested: 480p
Available: 240p, 360p, 720p
```

Expected:

```text
360p
```

### Test 6 — Least Bandwidth fallback upward — DENIED

Rejected as an exact-target test. If all streams are outside the configured range, the supported player may fail rather than silently override an explicit user limit.

```text
Mode: Least Bandwidth
Requested: 144p
Available: 240p, 360p, 720p
```

Expected:

```text
240p
```

### Test 7 — Low-performance first run

```text
Fresh install
Device effective display supports <= 600p
User selects Low performance
```

Expected:

```text
Least Bandwidth enabled
Preferred/default resolution <= display capability
```

### Test 8 — Standard first run

```text
Fresh install
User selects Standard
```

Expected:

```text
Normal SkyTube defaults preserved
```

### Test 9 — Reapply low-performance preset

```text
Current settings:
Best Quality
1080p

User selects Low performance
```

Expected:

```text
Least Bandwidth
resolution recalculated from display
```

### Test 10 — Manual override preserved

```text
User applies Low performance
User manually changes quality afterward
App restarts
```

Expected:

```text
Manual quality change remains
```

### Test 11 — Preset scope

```text
User selects Low performance or Standard
```

Expected:

```text
Only unmetered/metered playback quality and maximum-resolution keys change.
Download and minimum-resolution preferences remain unchanged.
```

### Test 12 — Display capability mapping

```text
Real display size: 1024x600
Supported resolutions: 144p, 240p, 360p, 480p, 720p, ...
```

Expected:

```text
Capability uses min(1024, 600) = 600 physical pixels.
Selected maximum = 480p.
```

### Test 13 — First-run choice is not repeated

```text
No performance-mode value exists.
User selects a mode, then the Activity is recreated or the app restarts.
```

Expected:

```text
The selector is not shown again.
The stored mode remains informational until explicitly selected again; manual quality overrides are preserved.
```

---

## 20. Performance validation

Compare before/after behavior on a low-resource device or equivalent test environment.

Observe or measure:

- cold startup;
- home/feed scrolling;
- video opening time;
- memory pressure;
- thumbnail loading;
- Mix transitions;
- video playback stability.

Reference hardware:

```text
Samsung Galaxy Tab 3 7.0 SM-T210
Android 4.4
1 GB RAM class device
```

Do not claim performance gains without either measurements or a clear observable reduction in work/resources.

---

## 21. README fork section requirements

The README fork section should mention:

- Continuous Mix playback;
- Mix enabled by default on fresh installs;
- Low performance preset;
- existing range-based quality selection;
- old-device compatibility focus;
- independent fork status;
- original SkyTube attribution.

Qualify Continuous Mix as an ExoPlayer-only feature. Do not claim support in Legacy, the official YouTube player, or Chromecast.

Suggested wording direction:

```text
This fork focuses on making SkyTube more practical as a lightweight,
continuous-playback client, especially on older Android hardware.
```

Do not imply endorsement by the upstream SkyTube project.

---

## 22. Non-goals

Do not include unless required elsewhere:

- YouTube Kids backend integration;
- Google account support;
- new recommendation engine;
- new player framework;
- ExoPlayer upgrade;
- Android minSdk increase;
- full UI redesign;
- Material migration;
- dependency modernization;
- background playback rewrite;
- download subsystem rewrite.

---

## 23. Codex implementation workflow

### Phase 1 — Audit only

Before editing code, identify:

1. current default and storage location for Mix playback;
2. current quality-mode preference implementation;
3. all video-resolution-related preferences;
4. exact stream/format-selection logic;
5. whether unavailable resolution already falls back correctly;
6. current Settings architecture;
7. startup/first-run initialization path;
8. how launcher icons are defined;
9. translation resource structure;
10. README structure;
11. likely performance bottlenecks on API 19 / low-memory devices.

Do not modify code in this phase.

### Phase 2 — Mix default

Change fresh-install default to ON.

Preserve stored values.

Build and test.

### Phase 3 — Resolution fallback — DENIED

The supplied screenshot was verified as a Legacy-player failure. Continuous Mix is not supported in Legacy, and ExoPlayer already selects available streams within the configured minimum/maximum range.

Do not implement the exact-target/nearest-direction algorithm and do not change player dependencies. Retain the existing `StreamSelectionPolicy` behavior and its unit tests.

### Phase 4 — Performance mode

Implement:

- first-run selector;
- Standard mode;
- Low performance mode;
- Settings option to reapply presets.

Reuse existing quality preferences.

### Phase 5 — Performance improvements

Make only small, justified optimizations.

For every optimization report:

```text
problem
change
expected benefit
risk
files changed
```

### Phase 6 — Translations

Review fork-added strings in English and pt-BR.

Avoid unrelated translation changes.

### Phase 7 — Branding

Integrate the new launcher icon assets.

Do not invent final artwork inside code changes if the icon artwork has not yet been supplied.

### Phase 8 — README

Add the fork-specific section while preserving upstream attribution and licensing information.

---

## 24. Definition of done

```text
[ ] Mix is ON by default on fresh installs.
[ ] Existing Mix preference values are preserved.
[-] DENIED: exact-target resolution fallback is excluded.
[ ] Existing minimum/maximum range selection remains unchanged and covered by tests.
[ ] First-run performance-mode popup exists.
[ ] Standard mode preserves normal defaults.
[ ] Low-performance mode sets Least Bandwidth.
[ ] Low-performance mode chooses default resolution <= device capability.
[ ] Performance mode can be reapplied from Settings.
[ ] Manual quality changes after preset application are preserved.
[ ] Fork-added translations were reviewed.
[ ] README contains a dedicated fork section.
[ ] Upstream attribution is preserved.
[ ] New launcher icon assets/resources are integrated.
[ ] Performance review for old devices was completed.
[ ] Any performance changes are justified and documented.
[ ] API 19 compatibility remains intact.
[ ] No unnecessary dependency upgrades were introduced.
[ ] OSS build succeeds.
```

---

## 25. Initial Codex prompt

```text
Read GENERAL_FIXES_AND_UPDATES.md completely.

Do not modify code yet.

Audit the current SkyTube fork and produce an implementation map for every item in this document.

Specifically identify:

1. the current default and storage location for Continuous playback with Mix;
2. all quality-mode and resolution preferences;
3. the exact stream/format-selection logic;
4. confirm the existing range-based selection behavior without redesigning it;
5. the correct location for a first-run performance-mode dialog;
6. how Settings preferences are implemented;
7. how device display resolution can be obtained while preserving API 19 compatibility;
8. launcher icon resource locations;
9. translation resource locations;
10. README structure;
11. likely performance bottlenecks for Android 4.4 / 1 GB RAM class devices.

For each item provide exact file paths, classes and relevant methods.

Then propose the smallest implementation plan possible.

Constraints:
- preserve API 19 compatibility;
- do not upgrade dependencies unless absolutely necessary;
- do not replace the current player;
- do not modify unrelated code;
- reuse existing preferences and quality-selection abstractions;
- do not implement the denied exact-target resolution fallback;
- preserve existing user preferences across updates;
- treat Low performance mode as a preset, not a permanent enforcement layer.

Stop after analysis and implementation planning.
```

---

## 26. Follow-up Codex prompt

```text
Implement GENERAL_FIXES_AND_UPDATES.md incrementally.

Start only with:

1. Mix enabled by default on fresh installs while preserving stored user values;
2. first-run performance-mode selection;
3. Low performance preset;
4. Standard preset;
5. Settings option to reapply either preset.

Do not implement resolution fallback, performance optimizations, icon changes, translations, or README updates yet.

Reuse existing quality preferences.

For Low performance mode:
- set the current quality-selection mode to Least Bandwidth;
- detect the device display capability using API-19-compatible APIs;
- set the playback maximum resolution to the closest supported value less than or equal to the device display capability;
- apply the preset to unmetered and metered playback settings, but not download or minimum-resolution settings;
- do not continuously enforce the preset after it has been applied.

Build the OSS debug variant and report:
- files changed;
- preferences changed;
- detected/default resolution logic;
- any API 19 compatibility concerns.
```
