package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.RatingDao
import com.hasyame.marvelchampions.data.db.dao.SyncStateDao
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.RatingEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.sync.AutoSync
import com.hasyame.marvelchampions.data.sync.RatingSummaryDto
import com.hasyame.marvelchampions.data.sync.SyncClient
import com.hasyame.marvelchampions.data.sync.SyncTrigger
import com.hasyame.marvelchampions.domain.ratings.RatingSubject
import com.hasyame.marvelchampions.domain.ratings.RatingWire
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Difficulty ratings: the player's own, and the community's.
 *
 * Two different questions, deliberately kept apart. The player's own rating
 * of a subject is a row in the local table, synced like any other record and
 * shown wherever the subject is, threshold or no threshold: their data is
 * theirs. The community's is a cached aggregate the server serves to anyone,
 * with the threshold already applied, and it is decoration on a decision,
 * never a reason a screen cannot render.
 * `docs/spec/ratings-and-modular-sets.md`, sections 2 to 4.
 */
@Singleton
class RatingRepository @Inject constructor(
    private val ratingDao: RatingDao,
    private val syncStateDao: SyncStateDao,
    private val client: SyncClient,
    private val autoSync: AutoSync,
    private val ioDispatcher: CoroutineDispatcher,
) {

    // --- the player's own -----------------------------------------------------

    /** The player's current score per subject key, for the subjects a screen shows. */
    fun observeOwn(subjects: List<String>): Flow<Map<String, Int>> =
        if (subjects.isEmpty()) {
            flowOf(emptyMap())
        } else {
            ratingDao.observe(subjects).map { rows -> rows.associate { it.subject to it.score } }
        }

    /**
     * Rates a scenario or a modular set, after the game the rating is about.
     *
     * Rating again is the same row with a newer `ratedAt`, which is also what
     * wins the merge, so the previous opinion is simply replaced. The context
     * is the play as it stands now, written once.
     */
    suspend fun rate(subject: RatingSubject, score: Int, play: PlayEntity) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        store(
            RatingWire(
                subject = subject.key,
                score = score.coerceIn(RatingWire.MIN_SCORE, RatingWire.MAX_SCORE),
                ratedAt = now,
                evidence = RatingWire.Evidence(playId = play.id),
                context = RatingWire.Context(
                    players = play.players,
                    heroes = play.roster.map { RatingWire.Hero(code = it.code, aspect = it.aspect) },
                    mode = play.difficulty,
                    standardSet = play.standardSet,
                    scenario = subject.pairedWith,
                ),
            ).toEntity(updatedAt = now),
        )
    }

    /** Rates a campaign as a whole, once its run is finished. */
    suspend fun rateCampaign(run: CampaignRunEntity, score: Int, players: Int) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        store(
            RatingWire(
                subject = RatingSubject.campaign(run.templateId).key,
                score = score.coerceIn(RatingWire.MIN_SCORE, RatingWire.MAX_SCORE),
                ratedAt = now,
                evidence = RatingWire.Evidence(runId = run.id),
                context = RatingWire.Context(
                    players = players,
                    mode = run.difficulty,
                    standardSet = run.standardSet,
                ),
            ).toEntity(updatedAt = now),
        )
    }

    /** Takes a rating back. The sync tombstones it; the server subtracts it. */
    suspend fun unrate(subject: String) = withContext(ioDispatcher) {
        ratingDao.remove(subject, System.currentTimeMillis())
        changed(subject)
    }

    private suspend fun store(rating: RatingEntity) {
        ratingDao.put(rating)
        changed(rating.subject)
    }

    private suspend fun changed(subject: String) {
        syncStateDao.markDirty(SyncCollection.RATINGS.key, subject)
        // The server answers with the subject's new summary the next time it
        // is asked; a screen that just rated must not keep showing the old.
        forget(subject)
        autoSync.after(SyncTrigger.RATED)
    }

    // --- the community's -------------------------------------------------------

    private val memo = HashMap<String, Pair<RatingSummaryDto, Long>>()
    private val memoLock = Mutex()

    /**
     * Summaries for a set of subjects, from memory where fresh, otherwise
     * fetched in one request for the rest.
     *
     * A subject that cannot be fetched, offline or a server hiccup, is simply
     * absent from the result, and every caller treats absent as "nothing to
     * show". A person with no account still gets these: the endpoint is
     * public.
     */
    suspend fun summaries(subjects: List<String>): Map<String, RatingSummaryDto> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val out = HashMap<String, RatingSummaryDto>()
        val missing = ArrayList<String>()
        memoLock.withLock {
            for (subject in subjects.distinct()) {
                val cached = memo[subject]
                if (cached != null && cached.second > now) {
                    out[subject] = cached.first
                } else {
                    missing += subject
                }
            }
        }
        for (chunk in missing.chunked(PER_REQUEST)) {
            val fetched = runCatching { client.ratingSummary(chunk) }.getOrNull() ?: continue
            memoLock.withLock {
                for (subject in chunk) {
                    val summary = fetched[subject] ?: continue
                    memo[subject] = summary to (now + TTL_MS)
                    out[subject] = summary
                }
            }
        }
        out
    }

    private suspend fun forget(subject: String) {
        memoLock.withLock { memo.remove(subject) }
    }

    private companion object {
        /** Matches the server's Cache-Control; a screen revisited within it costs nothing. */
        const val TTL_MS = 60_000L

        /** The most subjects one request may carry, per the endpoint. */
        const val PER_REQUEST = 50
    }
}
