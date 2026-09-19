package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.aspectColor
import com.hasyame.marvelchampions.data.db.entity.DeckFolderEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.data.repository.DeckFolderRepository
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckRepository

/** How the shelf is ordered. */
enum class ShelfSort { MODIFIED, CREATED, NAME, ASPECT }

/** What the search box looks through. */
enum class SearchScope { DECKS, HEROES, CARDS }

/** How far a tile zooms into its card, to land on the art rather than the frame. */
private const val ART_ZOOM = 1.4f

/**
 * The shelf of decks: a search box, the folders as tabs, and the decks as a
 * grid of tiles, the hero's art with the name over it and the aspects in
 * the corner. Made in the image of the deck apps people already keep on
 * their phones, so it needs no learning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(
    onDeckClick: (String) -> Unit,
    onEditDeck: (String) -> Unit,
    onDeckImported: (String) -> Unit,
    onBuildDeck: () -> Unit,
    /** A link shared into the app, imported once on arrival. */
    sharedLink: String? = null,
    onSharedLinkHandled: () -> Unit = {},
    viewModel: DecksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var importOpen by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var dialOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    // The deck the bin was pressed on, held until the question is answered.
    var confirmDelete by remember { mutableStateOf<SavedDeckEntity?>(null) }
    // The deck being filed, held while the folder is chosen.
    var filing by remember { mutableStateOf<SavedDeckEntity?>(null) }
    // The folder tab in front: null for every deck. Page state, not remembered.
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(SearchScope.DECKS) }
    var filtersOpen by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(ShelfSort.MODIFIED) }

    // A search by card takes the database, so it runs in the view model,
    // a beat after typing stops.
    LaunchedEffect(search, scope) {
        if (scope == SearchScope.CARDS) {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            viewModel.searchByCard(search)
        } else {
            viewModel.searchByCard("")
        }
    }
    var ascending by remember { mutableStateOf(false) }

    LaunchedEffect(sharedLink) {
        if (!sharedLink.isNullOrBlank()) {
            viewModel.import(sharedLink)
            onSharedLinkHandled()
        }
    }

    // A successful import opens the deck, which is what a share from the
    // browser should feel like.
    LaunchedEffect(state.importedDeckId) {
        state.importedDeckId?.let { deckId ->
            importOpen = false
            onDeckImported(deckId)
            viewModel.consumeImportedDeck()
        }
    }

    Scaffold(
        topBar = {
            // Under the status bar, which a TopAppBar would have handled itself.
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.statusBarsPadding()) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.decks_tab_decks)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.decks_tab_explorer)) },
                    icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                SpeedDial(
                    open = dialOpen,
                    onToggle = { dialOpen = !dialOpen },
                    sortOpen = sortOpen,
                    onToggleSort = { sortOpen = !sortOpen },
                    sort = sort,
                    ascending = ascending,
                    onSort = { sort = it },
                    onDirection = { ascending = it },
                    onNewDeck = {
                        dialOpen = false
                        onBuildDeck()
                    },
                    onImport = {
                        dialOpen = false
                        importOpen = true
                    },
                    onNewFolder = {
                        dialOpen = false
                        newFolderOpen = true
                    },
                )
            }
        },
    ) { padding ->
        when (tab) {
            0 -> Shelf(
                state = state,
                search = search,
                onSearch = { search = it },
                scope = scope,
                onScope = { scope = it },
                filtersOpen = filtersOpen,
                onToggleFilters = { filtersOpen = !filtersOpen },
                selectedFolder = selectedFolder,
                onSelectFolder = { selectedFolder = it },
                sort = sort,
                ascending = ascending,
                onNewFolder = { newFolderOpen = true },
                onRenameFolder = viewModel::renameFolder,
                onDeleteFolder = {
                    viewModel.deleteFolder(it)
                    selectedFolder = null
                },
                onOpen = onDeckClick,
                onEdit = onEditDeck,
                onFile = { filing = it },
                onDelete = { confirmDelete = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> Explorer(Modifier.fillMaxSize().padding(padding))
        }
    }

    if (importOpen) {
        ImportDeckDialog(onDismiss = { importOpen = false }, onImport = viewModel::import)
    }

    if (newFolderOpen) {
        NameDialog(
            title = stringResource(R.string.decks_folder_new),
            initial = "",
            onDismiss = { newFolderOpen = false },
            onConfirm = {
                viewModel.createFolder(it)
                newFolderOpen = false
            },
        )
    }

    filing?.let { deck ->
        FolderChoiceDialog(
            folders = state.folders,
            current = DeckFolderRepository.folderOf(state.folders, deck.id)?.id,
            onDismiss = { filing = null },
            onChoose = {
                viewModel.moveDeck(deck.id, it)
                filing = null
            },
        )
    }

    // A deck is somebody's evening of building. Asking costs one tap; not
    // asking cost a player their deck.
    confirmDelete?.let { deck ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.decks_delete_title)) },
            text = { Text(stringResource(R.string.decks_delete_message, deck.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(deck.id)
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

    state.importError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.decks_import_failed)) },
            text = { Text(importErrorMessage(error)) },
        )
    }
}

