package com.hasyame.marvelchampions.data.sync

import com.hasyame.marvelchampions.data.backup.BackupSettings
import com.hasyame.marvelchampions.data.db.dao.SyncRecordDao
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedScenarioEntity
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.settings.AppPreferences
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * One record of local data, in the shape the protocol wants it.
 *
 * [updatedAt] is epoch milliseconds here and RFC 3339 on the wire; the
 * conversion happens once, at the edge, so nothing above works in two units.
 */
data class LocalRecord(
    val collection: SyncCollection,
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean,
    /** Null exactly when [deleted]. */
    val body: JsonObject?,
)

/** What applying a pulled record did, so the caller can keep its books. */
sealed interface ApplyResult {
    /** Written. */
    data object Applied : ApplyResult

    /**
     * The local version was kept because it has edits that have not been
     * pushed yet. The revision is still recorded, so the push that follows can
     * say what it is overwriting.
     */
    data object KeptLocal : ApplyResult

    /** Both versions kept, the local one re-keyed to [newId] and now dirty. */
    data class Forked(val newId: String) : ApplyResult

    /**
     * Held back: a campaign event whose run has not arrived yet. Applying it
     * would fail the foreign key and take the whole page with it.
     */
    data object Deferred : ApplyResult

    /** A collection this build does not know. Left alone rather than guessed at. */
    data object Unknown : ApplyResult
}

/**
 * Turns rows into records and records back into rows.
 *
 * The body of a record is the entity, serialised exactly as the backup file
 * serialises it. That is not a coincidence and not laziness: the server maps
 * every collection to a field of the app's own `Backup` document so that
 * `GET /account/export` produces a file this app can already import. A body
 * that drifted from the entity would export into a backup that no longer
 * restores.
 *
 * Two things are deliberately not in a body:
 *
 * - **A campaign run's three timer columns.** A running clock is a fact about
 *   the device somebody is holding, not about the campaign.
 * - **Anything from `sync_state`.** The dirty flag and the revision are this
 *   device's bookkeeping and mean nothing on another one.
 */
