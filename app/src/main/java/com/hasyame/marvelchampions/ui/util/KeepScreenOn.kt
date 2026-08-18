package com.hasyame.marvelchampions.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Holds the screen awake for as long as this is in the composition.
 *
 * A tracker that has locked itself is worse than no tracker: the numbers on it
 * are the state of a game in progress, and a table does not touch the phone
 * every thirty seconds. Wanted only while a game is actually being counted, so
 * it is scoped to that screen rather than set on the window once and forgotten.
 *
 * `keepScreenOn` on the view rather than a wake lock: it needs no permission,
 * and it cannot outlive the screen that asked for it — Android clears it when
 * the view goes away, and [DisposableEffect] clears it before that anyway.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
