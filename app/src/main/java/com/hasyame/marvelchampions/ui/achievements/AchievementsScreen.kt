package com.hasyame.marvelchampions.ui.achievements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicEmptyState
import com.hasyame.marvelchampions.core.designsystem.component.ComicLoadingScreen
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.domain.achievements.AchievementCategory
import com.hasyame.marvelchampions.domain.achievements.AchievementStatusKind
import com.hasyame.marvelchampions.domain.achievements.DifficultyLevel
import com.hasyame.marvelchampions.ui.util.aspectLabel
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
    onCollection: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
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

            else -> AchievementsContent(state, viewModel, onCollection, onSettings, onHistory, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AchievementsContent(
    state: AchievementsUiState, viewModel: AchievementsViewModel,
    onCollection: () -> Unit, onSettings: () -> Unit, onHistory: () -> Unit, modifier: Modifier,
) {
    var selectedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var albumOpen by rememberSaveable { mutableStateOf(true) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val selected = state.columns.firstOrNull { it.code == selectedCode } ?: state.columns.firstOrNull()

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
        item {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = albumOpen, onClick = { albumOpen = true },
                    label = { Text(stringResource(R.string.achievements_album_title)) })
                FilterChip(selected = !albumOpen, onClick = { albumOpen = false },
                    label = { Text(stringResource(R.string.achievements_list_title)) })
            }
        }
        if (!albumOpen && state.recent.isNotEmpty()) {
            item { RecentPanel(state.recent) }
        }
        if (albumOpen) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { filtersOpen = !filtersOpen }) { Text(stringResource(R.string.achievements_filters)) }
                    TextButton(onClick = onHistory) { Text(stringResource(R.string.achievements_history)) }
                }
                if (filtersOpen) AchievementFilters(state, viewModel)
            }
            if (state.columns.isEmpty()) {
                item {
                    Panel {
                        Text(stringResource(R.string.achievements_grid_empty))
                        TextButton(onClick = viewModel::resetFilters) { Text(stringResource(R.string.achievements_reset_filters)) }
                        TextButton(onClick = onCollection) { Text(stringResource(R.string.achievements_choose_collection)) }
                        TextButton(onClick = onSettings) { Text(stringResource(R.string.achievements_update_cards)) }
                    }
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.columns, key = { it.code }) { hero ->
                            Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AchievementBadgeImage(AchievementBadge(hero.imageSrc, null), unlocked = true, size = 80.dp)
                                FilterChip(selected = hero.code == selected?.code, onClick = { selectedCode = hero.code },
                                    label = { Text(hero.name) })
                            }
                        }
                    }
                }
                selected?.let { hero ->
                    item { Text(hero.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    state.rows.groupBy { it.packCode }.forEach { (pack, rows) ->
                        item(key = "album-pack-$pack") { Text(rows.first().packName, style = MaterialTheme.typography.titleMedium) }
                        items(rows, key = { "album-${it.key}" }) { scenario ->
                            AlbumScenario(hero, scenario, state.cell(scenario.key, hero.code))
                        }
                    }
                }
            }
        } else {
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

@Composable
private fun AchievementFilters(state: AchievementsUiState, viewModel: AchievementsViewModel) {
    val filters = state.filters
    Panel {
        Picker(stringResource(R.string.achievements_filter_hero_pack),
            state.heroPacks.firstOrNull { it.code == filters.heroPack }?.name ?: stringResource(R.string.achievements_filter_all),
            listOf(null to stringResource(R.string.achievements_filter_all)) + state.heroPacks.map { it.code to it.name },
            viewModel::setHeroPack, Modifier.fillMaxWidth())
        Picker(stringResource(R.string.achievements_filter_scenario_pack),
            state.scenarioPacks.firstOrNull { it.code == filters.scenarioPack }?.name ?: stringResource(R.string.achievements_filter_all),
            listOf(null to stringResource(R.string.achievements_filter_all)) + state.scenarioPacks.map { it.code to it.name },
            viewModel::setScenarioPack, Modifier.fillMaxWidth())
        Picker(stringResource(R.string.achievements_filter_aspect),
            filters.aspect?.let { aspectLabel(it) } ?: stringResource(R.string.achievements_filter_all),
            listOf(null to stringResource(R.string.achievements_filter_all)) + state.aspects.map { it to aspectLabel(it) },
            viewModel::setAspect, Modifier.fillMaxWidth())
        Picker(stringResource(R.string.achievements_filter_min_difficulty),
            if (filters.minLevel == DifficultyLevel.UNKNOWN) stringResource(R.string.achievements_filter_all)
                else stringResource(AchievementTexts.level(filters.minLevel)),
            listOf(DifficultyLevel.UNKNOWN to stringResource(R.string.achievements_filter_all)) +
                listOf(DifficultyLevel.STANDARD, DifficultyLevel.EXPERT).map { it to stringResource(AchievementTexts.level(it)) },
            { viewModel.setMinLevel(it ?: DifficultyLevel.UNKNOWN) }, Modifier.fillMaxWidth())
        Tick(filters.anySeat, stringResource(R.string.achievements_filter_any_seat), viewModel::setAnySeat)
        Tick(filters.showLosses, stringResource(R.string.achievements_filter_show_losses), viewModel::setShowLosses)
        Tick(filters.everyHero, stringResource(R.string.achievements_every_hero), viewModel::setEveryHero)
        TextButton(onClick = viewModel::resetFilters) { Text(stringResource(R.string.achievements_reset_filters)) }
    }
}

@Composable
private fun AlbumScenario(hero: GridHero, scenario: GridScenario, cell: GridCell?) {
    val status = stringResource(when (cell?.state ?: CellState.NEVER) {
        CellState.NEVER -> R.string.achievements_grid_legend_never
        CellState.PLAYED -> R.string.achievements_grid_legend_played
        CellState.WON -> R.string.achievements_grid_legend_won
    })
    val counts = stringResource(R.string.achievements_grid_cell, hero.name, scenario.name, cell?.attempts ?: 0, cell?.wins ?: 0)
    Panel {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$counts. $status" },
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls.cardImage(scenario.imageSrc), contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = if (cell?.state == CellState.WON) 1f else 0.5f)
            Column(Modifier.weight(1f)) {
                Text(scenario.name, style = MaterialTheme.typography.titleMedium)
                Text(status, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.achievements_album_counts, cell?.wins ?: 0, cell?.attempts ?: 0),
                    style = MaterialTheme.typography.bodySmall)
                cell?.bestLevelWon?.let { Text(stringResource(AchievementTexts.level(it)), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

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
