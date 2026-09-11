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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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

    /** The subscription to the deck row, cancelled if this screen is reused. */
    private var watching: Job? = null

    init {
        // The same setting as the switch on the Decks page, so the two agree;
        // the chip here writes it back. Usually read before the first
        // candidate search, which waits for the deck row; if the row wins the
        // race, the search is run again, and refreshCandidates() is a no-op
        // until there is a deck.
        viewModelScope.launch {
            state.update { it.copy(ownedOnly = preferences.isDeckCollectionOnly()) }
            refreshCandidates()
        }
        // No distinctUntilChanged: StateFlow already conflates equal values.
        query
            .debounce(SEARCH_DEBOUNCE_MS)
            .onEach { refreshCandidates() }
            .launchIn(viewModelScope)
    }

    /**
     * Opens a deck and then keeps watching it.
     *
     * Observed rather than read once, and the difference only shows with sync
     * on: a deck edited on a tablet used to leave this screen displaying what
     * it had read when it opened, with no sign that anything had moved. Now the
     * change arrives here the moment it lands, through the same path this
     * screen's own edits take.
     *
     * The hazard this deliberately does not have is text under somebody's
     * fingers. What updates here is a list of quantities, not a field being
     * typed into: the deck's name is edited in a dialogue that holds its own
     * copy, and nothing writes over it while it is open.
     */
    fun load(id: String) {
        if (deckId == id) {
            return
        }
        deckId = id
        watching?.cancel()
        watching = deckRepository.observeDeck(id)
            .onEach { deck ->
                if (deck == null) {
                    state.value = state.value.copy(isLoading = false)
                    return@onEach
                }
                val rules = state.value.rules
                    ?: builderRepository.heroRules(
                        deck.heroCode,
                        preferences.currentCardLocale(),
                    )
                val first = state.value.deck == null
                state.value = state.value.copy(deck = deck, rules = rules, isLoading = false)
                reloadDeckContents()
                if (first) {
                    refreshCandidates()
                }
            }
            .launchIn(viewModelScope)
    }

    fun setQuery(value: String) {
        state.value = state.value.copy(query = value)
        query.value = value
    }

    fun setSort(sort: DeckSort) {
        state.value = state.value.copy(sort = sort)
    }

    fun setOwnedOnly(ownedOnly: Boolean) {
        state.update { it.copy(ownedOnly = ownedOnly) }
        viewModelScope.launch {
            preferences.setDeckCollectionOnly(ownedOnly)
            refreshCandidates()
        }
    }

    fun addCard(code: String) = changeQuantity(code, +1)

    fun removeCard(code: String) = changeQuantity(code, -1)

    /**
     * The tap is sent as what it is: one more, or one fewer.
     *
     * It used to read the quantity off this screen's own copy, add the delta
     * and send the total. That is fine on one device and wrong on two: the copy
     * on screen can be older than the row, so the total was computed from a
     * number another device had already changed, and sending it wrote that
     * change away. The repository does the arithmetic against the row instead.
     *
     * Nothing is reloaded here either. The screen observes the deck, so the
     * write comes back through the same path a change from another device
     * takes — one way in, and no chance of the two disagreeing.
     */
    private fun changeQuantity(code: String, delta: Int) {
        val id = deckId ?: return
        if (!state.value.isEditable) {
            return
        }
        viewModelScope.launch { deckRepository.adjustCardQuantity(id, code, delta) }
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
