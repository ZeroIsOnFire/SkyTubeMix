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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;

class ContinuousPlaybackManagerTest {
    private static final ContinuousPlaybackManager.VideoFilter NO_FILTER = videos -> videos;

    @Test
    void normalPlaylistContinuesWhenMixIsDisabled() throws Exception {
        RecordingFactory factory = factory("PL", pages(page("A", "B")));
        ContinuousPlaybackManager manager = manager("A", "PL", factory);

        assertEquals("B", manager.getNextVideo("A", false).getId());
        assertEquals(Collections.singletonList("PL"), factory.requestedIds);
    }

    @Test
    void normalPlaylistTakesPriorityWhenMixIsEnabled() throws Exception {
        RecordingFactory factory = factory("PL", pages(page("A", "B")));
        ContinuousPlaybackManager manager = manager("A", "PL", factory);

        assertEquals("B", manager.getNextVideo("A", true).getId());
        assertFalse(factory.requestedIds.contains("RDA"));
    }

    @Test
    void standaloneStopsWhenMixIsDisabled() throws Exception {
        RecordingFactory factory = new RecordingFactory(Collections.emptyMap());
        ContinuousPlaybackManager manager = manager("A", null, factory);

        assertNull(manager.getNextVideo("A", false));
        assertTrue(factory.requestedIds.isEmpty());
    }

    @Test
    void standaloneResolvesMixWhenEnabled() throws Exception {
        RecordingFactory factory = factory("RDA", pages(page("A", "B")));
        ContinuousPlaybackManager manager = manager("A", null, factory);

        assertEquals("B", manager.getNextVideo("A", true).getId());
        assertEquals(Collections.singletonList("RDA"), factory.requestedIds);
    }

    @Test
    void mixResolutionFailureIsReportedToCallerForSilentHandling() {
        ContinuousPlaybackManager.PageSourceFactory factory = playlistId -> {
            throw new IllegalStateException("unavailable");
        };
        ContinuousPlaybackManager manager = new ContinuousPlaybackManager("A", null, factory, NO_FILTER);

        assertThrows(IllegalStateException.class, () -> manager.getNextVideo("A", true));
    }

    @Test
    void currentVideoAtStartOfMixIsSkipped() throws Exception {
        ContinuousPlaybackManager manager = manager("A", null,
                factory("RDA", pages(page("A", "B", "C"))));

        assertEquals("B", manager.getNextVideo("A", true).getId());
    }

    @Test
    void previouslyPlayedMixVideosAreSkipped() throws Exception {
        ContinuousPlaybackManager manager = manager("A", null,
                factory("RDA", pages(page("A", "B", "A", "B", "C"))));

        assertEquals("B", manager.getNextVideo("A", true).getId());
        assertEquals("C", manager.getNextVideo("B", true).getId());
    }

    @Test
    void manualSelectionDiscardsOldMixSession() throws Exception {
        Map<String, List<List<YouTubeVideo>>> sources = new HashMap<>();
        sources.put("RDA", pages(page("A", "B")));
        sources.put("RDX", pages(page("X", "Y")));
        RecordingFactory factory = new RecordingFactory(sources);
        ContinuousPlaybackManager manager = manager("A", null, factory);

        assertEquals("B", manager.getNextVideo("A", true).getId());
        manager.resetStandalone("X");

        assertEquals("Y", manager.getNextVideo("X", true).getId());
        assertEquals(Arrays.asList("RDA", "RDX"), factory.requestedIds);
    }

    @Test
    void disablingActiveMixStopsAtCurrentVideo() throws Exception {
        ContinuousPlaybackManager manager = manager("A", null,
                factory("RDA", pages(page("A", "B", "C"))));

        assertEquals("B", manager.getNextVideo("A", true).getId());
        assertNull(manager.getNextVideo("B", false));
    }

