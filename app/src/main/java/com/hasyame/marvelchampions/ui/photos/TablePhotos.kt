package com.hasyame.marvelchampions.ui.photos

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.photos.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Takes a photograph of the table with the phone's own camera app.
 *
 * The app holds no camera permission and opens no camera. It creates an empty
 * file, hands the camera app a URI for it, and is told whether anything was
 * written. A camera that was opened and cancelled leaves an empty file behind,
 * which is thrown away rather than kept as a broken thumbnail.
 *
 * [onTaken] receives the file name to remember, which is what a play stores.
 */
@Composable
fun rememberTablePhotoCapture(
    photoStore: PhotoStore,
    scope: CoroutineScope,
    onTaken: (String) -> Unit,
): () -> Unit {
    // A remembered holder rather than a local var: the camera is another app,
    // and this composition is recomposed while it is in front. A plain var is
    // reinitialised by that, and the name of the file being written would be
    // lost between launching the camera and hearing back from it.
    val pending = remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val name = pending.value ?: return@rememberLauncherForActivityResult
        pending.value = null
        scope.launch {
            if (!saved || photoStore.discardIfEmpty(name)) {
                return@launch
            }
            onTaken(name)
        }
    }
    return {
        val photo = photoStore.newPhoto()
        pending.value = photo.name
        launcher.launch(photo.uri.toUri())
    }
}

/** The button that takes one, with a count of what has been taken already. */
@Composable
fun TablePhotoButton(
    taken: Int,
    onTake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onTake, modifier = modifier) {
        Text(
            text = if (taken == 0) {
                stringResource(R.string.photo_take)
            } else {
                pluralStringResource(R.plurals.photo_take_more, taken, taken)
            },
        )
    }
}

/**
 * The photographs of one play, as thumbnails.
 *
 * A row that scrolls rather than a grid: a table is photographed once or twice,
 * not twenty times, and a row keeps the play readable underneath.
 */
@Composable
fun TablePhotoStrip(
    names: List<String>,
    photoStore: PhotoStore,
    onOpen: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((String) -> Unit)? = null,
) {
    if (names.isEmpty()) {
        return
    }
    LazyRow(
        modifier = modifier.height(THUMBNAIL),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(names, key = { it }) { name ->
            val file = photoStore.file(name) ?: return@items
            Row {
                AsyncImage(
                    model = file,
                    contentDescription = stringResource(R.string.photo_of_table),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(THUMBNAIL)
                        .clickable { onOpen(file.toUri()) },
                )
                onDelete?.let { delete ->
                    IconButton(onClick = { delete(name) }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.photo_delete),
                        )
                    }
                }
            }
        }
    }
}

private val THUMBNAIL = 96.dp
