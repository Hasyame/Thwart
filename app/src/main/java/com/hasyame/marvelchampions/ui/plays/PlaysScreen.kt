package com.hasyame.marvelchampions.ui.plays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.hasyame.marvelchampions.ui.photos.TablePhotoStrip
import com.hasyame.marvelchampions.data.photos.PhotoStore
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
                    photoStore = viewModel.photoStore,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // One figure, large, because it is the one everybody opens this
            // page for. Nine numbers of equal weight meant reading all nine to
            // find it.
            Text(
                text = percent(state.totalWon, state.totalPlayed),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.plays_summary_record,
                    state.totalPlayed,
                    state.totalWon,
                    state.totalPlayed,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WinBar(won = state.totalWon, played = state.totalPlayed)

            // Everything else, deliberately smaller. These are things worth
            // knowing rather than things worth leading with.
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

/**
 * How much of a bar is won.
 *
 * A proportion is what the eye reads without counting, and it is the honest
 * shape for this: one win from one game fills the bar, and the count beside it
 * says why that means little. A percentage on its own says 100% and stops.
 */
@Composable
private fun WinBar(
    won: Int,
    played: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (played <= 0) 0f else won.toFloat() / played.toFloat()
    Box(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private val BAR_HEIGHT = 10.dp

@Composable
private fun RowScope.Stat(value: String, label: String) {
    // Equal share of the row, so the three columns line up between rows rather
    // than each being as wide as its own number.
    Column(Modifier.weight(1f)) {
        // titleMedium rather than headlineSmall: these sit under the win
        // rate and should not compete with it.
        Text(value, style = MaterialTheme.typography.titleMedium)
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
    // Most played first. Sorted by rate, a hero won with once sat at the top on
    // 100% above one played thirty times, which is the opposite of what the
    // list is for.
    val ordered = rows.sortedWith(
        compareByDescending<WinRateRow> { it.played }
            .thenByDescending { if (it.played == 0) 0.0 else it.won.toDouble() / it.played },
    )
    items(ordered, key = { "$title-${it.key}" }) { row ->
        WinRateItem(row)
    }
}

/**
 * One line of the list: who, how well, and how much.
 *
 * The three facts used to be one run of right-aligned text, "67%  (8/12)  ·  1h
 * 11m", which is four separators competing with the name for the same line. The
 * bar carries the rate, the counts sit under the name where there is room, and
 * the hours stay because "time spent with this hero" is a thing people want.
 */
@Composable
private fun WinRateItem(row: WinRateRow) {
    ListItem(
        headlineContent = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(readableKey(row.key))
                Text(
                    text = percent(row.won, row.played),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WinBar(won = row.won, played = row.played)
                Text(
                    text = pluralStringResource(
                        R.plurals.plays_row_detail,
                        row.played,
                        row.won,
                        row.played,
                        duration(row.totalMillis),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun PlayRow(
    play: PlayEntity,
    photoStore: PhotoStore,
    onDelete: () -> Unit,
    onReport: () -> Unit,
) {
    val heroes = listOfNotNull(
        play.heroName.takeIf { it.isNotBlank() },
        play.otherHeroes.takeIf { it.isNotBlank() },
    ).joinToString(", ")

    val photos = play.photos.split(",").filter { it.isNotBlank() }

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

    // Under the play rather than inside the row: a photograph is the
    // record's own, and a thumbnail squeezed into a list item's trailing
    // slot beside two buttons is a thumbnail nobody can see.
    TablePhotoStrip(
        names = photos,
        photoStore = photoStore,
        onOpen = { },
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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
