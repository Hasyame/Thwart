package com.hasyame.marvelchampions.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The same 24-unit artwork as Web's NavIcon.svelte, with rounded 1.8-unit strokes. */
internal object NavigationIcons {
    val Home by lazy { outline("Home", "m2 11 10-9 10 9M5 9v12h5v-7h4v7h5V9") }
    val Card by lazy { outline("Card",
        "M5 3h8a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z",
        "m7 8 2-2 2 2-2 3Z M20 15a4 4 0 1 1-8 0 4 4 0 1 1 8 0 M19 18l3 3",
    ) }
    val Deck by lazy {
        builder("Deck").apply {
            stroke("m5 17-3-1 3-13 12 3")
            stroke("M10 7h8a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z")
            stroke("m14 10 1.2 3 3.3.2-2.5 2.1.8 3.2-2.8-1.7-2.8 1.7.8-3.2-2.5-2.1 3.3-.2Z", 1.2f)
        }.build()
    }
    val Play by lazy {
        builder("Play").apply {
            stroke("M12 2 21 6v6c0 5-9 10-9 10S3 17 3 12V6Z")
            addPath(PathParser().parsePathString("m13 5-6 8h5l-1 6 6-9h-5Z").toNodes(), fill = SolidColor(Color.Black))
        }.build()
    }
    val Book by lazy { outline("Rules", "M12 5C8 2 4 3 2 4v16c4-2 7-1 10 1 3-2 6-3 10-1V4c-2-1-6-2-10 1Zm0 0v16") }
    val Chart by lazy { outline("Stats", "M3 3v18h18M7 17v-4m5 4V9m5 8V5m-11 2 5-3 4 1 5-3") }
    val Trophy by lazy { outline("Achievements", "M7 3h10v5a5 5 0 0 1-10 0ZM7 5H3v3a4 4 0 0 0 5 4m9-7h4v3a4 4 0 0 1-5 4M12 13v5m-5 3h10m-8-3h6") }
    val Collection by lazy { outline("Collection", "m3 7 9-4 9 4-9 4Zm0 0v12l9 3 9-3V7M12 11v11m-5-7h2m6 0h2") }
    val History by lazy { outline("History", "M3 10a9 9 0 1 1 1 7M3 4v6h6m3-4v6l4 3") }
    val Campaign by lazy { outline("Campaign", "m3 5 6-2 6 3 6-2v16l-6 2-6-3-6 2Zm6-2v16m6-13v16") }
    val Random by lazy { outline("Random", "m12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3Z") }

    val Website by lazy { outline("Website", "M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0 M3 12h18 M12 3c5 5 5 13 0 18-5-5-5-13 0-18") }
    val GitHub by lazy {
        builder("GitHub").apply {
            addPath(PathParser().parsePathString("M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.1.79-.25.79-.56v-2.17c-3.2.7-3.87-1.36-3.87-1.36-.52-1.33-1.28-1.68-1.28-1.68-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.19 1.76 1.19 1.03 1.76 2.69 1.25 3.35.96.1-.75.4-1.25.73-1.54-2.55-.29-5.24-1.28-5.24-5.68 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.17 1.18a11 11 0 0 1 5.77 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.83 1.19 3.09 0 4.41-2.69 5.38-5.26 5.67.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.51 11.51 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5z").toNodes(), fill = SolidColor(Color.Black))
        }.build()
    }

    private fun builder(name: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    private fun outline(name: String, vararg paths: String): ImageVector = builder(name).apply {
        paths.forEach { stroke(it) }
    }.build()
    private fun ImageVector.Builder.stroke(data: String, width: Float = 1.8f) {
        addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
}
