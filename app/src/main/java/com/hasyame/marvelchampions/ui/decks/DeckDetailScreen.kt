package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.domain.deckbuilder.DeckText
import com.hasyame.marvelchampions.domain.deckbuilder.DeckTextCard
import com.hasyame.marvelchampions.ui.util.aspectLabel
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

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = { Text(state.contents?.deck?.name ?: "") },
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

            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
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

                item {
                    ListItem(
                        overlineContent = { Text(stringResource(R.string.decks_hero)) },
                        headlineContent = { Text(contents.deck.heroName) },
                        supportingContent = {
                            Text(
                                DeckRepository.parseAspects(contents.deck.aspects)
                                    .map { aspectLabel(it) }
                                    .joinToString(" / "),
                            )
                        },
                        modifier = Modifier.clickable { onCardClick(contents.deck.heroCode) },
                    )
                    HorizontalDivider()
                    Text(
                        text = pluralStringResource(
                            R.plurals.decks_card_count,
                            contents.totalCards,
                            contents.totalCards,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                    DeckStatisticsSection(state.statistics)
                    HorizontalDivider()

                    // Legality is shown here because a campaign refuses an
                    // illegal deck, and finding that out at the campaign screen
                    // would be too late.
                    if (state.validation.isLegal) {
                        Text(
                            text = stringResource(R.string.decks_legal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        Text(
                            text = pluralStringResource(
                                R.plurals.decks_not_legal,
                                state.validation.problems.size,
                                state.validation.problems.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (state.hasLocalEdits) {
                        Text(
                            text = stringResource(R.string.decks_locally_edited),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        TextButton(
                            onClick = viewModel::revertToImported,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text(stringResource(R.string.decks_revert)) }
                    }
                    if (contents.missingCards.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.decks_missing_count,
                                contents.missingCards.size,
                                contents.missingCards.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (contents.unknownCardCodes.isNotEmpty()) {
                        // Happens when the deck uses a pack MarvelCDB has added
                        // since the last card sync.
                        Text(
                            text = pluralStringResource(
                                R.plurals.decks_unknown_cards,
                                contents.unknownCardCodes.size,
                                contents.unknownCardCodes.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                if (state.campaignCards.isNotEmpty()) {
                    item(key = "campaign-cards") {
                        Text(
                            text = stringResource(R.string.decks_campaign_cards),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        Text(
                            // They live on the campaign run, not in the deck,
                            // which is also why they are outside the deck size
                            // limits.
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
                        HorizontalDivider()
                    }
                }

                contents.cardsByType.forEach { (typeName, cards) ->
                    item(key = "type-$typeName") {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    items(cards, key = { it.card.code }) { deckCard ->
                        ListItem(
                            modifier = Modifier.clickable { onCardClick(deckCard.card.code) },
                            headlineContent = {
                                Text("${deckCard.quantity}× ${deckCard.card.name}")
                            },
                            supportingContent = {
                                Text(
                                    text = if (deckCard.missingFromCollection) {
                                        stringResource(
                                            R.string.decks_card_missing,
                                            deckCard.card.packCode.uppercase(),
                                        )
                                    } else {
                                        deckCard.card.packCode.uppercase()
                                    },
                                    color = if (deckCard.missingFromCollection) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
