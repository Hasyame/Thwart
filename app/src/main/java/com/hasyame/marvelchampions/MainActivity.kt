package com.hasyame.marvelchampions

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.core.designsystem.theme.MarvelChampionsTheme
import com.hasyame.marvelchampions.domain.deeplink.MarvelCdbDeckUrl
import com.hasyame.marvelchampions.domain.model.ThemeChoice
import com.hasyame.marvelchampions.ui.MarvelChampionsApp
import com.hasyame.marvelchampions.ui.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity. It extends [AppCompatActivity] rather than ComponentActivity
 * because per-app language selection goes through
 * `AppCompatDelegate.setApplicationLocales`, which needs the AppCompat delegate to
 * apply without an app restart below API 33.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var sharedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The top bar is now solid red and runs under the status bar, so the
        // status icons have to be light in both themes. The default picks them
        // from the theme, which puts dark icons on red in light mode.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        sharedLink = extractDeckLink(intent)

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val choice by themeViewModel.themeChoice.collectAsStateWithLifecycle()

            MarvelChampionsTheme(
                darkTheme = when (choice) {
                    ThemeChoice.LIGHT -> false
                    ThemeChoice.DARK -> true
                    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                },
            ) {
                MarvelChampionsApp(
                    sharedLink = sharedLink,
                    onSharedLinkHandled = { sharedLink = null },
                )
            }
        }
    }

    /**
     * The activity is `singleTop`, so a second share while it is already open
     * arrives here rather than through [onCreate].
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeckLink(intent)?.let { sharedLink = it }
    }

    /**
     * Pulls a MarvelCDB deck link out of a share or a tapped link.
     *
     * `ACTION_SEND` carries free text from a browser's share sheet;
     * `ACTION_VIEW` carries the URL itself. Both are validated by the parser, so
     * text that merely mentions marvelcdb.com without a deck is ignored rather
     * than opening an empty importer.
     */
    private fun extractDeckLink(intent: Intent?): String? {
        val candidate = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        return candidate?.takeIf { MarvelCdbDeckUrl.isMarvelCdbLink(it) }
    }
}
