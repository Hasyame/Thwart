package com.hasyame.marvelchampions.ui.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    code: String,
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(code) { viewModel.load(code) }

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = { Text(state.card?.name ?: stringResource(R.string.destination_cards)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val card = state.card
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            card == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.cards_not_found)) }

            else -> CardDetailContent(
                card = card,
                isFavourite = state.isFavourite,
                onToggleFavourite = viewModel::toggleFavourite,
                pack = state.pack,
                linkedCard = state.linkedCard,
                locale = state.locale,
                onLocaleToggle = viewModel::toggleLocale,
                onLinkedCardClick = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}
