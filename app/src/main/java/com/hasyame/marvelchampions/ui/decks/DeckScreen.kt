package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.aspectColor
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckCard
import com.hasyame.marvelchampions.data.repository.DeckFolderRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem
import com.hasyame.marvelchampions.domain.deckbuilder.DeckText
import com.hasyame.marvelchampions.domain.deckbuilder.DeckTextCard
import com.hasyame.marvelchampions.domain.model.PackType
import com.hasyame.marvelchampions.ui.navigation.NavigationIcons
import com.hasyame.marvelchampions.ui.util.aspectLabel
import com.hasyame.marvelchampions.ui.util.shareText

/**
 * One deck on three tabs: the cards, with the pool to add from; what the
 * deck is made of; and what there is to know about it, the problems
 * explained, the packs it draws on, and the player's own notes. Every
 * stepper writes at once, so looking and building are the same screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    deckId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: DeckViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirmRefresh by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var filing by remember { mutableStateOf(false) }
    var noShareApp by remember { mutableStateOf(false) }

    LaunchedEffect(deckId) { viewModel.load(deckId) }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            onBack()
        }
    }

    // --- dialogs ---------------------------------------------------------------
    renaming?.let { typed ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.decks_rename)) },
            text = { OutlinedTextField(value = typed, onValueChange = { renaming = it }, singleLine = true, label = { Text(stringResource(R.string.decks_name)) }) },
            confirmButton = {
                TextButton(enabled = typed.isNotBlank(), onClick = {
                    viewModel.rename(typed)
                    renaming = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmRefresh) {
        AlertDialog(
            onDismissRequest = { confirmRefresh = false },
            title = { Text(stringResource(R.string.decks_refresh)) },
            text = { Text(stringResource(R.string.decks_refresh_discards_edits)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRefresh = false
                    viewModel.refresh()
                }) { Text(stringResource(R.string.decks_refresh)) }
            },
            dismissButton = { TextButton(onClick = { confirmRefresh = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.decks_delete_title)) },
            text = { Text(stringResource(R.string.decks_delete_message, state.deck?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (filing) {
        FolderChoiceDialog(
            folders = state.folders,
            current = DeckFolderRepository.folderOf(state.folders, deckId)?.id,
            onDismiss = { filing = false },
            onChoose = {
                viewModel.moveToFolder(it)
                filing = false
            },
        )
    }
    if (noShareApp) {
        AlertDialog(
            onDismissRequest = { noShareApp = false },
            text = { Text(stringResource(R.string.decks_share_no_app)) },
            confirmButton = { TextButton(onClick = { noShareApp = false }) { Text(stringResource(R.string.action_done)) } },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(android.R.string.ok)) } },
            title = { Text(stringResource(R.string.decks_import_failed)) },
            text = { Text(importErrorMessage(error)) },
        )
    }

    val problems = state.validation.problems.size

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    colors = comicTopBarColors(),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.deck?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            state.deck?.let { deck ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        deck.heroName + " · " + pluralStringResource(R.plurals.decks_card_count, state.validation.totalCards, state.validation.totalCards),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DeckRepository.parseAspects(deck.aspects).forEach { AspectDot(it, Modifier.size(12.dp)) }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.decks_share)) }, onClick = {
                                menuOpen = false
                                state.deck?.let { deck ->
                                    val shared = shareText(
                                        context = context,
                                        subject = deck.name,
                                        text = DeckText.format(
                                            deckName = deck.name,
                                            heroName = deck.heroName,
                                            aspects = DeckRepository.parseAspects(deck.aspects),
                                            cardsByType = state.deckCards.groupBy { it.card.typeName }.mapValues { entry -> entry.value.map { DeckTextCard(it.quantity, it.card.name) } },
                                            marvelCdbUrl = deck.url,
                                        ),
                                    )
                                    if (!shared) {
                                        noShareApp = true
                                    }
                                }
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.decks_rename)) }, onClick = {
                                menuOpen = false
                                renaming = state.deck?.name.orEmpty()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.decks_move_to_folder)) }, onClick = {
                                menuOpen = false
                                filing = true
                            })
                            state.deck?.takeIf { !DeckRepository.isLocal(it) }?.let {
                                DropdownMenuItem(text = { Text(stringResource(R.string.decks_refresh)) }, onClick = {
                                    menuOpen = false
                                    if (state.hasLocalEdits) confirmRefresh = true else viewModel.refresh()
                                })
                            }
                            if (state.hasLocalEdits) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.decks_revert)) }, onClick = {
                                    menuOpen = false
                                    viewModel.revertToImported()
                                })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = {
                                menuOpen = false
                                confirmDelete = true
                            })
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.decks_tab_cards)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(NavigationIcons.Chart, contentDescription = stringResource(R.string.decks_stats_title)) })
                    Tab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = {
                            BadgedBox(badge = { if (problems > 0) WarningBadge(problems) }) {
                                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.decks_tab_info))
                            }
                        },
                    )
                }
                if (state.isRefreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.deck == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.decks_not_found)) }
            tab == 0 -> CardsTab(state, viewModel, onCardClick, Modifier.fillMaxSize().padding(padding))
            tab == 1 -> StatsTab(state, Modifier.fillMaxSize().padding(padding))
            else -> InfoTab(state, viewModel, onCardClick, Modifier.fillMaxSize().padding(padding))
        }
    }
}

// --- tab 1: the cards ------------------------------------------------------------------------

@Composable
private fun CardsTab(state: DeckUiState, viewModel: DeckViewModel, onCardClick: (String) -> Unit, modifier: Modifier = Modifier) {
    var filtersOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(modifier) {
        // The search box: a word finds cards to add, and the arrow opens the
        // chips. Blank, and the tab shows the deck itself; clearing also puts
        // the keyboard away, since the deck is what the person wants to see.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.decks_add_cards_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.isSearching) {
                        IconButton(onClick = { viewModel.clearSearch(); focus.clearFocus() }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear)) }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = { filtersOpen = !filtersOpen },
                shape = RoundedCornerShape(12.dp),
                color = if (filtersOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    if (filtersOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cards_filters),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        if (filtersOpen) {
            PoolFilters(state, viewModel)
        }
        if (state.isSearching) {
            PoolList(state, viewModel, onCardClick)
        } else {
            DeckList(state, viewModel, onCardClick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoolFilters(state: DeckUiState, viewModel: DeckViewModel) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.factionChoices.forEach { faction ->
                    FilterChip(
                        selected = faction in state.factionFilter,
                        onClick = { viewModel.toggleFaction(faction) },
                        leadingIcon = { AspectDot(faction) },
                        label = { Text(factionLabel(faction)) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.typeChoices.forEach { (code, name) ->
                    FilterChip(selected = code in state.typeFilter, onClick = { viewModel.toggleType(code) }, label = { Text(name) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.card_stat_cost), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (0..DeckBuilderRepository.COST_AND_ABOVE).forEach { cost ->
                    FilterChip(
                        selected = state.costFilter == cost,
                        onClick = { viewModel.setCost(cost) },
                        label = { Text(if (cost == DeckBuilderRepository.COST_AND_ABOVE) "$cost+" else cost.toString()) },
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
        }
    }
}

/** The pool, narrowed by the word and the chips, each row stepping the copies in the deck. */
@Composable
private fun PoolList(state: DeckUiState, viewModel: DeckViewModel, onCardClick: (String) -> Unit) {
    val candidates = remember(state.candidates, state.sort) { state.candidates.sortedWith(state.sort.comparator) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "count") {
            Text(
                stringResource(R.string.decks_pool_count, candidates.size, state.poolTotal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(candidates, key = { it.code }) { card ->
            val owned = state.ownedPackCodes.isEmpty() || card.packCode in state.ownedPackCodes
            CardLine(
                card = card,
                quantity = state.slots[card.code] ?: 0,
                warning = if (owned) null else stringResource(R.string.decks_not_owned),
                editable = state.isEditable,
                onOpen = { onCardClick(card.code) },
                onAdd = { viewModel.addCard(card.code) },
                onRemove = { viewModel.removeCard(card.code) },
            )
        }
    }
}

/** The deck by type, the hero's own cards first, every row with its steppers. */
@Composable
private fun DeckList(state: DeckUiState, viewModel: DeckViewModel, onCardClick: (String) -> Unit) {
    val heroSet = state.rules?.heroSetCode
    val (heroCards, others) = remember(state.deckCards, heroSet) {
        state.deckCards.partition { heroSet != null && it.card.cardSetCode == heroSet }
    }
    val byType = remember(others, state.sort) {
        others.sortedWith(compareBy(state.sort.comparator) { it.card }).groupBy { it.card.typeName }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
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
                GroupBar(stringResource(R.string.decks_hero_cards), heroCards.sumOf { it.quantity }, stringResource(R.string.decks_hero_cards_note))
            }
            items(heroCards, key = { "hero-" + it.card.code }) { deckCard ->
                // Fixed in number: the count reads as a fact, with no steppers.
                CardLine(card = deckCard.card, quantity = deckCard.quantity, warning = null, editable = false, onOpen = { onCardClick(deckCard.card.code) }, onAdd = {}, onRemove = {})
            }
        }
        byType.forEach { (typeName, cards) ->
            item(key = "type-$typeName") { GroupBar(typeName, cards.sumOf { it.quantity }, null) }
            items(cards, key = { it.card.code }) { deckCard ->
                CardLine(
                    card = deckCard.card,
                    quantity = deckCard.quantity,
                    warning = if (deckCard.missingFromCollection) stringResource(R.string.decks_not_owned) else null,
                    editable = state.isEditable,
                    onOpen = { onCardClick(deckCard.card.code) },
                    onAdd = { viewModel.addCard(deckCard.card.code) },
                    onRemove = { viewModel.removeCard(deckCard.card.code) },
                )
            }
        }
    }
}

