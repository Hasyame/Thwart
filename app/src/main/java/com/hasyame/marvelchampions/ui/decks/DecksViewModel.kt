package com.hasyame.marvelchampions.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckImportResult
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.deeplink.MarvelCdbDeckUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DecksUiState(
    val decks: List<SavedDeckEntity> = emptyList(),
    val isImporting: Boolean = false,
    val importError: DeckImportError? = null,
    /** Set when an import succeeds, so the UI can open the new deck. */
    val importedDeckId: String? = null,
    /** Whether building a deck offers only what the collection holds. */
    val collectionOnly: Boolean = true,
)

@HiltViewModel
class DecksViewModel @Inject constructor(
    private val repository: DeckRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val importError = MutableStateFlow<DeckImportError?>(null)
    private val importedDeckId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DecksUiState> = combine(
        repository.observeDecks(),
        importing,
        importError,
        importedDeckId,
        preferences.deckCollectionOnly,
    ) { decks, isImporting, error, imported, collectionOnly ->
        DecksUiState(
            decks = decks,
            isImporting = isImporting,
            importError = error,
            importedDeckId = imported,
            collectionOnly = collectionOnly,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DecksUiState(),
    )

    fun setCollectionOnly(enabled: Boolean) {
        viewModelScope.launch { preferences.setDeckCollectionOnly(enabled) }
    }

    /** Imports from anything the user pasted, or a share sheet delivered. */
    fun import(input: String) {
        val reference = MarvelCdbDeckUrl.parse(input)
        if (reference == null) {
            importError.value = DeckImportError.NotADeckLink
            return
        }
        viewModelScope.launch {
            importing.value = true
            importError.value = null
            when (val result = repository.import(reference)) {
                is DeckImportResult.Success -> importedDeckId.value = result.deckId
                is DeckImportResult.Failure -> importError.value = result.error
            }
            importing.value = false
        }
    }

    fun refresh(deckId: String) {
        viewModelScope.launch {
            importing.value = true
            importError.value = null
            val result = repository.refresh(deckId)
            if (result is DeckImportResult.Failure) {
                importError.value = result.error
            }
            importing.value = false
        }
    }

    fun delete(deckId: String) {
        viewModelScope.launch { repository.delete(deckId) }
    }

    fun consumeImportedDeck() {
        importedDeckId.value = null
    }

    fun dismissError() {
        importError.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
