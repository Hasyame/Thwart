package com.hasyame.marvelchampions.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicEmptyState
import com.hasyame.marvelchampions.core.designsystem.component.ComicLoadingScreen
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.domain.achievements.AchievementCategory
import com.hasyame.marvelchampions.domain.achievements.AchievementStatusKind
import com.hasyame.marvelchampions.domain.achievements.DifficultyLevel
import java.text.DateFormat
import java.util.Date

/**
 * The achievements: the completion, the heroes × scenarios grid, and the
 * named ones. Its own page, reached from the home page, the play hub and
 * the statistics, never a tab of them: statistics look back, this says
 * what to play next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.achievements_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> ComicLoadingScreen(modifier = Modifier.padding(padding))
            state.refused != null -> ComicEmptyState(
                message = stringResource(R.string.achievements_unavailable_file),
                modifier = Modifier.padding(padding),
            )

            else -> AchievementsContent(state, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AchievementsContent(state: AchievementsUiState, viewModel: AchievementsViewModel, modifier: Modifier) {
    val byCategory = remember(state.cards) { state.cards.groupBy { it.category } }
    LazyColumn(
        modifier = modifier.fillMaxSize().halftone(MaterialTheme.colorScheme.onBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.achievements_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { CompletionPanel(state) }
        if (state.recent.isNotEmpty()) {
            item { RecentPanel(state.recent) }
        }
        item { GridPanel(state, viewModel) }
        item {
            Text(
                stringResource(R.string.achievements_list_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        AchievementCategory.entries.forEach { category ->
            val cards = byCategory[category].orEmpty()
            if (cards.isEmpty()) {
                return@forEach
            }
            item(key = "category-$category") {
                Text(
                    stringResource(AchievementTexts.category(category)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(cards, key = { it.id }) { card -> AchievementRow(card) }
        }
    }
}

/** The rate over the collection, big, with the absolute counts it stands for. */
@Composable
private fun CompletionPanel(state: AchievementsUiState) {
    Panel {
        Text(
            stringResource(R.string.achievements_completion_rate, percent(state.owned.won, state.owned.cells)),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.achievements_completion_owned, state.owned.won, state.owned.cells),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { fraction(state.owned.won, state.owned.cells) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Text(
            stringResource(R.string.achievements_completion_global, state.global.won, state.global.cells) +
                " · " + stringResource(R.string.achievements_list_count, state.unlockedCount, state.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentPanel(recent: List<AchievementCard>) {
    Panel {
        Text(
            stringResource(R.string.achievements_recent_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        recent.forEach { card ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AchievementBadgeImage(card.badge, unlocked = true, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(titleOf(card), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    card.unlockedAt?.let {
                        Text(
                            stringResource(R.string.achievements_list_unlocked_on, dateOf(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// --- the grid --------------------------------------------------------------------

@Composable
private fun GridPanel(state: AchievementsUiState, viewModel: AchievementsViewModel) {
    val filters = state.filters
    var selected by remember { mutableStateOf<Pair<GridScenario, GridHero>?>(null) }
    Panel {
        Text(
            stringResource(R.string.achievements_grid_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(
                label = stringResource(R.string.achievements_filter_hero_pack),
                value = state.heroPacks.firstOrNull { it.code == filters.heroPack }?.name ?: stringResource(R.string.achievements_filter_all),
                options = listOf(null to stringResource(R.string.achievements_filter_all)) + state.heroPacks.map { it.code to it.name },
                onPick = viewModel::setHeroPack,
                modifier = Modifier.weight(1f),
            )
            Picker(
                label = stringResource(R.string.achievements_filter_scenario_pack),
                value = state.scenarioPacks.firstOrNull { it.code == filters.scenarioPack }?.name ?: stringResource(R.string.achievements_filter_all),
                options = listOf(null to stringResource(R.string.achievements_filter_all)) + state.scenarioPacks.map { it.code to it.name },
                onPick = viewModel::setScenarioPack,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(
                label = stringResource(R.string.achievements_filter_aspect),
                value = filters.aspect?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.achievements_filter_all),
                options = listOf(null to stringResource(R.string.achievements_filter_all)) +
                    state.aspects.map { it to it.replaceFirstChar { c -> c.uppercase() } },
                onPick = viewModel::setAspect,
                modifier = Modifier.weight(1f),
            )
            Picker(
                label = stringResource(R.string.achievements_filter_min_difficulty),
                value = if (filters.minLevel == DifficultyLevel.UNKNOWN) {
                    stringResource(R.string.achievements_filter_all)
                } else {
                    stringResource(AchievementTexts.level(filters.minLevel))
                },
                options = listOf(DifficultyLevel.UNKNOWN to stringResource(R.string.achievements_filter_all)) +
                    listOf(DifficultyLevel.STANDARD, DifficultyLevel.EXPERT).map { it to stringResource(AchievementTexts.level(it)) },
                onPick = { viewModel.setMinLevel(it ?: DifficultyLevel.UNKNOWN) },
                modifier = Modifier.weight(1f),
            )
        }
        Tick(
            checked = filters.anySeat,
            label = stringResource(if (filters.anySeat) R.string.achievements_filter_any_seat else R.string.achievements_filter_owner_seat),
            onChange = viewModel::setAnySeat,
        )
        Tick(
            checked = filters.showLosses,
            label = stringResource(R.string.achievements_filter_show_losses),
            onChange = viewModel::setShowLosses,
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(cellColor(CellState.NEVER), stringResource(R.string.achievements_grid_legend_never))
            Legend(cellColor(CellState.PLAYED), stringResource(R.string.achievements_grid_legend_played))
            Legend(cellColor(CellState.WON), stringResource(R.string.achievements_grid_legend_won))
        }

        if (state.rows.isEmpty() || state.columns.isEmpty()) {
            Text(stringResource(R.string.achievements_grid_empty), style = MaterialTheme.typography.bodyMedium)
            return@Panel
        }

        // The tapped cell, read out above the grid, where it stays in view
        // however far down the tapped row is.
        selected?.let { (row, hero) ->
            val cell = state.cell(row.key, hero.code)
            Text(
                stringResource(R.string.achievements_grid_cell, hero.name, row.name, cell?.attempts ?: 0, cell?.wins ?: 0) +
                    (cell?.bestLevelWon?.let { " · " + stringResource(R.string.achievements_grid_best_level, stringResource(AchievementTexts.level(it))) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Scenario names down the side, kept in view; the heroes and their
        // cells scroll sideways together.
        val scroll = rememberScrollState()
        Row(Modifier.fillMaxWidth()) {
            Column {
                Spacer(Modifier.height(HEADER_HEIGHT))
                state.rows.forEach { row ->
                    Box(Modifier.height(CELL_SIZE).width(LABEL_WIDTH), contentAlignment = Alignment.CenterStart) {
                        Text(
                            row.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }
            Column(Modifier.horizontalScroll(scroll)) {
                Row(Modifier.height(HEADER_HEIGHT), verticalAlignment = Alignment.Bottom) {
                    state.columns.forEach { hero ->
                        Box(Modifier.size(CELL_SIZE), contentAlignment = Alignment.Center) {
                            AchievementBadgeImage(AchievementBadge(hero.imageSrc, null), unlocked = true, size = CELL_SIZE - 4.dp)
                        }
                    }
                }
                CellCanvas(state, selected, onSelect = { selected = it })
            }
        }
    }
}

/**
 * The cells, drawn rather than composed: seventy heroes by sixty scenarios
 * is four thousand of them, and four thousand boxes with a click each took
 * seconds to lay out on a modest phone. One drawing, one tap handler that
 * works out which cell was under the finger.
 */
@Composable
private fun CellCanvas(
    state: AchievementsUiState,
    selected: Pair<GridScenario, GridHero>?,
    onSelect: (Pair<GridScenario, GridHero>) -> Unit,
) {
    val never = cellColor(CellState.NEVER)
    val played = cellColor(CellState.PLAYED)
    val won = cellColor(CellState.WON)
    val measurer = rememberTextMeasurer()
    val markStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val columns = state.columns
    val rows = state.rows
    Canvas(
        Modifier
            .size(CELL_SIZE * columns.size, CELL_SIZE * rows.size)
            .pointerInput(columns, rows) {
                detectTapGestures { offset ->
                    val cell = CELL_SIZE.toPx()
                    val column = (offset.x / cell).toInt()
                    val row = (offset.y / cell).toInt()
                    if (column in columns.indices && row in rows.indices) {
                        onSelect(rows[row] to columns[column])
                    }
                }
            },
    ) {
        val cell = CELL_SIZE.toPx()
        val inset = 1.dp.toPx()
        val radius = CornerRadius(3.dp.toPx())
        rows.forEachIndexed { r, row ->
            columns.forEachIndexed { c, hero ->
                val value = state.cell(row.key, hero.code)
                val colour = when (value?.state) {
                    CellState.WON -> won
                    CellState.PLAYED -> played
                    else -> never
                }
                val topLeft = Offset(c * cell + inset, r * cell + inset)
                val size = Size(cell - 2 * inset, cell - 2 * inset)
                drawRoundRect(colour, topLeft, size, radius)
                if (selected != null && selected.first.key == row.key && selected.second.code == hero.code) {
                    drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft, size, radius)
                }
                val mark = when (value?.bestLevelWon) {
                    DifficultyLevel.EXPERT -> "E"
                    DifficultyLevel.STANDARD -> "S"
                    else -> null
                }
                if (mark != null) {
                    val text = measurer.measure(mark, markStyle)
                    drawText(
                        text,
                        topLeft = Offset(
                            topLeft.x + (size.width - text.size.width) / 2,
                            topLeft.y + (size.height - text.size.height) / 2,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun cellColor(state: CellState): Color = when (state) {
    // Darker than the panel it sits on, so an empty cell still reads as a cell.
    CellState.NEVER -> MaterialTheme.colorScheme.surface
    CellState.PLAYED -> Color(0xFFE0B94A)
    CellState.WON -> MaterialTheme.colorScheme.primary
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** A labelled choice: the value on a button, the options in a menu under it. */
@Composable
private fun <T> Picker(
    label: String,
    value: String,
    options: List<Pair<T?, String>>,
    onPick: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (option, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = {
                        open = false
                        onPick(option)
                    })
                }
            }
        }
    }
}

@Composable
private fun Tick(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// --- the named ones -----------------------------------------------------------------

/**
 * One achievement: its badge, its words, where it stands. Unlocked ones
 * carry their date; the rest their progress, and a note when the box is
 * not in the collection, which keeps the progress rather than hiding it.
 */
@Composable
private fun AchievementRow(card: AchievementCard) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AchievementBadgeImage(card.badge, unlocked = card.unlocked, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(titleOf(card), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        descriptionOf(card),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when (card.status) {
                        AchievementStatusKind.UNLOCKED -> card.unlockedAt?.let { dateOf(it) } ?: stringResource(R.string.achievements_list_unlocked)
                        AchievementStatusKind.UNAVAILABLE -> stringResource(R.string.achievements_list_unavailable)
                        AchievementStatusKind.LOCKED -> stringResource(R.string.achievements_list_progress, card.progress.current, card.progress.target)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (card.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
            if (!card.unlocked) {
                LinearProgressIndicator(
                    progress = { fraction(card.progress.current, card.progress.target) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (card.tiers.isNotEmpty()) {
                val tierNames = card.tiers.map { stringResource(AchievementTexts.tier(it.tier)) }
                Text(
                    card.tiers.zip(tierNames).joinToString(" · ") { (tier, name) ->
                        if (card.tier != null && tier.tier.ordinal <= card.tier.ordinal) "✓ $name ${tier.n}" else "$name ${tier.n}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (card.status == AchievementStatusKind.UNAVAILABLE) {
                Text(
                    stringResource(R.string.achievements_list_unavailable_hint) + " · " +
                        stringResource(R.string.achievements_list_progress, card.progress.current, card.progress.target),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun titleOf(card: AchievementCard): String =
    AchievementTexts.title(card.id)?.let { stringResource(it) } ?: card.id

@Composable
fun descriptionOf(card: AchievementCard): String {
    AchievementTexts.countDescription(card.id)?.let { plural ->
        val n = card.tiers.lastOrNull()?.n ?: card.progress.target
        return pluralStringResource(plural, n, n)
    }
    val res = AchievementTexts.description(card.id) ?: return ""
    return if (card.packName != null) stringResource(res, card.packName) else stringResource(res)
}

@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

private fun percent(won: Int, cells: Int): Int = if (cells == 0) 0 else Math.round(won * 100f / cells)

private fun fraction(current: Int, target: Int): Float = if (target == 0) 0f else (current.toFloat() / target).coerceIn(0f, 1f)

fun dateOf(epochMillis: Long): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(epochMillis))

private val CELL_SIZE = 26.dp
private val HEADER_HEIGHT = 30.dp
private val LABEL_WIDTH = 96.dp
