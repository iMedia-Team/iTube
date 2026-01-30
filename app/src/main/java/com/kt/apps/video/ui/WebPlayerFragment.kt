package com.kt.apps.video.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView.setWebContentsDebuggingEnabled
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.kt.apps.video.ITubeIntegration
import com.kt.apps.video.data.OpenVideoDetailData
import com.kt.apps.video.utils.isPipSettingAllowed
import com.kt.apps.video.viewmodel.data.Event
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.YoutubePage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.getSearchQuery
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.getVideoId
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.isYouTubePlay
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.isYouTubeSearch
import kotlinx.coroutines.launch
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.databinding.LayoutWebPlayerBinding
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.util.logOnOpenVideoDetail
import org.schabi.newpipe.util.reportStreamError
import timber.log.Timber

class WebPlayerFragment : Fragment() {
    private var onUserLeaveHintCallback: (() -> Unit)? = null
    private var onNewIntentCallback: (() -> Unit)? = null
    private lateinit var binding: LayoutWebPlayerBinding
    private var currentPlayingListener: YouTubePlayerListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = LayoutWebPlayerBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = LayoutWebPlayerBinding.inflate(layoutInflater)
        initYouTubePlayerView()

        if (!loadVideoFromIntent(getUri(arguments))) {
            binding.youtubePlayerView.openYoutubePage(YoutubePage.Home)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerEnterPictureInPictureMode()
        }

        lifecycleScope.launch {
            ITubeIntegration.instance.commonRepository.registerCommonEvents().collect {
                if (it == Event.HideVideoDetail.Web) {
                    setFragmentResult("finishAndRemoveTask", bundleOf())
                }
            }
        }

        parentFragmentManager.setFragmentResultListener("onNewIntent", this) { _, data ->
            onNewIntent(data)
        }

        parentFragmentManager.setFragmentResultListener("onUserLeaveHint", this) { _, _ ->
            onUserLeaveHint()
        }
    }

    private fun loadVideoFromIntent(data: Uri?): Boolean {
        val youtubePlayerView = binding.youtubePlayerView
        data?.run {
            when {
                isYouTubePlay() -> {
                    val vid = getVideoId() ?: return false
                    var lastId = vid
                    youtubePlayerView.getYouTubePlayerWhenReady(object : YouTubePlayerCallback {
                        override fun onYouTubePlayer(youTubePlayer: YouTubePlayer) {
                            Timber.d("loadVideo: $vid")
                            youTubePlayer.loadVideo(vid, 0f)
                            currentPlayingListener?.also { youTubePlayer.removeListener(it) }
                            val currentPlayingListener = object : AbstractYouTubePlayerListener() {
                                override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {
                                    super.onVideoId(youTubePlayer, videoId)
                                    if (videoId != lastId) {
                                        lastId = videoId
                                        logOnOpenVideoDetail(
                                            OpenVideoDetailData(
                                                0,
                                                "https://www.youtube.com/watch?v=$videoId",
                                                "",
                                                null,
                                                false,
                                                VideoDetailFragment.EXTERNAL_SOURCE_RECOMMEND
                                            ),
                                            false
                                        )
                                    }
                                }

                                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                    super.onError(youTubePlayer, error)
                                    reportStreamError("https://www.youtube.com/watch?v=$lastId")
                                }
                            }
                            this@WebPlayerFragment.currentPlayingListener = currentPlayingListener
                            youTubePlayer.addListener(currentPlayingListener)
                        }
                    })
                    return true
                }

                isYouTubeSearch() -> {
                    youtubePlayerView.openYoutubePage(
                        YoutubePage.Search(
                            searchQuery = getSearchQuery() ?: ""
                        )
                    )
                    return true
                }

                else -> {}
            }
        }
        return false
    }

    private fun initYouTubePlayerView() {
        setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        binding.youtubePlayerView.run {
            val iFramePlayerOptions = IFramePlayerOptions.Builder()
                .controls(1)
                .autoplay(1)
                .rel(1)
                .fullscreen(0)
                .autoPlayNextVideo(true)
                .mediaSession(true)
                .build()
            enableAutomaticInitialization = false
            initialize(
                object : AbstractYouTubePlayerListener() {
                    @SuppressLint("SetTextI18n")
                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: PlayerConstants.PlayerError
                    ) {
                        super.onError(youTubePlayer, error)
                        binding.textErrDesc.text =
                            "Không thể phát video. Vui lòng thử lại sau (${error.name})"
                    }

                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        super.onReady(youTubePlayer)
                        visibility = View.VISIBLE
                    }
                },
                iFramePlayerOptions
            )
        }
    }

    private fun getUri(data: Bundle?): Uri? {
        return data?.let { BundleCompat.getParcelable(it, "data", Uri::class.java) }
    }

    private fun onNewIntent(data: Bundle) {
        onNewIntentCallback?.invoke()
        val action = data.getString("action", "")
        if (action == "request_enter_picture_in_picture") {
            @SuppressLint("NewApi")
            val result = requestEnterPictureInPictureMode()
            if (result && !binding.youtubePlayerView.isPlaying()) {
                binding.youtubePlayerView.getYouTubePlayerWhenReady(object : YouTubePlayerCallback {
                    override fun onYouTubePlayer(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.play()
                    }
                })
            }
        } else {
            val data = getUri(data)
            loadVideoFromIntent(data)
        }
    }

    private fun onUserLeaveHint() {
        Timber.tag("YouTubePlayer").d("onUserLeaveHint")
        onUserLeaveHintCallback?.invoke()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun registerEnterPictureInPictureMode() {
        // ////////////////////
        // Check back pressed
        // ////////////////////
        var isPlaying = binding.youtubePlayerView.isPlaying()
        var isInPictureInPictureMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requireActivity().isInPictureInPictureMode
        } else {
            false
        }

        // Handle callback.enabled by listening pip mode and youtube playing state
        val onBackPressCallback = object : OnBackPressedCallback(isPlaying && !isInPictureInPictureMode) {
            override fun handleOnBackPressed() {
                if (!requestEnterPictureInPictureMode()) {
                    isEnabled = false
                    parentFragmentManager.setFragmentResult("moveTaskToBack", bundleOf("nonRoot" to true))
                }
            }
        }
        val checkOnBackPressState = {
            onBackPressCallback.isEnabled = isPlaying && !isInPictureInPictureMode
        }

        binding.youtubePlayerView.addYouTubePlayerListener(object :
                AbstractYouTubePlayerListener() {
                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    state: PlayerConstants.PlayerState
                ) {
                    isPlaying = state == PlayerConstants.PlayerState.PLAYING
                    checkOnBackPressState()
                }
            })

        requireActivity().addOnPictureInPictureModeChangedListener {
            isInPictureInPictureMode = it.isInPictureInPictureMode
            checkOnBackPressState()
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressCallback)

        // ////////////////////
        // Check leave hint
        // ////////////////////
        onUserLeaveHintCallback = {
            if (isPipSettingAllowed() && onBackPressCallback.isEnabled) {
                requestEnterPictureInPictureMode()
            }
        }
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun requestEnterPictureInPictureMode(): Boolean {
        Timber.tag("YouTubePlayer").d("requestEnterPictureInPictureMode")
        return when {
            isPipSettingAllowed() -> {
                requireActivity().enterPictureInPictureMode()
                true
            }

            else -> {
                false
            }
        }
    }

    private fun isPipSettingAllowed(): Boolean {
        return isPipSettingAllowed(requireContext()) && PlayerHelper.getMinimizeOnExitAction(requireContext()) != PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("YouTubePlayer").d("onDestroy")
        binding.youtubePlayerView.release()
    }
}
