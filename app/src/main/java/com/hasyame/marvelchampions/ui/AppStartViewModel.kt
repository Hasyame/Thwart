package com.hasyame.marvelchampions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.FirstRunInitializer
import com.hasyame.marvelchampions.data.repository.FirstRunOutcome
import com.hasyame.marvelchampions.data.sync.CardSyncManager
import com.hasyame.marvelchampions.data.sync.CardUpdate
import com.hasyame.marvelchampions.data.sync.CardUpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartupState {
    data object Loading : StartupState

    /**
     * Ready to show the app.
     *
     * [openCollectionFirst] sends a brand new install to the collection screen:
     * an empty collection makes the randomiser useless and the campaign tab
     * unavailable, so asking on day one beats letting the user discover it.
     *
     * [startInSettings] is the wider case, and covers the build with no bundled
     * cards. There the collection screen would list nothing at all — there are
     * no packs to tick until a sync has run — so the app opens on Settings,
     * where the button that fixes it lives.
     */
    data class Ready(
        val openCollectionFirst: Boolean,
        val startInSettings: Boolean,
    ) : StartupState
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

    init {
        viewModelScope.launch {
            val outcome = firstRunInitializer.initialize()
            state.value = StartupState.Ready(
                // Only when there is a collection to correct. A build without
                // the bundled cards has no packs yet, and opening a screen
                // reading "0 of 0 packs owned" told the player nothing except
                // that something was wrong.
                openCollectionFirst = outcome == FirstRunOutcome.SEEDED,
                startInSettings = outcome != FirstRunOutcome.ALREADY_READY,
            )
            // After the app is on screen, not before. This is a network
            // call, and nobody should wait on MarvelCDB to reach their
            // own cards. A first run is skipped: it has just finished
            // populating itself and has nothing to compare against.
            if (outcome == FirstRunOutcome.ALREADY_READY) {
                updates.value = updateChecker.check()
            }
        }
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
