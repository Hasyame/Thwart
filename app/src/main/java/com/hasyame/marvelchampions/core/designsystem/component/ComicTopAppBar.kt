package com.hasyame.marvelchampions.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.core.designsystem.theme.HeadingFill
import com.hasyame.marvelchampions.core.designsystem.theme.HeadingInk

/** A printed, angled title banner shared by the main screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = comicTopBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        colors = colors,
        scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = (80 * LocalDensity.current.fontScale).dp,
        title = {
            Box(
                Modifier.semantics { heading() }.drawBehind {
                    val slant = 8.dp.toPx().coerceAtMost(size.width / 4)
                    drawPath(Path().apply {
                        moveTo(slant, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width - slant, size.height)
                        lineTo(0f, size.height)
                        close()
                    }, HeadingFill)
                }.padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides HeadingInk) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(
                        color = HeadingInk,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    ), content = title)
                }
            }
        },
    )
}
