package com.hasyame.marvelchampions.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckImportResult
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.DeckFolderEntity
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckFolderRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.deeplink.MarvelCdbDeckUrl
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A deck on the shelf, with what the tile shows beside its name. */
data class DeckTile(
    val deck: SavedDeckEntity,
    val heroImageSrc: String?,
    val cardCount: Int,
    /** Null while the rules are unknown, as for a hero the database lacks. */
    val legal: Boolean?,
)

data class DecksUiState(
    val decks: List<DeckTile> = emptyList(),
    /** The shelf's folders, by name. */
    val folders: List<DeckFolderEntity> = emptyList(),
    val isImporting: Boolean = false,
    val importError: DeckImportError? = null,
    /** Set when an import succeeds, so the UI can open the new deck. */
    val importedDeckId: String? = null,
    /**
     * The decks holding a card the search names, or null while the search
     * is not by card. Worked out here because it takes the card database:
     * the word finds cards, and the cards find the decks they are in.
     */
    val cardSearchHits: Set<String>? = null,
)

@HiltViewModel
class DecksViewModel @Inject constructor(
    private val repository: DeckRepository,
    private val builderRepository: DeckBuilderRepository,
    private val folderRepository: DeckFolderRepository,
    private val cardDao: CardDao,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val importError = MutableStateFlow<DeckImportError?>(null)
    private val importedDeckId = MutableStateFlow<String?>(null)
    private val cardHits = MutableStateFlow<Set<String>?>(null)

    val uiState: StateFlow<DecksUiState> = combine(
        repository.observeDecks(),
        folderRepository.observeFolders(),
        importing,
        importError,
        importedDeckId,
        cardHits,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DecksUiState(
            decks = tiles(values[0] as List<SavedDeckEntity>),
            folders = values[1] as List<DeckFolderEntity>,
            isImporting = values[2] as Boolean,
            importError = values[3] as DeckImportError?,
            importedDeckId = values[4] as String?,
            cardSearchHits = values[5] as Set<String>?,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DecksUiState(),
    )

    /**
     * The tiles: each deck with its hero's picture and whether it is legal.
     *
     * Judged here rather than on the deck page so the shelf can say it, the
     * way the web's does; a shelf holds a handful of decks and the validator
     * is cheap, so this is done afresh whenever the shelf changes.
     */
    private suspend fun tiles(decks: List<SavedDeckEntity>): List<DeckTile> {
        val locale = preferences.currentCardLocale()
        return decks.map { deck ->
            val hero = cardDao.getCardPreferringLocale(deck.heroCode, locale.code)
            val slots = DeckRepository.parseSlots(deck.slots)
            val legal = builderRepository.heroRules(deck.heroCode, locale)?.let { rules ->
                builderRepository.validate(rules, DeckRepository.parseAspects(deck.aspects), slots, locale).isLegal
            }
            DeckTile(deck = deck, heroImageSrc = hero?.imageSrc, cardCount = slots.values.sum(), legal = legal)
        }
    }

    /**
     * Searches the decks by the cards in them: the word is matched against
     * card names in the card language, and a deck holding any of those cards
     * is a hit. Blank clears the search.
     */
    fun searchByCard(query: String) {
        if (query.isBlank()) {
            cardHits.value = null
            return
        }
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val match = SearchNormalizer.toPrefixMatchQuery(query) ?: run {
                cardHits.value = null
                return@launch
            }
            val codes = cardDao.search(match, locale.code, limit = CARD_SEARCH_LIMIT).map { it.code }.toSet()
            val decks = repository.getDecks()
            cardHits.value = decks
                .filter { deck -> DeckRepository.parseSlots(deck.slots).keys.any { it in codes } }
                .map { it.id }
                .toSet()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch { folderRepository.create(name) }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch { folderRepository.rename(id, name) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch { folderRepository.delete(id) }
    }

    fun moveDeck(deckId: String, folderId: String?) {
        viewModelScope.launch { folderRepository.moveDeck(deckId, folderId) }
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
        const val CARD_SEARCH_LIMIT = 500
    }
}
