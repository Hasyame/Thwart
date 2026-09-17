package com.hasyame.marvelchampions.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.DeckFolderEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.CollectionRepository
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckCard
import com.hasyame.marvelchampions.data.repository.DeckFolderRepository
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckImportResult
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.deckbuilder.DeckStatistics
import com.hasyame.marvelchampions.domain.deckbuilder.DeckStatisticsCalculator
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidation
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import com.hasyame.marvelchampions.domain.deckbuilder.IdentityTraits
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyWarning
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.model.PackType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** A card a campaign put in this deck; it lives on the run, not in the deck. */
data class CampaignCardRow(
    val cardCode: String,
    val name: String,
    val campaignName: String,
)

/** A pack the deck draws on, for the information tab. */
data class PackUse(
    val code: String,
    val name: String,
    val type: PackType,
    val cardCount: Int,
    val owned: Boolean,
)

data class DeckUiState(
    val deck: SavedDeckEntity? = null,
    val rules: HeroDeckRules? = null,
    val hero: CardEntity? = null,
    /** Card code to copies, hero card excluded. */
    val slots: Map<String, Int> = emptyMap(),
    /** The deck resolved against the card database, with what is missing. */
    val deckCards: List<DeckCard> = emptyList(),
    /** Codes in the deck the local card database does not know. */
    val unknownCardCodes: List<String> = emptyList(),
    val validation: DeckValidation = DeckValidation(),
    val synergyWarnings: List<SynergyWarning> = emptyList(),
    val statistics: DeckStatistics = DeckStatistics(),
    val campaignCards: List<CampaignCardRow> = emptyList(),
    val packs: List<PackUse> = emptyList(),
    val nemesis: List<CardEntity> = emptyList(),
    val folders: List<DeckFolderEntity> = emptyList(),
    /** The card language, named in the app's language, for the note at the foot. */
    val cardLanguage: String = "",

    // --- the pool, on the first tab ---------------------------------------
    val candidates: List<CardEntity> = emptyList(),
    val query: String = "",
    val ownedOnly: Boolean = true,
    /** Off on every opening, on purpose: it hides cards silently. */
    val synergyOnly: Boolean = false,
    val sort: DeckSort = DeckSort.TYPE,
    val factionFilter: Set<String> = emptySet(),
    val typeFilter: Set<String> = emptySet(),
    val costFilter: Int? = null,
    val poolTotal: Int = 0,
    val typeChoices: List<Pair<String, String>> = emptyList(),
    val ownedPackCodes: Set<String> = emptySet(),

    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DeckImportError? = null,
    /** True once the deck has been removed, for the screen to leave on. */
    val deleted: Boolean = false,
) {
    /** Every deck can be edited, imported ones included. */
    val isEditable: Boolean get() = deck != null

    /** True when a refresh would discard changes made here. */
    val hasLocalEdits: Boolean
        get() = deck?.let { !DeckRepository.isLocal(it) && it.locallyEdited } == true

    /** True when the pool tab is showing a search rather than the deck. */
    val isSearching: Boolean
        get() = query.isNotBlank() || factionFilter.isNotEmpty() || typeFilter.isNotEmpty() || costFilter != null

    val factionChoices: List<String>
        get() = listOf("hero", "basic") + (deck?.let { DeckRepository.parseAspects(it.aspects) }.orEmpty())

    val missingCards: List<DeckCard> get() = deckCards.filter { it.missingFromCollection }
}

