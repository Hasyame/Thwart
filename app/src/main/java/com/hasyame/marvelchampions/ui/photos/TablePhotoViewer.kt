package com.hasyame.marvelchampions.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.photos.PhotoStore

@Composable
internal fun TablePhotoViewer(names: List<String>, initial: String, store: PhotoStore, onDismiss: () -> Unit) {
    val available = names.filter { store.file(it) != null }
    if (available.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var selected by rememberSaveable(initial) { mutableStateOf(initial) }
    val index = available.indexOf(selected).coerceAtLeast(0)
    var scale by remember(available[index]) { mutableFloatStateOf(1f) }
    var offset by remember(available[index]) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scale = (scale + 1f).coerceAtMost(5f) }) {
                    Text(stringResource(R.string.photo_zoom_in), color = Color.White)
                }
                TextButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                    Text(stringResource(R.string.photo_reset_zoom), color = Color.White)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().transformable(transform)) {
                AsyncImage(
                    model = store.file(available[index]),
                    contentDescription = stringResource(R.string.photo_of_table),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                )
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { selected = available[index - 1] }, enabled = index > 0) {
                    Text(stringResource(R.string.photo_previous), color = Color.White)
                }
                Text(stringResource(R.string.photo_position, index + 1, available.size), color = Color.White)
                TextButton(onClick = { selected = available[index + 1] }, enabled = index < available.lastIndex) {
                    Text(stringResource(R.string.photo_next), color = Color.White)
                }
            }
        }
    }
}
