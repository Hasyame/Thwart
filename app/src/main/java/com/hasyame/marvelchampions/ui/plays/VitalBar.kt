package com.hasyame.marvelchampions.ui.plays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.sin
import kotlin.random.Random

/**
 * How a bar reacts when its number moves the wrong way.
 *
 * [BLOOD] is the villain taking damage: the bar jolts and throws drops. Nothing
 * about a threat track suggests bleeding, so a scheme advancing gets [ELECTRIC]
 * instead, a sharper flicker with a charge running along the fill.
 */
enum class VitalFlavour { BLOOD, ELECTRIC }

/**
 * A health or threat bar that reacts when it is pushed.
 *
 * The numbers alone were readable but inert, and a tracker is watched across a
 * table by people who are not holding the phone. Movement carries further than
 * a digit changing.
 *
 * The reaction is driven by [value] changing rather than by the caller firing an
 * event, so nothing can move the counter without the bar noticing. It is
 * deliberately short: a table looks up, sees what happened, and looks back at
 * their cards.
 */
@Composable
fun VitalBar(
    value: Int,
    total: Int?,
    flavour: VitalFlavour,
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
) {
    val target = when {
        total == null || total <= 0 -> 0f
        else -> (value.toFloat() / total).coerceIn(0f, 1f)
    }
    val fill = remember { Animatable(target) }
    val shake = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val drops = remember { mutableStateListOf<Drop>() }
    var previous by remember { mutableStateOf(value) }
    val density = LocalDensity.current

    LaunchedEffect(target) {
        fill.animateTo(target, tween(durationMillis = 420, easing = LinearEasing))
    }

    LaunchedEffect(value) {
        val rose = value > previous
        previous = value
        if (!rose) {
            return@LaunchedEffect
        }
        if (flavour == VitalFlavour.BLOOD) {
            repeat(DROP_COUNT) {
                drops.add(
                    Drop(
                        x = Random.nextFloat(),
                        size = with(density) { (2 + Random.nextInt(3)).dp.toPx() },
                        delay = Random.nextInt(90),
                    ),
                )
            }
        }
        // One jolt, decaying. A loop here reads as a fault rather than a hit.
        shake.snapTo(0f)
        shake.animateTo(1f, tween(durationMillis = SHAKE_MILLIS, easing = LinearEasing))
        flash.snapTo(1f)
        flash.animateTo(0f, tween(durationMillis = 320, easing = LinearEasing))
        drops.clear()
    }

    val amplitude = with(density) { SHAKE_DP.dp.toPx() }
    // Decays as it goes, so the last shudder is small rather than stopping dead.
    val offset = sin(shake.value * SHAKE_TURNS) * amplitude * (1f - shake.value)

    val track = when (flavour) {
        VitalFlavour.BLOOD -> BLOOD_TRACK
        VitalFlavour.ELECTRIC -> ELECTRIC_TRACK
    }
    val bar = when (flavour) {
        VitalFlavour.BLOOD -> BLOOD
        VitalFlavour.ELECTRIC -> ELECTRIC
    }
    val hit = when (flavour) {
        VitalFlavour.BLOOD -> BLOOD_HIT
        VitalFlavour.ELECTRIC -> ELECTRIC_HIT
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { translationX = offset }
            .clip(RoundedCornerShape(percent = 50)),
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            drawRect(color = track, size = size)
            if (fill.value > 0f) {
                drawRect(
                    color = lerpColour(bar, hit, flash.value),
                    size = Size(size.width * fill.value, size.height),
                )
                if (flavour == VitalFlavour.ELECTRIC && flash.value > 0f) {
                    drawCharge(fill.value, flash.value)
                }
            }
            drops.forEach { drop ->
                // Falls and fades over the flash, so the drops are gone by the
                // time the bar has settled.
                val progress = (flash.value.let { 1f - it } - drop.delay / 300f)
                    .coerceIn(0f, 1f)
                if (progress > 0f) {
                    drawCircle(
                        color = BLOOD_HIT.copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                        radius = drop.size,
                        center = Offset(
                            x = size.width * drop.x,
                            y = size.height + progress * size.height * 1.6f,
                        ),
                    )
                }
            }
        }
    }
}

/** A single drop thrown off the bar when the villain is hit. */
private data class Drop(val x: Float, val size: Float, val delay: Int)

/** A charge running along the filled part, drawn as a jagged line. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCharge(
    fill: Float,
    strength: Float,
) {
    val end = size.width * fill
    val steps = 8
    var x = 0f
    var previous = Offset(0f, size.height / 2f)
    repeat(steps) { step ->
        x = end * (step + 1) / steps
        val up = step % 2 == 0
        val y = size.height / 2f + if (up) -size.height / 3f else size.height / 3f
        val point = Offset(x, y)
        drawLine(
            color = ELECTRIC_HIT.copy(alpha = strength),
            start = previous,
            end = point,
            strokeWidth = 2f,
        )
        previous = point
    }
}

/** Plain linear blend, so this file needs nothing from the animation graphics API. */
private fun lerpColour(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = from.alpha + (to.alpha - from.alpha) * amount,
)

private const val SHAKE_MILLIS = 380
private const val SHAKE_DP = 6f
/** Enough oscillations to read as a jolt rather than a slide. */
private const val SHAKE_TURNS = 22f
private const val DROP_COUNT = 7

private val BLOOD_TRACK = Color(0xFF3A1216)
private val BLOOD = Color(0xFFB3261E)
private val BLOOD_HIT = Color(0xFFFF5449)
private val ELECTRIC_TRACK = Color(0xFF102030)
private val ELECTRIC = Color(0xFF2E7BC4)
private val ELECTRIC_HIT = Color(0xFF7FDBFF)
