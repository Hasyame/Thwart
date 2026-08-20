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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewDeckUiState(
    val heroes: List<HeroChoice> = emptyList(),
    val selectedHero: HeroChoice? = null,
    val rules: HeroDeckRules? = null,
    val chosenAspects: List<String> = emptyList(),
    val name: String = "",
    val isLoading: Boolean = true,
    val createdDeckId: String? = null,
) {
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
            val heroes = builderRepository.heroes(locale)
            state.value = state.value.copy(
                heroes = heroes,
                isLoading = false,
            )
        }
    }

    fun selectHero(hero: HeroChoice) {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val rules = builderRepository.heroRules(hero.card.code, locale)
            state.value = state.value.copy(
                selectedHero = hero,
                rules = rules,
                // Aspect choices belong to a hero, so they reset with it.
                chosenAspects = emptyList(),
                name = state.value.name.ifBlank { hero.card.name },
            )
        }
    }

    fun toggleAspect(aspect: String) {
        val current = state.value.chosenAspects
        val needed = state.value.aspectsNeeded
        val next = when {
            aspect in current -> current - aspect
            // Picking past the limit replaces the oldest choice rather than
            // making the user deselect first.
            current.size >= needed -> current.drop(1) + aspect
            else -> current + aspect
        }
        state.value = state.value.copy(chosenAspects = next)
    }

    fun setName(name: String) {
        state.value = state.value.copy(name = name)
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
            state.value = state.value.copy(createdDeckId = id)
        }
    }

    fun consumeCreatedDeck() {
        state.value = state.value.copy(createdDeckId = null)
    }

    companion object {
        val ASPECTS: List<String> = RandomizerRepository.ASPECTS
    }
}
