package com.hasyame.marvelchampions.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hasyame.marvelchampions.R

/**
 * An aspect's name in the reader's language.
 *
 * Aspects travel through the app as their MarvelCDB identifiers, which are
 * English. Capitalising the identifier is close enough to read in English and
 * wrong in French, where the cards say Agressivité and Commandement, so every
 * screen that shows an aspect to a person goes through here.
 *
 * An unknown identifier is capitalised rather than dropped: a new aspect should
 * show up imperfectly rather than vanish from a deck's description.
 */
@Composable
fun aspectLabel(aspect: String): String = when (aspect.lowercase()) {
    "aggression" -> stringResource(R.string.aspect_aggression)
    "justice" -> stringResource(R.string.aspect_justice)
    "leadership" -> stringResource(R.string.aspect_leadership)
    "protection" -> stringResource(R.string.aspect_protection)
    "pool" -> stringResource(R.string.aspect_pool)
    else -> aspect.replaceFirstChar(Char::uppercase)
}
