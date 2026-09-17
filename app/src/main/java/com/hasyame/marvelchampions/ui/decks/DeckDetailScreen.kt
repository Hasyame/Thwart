package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.clickable
import com.hasyame.marvelchampions.data.repository.DeckContents
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.DeckFolderRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.domain.deckbuilder.DeckText
import com.hasyame.marvelchampions.domain.deckbuilder.DeckTextCard
import com.hasyame.marvelchampions.ui.util.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deckId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    onEdit: (String) -> Unit,
    viewModel: DeckDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shareContext = LocalContext.current
    var noShareApp by remember { mutableStateOf(false) }
    var confirmRefresh by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var filing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // A word to find a card in this deck, an order, and names or pictures.
    // Page state, not remembered: a question about this deck, not a preference.
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(DeckSort.TYPE) }
    var grid by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            onBack()
        }
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.decks_delete_title)) },
            text = { Text(stringResource(R.string.decks_delete_message, state.contents?.deck?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    // Null while the dialogue is closed; the name being typed while it is open.
    var renaming by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deckId) { viewModel.load(deckId) }

    if (noShareApp) {
        AlertDialog(
            onDismissRequest = { noShareApp = false },
            text = { Text(stringResource(R.string.decks_share_no_app)) },
            confirmButton = {
                TextButton(onClick = { noShareApp = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    if (confirmRefresh) {
        AlertDialog(
            onDismissRequest = { confirmRefresh = false },
            title = { Text(stringResource(R.string.decks_refresh)) },
            text = { Text(stringResource(R.string.decks_refresh_discards_edits)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRefresh = false
                        viewModel.refresh()
                    },
                ) { Text(stringResource(R.string.decks_refresh)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRefresh = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    renaming?.let { typed ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.decks_rename)) },
            text = {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { renaming = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.decks_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    // A deck with no name at all is worse than the one it has.
                    enabled = typed.isNotBlank(),
                    onClick = {
                        viewModel.rename(typed)
                        renaming = null
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = { Text(state.contents?.deck?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    val deck = state.contents?.deck

                    // Sharing a decklist is what people actually do with one,
                    // so it sits in the bar rather than behind a menu.
                    IconButton(
                        enabled = state.contents != null,
                        onClick = {
                            state.contents?.let { contents ->
                                val shared = shareText(
                                    context = shareContext,
                                    subject = contents.deck.name,
                                    text = DeckText.format(
                                        deckName = contents.deck.name,
                                        heroName = contents.deck.heroName,
                                        aspects = DeckRepository.parseAspects(
                                            contents.deck.aspects,
                                        ),
                                        cardsByType = contents.cardsByType.mapValues { entry ->
                                            entry.value.map {
                                                DeckTextCard(it.quantity, it.card.name)
                                            }
                                        },
                                        marvelCdbUrl = contents.deck.url,
                                    ),
                                )
                                if (!shared) {
                                    noShareApp = true
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.decks_share),
                        )
                    }
                    // Every deck is editable now, imported ones included.
                    IconButton(onClick = { onEdit(deckId) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.decks_edit),
                        )
                    }
                    if (deck != null && !DeckRepository.isLocal(deck)) {
                        IconButton(
                            onClick = {
                                // Only ask when there is something to lose.
                                if (state.hasLocalEdits) {
                                    confirmRefresh = true
                                } else {
                                    viewModel.refresh()
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.decks_refresh),
                            )
                        }
                    }
                    // Renaming lives in a menu rather than as a fifth icon: it
                    // is done once and the bar is already full of things done
                    // often.
                    IconButton(
                        enabled = deck != null,
                        onClick = { menuOpen = true },
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.decks_rename)) },
                            onClick = {
                                menuOpen = false
                                renaming = deck?.name.orEmpty()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.decks_move_to_folder)) },
                            onClick = {
                                menuOpen = false
                                filing = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val contents = state.contents
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            contents == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.decks_not_found)) }

            else -> DeckBody(
                state = state,
                contents = contents,
                search = search,
                onSearch = { search = it },
                sort = sort,
                onSort = { sort = it },
                grid = grid,
                onGrid = { grid = it },
                onCardClick = onCardClick,
                onRevert = viewModel::revertToImported,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

/**
 * The page under the bar: the hero's art, the hero, the verdicts, the deck
 * by type as names or as pictures, the nemesis set, and what the deck is
 * made of. One list, so it scrolls as one page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckBody(
    state: DeckDetailUiState,
    contents: DeckContents,
    search: String,
    onSearch: (String) -> Unit,
    sort: DeckSort,
    onSort: (DeckSort) -> Unit,
    grid: Boolean,
    onGrid: (Boolean) -> Unit,
    onCardClick: (String) -> Unit,
    onRevert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aspects = DeckRepository.parseAspects(contents.deck.aspects)
    // What the toolbar leaves of the deck: the word narrows it, the order
    // sorts inside each type, and the type groups themselves stay.
    val shown = remember(contents, search, sort) {
        val needle = search.trim()
        contents.cardsByType.mapValues { (_, cards) ->
            cards.filter { needle.isBlank() || it.card.name.contains(needle, ignoreCase = true) }
                .sortedWith(compareBy(sort.comparator) { it.card })
        }.filterValues { it.isNotEmpty() }
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 32.dp)) {
        if (state.isRefreshing) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.error?.let { error ->
            item {
                Text(
                    text = importErrorMessage(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item(key = "banner") {
            HeroBanner(imageSrc = contents.hero?.imageSrc, title = contents.deck.name, height = 200.dp) {
                DeckChips(heroName = contents.deck.heroName, aspects = aspects, cardCount = contents.totalCards)
            }
        }

        item(key = "hero") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                contents.hero?.let { HeroCard(it, onClick = { onCardClick(it.code) }) }
                if (state.validation.isLegal) {
                    Verdict(stringResource(R.string.decks_legal), ok = true)
                } else {
                    Verdict(
                        pluralStringResource(R.plurals.decks_not_legal, state.validation.problems.size, state.validation.problems.size),
                        ok = false,
                    )
                }
                if (contents.missingCards.isEmpty() && contents.unknownCardCodes.isEmpty()) {
                    Verdict(stringResource(R.string.decks_own_everything), ok = true)
                } else if (contents.missingCards.isNotEmpty()) {
                    Verdict(
                        pluralStringResource(R.plurals.decks_missing_count, contents.missingCards.size, contents.missingCards.size),
                        ok = false,
                    )
                }
                SynergyWarningText(state.synergyWarnings)
                if (state.hasLocalEdits) {
                    Text(stringResource(R.string.decks_locally_edited), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRevert) { Text(stringResource(R.string.decks_revert)) }
                }
                if (contents.unknownCardCodes.isNotEmpty()) {
                    // Happens when the deck uses a pack MarvelCDB has added
                    // since the last card sync.
                    Text(
                        text = pluralStringResource(R.plurals.decks_unknown_cards, contents.unknownCardCodes.size, contents.unknownCardCodes.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item(key = "toolbar") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearch,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.decks_search_in)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = sort == DeckSort.NAME, onClick = { onSort(DeckSort.NAME) }, label = { Text(stringResource(R.string.decks_sort_name)) })
                    FilterChip(selected = sort == DeckSort.COST, onClick = { onSort(DeckSort.COST) }, label = { Text(stringResource(R.string.decks_sort_cost)) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !grid, onClick = { onGrid(false) }, label = { Text(stringResource(R.string.decks_view_list)) })
                    FilterChip(selected = grid, onClick = { onGrid(true) }, label = { Text(stringResource(R.string.decks_view_grid)) })
                }
            }
        }

        if (state.campaignCards.isNotEmpty()) {
            item(key = "campaign-cards") {
                GroupHeading(stringResource(R.string.decks_campaign_cards), state.campaignCards.size)
                Text(
                    // They live on the campaign run, not in the deck, which is
                    // also why they are outside the deck size limits.
                    text = stringResource(R.string.decks_campaign_cards_note),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.campaignCards, key = { it.cardCode + it.campaignName }) { granted ->
                ListItem(
                    modifier = Modifier.clickable { onCardClick(granted.cardCode) },
                    headlineContent = { Text(granted.name) },
                    supportingContent = { Text(granted.campaignName) },
                )
            }
        }

        shown.forEach { (typeName, cards) ->
            item(key = "type-$typeName") { GroupHeading(typeName, cards.sumOf { it.quantity }) }
            if (grid) {
                item(key = "grid-$typeName") {
                    FlowRow(
                        Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3,
                    ) {
                        cards.forEach { deckCard ->
                            CardTile(
                                card = deckCard.card,
                                quantity = deckCard.quantity,
                                onClick = { onCardClick(deckCard.card.code) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                items(cards, key = { it.card.code }) { deckCard ->
                    DeckCardRow(
                        card = deckCard.card,
                        quantity = deckCard.quantity,
                        onClick = { onCardClick(deckCard.card.code) },
                        subtitle = if (deckCard.missingFromCollection) {
                            stringResource(R.string.decks_card_missing, deckCard.card.packCode.uppercase())
                        } else {
                            null
                        },
                        subtitleIsWarning = deckCard.missingFromCollection,
                    )
                }
            }
        }

        if (state.nemesis.isNotEmpty()) {
            item(key = "nemesis") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                GroupHeading(stringResource(R.string.decks_nemesis), state.nemesis.size)
                Text(
                    stringResource(R.string.decks_nemesis_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                FlowRow(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3,
                ) {
                    state.nemesis.forEach { card ->
                        CardTile(card = card, quantity = null, onClick = { onCardClick(card.code) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item(key = "stats") {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DeckStatisticsSection(state.statistics)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(
                stringResource(R.string.decks_card_language_note, state.cardLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
