package com.hasyame.marvelchampions.ui.achievements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls

/**
 * A round badge, the way a store shows an achievement: the card's art
 * zoomed onto the picture, in colour once earned, grey and faded while
 * not. A plain dark disc when no picture is known.
 */
@Composable
fun AchievementBadgeImage(badge: AchievementBadge, unlocked: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val ring = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val filter = if (unlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, ring, CircleShape)
            .alpha(if (unlocked) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        val cover = badge.cover
        val url = MarvelCdbUrls.cardImage(badge.imageSrc)
        when {
            cover != null -> Image(
                painterResource(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
            )

            url != null -> AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = ART_ZOOM
                    scaleY = ART_ZOOM
                },
            )
        }
    }
}

/** A card fitted to a disc shows its title band; zoomed, it shows the face. */
private const val ART_ZOOM = 1.6f
