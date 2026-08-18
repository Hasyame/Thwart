package com.hasyame.marvelchampions.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.core.designsystem.theme.PanelShadow

/**
 * Ben-day dots — the printed-halftone texture that reads as "comic" before any
 * other cue does.
 *
 * Drawn rather than shipped as an image so it stays crisp at any density and
 * costs nothing in the APK. Kept faint: it is paper texture, not decoration,
 * and anything stronger fights the text on top of it.
 */
fun Modifier.halftone(
    color: Color,
    dotRadius: Dp = 1.5.dp,
    spacing: Dp = 10.dp,
    alpha: Float = 0.14f,
): Modifier = drawBehind {
    val radius = dotRadius.toPx()
    val step = spacing.toPx()
    if (step <= 0f) {
        return@drawBehind
    }

    var row = 0
    var y = 0f
    while (y < size.height + step) {
        // Offsetting alternate rows by half a step gives the staggered screen a
        // press produces, rather than a plain grid.
        val startX = if (row % 2 == 0) 0f else step / 2f
        var x = startX
        while (x < size.width + step) {
            drawCircle(color = color, radius = radius, center = Offset(x, y), alpha = alpha)
            x += step
        }
        y += step
        row += 1
    }
}

/**
 * The radiating spokes a comic draws behind a word it wants you to hear.
 *
 * Drawn behind one headline and nowhere else. The effect works because it is
 * rare: put it behind a list and it stops meaning "this is the moment" and
 * starts meaning "this app has a background".
 */
fun Modifier.comicBurst(
    color: Color,
    spokes: Int = 24,
    alpha: Float = 0.13f,
): Modifier = drawBehind {
    if (spokes <= 0) {
        return@drawBehind
    }
    val centre = Offset(size.width / 2f, size.height / 2f)
    // Past the corners, so no spoke stops short inside the box.
    val reach = kotlin.math.hypot(size.width, size.height)
    val sweep = (2.0 * Math.PI / spokes).toFloat()

    for (index in 0 until spokes step 2) {
        val start = index * sweep
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(centre.x, centre.y)
            lineTo(
                centre.x + reach * kotlin.math.cos(start),
                centre.y + reach * kotlin.math.sin(start),
            )
            lineTo(
                centre.x + reach * kotlin.math.cos(start + sweep),
                centre.y + reach * kotlin.math.sin(start + sweep),
            )
            close()
        }
        drawPath(path = path, color = color, alpha = alpha)
    }
}

/**
 * A comic panel: heavy ink border and a hard offset shadow, no blur.
 *
 * Material's elevation shadow is a soft gradient, which is exactly what print
 * cannot do. A solid offset rectangle is what a panel actually looks like.
 */
@Composable
fun ComicPanel(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 2.dp,
    offset: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    val ink = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(6.dp)

    // The caller's modifier goes on the Surface, which is the panel you can
    // see, and the shadow is drawn behind that. It used to sit on an outer Box
    // with the Surface loose inside it: a panel asked to fill the width got a
    // full-width shadow with a content-width panel on top, which read as an
    // empty black slab beside the panel wherever the content was narrow.
    Surface(
        modifier = modifier.drawBehind {
            val dx = offset.toPx()
            drawRoundRect(
                // Always dark, never the outline colour. In the dark theme the
                // outline is a pale cream, so the drop shadow was drawn as a
                // bright slab sticking out behind every panel — a glow where a
                // shadow belonged.
                color = PanelShadow,
                topLeft = Offset(dx, dx),
                size = Size(size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                // Opaque. A half-transparent offset rectangle is neither a
                // printed shadow nor a real one — it reads as a smudge behind
                // the panel. Print has no blur and no partial ink.
                alpha = 1f,
            )
        },
        shape = shape,
        color = color,
        border = BorderStroke(borderWidth, ink),
        content = { Box(content = content) },
    )
}
