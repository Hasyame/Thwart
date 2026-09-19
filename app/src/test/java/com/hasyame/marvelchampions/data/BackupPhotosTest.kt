package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.backup.BackupRepository
import com.hasyame.marvelchampions.data.backup.BackupResult
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.settings.AppPreferences
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The backup file, with and without the photographs.
 *
 * Written after a hand-run round trip showed table photos did not survive a
 * reinstall. They are opt-in, so both shapes have to keep working: the plain
 * document every existing backup is, and the archive the box now produces.
 */
@RunWith(RobolectricTestRunner::class)
// The real application deletes photographs no play refers to, moments after it
// starts. That is right, and it would race every test in here: these photos
// belong to no play. A plain Application leaves the subject alone.
@Config(application = android.app.Application::class)
class BackupPhotosTest {

    private lateinit var context: Context
    private lateinit var database: MarvelChampionsDatabase
    private lateinit var photos: PhotoStore
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        photos = PhotoStore(context, Dispatchers.Unconfined)
        repository = BackupRepository(
            context,
            database,
            AppPreferences(context),
            photos,
            Dispatchers.Unconfined,
            sessions = com.hasyame.marvelchampions.data.sync.SyncSessionStore(context, com.hasyame.marvelchampions.data.security.SecretStore()),
        )
    }

    @After
    fun tearDown() {
        database.close()
        photoDirectory().deleteRecursively()
    }

    private fun photoDirectory() = File(context.filesDir, "play_photos")

    /** Through the store's own door, so the test uses the paths the app uses. */
    private suspend fun givenAPhoto(name: String, content: String) {
        assertTrue(photos.write(name, content.toByteArray()))
    }

    /** A file on disk the content resolver can open, standing in for a chosen one. */
    private fun destination(name: String) =
        android.net.Uri.fromFile(File(context.cacheDir, name).apply { parentFile?.mkdirs() })

    @Test
    fun `left unticked, the backup is a plain document and carries no photographs`() = runTest {
        givenAPhoto("a.jpg", "first")

        val target = destination("plain.json")
        val result = repository.export(target, includePhotos = false)
        assertTrue(result is BackupResult.Exported)

        val backup = repository.peek(target).getOrThrow()
        assertEquals(emptyList<String>(), backup.photos)
        // Still readable as text, which is the whole point of the JSON form.
        assertTrue(File(target.path!!).readText().trimStart().startsWith("{"))
    }

    @Test
    fun `ticked, the photographs go in and come back`() = runTest {
        givenAPhoto("a.jpg", "first")
        givenAPhoto("b.jpg", "second")

        val target = destination("with-photos.zip")
        val exported = repository.export(target, includePhotos = true)
        assertTrue(exported is BackupResult.Exported)

        // The reinstall: everything the app was holding is gone.
        photoDirectory().deleteRecursively()

        val backup = repository.peek(target).getOrThrow()
        assertEquals(listOf("a.jpg", "b.jpg"), backup.photos)

        val restored = repository.restore(backup, target)
        assertTrue(restored is BackupResult.Restored)
        assertEquals(2, (restored as BackupResult.Restored).summary.photos)
        assertEquals("first", File(photoDirectory(), "a.jpg").readText())
        assertEquals("second", File(photoDirectory(), "b.jpg").readText())
    }

    @Test
    fun `a backup written before photographs existed still restores`() = runTest {
        val target = destination("legacy.json")
        repository.export(target, includePhotos = false)

        val backup = repository.peek(target).getOrThrow()
        val result = repository.restore(backup, source = null)

        assertTrue(result is BackupResult.Restored)
        assertEquals(0, (result as BackupResult.Restored).summary.photos)
    }

    /**
     * A backup is an ordinary file that leaves the device, so it comes back
     * untrusted. An entry that climbs out of the photo folder is refused rather
     * than written wherever it asked to go.
     */
    @Test
    fun `an archive cannot write outside the photo folder`() = runTest {
        val escape = "../../databases/marvel.db"
        assertFalse(photos.write(escape, "owned".toByteArray()))
        assertFalse(photos.write("evil.db", "owned".toByteArray()))
        assertTrue(photos.write("ok.jpg", "fine".toByteArray()))
    }

    /**
     * The names in the document are the manifest. An archive carrying a file
     * the document does not mention is not restored from.
     */
    @Test
    fun `an entry the document does not name is skipped`() = runTest {
        givenAPhoto("named.jpg", "kept")
        val target = destination("tampered.zip")
        repository.export(target, includePhotos = true)

        // Rewrite the archive with an extra photograph nobody asked for.
        val document = repository.peek(target).getOrThrow()
        val json = File(target.path!!).let { file ->
            java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }
                    .first { it.name == "backup.json" }
                    .let { zip.readBytes() }
            }
        }
        ZipOutputStream(File(target.path!!).outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("photos/named.jpg"))
            zip.write("kept".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("photos/smuggled.jpg"))
            zip.write("unwanted".toByteArray())
            zip.closeEntry()
        }
        photoDirectory().deleteRecursively()

        val result = repository.restore(document, target) as BackupResult.Restored
        assertEquals(1, result.summary.photos)
        assertTrue(File(photoDirectory(), "named.jpg").isFile)
        assertFalse(File(photoDirectory(), "smuggled.jpg").isFile)
    }
    @Test
    fun unknownDocumentFieldsSurviveActualRestoreAndExport() = runTest {
        val original = com.hasyame.marvelchampions.data.backup.Backup(
            createdAt = 1L,
            extra = kotlinx.serialization.json.JsonObject(mapOf("auditFutureField" to kotlinx.serialization.json.JsonPrimitive("keep me"))),
        )
        assertTrue(repository.restore(original) is BackupResult.Restored)
        val target = destination("audit-roundtrip.json")
        assertTrue(repository.export(target) is BackupResult.Exported)
        assertEquals(original.extra, repository.peek(target).getOrThrow().extra)
    }

    @Test
    fun photoStoreCannotDeleteOutsidePhotoDirectory() = runTest {
        val sentinel = File(context.filesDir, "audit-sentinel.txt")
        sentinel.writeText("synthetic audit data")
        try {
            photos.delete("../audit-sentinel.txt")
            assertTrue("photo deletion must remain in play_photos", sentinel.exists())
        } finally {
            sentinel.delete()
        }
    }    @Test
    fun photoReadsAndCleanupRejectTraversal() = runTest {
        val sentinel = File(context.filesDir, "sentinel.jpg")
        sentinel.writeBytes(byteArrayOf())
        try {
            assertEquals(null, photos.file("../sentinel.jpg"))
            assertFalse(photos.discardIfEmpty("../sentinel.jpg"))
            assertTrue(sentinel.exists())
        } finally {
            sentinel.delete()
        }
    }

    @Test
    fun logicalWritesAndDirtyMarkersRollBackTogether() = runTest {
        val failure = runCatching {
            database.syncStateDao().transaction {
                database.ownedPackDao().upsert(com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity("synthetic", 1))
                database.syncStateDao().markDirty("ownedPacks", "synthetic")
                error("interrupted operation")
            }
        }
        assertTrue(failure.isFailure)
        assertTrue(database.ownedPackDao().getOwned().isEmpty())
        assertEquals(null, database.syncStateDao().get("ownedPacks", "synthetic"))
    }

}
