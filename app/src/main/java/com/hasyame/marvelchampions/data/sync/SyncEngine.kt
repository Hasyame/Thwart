package com.hasyame.marvelchampions.data.sync

import androidx.room.withTransaction
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.dao.SyncStateDao
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** What a sync did, for the screen to report. */
data class SyncOutcome(
    val pulled: Int = 0,
    val pushed: Int = 0,
    /** Records this device had edited that the account had newer versions of. */
    val overwrittenOnServer: Int = 0,
    /** Decks that were kept twice rather than one version being discarded. */
    val forkedDecks: List<String> = emptyList(),
    /** True when the whole account had to be re-read from revision zero. */
    val fullResync: Boolean = false,
    /**
     * True when the server would not serve the rest of the account.
     *
     * Reachable, and not the client's fault. A full resync pages with
     * `since = <the last revision of the previous page>`, and the server
     * refuses any `since` below its tombstone horizon — which live records
     * written before the last swept tombstone sit under. An account holding
     * more than one page of those cannot be resynchronised at all.
     *
     * There is no safe way round it here: the records between the page
     * boundary and the horizon are ones this device has never read, so
     * stepping over them would lose them. The client stops, keeps what it
     * applied, and says so. The fix belongs on the server, which is the only
     * side that can tell "resuming from an old cursor" from "paging through a
     * resync that started at zero".
     */
    val incomplete: Boolean = false,
    val cursor: Long = 0,
)

