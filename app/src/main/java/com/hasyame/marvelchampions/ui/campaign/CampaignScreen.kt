package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import com.hasyame.marvelchampions.core.designsystem.component.ComicEmptyState
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.CampaignStage
import com.hasyame.marvelchampions.data.repository.CampaignSummary
import com.hasyame.marvelchampions.data.repository.ScenarioProgress
import com.hasyame.marvelchampions.data.repository.ScenarioStanding
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState

/**
 * The campaign tab: runs in progress, campaigns already finished, and a way to
 * start another.
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

    val inProgress = summaries.filterNot { it.entity.finished }
    val finished = summaries.filter { it.entity.finished }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.destination_campaign)) },
                // Reached from the Play hub since Campaign stopped being a tab,
                // so it needs a way back like any other pushed screen.
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartCampaign,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.campaign_start)) },
            )
        },
    ) { padding ->
        if (summaries.isEmpty()) {
            ComicEmptyState(
                message = stringResource(R.string.campaign_empty),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (inProgress.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.campaign_in_progress_section)) }
                items(inProgress, key = { it.entity.id }) { summary ->
                    RunPanel(
                        summary = summary,
                        onClick = { onOpenRun(summary.entity.id) },
                        onDelete = { confirmDelete = summary },
                    )
                }
            }

            if (finished.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.campaign_finished_section)) }
                items(finished, key = { it.entity.id }) { summary ->
                    RunRow(
                        summary = summary,
                        subtitle = listOf(
                            summary.entity.difficulty.replaceFirstChar(Char::uppercase),
                            TimerState.format(summary.totalTimeMillis),
                            stringResource(
                                R.string.campaign_stat_vp_short,
                                summary.totalVictoryPoints,
                            ),
                        ).joinToString(" · "),
                        // A finished campaign opens its record, not the run.
                        onClick = { onOpenRecord(summary.entity.id) },
                        onDelete = { confirmDelete = summary },
                    )
                }
            }
        }
    }

    confirmDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.campaign_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.campaign_delete_message,
                        summary.entity.name.ifBlank { summary.entity.templateName },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRun(summary.entity.id)
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

    if (errors.isNotEmpty() || message != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessages,
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessages) {
                    Text(stringResource(android.R.string.ok))
                }
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

@Composable
private fun SectionHeader(title: String) {
    // A rule under the title, the way a comic breaks a page into sections. It
    // also does the practical job of separating "in progress" from "finished"
    // at a glance, which a colour change alone was not doing.
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .width(48.dp)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.tertiary),
        )
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
        CampaignStage.TURNING_ENVIRONMENTS ->
            stringResource(R.string.campaign_stage_environments)

        CampaignStage.READY -> if (scenario.isBlank()) {
            stringResource(R.string.campaign_stage_ready_unknown)
        } else {
            stringResource(R.string.campaign_stage_ready, scenario)
        }

        CampaignStage.PLAYING -> if (scenario.isBlank()) {
            stringResource(R.string.campaign_stage_playing_unknown)
        } else {
            stringResource(R.string.campaign_stage_playing, scenario)
        }

        CampaignStage.LONG_BREAK -> if (scenario.isBlank()) {
            stringResource(R.string.campaign_stage_long_break_unknown)
        } else {
            stringResource(R.string.campaign_stage_long_break, scenario)
        }

        CampaignStage.LOST -> stringResource(R.string.campaign_stage_lost)
        CampaignStage.FINISHED -> stringResource(R.string.campaign_stage_finished)
    }
}


/**
 * A campaign on the table, as a panel rather than a line.
 *
 * It answers, without being opened: what is this, who is playing it, what is it
 * waiting for, how long has it taken, how far through it is, and which
 * scenarios are done. All of that was a tap away before, which sounds close
 * enough until you have three campaigns and want to know which one to pick up.
 */
@Composable
private fun RunPanel(
    summary: CampaignSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ComicPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier.clickable(onClick = onClick).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.entity.name.ifBlank { summary.entity.templateName },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }

            Text(
                text = listOfNotNull(
                    summary.entity.difficulty.replaceFirstChar(Char::uppercase),
                    summary.heroNames.joinToString(", ").takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stageLine(summary),
                style = MaterialTheme.typography.labelLarge,
                // A break is the one state that is about the table rather than
                // about the campaign, so it is the one that gets its own
                // colour.
                color = if (summary.stage == CampaignStage.LONG_BREAK) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )

            if (summary.scenariosToPlay > 0) {
                Text(
                    text = listOf(
                        stringResource(
                            R.string.campaign_progress,
                            summary.scenariosSettled,
                            summary.scenariosToPlay,
                        ),
                        stringResource(
                            R.string.campaign_time_so_far,
                            TimerState.format(summary.timeSoFarMillis),
                        ),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }

            if (summary.scenarioStandings.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    summary.scenarioStandings.forEach { ScenarioChip(it) }
                }
            }
        }
    }
}

/**
 * One scenario, with its standing shown rather than spelled out.
 *
 * The fill carries it for anyone glancing, and the icon carries it for anyone
 * who cannot use the colour — which is also what the screen reader announces,
 * since the name alone would not say whether the job was won or pushed off the
 * board.
 *
 * The palette is the app's own two colours and nothing else. Green for a win
 * would be the obvious choice and it is not available here: this theme is red
 * and bone on purpose, and the one place gold was tried it collided with the
 * gold the game prints Justice in. So red is the scenario on the table, solid
 * bone is one that is finished, and everything unplayed is an outline.
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
            container = scheme.tertiaryContainer
            content = scheme.onTertiaryContainer
            icon = Icons.Filled.Check
            label = R.string.campaign_scenario_won
        }

        ScenarioStanding.LOST -> {
            container = scheme.surfaceVariant
            content = scheme.onSurfaceVariant
            icon = Icons.Filled.Clear
            label = R.string.campaign_scenario_lost
        }

        ScenarioStanding.REPLAYABLE -> {
            container = scheme.surfaceVariant
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

    // Every one gets an outline, so a row of them reads as a set of scenarios
    // rather than as chips with loose words between them.
    Surface(
        color = container,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, content.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = stringResource(label),
                    tint = content,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                // A job pushed off the board was never played and never will
                // be. Striking it through says that in a way a grey chip on its
                // own does not.
                textDecoration = if (item.standing == ScenarioStanding.GONE) {
                    TextDecoration.LineThrough
                } else {
                    null
                },
            )
        }
    }
}

/**
 * A finished campaign: a line in a list, because its story is in its record.
 *
 * The in-progress ones get [RunPanel] instead. The two are deliberately not the
 * same shape: one is something to carry on with and the other is something to
 * look back at, and a list where every entry looks equally live made the one
 * campaign actually on the table hard to find.
 */
@Composable
private fun RunRow(
    summary: CampaignSummary,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(summary.entity.name.ifBlank { summary.entity.templateName })
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(subtitle)
                if (summary.heroNames.isNotEmpty()) {
                    Text(
                        text = summary.heroNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        },
    )
    HorizontalDivider()
}
