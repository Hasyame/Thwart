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
 * no server to send it to. It is in the app's own directory and it goes when
 * the app goes.
 *
 * A backup carries these only when the player asks for it. Left alone, a
 * backup file is still a document that can be handed to somebody or dropped in
 * a shared drive, and photographs of a living room should not travel by
 * default.
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

    /**
     * Resolved once. Asking for the files directory on every access re-ran
     * mkdirs for no reason, and left the answer depending on when it was asked.
     */
    private val directory: File by lazy { File(context.filesDir, DIRECTORY) }

    private fun directory(): File = directory.apply { mkdirs() }

    /** A file for the camera to write into, and the name to remember it by. */
    fun newPhoto(): NewPhoto {
        val name = "${UUID.randomUUID()}.jpg"
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            File(directory(), name),
        )
        return NewPhoto(name = name, uri = uri.toString())
    }

    /** Where a remembered name actually lives, or null if the file has gone. */
    fun file(name: String): File? = File(directory(), name).takeIf { it.isFile }

    /** Every photograph currently held, for a backup that was asked to carry them. */
    suspend fun files(): List<File> = withContext(ioDispatcher) {
        directory().listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
    }

    /**
     * Puts a photograph back, under a name taken from a file the player chose.
     *
     * The name is stripped to its last segment before it is used. A backup is
     * an ordinary document that anyone can edit, and an entry called
     * `../../databases/plays.db` would otherwise be written wherever it liked.
     * Anything that is not a plain photograph name is refused outright.
     */
    suspend fun write(name: String, bytes: ByteArray): Boolean = withContext(ioDispatcher) {
        val safe = File(name).name
        if (safe != name || !SAFE_NAME.matches(safe)) {
            return@withContext false
        }
        File(directory(), safe).writeBytes(bytes)
        true
    }

    suspend fun delete(name: String) = withContext(ioDispatcher) {
        File(directory(), name).delete()
        Unit
    }

    /**
     * Throws away a photo the camera never wrote to.
     *
     * Cancelling the camera leaves a zero-length file behind, and a broken
     * thumbnail in the play is worse than no photo.
     */
    suspend fun discardIfEmpty(name: String): Boolean = withContext(ioDispatcher) {
        val file = File(directory(), name)
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
        directory().listFiles().orEmpty()
            .filter { it.name !in keep }
            .forEach { it.delete() }
    }

    companion object {
        private const val DIRECTORY = "play_photos"

        /** What newPhoto() produces: a UUID and a jpg suffix, nothing else. */
        private val SAFE_NAME = Regex("""[A-Za-z0-9._-]{1,80}\.jpg""", RegexOption.IGNORE_CASE)

        /** Matches the provider authority declared in the manifest. */
        const val AUTHORITY_SUFFIX = ".photos"
    }
}

/** A file the camera is about to fill, named so the play can remember it. */
data class NewPhoto(val name: String, val uri: String)
