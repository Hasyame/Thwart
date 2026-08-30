package com.hasyame.marvelchampions.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.sync.CardSyncState
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.model.ThemeChoice
import com.hasyame.marvelchampions.ui.util.CONTACT_ADDRESS
import com.hasyame.marvelchampions.data.diagnostics.CrashLog
import com.hasyame.marvelchampions.ui.util.sendContactEmail
import com.hasyame.marvelchampions.ui.util.sendCrashEmail
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCollection: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.destination_settings)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_collection)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_collection_summary))
                },
                modifier = Modifier.clickable(onClick = onOpenCollection),
            )
            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_card_language),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            )
            Text(
                text = stringResource(R.string.settings_card_language_summary),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardLocale.entries.forEach { locale ->
                    FilterChip(
                        selected = state.cardLocale == locale,
                        onClick = { viewModel.setCardLocale(locale) },
                        label = {
                            Text(
                                when (locale) {
                                    CardLocale.FRENCH -> stringResource(R.string.language_french)
                                    CardLocale.ENGLISH -> stringResource(R.string.language_english)
                                },
                            )
                        },
                    )
                }
            }
            HorizontalDivider()

            AppLanguageSection()
            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = state.themeChoice == choice,
                        onClick = { viewModel.setThemeChoice(choice) },
                        label = {
                            Text(
                                when (choice) {
                                    ThemeChoice.SYSTEM ->
                                        stringResource(R.string.settings_theme_system)

                                    ThemeChoice.LIGHT ->
                                        stringResource(R.string.settings_theme_light)

                                    ThemeChoice.DARK ->
                                        stringResource(R.string.settings_theme_dark)
                                },
                            )
                        },
                    )
                }
            }
            HorizontalDivider()

            CardUpdateSection(
                state = state,
                onSync = viewModel::syncCards,
                onCancel = viewModel::cancelSync,
            )
            HorizontalDivider()

            EncounterTrackerSection(
                state = state,
                onTrackEncounterChange = viewModel::setTrackEncounter,
            )
            HorizontalDivider()

            PlayLocationSection(
                state = state,
                onPlayLocationChange = viewModel::setPlayLocation,
            )
            HorizontalDivider()
            HorizontalDivider()

            val imageProgress by viewModel.imagePrefetchProgress.collectAsStateWithLifecycle()
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_images),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_images_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (imageProgress != null) {
                    Text(
                        text = stringResource(R.string.settings_images_running, imageProgress!!),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Button(
                        onClick = viewModel::prefetchImages,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_images_download)) }
                }
            }
            HorizontalDivider()

            BackupSection(
                pendingRestore = viewModel.pendingRestore.collectAsStateWithLifecycle().value,
                message = viewModel.backupMessage.collectAsStateWithLifecycle().value,
                suggestedFileName = viewModel::suggestedBackupName,
                onExport = viewModel::exportBackup,
                onFileChosen = viewModel::openBackup,
                onConfirmRestore = viewModel::confirmRestore,
                onCancelRestore = viewModel::cancelRestore,
                onDismissMessage = viewModel::dismissBackupMessage,
            )
            HorizontalDivider()

            BggSection(
                state = state.bgg,
                isVerifying = state.bggVerifying,
                error = state.bggError,
                onConnect = viewModel::connectBgg,
                onDisconnect = viewModel::disconnectBgg,
                onModeChange = viewModel::setBggMode,
            )
            HorizontalDivider()

            val context = LocalContext.current
            var noMailApp by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_contact)) },
                supportingContent = { Text(stringResource(R.string.settings_contact_summary)) },
                modifier = Modifier.clickable {
                    noMailApp = !sendContactEmail(context)
                },
            )
            // Only after a crash. Nothing to say is the normal state, and a row
            // that is always there invites people to go looking for trouble.
            val crash = remember { CrashLog.read(context) }
            if (crash != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_crash_report)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_crash_report_summary))
                    },
                    modifier = Modifier.clickable { noMailApp = !sendCrashEmail(context, crash) },
                )
            }

            if (noMailApp) {
                Text(
                    text = stringResource(R.string.settings_no_mail_app, CONTACT_ADDRESS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
            // No donate entry, deliberately. This project's standing is that it
            // is an unofficial, non-commercial fan work, and money coming in is
            // the thing most likely to undermine that.
        }
    }
}

