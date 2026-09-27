# Feature Specification: Continuous Playback with YouTube Mix

## 1. Objective

Implement an optional feature named:

**Reprodução contínua com Mix**

English internal name suggestion:

`Continuous playback with Mix`

The feature should allow SkyTube to continue playback automatically after a standalone video finishes by using that video's YouTube Mix as the source of subsequent videos.

The feature must NOT replace, alter, or interfere with SkyTube's existing continuous playback behavior for playlists or any other playback context that already has a defined next video.

The implementation must remain compatible with:

- Android API 19 / Android 4.4
- SkyTube OSS flavor
- Existing SkyTube playback architecture
- Existing ExoPlayer version unless modification is absolutely necessary

Do not introduce a newer player dependency only for this feature.

Implementation scope clarification (2026-09-27): Continuous Mix and automatic playlist continuation are supported only by the local ExoPlayer (`YouTubePlayerV2Fragment`). The Legacy `VideoView` player is explicitly unsupported: it must not initialize or resolve a continuous queue, receive the completion callback, or expose the Mix toggle. The official YouTube player and Chromecast keep their existing behavior.

---

## 2. Core behavior

There are two distinct playback scenarios.

### Scenario A: Video already belongs to a playlist or another continuous playback context

Existing behavior must remain unchanged.

Example:

```text
Playlist:
Video A
Video B
Video C
```

If Video A finishes:

```text
Video A
  ↓
Video B
```

The new Mix feature must NOT affect this flow.

Even if "Reprodução contínua com Mix" is disabled, existing playlist continuous playback must continue working exactly as it does today.

Rule:

```text
Existing playback queue/context takes precedence over Mix.
```

### Scenario B: Standalone video

A standalone video is a video opened without an existing playback context containing a defined next video.

Examples:

- video opened from search results;
- video opened from a channel page if SkyTube does not currently treat that page as a playback queue;
- video opened from Featured;
- video opened from a direct URL;
- video opened from another screen where no native continuous queue exists.

If:

```text
Reprodução contínua com Mix = ENABLED
```

SkyTube should attempt to obtain the YouTube Mix associated with the current video.

Conceptually:

```text
Current video:
VIDEO_ID

Potential Mix:
RD + VIDEO_ID
```

Example:

```text
Current video ID:
abc123

Mix playlist candidate:
RDabc123
```

The application should NOT assume blindly that this ID always works.

It must attempt to resolve the Mix through SkyTube's existing playlist/extractor infrastructure.

---

## 3. Settings

Add a persistent global setting:

```text
Reprodução contínua com Mix
```

Default for fresh installs:

```text
ON
```

If the preference already exists, preserve its stored value during updates. Never overwrite an existing user's choice.

The setting should use SkyTube's existing preference mechanism and SharedPreferences architecture.

Implemented preference key:

```text
pref_key_continuous_mix_playback
```

Do not introduce a separate preferences subsystem.

---

## 4. Settings UI

Add the option to the appropriate playback/video settings section.

UI:

```text
[ ] Reprodução contínua com Mix
```

Suggested description:

```text
Reproduz automaticamente vídeos do Mix do YouTube após o término de um vídeo avulso.
```

Behavior:

```text
checked   -> enabled
unchecked -> disabled
```

The value must persist after restarting the application.

---

## 5. Video-side menu option

When a video is currently playing in ExoPlayer, add the same option to the existing lateral/dropdown video menu. Hide it for Legacy, the official player, Chromecast, and normal playlist playback.

Label:

```text
Reprodução contínua com Mix
```

The menu option should behave as a toggle.

Example:

```text
✓ Reprodução contínua com Mix
```

or use whatever checked/toggle pattern is already used elsewhere in SkyTube.

The video-side menu should modify the same persistent preference used by Settings.

There must NOT be two independent values.

Conceptually:

```text
Settings toggle
       ↕
Shared preference
       ↕
Video menu toggle
```

Changing one changes the effective state of the other.

---

## 6. When the video-side option should appear

The Mix option should only be relevant for standalone videos.

