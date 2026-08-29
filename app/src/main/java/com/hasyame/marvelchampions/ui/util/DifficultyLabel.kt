package com.hasyame.marvelchampions.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.randomizer.Difficulty

/**
 * The game's own name for a difficulty.
 *
 * Shared because three screens now show one: the randomiser, the custom game
 * setup, and starting a campaign on expert. It existed twice already, once as a
 * resource id and once as a resolved string, and a third copy is how the names
 * drift apart.
 */
fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.STANDARD_I -> R.string.difficulty_standard_i
    Difficulty.STANDARD_II -> R.string.difficulty_standard_ii
    Difficulty.STANDARD_III -> R.string.difficulty_standard_iii
    Difficulty.EXPERT_I -> R.string.difficulty_expert_i
    Difficulty.EXPERT_II -> R.string.difficulty_expert_ii
}

/** The same, already resolved, for the places that want a string. */
@Composable
fun difficultyLabel(difficulty: Difficulty): String = stringResource(difficulty.labelRes())
