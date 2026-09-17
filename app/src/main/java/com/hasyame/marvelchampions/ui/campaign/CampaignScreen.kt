package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.data.repository.CampaignStage
import com.hasyame.marvelchampions.data.repository.CampaignSummary
import com.hasyame.marvelchampions.data.repository.ScenarioProgress
import com.hasyame.marvelchampions.data.repository.ScenarioStanding
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState

/**
 * The campaign shelf: the ones on the table, the ones over, and what they
 * add up to. Three tabs, as on the web.
 *
 * A tile per campaign, as on the shelf of decks: the final villain's art
 * across the top with the status on it, and everything written on the band
 * under it, where the contrast is certain. No box art, since the boxes are
 * product photography nothing here bundles; a campaign whose villains come
 * from no database (Fear No Evil) gets a colour field and its initial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(
    onBack: () -> Unit,
    onOpenRun: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onStartCampaign: () -> Unit,
    viewModel: CampaignListViewModel = hiltViewModel(),
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<CampaignSummary?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Two lists, not one sorted cleverly: a finished campaign is opened to
    // be read and a live one to be carried on, and mixed together the live
    // one is wherever its start date puts it.
    val inProgress = summaries.filterNot { it.finished }
    val finished = summaries.filter { it.finished }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    colors = comicTopBarColors(),
                    title = { Text(stringResource(R.string.destination_campaign)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.campaign_in_progress_section)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.campaign_finished_section)) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.campaign_tab_stats)) })
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                ExtendedFloatingActionButton(
                    onClick = onStartCampaign,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.campaign_start)) },
                )
            }
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        when (tab) {
            0 -> Shelf(
                summaries = inProgress,
                emptyMessage = stringResource(R.string.campaign_empty_in_progress),
                onOpen = onOpenRun,
                onDelete = { confirmDelete = it },
                modifier = content,
            )

            1 -> Shelf(
                summaries = finished,
                emptyMessage = stringResource(R.string.campaign_empty_finished),
                // A finished campaign opens its record, not the run.
                onOpen = onOpenRecord,
                onDelete = { confirmDelete = it },
                modifier = content,
            )

            else -> StatsTab(summaries, content)
        }
    }

    confirmDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.campaign_delete_title)) },
            text = { Text(stringResource(R.string.campaign_delete_message, summary.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRun(summary.entity.id)
                        confirmDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (errors.isNotEmpty() || message != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessages,
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessages) { Text(stringResource(android.R.string.ok)) }
            },
            title = { Text(stringResource(R.string.campaign_template_invalid)) },
            text = {
                Column {
                    message?.let { Text(it) }
                    errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
        )
    }
}

/** The name the table gave the campaign, or the box's when it gave none. */
private val CampaignSummary.title: String get() = entity.name.ifBlank { entity.templateName }

// --- the two shelves ------------------------------------------------------------------------

@Composable
private fun Shelf(
    summaries: List<CampaignSummary>,
    emptyMessage: String,
    onOpen: (String) -> Unit,
    onDelete: (CampaignSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (summaries.isEmpty()) {
        ComicEmptyState(message = emptyMessage, modifier = modifier)
        return
    }
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(summaries, key = { it.entity.id }) { summary ->
            CampaignTile(summary, onOpen = { onOpen(summary.entity.id) }, onDelete = { onDelete(summary) })
        }
    }
}

/** What a tile says about the campaign at the top of its art. */
private enum class Status(val labelRes: Int, val icon: ImageVector) {
    NOT_STARTED(R.string.campaign_status_not_started, Icons.Filled.PlayArrow),
    IN_PROGRESS(R.string.campaign_status_in_progress, Icons.Filled.PlayArrow),
    WON(R.string.campaign_status_won, Icons.Filled.Check),
    LOST(R.string.campaign_status_lost, Icons.Filled.Clear),
}

/**
 * Won when finished and not lost, whatever games were lost on the way: most
 * campaigns let a table lose a scenario and carry on, and a campaign is not
 * the result of its last game.
 */
