package ai.zalo.kiki.auto.specific.app_handle.webview

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

private val registerMediaSessionScript by lazy {
    """ let video;
        let videoTitle = '';
        let videoDuration = -1;
        function mediaPlay() {
            if (!video) {
                window.Android.log('Video element not found');
                return;
            }
            video.play();
        }
        function mediaPause() {
            if (!video) {
                window.Android.log('Video element not found');
                return;
            }
            video.pause();
        }
        (function() {
                video = document.querySelector('video');
                const injected = window.Android && typeof window.Android.onPlaybackStateChanged === 'function'
                && typeof window.Android.onMediaMetadataLoaded === 'function'
                && typeof window.Android.log === 'function';
                
                if (!injected) return;
                
                if (!video) {
                    window.Android.log('Video element not found');
                    return;
                }
                
                function loadMetadata() {
                    const oldTitle = videoTitle;
                    const oldDuration = videoDuration;
                    videoTitle = document.title;
                    videoDuration = video.duration;
                    return oldTitle != videoTitle || oldDuration != videoDuration;
                }
                
                function sendPlaybackState() {
                    if (loadMetadata()) {
                        sendMediaMetadata();
                    }
                    window.Android.onPlaybackStateChanged(!video.paused, video.currentTime, video.duration);
                }
                
                function sendMediaMetadata() {
                    window.Android.onMediaMetadataLoaded(document.title, video.duration);
                }
                
                video.addEventListener('loadedmetadata', function() {
                    if (loadMetadata()) {
                        sendMediaMetadata();
                    }
                });
                
                video.addEventListener('play', sendPlaybackState);
                video.addEventListener('pause', sendPlaybackState);
                video.addEventListener('timeupdate', sendPlaybackState);
                
                if (video.readyState >= 1) {
                    // The 'loadedmetadata' event has likely already fired.
                    // Let's send the data manually right now.
                    window.Android.log('Video already has metadata. Sending initial state.');
                    sendPlaybackState();
                } else {
                    // Metadata isn't loaded yet. We'll wait for the
                    // 'loadedmetadata' event listener to fire.
                    window.Android.log('Waiting for video metadata to load...');
                }
            })();"""
}
@Parcelize
data class InAppWebData(
    val userAgent: String = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36",
    val onPageFinishedScript: String = registerMediaSessionScript,
    val domStorageEnabled: Boolean = true
) : Parcelable
