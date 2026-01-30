package com.kt.apps.video.domain.repository

import ai.zalo.kiki.auto.specific.app_handle.webview.InAppWebData
import com.kt.apps.video.data.PlayerType
import kotlinx.coroutines.flow.Flow

interface BlockRepository {
    val pickedVideoDetailPlayer: Flow<PlayerType>
    val youtubeWebData: Flow<InAppWebData>
}
