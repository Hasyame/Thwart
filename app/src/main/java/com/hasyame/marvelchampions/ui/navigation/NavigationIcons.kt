package com.hasyame.marvelchampions.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The two icons Material does not have.
 *
 * Cards and Decks were a magnifying glass and a bulleted list, which say
 * "search" and "list" rather than what the tabs hold.
 *
 * Both are solid silhouettes rather than outlines, and that is the whole design
 * decision. Drawn as outlines they were legible at 96px and turned into a smear
 * at the 24dp a navigation bar actually uses: a 1.7 wide stroke leaves barely a
 * pixel of gap at that size, so the card, its border and the letter inside it
 * ran together. A filled shape with the detail knocked out of it survives being
 * small, which is the only size that matters here.
 */
internal object NavigationIcons {

    /**
     * A single card, with the M cut out of it.
     *
     * Knocked out rather than drawn on top: the icon is tinted a single colour
     * by [androidx.compose.material3.Icon], so the only way to show the letter
     * is to let the bar behind show through.
     */
    val Card: ImageVector by lazy {
        icon("Card") {
            // Even-odd, so the second subpath makes a hole in the first.
            filled(PathFillType.EvenOdd) {
                moveTo(7f, 2.5f)
                horizontalLineTo(17f)
                quadTo(19f, 2.5f, 19f, 4.5f)
                verticalLineTo(19.5f)
                quadTo(19f, 21.5f, 17f, 21.5f)
                horizontalLineTo(7f)
                quadTo(5f, 21.5f, 5f, 19.5f)
                verticalLineTo(4.5f)
                quadTo(5f, 2.5f, 7f, 2.5f)
                close()

                moveTo(8.9f, 16.8f)
                verticalLineTo(7.2f)
                horizontalLineTo(10.6f)
                lineTo(12f, 10.8f)
                lineTo(13.4f, 7.2f)
                horizontalLineTo(15.1f)
                verticalLineTo(16.8f)
                horizontalLineTo(13.7f)
                verticalLineTo(10.4f)
                lineTo(12f, 14.4f)
                lineTo(10.3f, 10.4f)
                verticalLineTo(16.8f)
                close()
            }
        }
    }

    /**
     * Three cards, stepped rather than fanned.
     *
     * A fan was the first idea and the wrong one: rotated cards in a single
     * colour merge into a shape that reads as a shirt. Stepping them up and to
     * the right keeps every gap axis-aligned, which is what survives being
     * drawn at 24dp.
     *
     * The two cards behind are L-shapes rather than whole cards. Only the strip
     * that clears the card in front of them is ever visible, and drawing the
     * hidden remainder would fill the gap that makes them separate cards.
     */
    val Deck: ImageVector by lazy {
        icon("Deck") {
            filled {
                // Back card: its top edge and its right-hand column.
                moveTo(11.8f, 3.2f)
                horizontalLineTo(17.4f)
                quadTo(18.8f, 3.2f, 18.8f, 4.6f)
                verticalLineTo(13.4f)
                quadTo(18.8f, 14.8f, 17.4f, 14.8f)
                horizontalLineTo(16.6f)
                verticalLineTo(5.4f)
                horizontalLineTo(10.4f)
                verticalLineTo(4.6f)
                quadTo(10.4f, 3.2f, 11.8f, 3.2f)
                close()

                // Middle card, the same shape one step down and left.
                moveTo(9f, 6f)
                horizontalLineTo(14.6f)
                quadTo(16f, 6f, 16f, 7.4f)
                verticalLineTo(16.2f)
                quadTo(16f, 17.6f, 14.6f, 17.6f)
                horizontalLineTo(13.8f)
                verticalLineTo(8.2f)
                horizontalLineTo(7.6f)
                verticalLineTo(7.4f)
                quadTo(7.6f, 6f, 9f, 6f)
                close()

                // The card in front, whole.
                moveTo(6.2f, 8.8f)
                horizontalLineTo(11.8f)
                quadTo(13.2f, 8.8f, 13.2f, 10.2f)
                verticalLineTo(19f)
                quadTo(13.2f, 20.4f, 11.8f, 20.4f)
                horizontalLineTo(6.2f)
                quadTo(4.8f, 20.4f, 4.8f, 19f)
                verticalLineTo(10.2f)
                quadTo(4.8f, 8.8f, 6.2f, 8.8f)
                close()
            }
        }
    }

    private fun icon(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = SIZE.dp,
            defaultHeight = SIZE.dp,
            viewportWidth = SIZE,
            viewportHeight = SIZE,
        ).apply(content).build()

    /** Tinted by Icon(), so the colour named here is only a placeholder. */
    private fun ImageVector.Builder.filled(
        fillType: PathFillType = PathFillType.NonZero,
        content: PathBuilder.() -> Unit,
    ) {
        path(fill = SolidColor(Color.Black), pathFillType = fillType, pathBuilder = content)
    }

    private const val SIZE = 24f
}
