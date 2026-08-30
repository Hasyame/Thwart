package com.hasyame.marvelchampions.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicEmptyState
import com.hasyame.marvelchampions.core.designsystem.component.ComicLoadingScreen
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.domain.model.CardSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    onCardClick: (String) -> Unit,
    viewModel: CardsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filtersOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    // A 12-inch tablet gets list and detail side by side; a phone navigates to
    // the detail as its own screen.
    val isWide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    // This was the one tab without a title bar, so its search field started
    // hard against the status bar while every other screen began below one.
    // The title slides away as you scroll and comes back the moment you scroll
    // up. On a list this long the red bar was holding one word across a tenth
    // of the screen for the entire time anybody spent reading.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.destination_cards)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                query = state.filter.query,
                activeFilterCount = state.filter.activeCount,
                onQueryChange = viewModel::onQueryChange,
                onFiltersClick = { filtersOpen = true },
                onSortClick = { sortOpen = true },
            )

            // A sort is not a filter — it never hides a card — so it is its own
            // control rather than another row inside the filter sheet.
            if (sortOpen) {
                SortSheet(
                    current = state.filter.sort,
                    onPick = {
                        viewModel.onFilterChange(state.filter.copy(sort = it))
                        sortOpen = false
                    },
                    onDismiss = { sortOpen = false },
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isDatabaseEmpty ->
                        EmptyMessage(stringResource(R.string.cards_database_empty))

                    state.isLoading && state.results.isEmpty() ->
                        ComicLoadingScreen(message = stringResource(R.string.cards_loading))

                    state.results.isEmpty() ->
                        EmptyMessage(stringResource(R.string.cards_no_results))

                    isWide -> Row(Modifier.fillMaxSize()) {
                        CardList(
                            state = state,
                            onCardClick = viewModel::onCardSelected,
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider()
                        Box(Modifier.weight(1.2f)) {
                            val selected = state.selectedCode
                            if (selected == null) {
                                EmptyMessage(stringResource(R.string.cards_select_a_card))
                            } else {
                                CardDetailPane(code = selected)
                            }
                        }
                    }

                    else -> CardList(
                        state = state,
                        onCardClick = onCardClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (filtersOpen) {
        CardFilterSheet(
            filter = state.filter,
            options = state.options,
            onFilterChange = viewModel::onFilterChange,
            onClear = viewModel::clearFilters,
            onDismiss = { filtersOpen = false },
        )
    }
}

@Composable
private fun CardList(
    state: CardsUiState,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(state.results, key = { _, card -> card.code }) { index, card ->
            CardListItem(
                card = card,
                selected = card.code == state.selectedCode,
                onClick = { onCardClick(card.code) },
                // Only where the pack changes, so the label marks a boundary
                // instead of repeating down the whole screen.
                showPack = index == 0 ||
                    state.results[index - 1].packCode != card.packCode,
            )
        }
    }
}

/** The detail pane of the two-pane layout, with its own view model instance. */
@Composable
private fun CardDetailPane(code: String) {
    val viewModel: CardDetailViewModel = hiltViewModel(key = "detail-pane")
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(code) { viewModel.load(code) }

    val card = state.card
    if (card == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        CardDetailContent(
            card = card,
            isFavourite = state.isFavourite,
            onToggleFavourite = viewModel::toggleFavourite,
            pack = state.pack,
            linkedCard = state.linkedCard,
            locale = state.locale,
            onLocaleToggle = viewModel::toggleLocale,
            onLinkedCardClick = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    activeFilterCount: Int,
    onQueryChange: (String) -> Unit,
    onFiltersClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            // One line, always. The field is single-line but the label was not,
            // so beside two icon buttons it wrapped and made the whole box two
            // rows tall for no gain.
            label = {
                Text(
                    text = stringResource(R.string.cards_search_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                }
            },
        )
        IconButton(onClick = onSortClick) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(R.string.cards_sort),
            )
        }
        BadgedBox(
            badge = {
                if (activeFilterCount > 0) {
                    Badge { Text(activeFilterCount.toString()) }
                }
            },
        ) {
            IconButton(onClick = onFiltersClick) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.cards_filters),
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    // "No results" and "no cards yet" are screens the user actually reads, so
    // they are where the comic styling earns its keep. The list stays plain.
    ComicEmptyState(message)
}

/** The four orderings, as a sheet rather than a menu so each can carry a reason. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: CardSort,
    onPick: (CardSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.cards_sort),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp),
            )
            CardSort.entries.forEach { sort ->
                ListItem(
                    headlineContent = { Text(stringResource(sort.labelRes())) },
                    trailingContent = {
                        if (sort == current) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    modifier = Modifier.clickable { onPick(sort) },
                )
            }
        }
    }
}

private fun CardSort.labelRes(): Int = when (this) {
    CardSort.SET -> R.string.cards_sort_set
    CardSort.NAME -> R.string.cards_sort_name
    CardSort.COST_LOW_TO_HIGH -> R.string.cards_sort_cost_asc
    CardSort.COST_HIGH_TO_LOW -> R.string.cards_sort_cost_desc
}
