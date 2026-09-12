package com.hasyame.marvelchampions.ui.ratings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.data.sync.RatingSummaryDto
import com.hasyame.marvelchampions.domain.ratings.RatingSubject
import com.hasyame.marvelchampions.domain.ratings.RatingWire

/** The word beside a score: each is a translation of a feeling, not of each other. */
@Composable
fun difficultyWord(score: Int): String = stringResource(
    when (score) {
        0 -> R.string.rating_word_0
        1 -> R.string.rating_word_1
        2 -> R.string.rating_word_2
        3 -> R.string.rating_word_3
        4 -> R.string.rating_word_4
        else -> R.string.rating_word_5
    },
)

/**
 * The question after a game, or on a game in the history: how hard was it?
 *
 * One row per subject, the scenario first and then each modular set that was
 * on the table. Optional by construction: nothing is preselected, there is no
 * state in which a row has to be answered, and clearing is one more control
 * beside the six rather than a dialog. The community average is never shown
 * here, where it would anchor the answer.
 */
@Composable
fun RatingPanel(
    title: String,
    subjects: List<RatingSubject>,
    own: Map<String, Int>,
    labelOf: (RatingSubject) -> String,
    onRate: (RatingSubject, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (subjects.isEmpty()) {
        return
    }
    ComicPanel(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.rating_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            subjects.forEach { subject ->
                RatingRow(
                    label = labelOf(subject),
                    sub = when (subject.kind) {
                        RatingSubject.Kind.SCENARIO -> stringResource(R.string.rating_sub_scenario)
                        RatingSubject.Kind.MODULAR -> stringResource(R.string.rating_sub_modular)
                        RatingSubject.Kind.CAMPAIGN -> null
                    },
                    own = own[subject.key],
                    onRate = { onRate(subject, it) },
                )
            }
        }
    }
}

/** One subject, six choices, nothing preselected. */
@Composable
fun RatingRow(
    label: String,
    sub: String?,
    own: Int?,
    onRate: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        sub?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Six chips, each the number and its word. Wrapped, because six words
        // do not fit one line on a phone in either language.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (score in RatingWire.MIN_SCORE..RatingWire.MAX_SCORE) {
                val word = difficultyWord(score)
                FilterChip(
                    selected = own == score,
                    // Tapping the chosen score again clears it: the same
                    // gesture as choosing, and no dialog to confirm nothing.
                    onClick = { onRate(if (own == score) null else score) },
                    label = { Text("$score · $word") },
                )
            }
        }
        if (own != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.rating_yours, own, difficultyWord(own)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { onRate(null) }) {
                    Text(stringResource(R.string.rating_clear))
                }
            }
        }
    }
}

/**
 * The average beside a decision, not beside the question.
 *
 * Shown where somebody is choosing, a drawn scenario, a set in the picker, a
 * campaign to start. No mean below the threshold, the server withholds it,
 * but the count, so it is clear that rating is a thing; and the player's own
 * score first, whatever the count, because their data is theirs. Nothing at
 * all when there is nothing to say.
 */
@Composable
fun RatingBadge(
    summary: RatingSummaryDto?,
    own: Int?,
    modifier: Modifier = Modifier,
) {
    val mean = summary?.mean
    val histogram = summary?.histogram
    val countOnly = summary != null && mean == null && summary.count > 0
    if (own == null && mean == null && !countOnly) {
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (own != null) {
            Text(
                text = "★ $own",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = "★ $own"
                },
            )
        }
        if (mean != null && summary != null) {
            Text(
                text = pluralStringResource(
                    R.plurals.rating_community,
                    summary.count,
                    // The locale from the composition, which recomposes when
                    // it changes; Locale.getDefault() here would not.
                    String.format(LocalConfiguration.current.locales[0], "%.1f", mean),
                    summary.count,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Six bars, index = score: where the truth of a bimodal
            // subject lives, and one small element.
            histogram?.let { bins ->
                val tallest = maxOf(1, bins.maxOrNull() ?: 1)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.height(12.dp),
                ) {
                    bins.forEach { n ->
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(maxOf(1.dp, 12.dp * n / tallest))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
            }
        } else if (countOnly && summary != null) {
            Text(
                text = pluralStringResource(R.plurals.rating_count_only, summary.count, summary.count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
