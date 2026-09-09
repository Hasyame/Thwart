package com.hasyame.marvelchampions.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The mark that stands for a card type in a list.
 *
 * Shape alone, and that is the whole point of it. A list told apart by colour
 * only is a list a colour-blind player cannot skim, so the type was always
 * carried by the shape; the colour that came with it said the same thing twice.
 * The shapes are distinct at 14dp, which is the size this is actually used at.
 *
 * Drawn rather than iconised because the project ships `material-icons-core`
 * only, and the icons that would fit are all in the extended set. Six shapes
 * are cheaper than that dependency and read better small.
 */
enum class CardTypeMark {
    ALLY,
    EVENT,
    SUPPORT,
    UPGRADE,
    RESOURCE,
    OBLIGATION,
    OTHER,
}

/** The mark for a MarvelCDB type code. */
fun cardTypeMark(typeCode: String): CardTypeMark = when (typeCode) {
    "ally" -> CardTypeMark.ALLY
    "event" -> CardTypeMark.EVENT
    "support" -> CardTypeMark.SUPPORT
    "upgrade" -> CardTypeMark.UPGRADE
    "resource" -> CardTypeMark.RESOURCE
    "obligation" -> CardTypeMark.OBLIGATION
    // Heroes, alter-egos and the whole encounter side share one mark: they
    // never sit in the same list as each other, so telling them apart here
    // would be drawing a distinction nobody is looking at.
    else -> CardTypeMark.OTHER
}

/**
 * A small shape standing for the card's type, in the colour of its aspect.
 *
 * Two facts in one mark, which is what makes it worth the space. The shape is
 * the type and the colour is the aspect: a player reading a decklist wants to
 * know how much leadership is in it as much as how many allies, and the colour
 * was previously spent restating the type the shape had already given.
 *
 * The aspect colours are the game's own, so they arrive already learned — the
 * same red for aggression that is printed on the card. A card with no aspect at
 * all, which in a deck means the hero's own cards, takes a neutral grey rather
 * than borrowing a colour that would claim an aspect it has not got.
 *
 * Decorative by default: in a list that already writes the type and the aspect
 * beside it, announcing a shape would only add noise. Where the row does not
 * write them, [contentDescription] carries them instead.
 */
@Composable
fun CardTypeBadge(
    typeCode: String,
    factionCode: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val mark = cardTypeMark(typeCode)
    val colour = aspectColor(factionCode) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val described = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(described.size(BADGE_SIZE)) {
        when (mark) {
            CardTypeMark.ALLY -> drawCircle(colour)
            CardTypeMark.EVENT -> drawPolygon(colour, sides = 3)
            CardTypeMark.SUPPORT -> drawRect(colour)
            CardTypeMark.UPGRADE -> drawPolygon(colour, sides = 4)
            CardTypeMark.RESOURCE -> drawPolygon(colour, sides = 6)
            // An open ring, so the one type a player does not choose to include
            // does not sit in the list looking like the ones they did.
            CardTypeMark.OBLIGATION -> drawCircle(
                colour,
                radius = size.minDimension / 2f - RING_WIDTH / 2f,
                style = Stroke(width = RING_WIDTH),
            )
            CardTypeMark.OTHER -> drawCircle(colour, radius = size.minDimension / 4f)
        }
    }
}

/** A regular polygon filling the canvas, point upwards. */
private fun DrawScope.drawPolygon(colour: Color, sides: Int) {
    val radius = size.minDimension / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val path = Path()
    for (corner in 0 until sides) {
        // Start at the top and go round: -90° puts a point up rather than a
        // flat edge, which is what makes the triangle and the diamond read.
        val angle = Math.toRadians((360.0 / sides) * corner - 90.0)
        val x = centre.x + radius * kotlin.math.cos(angle).toFloat()
        val y = centre.y + radius * kotlin.math.sin(angle).toFloat()
        if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, colour)
}

private val BADGE_SIZE = 14.dp
private const val RING_WIDTH = 3f