// --- the shelf -----------------------------------------------------------------------------

@Composable
private fun Shelf(
    state: DecksUiState,
    search: String,
    onSearch: (String) -> Unit,
    scope: SearchScope,
    onScope: (SearchScope) -> Unit,
    filtersOpen: Boolean,
    onToggleFilters: () -> Unit,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    sort: ShelfSort,
    ascending: Boolean,
    onNewFolder: () -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onFile: (SavedDeckEntity) -> Unit,
    onDelete: (SavedDeckEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    // What is on the shelf: the tab's decks, the ones the word matches, in
    // the order asked for.
    val current = state.folders.firstOrNull { it.id == selectedFolder }
    val shown = remember(state.decks, current, search, scope, state.cardSearchHits, sort, ascending) {
        val needle = search.trim()
        val filed = if (current == null) state.decks else state.decks.filter { it.deck.id in current.deckIds }
        val found = filed.filter {
            needle.isBlank() || when (scope) {
                SearchScope.DECKS -> it.deck.name.contains(needle, ignoreCase = true)
                SearchScope.HEROES -> it.deck.heroName.contains(needle, ignoreCase = true)
                SearchScope.CARDS -> state.cardSearchHits?.contains(it.deck.id) == true
            }
        }
        val ordered = when (sort) {
            ShelfSort.MODIFIED -> found.sortedBy { it.deck.updatedAt }
            ShelfSort.CREATED -> found.sortedBy { it.deck.lastSyncedAt }
            ShelfSort.NAME -> found.sortedBy { it.deck.name.lowercase() }
            ShelfSort.ASPECT -> found.sortedWith(compareBy({ it.deck.aspects }, { it.deck.name.lowercase() }))
        }
        if (ascending) ordered else ordered.reversed()
    }

    Column(modifier) {
        // The search box, the scope it looks through as a chip inside it,
        // and an arrow that opens the panel where the scope is changed.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.decks_search_decks)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    Text(
                        scopeLabel(scope).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onToggleFilters,
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.decks_search_scope), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    SearchScope.entries.forEach { option ->
                        FilterChip(
                            selected = scope == option,
                            onClick = { onScope(option) },
                            label = { Text(scopeLabel(option)) },
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
        FolderTabs(
            folders = state.folders,
            selected = selectedFolder,
            onSelect = onSelectFolder,
            onNewFolder = onNewFolder,
        )
        current?.let { folder ->
            FolderActions(
                folder = folder,
                count = shown.size,
                onRename = { onRenameFolder(folder.id, it) },
                onDelete = { onDeleteFolder(folder.id) },
            )
        }
        if (state.isImporting) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (shown.isEmpty() && !state.isImporting) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        when {
                            search.isNotBlank() -> R.string.cards_no_results
                            current != null -> R.string.decks_folder_empty
                            else -> R.string.decks_empty
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.deck.id }) { tile ->
                DeckTileCard(
                    tile = tile,
                    onOpen = { onOpen(tile.deck.id) },
                    onEdit = { onEdit(tile.deck.id) },
                    onFile = { onFile(tile.deck) },
                    onDelete = { onDelete(tile.deck) },
                )
            }
        }
    }
}

