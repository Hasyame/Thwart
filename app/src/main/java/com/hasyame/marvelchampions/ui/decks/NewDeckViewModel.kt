package com.hasyame.marvelchampions.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.repository.HeroChoice
import com.hasyame.marvelchampions.data.repository.RandomizerRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewDeckUiState(
    /** The heroes on offer: every one, or only the collection's, per the setting. */
    val heroes: List<HeroChoice> = emptyList(),
    /** True when [heroes] was narrowed to the collection, so an empty list can say why. */
    val collectionOnly: Boolean = true,
    val selectedHero: HeroChoice? = null,
    val rules: HeroDeckRules? = null,
    val chosenAspects: List<String> = emptyList(),
    val name: String = "",
    /**
     * Whether the player has typed a name of their own.
     *
     * Once they have, picking a hero must not touch it. Without this the
     * prefill was a plain "overwrite if blank", and blank is exactly what the
     * field is for the moment somebody starts typing into it while the hero's
     * rules are still loading: the load finished, read a field it had seen
     * empty, and put the hero's name back over what had just been typed. It
     * came out as a deck called "Iron Man" that the player had named something
     * else.
     */
    val nameEdited: Boolean = false,
    val isLoading: Boolean = true,
    val createdDeckId: String? = null,
) {
    /**
     * The state after a hero is picked, as far as the name is concerned.
     *
     * Its own function because it is the whole of the rule and the rule is what
     * went wrong: a name the player wrote is theirs and a hero must not take it
     * back, while an untouched field is a convenience worth filling so the
     * Create button can be pressed without typing anything.
     */
    fun namedAfterPicking(heroName: String): NewDeckUiState =
        if (nameEdited) this else copy(name = heroName)

    val aspectsNeeded: Int get() = rules?.aspectCount ?: 1
    val canCreate: Boolean
        get() = selectedHero != null &&
            chosenAspects.size == aspectsNeeded &&
            name.isNotBlank()
}

@HiltViewModel
class NewDeckViewModel @Inject constructor(
    private val builderRepository: DeckBuilderRepository,
    private val deckRepository: DeckRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(NewDeckUiState())
    val uiState: StateFlow<NewDeckUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            // Loaded into a local before the state is read. Written inline the
            // state is read before the load suspends, so an aspect ticked while
            // the sixty-five heroes were being fetched was quietly discarded.
            val collectionOnly = preferences.isDeckCollectionOnly()
            val heroes = builderRepository.heroes(locale)
                .filter { !collectionOnly || it.owned }
            state.update {
                it.copy(heroes = heroes, collectionOnly = collectionOnly, isLoading = false)
            }
        }
    }

    fun selectHero(hero: HeroChoice) {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val rules = builderRepository.heroRules(hero.card.code, locale)
            // `update` rather than an assignment: this lands after a database
            // read, and a plain read-modify-write puts back whatever the field
            // held when the read started, discarding anything typed meanwhile.
            state.update { current ->
                current.namedAfterPicking(hero.card.name).copy(
                    selectedHero = hero,
                    rules = rules,
                    // Aspect choices belong to a hero, so they reset with it.
                    chosenAspects = emptyList(),
                )
            }
        }
    }

    fun toggleAspect(aspect: String) {
        state.update { current ->
            val chosen = current.chosenAspects
            val next = when {
                aspect in chosen -> chosen - aspect
                // Picking past the limit replaces the oldest choice rather than
                // making the user deselect first.
                chosen.size >= current.aspectsNeeded -> chosen.drop(1) + aspect
                else -> chosen + aspect
            }
            current.copy(chosenAspects = next)
        }
    }

    fun setName(name: String) {
        state.update { it.copy(name = name, nameEdited = true) }
    }

    fun create() {
        val current = state.value
        val hero = current.selectedHero ?: return
        if (!current.canCreate) {
            return
        }
        viewModelScope.launch {
            val id = deckRepository.createLocalDeck(
                name = current.name.trim(),
                heroCode = hero.card.code,
                heroName = hero.card.name,
                aspects = current.chosenAspects,
                // The hero's signature cards, in their printed numbers. They
                // are required, so the deck opens with them already in it.
                slots = current.rules?.requiredCards.orEmpty(),
            )
            state.update { it.copy(createdDeckId = id) }
        }
    }

    fun consumeCreatedDeck() {
        state.update { it.copy(createdDeckId = null) }
    }

    companion object {
        val ASPECTS: List<String> = RandomizerRepository.ASPECTS
    }
}
