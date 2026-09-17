package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem
import com.hasyame.marvelchampions.domain.deckbuilder.DeckStatisticsCalculator
import com.hasyame.marvelchampions.domain.deckbuilder.DeckText
import com.hasyame.marvelchampions.domain.deckbuilder.DeckTextCard
import com.hasyame.marvelchampions.ui.util.aspectLabel
import com.hasyame.marvelchampions.ui.util.shareText

/**
 * Building a deck, rather than looking at one.
 *
 * Legality is reported continuously rather than on a button, because the
 * useful moment for "that is a fourth copy" is when the fourth copy goes in.
 * Every tap is written at once, which is what lets two devices edit the
 * same deck without one writing over the other; there is no Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckEditorScreen(
    deckId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            EditorHeader(state)

            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.decks_tab_deck_count, state.validation.totalCards)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.decks_tab_cards_count, state.poolTotal)) },
                )
            }

            when (tab) {
                0 -> DeckTab(state, viewModel, onCardClick)
                else -> PoolTab(state, viewModel, onCardClick)
            }
        }
    }
}

/** The hero, the aspects, and how the deck stands: its size and its problems. */
@Composable
private fun EditorHeader(state: DeckEditorUiState) {
    val deck = state.deck ?: return
    val rules = state.rules
    val problems = state.validation.problems.size
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                MarvelCdbUrls.cardImage(state.heroImageSrc)?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = BiasAlignment(0f, -0.55f),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    (listOf(deck.heroName) + DeckRepository.parseAspects(deck.aspects).map { aspectLabel(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val legal = state.validation.isLegal
            val outline = if (legal) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(state.validation.totalCards.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "/ ${rules?.effectiveMinimum ?: 40}–${rules?.effectiveMaximum ?: 50}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (legal) {
                Text(stringResource(R.string.decks_legal_short), style = MaterialTheme.typography.titleSmall)
            } else {
                Text(
                    pluralStringResource(R.plurals.decks_problems, problems, problems),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The problems, folded under one line until they are wanted. */
@Composable
private fun ProblemsPanel(state: DeckEditorUiState) {
    val problems = state.validation.problems
    if (problems.isEmpty() && state.synergyWarnings.isEmpty()) {
        return
    }
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { open = !open }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (problems.isEmpty()) {
                    stringResource(R.string.decks_legal)
                } else {
                    pluralStringResource(R.plurals.decks_problems_in_deck, problems.size, problems.size)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (open) {
            problems.forEach { problem ->
                Text(problemMessage(problem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            SynergyWarningText(state.synergyWarnings)
        }
    }
}

// --- the deck -----------------------------------------------------------------------------

@Composable
private fun DeckTab(state: DeckEditorUiState, viewModel: DeckEditorViewModel, onCardClick: (String) -> Unit) {
    val heroSet = state.rules?.heroSetCode
    val (heroCards, others) = remember(state.deckCards, heroSet) {
        state.deckCards.partition { heroSet != null && it.cardSetCode == heroSet }
    }
    val byType = remember(others, state.sort) {
        others.sortedWith(state.sort.comparator).groupBy { it.typeName }
    }
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "problems") { ProblemsPanel(state) }

        if (state.deckCards.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.decks_editor_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (heroCards.isNotEmpty()) {
            item(key = "hero-cards") {
                GroupHeading(stringResource(R.string.decks_hero_cards), heroCards.sumOf { state.slots[it.code] ?: 0 })
                Text(
                    stringResource(R.string.decks_hero_cards_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(heroCards.sortedWith(state.sort.comparator), key = { "hero-" + it.code }) { card ->
                // Fixed in number, so no steppers: the count reads as a fact.
                DeckCardRow(card = card, quantity = state.slots[card.code], onClick = { onCardClick(card.code) })
            }
        }

        byType.forEach { (typeName, cards) ->
            item(key = "type-$typeName") { GroupHeading(typeName, cards.sumOf { state.slots[it.code] ?: 0 }) }
            items(cards, key = { it.code }) { card ->
                DeckCardRow(
                    card = card,
                    quantity = null,
                    onClick = { onCardClick(card.code) },
                    subtitle = card.typeName,
                    trailing = {
                        Steppers(
                            quantity = state.slots[card.code] ?: 0,
                            editable = state.isEditable,
                            onAdd = { viewModel.addCard(card.code) },
                            onRemove = { viewModel.removeCard(card.code) },
                        )
                    },
                )
            }
        }

        item(key = "stats") {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            GroupHeading(stringResource(R.string.decks_stats_made_of), null)
            DeckStatisticsSection(DeckStatisticsCalculator.calculate(state.deckCards.map { it to (state.slots[it.code] ?: 0) }))
            state.deck?.let { deck ->
                OutlinedButton(
                    onClick = {
                        shareText(
                            context = context,
                            subject = deck.name,
                            text = DeckText.format(
                                deckName = deck.name,
                                heroName = deck.heroName,
                                aspects = DeckRepository.parseAspects(deck.aspects),
                                cardsByType = state.deckCards.groupBy { it.typeName }.mapValues { entry ->
                                    entry.value.map { DeckTextCard(state.slots[it.code] ?: 0, it.name) }
                                },
                                marvelCdbUrl = deck.url,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.decks_share))
                }
            }
        }
    }
}

// --- the pool ------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoolTab(state: DeckEditorUiState, viewModel: DeckEditorViewModel, onCardClick: (String) -> Unit) {
    val candidates = remember(state.candidates, state.sort) { state.candidates.sortedWith(state.sort.comparator) }
    val ownedCodes = state.ownedPackCodes

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "problems") { ProblemsPanel(state) }

        item(key = "filters") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.cards_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Which of the deck's factions to list. All of them until one is chosen.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.factionChoices.forEach { faction ->
                        FilterChip(
                            selected = faction in state.factionFilter,
                            onClick = { viewModel.toggleFaction(faction) },
                            leadingIcon = { AspectDot(faction) },
                            label = { Text(factionLabel(faction)) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.typeChoices.forEach { (code, name) ->
                        FilterChip(
                            selected = code in state.typeFilter,
                            onClick = { viewModel.toggleType(code) },
                            label = { Text(name) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.card_stat_cost), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (0..DeckBuilderRepository.COST_AND_ABOVE).forEach { cost ->
                        val label = if (cost == DeckBuilderRepository.COST_AND_ABOVE) "$cost+" else cost.toString()
                        FilterChip(
                            selected = state.costFilter == cost,
                            onClick = { viewModel.setCost(cost) },
                            label = { Text(label) },
                        )
                    }
                }
                TickRow(stringResource(R.string.decks_filter_owned), state.ownedOnly) { viewModel.setOwnedOnly(it) }
                TickRow(stringResource(R.string.decks_synergy_hide), state.synergyOnly) { viewModel.setSynergyOnly(it) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.decks_sort), style = MaterialTheme.typography.labelMedium)
                    DeckSort.entries.forEach { option ->
                        FilterChip(
                            selected = state.sort == option,
                            onClick = { viewModel.setSort(option) },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            DeckSort.TYPE -> R.string.decks_sort_type
                                            DeckSort.COST -> R.string.decks_sort_cost
                                            DeckSort.NAME -> R.string.decks_sort_name
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.decks_pool_count, candidates.size, state.poolTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(candidates, key = { it.code }) { card ->
            val owned = ownedCodes.isEmpty() || card.packCode in ownedCodes
            DeckCardRow(
                card = card,
                quantity = null,
                onClick = { onCardClick(card.code) },
                subtitle = listOfNotNull(
                    card.typeName,
                    stringResource(R.string.decks_not_owned).takeIf { !owned },
                ).joinToString(" · "),
                subtitleIsWarning = !owned,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        card.cost?.let { Counter(it.toString()) }
                        Steppers(
                            quantity = state.slots[card.code] ?: 0,
                            editable = state.isEditable,
                            onAdd = { viewModel.addCard(card.code) },
                            onRemove = { viewModel.removeCard(card.code) },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun TickRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One fewer, the count, one more. */
@Composable
private fun Steppers(quantity: Int, editable: Boolean, onAdd: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onRemove, enabled = editable && quantity > 0) {
            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.decks_remove_card))
        }
        Text(
            quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (quantity == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onAdd, enabled = editable) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.decks_add_card))
        }
    }
}

@Composable
private fun factionLabel(faction: String): String = when (faction) {
    "hero" -> stringResource(R.string.decks_hero)
    "basic" -> stringResource(R.string.aspect_basic)
    else -> aspectLabel(faction)
}

@Composable
internal fun problemMessage(problem: DeckProblem): String = when (problem) {
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
        problem.counts.entries
            .map { "${aspectLabel(it.key)} ${it.value}" }
            .joinToString(", "),
    )
}
