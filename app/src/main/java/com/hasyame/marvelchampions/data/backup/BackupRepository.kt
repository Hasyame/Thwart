package com.hasyame.marvelchampions.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.hasyame.marvelchampions.core.util.runCatchingCancellable
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.BackupMetadataEntity
import com.hasyame.marvelchampions.data.db.entity.NO_EXTRAS
import com.hasyame.marvelchampions.data.io.ImportTooLargeException
import com.hasyame.marvelchampions.data.io.readBounded
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.ratings.of
import com.hasyame.marvelchampions.data.ratings.toEntity
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.ratings.RatingWire
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Writes and reads a backup file.
 *
 * An independent recovery point, whether or not account sync is enabled.
 * Synchronization propagates changes; a portable backup preserves a snapshot.
 *
 * Deliberately a plain, readable JSON document rather than a database copy: it
 * survives a schema change, can be inspected, and can be repaired by hand if it
 * ever comes to that.
 */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MarvelChampionsDatabase,
    private val preferences: AppPreferences,
    private val photoStore: PhotoStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val sessions: com.hasyame.marvelchampions.data.sync.SyncSessionStore,
) {

    private val json = DOCUMENT_JSON

    /**
     * @param includePhotos writes an archive carrying the table photographs
     *   too. Off by default: a backup is a document that gets handed around,
     *   and photographs of somebody's living room should not travel unasked.
     */
    suspend fun export(
        destination: Uri,
        includePhotos: Boolean = false,
    ): BackupResult = withContext(ioDispatcher) {
        runCatchingCancellable {
            // Listed before the database is touched. A suspending query can
            // resume on another thread, and the photo folder has to be read
            // from the one that owns it.
            val photoFiles = if (includePhotos) photoStore.files() else emptyList()
            // Read once and reused: the events are looked up per run, and
            // fetching the run list a second time to do it was pure waste.
            val settings = preferences.snapshot()
            val backup = database.withTransaction {
                val runs = database.campaignDao().getRuns()
                Backup(
                    createdAt = System.currentTimeMillis(),
                    appVersion = appVersion(),
                    ownedPacks = database.ownedPackDao().getOwned(),
                    excludedModularSets = database.excludedModularSetDao().getExcluded(),
                    excludedScenarios = database.excludedScenarioDao().getExcluded(),
                    decks = database.savedDeckDao().getDecks(),
                    campaignRuns = runs,
                    campaignEvents = runs.flatMap { database.campaignDao().getEvents(it.id) },
                    plays = database.playDao().getAllPlays(),
                    randomizerHistory = database.randomizerHistoryDao().getHistory(),
                    favouriteCards = database.favouriteDao().getAll(),
                    favouritePlays = database.favouritePlayDao().getAll(),
                    ratings = database.ratingDao().getAll().map { RatingWire.of(it) },
                    deckFolders = database.deckFolderDao().getFolders(),
                    photos = photoFiles.map { it.name },
                    settings = settings.copy(extra = database.syncStateDao().backupSettingsExtras()
                        ?.let { json.parseToJsonElement(it).jsonObject } ?: NO_EXTRAS),
                    extra = database.syncStateDao().backupExtras()?.let { json.parseToJsonElement(it).jsonObject } ?: NO_EXTRAS,
                )
            }

            val bytes = json.encodeToString(Backup.serializer(), backup).toByteArray()
            // Refuse an export the bounded importer could not read back.
            if (bytes.size > MAX_DOCUMENT_BYTES || photoFiles.size + 1 > MAX_ENTRIES ||
                photoFiles.any { it.length() > MAX_PHOTO_BYTES } ||
                bytes.size.toLong() + photoFiles.sumOf { it.length() } > MAX_ARCHIVE_BYTES
            ) throw ImportTooLargeException()
            // "wt", not the default "w". Several document providers do not
            // truncate on plain write, so overwriting a longer backup with a
            // shorter one left the old tail behind and produced a file that no
            // longer parsed — a corrupt backup being much worse than none.
            val written = context.contentResolver.openOutputStream(destination, "wt")
                ?.use { out ->
                    if (photoFiles.isEmpty()) {
                        out.write(bytes)
                        bytes.size.toLong()
                    } else {
                        // Photographs are full camera JPEGs, several megabytes
                        // each. Inside the JSON they would have to be base64,
                        // which costs a third again and builds the whole file
                        // in memory. The archive streams them one at a time.
                        var total = bytes.size.toLong()
                        ZipOutputStream(out.buffered()).use { zip ->
                            zip.putNextEntry(ZipEntry(DOCUMENT_ENTRY))
                            zip.write(bytes)
                            zip.closeEntry()
                            photoFiles.forEach { file ->
                                zip.putNextEntry(ZipEntry(PHOTO_PREFIX + file.name))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                                total += file.length()
                            }
                        }
                        total
                    }
                } ?: error("could not open the file for writing")

            BackupResult.Exported(written)
        }.getOrElse {
            if (it is CancellationException) throw it
            BackupResult.Failed(it.message ?: "unknown error")
        }
    }

    /** Reads a file without changing anything, so a restore can be confirmed first. */
    suspend fun peek(source: Uri): Result<Backup> = withContext(ioDispatcher) {
        runCatchingCancellable {
            val text = context.contentResolver.openInputStream(source)?.buffered()?.use { stream ->
                stream.mark(4)
                val signature = ByteArray(4)
                val length = stream.read(signature)
                stream.reset()
                if (length == 4 && isArchive(signature)) {
                    var document: ByteArray? = null
                    readArchive(stream) { name, bytes ->
                        if (name == DOCUMENT_ENTRY) {
                            check(document == null) { "Duplicate backup document" }
                            document = bytes
                        }
                    }
                    (document ?: error("Missing backup document")).decodeToString()
                } else {
                    stream.readBounded(MAX_DOCUMENT_BYTES).decodeToString()
                }
            } ?: error("could not open the file")

            // A newer format still reads: the convention shared with the web
            // is that unknown keys are kept and written back, not refused, so
            // a phone one release behind round-trips a newer file whole.
            json.decodeFromString(Backup.serializer(), text)
        }.onFailure { if (it is CancellationException) throw it }
    }

    /**
     * Replaces the player's data with the contents of a backup.
     *
     * A restore replaces rather than merges. Merging two histories means
     * deciding what to do about a deck edited on both sides, and there is no
     * answer to that which is not a guess — replacing is at least a thing the
     * player can predict. The confirmation says so before this runs.
     *
     * In one transaction, so a failure halfway leaves the previous data intact
     * rather than half of each.
     */
    suspend fun restore(
        backup: Backup,
        source: Uri? = null,
    ): BackupResult = withContext(ioDispatcher) {
        sessions.dataLock.withLock {
            runCatchingCancellable {
                // Validate the archive before replacing any database rows.
                if (source != null && backup.photos.isNotEmpty()) {
                    peek(source).getOrThrow()
                }
                sessions.endBatch()
                database.withTransaction {
                    database.syncStateDao().storeBackupExtras(BackupMetadataEntity(
                        extras = backup.extra.toString(),
                        settingsExtras = (backup.settings?.extra ?: NO_EXTRAS).toString(),
                    ))
                    database.playDao().deleteAll()
                    database.campaignDao().deleteAllRuns()
                    database.savedDeckDao().deleteAll()
                    database.ownedPackDao().clear()
                    database.excludedModularSetDao().clear()
                    database.excludedScenarioDao().clear()
                    database.randomizerHistoryDao().clear()
                    database.favouriteDao().deleteAll()
                    database.favouritePlayDao().deleteAll()
                    database.ratingDao().deleteAll()
                    database.deckFolderDao().deleteAll()
                    // The revisions described rows that are no longer here, and
                    // every restored row is new to a server until it is pushed.
                    database.syncStateDao().clear()

                    database.ownedPackDao().upsertAll(backup.ownedPacks)
                    database.excludedModularSetDao().excludeAll(backup.excludedModularSets)
                    database.excludedScenarioDao().excludeAll(backup.excludedScenarios)
                    database.savedDeckDao().upsertAll(backup.decks)
                    backup.campaignRuns.forEach { database.campaignDao().insertRun(it) }
                    // After the runs: an event references its run, and the foreign
                    // key would reject it the other way round.
                    database.campaignDao().appendEvents(backup.campaignEvents)
                    backup.plays.forEach { database.playDao().insert(it) }
                    database.randomizerHistoryDao().insertAll(backup.randomizerHistory)
                    database.favouriteDao().addAll(backup.favouriteCards)
                    database.favouritePlayDao().addAll(backup.favouritePlays)
                    database.ratingDao().putAll(backup.ratings.map { it.toEntity() })
                    database.deckFolderDao().upsertAll(backup.deckFolders)
                }
                // Outside the transaction because the settings are a DataStore
                // rather than a table, so they cannot be rolled back with it. After
                // it, so a database restore that fails leaves the device's own
                // settings alone. Null means the file predates settings being
                // included, and there is nothing to put back.
                var incomplete = false
                try {
                    backup.settings?.let { preferences.restore(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    incomplete = true
                }
                val photos = try {
                    if (backup.photos.isEmpty() || source == null) 0 else restorePhotos(backup, source)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    incomplete = true
                    0
                }
                BackupResult.Restored(
                    backup.summary().copy(photos = photos),
                    incomplete = incomplete || photos < backup.photos.size,
                )
            }.getOrElse {
                if (it is CancellationException) throw it
                BackupResult.Failed(it.message ?: "unknown error")
            }
        }
    }

    /**
     * Puts the photographs back beside the plays that name them.
     *
     * Returns how many actually landed. An entry the document does not name,
     * or one whose name is not a plain photograph name, is skipped rather
     * than trusted: this file has been outside the app and may have been
     * edited by anything.
     */
    private suspend fun restorePhotos(backup: Backup, source: Uri): Int {
        val wanted = backup.photos.toSet()
        var restored = 0
        context.contentResolver.openInputStream(source)?.use { stream ->
            readArchive(stream) { entryName, bytes ->
                val name = entryName.removePrefix(PHOTO_PREFIX)
                if (entryName.startsWith(PHOTO_PREFIX) && name in wanted && photoStore.write(name, bytes)) {
                    restored++
                }
            }
        } ?: error("could not open the photo archive")
        return restored
    }

    /** The four bytes every zip begins with. */
    private fun isArchive(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private suspend fun readArchive(
        stream: java.io.InputStream,
        consume: suspend (String, ByteArray) -> Unit,
    ) {
        var total = 0L
        val names = mutableSetOf<String>()
        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (names.size >= MAX_ENTRIES) throw ImportTooLargeException()
                check(names.add(entry.name)) { "Duplicate archive entry" }
                val limit = if (entry.name == DOCUMENT_ENTRY) MAX_DOCUMENT_BYTES else MAX_PHOTO_BYTES
                val bytes = zip.readBounded(minOf(limit.toLong(), MAX_ARCHIVE_BYTES - total).toInt())
                total += bytes.size
                if (!entry.isDirectory) consume(entry.name, bytes)
                zip.closeEntry()
            }
        }
    }

    /** A filename that sorts by date and says what it is. */
    fun suggestedFileName(withPhotos: Boolean = false): String {
        val stamp = DATE.format(java.util.Date())
        val suffix = if (withPhotos) "zip" else "json"
        return "marvel-champions-companion-backup-$stamp.$suffix"
    }

    private fun appVersion(): String = runCatchingCancellable {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    companion object {
        internal const val MAX_DOCUMENT_BYTES = 16 * 1024 * 1024
        internal const val MAX_PHOTO_BYTES = 20 * 1024 * 1024
        internal const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        internal const val MAX_ENTRIES = 4096
        private val DATE = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

        /**
         * Indented on purpose. A backup is read by people at least as often
         * as by the app, usually when something has already gone wrong.
         * Shared with the tests, so a fixture is read exactly as a file is.
         */
        val DOCUMENT_JSON: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** The document, under the same name whether or not it is alone. */
        internal const val DOCUMENT_ENTRY = "backup.json"

        /** Photographs live in their own folder, so the archive reads clearly. */
        internal const val PHOTO_PREFIX = "photos/"
    }
}
