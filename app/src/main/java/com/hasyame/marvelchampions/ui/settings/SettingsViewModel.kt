package com.hasyame.marvelchampions.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.data.repository.CollectionRepository
import com.hasyame.marvelchampions.data.sync.CardImagePrefetcher
import com.hasyame.marvelchampions.data.sync.CardSyncManager
import com.hasyame.marvelchampions.data.sync.CardSyncState
import com.hasyame.marvelchampions.data.backup.Backup
import com.hasyame.marvelchampions.data.backup.BackupRepository
import com.hasyame.marvelchampions.data.backup.BackupResult
import com.hasyame.marvelchampions.data.backup.BackupSummary
import com.hasyame.marvelchampions.data.bgg.BggAccount
import com.hasyame.marvelchampions.data.bgg.BggAccountState
import com.hasyame.marvelchampions.data.bgg.BggClient
import com.hasyame.marvelchampions.data.bgg.BggResult
import com.hasyame.marvelchampions.domain.model.BggReportingMode
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.model.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val cardLocale: CardLocale = CardLocale.FRENCH,
    val lastCardSync: Long? = null,
    val syncState: CardSyncState = CardSyncState.Idle,
    val playLocation: String = "",
    /** Counters during a game. Off unless the player asks for them. */
    val trackEncounter: Boolean = false,
    val themeChoice: ThemeChoice = ThemeChoice.DARK,
    val bgg: BggAccountState = BggAccountState(),
    val bggVerifying: Boolean = false,
    val bggError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val bggAccount: BggAccount,
    private val bggClient: BggClient,
    private val syncManager: CardSyncManager,
    private val backupRepository: BackupRepository,
    private val imagePrefetcher: CardImagePrefetcher,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {

    /** Verifying and error are moments, not settings, so they are not persisted. */
    private val bggTransient = MutableStateFlow(BggTransient())

    private val storedSettings = combine(
        preferences.cardLocale,
        preferences.lastCardSync,
        syncManager.observeState(),
        preferences.themeChoice,
        preferences.playLocation,
        preferences.trackEncounter,
    ) { values ->
        SettingsUiState(
            cardLocale = values[0] as CardLocale,
            lastCardSync = values[1] as Long?,
            syncState = values[2] as CardSyncState,
            themeChoice = values[3] as ThemeChoice,
            playLocation = values[4] as String,
            trackEncounter = values[5] as Boolean,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        storedSettings,
        bggAccount.state,
        bggTransient,
    ) { stored, bgg, transient ->
        stored.copy(
            bgg = bgg,
            bggVerifying = transient.verifying,
            bggError = transient.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    /**
     * Checks the credentials against BoardGameGeek before storing them, so a
     * typo is caught while the player is still looking at the form rather than
     * silently failing after a game weeks later.
     */
    fun connectBgg(username: String, password: String) {
        viewModelScope.launch {
            bggTransient.value = BggTransient(verifying = true)
            val result = bggClient.verify(username, password)
            bggTransient.value = when (result) {
                is BggResult.Success -> {
                    if (bggAccount.connect(username, password)) {
                        // Connecting without choosing when to report would do
                        // nothing, so asking is the useful default.
                        bggAccount.setMode(BggReportingMode.ASK)
                        BggTransient()
                    } else {
                        BggTransient(error = "could not store the credentials securely")
                    }
                }

                is BggResult.BadCredentials -> BggTransient(error = "username or password rejected")
                is BggResult.Rejected -> BggTransient(error = "BoardGameGeek refused: ${result.detail}")
                is BggResult.Offline -> BggTransient(error = "could not reach BoardGameGeek: ${result.detail}")
            }
        }
    }

    fun disconnectBgg() {
        viewModelScope.launch {
            bggAccount.disconnect()
            bggTransient.value = BggTransient()
        }
    }

    fun setBggMode(mode: BggReportingMode) {
        viewModelScope.launch { bggAccount.setMode(mode) }
    }

    fun suggestedBackupName(withPhotos: Boolean): String =
        backupRepository.suggestedFileName(withPhotos)

    private val pendingBackup = MutableStateFlow<Backup?>(null)

    /**
     * The file the pending restore came from, kept because the photographs
     * are still in it. Reading them at confirmation time rather than at
     * preview time keeps a restore that is never confirmed from touching
     * anything at all.
     */
    private var pendingSource: android.net.Uri? = null

    /** What a chosen file contains, shown before anything is replaced. */
    val pendingRestore: StateFlow<BackupSummary?> = pendingBackup
        .map { it?.summary() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val backupMessages = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = backupMessages.asStateFlow()

    fun exportBackup(destination: android.net.Uri, includePhotos: Boolean = false) {
        viewModelScope.launch {
            val result = backupRepository.export(destination, includePhotos)
            backupMessages.value = when (result) {
                is BackupResult.Exported -> "Backup saved."
                is BackupResult.Failed -> "Could not save the backup: ${result.detail}"
                else -> null
            }
        }
    }

    /** Reads the file but changes nothing, so the restore can be confirmed. */
    fun openBackup(source: android.net.Uri) {
        viewModelScope.launch {
            backupRepository.peek(source).fold(
                onSuccess = {
                    pendingBackup.value = it
                    pendingSource = source
                },
                onFailure = {
                    pendingSource = null
                    backupMessages.value = "That file is not a backup: ${it.message}"
                },
            )
        }
    }

    fun confirmRestore() {
        val backup = pendingBackup.value ?: return
        val source = pendingSource
        pendingBackup.value = null
        pendingSource = null
        viewModelScope.launch {
            val result = backupRepository.restore(backup, source)
            backupMessages.value = when (result) {
                is BackupResult.Restored ->
                    "Restored ${result.summary.decks} decks, " +
                        "${result.summary.campaigns} campaigns and " +
                        "${result.summary.plays} games."

                is BackupResult.Failed -> "Could not restore: ${result.detail}"
                else -> null
            }
        }
    }

    fun cancelRestore() {
        pendingBackup.value = null
    }

    fun dismissBackupMessage() {
        backupMessages.value = null
    }

    private val imageProgress = MutableStateFlow<String?>(null)

    /** Non-null while images are downloading, holding what to show. */
    val imagePrefetchProgress: StateFlow<String?> = imageProgress.asStateFlow()

    /**
     * Downloads card images for the packs the player owns.
     *
     * An explicit action rather than part of the card sync: it is hundreds of
     * files and tens of megabytes, and doing that unasked to somebody on mobile
     * data would be indefensible.
     */
    fun prefetchImages() {
        if (imageProgress.value != null) {
            return
        }
        viewModelScope.launch {
            imageProgress.value = "…"
            runCatching {
                imagePrefetcher.prefetchOwned(
                    ownedPackCodes = collectionRepository.getOwnedCodes().toSet(),
                ) { done, total -> imageProgress.value = "$done / $total" }
            }
            imageProgress.value = null
        }
    }

    fun setCardLocale(locale: CardLocale) {
        viewModelScope.launch { preferences.setCardLocale(locale) }
    }

    fun setThemeChoice(choice: ThemeChoice) {
        viewModelScope.launch { preferences.setThemeChoice(choice) }
    }

    fun setPlayLocation(location: String) {
        viewModelScope.launch { preferences.setPlayLocation(location) }
    }

    fun setTrackEncounter(enabled: Boolean) {
        viewModelScope.launch { preferences.setTrackEncounter(enabled) }
    }

    fun syncCards() = syncManager.start()

    fun cancelSync() = syncManager.cancel()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The parts of the BoardGameGeek form that exist only while it is on screen. */
private data class BggTransient(
    val verifying: Boolean = false,
    val error: String? = null,
)
