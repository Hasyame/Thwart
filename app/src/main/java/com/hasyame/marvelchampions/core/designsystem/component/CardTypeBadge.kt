package com.hasyame.marvelchampions.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.core.designsystem.theme.TypeAlly
import com.hasyame.marvelchampions.core.designsystem.theme.TypeEvent
import com.hasyame.marvelchampions.core.designsystem.theme.TypeObligation
import com.hasyame.marvelchampions.core.designsystem.theme.TypeOther
import com.hasyame.marvelchampions.core.designsystem.theme.TypeResource
import com.hasyame.marvelchampions.core.designsystem.theme.TypeSupport
import com.hasyame.marvelchampions.core.designsystem.theme.TypeUpgrade

/**
 * The mark that stands for a card type in a list.
 *
 * Shape as well as colour, deliberately. A list told apart by colour alone is
 * a list a colour-blind player cannot skim, and these six are exactly the
 * greens and reds that go first. The shapes are distinct at 14dp, which is the
 * size this is actually used at.
 *
 * Drawn rather than iconised because the project ships `material-icons-core`
 * only, and the icons that would fit are all in the extended set. Six shapes
 * are cheaper than that dependency and read better small.
 */
enum class CardTypeMark(val colour: Color) {
    ALLY(TypeAlly),
    EVENT(TypeEvent),
    SUPPORT(TypeSupport),
    UPGRADE(TypeUpgrade),
    RESOURCE(TypeResource),
    OBLIGATION(TypeObligation),
    OTHER(TypeOther),
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
 * A small filled shape standing for the card's type.
 *
 * Decorative: the type is already written beside it in words, so this carries
 * no content description and screen readers skip it rather than announcing a
 * shape.
 */
@Composable
fun CardTypeBadge(typeCode: String, modifier: Modifier = Modifier) {
    val mark = cardTypeMark(typeCode)
    Canvas(modifier.size(BADGE_SIZE)) {
        when (mark) {
            CardTypeMark.ALLY -> drawCircle(mark.colour)
            CardTypeMark.EVENT -> drawPolygon(mark.colour, sides = 3)
            CardTypeMark.SUPPORT -> drawRect(mark.colour)
            CardTypeMark.UPGRADE -> drawPolygon(mark.colour, sides = 4)
            CardTypeMark.RESOURCE -> drawPolygon(mark.colour, sides = 6)
            // An open ring, so the one type a player does not choose to include
            // does not sit in the list looking like the ones they did.
            CardTypeMark.OBLIGATION -> drawCircle(
                mark.colour,
                radius = size.minDimension / 2f - RING_WIDTH / 2f,
                style = Stroke(width = RING_WIDTH),
            )
            CardTypeMark.OTHER -> drawCircle(mark.colour, radius = size.minDimension / 4f)
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
