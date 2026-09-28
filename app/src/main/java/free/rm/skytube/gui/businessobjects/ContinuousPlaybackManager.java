/*
 * SkyTube
 * Copyright (C) 2026  SkyTube Team
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

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import free.rm.skytube.businessobjects.YouTube.POJOs.CardData;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;
import free.rm.skytube.businessobjects.YouTube.VideoBlocker;
import free.rm.skytube.businessobjects.YouTube.newpipe.NewPipeService;
import free.rm.skytube.businessobjects.YouTube.newpipe.PlaylistPager;

/**
 * Holds the in-memory queue used for normal playlist playback and optional YouTube Mix playback.
 * Network access is synchronous here by design; callers must invoke {@link #getNextVideo(String,
 * boolean)} away from the UI thread.
 */
public final class ContinuousPlaybackManager {
    static final String MIX_PLAYLIST_PREFIX = "RD";

    enum Mode {
        STANDALONE,
        PLAYLIST,
        MIX
    }

    interface PageSource {
        List<YouTubeVideo> getNextPage() throws Exception;
        boolean hasNextPage();
    }

    interface PageSourceFactory {
        PageSource create(String playlistId) throws Exception;
    }

    interface VideoFilter {
        List<YouTubeVideo> filter(List<YouTubeVideo> videos);
    }

    private final PageSourceFactory pageSourceFactory;
    private final VideoFilter videoFilter;
    private final Deque<YouTubeVideo> queuedVideos = new ArrayDeque<>();
    private final Deque<YouTubeVideo> playlistVideosBeforeAnchor = new ArrayDeque<>();
    private final Set<String> playedVideoIds = new HashSet<>();

    private Mode mode;
    private String playlistId;
    private PageSource pageSource;
    private boolean anchorFound;
    private boolean exhausted;

    public ContinuousPlaybackManager(String currentVideoId, @Nullable String normalPlaylistId) {
        this(currentVideoId, normalPlaylistId, ContinuousPlaybackManager::createPageSource,
                ContinuousPlaybackManager::filterVideos);
    }

    ContinuousPlaybackManager(String currentVideoId, @Nullable String normalPlaylistId,
                              PageSourceFactory pageSourceFactory, VideoFilter videoFilter) {
        this.pageSourceFactory = pageSourceFactory;
        this.videoFilter = videoFilter;
        reset(currentVideoId, normalPlaylistId);
    }

    public synchronized boolean isNormalPlaylistContext() {
        return mode == Mode.PLAYLIST;
    }

    public synchronized boolean isMixContext() {
        return mode == Mode.MIX;
    }

    public synchronized void resetStandalone(String currentVideoId) {
        reset(currentVideoId, null);
    }

    private void reset(String currentVideoId, @Nullable String normalPlaylistId) {
        playlistId = normalPlaylistId;
        mode = normalPlaylistId == null ? Mode.STANDALONE : Mode.PLAYLIST;
        pageSource = null;
        anchorFound = false;
        exhausted = false;
        queuedVideos.clear();
        playlistVideosBeforeAnchor.clear();
        playedVideoIds.clear();
        if (currentVideoId != null) {
            playedVideoIds.add(currentVideoId);
        }
    }

    /**
     * Returns the next valid video or {@code null} when playback should stop.
     */
    @Nullable
    public synchronized YouTubeVideo getNextVideo(String currentVideoId,
                                                  boolean mixPlaybackEnabled) throws Exception {
        if (currentVideoId != null) {
            playedVideoIds.add(currentVideoId);
        }

        if (mode == Mode.STANDALONE) {
            if (!mixPlaybackEnabled) {
                return null;
            }
            beginMix(currentVideoId);
        } else if (mode == Mode.MIX && !mixPlaybackEnabled) {
            return null;
        }

        if (mode == Mode.PLAYLIST && !anchorFound) {
            findNormalPlaylistAnchor(currentVideoId);
        } else if (mode == Mode.MIX && !anchorFound) {
            loadInitialMixPage(currentVideoId);
        }

        return pollNextValidVideo(currentVideoId);
    }

    public synchronized boolean hasPreviousPlaylistVideo() {
        return mode == Mode.PLAYLIST && !playlistVideosBeforeAnchor.isEmpty();
    }

    /**
     * Returns the playlist item immediately before the original playback anchor. Runtime history
     * is held by the player activity; this deque only covers items that preceded the first video.
     */
    @Nullable
    public synchronized YouTubeVideo getPreviousPlaylistVideo(String currentVideoId) {
        if (mode != Mode.PLAYLIST) {
            return null;
        }
        while (!playlistVideosBeforeAnchor.isEmpty()) {
            YouTubeVideo candidate = playlistVideosBeforeAnchor.removeLast();
            String candidateId = candidate != null ? candidate.getId() : null;
            if (candidateId == null || candidateId.equals(currentVideoId)) {
                continue;
            }
            playedVideoIds.add(candidateId);
            return candidate;
        }
        return null;
    }

