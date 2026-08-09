package com.hasyame.marvelchampions.ui.plays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicEmptyState
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.db.dao.WinRateRow
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import java.text.DateFormat
import java.util.Date

/**
 * Everything played, and what it adds up to.
 *
 * Statistics first, history below: after a few dozen games the interesting
 * question is "which heroes actually win for me", not "what did I play in
 * March".
 *
 * [onBack] is null when this is a tab root: a top level destination has nothing
 * to go back to, and an arrow there is a lie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaysScreen(
    onBack: (() -> Unit)? = null,
    viewModel: PlaysViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // Deleting a play cannot be undone and the history is the whole point of
    // the screen, so it asks — the same courtesy a campaign already gets.
    var confirmDelete by remember { mutableStateOf<PlayEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.plays_title)) },
                navigationIcon = {
                    onBack?.let { back ->
                        IconButton(onClick = back) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.plays.isEmpty()) {
            ComicEmptyState(
                message = stringResource(R.string.plays_empty),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        // Resolved out here: the lazy list scope is not composable, so a
        // stringResource call inside it does not compile.
        val sections = listOf(
            stringResource(R.string.plays_by_hero) to state.byHero,
            stringResource(R.string.plays_by_aspect) to state.byAspect,
            stringResource(R.string.plays_by_scenario) to state.byScenario,
            stringResource(R.string.plays_by_difficulty) to state.byDifficulty,
            // Solo and group win rates differ enormously; a blended figure
            // describes neither.
            stringResource(R.string.plays_by_players) to state.bySoloOrGroup,
            // The pairing a player actually asks about: not how Justice does,
            // but how Justice does for this hero.
            stringResource(R.string.plays_by_hero_aspect) to state.byHeroAspect,
        )
        val historyTitle = stringResource(R.string.plays_history)

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Overall(state) }

            sections.forEach { (title, rows) -> winRateSection(title, rows) }

            item {
                Text(
                    text = historyTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.plays, key = { it.id }) { play ->
                PlayRow(
                    play = play,
                    onDelete = { confirmDelete = play },
                    onReport = { viewModel.reportLater(play.id) },
                )
                HorizontalDivider()
            }
        }
    }

    confirmDelete?.let { play ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.plays_delete_title)) },
            text = { Text(stringResource(R.string.plays_delete_message, play.scenarioName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(play.id)
                        confirmDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    viewModel.pendingReport.collectAsStateWithLifecycle().value?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReport,
            title = { Text(stringResource(R.string.plays_send_title)) },
            text = { Text(stringResource(R.string.plays_send_message, pending.summary)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmReport(pending.playId) }) {
                    Text(stringResource(R.string.plays_send_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReport) {
                    Text(stringResource(R.string.plays_send_no))
                }
            },
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }
}

@Composable
private fun Overall(state: PlaysUiState) {
    ComicPanel(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(state.totalPlayed.toString(), stringResource(R.string.plays_stat_played))
                Stat(state.totalWon.toString(), stringResource(R.string.plays_stat_won))
                Stat(
                    value = percent(state.totalWon, state.totalPlayed),
                    label = stringResource(R.string.plays_stat_rate),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(duration(state.totalMillis), stringResource(R.string.plays_stat_total_time))
                Stat(duration(state.averageMillis), stringResource(R.string.plays_stat_average))
                Stat(duration(state.longestMillis), stringResource(R.string.plays_stat_longest))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(state.currentStreak.toString(), stringResource(R.string.plays_stat_streak))
                Stat(state.bestStreak.toString(), stringResource(R.string.plays_stat_best_streak))
                Stat(
                    value = state.campaignPlays.toString(),
                    label = stringResource(R.string.plays_stat_campaign),
                )
            }
        }
    }
}

@Composable
private fun RowScope.Stat(value: String, label: String) {
    // Equal share of the row, so the three columns line up between rows rather
    // than each being as wide as its own number.
    Column(Modifier.weight(1f)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.winRateSection(
    title: String,
    rows: List<WinRateRow>,
) {
    if (rows.isEmpty()) {
        return
    }
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
    }
    items(rows, key = { "$title-${it.key}" }) { row ->
        ListItem(
            headlineContent = { Text(readableKey(row.key)) },
            trailingContent = {
                Text(
                    // Both the rate and the raw counts: a single win from one
                    // game is 100%, and the counts are what stop that reading
                    // as a fact about the hero.
                    text = "${percent(row.won, row.played)}  (${row.won}/${row.played})  ·  ${duration(row.totalMillis)}",
                    style = MaterialTheme.typography.labelLarge,
                )
            },
        )
    }
}

@Composable
private fun PlayRow(play: PlayEntity, onDelete: () -> Unit, onReport: () -> Unit) {
    val heroes = listOfNotNull(
        play.heroName.takeIf { it.isNotBlank() },
        play.otherHeroes.takeIf { it.isNotBlank() },
    ).joinToString(", ")

    ListItem(
        overlineContent = {
            Text(DateFormat.getDateInstance().format(Date(play.playedAt)))
        },
        headlineContent = {
            Text(
                text = stringResource(
                    if (play.won) R.string.plays_won else R.string.plays_lost,
                    play.scenarioName,
                ),
                color = if (play.won) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    heroes.takeIf { it.isNotBlank() },
                    play.aspects.takeIf { it.isNotBlank() },
                    play.difficulty,
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            Row {
                // Only offered for a play that has not been sent, so the button
                // is never a way to file the same game twice.
                if (!play.reportedToBgg) {
                    TextButton(onClick = onReport) {
                        Text(stringResource(R.string.plays_send_short))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }
        },
    )
}

private fun percent(part: Int, whole: Int): String =
    if (whole == 0) "—" else "${part * 100 / whole}%"

/**
 * A duration a person would say out loud: "12h 30m", "45m", or a dash when
 * nothing was timed. Seconds are never interesting at this scale.
 */
private fun duration(millis: Long): String {
    if (millis <= 0L) {
        return "—"
    }
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/**
 * Turns a grouping key into something a person reads.
 *
 * Some keys are machine tokens: the solo and group split comes straight out of
 * a CASE expression, and difficulty is stored as the enum name so that two
 * screens agree on it. Both were being shown raw — "group", "standard_i" —
 * and neither is translated.
 */
@Composable
private fun readableKey(key: String): String = when (key) {
    "solo" -> stringResource(R.string.plays_players_solo)
    "group" -> stringResource(R.string.plays_players_group)
    "standard_i" -> stringResource(R.string.difficulty_standard_i)
    "standard_ii" -> stringResource(R.string.difficulty_standard_ii)
    "expert_i" -> stringResource(R.string.difficulty_expert_i)
    "expert_ii" -> stringResource(R.string.difficulty_expert_ii)
    // A campaign records its own difficulty word, and heroes, aspects and
    // scenarios are already names. Those pass through as they are.
    else -> key.replaceFirstChar(Char::uppercase)
}
