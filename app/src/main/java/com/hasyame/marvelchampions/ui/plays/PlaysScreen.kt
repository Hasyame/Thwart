package com.hasyame.marvelchampions.ui.plays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import kotlinx.coroutines.launch
import com.hasyame.marvelchampions.core.designsystem.component.ComicEmptyState
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.aspectColor
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.theme.HeadingFill
import com.hasyame.marvelchampions.core.designsystem.theme.HeadingInk
import com.hasyame.marvelchampions.data.db.dao.WinRateRow
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.ui.photos.TablePhotoStrip
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.ui.util.aspectLabel
import com.hasyame.marvelchampions.ui.util.labelRes
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Everything played, and what it adds up to.
 *
 * Statistics first, history below: after a few dozen games the interesting
 * question is "which heroes actually win for me", not "what did I play in
 * March".
 *
 * The tables are behind headed boxes that open when tapped. Six tables laid out
 * end to end is a page nobody reaches the bottom of, and only one of them is
 * ever the one you came for.
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

    // Survives rotation: opening a table, turning the phone and finding it shut
    // again is the screen forgetting what you were reading.
    var openSections by rememberSaveable { mutableStateOf(setOf(StatTable.HERO.name)) }
    // Tables that have been asked to show everything rather than their first
    // few rows.
    var fullSections by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var sort by rememberSaveable { mutableStateOf(StatSort.ALPHABETICAL) }
    var query by rememberSaveable { mutableStateOf("") }
    var historyPage by rememberSaveable { mutableStateOf(0) }
    var historyOpen by rememberSaveable { mutableStateOf(true) }
    // What each box is measuring, by box. Kept per box because the useful
    // question differs: which aspect you reach for is a share, whether a hero
    // wins is a rate.
    var metrics by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    fun metricOf(key: String): StatMetric =
        metrics[key]?.let { StatMetric.valueOf(it) } ?: StatMetric.WIN_RATE

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

        val tables = StatTable.entries.map { it to it.rowsOf(state) }.filter { it.second.isNotEmpty() }
        val historyTitle = stringResource(R.string.plays_history)

        val pageCount = ((state.plays.size + HISTORY_PAGE - 1) / HISTORY_PAGE).coerceAtLeast(1)
        // Clamped rather than remembered blindly: deleting the last game on the
        // last page would otherwise leave the list showing a page that has
        // stopped existing.
        val page = historyPage.coerceIn(0, pageCount - 1)
        val pagePlays = state.plays.drop(page * HISTORY_PAGE).take(HISTORY_PAGE)

        // Where the history heading sits in the list, counted from the items
        // added below it. Derived rather than searched for: the conditions are
        // the same ones three lines down, so the two cannot drift apart without
        // both being edited at once.
        val historyIndex = 1 +
            (if (state.byAspect.isNotEmpty()) 1 else 0) +
            (if (tables.isNotEmpty()) 1 else 0) +
            tables.size
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState) {
            item(key = "overall") { Overall(state) }
            if (state.byAspect.isNotEmpty()) {
                item(key = "aspects") {
                    AspectChart(
                        rows = state.byAspect,
                        metric = metricOf(ASPECT_PANEL),
                        onMetric = { metrics = metrics + (ASPECT_PANEL to it.name) },
                    )
                }
            }
            if (tables.isNotEmpty()) {
                item(key = "controls") {
                    TableControls(
                        query = query,
                        onQuery = { query = it },
                        sort = sort,
                        onSort = { sort = it },
                    )
                }
            }

            tables.forEach { (table, rows) ->
                item(key = table.name) {
                    StatSection(
                        table = table,
                        rows = rows,
                        sort = sort,
                        query = query,
                        metric = metricOf(table.name),
                        // A closed box hides the very rows being searched for,
                        // so a search opens every table that has a match.
                        expanded = table.name in openSections || query.isNotBlank(),
                        showAll = table.name in fullSections || query.isNotBlank(),
                        onToggle = {
                            openSections = if (table.name in openSections) {
                                openSections - table.name
                            } else {
                                openSections + table.name
                            }
                        },
                        onShowAll = {
                            fullSections = if (table.name in fullSections) {
                                fullSections - table.name
                            } else {
                                fullSections + table.name
                            }
                        },
                        onMetric = { metrics = metrics + (table.name to it.name) },
                    )
                }
            }

            item(key = "history") {
                HistorySection(
                    title = historyTitle,
                    total = state.plays.size,
                    plays = pagePlays,
                    page = page,
                    pageCount = pageCount,
                    expanded = historyOpen,
                    photoStore = viewModel.photoStore,
                    onToggle = { historyOpen = !historyOpen },
                    onPage = {
                        historyPage = it
                        // Turning a page and landing halfway down it reads as
                        // nothing having happened.
                        scope.launch { listState.animateScrollToItem(historyIndex) }
                    },
                    onDelete = { confirmDelete = it },
                    onReport = { viewModel.reportLater(it.id) },
                )
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

/**
 * The breakdowns, each its own box.
 *
 * Aspect is not among them: it has its own panel above, which is the same table
 * drawn in the colours the game prints, and two boxes of identical numbers is
 * not a fuller page.
 */
private enum class StatTable(val titleRes: Int) {
    HERO(R.string.plays_by_hero),
    SCENARIO(R.string.plays_by_scenario),
    DIFFICULTY(R.string.plays_by_difficulty),
    // Solo and group win rates differ enormously; a blended figure describes
    // neither.
    PLAYERS(R.string.plays_by_players),
    // The pairing a player actually asks about: not how Justice does, but how
    // Justice does for this hero.
    HERO_ASPECT(R.string.plays_by_hero_aspect),
    ;

    fun rowsOf(state: PlaysUiState): List<WinRateRow> = when (this) {
        HERO -> state.byHero
        SCENARIO -> state.byScenario
        DIFFICULTY -> state.byDifficulty
        PLAYERS -> state.bySoloOrGroup
        HERO_ASPECT -> state.byHeroAspect
    }
}

private enum class StatSort(val labelRes: Int) {
    ALPHABETICAL(R.string.plays_sort_alpha),
    MOST_PLAYED(R.string.plays_sort_played),
    BEST_RATE(R.string.plays_sort_rate),

    /**
     * The same table read from the other end.
     *
     * Best-first answers "what am I good at", which is the pleasant question
     * and not usually the useful one. What loses is what you would change.
     */
    WORST_RATE(R.string.plays_sort_worst_rate),
}

/**
 * What a bar and its percentage are measuring.
 *
 * There used to be no answer to that question. A bar was drawn at one quantity
 * and the number beside it printed another — the aspect chart sized its bars by
 * games played and then wrote the win rate on the end, so "138 · 61%" was two
 * unrelated facts sharing a line with nothing to say which was which. Each box
 * now states its measure and lets you change it.
 */
internal enum class StatMetric(val labelRes: Int) {
    WIN_RATE(R.string.plays_metric_wins),

    /**
     * Losses, the same figure the other way up.
     *
     * Not redundant with [WIN_RATE] on a chart: the eye reads the long bar, so
     * a page of wins shows what goes well and hides what does not. Switching
     * the measure re-sorts and re-draws, and the hero you keep losing with
     * becomes the longest bar instead of the shortest.
     */
    LOSS_RATE(R.string.plays_metric_losses),
    SHARE_PLAYED(R.string.plays_metric_games),
    SHARE_TIME(R.string.plays_metric_time),
}

/** What one row is worth under [metric], before any total is applied. */
internal fun rawValue(row: WinRateRow, metric: StatMetric): Double = when (metric) {
    StatMetric.WIN_RATE -> if (row.played == 0) 0.0 else row.won.toDouble() / row.played
    // Losses over games played, not one minus the win rate: a row with no games
    // has no rate either way, and the subtraction would have called it a
    // hundred percent defeats.
    StatMetric.LOSS_RATE ->
        if (row.played == 0) 0.0 else (row.played - row.won).toDouble() / row.played
    StatMetric.SHARE_PLAYED -> row.played.toDouble()
    StatMetric.SHARE_TIME -> row.totalMillis.toDouble()
}

/**
 * The percentage to print, and how full to draw the bar. They are not always
 * the same number.
 *
 * A win rate is already a proportion, so the bar is that proportion and the two
 * agree. A share is not: one hero out of forty-seven is three percent of the
 * table, and a bar drawn three percent full is a bar nobody can compare to the
 * one below it. So a share prints its true percentage and draws itself against
 * the largest row, which is what a bar chart has always done.
 */
internal fun measure(
    row: WinRateRow,
    metric: StatMetric,
    total: Double,
    largest: Double,
): Pair<String, Float> {
    val raw = rawValue(row, metric)
    return when (metric) {
        // Both are already proportions of this row's own games, so the bar is
        // that proportion and the number beside it agrees. Losses fell into
        // the share branch below and were divided by the total of every row's
        // rate, which printed a hero who loses half his games as something
        // like twelve percent.
        StatMetric.WIN_RATE, StatMetric.LOSS_RATE -> percentOf(raw) to raw.toFloat()
        else -> {
            val share = if (total <= 0.0) 0.0 else raw / total
            val bar = if (largest <= 0.0) 0.0 else raw / largest
            percentOf(share) to bar.toFloat()
        }
    }
}

private fun percentOf(fraction: Double): String =
    if (fraction.isNaN()) "—" else "${(fraction * 100).roundToInt()}%"

/**
 * The chips that say what a box is measuring.
 *
 * Inside the box rather than up with the sort control, because the useful
 * question differs per table: which aspect you reach for most is a share, and
 * whether it wins is a rate.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricChips(metric: StatMetric, onMetric: (StatMetric) -> Unit) {
    // A FlowRow, and no "Showing" label in front of it any more.
    //
    // A fourth measure did not fit. The label plus four chips overran the width
    // on a phone, in French first, and a plain Row does not wrap: it squeezes
    // the chips until the words break. The label was the least of the four
    // things on the line — a row of chips above a chart is read as what the
    // chart is measuring without being told — so it went, and what is left
    // wraps rather than deforming when it does not fit.
    FlowRow(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatMetric.entries.forEach { option ->
            FilterChip(
                selected = metric == option,
                onClick = { onMetric(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}

/**
 * One filter and one order, for every table at once.
 *
 * A search box per table would be six of them, and the question is almost never
 * "where does Spider-Man appear in this particular table" — it is "where does
 * Spider-Man appear". Filtering everything from one field answers that, and
 * costs one piece of state instead of six.
 */
@Composable
private fun TableControls(
    query: String,
    onQuery: (String) -> Unit,
    sort: StatSort,
    onSort: (StatSort) -> Unit,
) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.plays_filter_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.plays_filter_clear),
                        )
                    }
                }
            },
        )
        // No heading, and a row that wraps, for the reason the measure chips
        // have neither: a fourth order did not fit beside a sentence, and a
        // plain Row squeezes its children rather than moving them down. Four
        // chips under a search box are read as what orders the tables without
        // a line of prose saying so.
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StatSort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { onSort(option) },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
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
            WinBar(
                fraction = if (state.totalPlayed <= 0) {
                    0f
                } else {
                    state.totalWon.toFloat() / state.totalPlayed
                },
            )

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
 * The aspects, in the colours the game prints them.
 *
 * This is the by-aspect table, drawn rather than listed: there are only five
 * aspects and they already have colours a player reads without a legend, which
 * is exactly the case a chart is for. Ordered by the measure on show, so the
 * longest bar is always the top row.
 */
