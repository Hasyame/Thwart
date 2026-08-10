package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.CampaignSummary
import com.hasyame.marvelchampions.domain.campaign.CampaignShareLabels
import com.hasyame.marvelchampions.domain.campaign.CampaignShareScenario
import com.hasyame.marvelchampions.domain.campaign.CampaignShareText
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.ui.util.shareText

/**
 * What a campaign amounted to: the totals, then each scenario with the answers
 * recorded for it.
 *
 * All of it is folded from the event log, so the record is the campaign as it
 * was actually played rather than a separate summary that could drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignRecordScreen(
    runId: String,
    onBack: () -> Unit,
    viewModel: CampaignRecordViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val shareContext = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var noShareApp by remember { mutableStateOf(false) }
    val noShareAppMessage = stringResource(R.string.campaign_share_no_app)

    LaunchedEffect(runId) { viewModel.load(runId) }

    LaunchedEffect(noShareApp) {
        if (noShareApp) {
            snackbarHostState.showSnackbar(noShareAppMessage)
            noShareApp = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = {
                    Text(
                        summary?.entity?.name?.ifBlank { summary?.entity?.templateName.orEmpty() }
                            .orEmpty(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // In the bar rather than behind a menu, for the same reason
                    // a decklist's is: the end of a campaign is the moment
                    // somebody wants to show the people they played it with.
                    val record = summary
                    val labels = campaignShareLabels()
                    IconButton(
                        enabled = record != null,
                        onClick = {
                            record?.let {
                                val shared = shareText(
                                    context = shareContext,
                                    subject = it.entity.templateName,
                                    text = it.asShareText(labels),
                                )
                                if (!shared) {
                                    noShareApp = true
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.campaign_share),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val record = summary
        if (record == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatsCard(record) }

            if (record.scenarios.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.campaign_log),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(record.scenarios) { entry ->
                    ComicPanel(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = entry.scenarioName,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(
                                    if (entry.victory) {
                                        R.string.campaign_victory
                                    } else {
                                        R.string.campaign_defeat
                                    },
                                ) + " · " + TimerState.format(entry.elapsedMillis),
                                color = if (entry.victory) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            if (entry.answers.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                entry.answers.forEach { (label, value) ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(record: CampaignSummary) {
    ComicPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // A campaign is a dozen hours of somebody's life. Finishing one
            // deserves more than a table of numbers.
            if (record.finished) {
                Text(
                    text = stringResource(R.string.campaign_finished_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.campaign_finished_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.campaign_finished_cleanup),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = record.entity.templateName,
                style = MaterialTheme.typography.titleMedium,
            )
            Stat(
                stringResource(R.string.campaign_stat_difficulty),
                record.entity.difficulty.replaceFirstChar(Char::uppercase),
            )
            Stat(
                stringResource(R.string.campaign_stat_time),
                TimerState.format(record.totalTimeMillis),
            )
            Stat(
                stringResource(R.string.campaign_stat_vp),
                record.totalVictoryPoints.toString(),
            )
            Stat(
                stringResource(R.string.campaign_stat_heroes),
                record.heroNames.joinToString(", "),
            )
            Stat(
                stringResource(R.string.campaign_stat_scenarios),
                (record.scenariosWon + record.scenariosLost).toString(),
            )
            Stat(stringResource(R.string.campaign_stat_wins), record.scenariosWon.toString())
            Stat(stringResource(R.string.campaign_stat_defeats), record.scenariosLost.toString())
            Stat(
                stringResource(R.string.campaign_stat_winrate),
                "${record.winRatePercent}%",
            )
            // Only the campaign with a shop has anything to say here; for the
            // others these were two rows of nought.
            if (record.hasMarket) {
                Stat(
                    stringResource(R.string.campaign_stat_cards_bought),
                    record.cardsBought.toString(),
                )
                Stat(
                    stringResource(R.string.campaign_stat_credits_left),
                    record.creditsRemaining.toString(),
                )
            }
        }
    }
}

/** The same words the screen shows, for the text that leaves the app. */
@Composable
private fun campaignShareLabels() = CampaignShareLabels(
    finished = stringResource(R.string.campaign_finished_title),
    inProgress = stringResource(R.string.campaign_share_in_progress),
    difficulty = stringResource(R.string.campaign_stat_difficulty),
    totalTime = stringResource(R.string.campaign_stat_time),
    victoryPoints = stringResource(R.string.campaign_stat_vp),
    heroes = stringResource(R.string.campaign_stat_heroes),
    scenariosPlayed = stringResource(R.string.campaign_stat_scenarios),
    wins = stringResource(R.string.campaign_stat_wins),
    defeats = stringResource(R.string.campaign_stat_defeats),
    winRate = stringResource(R.string.campaign_stat_winrate),
    cardsBought = stringResource(R.string.campaign_stat_cards_bought),
    creditsLeft = stringResource(R.string.campaign_stat_credits_left),
    victory = stringResource(R.string.campaign_victory),
    defeat = stringResource(R.string.campaign_defeat),
    footer = stringResource(R.string.campaign_share_footer),
)

private fun CampaignSummary.asShareText(labels: CampaignShareLabels): String =
    CampaignShareText.format(
        campaignName = entity.name,
        templateName = entity.templateName,
        difficulty = entity.difficulty.replaceFirstChar(Char::uppercase),
        finished = finished,
        totalTime = TimerState.format(totalTimeMillis),
        victoryPoints = totalVictoryPoints,
        heroNames = heroNames,
        scenariosWon = scenariosWon,
        scenariosLost = scenariosLost,
        winRatePercent = winRatePercent,
        hasMarket = hasMarket,
        cardsBought = cardsBought,
        creditsRemaining = creditsRemaining,
        scenarios = scenarios.map {
            CampaignShareScenario(
                name = it.scenarioName,
                victory = it.victory,
                time = TimerState.format(it.elapsedMillis),
            )
        },
        labels = labels,
    )

@Composable
private fun Stat(label: String, value: String) {
    if (value.isBlank()) {
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
