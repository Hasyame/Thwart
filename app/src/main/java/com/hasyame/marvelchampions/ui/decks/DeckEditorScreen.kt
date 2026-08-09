package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckEditorScreen(
    deckId: String,
    onBack: () -> Unit,
    viewModel: DeckEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(deckId) { viewModel.load(deckId) }

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = { Text(state.deck?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            ValidationSummary(state)

            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.decks_tab_deck)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.decks_tab_add)) },
                )
            }

            when (tab) {
                0 -> DeckContentsTab(state = state, viewModel = viewModel)
                else -> AddCardsTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ValidationSummary(state: DeckEditorUiState) {
    val validation = state.validation
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = pluralStringResource(
                    R.plurals.decks_card_total,
                    validation.totalCards,
                    validation.totalCards,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.hasLocalEdits) {
                Text(
                    text = stringResource(R.string.decks_locally_edited),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (validation.isLegal) {
                Text(
                    text = stringResource(R.string.decks_legal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            validation.problems.forEach { problem ->
                Text(
                    text = problemMessage(problem),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeckContentsTab(state: DeckEditorUiState, viewModel: DeckEditorViewModel) {
    if (state.deckCards.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.decks_editor_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.deckCards, key = { it.code }) { card ->
            CardRow(
                card = card,
                quantity = state.slots[card.code] ?: 0,
                editable = state.isEditable,
                onAdd = { viewModel.addCard(card.code) },
                onRemove = { viewModel.removeCard(card.code) },
            )
        }
    }
}

@Composable
private fun AddCardsTab(state: DeckEditorUiState, viewModel: DeckEditorViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                label = { Text(stringResource(R.string.cards_search_hint)) },
                modifier = Modifier.weight(1f),
            )
        }
        FilterChip(
            selected = state.ownedOnly,
            onClick = { viewModel.setOwnedOnly(!state.ownedOnly) },
            label = { Text(stringResource(R.string.cards_filter_owned_only)) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.candidates, key = { it.code }) { card ->
                CardRow(
                    card = card,
                    quantity = state.slots[card.code] ?: 0,
                    editable = state.isEditable,
                    onAdd = { viewModel.addCard(card.code) },
                    onRemove = { viewModel.removeCard(card.code) },
                )
            }
        }
    }
}

@Composable
private fun CardRow(
    card: CardEntity,
    quantity: Int,
    editable: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(if (quantity > 0) "$quantity× ${card.name}" else card.name)
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    card.typeName,
                    card.factionName,
                    card.cost?.let { stringResource(R.string.card_stat_cost) + " $it" },
                    // The copy limit is the rule most often tripped over, so it
                    // is shown rather than only reported after the fact.
                    card.deckLimit?.let { stringResource(R.string.decks_limit, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            if (editable) {
                Row {
                    if (quantity > 0) {
                        IconButton(onClick = onRemove) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.decks_remove_card),
                            )
                        }
                    }
                    IconButton(onClick = onAdd) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.decks_add_card),
                        )
                    }
                }
            }
        },
    )
    HorizontalDivider()
}

@Composable
private fun problemMessage(problem: DeckProblem): String = when (problem) {
    is DeckProblem.TooFewCards -> pluralStringResource(
        R.plurals.decks_problem_too_few,
        problem.actual,
        problem.actual,
        problem.required,
    )

    is DeckProblem.TooManyCards -> pluralStringResource(
        R.plurals.decks_problem_too_many,
        problem.actual,
        problem.actual,
        problem.allowed,
    )

    is DeckProblem.WrongAspectCount -> pluralStringResource(
        R.plurals.decks_problem_aspect_count,
        problem.actual,
        problem.actual,
        problem.required,
    )

    is DeckProblem.OffAspectCard ->
        stringResource(R.string.decks_problem_off_aspect, problem.cardName, problem.factionCode)

    is DeckProblem.OverCopyLimit -> pluralStringResource(
        R.plurals.decks_problem_copy_limit,
        problem.quantity,
        problem.cardName,
        problem.quantity,
        problem.limit,
    )

    is DeckProblem.DuplicateUniqueCard ->
        stringResource(R.string.decks_problem_unique, problem.cardName)

    is DeckProblem.MissingRequiredCard -> stringResource(
        R.string.decks_problem_required_card,
        problem.cardName,
        problem.required,
        problem.actual,
    )

    // Named rather than counted: "justice 9, aggression 11" says what to fix,
    // where "your aspects are uneven" leaves the player counting.
    is DeckProblem.UnbalancedAspects -> stringResource(
        R.string.decks_problem_unbalanced_aspects,
        problem.counts.entries.joinToString(", ") { "${it.key} ${it.value}" },
    )
}