If the current video already belongs to an existing playback sequence that SkyTube knows how to continue automatically, the Mix toggle should not affect that playback.

Preferred behavior:

Either:

1. hide the option;

or, if hiding would be inconsistent with the existing menu implementation:

2. show it disabled.

Preferred option:

```text
Hide it when an existing playlist/continuous playback context is active.
```

Example:

```text
Standalone video
----------------
Playback speed
Quality
Reprodução contínua com Mix
...

Playlist video
--------------
Playback speed
Quality
...
```

The exact UI approach should follow existing SkyTube menu conventions.

---

## 7. Playback priority

The decision logic when a video finishes must follow this exact priority:

```text
1. Does the current playback context already have a next video?
      YES
        -> use existing SkyTube behavior
        -> DO NOT use Mix

      NO
        ↓

2. Is "Reprodução contínua com Mix" enabled?
      NO
        -> stop playback normally

      YES
        ↓

3. Is there already a valid Mix queue for this playback session?
      YES
        -> play next valid item

      NO
        ↓

4. Resolve/create a Mix from the current video.
      SUCCESS
        -> play next valid Mix item

      FAILURE
        -> stop playback normally
```

Existing continuous playback must always take precedence.

---

## 8. Mix resolution

For a standalone video with:

```text
videoId = CURRENT_VIDEO_ID
```

first attempt to resolve:

```text
playlistId = "RD" + videoId
```

Use existing SkyTube/NewPipeExtractor playlist mechanisms wherever possible.

Do not implement raw YouTube HTTP scraping directly inside the player unless absolutely necessary.

Preferred architecture:

```text
Player
   ↓
Mix resolver/service
   ↓
Existing playlist/extractor layer
   ↓
Mix result
```

The player should not contain YouTube extraction logic.

---

## 9. Important assumption about RD playlist IDs

The `RD<videoId>` mechanism is an implementation detail of YouTube and not a guaranteed public API.

Therefore:

DO NOT write code based on the assumption:

```java
"RD" + videoId always returns a valid playlist
```

Instead:

```java
String mixId = "RD" + videoId;

tryResolvePlaylist(mixId);
```

If resolution fails:

```text
do nothing
stop normally
```

No error dialog is required for normal Mix lookup failure.

The feature should fail gracefully.

---

## 10. Mix queue

After resolving the Mix, maintain it as a playback context/queue.

Conceptual model:

```text
MixPlaybackContext
- sourceVideoId
- mixPlaylistId
- videos
- currentIndex
- continuation/page token if applicable
```

Do not necessarily create this exact class.

First inspect SkyTube's existing playlist queue/playback state architecture.

Reuse an existing playback/playlist context if possible.

Avoid duplicating queue logic.

---

## 11. Finding the current video inside the Mix

When the Mix is resolved, the source/current video may be included in the returned list.

Example:

```text
Mix:
0 Current video
1 Video B
2 Video C
3 Video D
```

SkyTube must not replay the current video.

Find the current video's ID inside the Mix.

Then start from the following valid item.

Conceptually:

```java
int currentIndex = findVideoIndex(currentVideoId);

int nextIndex = currentIndex + 1;
```

If the current video does not exist in the returned Mix:

Use the first valid video that is not the current video.

---

## 12. Duplicate protection

Do not replay the current video.

Also avoid immediately replaying previously consumed videos within the same Mix session.

Maintain a lightweight set of played IDs for the Mix session.

Conceptually:

```java
Set<String> playedVideoIds;
```

Before starting a candidate:

```text
candidate ID == current video ID
    -> skip

candidate already played in this Mix session
    -> skip
```

This only needs to exist for the current playback session.

Persistent history is not required for this feature.

---

## 13. Pagination / continuation

YouTube Mixes may be returned incrementally.

If SkyTube reaches the end of the currently loaded Mix and the extractor exposes continuation/pagination support:

```text
Current loaded Mix exhausted
        ↓
request next page
        ↓
append items
        ↓
continue playback
```

Reuse the existing playlist pagination implementation if one exists.

Do not implement separate Mix pagination if normal playlists already solve this problem.

