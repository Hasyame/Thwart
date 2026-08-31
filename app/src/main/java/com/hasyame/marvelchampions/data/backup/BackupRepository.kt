package com.hasyame.marvelchampions.data.backup

import android.content.Context
import android.net.Uri
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.photos.PhotoStore
import com.hasyame.marvelchampions.data.settings.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes and reads a backup file.
 *
 * This app has no account and no server, so the device is the only copy of
 * everything a player has built. A lost phone loses a collection, every deck,
 * every campaign and years of play history with no recourse whatever. A file
 * the player can put somewhere else is the whole answer.
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
) {

    /**
     * Indented on purpose. A backup is read by people at least as often as by
     * the app — usually when something has already gone wrong.
     */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * @param includePhotos writes an archive carrying the table photographs
     *   too. Off by default: a backup is a document that gets handed around,
     *   and photographs of somebody's living room should not travel unasked.
     */
    suspend fun export(
        destination: Uri,
        includePhotos: Boolean = false,
    ): BackupResult = withContext(ioDispatcher) {
        runCatching {
            // Listed before the database is touched. A suspending query can
            // resume on another thread, and the photo folder has to be read
            // from the one that owns it.
            val photoFiles = if (includePhotos) photoStore.files() else emptyList()
            // Read once and reused: the events are looked up per run, and
            // fetching the run list a second time to do it was pure waste.
            val runs = database.campaignDao().getRuns()

            val backup = Backup(
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
                photos = photoFiles.map { it.name },
                settings = preferences.snapshot(),
            )

            val bytes = json.encodeToString(Backup.serializer(), backup).toByteArray()
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
        }.getOrElse { BackupResult.Failed(it.message ?: "unknown error") }
    }

    /** Reads a file without changing anything, so a restore can be confirmed first. */
    suspend fun peek(source: Uri): Result<Backup> = withContext(ioDispatcher) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use {
                it.readBytes()
            } ?: error("could not open the file")

            // A backup written with photographs is an archive with the same
            // document inside it, so both kinds open through this one door.
            val text = if (isArchive(bytes)) {
                readEntry(bytes, DOCUMENT_ENTRY)?.decodeToString()
                    ?: error("this archive does not contain " + DOCUMENT_ENTRY)
            } else {
                bytes.decodeToString()
            }

            val backup = json.decodeFromString(Backup.serializer(), text)
            if (backup.formatVersion > Backup.CURRENT_FORMAT_VERSION) {
                error(
                    "this backup was written by a newer version of the app " +
                        "(format ${backup.formatVersion})",
                )
            }
            backup
        }
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
        runCatching {
            database.withTransaction {
                database.playDao().deleteAll()
                database.campaignDao().deleteAllRuns()
                database.savedDeckDao().deleteAll()
                database.ownedPackDao().clear()
                database.excludedModularSetDao().clear()
                database.excludedScenarioDao().clear()
                database.randomizerHistoryDao().clear()
                database.favouriteDao().deleteAll()
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
            }
            // Outside the transaction because the settings are a DataStore
            // rather than a table, so they cannot be rolled back with it. After
            // it, so a database restore that fails leaves the device's own
            // settings alone. Null means the file predates settings being
            // included, and there is nothing to put back.
            backup.settings?.let { preferences.restore(it) }

            // After the transaction, and deliberately outside it: a photo
            // that will not write back is a missing picture, not a reason to
            // throw away a collection that has just been restored.
            val photos = if (backup.photos.isEmpty() || source == null) {
                0
            } else {
                restorePhotos(backup, source)
            }
            BackupResult.Restored(backup.summary().copy(photos = photos))
        }.getOrElse { BackupResult.Failed(it.message ?: "unknown error") }
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
            ZipInputStream(stream.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val name = entry.name.removePrefix(PHOTO_PREFIX)
                    val wanting = !entry.isDirectory &&
                        entry.name.startsWith(PHOTO_PREFIX) &&
                        name in wanted
                    if (wanting && photoStore.write(name, zip.readBytes())) {
                        restored++
                    }
                    zip.closeEntry()
                }
            }
        }
        return restored
    }

    /** The four bytes every zip begins with. */
    private fun isArchive(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private fun readEntry(bytes: ByteArray, name: String): ByteArray? =
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == name }
                ?.let { zip.readBytes() }
        }

    /** A filename that sorts by date and says what it is. */
    fun suggestedFileName(withPhotos: Boolean = false): String {
        val stamp = DATE.format(java.util.Date())
        val suffix = if (withPhotos) "zip" else "json"
        return "marvel-champions-companion-backup-$stamp.$suffix"
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private companion object {
        val DATE = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

        /** The document, under the same name whether or not it is alone. */
        const val DOCUMENT_ENTRY = "backup.json"

        /** Photographs live in their own folder, so the archive reads clearly. */
        const val PHOTO_PREFIX = "photos/"
    }
}