private fun CampaignSummary.status(): Status = when {
    lost -> Status.LOST
    finished -> Status.WON
    scenarios.isEmpty() -> Status.NOT_STARTED
    else -> Status.IN_PROGRESS
}

/**
 * One campaign: the villain's art with the status on it, then the band with
 * the name, the box, how far it is, and every game's result in the order
 * it was played, so a campaign that lost twice on the way reads as one that
 * did. Details unfolds the scenarios, the clock, and the way to forget it.
 */
@Composable
private fun CampaignTile(summary: CampaignSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    var open by rememberSaveable(summary.entity.id) { mutableStateOf(false) }
    val status = summary.status()
    val live = status == Status.NOT_STARTED || status == Status.IN_PROGRESS

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Face(summary, status, Modifier.clickable(onClick = onOpen))

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        summary.entity.templateName,
                        summary.entity.difficulty.replaceFirstChar(Char::uppercase),
                        summary.heroNames.joinToString(", ").takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (live) {
                    Text(
                        stageLine(summary),
                        style = MaterialTheme.typography.labelLarge,
                        // A break is the one state about the table rather than
                        // the campaign, so it is the one with its own colour.
                        color = if (summary.stage == CampaignStage.LONG_BREAK) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                }

                // How far: scenarios beaten out of those the box holds, the
                // marks, and the bar under both.
                val total = summary.scenarioStandings.size
                val beaten = summary.scenarioStandings.count { it.standing == ScenarioStanding.WON }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.campaign_beaten, beaten, beaten, total),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (summary.scenarios.isNotEmpty()) {
                        ResultStrip(summary.scenarios.map { it.victory })
                    }
                }
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else beaten.toFloat() / total },
                    color = if (status == Status.LOST) ResultLost else if (live) MaterialTheme.colorScheme.primary else ResultWon,
                    trackColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (live) {
                        Button(onClick = onOpen) { Text(stringResource(R.string.campaign_continue)) }
                    }
                    OutlinedButton(onClick = { open = !open }) {
                        Text(stringResource(if (open) R.string.campaign_hide_details else R.string.campaign_details))
                    }
                }

                if (open) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.scenarioStandings.forEach { ScenarioChip(it) }
                    }
                    Text(
                        listOfNotNull(
                            stringResource(R.string.campaign_time_so_far, TimerState.format(summary.timeSoFarMillis)),
                            stringResource(R.string.campaign_stat_vp_short, summary.totalVictoryPoints).takeIf { summary.totalVictoryPoints > 0 },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDelete, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.campaign_forget))
                    }
                }
            }
        }
    }
}

/**
 * The art across the top of a tile, and the status badge on it. Nothing else
 * sits on the picture: text over art is the contrast trap the band avoids.
 * Without art the top is a colour field with the campaign's initial, the
 * same for the same campaign every time, and the tile looks finished all
 * the same, since offline the art will sometimes not arrive.
 */