/**
 * A deck as a tile: the hero's art, the name and the hero over a darker
 * top band, the aspects as dots in the corner, and a stamp when the deck is
 * not legal. A tap opens it; a long press offers the rest.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckTileCard(
    tile: DeckTile,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFile: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        MarvelCdbUrls.cardImage(tile.heroImageSrc)?.let { url ->
            // Zoomed onto the picture: a card fitted to the tile's width
            // shows its title band and its text box, neither of which is the
            // hero. Cropped from the top and scaled up about the centre, the
            // window lands on the face, with no gap at either edge.
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = ART_ZOOM
                        scaleY = ART_ZOOM
                    },
            )
        }
        // The band the name sits on: dark at the top, clear below.
        Box(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.75f), 1f to Color.Transparent)),
        )
        Column(Modifier.padding(10.dp)) {
            Text(
                tile.deck.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tile.deck.heroName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DeckRepository.parseAspects(tile.deck.aspects).forEach { aspect ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(aspectColor(aspect) ?: Color.LightGray))
                }
            }
        }
        if (tile.legal == false) {
            LegalBadge(legal = false, Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp)))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.decks_edit)) }, onClick = {
                menuOpen = false
                onEdit()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.decks_move_to_folder)) }, onClick = {
                menuOpen = false
                onFile()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = {
                menuOpen = false
                onDelete()
            })
        }
    }
}

/**
 * The folders as a strip of tabs, "All" first, the way a file browser lists
 * its folders: one tap filters the shelf, and the last tab makes a folder.
 */
@Composable
private fun FolderTabs(
    folders: List<DeckFolderEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onNewFolder: () -> Unit,
) {
    val index = folders.indexOfFirst { it.id == selected }.let { if (it < 0) 0 else it + 1 }
    SecondaryScrollableTabRow(selectedTabIndex = index, edgePadding = 8.dp) {
        Tab(selected = index == 0, onClick = { onSelect(null) }, text = { Text(stringResource(R.string.decks_folder_all)) })
        folders.forEach { folder ->
            Tab(selected = selected == folder.id, onClick = { onSelect(folder.id) }, text = { Text(folder.name) })
        }
        Tab(
            selected = false,
            onClick = onNewFolder,
            icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.decks_folder_new)) },
        )
    }
}

/** Under the tabs, for the folder in front: how many decks, and rename or delete. */
@Composable
private fun FolderActions(folder: DeckFolderEntity, count: Int, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (renaming) {
        NameDialog(
            title = stringResource(R.string.decks_folder_rename),
            initial = folder.name,
            onDismiss = { renaming = false },
            onConfirm = {
                onRename(it)
                renaming = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.decks_folder_delete)) },
            text = { Text(stringResource(R.string.decks_folder_delete_message, folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            pluralStringResource(R.plurals.decks_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.decks_folder_rename)) }
        TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.decks_folder_delete)) }
    }
}

@Composable
private fun scopeLabel(scope: SearchScope): String = stringResource(
    when (scope) {
        SearchScope.DECKS -> R.string.decks_scope_decks
        SearchScope.HEROES -> R.string.decks_scope_heroes
        SearchScope.CARDS -> R.string.decks_scope_cards
    },
)

// --- the corner --------------------------------------------------------------------------------

/**
 * The buttons in the corner: the sort popover on the small one, and on the
 * large one a dial that unfolds into the three ways of adding to the shelf.
 */