@Composable
private fun AspectChart(
    rows: List<WinRateRow>,
    metric: StatMetric,
    onMetric: (StatMetric) -> Unit,
) {
    val ordered = remember(rows, metric) {
        rows.sortedByDescending { rawValue(it, metric) }
    }
    val total = ordered.sumOf { rawValue(it, metric) }
    val largest = ordered.maxOfOrNull { rawValue(it, metric) } ?: 0.0
    if (ordered.isEmpty()) {
        return
    }

    ComicPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HeadingFill)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.plays_aspects_chart),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HeadingInk,
                )
            }
            MetricChips(metric = metric, onMetric = onMetric)
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ordered.forEach { row ->
                    AspectBar(row = row, metric = metric, total = total, largest = largest)
                }
            }
        }
    }
}

@Composable
private fun AspectBar(
    row: WinRateRow,
    metric: StatMetric,
    total: Double,
    largest: Double,
) {
    val label = aspectLabel(row.key)
    val colour = aspectColor(row.key) ?: MaterialTheme.colorScheme.primary
    val (text, fill) = measure(row, metric, total, largest)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colour),
            )
        }
        // The raw counts under every bar, so the percentage above is never the
        // only thing on screen and can always be checked against something.
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
}

/**
 * A headed box holding one table, shut until tapped.
 *
 * The whole header is the target rather than the chevron: a 48dp strip across
 * the screen is what a thumb actually hits, and a chevron alone is a dot.
 */
