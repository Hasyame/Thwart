package com.hasyame.marvelchampions.ui.history

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicEmptyState
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.theme.ResultLost
import com.hasyame.marvelchampions.core.designsystem.theme.ResultWon
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.ui.decks.AspectDot
import com.hasyame.marvelchampions.ui.photos.TablePhotoStrip
import java.text.DateFormat
import java.util.Date

/**
 * Every game played, as a shelf of tiles: the villain's face, the result,
 * the scenario, the heroes and the day. Newest first. A tap opens the game.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (tiles.isEmpty()) {
            ComicEmptyState(message = stringResource(R.string.history_empty), modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(tiles, key = { it.play.id }) { tile ->
                PlayTileCard(tile, onOpen = { onOpen(tile.play.id) })
            }
        }
    }
}

/**
 * One game, as the shelf of decks draws a deck: the villain's art with the
 * name band over its head, the result at its foot.
 */
@Composable
private fun PlayTileCard(tile: PlayTile, onOpen: () -> Unit) {
    val play = tile.play
    Box(
        Modifier
            .fillMaxWidth()
            .height(TILE_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen),
    ) {
        VillainArt(tile.villainImageSrc, tile.coverRes, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.75f), 1f to Color.Transparent)),
        )
        Column(Modifier.padding(10.dp)) {
            Text(
                play.scenarioName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                heroesOf(play),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResultBadge(play.won)
            Spacer(Modifier.weight(1f))
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(play.playedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Won or lost, in the colours the campaign shelf uses for the same words. */
@Composable
private fun ResultBadge(won: Boolean) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.7f)) {
        Row(
            Modifier.padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(18.dp).background(if (won) ResultWon else ResultLost, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (won) Icons.Filled.Check else Icons.Filled.Clear, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Text(
                stringResource(if (won) R.string.campaign_status_won else R.string.campaign_status_lost),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/**
 * A bundled cover when the game's campaign has one; otherwise the villain's
 * card, zoomed onto the picture as the deck tiles are, since fitted to the
 * width a card shows its title band and text box. Clipped, since the zoom
 * paints past the box otherwise. A plain dark field without either.
 */
@Composable
private fun VillainArt(imageSrc: String?, @DrawableRes coverRes: Int?, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (coverRes != null) {
            Image(painterResource(coverRes), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            return@Box
        }
        MarvelCdbUrls.cardImage(imageSrc)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = ART_ZOOM
                    scaleY = ART_ZOOM
                },
            )
        }
    }
}

private fun heroesOf(play: PlayEntity): String = listOfNotNull(
    play.heroName.takeIf { it.isNotBlank() },
    play.otherHeroes.takeIf { it.isNotBlank() },
).joinToString(", ")

// --- one game --------------------------------------------------------------------------------

/**
 * One game, read back: the villain across the top, the result, then what
 * was written down about it. The buttons are what can still be done with
 * it: play the same table again, send it to BoardGameGeek, forget it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayDetailScreen(
    playId: String,
    onBack: () -> Unit,
    onPlayAgain: (String) -> Unit,
    onOpenCampaign: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val tile = tiles.firstOrNull { it.play.id == playId }
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = comicTopBarColors(),
                title = { Text(tile?.play?.scenarioName.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (tile != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val play = tile?.play
        if (play == null) {
            // Gone, or not yet loaded: the shelf has nothing to say about it.
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(BANNER_HEIGHT)) {
                VillainArt(tile.villainImageSrc, tile.coverRes, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
                )
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResultBadge(play.won)
                    Text(
                        DateFormat.getDateInstance(DateFormat.LONG).format(Date(play.playedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // The table: who sat at it, and with what.
                Panel(stringResource(R.string.history_heroes)) {
                    if (play.roster.isNotEmpty()) {
                        play.roster.forEach { hero ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(hero.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                hero.aspect.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { aspect ->
                                    AspectDot(aspect, Modifier.padding(start = 6.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(hero.aspect, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text(heroesOf(play), style = MaterialTheme.typography.bodyLarge)
                        if (play.aspects.isNotBlank()) {
                            Text(play.aspects, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // The game: how it was set up and how long it took.
                Panel(stringResource(R.string.history_game)) {
                    Line(stringResource(R.string.campaign_stat_difficulty), listOfNotNull(play.difficulty.replaceFirstChar(Char::uppercase), play.standardSet.takeIf { it.isNotBlank() }).joinToString(" · "))
                    Line(stringResource(R.string.history_players), play.players.toString())
                    if (play.elapsedMillis > 0) {
                        Line(stringResource(R.string.history_duration), duration(play.elapsedMillis))
                    }
                    if (play.victoryPoints > 0) {
                        Line(stringResource(R.string.campaign_stat_vp), play.victoryPoints.toString())
                    }
                    if (play.location.isNotBlank()) {
                        Line(stringResource(R.string.history_location), play.location)
                    }
                    if (play.reportedToBgg) {
                        Line(stringResource(R.string.history_bgg), stringResource(R.string.history_bgg_sent))
                    }
                }

                if (play.notes.isNotBlank()) {
                    Panel(stringResource(R.string.decks_notes)) {
                        Text(play.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                val photos = play.photos.split(',').filter { it.isNotBlank() }
                if (photos.isNotEmpty()) {
                    Panel(stringResource(R.string.history_photos)) {
                        TablePhotoStrip(names = photos, photoStore = viewModel.photoStore, onOpen = { })
                    }
                }

                play.campaignRunId?.let { runId ->
                    OutlinedButton(onClick = { onOpenCampaign(runId) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.history_open_campaign))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    // The same table again, on the setup page. Not for a
                    // campaign's scenario, which is replayed from the campaign.
                    if (play.campaignRunId == null && play.scenarioCode.isNotBlank()) {
                        OutlinedButton(onClick = { onPlayAgain(play.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.plays_play_again))
                        }
                    }
                    // Only for a game not yet sent, so it is never sent twice.
                    if (!play.reportedToBgg) {
                        OutlinedButton(onClick = { viewModel.report(play.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.history_send_bgg))
                        }
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.plays_delete_title)) },
                text = { Text(stringResource(R.string.plays_delete_message, play.scenarioName)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            viewModel.delete(play.id)
                            onBack()
                        },
                    ) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

/** A label and its value on one line. */
@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
}

/** A rounded panel with a heading, like the ones the deck page stacks. */
@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

/** Hours and minutes when there are hours, minutes and seconds otherwise. */
private fun duration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d h %02d min".format(hours, minutes) else "%d:%02d".format(minutes, seconds)
}

private val TILE_HEIGHT = 170.dp
private val BANNER_HEIGHT = 220.dp

/** Scaled up about the centre, as the deck tiles are, so the window lands on the face. */
private const val ART_ZOOM = 1.4f
