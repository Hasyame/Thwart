package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.ui.util.aspectLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(
    onDeckClick: (String) -> Unit,
    onDeckImported: (String) -> Unit,
    onBuildDeck: () -> Unit,
    /** A link shared into the app, imported once on arrival. */
    sharedLink: String? = null,
    onSharedLinkHandled: () -> Unit = {},
    viewModel: DecksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addDialogOpen by remember { mutableStateOf(false) }

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
        Box(Modifier.fillMaxSize().padding(padding)) {
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.decks, key = { it.id }) { deck ->
                        ListItem(
                            modifier = Modifier.clickable { onDeckClick(deck.id) },
                            headlineContent = { Text(deck.name) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        deck.heroName,
                                        DeckRepository.parseAspects(deck.aspects)
                                            .map { aspect -> aspectLabel(aspect) }
                                            .joinToString(" / ")
                                            .takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.delete(deck.id) }) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.action_delete),
                                    )
                                }
                            },
                        )
                        HorizontalDivider()
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
