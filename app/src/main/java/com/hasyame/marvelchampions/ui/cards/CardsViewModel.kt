package com.hasyame.marvelchampions.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.repository.CardFilterOptions
import com.hasyame.marvelchampions.data.repository.CardSearchRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/** Results plus the context needed to render them. */
private data class SearchOutcome(
    val results: List<CardEntity> = emptyList(),
    val options: CardFilterOptions = CardFilterOptions(),
    val hasMore: Boolean = false,
    val isDatabaseEmpty: Boolean = false,
    val isLoading: Boolean = true,
)

data class CardsUiState(
    val filter: CardFilter = CardFilter(),
    val results: List<CardEntity> = emptyList(),
    val options: CardFilterOptions = CardFilterOptions(),
    val locale: CardLocale = CardLocale.FRENCH,
    val isLoading: Boolean = true,
    /** True when the card database has not been populated yet. */
    val hasMore: Boolean = false,
    val isDatabaseEmpty: Boolean = false,
    /** Only meaningful in the two-pane layout on a wide screen. */
    val selectedCode: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: CardSearchRepository,
    preferences: AppPreferences,
) : ViewModel() {

    private val filter = MutableStateFlow(CardFilter())
    private val pageSize = MutableStateFlow(PAGE_SIZE)
    private val selectedCode = MutableStateFlow<String?>(null)

    /**
     * Typing must not fire a query per keystroke, so the filter is debounced.
     * `mapLatest` then cancels a search that a newer keystroke has superseded.
     */
    private val options = preferences.cardLocale.flatMapLatest(repository::observeFilterOptions)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private val searchOutcome = combine(filter, preferences.cardLocale, pageSize, options) { filter, locale, size, options ->
        SearchRequest(filter, locale, size, options)
    }.debounce(SEARCH_DEBOUNCE_MS).flatMapLatest { request ->
        repository.observeSearchChanges().mapLatest {
            val results = repository.search(request.filter, request.locale, request.size + 1)
            SearchOutcome(
                results = results.take(request.size),
                options = request.options,
                hasMore = results.size > request.size,
                isDatabaseEmpty = repository.countForLocale(request.locale) == 0,
                isLoading = false,
            )
        }.onStart { emit(SearchOutcome(options = request.options)) }
    }

    private data class SearchRequest(val filter: CardFilter, val locale: CardLocale, val size: Int, val options: CardFilterOptions)

    val uiState: StateFlow<CardsUiState> = combine(
        filter,
        selectedCode,
        preferences.cardLocale,
        searchOutcome,
    ) { currentFilter, selected, locale, outcome ->
        CardsUiState(
            filter = currentFilter,
            results = outcome.results,
            options = outcome.options,
            locale = locale,
            isLoading = outcome.isLoading,
            isDatabaseEmpty = outcome.isDatabaseEmpty,
            hasMore = outcome.hasMore,
            selectedCode = selected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CardsUiState(),
    )

    val cardLocale: StateFlow<CardLocale> = preferences.cardLocale.map { it }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CardLocale.FRENCH,
    )

    fun onQueryChange(query: String) {
        pageSize.value = PAGE_SIZE
        filter.value = filter.value.copy(query = query)
    }

    fun onFilterChange(newFilter: CardFilter) {
        pageSize.value = PAGE_SIZE
        filter.value = newFilter
    }

    /** Clears the filters but keeps what the user typed. */
    fun clearFilters() {
        pageSize.value = PAGE_SIZE
        filter.value = CardFilter(query = filter.value.query)
    }

    fun loadMore() {
        if (uiState.value.hasMore && !uiState.value.isLoading) pageSize.value += PAGE_SIZE
    }

    fun onCardSelected(code: String?) {
        selectedCode.value = code
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