    @Test
    void playlistAndMixContinueAcrossPages() throws Exception {
        ContinuousPlaybackManager playlist = manager("A", "PL",
                factory("PL", pages(page("A"), page("B"))));
        ContinuousPlaybackManager mix = manager("A", null,
                factory("RDA", pages(page("A"), page("B"))));

        assertEquals("B", playlist.getNextVideo("A", true).getId());
        assertEquals("B", mix.getNextVideo("A", true).getId());
    }

    @Test
    void exhaustedNormalPlaylistNeverFallsBackToMix() throws Exception {
        RecordingFactory factory = factory("PL", pages(page("A")));
        ContinuousPlaybackManager manager = manager("A", "PL", factory);

        assertNull(manager.getNextVideo("A", true));
        assertFalse(factory.requestedIds.contains("RDA"));
    }

    @Test
    void firstValidVideoIsUsedWhenSourceIsMissingFromMix() throws Exception {
        ContinuousPlaybackManager manager = manager("A", null,
                factory("RDA", pages(page("X", "Y"))));

        assertEquals("X", manager.getNextVideo("A", true).getId());
    }

    @Test
    void filteredVideosAreSkipped() throws Exception {
        ContinuousPlaybackManager.VideoFilter filter = videos -> {
            List<YouTubeVideo> result = new ArrayList<>();
            for (YouTubeVideo video : videos) {
                if (!"B".equals(video.getId())) {
                    result.add(video);
                }
            }
            return result;
        };
        RecordingFactory factory = factory("RDA", pages(page("A", "B", "C")));
        ContinuousPlaybackManager manager = new ContinuousPlaybackManager("A", null, factory, filter);

        assertEquals("C", manager.getNextVideo("A", true).getId());
    }

    private static ContinuousPlaybackManager manager(String currentVideoId, String playlistId,
                                                       RecordingFactory factory) {
        return new ContinuousPlaybackManager(currentVideoId, playlistId, factory, NO_FILTER);
    }

    private static RecordingFactory factory(String playlistId, List<List<YouTubeVideo>> pages) {
        Map<String, List<List<YouTubeVideo>>> sources = new HashMap<>();
        sources.put(playlistId, pages);
        return new RecordingFactory(sources);
    }

    @SafeVarargs
    private static List<List<YouTubeVideo>> pages(List<YouTubeVideo>... pages) {
        return Arrays.asList(pages);
    }

    private static List<YouTubeVideo> page(String... ids) {
        List<YouTubeVideo> result = new ArrayList<>();
        for (String id : ids) {
            result.add(video(id));
        }
        return result;
    }

    private static YouTubeVideo video(String id) {
        return new YouTubeVideo(id, id, null, 60,
                new YouTubeChannel("channel", "Channel"), 0, null, false, "");
    }

    private static final class RecordingFactory implements ContinuousPlaybackManager.PageSourceFactory {
        private final Map<String, List<List<YouTubeVideo>>> sources;
        private final List<String> requestedIds = new ArrayList<>();

        private RecordingFactory(Map<String, List<List<YouTubeVideo>>> sources) {
            this.sources = sources;
        }

        @Override
        public ContinuousPlaybackManager.PageSource create(String playlistId) {
            requestedIds.add(playlistId);
            List<List<YouTubeVideo>> pages = sources.get(playlistId);
            if (pages == null) {
                throw new IllegalArgumentException("Missing source " + playlistId);
            }
            return new FakePageSource(pages);
        }
    }

    private static final class FakePageSource implements ContinuousPlaybackManager.PageSource {
        private final List<List<YouTubeVideo>> pages;
        private int index;

        private FakePageSource(List<List<YouTubeVideo>> pages) {
            this.pages = pages;
        }

        @Override
        public List<YouTubeVideo> getNextPage() {
            if (!hasNextPage()) {
                return Collections.emptyList();
            }
            return pages.get(index++);
        }

        @Override
        public boolean hasNextPage() {
            return index < pages.size();
        }
    }
}
