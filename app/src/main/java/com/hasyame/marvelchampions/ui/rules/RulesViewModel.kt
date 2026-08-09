package com.hasyame.marvelchampions.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.seed.RuleEntry
import com.hasyame.marvelchampions.data.seed.RulesReference
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RulesUiState(
    val query: String = "",
    val entries: List<RuleEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RulesViewModel @Inject constructor(
    rules: RulesReference,
    preferences: AppPreferences,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Follows the card language rather than the app language: somebody reading
    // French cards wants the French rule for the word printed on them.
    private val all = preferences.cardLocale.map { rules.entries(it) }

    val uiState: StateFlow<RulesUiState> = combine(all, query) { entries, text ->
        RulesUiState(
            query = text,
            entries = filter(entries, text),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = RulesUiState(),
    )

    fun onQueryChange(text: String) {
        query.value = text
    }

    private fun filter(entries: List<RuleEntry>, text: String): List<RuleEntry> {
        val needle = SearchNormalizer.normalize(text).orEmpty()
        if (needle.isBlank()) {
            return entries
        }
        // The term first, then the body. Searching "stun" should put STUNNED at
        // the top rather than the eleven other rules that mention it.
        val byTerm = entries.filter {
            SearchNormalizer.normalize(it.term).orEmpty().contains(needle)
        }
        val byBody = entries.filter {
            it !in byTerm && SearchNormalizer.normalize(it.body).orEmpty().contains(needle)
        }
        return byTerm + byBody
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
