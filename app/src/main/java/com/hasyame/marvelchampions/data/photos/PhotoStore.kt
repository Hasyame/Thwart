package com.hasyame.marvelchampions.data.photos

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Photographs of a table, kept beside the play they belong to.
 *
 * Private storage, and nothing else. A picture of somebody's living room is not
 * something to put in the shared gallery without being asked, and the app has
 * no server to send it to. It is in the app's own directory, it goes when the
 * app goes, and the backup file does not carry it.
 *
 * The camera is the phone's own camera app, reached through a content URI. That
 * is why the app asks for no camera permission: it never opens the camera, it
 * hands a file to whichever app the owner already trusts with one.
 */
@Singleton
class PhotoStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /** A file for the camera to write into, and the name to remember it by. */
    fun newPhoto(): NewPhoto {
        val name = "${UUID.randomUUID()}.jpg"
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            File(directory, name),
        )
        return NewPhoto(name = name, uri = uri.toString())
    }

    /** Where a remembered name actually lives, or null if the file has gone. */
    fun file(name: String): File? = File(directory, name).takeIf { it.isFile }

    suspend fun delete(name: String) = withContext(ioDispatcher) {
        File(directory, name).delete()
        Unit
    }

    /**
     * Throws away a photo the camera never wrote to.
     *
     * Cancelling the camera leaves a zero-length file behind, and a broken
     * thumbnail in the play is worse than no photo.
     */
    suspend fun discardIfEmpty(name: String): Boolean = withContext(ioDispatcher) {
        val file = File(directory, name)
        if (file.exists() && file.length() == 0L) {
            file.delete()
            true
        } else {
            false
        }
    }

    /**
     * Deletes photographs no play refers to any more.
     *
     * A game abandoned halfway leaves its pictures behind: they were taken
     * against a play that was never filed. Nothing else would ever remove them.
     */
    suspend fun deleteOrphans(keep: Set<String>) = withContext(ioDispatcher) {
        directory.listFiles().orEmpty()
            .filter { it.name !in keep }
            .forEach { it.delete() }
    }

    companion object {
        private const val DIRECTORY = "play_photos"

        /** Matches the provider authority declared in the manifest. */
        const val AUTHORITY_SUFFIX = ".photos"
    }
}

/** A file the camera is about to fill, named so the play can remember it. */
data class NewPhoto(val name: String, val uri: String)