If continuation fails:

```text
stop playback normally
```

---

## 14. Loading strategy

Avoid loading the Mix when the video begins unless necessary.

Preferred behavior:

```text
standalone video begins
        ↓
normal playback
        ↓
near end OR playback ends
        ↓
resolve Mix
```

However, if asynchronous Mix resolution introduces an undesirable pause between videos, prefetching is acceptable.

Recommended optimized version:

```text
standalone video starts
        ↓
Mix enabled?
        ↓ yes
resolve Mix asynchronously
        ↓
cache playback queue
        ↓
video ends
        ↓
next video starts immediately
```

Important:

Mix loading must never block:

```text
UI thread
video playback
video controls
```

Use the project's existing asynchronous execution mechanism.

---

## 15. Session behavior

Once a standalone video starts a Mix:

```text
Video A
 ↓
Mix item B
 ↓
Mix item C
 ↓
Mix item D
```

B, C and D should be treated as part of the same Mix playback context.

Do NOT generate a completely new Mix on every video transition.

Correct:

```text
A
 ↓
Mix(A)
 ↓
B
 ↓
next item from Mix(A)
 ↓
C
```

Incorrect:

```text
A
 ↓
Mix(A)
 ↓
B
 ↓
Mix(B)
 ↓
C
 ↓
Mix(C)
```

Using a new Mix for every item could cause:

- unstable recommendations;
- duplicated videos;
- loops;
- unnecessary extractor requests.

The Mix should behave like a playlist once it has been resolved.

---

## 16. User manually chooses another video

If the user leaves the current Mix by manually selecting another standalone video, the old Mix session should be discarded.

Example:

```text
A
 ↓
Mix(A)
 ↓
B

User manually taps Video X

X
 ↓
Mix(X)
```

The new manually selected video becomes the new root/source video.

---

## 17. User manually opens a playlist

If the user enters a normal playlist while a Mix session is active:

```text
Mix context
   ↓
discard/suspend Mix
   ↓
normal playlist context
```

Normal SkyTube playlist behavior takes precedence.

---

## 18. Setting disabled during playback

Example:

```text
Mix playback enabled
A -> B -> C

User disables:
Reprodução contínua com Mix
```

Expected behavior:

The currently playing video continues.

When it ends:

```text
no automatic Mix continuation
```

Do not immediately stop the current video.

---

## 19. Setting enabled during playback

Example:

```text
Standalone video currently playing
Mix disabled

User enables Mix
```

Expected:

SkyTube may begin resolving the Mix immediately or wait until playback approaches completion.

When the video ends:

```text
continue through Mix
```

---

## 20. Existing playlists must ignore the Mix setting

This is a mandatory compatibility rule.

Example:

```text
Mix setting = OFF

User opens playlist:
A
B
C
```

Expected:

```text
A -> B -> C
```

Existing continuous playback remains functional.

Likewise:

```text
Mix setting = ON
```

must not cause:

```text
Playlist A
 ↓
YouTube Mix
```

The next item must still be:

```text
Playlist B
```

---

## 21. Failure behavior

Failures must be silent/non-destructive.

Possible failures:

- Mix does not exist;
- extractor cannot parse Mix;
- network unavailable;
- YouTube changes internal Mix behavior;
- playlist returns empty;
- all returned videos are invalid;
- continuation fails.

Expected:

```text
Current video ends
        ↓
Mix unavailable
        ↓
normal playback completion
```

Optional:

Write a debug log entry.

Example:

```text
Mix autoplay: failed to resolve Mix for <videoId>
```

Do not display intrusive errors to the user.

---

## 22. Interaction with Video Blocker

For the first implementation, preserve the behavior of the normal SkyTube video filtering architecture.

Do not intentionally bypass Video Blocker.

If playlist extraction normally passes items through an existing blocker/filter, Mix results should use the same pipeline.

Avoid adding a separate filtering implementation unless required.

Possible future feature:

```text
Mix autoplay restricted to whitelisted channels
```

This is NOT required for the first version.

---

## 23. Shorts

No special handling is required initially.

