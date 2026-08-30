package com.hasyame.marvelchampions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.FirstRunInitializer
import com.hasyame.marvelchampions.data.repository.FirstRunOutcome
import com.hasyame.marvelchampions.data.sync.CardSyncManager
import com.hasyame.marvelchampions.data.sync.CardSyncState
import com.hasyame.marvelchampions.data.sync.CardUpdate
import com.hasyame.marvelchampions.data.sync.CardUpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Matches the other view models: long enough to survive a rotation. */
private const val STOP_TIMEOUT_MS = 5_000L

sealed interface StartupState {
    data object Loading : StartupState

    /**
     * Ready to show the app.
     *
     * [openCollectionFirst] sends a brand new install to the collection screen:
     * an empty collection makes the randomiser useless and the campaign tab
     * unavailable, so asking on day one beats letting the user discover it.
     *
     * [startInSettings] is the wider case: somebody who skipped the first-run
     * download has no packs to tick, so the collection screen would read "0 of
     * 0 packs owned" and tell them nothing except that something is wrong. The
     * app opens on Settings instead, where the button that fixes it lives.
     */
    data class Ready(
        val openCollectionFirst: Boolean,
        val startInSettings: Boolean,
    ) : StartupState

    /**
     * A new install with no cards in it, which is every new install.
     *
     * The cards are not in the package. They belong to MarvelCDB, they change
     * when MarvelCDB changes, and a snapshot signed into an APK would be both
     * somebody else's content and out of date. So the app asks for them once,
     * here, rather than opening onto a game it cannot deal.
     */
    data object NeedsCards : StartupState
}

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val firstRunInitializer: FirstRunInitializer,
    private val updateChecker: CardUpdateChecker,
    private val syncManager: CardSyncManager,
) : ViewModel() {

    private val state = MutableStateFlow<StartupState>(StartupState.Loading)
    val startupState: StateFlow<StartupState> = state.asStateFlow()

    private val collectionPrompt = FirstRunPrompt()

    private val updates = MutableStateFlow<List<CardUpdate>>(emptyList())

    /**
     * Packs MarvelCDB has that this device has not, if any.
     *
     * Empty while the check is in flight, and empty for good if it fails,
     * so the app opens at its usual speed and says nothing when there is
     * nothing to say.
     */
    val newCards: StateFlow<List<CardUpdate>> = updates.asStateFlow()

    /** What the download is doing, for the first-run screen to show. */
    val cardDownload: StateFlow<CardSyncState> = syncManager.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CardSyncState.Idle)

    init {
        viewModelScope.launch { settle() }
    }

    /**
     * Decides which of the three ways in this launch takes.
     *
     * Called again after a download finishes, so the same decision is made once
     * rather than being duplicated by whoever happens to finish first.
     */
    private suspend fun settle(justDownloaded: Boolean = false) {
        when (val outcome = firstRunInitializer.initialize()) {
            FirstRunOutcome.NEEDS_SYNC -> state.value = StartupState.NeedsCards

            else -> {
                state.value = StartupState.Ready(
                    // Somebody who has just fetched the cards is as new an
                    // install as one that arrived with them, and the collection
                    // is the first thing they need to correct. Without this the
                    // download itself swallowed the introduction.
                    openCollectionFirst = justDownloaded ||
                        outcome == FirstRunOutcome.SEEDED,
                    startInSettings = false,
                )
                // After the app is on screen, not before. This is a network
                // call, and nobody should wait on MarvelCDB to reach their
                // own cards. Skipped on a first run, which has just finished
                // populating itself and has nothing to compare against.
                if (outcome == FirstRunOutcome.ALREADY_READY && !justDownloaded) {
                    updates.value = updateChecker.check()
                }
            }
        }
    }

    /** Fetch the cards, through the same refresh the Settings button uses. */
    fun startCardDownload() {
        syncManager.start()
    }

    /** The download finished. Seed the collection and open the app properly. */
    fun cardDownloadFinished() {
        viewModelScope.launch { settle(justDownloaded = true) }
    }

    /**
     * Go in without them.
     *
     * Offered because the alternative is a locked door. Somebody with no signal
     * can still read the rules reference and look at their play history, and
     * being told to come back later when they have opened the app to settle an
     * argument at the table is the worse answer. Settings is where they land,
     * because that is where the button is.
     */
    fun skipCardDownload() {
        state.value = StartupState.Ready(openCollectionFirst = false, startInSettings = true)
    }

    /** Yes: fetch them, through the same refresh the Settings button uses. */
    fun downloadNewCards() {
        updates.value = emptyList()
        syncManager.start()
    }

    /** No: remember which packs were turned down, and stop asking. */
    fun ignoreNewCards() {
        val turnedDown = updates.value
        updates.value = emptyList()
        viewModelScope.launch { updateChecker.dismiss(turnedDown) }
    }

    /**
     * True once, on a new install: the app should open the collection screen.
     *
     * It has to be consumed rather than read, because the thing that acts on it
     * is a composition and a composition does not survive a configuration
     * change while this view model does. Read plainly, it fired again on every
     * fold, unfold and rotation — dropping the player into the collection
     * screen mid-game, stacking another copy of it on the back stack each time,
     * and leaving the navigation bar unable to switch tabs afterwards.
     *
     * Reported from a Galaxy Z Fold 7, where opening the phone does this every
     * time rather than only when somebody turns it sideways.
     */
    fun consumeOpenCollection(): Boolean {
        val ready = state.value as? StartupState.Ready ?: return false
        return collectionPrompt.consume(ready.openCollectionFirst)
    }
}
