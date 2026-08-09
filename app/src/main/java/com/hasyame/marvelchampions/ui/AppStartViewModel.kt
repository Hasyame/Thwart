package com.hasyame.marvelchampions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.FirstRunInitializer
import com.hasyame.marvelchampions.data.repository.FirstRunOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartupState {
    data object Loading : StartupState

    /**
     * Ready to show the app. [openCollectionFirst] sends a brand new install to
     * the collection screen: an empty collection makes the randomiser useless
     * and the campaign tab unavailable, so asking on day one beats letting the
     * user discover it.
     */
    data class Ready(val openCollectionFirst: Boolean) : StartupState
}

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val firstRunInitializer: FirstRunInitializer,
) : ViewModel() {

    private val state = MutableStateFlow<StartupState>(StartupState.Loading)
    val startupState: StateFlow<StartupState> = state.asStateFlow()

    private val collectionPrompt = FirstRunPrompt()

    init {
        viewModelScope.launch {
            val outcome = firstRunInitializer.initialize()
            state.value = StartupState.Ready(
                openCollectionFirst = outcome != FirstRunOutcome.ALREADY_READY,
            )
        }
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