@Singleton
class SyncRecordCodec @Inject constructor(
    private val dao: SyncRecordDao,
    private val preferences: AppPreferences,
    private val json: Json,
) {

    // --- reading ------------------------------------------------------------

    /** One local row as a record, or null when there is no such row at all. */
    suspend fun read(collection: SyncCollection, id: String): LocalRecord? = when (collection) {
        SyncCollection.OWNED_PACKS -> dao.ownedPack(id)?.let {
            record(collection, it.packCode, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.EXCLUDED_MODULAR_SETS -> dao.excludedModularSet(id)?.let {
            record(collection, it.setCode, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.EXCLUDED_SCENARIOS -> dao.excludedScenario(id)?.let {
            record(collection, it.scenarioCode, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.FAVOURITE_CARDS -> dao.favourite(id)?.let {
            record(collection, it.cardCode, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.SAVED_DECKS -> dao.deck(id)?.let {
            record(collection, it.id, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.CAMPAIGN_RUNS -> dao.campaignRun(id)?.let {
            record(collection, it.id, it.updatedAt, it.deletedAt) { encodeRun(it) }
        }

        SyncCollection.CAMPAIGN_EVENTS -> dao.campaignEvent(id)?.let {
            // No tombstone column at all: the log is append-only, and an event
            // is hidden by its run's tombstone rather than by one of its own.
            record(collection, it.id, it.timestamp, null) { encode(it) }
        }

        SyncCollection.PLAYS -> dao.play(id)?.let {
            record(collection, it.id, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.RANDOMIZER_HISTORY -> dao.draw(id)?.let {
            record(collection, it.id, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.SETTINGS -> LocalRecord(
            collection = collection,
            id = SyncCollection.SETTINGS_ID,
            // The preferences carry no timestamp of their own, so the record is
            // stamped when it is read. Settings change rarely and are one
            // record; there is nothing here for a stale stamp to lose.
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            body = encode(preferences.snapshot()),
        )
    }

    /** Every local row of a collection, tombstones included. */
    suspend fun readAll(collection: SyncCollection): List<LocalRecord> = when (collection) {
        SyncCollection.OWNED_PACKS -> dao.ownedPacks().map {
            record(collection, it.packCode, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.EXCLUDED_MODULAR_SETS -> dao.excludedModularSets().map {
            record(collection, it.setCode, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.EXCLUDED_SCENARIOS -> dao.excludedScenarios().map {
            record(collection, it.scenarioCode, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.FAVOURITE_CARDS -> dao.favourites().map {
            record(collection, it.cardCode, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.SAVED_DECKS -> dao.decks().map {
            record(collection, it.id, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.CAMPAIGN_RUNS -> dao.campaignRuns().map {
            record(collection, it.id, it.updatedAt, it.deletedAt) { encodeRun(it) }
        }

        SyncCollection.CAMPAIGN_EVENTS -> dao.campaignEvents().map {
            record(collection, it.id, it.timestamp, null) { encode(it) }
        }

        SyncCollection.PLAYS -> dao.plays().map {
            record(collection, it.id, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.RANDOMIZER_HISTORY -> dao.draws().map {
            record(collection, it.id, it.updatedAt, it.deletedAt) { encode(it) }
        }

        SyncCollection.SETTINGS -> listOfNotNull(read(collection, SyncCollection.SETTINGS_ID))
    }

    // --- applying -----------------------------------------------------------

    /**
     * Writes a pulled record into the local tables.
     *
     * [localIsDirty] is the whole of the conflict policy on this side. A record
     * this device has edited and not yet pushed is **not** overwritten: the
     * local version is kept and pushed afterwards, where the server's own
     * last-write-wins makes it the winner because it arrived last. Overwriting
     * it here would discard an edit that nobody ever saw.
     *
     * [firstMerge] switches the inclusive rules on — the ones for a first sign
     * in and a full resync, where there is no history to adjudicate with and
     * keeping data is the safe direction.
     */
    suspend fun apply(
        incoming: SyncRecordDto,
        localIsDirty: Boolean,
        firstMerge: Boolean,
        newDeckId: () -> String,
        forkSuffix: String,
    ): ApplyResult {
        val collection = SyncCollection.byKey(incoming.collection) ?: return ApplyResult.Unknown
        val deletedAt = if (incoming.deleted) incoming.updatedAt.toEpochMillis() else null

        // Append-only, so an id that is already here is the same event by
        // definition and there is nothing to decide.
        if (collection == SyncCollection.CAMPAIGN_EVENTS) {
            val event = incoming.body?.let { decode<CampaignEventEntity>(it) } ?: return ApplyResult.Unknown
            if (dao.campaignEvent(event.id) != null) {
                return ApplyResult.Applied
            }
            if (!dao.hasCampaignRun(event.runId)) {
                return ApplyResult.Deferred
            }
            dao.putCampaignEvent(event)
            return ApplyResult.Applied
        }

        if (localIsDirty) {
            // One exception, and it is the reason the fork rule exists: two
            // devices that have both edited the same imported deck keep both,
            // rather than one of them keeping only its own.
            if (collection == SyncCollection.SAVED_DECKS && !incoming.deleted) {
                val body = incoming.body ?: return ApplyResult.KeptLocal
                val remote = decode<SavedDeckEntity>(body)
                val local = dao.deck(incoming.id)
                if (SyncMerge.deckForks(remote, local) && local != null) {
                    val forkId = newDeckId()
                    dao.putDeck(SyncMerge.forkedDeck(local, forkId, forkSuffix))
                    dao.putDeck(remote)
                    return ApplyResult.Forked(forkId)
                }
            }
            return ApplyResult.KeptLocal
        }

        when (collection) {
            SyncCollection.OWNED_PACKS -> {
                val local = dao.ownedPack(incoming.id)
                val remote = incoming.body?.let { decode<OwnedPackEntity>(it) }
                    ?: local?.copy(deletedAt = deletedAt)
                    ?: OwnedPackEntity(incoming.id, 0, incoming.updatedAt.toEpochMillis(), deletedAt)
                val merged = if (firstMerge) {
                    SyncMerge.ownedPackOnFirstMerge(remote.stamped(incoming, deletedAt), local)
                } else {
                    remote.stamped(incoming, deletedAt)
                }
                dao.putOwnedPack(merged)
            }

            SyncCollection.EXCLUDED_MODULAR_SETS -> {
                val remote = incoming.body?.let { decode<ExcludedModularSetEntity>(it) }
                    ?: ExcludedModularSetEntity(incoming.id)
                dao.putExcludedModularSet(
                    remote.copy(
                        setCode = incoming.id,
                        updatedAt = incoming.updatedAt.toEpochMillis(),
                        deletedAt = deletedAt,
                    ),
                )
            }

            SyncCollection.EXCLUDED_SCENARIOS -> {
                val remote = incoming.body?.let { decode<ExcludedScenarioEntity>(it) }
                    ?: ExcludedScenarioEntity(incoming.id)
                dao.putExcludedScenario(
                    remote.copy(
                        scenarioCode = incoming.id,
                        updatedAt = incoming.updatedAt.toEpochMillis(),
                        deletedAt = deletedAt,
                    ),
                )
            }

            SyncCollection.FAVOURITE_CARDS -> {
                val local = dao.favourite(incoming.id)
                val remote = incoming.body?.let { decode<FavouriteCardEntity>(it) }
                    ?: FavouriteCardEntity(incoming.id, local?.addedAt ?: 0)
                dao.putFavourite(
                    SyncMerge.favourite(
                        remote.copy(
                            cardCode = incoming.id,
                            updatedAt = incoming.updatedAt.toEpochMillis(),
                            deletedAt = deletedAt,
                        ),
                        local,
                    ),
                )
            }

            SyncCollection.SAVED_DECKS -> {
                val local = dao.deck(incoming.id)
                val remote = incoming.body?.let { decode<SavedDeckEntity>(it) }
                    ?: local?.copy(deletedAt = deletedAt)
                    ?: return ApplyResult.Unknown
                val stamped = remote.copy(
                    id = incoming.id,
                    updatedAt = incoming.updatedAt.toEpochMillis(),
                    deletedAt = deletedAt,
                )
                if (SyncMerge.deckForks(stamped, local) && local != null) {
                    val forkId = newDeckId()
                    dao.putDeck(SyncMerge.forkedDeck(local, forkId, forkSuffix))
                    dao.putDeck(stamped)
                    return ApplyResult.Forked(forkId)
                }
                dao.putDeck(stamped)
            }

            SyncCollection.CAMPAIGN_RUNS -> {
                val local = dao.campaignRun(incoming.id)
                val remote = incoming.body?.let { decode<CampaignRunEntity>(it) }
                    ?: local?.copy(deletedAt = deletedAt)
                    ?: return ApplyResult.Unknown
                dao.putCampaignRun(
                    SyncMerge.campaignRun(
                        remote.copy(
                            id = incoming.id,
                            updatedAt = incoming.updatedAt.toEpochMillis(),
                            deletedAt = deletedAt,
                        ),
                        local,
                    ),
                )
            }

            SyncCollection.PLAYS -> {
                val local = dao.play(incoming.id)
                val remote = incoming.body?.let { decode<PlayEntity>(it) }
                    ?: local?.copy(deletedAt = deletedAt)
                    ?: return ApplyResult.Unknown
                dao.putPlay(
                    SyncMerge.play(
                        remote.copy(
                            id = incoming.id,
                            updatedAt = incoming.updatedAt.toEpochMillis(),
                            deletedAt = deletedAt,
                        ),
                        local,
                    ),
                )
            }

            SyncCollection.RANDOMIZER_HISTORY -> {
                val local = dao.draw(incoming.id)
                val remote = incoming.body?.let { decode<RandomizerHistoryEntity>(it) }
                    ?: local?.copy(deletedAt = deletedAt)
                    ?: return ApplyResult.Unknown
                dao.putDraw(
                    remote.copy(
                        id = incoming.id,
                        updatedAt = incoming.updatedAt.toEpochMillis(),
                        deletedAt = deletedAt,
                    ),
                )
            }

            SyncCollection.SETTINGS -> {
                val remote = incoming.body?.let { decode<BackupSettings>(it) } ?: return ApplyResult.Unknown
                preferences.restore(SyncMerge.settings(remote, preferences.snapshot(), firstMerge))
            }

            SyncCollection.CAMPAIGN_EVENTS -> Unit // handled above
        }
        return ApplyResult.Applied
    }

    // --- plumbing -----------------------------------------------------------

    private fun OwnedPackEntity.stamped(incoming: SyncRecordDto, deletedAt: Long?) = copy(
        packCode = incoming.id,
        updatedAt = incoming.updatedAt.toEpochMillis(),
        deletedAt = deletedAt,
    )

    private inline fun record(
        collection: SyncCollection,
        id: String,
        updatedAt: Long,
        deletedAt: Long?,
        body: () -> JsonObject,
    ): LocalRecord = LocalRecord(
        collection = collection,
        id = id,
        // A tombstone is timed by its deletion, not by whatever the row said
        // before it: the delete is the write, and it is the write the other
        // device has to be told about.
        updatedAt = deletedAt ?: updatedAt,
        deleted = deletedAt != null,
        body = if (deletedAt != null) null else body(),
    )

    private inline fun <reified T> encode(value: T): JsonObject =
        json.encodeToJsonElement(value) as JsonObject

    private inline fun <reified T> decode(body: JsonObject): T =
        json.decodeFromJsonElement(body)

    /**
     * A campaign run without its clock.
     *
     * Dropped by name rather than by a second data class, so adding a column to
     * the entity puts it in the body automatically and only the three named
     * here stay behind. A second class would silently stop carrying anything
     * added to the first.
     */
    private fun encodeRun(run: CampaignRunEntity): JsonObject =
        JsonObject(encode(run).filterKeys { it !in TIMER_COLUMNS })

    private companion object {
        val TIMER_COLUMNS = setOf(
            "timerAccumulatedMillis",
            "timerRunningSince",
            "timerScenarioId",
        )
    }
}

/** Epoch milliseconds as the RFC 3339 the server insists on. */
fun Long.toRfc3339(): String =
    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(this))

/**
 * RFC 3339 back to epoch milliseconds, or zero.
 *
 * Zero for anything unparseable rather than an exception: the field is used to
 * display a time and to break a tie, and neither is worth failing a whole sync
 * over. A record with no usable stamp simply sorts as old.
 */
fun String.toEpochMillis(): Long = try {
    Instant.parse(this).toEpochMilli()
} catch (invalid: DateTimeParseException) {
    0
}