/**
 * Pull, merge, push.
 *
 * Four properties here are the ones the protocol will not forgive being got
 * casually, and each is implemented in one place so it can be read:
 *
 * **Batches go up in order, one at a time, and never in parallel.** Two batches
 * in flight can interleave two edits of the same record and land them in an
 * order this device did not intend. [runLock] makes that impossible even if
 * two callers ask to sync at once.
 *
 * **A failed batch stops the run and keeps its `batchId`.** The retry carries
 * the same id, so a batch that in fact succeeded and whose response was lost
 * returns the stored result rather than applying every row a second time. That
 * — request arrived, reply did not — is the commonest real failure on a phone,
 * and without the id it silently duplicates writes.
 *
 * **Records stay dirty until an explicit success.** Re-uploading a row the
 * server already has is a no-op, because it is keyed by id. Assuming a write
 * landed is not.
 *
 * **A cursor below the tombstone horizon is a full resync, and a full resync is
 * a merge.** It is emphatically not "delete local and download": this device
 * may hold six months of plays that never reached the server. It runs the same
 * reconciliation as a first sign-in, which is the only way the rare path is
 * ever exercised.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val client: SyncClient,
    private val codec: SyncRecordCodec,
    private val sessions: SyncSessionStore,
    private val syncState: SyncStateDao,
    private val database: MarvelChampionsDatabase,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * One sync at a time, per process.
     *
     * Not only for the push ordering: two concurrent runs would also both read
     * the dirty set, both upload it, and both report the result.
     */
    private val runLock = Mutex()

    /**
     * Everything this device has that the account has not been told about.
     *
     * "Dirty" is two things, and the second is easy to forget: a record marked
     * dirty in the bookkeeping, **and** a row with no bookkeeping at all. The
     * second is every row written before sync was ever switched on, which on a
     * first upload is all of them.
     */
    suspend fun pendingCount(): Int = withContext(ioDispatcher) { pending().size }

    private suspend fun pending(): List<LocalRecord> {
        val state = syncState.all().associateBy { it.collection to it.rowId }
        return SyncCollection.entries.flatMap { collection ->
            codec.readAll(collection).filter { record ->
                val known = state[collection.key to record.id]
                known == null || known.dirty
            }
        }
    }

    /**
     * The ordinary run: read what the account has, then send what it has not.
     *
     * Pull first on purpose. Pushing first would upload a record and then
     * immediately pull the version it just replaced, and the merge would have
     * to unpick which of the two was its own.
     */
    suspend fun sync(): SyncOutcome = runLock.withLock {
        withContext(ioDispatcher) {
            val session = sessions.current()
            if (!session.isSignedIn) {
                throw SyncException(SyncException.UNAUTHORIZED)
            }
            val limits = runCatching { client.version().limits }.getOrDefault(LimitsDto())

            val pull = try {
                pullFrom(session.cursor, limits, firstMerge = session.cursor == 0L)
            } catch (tooOld: SyncException) {
                if (tooOld.code != SyncException.CURSOR_TOO_OLD) {
                    throw tooOld
                }
                // Away longer than the server can account for. Start again from
                // nothing, as a merge: the rows this device holds and the
                // server has never seen must survive it.
                sessions.resetCursor()
                pullFrom(0, limits, firstMerge = true).copy(fullResync = true)
            }

            val pushed = pushPending(limits)
            val now = System.currentTimeMillis()
            sessions.recordSync(now)
            SyncOutcome(
                pulled = pull.pulled,
                pushed = pushed.pushed,
                overwrittenOnServer = pushed.overwrittenOnServer,
                forkedDecks = pull.forkedDecks,
                fullResync = pull.fullResync,
                incomplete = pull.incomplete,
                cursor = pull.cursor,
            )
        }
    }

    // --- pull ---------------------------------------------------------------

    /**
     * Reads pages until the account is exhausted, applying each as it arrives.
     *
     * The cursor advances only over records that were actually applied. A
     * campaign event whose run has not arrived yet is held back rather than
     * dropped, and if it is still an orphan when the pages run out, the cursor
     * stops short of it so the next sync sees it again. Advancing past a record
     * this device could not apply is how a row goes missing forever.
     */
    private suspend fun pullFrom(
        since: Long,
        limits: LimitsDto,
        firstMerge: Boolean,
    ): SyncOutcome {
        var cursor = since
        var applied = 0
        val forks = mutableListOf<String>()
        var deferred = listOf<SyncRecordDto>()

        while (true) {
            val page = try {
                client.pull(cursor, limits.pageSize)
            } catch (refused: SyncException) {
                // Already reading from zero and still refused: this is the
                // paging gap above, not a stale cursor. Stop with what has been
                // applied rather than skipping what could not be read.
                if (since == 0L && refused.code == SyncException.CURSOR_TOO_OLD) {
                    return SyncOutcome(
                        pulled = applied,
                        forkedDecks = forks,
                        incomplete = true,
                        cursor = cursor,
                    )
                }
                throw refused
            }
            if (page.changes.isEmpty()) {
                cursor = maxOf(cursor, page.cursor)
                break
            }
            val result = applyPage(page.changes, firstMerge)
            applied += result.applied
            forks += result.forks
            deferred = deferred + result.deferred
            // Short of the first record that could not be applied, so the next
            // run sees it again.
            cursor = result.highWaterMark ?: cursor
            sessions.setCursor(cursor)
            if (!page.hasMore) {
                break
            }
        }

        // A second attempt once every page has landed: a run that arrived in a
        // later page than its events makes an orphan into an ordinary event.
        if (deferred.isNotEmpty()) {
            val retry = applyPage(deferred, firstMerge)
            applied += retry.applied
            forks += retry.forks
            if (retry.deferred.isEmpty()) {
                cursor = maxOf(cursor, deferred.maxOf { it.revision })
                sessions.setCursor(cursor)
            }
        }

        return SyncOutcome(pulled = applied, forkedDecks = forks, cursor = cursor)
    }

    private data class PageResult(
        val applied: Int,
        val forks: List<String>,
        val deferred: List<SyncRecordDto>,
        /** The highest revision safely behind us, or null if the first failed. */
        val highWaterMark: Long?,
    )

    /**
     * Applies one page in a single transaction.
     *
     * Runs before events so a run is on the table before anything hangs off it,
     * which is most of what makes the deferred list rare rather than routine.
     */
    private suspend fun applyPage(page: List<SyncRecordDto>, firstMerge: Boolean): PageResult {
        val ordered = page.sortedBy { if (it.collection == SyncCollection.CAMPAIGN_EVENTS.key) 1 else 0 }
        var applied = 0
        val forks = mutableListOf<String>()
        val deferred = mutableListOf<SyncRecordDto>()
        var highWater: Long? = null

        database.withTransaction {
            for (record in ordered) {
                val collection = SyncCollection.byKey(record.collection)
                if (collection == null) {
                    // A collection a later release added. Left alone rather
                    // than guessed at, and the cursor does not pass it.
                    deferred += record
                    continue
                }
                val known = syncState.get(collection.key, record.id)
                val dirty = known == null || known.dirty
                val outcome = codec.apply(
                    incoming = record,
                    // A record this device has never pushed is not "dirty" in
                    // the sense that matters here unless it actually exists
                    // locally. One the account has and this phone does not is
                    // simply new.
                    localIsDirty = dirty && codec.read(collection, record.id) != null,
                    firstMerge = firstMerge,
                    newDeckId = { "local-${UUID.randomUUID()}" },
                    forkSuffix = FORK_SUFFIX,
                )
                when (outcome) {
                    is ApplyResult.Applied -> {
                        syncState.markSynced(collection.key, record.id, record.revision)
                        applied++
                    }

                    is ApplyResult.KeptLocal -> {
                        // Kept, and still to be pushed. Recording the revision
                        // is what lets that push report the overwrite instead
                        // of making it silently.
                        syncState.noteServerRevision(collection.key, record.id, record.revision)
                        applied++
                    }

                    is ApplyResult.Forked -> {
                        syncState.markSynced(collection.key, record.id, record.revision)
                        syncState.markDirty(collection.key, outcome.newId)
                        forks += outcome.newId
                        applied++
                    }

                    is ApplyResult.Deferred, is ApplyResult.Unknown -> {
                        deferred += record
                        continue
                    }
                }
                if (deferred.isEmpty()) {
                    highWater = record.revision
                }
            }
        }
        return PageResult(applied, forks, deferred, highWater)
    }

    // --- push ---------------------------------------------------------------

    /**
     * Sends everything outstanding, in batches, stopping at the first failure.
     *
     * The batch id is generated once per batch and reused on every retry of
     * that batch, which is the whole of the idempotency story. Nothing is
     * marked clean until the server has said so for that record by name.
     */
    private suspend fun pushPending(limits: LimitsDto): SyncOutcome {
        val outstanding = pending()
        if (outstanding.isEmpty()) {
            return SyncOutcome()
        }
        var pushed = 0
        var overwritten = 0
        var cursor = 0L
        val assigned = mutableListOf<Long>()
        val startedAt = sessions.current().cursor
        // Reused for the first batch when a previous run sent one and never
        // heard back. After that, a fresh id per batch.
        var retryId = sessions.current().inFlightBatchId

        for (batch in outstanding.batched(limits)) {
            val sent = batch.associateBy { it.collection.key to it.id }
            val records = batch.map { record ->
                val known = syncState.get(record.collection.key, record.id)
                PushRecordDto(
                    collection = record.collection.key,
                    id = record.id,
                    updatedAt = record.updatedAt.toRfc3339(),
                    deleted = record.deleted,
                    // Absent when the server has never seen it. Zero would be a
                    // claim about a revision that does not exist.
                    baseRevision = known?.serverRevision?.takeIf { it > 0 },
                    body = record.body,
                )
            }
            val batchId = retryId.ifBlank { UUID.randomUUID().toString() }
            retryId = ""
            // Written down *before* the request, because the failure this
            // guards against is one where the request arrives and the answer
            // does not, and an id remembered only on success is no id at all.
            sessions.beginBatch(batchId)
            val response = client.push(batchId, records)
            sessions.endBatch()

            cursor = maxOf(cursor, response.cursor)
            database.withTransaction {
                for (result in response.results) {
                    if (result.outcome == RecordResultDto.OUTCOME_OVER_CONFLICT) {
                        overwritten++
                    }
                    assigned += result.revision
                    val key = result.collection to result.id
                    val collection = SyncCollection.byKey(result.collection)
                    val current = collection?.let { codec.read(it, result.id) }
                    // Only clean if the row still says what was sent. A person
                    // editing a play while it was in the air would otherwise
                    // have that edit marked as synced and never sent: the row
                    // is not dirty any more and nothing will look at it again
                    // until it changes a second time.
                    if (collection != null && current?.body != sent[key]?.body) {
                        syncState.noteServerRevision(result.collection, result.id, result.revision)
                    } else {
                        syncState.markSynced(result.collection, result.id, result.revision)
                    }
                }
            }
            pushed += response.results.size
        }
        advanceCursorPast(assigned, startedAt)
        return SyncOutcome(pushed = pushed, overwrittenOnServer = overwritten, cursor = cursor)
    }

    /**
     * Moves the cursor past the revisions this device was just given.
     *
     * Without it, everything uploaded comes straight back on the next pull: the
     * push creates revisions above the cursor, and the cursor has no way of
     * knowing they are its own. Harmless — they are the same records — but on a
     * real account it re-downloads and rewrites the lot every time anything is
     * sent.
     *
     * Only when the new revisions are **contiguous** from where the cursor
     * stood. A gap means another device wrote while this one was pushing, and
     * stepping over that gap would lose its records permanently. Leaving the
     * cursor alone costs one wasted re-read; stepping over it costs somebody
     * their data.
     */
    private suspend fun advanceCursorPast(assigned: List<Long>, from: Long) {
        val fresh = assigned.filter { it > from }.sorted().distinct()
        if (fresh.isEmpty()) {
            return
        }
        val contiguous = fresh == (from + 1..fresh.last()).toList()
        if (contiguous) {
            sessions.setCursor(fresh.last())
        }
    }

    /**
     * Splits records into batches the server will accept.
     *
     * Both limits are the server's published ones rather than constants here:
     * count, and encoded size. The size is measured on the encoded record
     * because that is what the server counts, and a hundred plays with long
     * notes reach two megabytes long before they reach five hundred rows.
     */
    private fun List<LocalRecord>.batched(limits: LimitsDto): List<List<LocalRecord>> {
        val batches = mutableListOf<List<LocalRecord>>()
        var current = mutableListOf<LocalRecord>()
        var bytes = 0L
        for (record in this) {
            val size = record.body?.let { json.encodeToString(it).toByteArray().size.toLong() } ?: 0L
            val full = current.size >= limits.batchRecords ||
                (current.isNotEmpty() && bytes + size > limits.batchBytes)
            if (full) {
                batches += current
                current = mutableListOf()
                bytes = 0
            }
            current += record
            bytes += size
        }
        if (current.isNotEmpty()) {
            batches += current
        }
        return batches
    }

    // --- adopting an account ------------------------------------------------

    /**
     * Reads the whole account into memory and says what merging would mean.
     *
     * **Writes nothing.** That is the point: the person is about to be asked a
     * question whose wrong answer costs them years of play history, and a
     * question asked after the fact is not a question. The records come back
     * inside the plan so that answering yes applies exactly what was counted,
     * rather than pulling a second time and merging something subtly different.
     */
    suspend fun planAdoption(): AdoptionPlan = runLock.withLock {
        withContext(ioDispatcher) {
            val limits = runCatching { client.version().limits }.getOrDefault(LimitsDto())
            val records = mutableListOf<SyncRecordDto>()
            var cursor = 0L
            while (true) {
                val page = client.pull(cursor, limits.pageSize)
                records += page.changes
                cursor = maxOf(cursor, page.cursor)
                if (!page.hasMore || page.changes.isEmpty()) {
                    break
                }
            }

            val live = records.filterNot { it.deleted }
            val serverIds = live.groupBy({ it.collection }, { it.id })
                .mapValues { (_, ids) -> ids.toSet() }
            val localOnly = SyncCollection.entries.associateWith { collection ->
                if (collection == SyncCollection.SETTINGS) {
                    0
                } else {
                    codec.readAll(collection)
                        .filterNot { it.deleted }
                        .count { it.id !in serverIds[collection.key].orEmpty() }
                }
            }
            AdoptionPlan(
                counts = AdoptionCounts(
                    server = SyncCollection.entries.associateWith {
                        serverIds[it.key].orEmpty().size
                    },
                    localOnly = localOnly,
                ),
                records = records,
                cursor = cursor,
            )
        }
    }

    /**
     * Takes both sides.
     *
     * Everything the account holds is written locally; everything this phone
     * holds that the account does not is left dirty — by having no bookkeeping
     * row at all, which is what "never uploaded" looks like — and uploaded
     * immediately afterwards. Nothing is discarded on either side.
     *
     * The inclusive rules are on: `max(quantity)` for packs, the earlier date
     * for a favourite, union for exclusions and for the campaign log. On a
     * first merge there is no history to adjudicate with, so the safe direction
     * is keeping data. A wrongly kept favourite is one tap to remove; a wrongly
     * dropped campaign is gone.
     */
    suspend fun adoptMerging(plan: AdoptionPlan): SyncOutcome = runLock.withLock {
        withContext(ioDispatcher) {
            val limits = runCatching { client.version().limits }.getOrDefault(LimitsDto())
            val applied = applyPage(plan.records, firstMerge = true)
            // Orphan events get their second chance the same way an ordinary
            // pull gives them one, now that every run in the account has landed.
            val retry = if (applied.deferred.isEmpty()) {
                null
            } else {
                applyPage(applied.deferred, firstMerge = true)
            }
            if (applied.deferred.isEmpty() || retry?.deferred?.isEmpty() == true) {
                sessions.setCursor(plan.cursor)
            }
            val pushed = pushPending(limits)
            sessions.recordSync(System.currentTimeMillis())
            SyncOutcome(
                pulled = applied.applied + (retry?.applied ?: 0),
                pushed = pushed.pushed,
                overwrittenOnServer = pushed.overwrittenOnServer,
                forkedDecks = applied.forks + retry?.forks.orEmpty(),
                cursor = plan.cursor,
            )
        }
    }

    /**
     * Throws this device's data away and takes the account's.
     *
     * The destructive answer, and it is only reachable once the local data has
     * been written to a backup file — [exported] is the caller's word that it
     * has, and this refuses without it. Even the answer that discards is
     * recoverable, which is the difference between a feature people trust and
     * one they find out about afterwards.
     */
    suspend fun adoptKeepingServerOnly(
        plan: AdoptionPlan,
        exported: Boolean,
    ): SyncOutcome = runLock.withLock {
        require(exported) { "local data must be exported before it is replaced" }
        withContext(ioDispatcher) {
            database.withTransaction {
                database.playDao().deleteAll()
                database.campaignDao().deleteAllRuns()
                database.savedDeckDao().deleteAll()
                database.ownedPackDao().clear()
                database.excludedModularSetDao().clear()
                database.excludedScenarioDao().clear()
                database.randomizerHistoryDao().clear()
                database.favouriteDao().deleteAll()
                // The revisions described rows that are no longer here.
                syncState.clear()
            }
            val applied = applyPage(plan.records, firstMerge = false)
            val retry = if (applied.deferred.isEmpty()) {
                null
            } else {
                applyPage(applied.deferred, firstMerge = false)
            }
            sessions.setCursor(plan.cursor)
            sessions.recordSync(System.currentTimeMillis())
            SyncOutcome(
                pulled = applied.applied + (retry?.applied ?: 0),
                cursor = plan.cursor,
            )
        }
    }

    // --- housekeeping -------------------------------------------------------

    /**
     * Marks every local row as needing to be sent.
     *
     * For adopting an account: the rows this device holds are new to it, and
     * "never uploaded" is a fact the bookkeeping can state rather than infer.
     */
    suspend fun markEverythingDirty() = withContext(ioDispatcher) {
        database.withTransaction {
            for (collection in SyncCollection.entries) {
                for (record in codec.readAll(collection)) {
                    syncState.markDirty(collection.key, record.id)
                }
            }
        }
    }

    /** The bookkeeping only. Local data is not touched. */
    suspend fun forgetSyncState() = withContext(ioDispatcher) {
        syncState.clear()
    }

    private companion object {
        /**
         * Appended to the name of a deck that was kept alongside the account's
         * version of it. Not translated: it becomes part of a stored deck name
         * and would otherwise change meaning if the app's language changed.
         */
        const val FORK_SUFFIX = "(2)"
    }
}
