package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicEmptyState
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.CampaignStage
import com.hasyame.marvelchampions.data.repository.CampaignSummary
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
                    RunRow(
                        summary = summary,
                        subtitle = summary.entity.difficulty.replaceFirstChar(Char::uppercase),
                        showProgress = true,
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

@Composable
private fun RunRow(
    summary: CampaignSummary,
    subtitle: String,
    /**
     * Whether to draw the bar and say where the campaign stands.
     *
     * Decided by the caller rather than read off the summary, because the two
     * ways of asking "is this over" do not always agree: the list splits its
     * sections on the row's own flag, while the summary folds the log. A run
     * filed as finished whose log has not caught up was showing a progress bar
     * and a "next scenario" under the finished heading.
     */
    showProgress: Boolean = false,
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
                // Only while it is running. A finished campaign has its record,
                // and a full bar under it would say nothing the section heading
                // has not already said.
                if (showProgress && summary.scenariosToPlay > 0) {
                    Text(
                        text = stageLine(summary),
                        style = MaterialTheme.typography.labelLarge,
                        // A break is the one state that is about the table
                        // rather than the campaign, so it is the one that gets
                        // a colour.
                        color = if (summary.stage == CampaignStage.LONG_BREAK) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        text = stringResource(
                            R.string.campaign_progress,
                            summary.scenariosSettled,
                            summary.scenariosToPlay,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { summary.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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