@Composable
private fun StatSection(
    table: StatTable,
    rows: List<WinRateRow>,
    sort: StatSort,
    query: String,
    metric: StatMetric,
    expanded: Boolean,
    showAll: Boolean,
    onToggle: () -> Unit,
    onShowAll: () -> Unit,
    onMetric: (StatMetric) -> Unit,
) {
    val title = stringResource(table.titleRes)
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val visible = sortedRows(rows, sort, query)

    // A table with nothing matching the filter is not a table worth a box: six
    // empty headers is the search telling you nothing six times.
    if (query.isNotBlank() && visible.isEmpty()) {
        return
    }

    ComicPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HeadingFill)
                    .clickable(
                        onClick = onToggle,
                        onClickLabel = stringResource(
                            if (expanded) R.string.plays_section_hide else R.string.plays_section_show,
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HeadingInk,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    // The matching count while filtering, so the header says
                    // how much of the table the search actually left.
                    text = pluralStringResource(
                        R.plurals.plays_section_entries,
                        visible.size,
                        visible.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = HeadingInk,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    // The row already announces what tapping it does, so the
                    // chevron would only repeat it.
                    contentDescription = null,
                    tint = HeadingInk,
                    modifier = Modifier.rotate(turn),
                )
            }

            AnimatedVisibility(expanded) {
                // A Column rather than nested lazy items: nesting a scroller
                // inside the page scroller is how a list stops scrolling with
                // the thumb. What keeps it cheap is the cap below — an opened
                // table composes five rows, not ninety.
                Column {
                    MetricChips(metric = metric, onMetric = onMetric)
                    // Totals over the whole table, not the five rows on show,
                    // or a share would change meaning when the rest unfolded.
                    val total = visible.sumOf { rawValue(it.second, metric) }
                    val largest = visible.maxOfOrNull { rawValue(it.second, metric) } ?: 0.0

                    val shown = if (showAll) visible else visible.take(PREVIEW_ROWS)
                    shown.forEach { (label, row) ->
                        WinRateItem(
                            label = label,
                            row = row,
                            metric = metric,
                            total = total,
                            largest = largest,
                        )
                    }

                    if (visible.size > PREVIEW_ROWS) {
                        TextButton(
                            onClick = onShowAll,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        ) {
                            Text(
                                if (showAll) {
                                    stringResource(R.string.plays_show_fewer)
                                } else {
                                    stringResource(R.string.plays_show_all, visible.size)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How many rows an opened table shows before it offers the rest. */
private const val PREVIEW_ROWS = 5

/** The aspect panel's key in the per-box metric map. Not a StatTable. */
private const val ASPECT_PANEL = "ASPECTS"

/**
 * The rows, labelled in the reader's language, filtered and put in order.
 *
 * Alphabetical is the default because a table you are looking something up in
 * has to be one you can look something up in. The other two orders answer
 * different questions and are a tap away.
 *
 * Filtering is on the label rather than the key, so what you type is what you
 * can see — a French reader searching "Agressivité" is not matched against
 * "aggression".
 */
@Composable
private fun sortedRows(
    rows: List<WinRateRow>,
    sort: StatSort,
    query: String,
): List<Pair<String, WinRateRow>> {
    // A for loop, not a map: readableKey resolves string resources, so it can
    // only be called from a composable body.
    val needle = query.trim()
    val labelled = ArrayList<Pair<String, WinRateRow>>(rows.size)
    for (row in rows) {
        val label = readableKey(row.key)
        if (needle.isEmpty() || label.contains(needle, ignoreCase = true)) {
            labelled += label to row
        }
    }
    return when (sort) {
        StatSort.ALPHABETICAL -> labelled.sortedBy { it.first.lowercase() }
        StatSort.MOST_PLAYED -> labelled.sortedWith(
            compareByDescending<Pair<String, WinRateRow>> { it.second.played }
                .thenBy { it.first.lowercase() },
        )
        // Ties broken by plays, so thirty games at 60% sit above one game at
        // 60% rather than landing wherever the query happened to put them.
        StatSort.BEST_RATE -> labelled.sortedWith(
            compareByDescending<Pair<String, WinRateRow>> { rate(it.second) }
                .thenByDescending { it.second.played },
        )
        // Ties broken by plays again, and the same way round: among equally bad
        // records the one played most is the one worth reading first.
        StatSort.WORST_RATE -> labelled.sortedWith(
            compareBy<Pair<String, WinRateRow>> { rate(it.second) }
                .thenByDescending { it.second.played },
        )
    }
}

private fun rate(row: WinRateRow): Double =
    if (row.played == 0) 0.0 else row.won.toDouble() / row.played

/**
 * How much of a bar is won.
 *
 * A proportion is what the eye reads without counting, and it is the honest
 * shape for this: one win from one game fills the bar, and the count beside it
 * says why that means little. A percentage on its own says 100% and stops.
 */
@Composable
private fun WinBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
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

/**
 * One line of a table: who, how well, and how much.
 *
 * The three facts used to be one run of right-aligned text, "67%  (8/12)  ·  1h
 * 11m", which is four separators competing with the name for the same line. The
 * bar carries the rate, the counts sit under the name where there is room, and
 * the hours stay because "time spent with this hero" is a thing people want.
 */
@Composable
private fun WinRateItem(
    label: String,
    row: WinRateRow,
    metric: StatMetric,
    total: Double,
    largest: Double,
) {
    val (text, fill) = measure(row, metric, total, largest)
    ListItem(
        // The box behind it is already a surface; a second one on every row
        // draws a border around each line.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label)
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Decorative here: the line below states the same counts in
                // words, and a screen reader announcing a bar twice is noise.
                Box(Modifier.clearAndSetSemantics { }) {
                    WinBar(fraction = fill)
                }
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

/**
 * The history, in the same headed box as the tables above it.
 *
 * A Column rather than lazy items, for the reason the tables give: a scroller
 * inside a scroller is how a list stops following the thumb. Cheap here for a
 * different reason, which is that a page is ten rows whatever the history holds.
 */
@Composable
private fun HistorySection(
    title: String,
    total: Int,
    plays: List<PlayEntity>,
    page: Int,
    pageCount: Int,
    expanded: Boolean,
    photoStore: PhotoStore,
    onToggle: () -> Unit,
    onPage: (Int) -> Unit,
    onDelete: (PlayEntity) -> Unit,
    onReport: (PlayEntity) -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "history-chevron")

    ComicPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HeadingFill)
                    .clickable(
                        onClick = onToggle,
                        onClickLabel = stringResource(
                            if (expanded) R.string.plays_section_hide else R.string.plays_section_show,
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HeadingInk,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    // Every game, not the page's ten. Paging should never be
                    // mistaken for having fewer games than you have.
                    text = pluralStringResource(R.plurals.plays_section_entries, total, total),
                    style = MaterialTheme.typography.labelMedium,
                    color = HeadingInk,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = HeadingInk,
                    modifier = Modifier.rotate(turn),
                )
            }

            AnimatedVisibility(expanded) {
                Column {
                    plays.forEach { play ->
                        PlayRow(
                            play = play,
                            photoStore = photoStore,
                            onDelete = { onDelete(play) },
                            onReport = { onReport(play) },
                        )
                        HorizontalDivider()
                    }
                    if (pageCount > 1) {
                        HistoryPager(page = page, pageCount = pageCount, onPage = onPage)
                    }
                }
            }
        }
    }
}

/**
 * Ten games to a page, and the means to reach the rest.
 *
 * Ten because it is about a screenful: enough that paging is not constant, few
 * enough that a page is a thing you read rather than a thing you scroll. The
 * whole count stays in the heading, so paging never hides how much is there.
 */
@Composable
private fun HistoryPager(
    page: Int,
    pageCount: Int,
    onPage: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 0) {
            Text(stringResource(R.string.plays_page_previous))
        }
        Text(
            text = stringResource(R.string.plays_page_of, page + 1, pageCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onPage(page + 1) }, enabled = page < pageCount - 1) {
            Text(stringResource(R.string.plays_page_next))
        }
    }
}

/** A screenful, near enough. */
private const val HISTORY_PAGE = 10

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

/** The separator PlayStats uses to pair a hero with an aspect. */
private const val PAIR = " · "

/**
 * Turns a grouping key into something a person reads.
 *
 * Some keys are machine tokens: the solo and group split comes straight out of
 * a CASE expression, difficulty is stored as the enum name so that two screens
 * agree on it, and an aspect travels as its MarvelCDB identifier, which is
 * English. All three were being shown raw — "group", "standard_i",
 * "aggression" — and none is translated.
 *
 * The hero-with-aspect table pairs two of those with a separator, so each half
 * is resolved on its own.
 */
@Composable
private fun readableKey(key: String): String {
    if (PAIR in key) {
        // A for loop, because resolving each half calls a composable and
        // joinToString's lambda is not one.
        val parts = ArrayList<String>(2)
        for (part in key.split(PAIR)) {
            parts += readableKey(part)
        }
        return parts.joinToString(PAIR)
    }
    // Asked of the enum rather than listed here. This screen used to keep its
    // own copy of the difficulty names and had never been told about Standard
    // III, so it fell through to the capitaliser below and read "Standard_iii"
    // on somebody's statistics. DifficultyLabel's own comment predicted it: a
    // third copy is how the names drift apart. A difficulty added later is now
    // covered without anyone remembering this function exists.
    Difficulty.entries.firstOrNull { it.name.lowercase() == key }?.let {
        return stringResource(it.labelRes())
    }
    return when (key) {
        "players_1" -> stringResource(R.string.plays_players_solo)
        "players_2" -> stringResource(R.string.plays_players_two)
        "players_3" -> stringResource(R.string.plays_players_three)
        "players_4" -> stringResource(R.string.plays_players_four)
        "players_5plus" -> stringResource(R.string.plays_players_more)
        in ASPECT_CODES -> aspectLabel(key)
        // A campaign records its own difficulty word, and heroes and scenarios
        // are already names. Those pass through as they are.
        else -> key.replaceFirstChar(Char::uppercase)
    }
}

/**
 * The aspect identifiers, so a hero named after one is not renamed.
 *
 * Matching on the set rather than asking aspectLabel to decide keeps the
 * fallback where it belongs: anything not listed here is somebody's name.
 */
private val ASPECT_CODES = setOf(
    "aggression",
    "justice",
    "leadership",
    "protection",
    "pool",
)
