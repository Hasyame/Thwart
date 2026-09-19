package com.hasyame.marvelchampions.ui.achievements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.achievements.Unlock

/**
 * What the game just recorded earned, on its result page: at most three
 * named, more summarised as a count, and a way to the achievements. Only
 * the delta between before and after the game, never the whole history,
 * so a first import does not fire forty of these.
 */
@Composable
fun UnlockedAchievements(unlocked: List<Unlock>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (unlocked.isEmpty()) {
        return
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        // The panel's own surface, with the star and the link in the accent:
        // on the accent itself the link could not be read.
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (unlocked.size > SHOWN) {
                    Text(
                        pluralStringResource(R.plurals.achievements_toast_many, unlocked.size, unlocked.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    unlocked.forEach { unlock ->
                        val title = AchievementTexts.title(unlock.id)?.let { stringResource(it) } ?: unlock.id
                        Text(
                            stringResource(R.string.achievements_toast_one, title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.achievements_toast_open)) }
        }
    }
}

private const val SHOWN = 3
