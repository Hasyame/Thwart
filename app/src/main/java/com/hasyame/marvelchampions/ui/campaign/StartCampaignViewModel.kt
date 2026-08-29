package com.hasyame.marvelchampions.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.CollectionRepository
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.TranslationCoverage
import com.hasyame.marvelchampions.domain.campaign.template.translationCoverage
import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A deck offered for the roster, with whether it may actually be used. */
data class RosterCandidate(
    val deck: SavedDeckEntity,
    val problems: List<DeckProblem> = emptyList(),
) {
    val isLegal: Boolean get() = problems.isEmpty()
}

data class StartCampaignUiState(
    val templates: List<CampaignTemplate> = emptyList(),
    val candidates: List<RosterCandidate> = emptyList(),
    val isLoading: Boolean = true,
    /** Campaign names follow the card language, like the rest of the campaign. */
    val localeCode: String = CardLocale.FRENCH.code,
    /** How much of each campaign's own text is written, by template id. */
    val coverage: Map<String, TranslationCoverage> = emptyMap(),
    /**
     * Standard sets the collection can field, offered when the campaign is
     * played on expert. Each one arrived in a particular box.
     */
    val standardSets: List<Difficulty> = emptyList(),
)

@HiltViewModel
class StartCampaignViewModel @Inject constructor(
    private val campaignRepository: CampaignRepository,
    private val deckRepository: DeckRepository,
    private val builderRepository: DeckBuilderRepository,
    private val collectionRepository: CollectionRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(StartCampaignUiState())
    val uiState: StateFlow<StartCampaignUiState> = state.asStateFlow()

    init {
        // Collected, not read once. This used to be a single suspend read in
        // init, and the view model outlives a trip to the Decks tab — so a
        // player who imported a deck because the screen told them to came back
        // to the same screen still saying they had none, and the only way out
        // was to restart the app.
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val bundled = campaignRepository.bundledTemplates()

            // Owned packs are collected alongside the decks: a player who ticks
            // a box in the collection while this screen is alive should find
            // its campaign waiting when they come back, the same way an
            // imported deck appears.
            combine(
                deckRepository.observeDecks(),
                collectionRepository.observeOwnedCodes(),
            ) { decks, owned -> decks to owned }.collect { (decks, owned) ->
                val templates = bundled.filter { it.packCode.isNullOrBlank() || it.packCode in owned }
                state.value = StartCampaignUiState(
                    templates = templates,
                    // Counted here rather than in the composable: it walks the
                    // whole template, and the list is rebuilt on every deck
                    // change.
                    coverage = templates.associate { it.id to it.translationCoverage() },
                    standardSets = Difficulty.standards.filter { it.packCode in owned },
                    candidates = decks.map { deck ->
                        val rules = builderRepository.heroRules(deck.heroCode, locale)
                        RosterCandidate(
                            deck = deck,
                            // Cards missing from the collection are deliberately
                            // not a problem here: a campaign is about deck
                            // legality, and owning the cards is a separate matter.
                            problems = rules?.let {
                                builderRepository.validate(
                                    rules = it,
                                    aspects = DeckRepository.parseAspects(deck.aspects),
                                    slots = DeckRepository.parseSlots(deck.slots),
                                    locale = locale,
                                ).problems
                            }.orEmpty(),
                        )
                    },
                    isLoading = false,
                    localeCode = locale.code,
                )
            }
        }
    }

    fun start(
        template: CampaignTemplate,
        difficulty: String,
        standardSet: String,
        deckIds: List<String>,
        name: String,
        choices: Map<String, String>,
        onStarted: (String) -> Unit,
    ) {
        // Illegal decks cannot reach here through the UI; refusing again keeps
        // that true if the screen ever changes.
        val legal = state.value.candidates.filter { it.isLegal }.map { it.deck.id }
        val roster = deckIds.filter { it in legal }
        if (roster.isEmpty()) {
            return
        }
        viewModelScope.launch {
            onStarted(
                campaignRepository.startRun(
                    template = template,
                    difficulty = difficulty,
                    standardSet = standardSet,
                    deckIds = roster,
                    name = name,
                    choices = choices,
                ),
            )
        }
    }
}