    private void beginMix(String currentVideoId) throws Exception {
        mode = Mode.MIX;
        playlistId = MIX_PLAYLIST_PREFIX + currentVideoId;
        pageSource = pageSourceFactory.create(playlistId);
        anchorFound = false;
        exhausted = false;
        queuedVideos.clear();
    }

    private void findNormalPlaylistAnchor(String currentVideoId) throws Exception {
        ensurePageSource();
        List<YouTubeVideo> videosBeforeAnchor = new ArrayList<>();
        while (!anchorFound && !exhausted) {
            List<YouTubeVideo> page = safePage(pageSource.getNextPage());
            int currentIndex = findVideo(page, currentVideoId);
            if (currentIndex >= 0) {
                anchorFound = true;
                videosBeforeAnchor.addAll(filtered(page.subList(0, currentIndex)));
                enqueueBeforeAnchor(videosBeforeAnchor, currentVideoId);
                enqueueFiltered(page.subList(currentIndex + 1, page.size()));
            } else if (!pageSource.hasNextPage()) {
                exhausted = true;
            } else {
                videosBeforeAnchor.addAll(filtered(page));
            }
        }
    }

    private void loadInitialMixPage(String currentVideoId) throws Exception {
        ensurePageSource();
        List<YouTubeVideo> firstNonEmptyPage = Collections.emptyList();
        while (firstNonEmptyPage.isEmpty() && !exhausted) {
            firstNonEmptyPage = safePage(pageSource.getNextPage());
            if (firstNonEmptyPage.isEmpty() && !pageSource.hasNextPage()) {
                exhausted = true;
            }
        }

        int currentIndex = findVideo(firstNonEmptyPage, currentVideoId);
        if (currentIndex >= 0) {
            enqueueFiltered(firstNonEmptyPage.subList(currentIndex + 1, firstNonEmptyPage.size()));
        } else {
            enqueueFiltered(firstNonEmptyPage);
        }
        anchorFound = true;
    }

    @Nullable
    private YouTubeVideo pollNextValidVideo(String currentVideoId) throws Exception {
        while (true) {
            while (!queuedVideos.isEmpty()) {
                YouTubeVideo candidate = queuedVideos.removeFirst();
                String candidateId = candidate != null ? candidate.getId() : null;
                if (candidateId == null || candidateId.equals(currentVideoId)
                        || playedVideoIds.contains(candidateId)) {
                    continue;
                }
                playedVideoIds.add(candidateId);
                return candidate;
            }

            if (exhausted || pageSource == null || !pageSource.hasNextPage()) {
                exhausted = true;
                return null;
            }

            List<YouTubeVideo> nextPage = safePage(pageSource.getNextPage());
            enqueueFiltered(nextPage);
            if (!pageSource.hasNextPage() && queuedVideos.isEmpty()) {
                exhausted = true;
            }
        }
    }

    private void ensurePageSource() throws Exception {
        if (pageSource == null) {
            pageSource = pageSourceFactory.create(playlistId);
        }
    }

    private void enqueueFiltered(List<YouTubeVideo> videos) {
        queuedVideos.addAll(filtered(videos));
    }

    private void enqueueBeforeAnchor(List<YouTubeVideo> videos, String currentVideoId) {
        for (YouTubeVideo video : videos) {
            if (video != null && video.getId() != null && !video.getId().equals(currentVideoId)) {
                playlistVideosBeforeAnchor.addLast(video);
            }
        }
    }

    private List<YouTubeVideo> filtered(List<YouTubeVideo> videos) {
        List<YouTubeVideo> filtered = videoFilter.filter(safePage(videos));
        return filtered != null ? filtered : Collections.emptyList();
    }

    private static int findVideo(List<YouTubeVideo> videos, String videoId) {
        if (videoId == null) {
            return -1;
        }
        for (int i = 0; i < videos.size(); i++) {
            YouTubeVideo video = videos.get(i);
            if (video != null && videoId.equals(video.getId())) {
                return i;
            }
        }
        return -1;
    }

    private static List<YouTubeVideo> safePage(@Nullable List<YouTubeVideo> videos) {
        return videos != null ? videos : Collections.emptyList();
    }

    private static PageSource createPageSource(String playlistId) throws Exception {
        final PlaylistPager pager = NewPipeService.get().getPlaylistPager(playlistId);
        return new PageSource() {
            @Override
            public List<YouTubeVideo> getNextPage() throws Exception {
                return pager.getNextPageAsVideos();
            }

            @Override
            public boolean hasNextPage() {
                return pager.isHasNextPage();
            }
        };
    }

    private static List<YouTubeVideo> filterVideos(List<YouTubeVideo> videos) {
        if (videos.isEmpty()) {
            return videos;
        }
        List<CardData> cards = new ArrayList<>(videos);
        List<CardData> filteredCards = new VideoBlocker().filter(cards);
        List<YouTubeVideo> filteredVideos = new ArrayList<>(filteredCards.size());
        for (CardData card : filteredCards) {
            if (card instanceof YouTubeVideo) {
                filteredVideos.add((YouTubeVideo) card);
            }
        }
        return filteredVideos;
    }
}
