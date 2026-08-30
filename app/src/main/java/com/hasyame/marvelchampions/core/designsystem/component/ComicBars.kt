package com.hasyame.marvelchampions.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hasyame.marvelchampions.core.designsystem.theme.AspectAggression
import com.hasyame.marvelchampions.core.designsystem.theme.AspectBasic
import com.hasyame.marvelchampions.core.designsystem.theme.AspectJustice
import com.hasyame.marvelchampions.core.designsystem.theme.AspectLeadership
import com.hasyame.marvelchampions.core.designsystem.theme.AspectPool
import com.hasyame.marvelchampions.core.designsystem.theme.AspectProtection

/**
 * A solid red header, rather than the same colour as the page.
 *
 * Material's default top bar is the surface colour, which reads as a gap above
 * the content. Colouring it is most of what makes an app look like itself
 * rather than like a settings screen — and since it runs under the status bar
 * edge to edge, it frames every screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun comicTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    scrolledContainerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)

/**
 * The aspect a card belongs to, as its published colour.
 *
 * These are the game's own colours rather than the app's palette, and that is
 * deliberate: a player already reads aggression as red and leadership as blue
 * from the cards on the table. Recolouring them to match the app would throw
 * away knowledge the player arrives with.
 */
@Composable
fun aspectColor(factionCode: String?): Color? = when (factionCode?.lowercase()) {
    "aggression" -> AspectAggression
    "justice" -> AspectJustice
    "leadership" -> AspectLeadership
    "protection" -> AspectProtection
    "pool" -> AspectPool
    "basic" -> AspectBasic
    // Hero and encounter cards have no aspect, so they get no stripe rather
    // than a misleading one.
    else -> null
}
