package com.hasyame.marvelchampions.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.aspectColor
import com.hasyame.marvelchampions.core.designsystem.theme.PipInk
import com.hasyame.marvelchampions.core.designsystem.theme.PipRim
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls

/**
 * Enough of the art to recognise the card, without turning the list into a
 * gallery.
 *
 * Wider than it is tall on purpose. A portrait thumbnail is almost exactly the
 * proportions of a whole card, so cropping did nothing and each row showed a
 * legible-at-no-size miniature of the entire card, rules text and all. A
 * landscape window crops away everything below the illustration.
 */
private val THUMBNAIL_WIDTH = 58.dp
private val THUMBNAIL_HEIGHT = 44.dp

@Composable
fun CardListItem(
    card: CardEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether to name the pack on this row.
     *
     * The list is grouped by pack, so printing it on every row repeated the
     * same word down the whole screen. Shown on the first row of each pack, it
     * marks where one ends and the next begins instead.
     */
    showPack: Boolean = true,
) {
    ListItem(
        // The parameter existed and was never used, so every card in the list
        // simply ignored taps.
        modifier = modifier.clickable(onClick = onClick),
        colors = if (selected) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            ListItemDefaults.colors()
        },
        // A colour bar reads faster than the aspect's name does, and it costs no
        // row height. Hero and encounter cards have no aspect, so they get a
        // transparent bar rather than a misleading one — the rows stay aligned
        // either way.
        //
        // Beside it, the art. This is a game about cards people recognise on
        // sight, and a name in a list makes them read what they could have
        // recognised. The image is the same one the detail screen loads, so a
        // card opened once costs nothing to show again.
        leadingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(THUMBNAIL_HEIGHT)
                        .clip(RoundedCornerShape(2.dp))
                        .background(aspectColor(card.factionCode) ?: Color.Transparent),
                )
                CardThumbnail(card)
            }
        },
        headlineContent = {
            Text(
                text = card.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                // The faction only when it is an aspect. On a hero card
                // MarvelCDB reports the type and the faction both as "Hero",
                // so the row read "Héros · Héros ·" — the same word twice,
                // which looked like a bug because it was one.
                text = listOfNotNull(
                    card.typeName,
                    card.factionName.takeIf { aspectColor(card.factionCode) != null },
                    card.traits?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // Only the pack, and only where it changes. The cost used to sit here
        // too, as a bare number in a circle at the far right of the row — which
        // says nothing about what it counts. It now sits on the corner of the
        // art, where the card itself prints it, and needs no label.
        trailingContent = if (showPack) {
            {
                Text(
                    text = card.packCode.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
    )
    androidx.compose.material3.HorizontalDivider()
}

/**
 * The card's art, or a blank slot the same size.
 *
 * A placeholder rather than nothing: rows that grow a thumbnail as each image
 * arrives reflow the whole list under the reader's thumb.
 */
@Composable
private fun CardThumbnail(card: CardEntity) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(width = THUMBNAIL_WIDTH, height = THUMBNAIL_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        MarvelCdbUrls.cardImage(card.imageSrc)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                // The face is at the top of a card, so a crop that keeps the
                // centre would show mostly rules text.
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.size(width = THUMBNAIL_WIDTH, height = THUMBNAIL_HEIGHT),
            )
        }
        // Top-left, over the art, because that is where the printed card puts
        // it. A player reads it without being told what it is.
        card.cost?.let {
            CostPip(it, Modifier.align(Alignment.TopStart).padding(2.dp))
        }
    }
}

/**
 * Resource cost as a pip rather than a loose digit.
 *
 * The game prints costs in a circle, so a bare number beside a pack code reads
 * as neither one thing nor the other.
 */
@Composable
private fun CostPip(cost: Int, modifier: Modifier = Modifier) {
    // Fixed ink and cream rather than the theme's accent. This pip sits on
    // whatever art the card happens to have, so a colour that follows the theme
    // is a colour that will land on something the same shade sooner or later —
    // in the dark theme it did, and the number all but vanished. The cost is
    // information; it has to survive any background underneath it.
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(PipInk)
            .border(1.dp, PipRim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.card_cost_short, cost),
            style = MaterialTheme.typography.labelMedium,
            color = PipRim,
        )
    }
}
