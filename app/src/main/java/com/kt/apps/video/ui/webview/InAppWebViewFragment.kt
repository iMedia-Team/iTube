package com.kt.apps.video.ui.webview

import ai.zalo.kiki.auto.specific.app_handle.webview.InAppWebData
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.App
import org.schabi.newpipe.databinding.ActivityWebviewBinding
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class InAppWebViewFragment : Fragment() {
    private lateinit var binding: ActivityWebviewBinding
    private val webData by lazy {
        runBlocking {
            withTimeoutOrNull(3.seconds) {
                App.getApp().iTubeIntegration.commonRepository.youtubeWebData.first()
            } ?: InAppWebData()
        }
    }

    private val handleGoBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                isEnabled = false
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        handleIntent(arguments)
        parentFragmentManager.setFragmentResultListener("onNewIntent", this) { _, data ->
            onNewIntent(data)
        }
    }

    private fun onNewIntent(data: Bundle) {
        handleIntent(data)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = webData.domStorageEnabled
            settings.userAgentString = webData.userAgent
            setUpWebChrome()
            setUpMediaSession()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Timber.tag("InAppWebView").d("onPageStarted: $url")
                    binding.progressBar.visibility = View.VISIBLE
                    handleGoBackCallback.isEnabled = view.canGoBack()
                }

                @RequiresApi(Build.VERSION_CODES.M)
                override fun onPageCommitVisible(view: WebView, url: String) {
                    super.onPageCommitVisible(view, url)
                    Timber.tag("InAppWebView").d("onPageCommitVisible: $url")
                    binding.progressBar.visibility = View.GONE
                    handleGoBackCallback.isEnabled = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Timber.tag("InAppWebView").d("onPageFinished: $url")
                    binding.progressBar.visibility = View.GONE
                    handleGoBackCallback.isEnabled = view.canGoBack()
                    if (webData.onPageFinishedScript.isNotBlank()) {
                        evaluateJavascript(webData.onPageFinishedScript, null)
                    }
                }
            }
            handleGoBackCallback.isEnabled = canGoBack()
            requireActivity().onBackPressedDispatcher.addCallback(this@InAppWebViewFragment, handleGoBackCallback)
        }
    }

    private fun getUri(data: Bundle?): Uri? {
        return data?.let { BundleCompat.getParcelable(it, "data", Uri::class.java) }
    }

    private fun handleIntent(data: Bundle?) {
        getUri(data)?.let { url ->
            loadUrl(url.toString())
        } ?: finish() // If no URL is provided, close the activity.
    }

    private fun loadUrl(url: String) {
        binding.webView.loadUrl(url)
    }

    private fun finish() {
        parentFragmentManager.setFragmentResult("finish", bundleOf())
    }

    private fun WebView.setUpMediaSession() {
        val mediaSession = MediaSessionCompat(requireContext(), "WebViewMediaSession")
        var isStatePlaying = false
        var isMediaSessionReleased = false
        var isMediaSessionShouldPause = false
        var isPausedByLifecycle = false
        // We need to pass the MediaSession to our interface
        class WebAppInterface() {
            private val playbackStateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                )

            @Suppress("unused")
            @JavascriptInterface
            fun onPlaybackStateChanged(isPlaying: Boolean, currentTime: Double, duration: Double) {
                if (isMediaSessionReleased) {
                    return
                }
                Timber.tag("InAppWebView").d("onPlaybackStateChanged: $isPlaying, $currentTime, $duration, shouldPause=$isMediaSessionShouldPause")
                isStatePlaying = isPlaying

                val state = if (isPlaying) {
                    PlaybackStateCompat.STATE_PLAYING
                } else {
                    PlaybackStateCompat.STATE_PAUSED
                }

                // Update the playback state
                playbackStateBuilder.setState(state, (currentTime * 1000).toLong(), 1.0f)
                mediaSession.setPlaybackState(playbackStateBuilder.build())

                // This is what makes the notification show up!
                mediaSession.isActive = true
            }

            @Suppress("unused")
            @JavascriptInterface
            fun onMediaMetadataLoaded(title: String, duration: Double) {
                if (isMediaSessionReleased) {
                    return
                }
                Timber.tag("InAppWebView").d("onMediaMetadataLoaded: $title, $duration")
                val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (duration * 1000).toLong())
                    .build()

                mediaSession.setMetadata(metadata)
            }

            @Suppress("unused")
            @JavascriptInterface
            fun log(message: String) {
                Timber.tag("InAppWebView").i(message)
            }
        }
        addJavascriptInterface(WebAppInterface(), "Android")
        fun pauseVideo() {
            evaluateJavascript("mediaPause();", null)
        }

        fun resumeVideo() {
            evaluateJavascript("mediaResume();", null)
        }
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        Timber.tag("InAppWebView").d("Lifecycle: onStart, isPausedByLifecycle=$isPausedByLifecycle")
                        isMediaSessionShouldPause = false
                        this@setUpMediaSession.onResume()
                        this@setUpMediaSession.resumeTimers()
                        if (isPausedByLifecycle) resumeVideo()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        Timber.tag("InAppWebView").d("Lifecycle: onPause, isPlaying=$isStatePlaying")
                        isMediaSessionShouldPause = true
                        if (isStatePlaying) {
                            isPausedByLifecycle = true
                        }
                        pauseVideo()
                        // If it still plays, it likely dues to audio focus gain struggle.
                        // Try pausing media again after a short delay.
                        lifecycleScope.launch(Dispatchers.Main) {
                            delay(2500)
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
                            Timber.tag("InAppWebView").d("Lifecycle: onPostPause, isPlaying=$isStatePlaying")
                            pauseVideo()
                            delay(150)
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
                            this@setUpMediaSession.onPause()
                            this@setUpMediaSession.pauseTimers()
                        }
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        mediaSession.release()
                        isMediaSessionReleased = true
                        lifecycle.removeObserver(this)
                    }
                    else -> {
                    }
                }
            }
        })
    }

    // Variables to manage the fullscreen view
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private fun setUpWebChrome() {
        val webView = binding.webView
        val fullscreenContainer = binding.fullscreenContainer
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback

                // Hide the WebView and show the Fullscreen Container
                webView.animate().alpha(0f).withEndAction {
                    if (customView != null) {
                        webView.visibility = View.GONE
                    }
                }
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(view)

                // Optional: Hide system UI (Status bar, etc.) for immersive experience
                hideSystemUI()
            }

            // Triggered when the content exits fullscreen
            override fun onHideCustomView() {
                if (customView == null) return

                fullscreenContainer.removeView(customView)
                customView = null

                fullscreenContainer.visibility = View.INVISIBLE
                webView.visibility = View.VISIBLE
                webView.alpha = 1f

                // Notify the web page that fullscreen has closed
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null

                // Show system UI again
                showSystemUI()
            }
        }
    }

    // Helper to hide Status Bar/Navigation Bar
    private fun hideSystemUI() {
        val activity = activity ?: return
        WindowCompat.getInsetsController(activity.window, binding.root).hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemUI() {
        val activity = activity ?: return
        WindowCompat.getInsetsController(activity.window, binding.root).show(WindowInsetsCompat.Type.systemBars())
    }
}
