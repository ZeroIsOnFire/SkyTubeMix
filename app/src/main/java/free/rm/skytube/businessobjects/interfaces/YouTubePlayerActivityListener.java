package free.rm.skytube.businessobjects.interfaces;

import android.view.Menu;

import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;

/**
 * In order for the Cast icon to be shown in the menu bar while playing a video, BaseActivity will have to be notified
 * that the options menu has been created. This interface will allow {@link free.rm.skytube.gui.fragments.YouTubePlayerV1Fragment}
 * and {@link free.rm.skytube.gui.fragments.YouTubePlayerV2Fragment} to do that notification.
 */
public interface YouTubePlayerActivityListener {
	void onOptionsMenuCreated(Menu menu);

	void onVideoPlaybackCompleted(YouTubeVideo video);

	void onManualVideoSelected(String videoId);

	void onPreviousVideoRequested();

	void onNextVideoRequested();

	boolean canNavigateToPreviousVideo();

	boolean canNavigateToNextVideo();
}
