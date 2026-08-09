package com.hasyame.marvelchampions.ui.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.ui.campaign.CampaignListViewModel

/**
 * Where a game starts, whichever kind it is.
 *
 * Three ways in, as three panels rather than a menu: the choice between rolling
 * a game and building one is the first decision a player makes, and burying it
 * in a list of equal-weight rows would make it look like an afterthought.
 *
 * A campaign already under way is promoted to the top. Resuming is far more
 * common than starting, and it used to be a tab away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    onRandomDraw: () -> Unit,
    onOwnSetup: () -> Unit,
    onCampaigns: () -> Unit,
    onResumeCampaign: (String) -> Unit,
    viewModel: CampaignListViewModel = hiltViewModel(),
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val inProgress = summaries.filterNot { it.entity.finished }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.destination_play)) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .halftone(MaterialTheme.colorScheme.onBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            inProgress.firstOrNull()?.let { running ->
                Choice(
                    icon = Icons.Filled.PlayArrow,
                    title = stringResource(R.string.play_continue),
                    subtitle = running.entity.name.ifBlank { running.entity.templateName },
                    onClick = { onResumeCampaign(running.entity.id) },
                )
            }

            Text(
                text = stringResource(R.string.play_start_something),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Choice(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.play_random),
                subtitle = stringResource(R.string.play_random_detail),
                onClick = onRandomDraw,
            )
            Choice(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.play_own),
                subtitle = stringResource(R.string.play_own_detail),
                onClick = onOwnSetup,
            )
            Choice(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.play_campaign),
                subtitle = stringResource(R.string.play_campaign_detail),
                onClick = onCampaigns,
            )
        }
    }
}

/** One way to start a game: a panel big enough to be a decision, not a row. */
@Composable
private fun Choice(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ComicPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