/** A type's heading as a bar, with the count at the end. */
@Composable
private fun GroupBar(title: String, count: Int, note: String?) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.decks_card_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * A card as a line: its picture, its name with the aspect's dot, the type
 * and cost under it, and the copies stepped up and down at the end.
 */
@Composable
private fun CardLine(
    card: CardEntity,
    quantity: Int,
    warning: String?,
    editable: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(64.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            MarvelCdbUrls.cardImage(card.imageSrc)?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -0.5f),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AspectDot(card.factionCode)
                Text(card.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (card.isUnique) {
                    Text("◆", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                card.cost?.let { Counter(it.toString()) }
                Text(card.typeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                warning?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (editable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove, enabled = quantity > 0) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.decks_remove_card)) }
                Text(quantity.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.decks_add_card)) }
            }
        } else {
            Text(quantity.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun TickRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun factionLabel(faction: String): String = when (faction) {
    "hero" -> stringResource(R.string.decks_hero)
    "basic" -> stringResource(R.string.aspect_basic)
    else -> aspectLabel(faction)
}

// --- tab 2: the figures ------------------------------------------------------------------------

@Composable
private fun StatsTab(state: DeckUiState, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel { DeckStatisticsSection(state.statistics) }
    }
}

// --- tab 3: the information -------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoTab(state: DeckUiState, viewModel: DeckViewModel, onCardClick: (String) -> Unit, modifier: Modifier = Modifier) {
    var editingNotes by remember { mutableStateOf<String?>(null) }
    editingNotes?.let { typed ->
        AlertDialog(
            onDismissRequest = { editingNotes = null },
            title = { Text(stringResource(R.string.decks_notes)) },
            text = {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { editingNotes = it },
                    minLines = 6,
                    placeholder = { Text(stringResource(R.string.decks_notes_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNotes(typed)
                    editingNotes = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editingNotes = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The verdict, and every reason behind it.
        Panel {
            val problems = state.validation.problems
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (problems.isEmpty()) Icons.Filled.Info else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (problems.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (problems.isEmpty()) stringResource(R.string.decks_legal) else stringResource(R.string.decks_problems_title, problems.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (problems.isNotEmpty()) {
                Text(
                    stringResource(R.string.decks_problems_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                problems.forEach { problem ->
                    Text("• " + problemMessage(problem), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            SynergyWarningText(state.synergyWarnings, Modifier.padding(top = 8.dp))
            if (state.synergyWarnings.isNotEmpty()) {
                Text(stringResource(R.string.decks_synergy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.unknownCardCodes.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.decks_unknown_cards, state.unknownCardCodes.size, state.unknownCardCodes.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // The boxes the deck draws on.
        Panel {
            Text(stringResource(R.string.decks_packs_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.missingCards.isEmpty()) {
                Verdict(stringResource(R.string.decks_own_everything), ok = true, Modifier.padding(vertical = 6.dp))
            } else {
                Verdict(pluralStringResource(R.plurals.decks_missing_count, state.missingCards.size, state.missingCards.size), ok = false, Modifier.padding(vertical = 6.dp))
            }
            state.packs.forEach { pack ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pack.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            packTypeLabel(pack.type) + " · " + pluralStringResource(R.plurals.decks_card_count, pack.cardCount, pack.cardCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (pack.owned) "✓" else stringResource(R.string.decks_not_owned),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (pack.owned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (state.campaignCards.isNotEmpty()) {
            Panel {
                Text(stringResource(R.string.decks_campaign_cards), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.decks_campaign_cards_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.campaignCards.forEach { granted ->
                    Row(Modifier.fillMaxWidth().clickable { onCardClick(granted.cardCode) }.padding(vertical = 6.dp)) {
                        Text(granted.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(granted.campaignName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // The player's own words.
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.decks_notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { editingNotes = state.deck?.descriptionMd.orEmpty() }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.decks_notes_edit))
                }
            }
            val notes = state.deck?.descriptionMd
            if (notes.isNullOrBlank()) {
                Text(stringResource(R.string.decks_notes_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.nemesis.isNotEmpty()) {
            Panel {
                Text(stringResource(R.string.decks_nemesis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.decks_nemesis_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                    state.nemesis.forEach { card ->
                        CardTile(card = card, quantity = null, onClick = { onCardClick(card.code) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Text(
            stringResource(R.string.decks_card_language_note, state.cardLanguage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun packTypeLabel(type: PackType): String = stringResource(
    when (type) {
        PackType.CORE -> R.string.pack_type_core
        PackType.HERO_PACK -> R.string.pack_type_hero
        PackType.SCENARIO_PACK -> R.string.pack_type_scenario
        PackType.CAMPAIGN_BOX -> R.string.pack_type_campaign_box
        PackType.MODULAR_SET -> R.string.pack_type_modular_set
        PackType.UNKNOWN -> R.string.pack_type_unknown
    },
)

/** A rounded panel, one subject each, the way the tabs stack their sections. */
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
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
