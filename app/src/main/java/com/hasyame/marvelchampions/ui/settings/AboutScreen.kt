package com.hasyame.marvelchampions.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.ui.util.CONTACT_ADDRESS
import com.hasyame.marvelchampions.ui.util.sendContactEmail

/**
 * Who made this and why.
 *
 * Worth a screen rather than a line in Settings: a fan app that grows with one
 * person's collection sets expectations very differently from one that promises
 * to cover everything, and saying so plainly is fairer than letting a player
 * discover it by finding their campaign missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var noMailApp by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .halftone(MaterialTheme.colorScheme.onBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Prose over the halftone needs a box, as on the campaign pages.
            ComicPanel(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.about_story),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }

            ComicPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.about_note_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.about_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            ComicPanel(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.about_no_ads),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            OutlinedButton(
                onClick = { noMailApp = !sendContactEmail(context, state.lastCardSync) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.about_contact)) }

            if (noMailApp) {
                Text(
                    text = stringResource(R.string.settings_no_mail_app, CONTACT_ADDRESS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.labelMedium,
            )

            // Small print, but the part that says what this app is not.
            Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_data_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