If Mix returns Shorts and SkyTube normally supports them, keep the existing behavior.

Filtering Shorts may be added later as an independent feature.

---

## 24. UI state source of truth

There must be one source of truth:

```text
SharedPreferences / existing preferences storage
```

Both:

```text
Settings screen
```

and:

```text
Video side/dropdown menu
```

must read and write the same preference.

Do not store a separate player-only copy permanently.

Temporary cached state is acceptable but must reflect preference updates.

---

## 25. Suggested architecture

Do not implement this architecture blindly.

First inspect existing SkyTube abstractions.

Conceptually the feature may contain:

```text
MixPlaybackManager
or
MixResolver
```

Responsibilities:

```text
- determine Mix playlist ID
- request playlist through existing extractor
- maintain Mix queue/session
- return next candidate
- handle continuation
- prevent duplicate playback
```

The actual player should primarily ask:

```java
Video next = playbackContext.getNextVideo();
```

rather than containing extraction/network logic.

---

## 26. Detection of standalone playback

This is critical.

Before implementing anything, inspect how SkyTube currently identifies:

- playlist playback;
- current playlist;
- playlist index;
- automatically queued next video;
- video launched from lists;
- explicit playlist context.

Do not use a fragile heuristic such as:

```java
if (playlistId == null)
```

unless that is proven to reflect the existing architecture correctly.

Define a reusable method conceptually equivalent to:

```java
boolean hasExistingContinuousPlaybackContext()
```

Expected semantics:

```text
true:
SkyTube already knows the intended next video.

false:
The video is effectively standalone.
```

---

## 27. Player completion event

Locate the existing callback/event used when video playback reaches the end.

The supported implementation path uses ExoPlayer. Legacy must not register an equivalent completion callback for this feature.

Likely conceptually equivalent to:

```java
Player.STATE_ENDED
```

Do not add another playback polling loop.

Reuse the existing player state callback.

Desired behavior:

```java
onPlaybackEnded() {
    if (existingContextHasNext()) {
        playExistingNext();
        return;
    }

    if (!isMixAutoplayEnabled()) {
        return;
    }

    playNextFromMixIfAvailable();
}
```

The exact implementation should conform to SkyTube's current player architecture.

---

## 28. Concurrency

Mix loading must handle races.

Examples:

### User leaves video while Mix is loading

```text
A loading Mix...
user opens X
Mix(A) finishes loading
```

Do NOT attach Mix(A) to X.

Results must be associated with the source video/session that requested them.

Possible approach:

```text
sessionId
or
sourceVideoId comparison
```

### User disables Mix while request is running

Request may finish, but playback must not continue automatically if the setting is currently disabled.

Check preference again before automatically starting the next video.

---

## 29. Memory constraints

Target device includes very low-memory Android devices.

Do not preload:

- video streams;
- thumbnails in bulk;
- entire extremely large playlists.

Only playlist metadata required for queue progression should be retained.

Reuse SkyTube's existing playlist data objects.

Avoid introducing:

- large caches;
- additional background services;
- heavyweight dependencies.

---

## 30. Android compatibility

Mandatory:

```text
minSdkVersion must remain 19 or lower/equal to current project value.
```

Do not use APIs requiring Android versions above the current minimum without compatibility handling.

Do not update AndroidX, ExoPlayer, NewPipeExtractor, Gradle, or other dependencies as part of this feature unless the feature cannot reasonably be implemented otherwise.

Dependency upgrades must not be included as incidental cleanup.

---

## 31. Localization

Do not hard-code user-facing strings.

Create string resources.

Portuguese:

```text
Reprodução contínua com Mix
```

Suggested description:

```text
Reproduz automaticamente vídeos do Mix do YouTube após o término de um vídeo avulso.
```

Suggested English:

```text
Continuous playback with Mix
```

Description:

```text
Automatically play videos from the YouTube Mix after a standalone video ends.
```

If the repository requires translations only in default resources, add the base English resource and Portuguese resource following existing conventions.

Do not modify unrelated translations.

---

## 32. Suggested logging

