package com.dominikgold.tonka

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dominikgold.tonka.navigation.IncomingShares
import com.dominikgold.tonka.platform.initPlatformUi

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initPlatformUi(this)
        handleShare(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    // Recipe pages shared from a browser open the website import
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let(IncomingShares::share)
    }
}