@Composable
private fun SpeedDial(
    open: Boolean,
    onToggle: () -> Unit,
    sortOpen: Boolean,
    onToggleSort: () -> Unit,
    sort: ShelfSort,
    ascending: Boolean,
    onSort: (ShelfSort) -> Unit,
    onDirection: (Boolean) -> Unit,
    onNewDeck: () -> Unit,
    onImport: () -> Unit,
    onNewFolder: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (open) {
            DialEntry(stringResource(R.string.decks_folder_new), Icons.Filled.AddCircle, onNewFolder)
            DialEntry(stringResource(R.string.decks_import_deck), Icons.Filled.Share, onImport)
            DialEntry(stringResource(R.string.decks_new_deck), Icons.Filled.Add, onNewDeck)
        }
        if (sortOpen) {
            SortPopover(sort, ascending, onSort, onDirection)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallFloatingActionButton(
                onClick = onToggleSort,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.decks_sort))
            }
            FloatingActionButton(onClick = onToggle) {
                Icon(
                    if (open) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = stringResource(if (open) R.string.action_cancel else R.string.decks_add),
                )
            }
        }
    }
}

@Composable
private fun DialEntry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = onClick,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(end = 4.dp),
        ) { Icon(icon, contentDescription = null) }
    }
}

/** Ascending or descending, and what by. */
@Composable
private fun SortPopover(sort: ShelfSort, ascending: Boolean, onSort: (ShelfSort) -> Unit, onDirection: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.clickable { onDirection(!ascending) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (ascending) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                Text(
                    stringResource(if (ascending) R.string.decks_sort_asc else R.string.decks_sort_desc),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortChip(ShelfSort.NAME, sort, onSort, stringResource(R.string.decks_sort_name))
                    SortChip(ShelfSort.MODIFIED, sort, onSort, stringResource(R.string.decks_sort_modified))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortChip(ShelfSort.CREATED, sort, onSort, stringResource(R.string.decks_sort_created))
                    SortChip(ShelfSort.ASPECT, sort, onSort, stringResource(R.string.decks_sort_aspect))
                }
            }
        }
    }
}

@Composable
private fun SortChip(option: ShelfSort, current: ShelfSort, onSort: (ShelfSort) -> Unit, label: String) {
    FilterChip(selected = current == option, onClick = { onSort(option) }, label = { Text(label) })
}

// --- the other tab ---------------------------------------------------------------------------

/** Decks to browse: precons, and other people's. Not there yet; the tab says so. */
@Composable
private fun Explorer(modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.decks_explorer_soon),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- dialogs ---------------------------------------------------------------------------------

@Composable
private fun ImportDeckDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.decks_import_deck)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.decks_paste_url)) },
                supportingText = { Text(stringResource(R.string.decks_paste_url_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.decks_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.decks_folder_name)) })
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Which folder a deck goes in: one of them, or none. */
@Composable
fun FolderChoiceDialog(
    folders: List<DeckFolderEntity>,
    current: String?,
    onDismiss: () -> Unit,
    onChoose: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.decks_move_to_folder)) },
        text = {
            Column {
                FolderChoiceRow(stringResource(R.string.decks_folder_none), selected = current == null) { onChoose(null) }
                folders.forEach { folder ->
                    FolderChoiceRow(folder.name, selected = current == folder.id) { onChoose(folder.id) }
                }
                if (folders.isEmpty()) {
                    Text(
                        stringResource(R.string.decks_no_folder_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FolderChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else null, modifier = Modifier.weight(1f))
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

private const val SEARCH_DEBOUNCE_MS = 250L

@Composable
internal fun importErrorMessage(error: DeckImportError): String = when (error) {
    DeckImportError.NotADeckLink -> stringResource(R.string.decks_error_not_a_link)
    DeckImportError.NotFound -> stringResource(R.string.decks_error_not_found)
    DeckImportError.NotShared -> stringResource(R.string.decks_error_not_shared)
    DeckImportError.Network -> stringResource(R.string.decks_error_network)
    DeckImportError.LocalDeck -> stringResource(R.string.decks_error_local)
    is DeckImportError.Unexpected -> stringResource(
        R.string.decks_error_unexpected,
        error.message ?: "",
    )
}
