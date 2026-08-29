package com.hasyame.marvelchampions.ui.randomizer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.repository.RandomizerRepository
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.randomizer.DrawField
import com.hasyame.marvelchampions.ui.plays.PlaysViewModel
import com.hasyame.marvelchampions.ui.util.ChoiceOption
import com.hasyame.marvelchampions.ui.util.ChooseValueDialog
import com.hasyame.marvelchampions.ui.util.aspectLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomizerScreen(
    onBack: () -> Unit,
    onPlayDraw: (
        scenarioCode: String,
        heroes: String,
        modularSets: String,
        difficulty: String,
    ) -> Unit,
    viewModel: RandomizerViewModel = hiltViewModel(),
    playsViewModel: PlaysViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.destination_randomizer)) },
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
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.hasNoOwnedPacks -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.randomizer_no_packs)) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    DrawCard(state = state, viewModel = viewModel)
                }
                item {
                    // Said once, where somebody wondering "why is that missing"
                    // is looking. The alternative is them concluding the app has
                    // lost a scenario, which is what happened.
                    Text(
                        text = stringResource(R.string.randomizer_collection_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            // A tick under the thumb, the way a physical roll
                            // has one. Rolling is the one action here that is
                            // meant to feel like an event.
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.rollAll()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.randomizer_roll))
                        }
                        OutlinedButton(
                            onClick = viewModel::saveDraw,
                            enabled = state.draw.isComplete,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.randomizer_save_draw))
                        }
                    }
                }

                // The draw already knows the scenario, heroes and aspects, so
                // playing it hands all of that to the timed session rather than
                // asking for it again.
                item {
                    Button(
                        onClick = {
                            onPlayDraw(
                                state.draw.scenarioCode.orEmpty(),
                                state.draw.asSessionHeroes(),
                                state.draw.modularSetCodes.joinToString(","),
                                // Dropped before, so a rolled Expert II game
                                // was filed as Standard I.
                                state.draw.difficulty?.name?.lowercase().orEmpty(),
                            )
                        },
                        enabled = state.draw.isComplete,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.randomizer_play_this))
                    }
                }
                item { FiltersCard(state = state, viewModel = viewModel) }

                if (state.history.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.randomizer_history),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    items(state.history, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(state.names.scenarios[entry.scenarioCode] ?: entry.scenarioCode)
                            },
                            supportingContent = {
                                Text(
                                    RandomizerRepository.parseHeroes(entry.heroes)
                                        .joinToString(", ") { assignment ->
                                            val hero = state.names.heroes[assignment.heroCode]
                                                ?: assignment.heroCode
                                            "$hero (${assignment.aspect})"
                                        },
                                )
                            },
                            trailingContent = {
                                Row {
                                    FilterChip(
                                        selected = entry.beaten,
                                        onClick = { viewModel.setBeaten(entry.id, !entry.beaten) },
                                        label = {
                                            Text(stringResource(R.string.randomizer_beaten))
                                        },
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteHistoryEntry(entry.id) },
                                    ) {
                                        Icon(
                                            Icons.Filled.Clear,
                                            contentDescription = stringResource(R.string.action_delete),
                                        )
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawCard(state: RandomizerUiState, viewModel: RandomizerViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            DrawRow(
                label = stringResource(R.string.randomizer_scenario),
                value = state.draw.scenarioCode
                    ?.let { state.names.scenarios[it] ?: it }
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.SCENARIO,
                state = state,
                viewModel = viewModel,
            )
            if (state.scenarioNeedsReview) {
                Text(
                    text = stringResource(R.string.randomizer_needs_review),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            DrawRow(
                label = stringResource(R.string.randomizer_difficulty),
                value = state.draw.difficulty?.let { drawn ->
                    // The extras read as part of the difficulty, because at the
                    // table that is what they are: more cards in the same deck.
                    (listOf(drawn) + state.draw.extraDifficulties)
                        .map { difficultyLabel(it) }
                        .joinToString(" + ")
                } ?: stringResource(R.string.randomizer_none),
                field = DrawField.DIFFICULTY,
                state = state,
                viewModel = viewModel,
            )
            DrawRow(
                label = stringResource(R.string.randomizer_modular_sets),
                value = state.draw.modularSetCodes
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { code ->
                        val name = state.names.modularSets[code] ?: code
                        // Mandatory sets are marked so it is obvious which ones
                        // rerolling cannot change.
                        if (code in state.draw.mandatoryModularCodes) "$name*" else name
                    }
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.MODULAR_SETS,
                state = state,
                viewModel = viewModel,
            )
            // The asterisk meant something only to whoever wrote it. Said out
            // loud, and only when there is one to explain.
            if (state.draw.mandatoryModularCodes.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.randomizer_mandatory_legend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            DrawRow(
                label = stringResource(R.string.randomizer_players),
                value = state.draw.playerCount.toString(),
                field = DrawField.PLAYER_COUNT,
                state = state,
                viewModel = viewModel,
            )
            DrawRow(
                label = stringResource(R.string.randomizer_heroes),
                value = state.draw.heroes
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { state.names.heroes[it.heroCode] ?: it.heroCode }
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.HEROES,
                state = state,
                viewModel = viewModel,
            )
            DrawRow(
                label = stringResource(R.string.randomizer_aspects),
                // map is inline so it can host the composable label lookup;
                // joinToString is not, hence the two steps.
                value = state.draw.heroes
                    .map { aspectLabel(it.aspect) }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.ASPECTS,
                state = state,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun DrawRow(
    label: String,
    value: String,
    field: DrawField,
    state: RandomizerUiState,
    viewModel: RandomizerViewModel,
) {
    val isLocked = field in state.locked
    val haptics = LocalHapticFeedback.current
    var choosing by remember { mutableStateOf(false) }

    if (choosing) {
        val options = state.optionsFor(field)
        ChooseValueDialog(
            title = label,
            options = options,
            selected = state.selectionFor(field),
            limit = field.pickLimit(state.draw.playerCount),
            onDismiss = { choosing = false },
            onConfirm = {
                viewModel.choose(field, it)
                choosing = false
            },
        )
    }

    ListItem(
        overlineContent = { Text(label) },
        // The value tumbles in rather than blinking. Rolling is the most fun
        // thing this screen does and it used to happen with no sign that
        // anything had moved — on a reroll of one field you could miss it.
        headlineContent = {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn())
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut())
                },
                label = "draw-value",
            ) { shown ->
                Text(shown, style = MaterialTheme.typography.titleMedium)
            }
        },
        modifier = Modifier.clickable { choosing = true },
        trailingContent = {
            Row {
                IconButton(onClick = { viewModel.toggleLock(field) }) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(
                            if (isLocked) R.string.randomizer_unlock else R.string.randomizer_lock,
                        ),
                        tint = if (isLocked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.reroll(field)
                }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.randomizer_reroll),
                    )
                }
            }
        },
    )
}

