package com.kt.apps.video.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.kt.apps.video.ui.webview.InAppWebViewFragment
import com.kt.apps.video.utils.isPipSettingAllowed
import org.schabi.newpipe.player.helper.PlayerHelper
import timber.log.Timber

class WebPlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Result set for child fragment
        supportFragmentManager.setFragmentResultListener("finish", this) { _, _ ->
            finish()
        }

        supportFragmentManager.setFragmentResultListener("moveTaskToBack", this) { _, bundle ->
            val nonRoot = bundle.getBoolean("nonRoot", true)
            moveTaskToBack(nonRoot)
        }

        supportFragmentManager.setFragmentResultListener("finishAndRemoveTask", this) { _, _ ->
            finishAndRemoveTask()
        }

        val fragment = when (intent?.getBooleanExtra("isWebPlayer", true)) {
            true -> WebPlayerFragment()
            false -> InAppWebViewFragment()
            null -> WebPlayerFragment()
        }
        fragment.arguments = bundleOf(
            "action" to intent.action,
            "data" to intent.data
        )
        val layout = FrameLayout(this)
        layout.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        layout.id = View.generateViewId()
        setContentView(layout)
        supportFragmentManager.beginTransaction()
            .replace(layout.id, fragment)
            .commit()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        supportFragmentManager.setFragmentResult(
            "onNewIntent",
            bundleOf(
                "action" to intent?.action,
                "data" to intent?.data
            )
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        supportFragmentManager.setFragmentResult("onUserLeaveHint", bundleOf())
    }
}
