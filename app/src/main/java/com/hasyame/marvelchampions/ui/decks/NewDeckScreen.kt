package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.ui.util.aspectLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeckScreen(
    onBack: () -> Unit,
    onDeckCreated: (String) -> Unit,
    viewModel: NewDeckViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdDeckId) {
        state.createdDeckId?.let {
            onDeckCreated(it)
            viewModel.consumeCreatedDeck()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.decks_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    singleLine = true,
                    label = { Text(stringResource(R.string.decks_name)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }

            if (state.selectedHero != null) {
                item {
                    Text(
                        text = pluralStringResource(
                            R.plurals.decks_choose_aspects,
                            state.aspectsNeeded,
                            state.aspectsNeeded,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NewDeckViewModel.ASPECTS.forEach { aspect ->
                            FilterChip(
                                selected = aspect in state.chosenAspects,
                                onClick = { viewModel.toggleAspect(aspect) },
                                label = { Text(aspectLabel(aspect)) },
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::create,
                        enabled = state.canCreate,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text(stringResource(R.string.decks_create)) }
                    HorizontalDivider(Modifier.padding(top = 16.dp))
                }
            }

            item {
                Text(
                    text = stringResource(R.string.decks_choose_hero),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp),
                )
            }

            items(state.heroes, key = { it.card.code }) { hero ->
                val selected = state.selectedHero?.card?.code == hero.card.code
                ListItem(
                    modifier = Modifier.clickable { viewModel.selectHero(hero) },
                    colors = if (selected) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    headlineContent = { Text(hero.card.name) },
                    supportingContent = {
                        Text(
                            text = if (hero.owned) {
                                hero.card.packCode.uppercase()
                            } else {
                                stringResource(
                                    R.string.decks_hero_not_owned,
                                    hero.card.packCode.uppercase(),
                                )
                            },
                            color = if (hero.owned) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
