package com.hasyame.marvelchampions.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidation
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckEditorUiState(
    val deck: SavedDeckEntity? = null,
    val rules: HeroDeckRules? = null,
    val slots: Map<String, Int> = emptyMap(),
    val deckCards: List<CardEntity> = emptyList(),
    val candidates: List<CardEntity> = emptyList(),
    val validation: DeckValidation = DeckValidation(),
    val query: String = "",
    val ownedOnly: Boolean = true,
    val sort: DeckSort = DeckSort.TYPE,
    val isLoading: Boolean = true,
) {
    /**
     * Every deck can be edited, imported ones included. An imported deck that
     * turns out to be illegal would otherwise be unfixable, and a campaign
     * refuses illegal decks.
     */
    val isEditable: Boolean get() = deck != null

    /** True when a refresh would discard changes made here. */
    val hasLocalEdits: Boolean
        get() = deck?.let { !DeckRepository.isLocal(it) && it.locallyEdited } == true
}

@OptIn(FlowPreview::class)
@HiltViewModel
class DeckEditorViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val builderRepository: DeckBuilderRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(DeckEditorUiState())
    val uiState: StateFlow<DeckEditorUiState> = state.asStateFlow()

    private val query = MutableStateFlow("")
    private var deckId: String? = null

    init {
        // No distinctUntilChanged: StateFlow already conflates equal values.
        query
            .debounce(SEARCH_DEBOUNCE_MS)
            .onEach { refreshCandidates() }
            .launchIn(viewModelScope)
    }

    fun load(id: String) {
        deckId = id
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val deck = deckRepository.getDeck(id)
            if (deck == null) {
                state.value = state.value.copy(isLoading = false)
                return@launch
            }
            val rules = builderRepository.heroRules(deck.heroCode, locale)
            state.value = state.value.copy(deck = deck, rules = rules, isLoading = false)
            reloadDeckContents()
            refreshCandidates()
        }
    }

    fun setQuery(value: String) {
        state.value = state.value.copy(query = value)
        query.value = value
    }

    fun setSort(sort: DeckSort) {
        state.value = state.value.copy(sort = sort)
    }

    fun setOwnedOnly(ownedOnly: Boolean) {
        state.value = state.value.copy(ownedOnly = ownedOnly)
        viewModelScope.launch { refreshCandidates() }
    }

    fun addCard(code: String) = changeQuantity(code, +1)

    fun removeCard(code: String) = changeQuantity(code, -1)

    private fun changeQuantity(code: String, delta: Int) {
        val id = deckId ?: return
        if (!state.value.isEditable) {
            return
        }
        viewModelScope.launch {
            val current = state.value.slots[code] ?: 0
            deckRepository.setCardQuantity(id, code, current + delta)
            reloadDeckContents()
        }
    }

    fun rename(name: String) {
        val id = deckId ?: return
        viewModelScope.launch {
            deckRepository.renameDeck(id, name)
            val renamed = deckRepository.getDeck(id)
            state.value = state.value.copy(deck = renamed)
        }
    }

    private suspend fun reloadDeckContents() {
        val id = deckId ?: return
        val locale = preferences.currentCardLocale()
        val deck = deckRepository.getDeck(id) ?: return
        val slots = DeckRepository.parseSlots(deck.slots)
        val aspects = DeckRepository.parseAspects(deck.aspects)
        val rules = state.value.rules

        val contents = deckRepository.contents(id, locale)
        val validation = rules?.let {
            builderRepository.validate(it, aspects, slots, locale)
        } ?: DeckValidation(totalCards = slots.values.sum())

        state.value = state.value.copy(
            deck = deck,
            slots = slots,
            deckCards = contents?.cardsByType?.values?.flatten()?.map { it.card }.orEmpty(),
            validation = validation,
        )
    }

    private suspend fun refreshCandidates() {
        val deck = state.value.deck ?: return
        val locale = preferences.currentCardLocale()
        val rules = state.value.rules
        // Fetched first, and only then written back. Inline, the state is read
        // before the search suspends, so anything typed while four hundred
        // candidates were being queried was reverted under the player's hands.
        val candidates = builderRepository.candidateCards(
            heroSetCode = rules?.heroSetCode,
            aspects = DeckRepository.parseAspects(deck.aspects),
            locale = locale,
            query = state.value.query,
            ownedOnly = state.value.ownedOnly,
        )
        state.value = state.value.copy(candidates = candidates)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