/**
 * Where games get played, sent with a play to BoardGameGeek.
 *
 * Typed, not sensed. BGG's location on a play is a string a person wrote, so
 * asking Android for the position would mean a location permission on an app
 * that has only ever wanted the network, to produce something less useful than
 * the word you would have typed anyway.
 */
/**
 * The language the app speaks, as against the language of the cards.
 *
 * The two really are separate — plenty of people read the app in French and the
 * cards in English, because English is what their group says out loud — but only
 * the card language had a control here. The app language could be changed from
 * Android's own settings, three screens deep, and on Android 12 not at all.
 *
 * The choice is not kept in this app's preferences. AppCompat owns it, so that
 * Android 13 and above shows the same value in system settings instead of the
 * two quietly disagreeing.
 */
@Composable
private fun AppLanguageSection() {
    val selected = AppCompatDelegate.getApplicationLocales()
        .toLanguageTags()
        .takeIf { it.isNotBlank() }
        ?.substringBefore('-')

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_app_language),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_app_language_summary),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Following the system has to stay reachable: somebody who picks a
            // language should be able to put it back without knowing which one
            // their phone was using.
            FilterChip(
                selected = selected == null,
                onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                },
                label = { Text(stringResource(R.string.settings_app_language_system)) },
            )
            listOf(
                "fr" to R.string.language_french,
                "en" to R.string.language_english,
            ).forEach { (tag, label) ->
                FilterChip(
                    selected = selected == tag,
                    onClick = {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(tag),
                        )
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

/**
 * Counting health and threat during a game, which not everybody wants.
 *
 * Off by default. The app has always been a thing you consult before and after
 * a game, and turning this on makes it something that sits on the table for the
 * whole of one — a different bargain, and the player's to make.
 */
@Composable
private fun EncounterTrackerSection(
    state: SettingsUiState,
    onTrackEncounterChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_tracker_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.settings_tracker_summary),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = state.trackEncounter,
            onCheckedChange = onTrackEncounterChange,
        )
    }
}

@Composable
private fun PlayLocationSection(
    state: SettingsUiState,
    onPlayLocationChange: (String) -> Unit,
) {
    var draft by remember(state.playLocation) { mutableStateOf(state.playLocation) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_play_location),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_play_location_summary),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                onPlayLocationChange(it)
            },
            singleLine = true,
            label = { Text(stringResource(R.string.settings_play_location_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CardUpdateSection(
    state: SettingsUiState,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.settings_update_cards),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = state.lastCardSync?.let {
                stringResource(
                    R.string.settings_last_sync,
                    DateFormat.getDateTimeInstance().format(Date(it)),
                )
            } ?: stringResource(R.string.settings_never_synced),
            style = MaterialTheme.typography.bodySmall,
        )

        when (val sync = state.syncState) {
            is CardSyncState.Running -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    text = syncStepLabel(sync),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }

            is CardSyncState.Failed -> {
                Text(
                    text = stringResource(
                        R.string.settings_sync_failed,
                        sync.message ?: stringResource(R.string.settings_sync_unknown_error),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onSync) { Text(stringResource(R.string.settings_update_now)) }
            }

            is CardSyncState.Cancelled -> {
                Text(
                    text = stringResource(R.string.settings_sync_cancelled),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onSync) { Text(stringResource(R.string.settings_update_now)) }
            }

            else -> Button(onClick = onSync) {
                Text(stringResource(R.string.settings_update_now))
            }
        }
    }
}

@Composable
private fun syncStepLabel(running: CardSyncState.Running): String {
    val locale = running.locale?.uppercase()
    return when (running.step) {
        "PACKS" -> stringResource(R.string.settings_sync_step_packs)
        "DOWNLOADING_CARDS" -> stringResource(R.string.settings_sync_step_downloading, locale ?: "")
        "STORING_CARDS" -> stringResource(R.string.settings_sync_step_storing, locale ?: "")
        else -> stringResource(R.string.settings_sync_step_starting)
    }
}
