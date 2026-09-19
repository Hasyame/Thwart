package com.hasyame.marvelchampions.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.achievements.AchievementStatusKind
import com.hasyame.marvelchampions.domain.achievements.TargetKind
import com.hasyame.marvelchampions.ui.util.aspectLabel

/** Details always use full history, independent of the album's display filters. */
@Composable
fun AchievementDetailDialogs(state: AchievementsUiState, onDismiss: () -> Unit, onHistory: () -> Unit, onPrepare: (com.hasyame.marvelchampions.domain.achievements.AchievementChallenge) -> Unit) {
    val detail = state.detail
    val album = state.albumDetail
    if (detail == null && album == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (detail != null) titleOf(detail.card) else "${album!!.hero} · ${album.scenario}") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.challenge?.let { challenge ->
                    item { TextButton(onClick = { onPrepare(challenge) }) { Text(stringResource(R.string.achievement_prepare)) } }
                }
                if (detail != null) {
                    item {
                        Text(descriptionOf(detail.card))
                        Text(stringResource(R.string.achievements_list_progress, detail.card.progress.current, detail.card.progress.target))
                        if (detail.card.status == AchievementStatusKind.UNAVAILABLE) Text(stringResource(R.string.achievements_list_unavailable_hint))
                    }
                    detail.targets?.let { targets ->
                        listOf(false, true).forEach { completed ->
                            val rows = targets.filter { (it.completedBy != null) == completed }
                            if (rows.isNotEmpty()) {
                                item { Text(stringResource(if (completed) R.string.achievements_detail_completed else R.string.achievements_detail_remaining), style = MaterialTheme.typography.titleSmall) }
                                items(rows, key = { it.key }) { target ->
                                    Column {
                                        Text(if (target.kind == TargetKind.ASPECT) aspectLabel(target.key) else target.name)
                                        target.pack?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        target.completedBy?.let { PlayEvidence(it) }
                                    }
                                }
                            }
                        }
                    }
                    if (detail.card.tiers.isNotEmpty()) {
                        item { Text(stringResource(R.string.achievements_detail_tiers), style = MaterialTheme.typography.titleSmall) }
                        items(detail.card.tiers) { tier ->
                            Text(stringResource(R.string.achievements_detail_tier, stringResource(AchievementTexts.tier(tier.tier)), tier.n,
                                stringResource(if (detail.card.progress.current >= tier.n) R.string.achievements_list_unlocked else R.string.achievements_detail_remaining)))
                            if (detail.card.progress.current < tier.n) Text(stringResource(R.string.achievements_detail_needed, tier.n - detail.card.progress.current))
                        }
                    }
                    detail.card.unlockedAt?.let { date -> item { Text(dateOf(date)) } }
                    detail.unlock?.let { play ->
                        item { Text(stringResource(R.string.achievements_detail_unlock), style = MaterialTheme.typography.titleSmall); PlayEvidence(play) }
                    }
                }
                if (album != null) {
                    if (album.plays.isEmpty()) item { Text(stringResource(R.string.achievements_grid_legend_never)) }
                    items(album.plays, key = { it.id }) { PlayEvidence(it) }
                }
                item { Text(stringResource(R.string.achievements_detail_derived), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.achievements_detail_close)) } },
        dismissButton = { TextButton(onClick = onHistory) { Text(stringResource(R.string.achievements_history)) } },
    )
}

@Composable
private fun PlayEvidence(play: AchievementPlayEvidence) {
    Text("${dateOf(play.date)} · ${stringResource(AchievementTexts.level(play.level))}", style = MaterialTheme.typography.bodySmall)
    Text(play.scenario, style = MaterialTheme.typography.bodySmall)
    Text(play.heroes, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(if (play.won) R.string.achievements_grid_legend_won else R.string.achievements_grid_legend_played), style = MaterialTheme.typography.bodySmall)
}
