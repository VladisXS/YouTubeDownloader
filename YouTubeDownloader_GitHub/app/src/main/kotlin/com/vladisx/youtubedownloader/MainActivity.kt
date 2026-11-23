package com.vladisx.youtubedownloader

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vladisx.youtubedownloader.download.DownloadService
import com.vladisx.youtubedownloader.ui.LoginScreen
import com.vladisx.youtubedownloader.ui.MainScreen
import com.vladisx.youtubedownloader.ui.MainViewModel
import com.vladisx.youtubedownloader.ui.theme.CosmicBackdrop
import com.vladisx.youtubedownloader.ui.theme.YtDlTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* progress still works without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        askForNotificationPermission()
        handleShareIntent(intent)

        setContent {
            YtDlTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val download by viewModel.downloadState.collectAsStateWithLifecycle()
                val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
                var showLogin by rememberSaveable { mutableStateOf(false) }

                if (showLogin) {
                    LoginScreen(
                        onFinished = { success ->
                            showLogin = false
                            viewModel.onLoginFinished(success)
                        },
                        onClose = { showLogin = false },
                    )
                    return@YtDlTheme
                }

                CosmicBackdrop()

                MainScreen(
                    ui = ui,
                    downloadState = download,
                    onUrlChange = viewModel::onUrlChange,
                    onPasteRequested = viewModel::pasteAndFetch,
                    onKindChange = viewModel::onKindChange,
                    onAudioCodecChange = viewModel::onAudioCodecChange,
                    onQualityChange = viewModel::onQualityChange,
                    onDownload = {
                        viewModel.startDownload { DownloadService.start(this) }
                    },
                    onCancel = {
                        viewModel.cancelDownload()
                        DownloadService.cancel(this)
                    },
                    onDismissResult = viewModel::dismissResult,
                    signedIn = signedIn,
                    onLoginClick = { showLogin = true },
                    onSignOutClick = viewModel::signOut,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("""https?://\S+""").find(shared)?.value ?: shared
        viewModel.submitSharedUrl(url)
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
