package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.background
import com.hasyame.marvelchampions.data.repository.DeckFolderRepository
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import com.hasyame.marvelchampions.data.db.entity.DeckFolderEntity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckRepository

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
    var addDialogOpen by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    // The deck the bin was pressed on, held until the question is answered.
    var confirmDelete by remember { mutableStateOf<SavedDeckEntity?>(null) }
    // The folder tab in front: null for every deck. Page state, not remembered.
    var selectedFolder by remember { mutableStateOf<String?>(null) }

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
            addDialogOpen = false
            onDeckImported(deckId)
            viewModel.consumeImportedDeck()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.destination_decks)) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.decks_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            Box(Modifier.fillMaxSize()) {
                if (state.isImporting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (state.decks.isEmpty() && state.folders.isEmpty() && !state.isImporting) {
                    Box(
                        Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.decks_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // One shelf, the tab in front deciding what is on it:
                    // every deck, or the ones filed in that folder.
                    val current = state.folders.firstOrNull { it.id == selectedFolder }
                    val shown = if (current == null) state.decks else state.decks.filter { it.deck.id in current.deckIds }
                    Column(Modifier.fillMaxSize()) {
                        FolderTabs(
                            folders = state.folders,
                            selected = selectedFolder,
                            onSelect = { selectedFolder = it },
                            onNewFolder = { newFolderOpen = true },
                        )
                        current?.let { folder ->
                            FolderActions(
                                folder = folder,
                                count = shown.size,
                                onRename = { viewModel.renameFolder(folder.id, it) },
                                onDelete = {
                                    viewModel.deleteFolder(folder.id)
                                    selectedFolder = null
                                },
                            )
                        }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (shown.isEmpty()) {
                                item(key = "empty") {
                                    Text(
                                        stringResource(R.string.decks_folder_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(shown, key = { it.deck.id }) { tile ->
                                DeckTileCard(
                                    tile = tile,
                                    folders = state.folders,
                                    folder = DeckFolderRepository.folderOf(state.folders, tile.deck.id),
                                    onMove = { viewModel.moveDeck(tile.deck.id, it) },
                                    onOpen = { onDeckClick(tile.deck.id) },
                                    onEdit = { onEditDeck(tile.deck.id) },
                                    onDelete = { confirmDelete = tile.deck },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (newFolderOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderOpen = false },
            title = { Text(stringResource(R.string.decks_folder_new)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.decks_folder_name)) })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.createFolder(name)
                    newFolderOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { newFolderOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (addDialogOpen) {
        AddDeckDialog(
            onDismiss = { addDialogOpen = false },
            onImport = viewModel::import,
            onBuild = {
                addDialogOpen = false
                onBuildDeck()
            },
        )
    }

    // A deck is somebody's evening of building, and the bin sits on the row
    // beside a deck they meant to open. Asking costs one tap; not asking cost
    // a player their deck.
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

/**
 * A deck as a tile: the hero's art with the name over it, then what the
 * deck is in chips, and the two things done to a deck apart from opening
 * it, at the foot where they cannot be hit by mistake.
 */
/**
 * The folders as a strip of tabs across the top, "All" first, the way a
 * file browser lists its folders: one tap filters the shelf, and the last
 * tab makes a folder.
 */
@Composable
private fun FolderTabs(
    folders: List<DeckFolderEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onNewFolder: () -> Unit,
) {
    val index = folders.indexOfFirst { it.id == selected }.let { if (it < 0) 0 else it + 1 }
    ScrollableTabRow(selectedTabIndex = index, edgePadding = 8.dp) {
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
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    renaming?.let { typed ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.decks_folder_rename)) },
            text = {
                OutlinedTextField(value = typed, onValueChange = { renaming = it }, singleLine = true, label = { Text(stringResource(R.string.decks_folder_name)) })
            },
            confirmButton = {
                TextButton(enabled = typed.isNotBlank(), onClick = {
                    onRename(typed)
                    renaming = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
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

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(R.plurals.decks_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { renaming = folder.name }) { Text(stringResource(R.string.decks_folder_rename)) }
        TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.decks_folder_delete)) }
    }
}

/** The folder a deck is in, as a button that opens the list of the others. */
@Composable
private fun FolderPicker(folders: List<DeckFolderEntity>, current: DeckFolderEntity?, onMove: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(current?.name ?: stringResource(R.string.decks_folder_none))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.decks_folder_none)) }, onClick = {
                open = false
                onMove(null)
            })
            folders.forEach { folder ->
                DropdownMenuItem(text = { Text(folder.name) }, onClick = {
                    open = false
                    onMove(folder.id)
                })
            }
        }
    }
}

@Composable
private fun DeckTileCard(
    tile: DeckTile,
    folders: List<DeckFolderEntity>,
    folder: DeckFolderEntity?,
    onMove: (String?) -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen),
    ) {
        HeroBanner(
            imageSrc = tile.heroImageSrc,
            title = tile.deck.name,
            height = 150.dp,
        ) {
            Text(
                tile.deck.heroName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DeckChips(
                heroName = tile.deck.heroName,
                aspects = DeckRepository.parseAspects(tile.deck.aspects),
                cardCount = tile.cardCount,
                legal = tile.legal,
            )
            if (folders.isNotEmpty()) {
                FolderPicker(folders = folders, current = folder, onMove = onMove)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.decks_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun AddDeckDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onBuild: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.decks_add)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.decks_paste_url)) },
                    supportingText = { Text(stringResource(R.string.decks_paste_url_hint)) },
                )
                TextButton(onClick = onBuild) {
                    Text(stringResource(R.string.decks_build_from_scratch))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.decks_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

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