@Composable
private fun Face(summary: CampaignSummary, status: Status, modifier: Modifier = Modifier) {
    // mod, not %: the sum wraps for a long id and hsl refuses a negative hue.
    val hue = summary.entity.templateId.fold(0) { acc, c -> (acc * 31 + c.code).mod(360) }
    Box(
        modifier
            .fillMaxWidth()
            .height(FACE_HEIGHT)
            // The zoomed art would otherwise paint past the box, over the band.
            .clipToBounds()
            .background(Color.hsl(hue.toFloat(), 0.35f, 0.22f)),
    ) {
        val url = MarvelCdbUrls.cardImage(summary.faceImageSrc)
        if (url != null) {
            // Zoomed onto the picture, as the deck tiles are: fitted to the
            // width a card shows its title band and text box, neither of
            // which is the villain.
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
        } else {
            Text(
                summary.title.take(1).uppercase(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent)),
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val tint = when (status) {
                    Status.WON -> ResultWon
                    Status.LOST -> ResultLost
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(Modifier.size(22.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(status.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(stringResource(status.labelRes), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/**
 * Every game's result, in the order it was played: a green tick, a red
 * cross. One description for the strip, since reading each mark aloud
 * would be noise.
 */
@Composable
private fun ResultStrip(results: List<Boolean>) {
    val won = results.count { it }
    val label = stringResource(R.string.campaign_results_label, won, results.size)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        results.forEach { victory ->
            Box(
                Modifier.size(22.dp).background(if (victory) ResultWon else ResultLost, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (victory) Icons.Filled.Check else Icons.Filled.Clear,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Where a campaign stands, in one line.
 *
 * A campaign is not always inside a scenario, and saying nothing during those
 * stretches was the worst of it: Fear No Evil and The Galaxy's Most Wanted both
 * stop between jobs and ask the table to do something, and the box went blank
 * for exactly the part nobody remembers a week later.
 */
@Composable
private fun stageLine(summary: CampaignSummary): String {
    val scenario = summary.currentScenarioName
    return when (summary.stage) {
        CampaignStage.CHOOSING -> stringResource(R.string.campaign_stage_choosing)
        CampaignStage.TURNING_ENVIRONMENTS -> stringResource(R.string.campaign_stage_environments)
        CampaignStage.READY -> if (scenario.isBlank()) stringResource(R.string.campaign_stage_ready_unknown) else stringResource(R.string.campaign_stage_ready, scenario)
        CampaignStage.PLAYING -> if (scenario.isBlank()) stringResource(R.string.campaign_stage_playing_unknown) else stringResource(R.string.campaign_stage_playing, scenario)
        CampaignStage.LONG_BREAK -> if (scenario.isBlank()) stringResource(R.string.campaign_stage_long_break_unknown) else stringResource(R.string.campaign_stage_long_break, scenario)
        CampaignStage.LOST -> stringResource(R.string.campaign_stage_lost)
        CampaignStage.FINISHED -> stringResource(R.string.campaign_stage_finished)
    }
}

/**
 * One scenario, with its standing shown rather than spelled out.
 *
 * The fill carries it for anyone glancing, and the icon carries it for anyone
 * who cannot use the colour, which is also what the screen reader announces.
 * A job pushed off the board was never played and never will be; striking
 * it through says that in a way a grey chip on its own does not.
 */
@Composable
private fun ScenarioChip(item: ScenarioProgress) {
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    val icon: ImageVector?
    val label: Int
    when (item.standing) {
        ScenarioStanding.WON -> {
            container = ResultWon.copy(alpha = 0.25f)
            content = scheme.onSurface
            icon = Icons.Filled.Check
            label = R.string.campaign_scenario_won
        }

        ScenarioStanding.LOST -> {
            container = ResultLost.copy(alpha = 0.25f)
            content = scheme.onSurface
            icon = Icons.Filled.Clear
            label = R.string.campaign_scenario_lost
        }

        ScenarioStanding.REPLAYABLE -> {
            container = scheme.surface
            content = scheme.onSurfaceVariant
            icon = Icons.Filled.Refresh
            label = R.string.campaign_scenario_replayable
        }

        ScenarioStanding.GONE -> {
            container = Color.Transparent
            content = scheme.outline
            icon = Icons.Filled.Clear
            label = R.string.campaign_scenario_gone
        }

        ScenarioStanding.CURRENT -> {
            container = scheme.primaryContainer
            content = scheme.onPrimaryContainer
            icon = Icons.Filled.PlayArrow
            label = R.string.campaign_scenario_current
        }

        ScenarioStanding.TO_PLAY -> {
            container = Color.Transparent
            content = scheme.onSurfaceVariant
            icon = null
            label = R.string.campaign_scenario_to_play
        }
    }

    Surface(
        color = container,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, content.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(it, contentDescription = stringResource(label), tint = content, modifier = Modifier.size(14.dp))
            }
            Text(
                item.name,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                textDecoration = if (item.standing == ScenarioStanding.GONE) TextDecoration.LineThrough else null,
            )
        }
    }
}

// --- the figures ----------------------------------------------------------------------------

/**
 * What every campaign adds up to: the totals, then the same by box and by
 * hero. All of it is folded from the runs on the shelf, so a campaign
 * forgotten stops counting, which is what forgetting it is for.
 */
@Composable
private fun StatsTab(summaries: List<CampaignSummary>, modifier: Modifier = Modifier) {
    if (summaries.isEmpty()) {
        ComicEmptyState(message = stringResource(R.string.campaign_stats_empty), modifier = modifier)
        return
    }
    val won = summaries.count { it.finished && !it.lost }
    val lost = summaries.count { it.lost }
    val scenariosWon = summaries.sumOf { it.scenariosWon }
    val scenariosLost = summaries.sumOf { it.scenariosLost }
    val played = scenariosWon + scenariosLost
    val time = summaries.sumOf { it.timeSoFarMillis }
    val points = summaries.sumOf { it.totalVictoryPoints }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(stringResource(R.string.campaign_stats_overview)) {
            Row(Modifier.fillMaxWidth()) {
                Figure(summaries.size.toString(), stringResource(R.string.campaign_stats_campaigns), Modifier.weight(1f))
                Figure(won.toString(), stringResource(R.string.campaign_status_won), Modifier.weight(1f), ResultWon)
                Figure(lost.toString(), stringResource(R.string.campaign_status_lost), Modifier.weight(1f), ResultLost)
                Figure((summaries.size - won - lost).toString(), stringResource(R.string.campaign_status_in_progress), Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                Figure(played.toString(), stringResource(R.string.campaign_stat_scenarios), Modifier.weight(1f))
                Figure(scenariosWon.toString(), stringResource(R.string.campaign_stat_wins), Modifier.weight(1f), ResultWon)
                Figure(scenariosLost.toString(), stringResource(R.string.campaign_stat_defeats), Modifier.weight(1f), ResultLost)
                Figure(
                    if (played == 0) "–" else "${scenariosWon * 100 / played} %",
                    stringResource(R.string.campaign_stat_winrate),
                    Modifier.weight(1f),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                Figure(TimerState.format(time), stringResource(R.string.campaign_stat_time), Modifier.weight(1f))
                Figure(points.toString(), stringResource(R.string.campaign_stat_vp), Modifier.weight(1f))
            }
        }

        // By box: each campaign played, how many times, and how those went.
        Panel(stringResource(R.string.campaign_stats_by_box)) {
            summaries.groupBy { it.entity.templateName }.entries
                .sortedByDescending { it.value.size }
                .forEach { (name, runs) ->
                    Breakdown(
                        name = name,
                        runs = runs.size,
                        won = runs.count { it.finished && !it.lost },
                        lost = runs.count { it.lost },
                        scenariosWon = runs.sumOf { it.scenariosWon },
                        scenariosPlayed = runs.sumOf { it.scenariosWon + it.scenariosLost },
                    )
                }
        }

        // By hero: who has been through the most, and who comes out best.
        val byHero = summaries.flatMap { summary -> summary.heroNames.map { it to summary } }.groupBy({ it.first }, { it.second })
        if (byHero.isNotEmpty()) {
            Panel(stringResource(R.string.campaign_stats_by_hero)) {
                byHero.entries.sortedByDescending { it.value.size }.forEach { (hero, runs) ->
                    Breakdown(
                        name = hero,
                        runs = runs.size,
                        won = runs.count { it.finished && !it.lost },
                        lost = runs.count { it.lost },
                        scenariosWon = runs.sumOf { it.scenariosWon },
                        scenariosPlayed = runs.sumOf { it.scenariosWon + it.scenariosLost },
                    )
                }
            }
        }
    }
}

/** One line of a breakdown: the name, the count, and the marks. */
@Composable
private fun Breakdown(name: String, runs: Int, won: Int, lost: Int, scenariosWon: Int, scenariosPlayed: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                listOf(
                    pluralStringResource(R.plurals.campaign_stats_runs, runs, runs),
                    stringResource(R.string.campaign_stats_won_lost, won, lost),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (scenariosPlayed == 0) "–" else "${scenariosWon * 100 / scenariosPlayed} %",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A number over its word, for a row of them. */
@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
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

private val FACE_HEIGHT = 200.dp

/** Scaled up about the centre, as the deck tiles are, so the window lands on the face. */
private const val ART_ZOOM = 1.4f
