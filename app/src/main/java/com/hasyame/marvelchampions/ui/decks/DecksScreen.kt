package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
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
    // The deck the bin was pressed on, held until the question is answered.
    var confirmDelete by remember { mutableStateOf<SavedDeckEntity?>(null) }

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
            /*
                Whether building a deck offers only what the collection holds.

                Here rather than in Settings because it changes what the two
                screens behind this one show — which heroes a new deck can
                have, which cards the editor lists — and a switch you cannot
                see from where its effect is felt is a switch people conclude
                does not exist. The editor's own chip toggles the same setting.
            */
            ListItem(
                headlineContent = { Text(stringResource(R.string.decks_collection_only)) },
                supportingContent = {
                    Text(stringResource(R.string.decks_collection_only_summary))
                },
                trailingContent = {
                    Switch(
                        checked = state.collectionOnly,
                        onCheckedChange = viewModel::setCollectionOnly,
                    )
                },
                modifier = Modifier.toggleable(
                    value = state.collectionOnly,
                    role = Role.Switch,
                    onValueChange = viewModel::setCollectionOnly,
                ),
            )
            HorizontalDivider()

            Box(Modifier.fillMaxSize()) {
                if (state.isImporting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (state.decks.isEmpty() && !state.isImporting) {
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
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.decks, key = { it.deck.id }) { tile ->
                            DeckTileCard(
                                tile = tile,
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
@Composable
private fun DeckTileCard(
    tile: DeckTile,
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
