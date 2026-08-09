package com.hasyame.marvelchampions.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.seed.RuleEntry
import com.hasyame.marvelchampions.data.seed.RulesCredit
import com.hasyame.marvelchampions.data.seed.RulesReference
import com.hasyame.marvelchampions.domain.model.CardLocale
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RulesUiState(
    val query: String = "",
    val entries: List<RuleEntry> = emptyList(),
    /** Who compiled the reference, shown under the list. */
    val credit: RulesCredit? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val rules: RulesReference,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /**
     * Follows the app language, not the card language.
     *
     * The rules are the app talking, not a card: somebody reading the interface
     * in French wants the rules in French, and the keyword named in French too,
     * whichever language their cards happen to be printed in. Set by the screen
     * from the current configuration, so switching the app language changes it.
     */
    private val locale = MutableStateFlow(CardLocale.ENGLISH)

    fun onAppLanguage(language: String) {
        locale.value =
            if (language.startsWith("fr")) CardLocale.FRENCH else CardLocale.ENGLISH
    }

    private val all = locale.map { rules.entries(it) }

    private val credit = MutableStateFlow<RulesCredit?>(null)

    init {
        viewModelScope.launch { credit.value = rules.credit() }
    }

    val uiState: StateFlow<RulesUiState> = combine(all, query, credit) { entries, text, who ->
        RulesUiState(
            query = text,
            entries = filter(entries, text),
            credit = who,
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