@Composable
private fun FiltersCard(state: RandomizerUiState, viewModel: RandomizerViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.cards_filters),
                style = MaterialTheme.typography.titleMedium,
            )

            FilterChip(
                selected = state.excludeBeaten,
                onClick = { viewModel.setExcludeBeaten(!state.excludeBeaten) },
                label = { Text(stringResource(R.string.randomizer_exclude_beaten)) },
            )

            Text(
                text = stringResource(R.string.randomizer_difficulty),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Only the difficulties the collection can field. The draw and
                // the picker already knew this; the filter chips did not, so
                // Standard II sat here offering itself to somebody who does not
                // own The Hood.
                state.pools.difficulties.forEach { difficulty ->
                    val selected = difficulty in state.filters.allowedDifficulties
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = if (selected) {
                                state.filters.allowedDifficulties - difficulty
                            } else {
                                state.filters.allowedDifficulties + difficulty
                            }
                            viewModel.setAllowedDifficulties(next)
                        },
                        label = { Text(difficultyLabel(difficulty)) },
                    )
                }
            }

            // Off unless asked for. Standard II and Expert II are extra
            // encounter cards a table chooses to shuffle in, so a draw that
            // handed them over unprompted would be setting up a harder game
            // than anybody agreed to.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.randomizer_extra_difficulty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.randomizer_extra_difficulty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.filters.includeExtraDifficulty,
                    onCheckedChange = viewModel::setIncludeExtraDifficulty,
                )
            }

            Text(
                text = stringResource(R.string.randomizer_excluded_aspects),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Only aspects the collection can field: 'Pool came with
                // Deadpool, so without him there is no such aspect to exclude.
                state.pools.aspects.forEach { aspect ->
                    FilterChip(
                        selected = aspect in state.filters.excludedAspects,
                        onClick = { viewModel.toggleExcludedAspect(aspect) },
                        label = { Text(aspectLabel(aspect)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.randomizer_players),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { count ->
                    val selected = state.filters.minPlayers <= count &&
                        state.filters.maxPlayers >= count
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setPlayerRange(count, count) },
                        label = { Text(count.toString()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String = stringResource(
    when (difficulty) {
        Difficulty.STANDARD_I -> R.string.difficulty_standard_i
        Difficulty.STANDARD_II -> R.string.difficulty_standard_ii
        Difficulty.STANDARD_III -> R.string.difficulty_standard_iii
        Difficulty.EXPERT_I -> R.string.difficulty_expert_i
        Difficulty.EXPERT_II -> R.string.difficulty_expert_ii
    },
)


/** Heroes as code-and-aspect pairs, which is what the session route carries. */
private fun com.hasyame.marvelchampions.domain.randomizer.RandomizerDraw.asSessionHeroes(): String =
    heroes.joinToString(",") { "${it.heroCode}:${it.aspect}" }

/**
 * The values a row may be set to, already named in the reader's language.
 *
 * Drawn from the same pools the randomiser rolls against, so a hero the player
 * does not own is not offered here either.
 */
@Composable
private fun RandomizerUiState.optionsFor(field: DrawField): List<ChoiceOption> = when (field) {
    DrawField.SCENARIO -> pools.scenarios
        .map {
            ChoiceOption(
                id = it.code,
                label = names.scenarios[it.code] ?: it.code,
                detail = names.packs[it.packCode],
            )
        }
        .sortedBy { it.label }

    // Only the difficulties the filter allows. The draw has always respected
    // that filter; this picker did not, so tapping the row offered five and
    // let you pick one you had just excluded.
    DrawField.DIFFICULTY -> pools.difficulties
        .filter { it in filters.allowedDifficulties }
        .map { ChoiceOption(it.name, difficultyLabel(it)) }

    DrawField.MODULAR_SETS -> pools.modularSets
        .map {
            ChoiceOption(
                id = it.code,
                label = names.modularSets[it.code] ?: it.code,
                detail = names.packs[it.packCode],
            )
        }
        .sortedBy { it.label }

    DrawField.PLAYER_COUNT -> (1..MAX_PLAYERS).map { ChoiceOption(it.toString(), it.toString()) }

    DrawField.HEROES -> pools.heroes
        .map { ChoiceOption(it.code, names.heroes[it.code] ?: it.code) }
        .sortedBy { it.label }

    DrawField.ASPECTS -> pools.aspects.map { ChoiceOption(it, aspectLabel(it)) }
}

/** What the row currently holds, so the picker opens on it. */
private fun RandomizerUiState.selectionFor(field: DrawField): List<String> = when (field) {
    DrawField.SCENARIO -> listOfNotNull(draw.scenarioCode)
    DrawField.DIFFICULTY -> listOfNotNull(draw.difficulty?.name)
    DrawField.MODULAR_SETS -> draw.modularSetCodes
    DrawField.PLAYER_COUNT -> listOf(draw.playerCount.toString())
    DrawField.HEROES -> draw.heroes.map { it.heroCode }
    DrawField.ASPECTS -> draw.heroes.map { it.aspect }
}

/** Marvel Champions seats four. */
private const val MAX_PLAYERS = 4

/** How many values a field takes. */
fun DrawField.pickLimit(playerCount: Int): Int = when (this) {
    DrawField.SCENARIO, DrawField.DIFFICULTY, DrawField.PLAYER_COUNT -> 1
    DrawField.HEROES, DrawField.ASPECTS -> playerCount
    // No sensible cap: a scenario can take one modular set or five.
    DrawField.MODULAR_SETS -> Int.MAX_VALUE
}