Use the project's existing logging facility.

Useful debug events:

```text
Mix autoplay enabled for video=<id>
Resolving Mix playlist=<id>
Mix resolved items=<count>
Mix next video=<id>
Mix exhausted
Mix continuation requested
Mix resolution failed
Mix discarded due to new playback session
```

Do not log URLs containing sensitive information or excessive response payloads.

---

## 33. Tests

Add tests where the architecture permits.

At minimum validate the decision logic.

### Test 1

```text
Existing playlist has next video
Mix setting OFF
```

Expected:

```text
existing next video plays
```

### Test 2

```text
Existing playlist has next video
Mix setting ON
```

Expected:

```text
existing next video plays
Mix not used
```

### Test 3

```text
Standalone video
Mix setting OFF
```

Expected:

```text
playback ends normally
```

### Test 4

```text
Standalone video
Mix setting ON
Mix resolves successfully
```

Expected:

```text
next Mix video plays
```

### Test 5

```text
Standalone video
Mix setting ON
Mix fails
```

Expected:

```text
playback ends normally
no crash
```

### Test 6

```text
Mix contains current video as first element
```

Expected:

```text
current video is skipped
next Mix item plays
```

### Test 7

```text
Mix contains duplicate previously played item
```

Expected:

```text
duplicate skipped
```

### Test 8

```text
Mix playback active
user selects unrelated video manually
```

Expected:

```text
old Mix context discarded
new video may create its own Mix
```

### Test 9

```text
Mix request running
user changes video
old request completes afterward
```

Expected:

```text
old result ignored
```

### Test 10

```text
Mix active
user disables option
```

Expected:

```text
current video continues
next automatic Mix transition does not happen
```

---

## 34. Manual acceptance tests

Perform on Android 4.4 if possible.

### Standalone video

1. Disable Mix.
2. Open standalone video.
3. Let video end.
4. Confirm playback stops.

Then:

1. Enable Mix.
2. Open standalone video.
3. Let video end.
4. Confirm next Mix video begins.

### Playlist

1. Disable Mix.
2. Open playlist.
3. Play first video.
4. Let it end.
5. Confirm second playlist video starts.

Repeat with Mix enabled.

Result must be identical.

### Video menu

1. Open standalone video.
2. Open video-side menu.
3. Toggle "Reprodução contínua com Mix".
4. Open Settings.
5. Confirm value matches.

Reverse the test:

1. Change value in Settings.
2. Start standalone video.
3. Open video-side menu.
4. Confirm state matches.

### Playlist menu

Open video from a playlist.

Confirm the Mix option:

```text
is hidden
```

or, if architecture strongly favors it:

```text
is disabled and cannot affect playlist playback.
```

---

## 35. Non-goals

Do NOT implement as part of this task:

- YouTube Kids backend;
- account/login support;
- recommendation algorithm;
- custom children's filtering;
- parental controls;
- autoplay timers;
- sleep timers;
- Mix personalization;
- new video player;
- ExoPlayer migration;
- NewPipeExtractor upgrade;
- playlist redesign;
- background playback changes;
- queue UI redesign.

Keep scope strictly limited to Mix-based continuous playback.

---

## 36. First task for the coding agent

Before editing code, inspect the repository and report:

1. Where video playback is controlled.
2. Where ExoPlayer end-of-playback is handled.
3. How normal playlist continuous playback works.
4. How playlist state/current index is stored.
5. Which code loads YouTube playlists.
6. How pagination/continuation is handled.
7. Where preferences are defined and read.
8. Which component creates the video-side/dropdown menu.
9. How the player knows whether a video belongs to a playlist.
10. Whether playlist items pass through Video Blocker.

Do not implement until those points are understood.

---

## 37. Implementation strategy for Codex

Use this sequence.

### Phase 1 — Repository analysis

Do not modify files.

Find the existing implementations for:

```text
player
playlist playback
playlist extraction
preferences
video menu
playback-ended callback
```

Return a short architecture map.

### Phase 2 — Minimal data flow design

Determine whether existing playlist state can represent a Mix.