/**
 * One deck, on three tabs: the cards (and the pool to add from), what the
 * deck is made of, and what there is to know about it. Every tap on a
 * stepper is written at once, which is what lets two devices edit the same
 * deck without one writing over the other; there is no Save.
 *
 * The deck row is observed, so a change from another device arrives here
 * through the same path this screen's own edits take.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class DeckViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val builderRepository: DeckBuilderRepository,
    private val collectionRepository: CollectionRepository,
    private val campaignRepository: CampaignRepository,
    private val folderRepository: DeckFolderRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(DeckUiState())
    val uiState: StateFlow<DeckUiState> = state.asStateFlow()

    private val query = MutableStateFlow("")
    private var deckId: String? = null
    private var watching: Job? = null
    private var identity: IdentityTraits = IdentityTraits.NONE

    init {
        viewModelScope.launch {
            state.update { it.copy(ownedOnly = preferences.isDeckCollectionOnly()) }
            refreshCandidates()
        }
        query.debounce(SEARCH_DEBOUNCE_MS).onEach { refreshCandidates() }.launchIn(viewModelScope)
        collectionRepository.observeOwnedCodes()
            .onEach { owned -> state.update { it.copy(ownedPackCodes = owned) } }
            .launchIn(viewModelScope)
        folderRepository.observeFolders()
            .onEach { folders -> state.update { it.copy(folders = folders) } }
            .launchIn(viewModelScope)
    }

    fun load(id: String) {
        if (deckId == id) {
            return
        }
        deckId = id
        watching?.cancel()
        watching = deckRepository.observeDeck(id)
            .onEach { deck ->
                if (deck == null) {
                    state.update { it.copy(isLoading = false) }
                    return@onEach
                }
                val locale = preferences.currentCardLocale()
                val first = state.value.deck == null
                val rules = state.value.rules ?: builderRepository.heroRules(deck.heroCode, locale)
                if (first) {
                    identity = builderRepository.identityTraits(deck.heroCode)
                    state.update {
                        it.copy(
                            rules = rules,
                            nemesis = deckRepository.nemesisSet(deck.heroCode, locale),
                            campaignCards = campaignCards(id, locale),
                            cardLanguage = Locale.forLanguageTag(locale.code).getDisplayLanguage(Locale.getDefault()),
                        )
                    }
                }
                reload(deck, rules, locale)
                if (first) {
                    refreshCandidates()
                }
            }
            .launchIn(viewModelScope)
    }

    /** Everything worked out from the deck as it stands: cards, verdicts, figures, packs. */
    private suspend fun reload(deck: SavedDeckEntity, rules: HeroDeckRules?, locale: CardLocale) {
        val contents = deckRepository.contents(deck.id, locale)
        val slots = DeckRepository.parseSlots(deck.slots)
        val aspects = DeckRepository.parseAspects(deck.aspects)
        val cards = contents?.cardsByType?.values?.flatten().orEmpty()
        val validation = rules?.let { builderRepository.validate(it, aspects, slots, locale) }
            ?: DeckValidation(totalCards = slots.values.sum())
        val warnings = rules?.let { builderRepository.synergyWarnings(it, slots, locale) }.orEmpty()
        state.update {
            it.copy(
                deck = deck,
                hero = contents?.hero,
                slots = slots,
                deckCards = cards,
                unknownCardCodes = contents?.unknownCardCodes.orEmpty(),
                validation = validation,
                synergyWarnings = warnings,
                statistics = DeckStatisticsCalculator.calculate(cards.map { card -> card.card to card.quantity }),
                packs = packsUsed(cards, locale),
                isLoading = false,
            )
        }
    }

    /** The packs the deck's cards come from, named in the card language, with whether they are owned. */
    private suspend fun packsUsed(cards: List<DeckCard>, locale: CardLocale): List<PackUse> {
        val shelf = collectionRepository.observeCollection(locale).first().associateBy { it.pack.code }
        return cards.groupBy { it.card.packCode }
            .map { (code, inPack) ->
                val ownership = shelf[code]
                PackUse(
                    code = code,
                    name = ownership?.name ?: inPack.first().card.packName,
                    type = ownership?.pack?.type?.let { PackType.fromName(it) } ?: PackType.UNKNOWN,
                    cardCount = inPack.sumOf { it.quantity },
                    owned = ownership?.isOwned == true,
                )
            }
            .sortedWith(compareBy({ it.type.ordinal }, { it.name }))
    }

    private suspend fun campaignCards(deckId: String, locale: CardLocale): List<CampaignCardRow> =
        campaignRepository.campaignCardsForDeck(deckId).map { granted ->
            CampaignCardRow(
                cardCode = granted.cardCode,
                name = builderRepository.cardName(granted.cardCode, locale) ?: granted.cardCode,
                campaignName = granted.campaignName,
            )
        }

    // --- the deck ------------------------------------------------------------

    fun addCard(code: String) = changeQuantity(code, +1)

    fun removeCard(code: String) = changeQuantity(code, -1)

    /** One more or one fewer, never a total: see DeckRepository.adjustCardQuantity. */
    private fun changeQuantity(code: String, delta: Int) {
        val id = deckId ?: return
        if (!state.value.isEditable) {
            return
        }
        viewModelScope.launch { deckRepository.adjustCardQuantity(id, code, delta) }
    }

    fun rename(name: String) {
        val id = deckId ?: return
        viewModelScope.launch { deckRepository.renameDeck(id, name) }
    }

    /** The player's own words about the deck, kept in the description the deck already carries. */
    fun setNotes(text: String) {
        val id = deckId ?: return
        viewModelScope.launch { deckRepository.setNotes(id, text) }
    }

    fun moveToFolder(folderId: String?) {
        val id = deckId ?: return
        viewModelScope.launch { folderRepository.moveDeck(id, folderId) }
    }

    fun delete() {
        val id = deckId ?: return
        viewModelScope.launch {
            deckRepository.delete(id)
            state.update { it.copy(deleted = true) }
        }
    }

    fun revertToImported() {
        val id = deckId ?: return
        viewModelScope.launch { deckRepository.revertToImported(id) }
    }

    /** Re-fetches from MarvelCDB, replacing whatever is stored locally. */
    fun refresh() {
        val id = deckId ?: return
        viewModelScope.launch {
            state.update { it.copy(isRefreshing = true, error = null) }
            val result = deckRepository.refresh(id)
            state.update { it.copy(isRefreshing = false, error = (result as? DeckImportResult.Failure)?.error) }
        }
    }

    fun dismissError() = state.update { it.copy(error = null) }

    // --- the pool ------------------------------------------------------------

    fun setQuery(value: String) {
        state.update { it.copy(query = value) }
        query.value = value
    }

    fun setSort(sort: DeckSort) = state.update { it.copy(sort = sort) }

    fun setOwnedOnly(ownedOnly: Boolean) {
        state.update { it.copy(ownedOnly = ownedOnly) }
        viewModelScope.launch {
            preferences.setDeckCollectionOnly(ownedOnly)
            refreshCandidates()
        }
    }

    fun setSynergyOnly(synergyOnly: Boolean) {
        state.update { it.copy(synergyOnly = synergyOnly) }
        viewModelScope.launch { refreshCandidates() }
    }

    fun toggleFaction(faction: String) {
        state.update { it.copy(factionFilter = it.factionFilter.toggled(faction)) }
        viewModelScope.launch { refreshCandidates() }
    }

    fun toggleType(type: String) {
        state.update { it.copy(typeFilter = it.typeFilter.toggled(type)) }
        viewModelScope.launch { refreshCandidates() }
    }

    fun setCost(cost: Int?) {
        state.update { it.copy(costFilter = if (it.costFilter == cost) null else cost) }
        viewModelScope.launch { refreshCandidates() }
    }

    fun clearSearch() {
        state.update { it.copy(query = "", factionFilter = emptySet(), typeFilter = emptySet(), costFilter = null) }
        query.value = ""
        viewModelScope.launch { refreshCandidates() }
    }

    private fun Set<String>.toggled(value: String): Set<String> = if (value in this) this - value else this + value

    private suspend fun refreshCandidates() {
        val deck = state.value.deck ?: return
        val locale = preferences.currentCardLocale()
        val rules = state.value.rules
        val current = state.value
        val aspects = DeckRepository.parseAspects(deck.aspects)
        val candidates = builderRepository.candidateCards(
            heroSetCode = rules?.heroSetCode,
            aspects = aspects,
            locale = locale,
            query = current.query,
            ownedOnly = current.ownedOnly,
            synergyWith = identity.takeIf { current.synergyOnly },
            factions = current.factionFilter - HERO_FACTION,
            typeCodes = current.typeFilter,
            cost = current.costFilter,
            includeHeroCards = current.factionFilter.isEmpty() || HERO_FACTION in current.factionFilter,
            includeAspectCards = current.factionFilter.isEmpty() || (current.factionFilter - HERO_FACTION).isNotEmpty(),
        )
        val poolTotal = builderRepository.poolSize(rules?.heroSetCode, aspects, locale, current.ownedOnly)
        val types = current.typeChoices.ifEmpty { builderRepository.playerCardTypes(locale) }
        state.update { it.copy(candidates = candidates, poolTotal = poolTotal, typeChoices = types) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val HERO_FACTION = "hero"
    }
}
