package com.hasyame.marvelchampions.data.sync

import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * A server, in memory, that behaves the way the real one is specified to.
 *
 * Enough of it to exercise the four things the brief says will go wrong if they
 * are approached casually: the revision counter, the batch idempotency store,
 * the tombstone horizon, and a full account to adopt. Deliberately not a mock
 * with expectations — the interesting failures here are about *sequences* of
 * calls, and a fake that keeps state can be asked afterwards what it was
 * actually sent.
 *
 * It is strict where the real server is strict. A batch id it has seen before
 * returns the stored response and applies nothing, and a cursor below the
 * horizon is refused rather than half-served, because a client that is only
 * tested against a forgiving server is not tested.
 */
class FakeSyncApi : SyncApi {

    private data class Stored(
        val record: SyncRecordDto,
    )

    private val records = linkedMapOf<Pair<String, String>, Stored>()
    private val batches = mutableMapOf<String, PushResponseDto>()
    private var revision = 0L

    /** Raised to pretend the oldest tombstones have been swept away. */
    var minCursor: Long = 0

    /** Every batch id the fake was sent, in order, retries included. */
    val batchIds = mutableListOf<String>()

    /** How many batches actually applied, as opposed to replaying. */
    var applied = 0
        private set

    /**
     * Fail the nth push outright: nothing is written and nothing is stored.
     *
     * A batch that never arrived. Retrying it is ordinary work.
     */
    var failOnAttempt: Int? = null

    /**
     * Apply the nth push and then lose the answer.
     *
     * The failure that actually happens on a phone: the request landed, the
     * reply did not, and the client cannot tell this from the case above. The
     * response is stored, so a retry carrying the same batch id must replay it
     * rather than write everything a second time.
     */
    var loseResponseOnAttempt: Int? = null

    private var attempts = 0

    fun seed(collection: String, id: String, body: JsonObject?, deleted: Boolean = false) {
        revision++
        records[collection to id] = Stored(
            SyncRecordDto(
                collection = collection,
                id = id,
                revision = revision,
                updatedAt = "2026-01-01T00:00:00Z",
                deleted = deleted,
                body = body,
            ),
        )
    }

    fun stored(collection: String, id: String): SyncRecordDto? = records[collection to id]?.record

    fun count(): Int = records.size

    /** How many records the account holds in one collection. */
    fun countIn(collection: String): Int = records.keys.count { it.first == collection }

    // --- the protocol -------------------------------------------------------

    override suspend fun version(url: String): Response<VersionDto> =
        Response.success(VersionDto(registrationOpen = true, limits = limits))

    /** Small on purpose, so a handful of records is several batches and pages. */
    var limits = LimitsDto(batchRecords = 2, batchBytes = 1_000_000, pageSize = 2)

    /**
     * Set to behave like a server from before the resync flag existed, which
     * refuses a page of a rebuild the same way it refuses a stale cursor.
     */
    var ignoresResync = false

    override suspend fun pull(
        url: String,
        authorization: String,
        since: Long,
        limit: Int,
        resync: Boolean,
    ): Response<PullResponseDto> {
        val claimed = resync && !ignoresResync
        if (since > 0 && !claimed && since < minCursor) {
            return refusal(409, SyncException.CURSOR_TOO_OLD)
        }
        val ordered = records.values.map { it.record }.sortedBy { it.revision }
        val page = ordered.filter { it.revision > since }.take(limit)
        return Response.success(
            PullResponseDto(
                changes = page,
                cursor = page.lastOrNull()?.revision ?: since,
                hasMore = page.size == limit && ordered.any { it.revision > (page.lastOrNull()?.revision ?: since) },
                minCursor = minCursor,
            ),
        )
    }

    override suspend fun push(
        url: String,
        authorization: String,
        body: PushRequestDto,
    ): Response<PushResponseDto> {
        batchIds += body.batchId

        // Before anything is written, exactly as the server does it: a batch id
        // already answered replays and applies nothing.
        batches[body.batchId]?.let { return Response.success(it) }

        attempts++
        if (failOnAttempt == attempts) {
            return refusal(500, SyncException.SERVER_ERROR)
        }

        val results = body.records.map { incoming ->
            val key = incoming.collection to incoming.id
            val existing = records[key]?.record
            revision++
            records[key] = Stored(
                SyncRecordDto(
                    collection = incoming.collection,
                    id = incoming.id,
                    revision = revision,
                    updatedAt = incoming.updatedAt,
                    deleted = incoming.deleted,
                    body = incoming.body,
                ),
            )
            val superseded = existing?.revision
                ?.takeIf { stored -> incoming.baseRevision?.let { stored > it } == true }
            RecordResultDto(
                id = incoming.id,
                collection = incoming.collection,
                revision = revision,
                outcome = if (superseded == null) {
                    RecordResultDto.OUTCOME_APPLIED
                } else {
                    RecordResultDto.OUTCOME_OVER_CONFLICT
                },
                supersededRevision = superseded,
            )
        }
        applied++
        val response = PushResponseDto(cursor = revision, results = results)
        batches[body.batchId] = response
        if (loseResponseOnAttempt == attempts) {
            return refusal(500, SyncException.SERVER_ERROR)
        }
        return Response.success(response)
    }

    // --- the parts these tests do not exercise ------------------------------

    override suspend fun register(url: String, language: String, body: RegisterDto) =
        authResponse()

    override suspend fun login(url: String, language: String, body: SignInDto) = authResponse()

    override suspend fun recover(url: String, language: String, body: RecoverDto) = authResponse()

    override suspend fun changePassword(
        url: String,
        authorization: String,
        body: ChangePasswordDto,
    ): Response<Unit> = Response.success(Unit)

    override suspend fun devices(url: String, authorization: String) =
        Response.success(DevicesResponseDto())

    override suspend fun deleteDevice(url: String, authorization: String): Response<Unit> =
        Response.success(Unit)

    override suspend fun deleteAccount(
        url: String,
        authorization: String,
        body: DeleteAccountDto,
    ): Response<Unit> = Response.success(Unit)

    private fun authResponse() = Response.success(
        AuthResponseDto(accountId = "account", handle = "tester", token = "token"),
    )

    private fun <T> refusal(status: Int, code: String): Response<T> = Response.error(
        status,
        """{"error":{"code":"$code","message":"refused","details":{}}}"""
            .toResponseBody("application/json".toMediaType()),
    )
}