Prefer:

```text
reuse existing playlist context
```

over:

```text
new independent queue implementation
```

Only introduce a Mix-specific manager if necessary.

### Phase 3 — Preference

Implement:

```text
pref_key_continuous_mix_playback
```

Add Settings UI.

Verify persistence.

### Phase 4 — Video menu

Add toggle to standalone video menu.

Connect it to the same preference.

Hide it for normal playlist playback.

### Phase 5 — Mix resolver

Given:

```text
videoId
```

attempt:

```text
playlistId = "RD" + videoId
```

Resolve using existing playlist/extractor code.

Handle failure silently.

### Phase 6 — Playback integration

At playback completion:

```text
existing queue?
    -> existing behavior

else Mix disabled?
    -> stop

else Mix available?
    -> next Mix item

else
    -> try resolve Mix
    -> play next if successful
```

### Phase 7 — Session safety

Implement:

```text
source video/session validation
duplicate protection
manual navigation invalidation
preference re-check
```

### Phase 8 — Tests

Run existing tests.

Add focused tests.

Build:

```text
OSS debug APK
```

Do not modify unrelated code.

---

## 38. Definition of done

The feature is complete when all of the following are true:

```text
[ ] Global Mix setting exists.
[ ] Fresh installs default Mix to ON while existing stored values are preserved.
[ ] Setting persists after restart.
[ ] Video-side Mix toggle exists.
[ ] Both toggles use same preference.
[ ] Video-side toggle is shown only when appropriate.
[ ] Legacy, official player, and Chromecast do not expose or execute Continuous Mix.
[ ] Existing playlists work unchanged.
[ ] Existing continuous playback works even with Mix disabled.
[ ] Standalone video stops when Mix is disabled.
[ ] Standalone video continues through Mix when enabled.
[ ] Mix uses existing extractor infrastructure.
[ ] Current video is not replayed.
[ ] Previously played Mix items are not immediately repeated.
[ ] Mix survives multiple automatic transitions.
[ ] New manual video invalidates old Mix.
[ ] Network/Mix failures do not crash playback.
[ ] No heavy new dependency is introduced.
[ ] Android API 19 compatibility is preserved.
[ ] OSS build succeeds.
```

---

## 39. Initial Codex prompt

Use the following prompt as the first task:

```text
Read FEATURE_CONTINUOUS_MIX.md completely before making changes.

This repository is SkyTube.

I want to implement the feature described in that document, but do not modify code yet.

First inspect the current repository and produce an implementation map containing:

1. the classes responsible for video playback;
2. where ExoPlayer playback completion is detected;
3. how existing playlist continuous playback selects the next video;
4. how playlist context/current index is represented;
5. how YouTube playlists are resolved through NewPipeExtractor or other existing extraction code;
6. how playlist pagination/continuation works;
7. where application preferences are declared/read;
8. where the menu shown while a video is playing is created;
9. how the player distinguishes standalone videos from playlist videos;
10. whether playlist results are passed through Video Blocker.

For each item provide exact file paths, classes and relevant methods.

Then propose the smallest implementation plan possible.

Important constraints:
- preserve Android API 19 compatibility;
- preserve existing playlist behavior exactly;
- do not upgrade dependencies;
- do not replace ExoPlayer;
- reuse existing queue/playlist abstractions wherever possible;
- do not introduce YouTube HTTP scraping if existing extractor infrastructure can resolve RD<videoId>;
- do not modify unrelated code.

Stop after the analysis and implementation plan. Do not implement yet.
```

---

## 40. Second Codex prompt

After reviewing its architecture analysis:

```text
Implement the feature from FEATURE_CONTINUOUS_MIX.md following the architecture you identified.

Work incrementally.

First implement only:
1. the persistent "Continuous playback with Mix" preference;
2. the Settings toggle;
3. the video-side menu toggle;
4. correct visibility based on whether the current video already has an existing continuous playback context.

Do not implement Mix fetching or autoplay yet.

Run/build the relevant project targets and report the files changed.
```

After validating that part, proceed to Mix resolution and playback integration separately.
