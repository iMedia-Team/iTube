package com.kt.apps.video.data.api

import kotlinx.coroutines.flow.StateFlow

interface FirebaseApi {
    data class Data(
        val iTubeNewVersion: String = "",
        val iTubePlayerChooser: String = "",
        val youtubeWebViewData: String = ""
    )
    val data: StateFlow<Data?>
    fun setUp()
}
