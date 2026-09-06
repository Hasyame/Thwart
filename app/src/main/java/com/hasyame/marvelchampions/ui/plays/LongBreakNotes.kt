package com.hasyame.marvelchampions.ui.plays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.db.entity.PausedGameEntity
import com.hasyame.marvelchampions.data.db.entity.PausedPhase
import com.hasyame.marvelchampions.data.db.entity.VillainStep
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.ui.photos.TablePhotoStrip
import java.text.DateFormat
import java.util.Date

/**
 * The note a table left itself, read back.
 *
 * Its own file because two screens need it and they are not near each other: a
 * standalone game finds its note on the Play tab, and a campaign finds it on
 * the briefing of the run it belongs to. Two copies of this drifted apart the
 * moment either grew a field.
 *
 * Everything on the page that wrote this was optional, so everything here is
 * shown only if it was answered. A recap of blanks is worse than none.
 */
@Composable
fun LongBreakNotes(
    game: PausedGameEntity,
    photoStore: PhotoStore,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showDate) {
            Text(
                text = DateFormat.getDateInstance().format(Date(game.savedAt)),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(stringResource(pausedPhaseLabel(game)))
        pausedHeroLives(game).forEach { (name, life) ->
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
        val photos = game.photos.split(",").filter { it.isNotBlank() }
        TablePhotoStrip(names = photos, photoStore = photoStore, onOpen = { })
    }
}

/**
 * Hero names against the hit points written down for them.
 *
 * The two lists are stored apart because they are answered apart: the roster is
 * known, the hit points are typed. A life left blank was stored as "?" and is
 * dropped here rather than shown as an empty line.
 */
fun pausedHeroLives(game: PausedGameEntity): List<Pair<String, String>> {
    // heroLives is code|life; the names it should show are in heroes as code|name.
    val names = game.heroes.split(",")
        .filter { it.isNotBlank() }
        .associate { it.substringBefore('|') to it.substringAfter('|', it) }
    return game.heroLives.split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val code = entry.substringBefore('|')
            val life = entry.substringAfter('|', "")
            if (life.isBlank() || life == "?") {
                null
            } else {
                (names[code] ?: code) to life
            }
        }
}

/** Where the game stopped, as one line. */
fun pausedPhaseLabel(game: PausedGameEntity): Int =
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
