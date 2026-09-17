package com.hasyame.marvelchampions.ui.decks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyWarning

/**
 * "Synergy issue: Rocket Raccoon (and Groot, Drax) has no synergy with this
 * identity." One line for the lot, in the tertiary colour rather than the
 * error one: the deck is legal, these cards are only ever resources in it.
 */
@Composable
fun SynergyWarningText(warnings: List<SynergyWarning>, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) {
        return
    }
    val first = warnings.first().cardName
    val names = if (warnings.size == 1) {
        first
    } else {
        stringResource(
            R.string.decks_synergy_and_others,
            first,
            warnings.drop(1).joinToString(", ") { it.cardName },
        )
    }
    Text(
        text = stringResource(R.string.decks_synergy_issue, names),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}
