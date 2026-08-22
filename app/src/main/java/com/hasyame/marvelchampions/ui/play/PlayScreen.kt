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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hasyame.marvelchampions.data.db.entity.PausedGameEntity
import com.hasyame.marvelchampions.data.db.entity.PausedPhase
import com.hasyame.marvelchampions.data.db.entity.VillainStep
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.ui.photos.TablePhotoStrip
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.res.pluralStringResource
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
    onVersus: () -> Unit,
    onResumeCampaign: (String) -> Unit,
    viewModel: CampaignListViewModel = hiltViewModel(),
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val paused by viewModel.pausedGame.collectAsStateWithLifecycle()
    var showPaused by remember { mutableStateOf(false) }
    val inProgress = summaries.filterNot { it.entity.finished }

    // Not a screen of its own: it is a page of notes, read once, and a dialog
    // keeps the table it belongs to one tap away.
    if (showPaused) {
        paused?.let { game ->
            PausedGameRecap(
                game = game,
                photoStore = viewModel.photoStore,
                onForget = {
                    viewModel.forgetPausedGame()
                    showPaused = false
                },
                onClose = { showPaused = false },
            )
        }
    }

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

            // A game left mid-play, with what was written down about the table.
            paused?.let { game ->
                Choice(
                    icon = Icons.Filled.PlayArrow,
                    title = stringResource(R.string.paused_game_title),
                    subtitle = game.scenarioName,
                    onClick = { showPaused = true },
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

            // Civil War and Synthezoid Smackdown only: two teams, two boards,
            // one device between them.
            Choice(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.versus_setup_title),
                subtitle = stringResource(R.string.versus_subtitle),
                onClick = onVersus,
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

/**
 * The note a table left itself, read back.
 *
 * Everything on it was optional when it was written, so everything here is
 * shown only if it was answered. A recap of blanks would be worse than none.
 */
@Composable
private fun PausedGameRecap(
    game: PausedGameEntity,
    photoStore: PhotoStore,
    onForget: () -> Unit,
    onClose: () -> Unit,
) {
    // heroLives is code|life; the names it should show are in heroes as code|name.
    val heroNames = game.heroes.split(",")
        .filter { it.isNotBlank() }
        .associate { it.substringBefore('|') to it.substringAfter('|', it) }
    // A life left blank was stored as "?". Everything on that page was
    // optional, so an unanswered hero is left out rather than shown empty.
    val lives = game.heroLives.split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val code = entry.substringBefore('|')
            val life = entry.substringAfter('|', "")
            if (life.isBlank() || life == "?") {
                null
            } else {
                (heroNames[code] ?: code) to life
            }
        }
    val photos = game.photos.split(",").filter { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(game.scenarioName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = DateFormat.getDateInstance().format(Date(game.savedAt)),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(stringResource(phaseLabel(game)))
                lives.forEach { (name, life) ->
                    Text("$name: $life")
                }
                if (game.villainLife > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.paused_game_villain,
                            game.villainLife,
                            game.villainLife,
                            "I".repeat(game.villainStage.coerceIn(1, 3)),
                        ),
                    )
                }
                TablePhotoStrip(names = photos, photoStore = photoStore, onOpen = { })
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            TextButton(onClick = onForget) {
                Text(stringResource(R.string.paused_game_discard))
            }
        },
    )
}

/** Where the game stopped, as one line. */
private fun phaseLabel(game: PausedGameEntity): Int =
    if (game.phase == PausedPhase.VILLAIN.name) {
        when (game.villainStep) {
            VillainStep.PLACE_THREAT.name -> R.string.villain_step_threat
            VillainStep.ACTIVATE_MINIONS.name -> R.string.villain_step_minions
            VillainStep.DEAL_ENCOUNTERS.name -> R.string.villain_step_deal
            VillainStep.REVEAL_ENCOUNTERS.name -> R.string.villain_step_reveal
            VillainStep.PASS_FIRST_PLAYER.name -> R.string.villain_step_pass
            else -> R.string.phase_villain
        }
    } else {
        R.string.phase_player
    }
