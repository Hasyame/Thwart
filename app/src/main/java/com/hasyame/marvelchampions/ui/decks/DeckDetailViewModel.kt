package com.hasyame.marvelchampions.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.CardSearchRepository
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckContents
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckImportResult
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.domain.deckbuilder.DeckStatistics
import com.hasyame.marvelchampions.domain.deckbuilder.DeckStatisticsCalculator
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidation
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A card a campaign granted, resolved for display. */
data class CampaignCardRow(
    val cardCode: String,
    val name: String,
    val campaignName: String,
)

data class DeckDetailUiState(
    val contents: DeckContents? = null,
    val statistics: DeckStatistics = DeckStatistics(),
    val campaignCards: List<CampaignCardRow> = emptyList(),
    val validation: DeckValidation = DeckValidation(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DeckImportError? = null,
) {
    /** True when refreshing would discard changes made in the app. */
    val hasLocalEdits: Boolean
        get() = contents?.deck?.let { !DeckRepository.isLocal(it) && it.locallyEdited } == true
}

@HiltViewModel
class DeckDetailViewModel @Inject constructor(
    private val repository: DeckRepository,
    private val campaignRepository: CampaignRepository,
    private val cardSearchRepository: CardSearchRepository,
    private val builderRepository: DeckBuilderRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(DeckDetailUiState())
    val uiState: StateFlow<DeckDetailUiState> = state.asStateFlow()

    private var deckId: String? = null

    fun load(id: String) {
        deckId = id
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)
            val locale = preferences.currentCardLocale()
            val contents = repository.contents(id, locale)
            state.value = DeckDetailUiState(
                contents = contents,
                statistics = contents.statistics(),
                campaignCards = campaignCards(id, locale),
                validation = validate(contents, locale),
                isLoading = false,
            )
        }
    }

    /**
     * Gives the deck a name of your own.
     *
     * Imported decks included, which is the point: MarvelCDB names a deck
     * after its hero unless the author renamed it there, so a list of imports
     * can be four decks all called Iron Man with no way to tell them apart. The
     * name is a local edit like any other, so refreshing from MarvelCDB will
     * warn before it takes the name back.
     */
    fun rename(name: String) {
        val id = deckId ?: return
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return
        }
        viewModelScope.launch {
            repository.renameDeck(id, cleaned)
            load(id)
        }
    }

    /**
     * Legality is shown for every deck, imported ones included: a campaign
     * refuses an illegal deck, so it has to be visible before you get there.
     */
    private suspend fun validate(contents: DeckContents?, locale: CardLocale): DeckValidation {
        val deck = contents?.deck ?: return DeckValidation()
        val rules = builderRepository.heroRules(deck.heroCode, locale) ?: return DeckValidation()
        return builderRepository.validate(
            rules = rules,
            aspects = DeckRepository.parseAspects(deck.aspects),
            slots = DeckRepository.parseSlots(deck.slots),
            locale = locale,
        )
    }

    fun revertToImported() {
        val id = deckId ?: return
        viewModelScope.launch {
            repository.revertToImported(id)
            load(id)
        }
    }

    /**
     * Cards campaigns have added to this deck. They are stored on the campaign
     * run, never merged into the deck, so the deck stays exactly as imported.
     */
    private suspend fun campaignCards(deckId: String, locale: CardLocale): List<CampaignCardRow> =
        campaignRepository.campaignCardsForDeck(deckId).map { granted ->
            CampaignCardRow(
                cardCode = granted.cardCode,
                name = cardSearchRepository.getCard(granted.cardCode, locale)?.name
                    ?: granted.cardCode,
                campaignName = granted.campaignName,
            )
        }

    /** Re-fetches from MarvelCDB, replacing whatever is stored locally. */
    fun refresh() {
        val id = deckId ?: return
        viewModelScope.launch {
            state.value = state.value.copy(isRefreshing = true, error = null)
            val result = repository.refresh(id)
            val error = (result as? DeckImportResult.Failure)?.error
            val locale = preferences.currentCardLocale()
            val contents = repository.contents(id, locale)
            state.value = DeckDetailUiState(
                contents = contents,
                statistics = contents.statistics(),
                campaignCards = campaignCards(id, locale),
                validation = validate(contents, locale),
                isLoading = false,
                isRefreshing = false,
                error = error,
            )
        }
    }
}

/**
 * Counts the deck, hero excluded: the hero is not part of the deck, has no
 * cost, and would distort both the curve and the aspect split.
 */
private fun DeckContents?.statistics(): DeckStatistics =
    DeckStatisticsCalculator.calculate(
        this?.cardsByType?.values?.flatten()?.map { it.card to it.quantity }.orEmpty(),
    )
