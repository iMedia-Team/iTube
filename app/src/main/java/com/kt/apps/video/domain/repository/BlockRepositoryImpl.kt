package com.kt.apps.video.domain.repository

import ai.zalo.kiki.auto.specific.app_handle.webview.InAppWebData
import com.kt.apps.video.data.PlayerType
import com.kt.apps.video.data.source.ConfigurationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.schabi.newpipe.BuildConfig
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class BlockRepositoryImpl(
    configurationDataSource: ConfigurationDataSource
) : BlockRepository, CoroutineScope {
    override val coroutineContext: CoroutineContext by lazy { Dispatchers.Default }
    private val _selectVideoDetailPlayer = configurationDataSource.playerChooser.map { it ->
        // Đi từ cuối tới đầu, lấy giá trị có version bé hơn hoặc bằng current
        // BuildConfig.VERSION_CODE = 5
        // [{3, "origin"},{4, "web"}, {6, "origin" }] => web
        // [{3, "origin"},{4, "web"}] => web
        // [{3, "origin"},{4, "web"}, {5, "origin" }] => origin
        Timber.tag("SelectPlayer").d(it.versionPlayers.joinToString())
        it.versionPlayers.lastOrNull {
            BuildConfig.VERSION_CODE >= it.version
        }?.playerType ?: PlayerType.Origin
    }.shareIn(this, SharingStarted.Eagerly, 1)
    override val pickedVideoDetailPlayer: Flow<PlayerType> = _selectVideoDetailPlayer
    override val youtubeWebData: Flow<InAppWebData> = configurationDataSource.youtubeWebData.shareIn(this, SharingStarted.Lazily, 1)
}
