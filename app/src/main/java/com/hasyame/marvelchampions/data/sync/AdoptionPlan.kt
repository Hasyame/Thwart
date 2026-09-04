package com.hasyame.marvelchampions.data.sync

import com.hasyame.marvelchampions.data.db.entity.SyncCollection

/**
 * What signing this phone into an account would mean, counted before anything
 * is written.
 *
 * The case that matters most in the whole feature, and the one where a careless
 * implementation is unforgivable: somebody's two years of play history either
 * duplicated or gone. So the numbers are put in front of them first, in their
 * own terms — plays, decks, campaigns — and nothing at all happens until they
 * answer.
 *
 * The pulled records travel inside the plan rather than being fetched again on
 * the way in. Two pulls could differ, and then the counts that were agreed to
 * would not be the ones applied.
 */
class AdoptionPlan internal constructor(
    val counts: AdoptionCounts,
    internal val records: List<SyncRecordDto>,
    internal val cursor: Long,
)

/** The two sides of the sum, per collection. */
data class AdoptionCounts(
    /** Live records the account holds. */
    val server: Map<SyncCollection, Int> = emptyMap(),
    /** Live rows this phone holds that the account has never seen. */
    val localOnly: Map<SyncCollection, Int> = emptyMap(),
) {

    fun server(collection: SyncCollection): Int = server[collection] ?: 0

    fun localOnly(collection: SyncCollection): Int = localOnly[collection] ?: 0

    /** True when the account is empty, so merging cannot lose an argument. */
    val serverIsEmpty: Boolean get() = server.values.sum() == 0

    /** True when this phone has nothing the account lacks. */
    val nothingToContribute: Boolean get() = localOnly.values.sum() == 0

    /**
     * The three collections worth naming in the question.
     *
     * Not all ten. "This account has 128 plays, 6 decks and 3 campaigns" is a
     * sentence somebody can weigh; the same sentence with owned packs,
     * exclusions, favourites and randomiser draws in it is a list they will
     * skip, and the whole point is that they read it.
     */
    companion object {
        val HEADLINE = listOf(
            SyncCollection.PLAYS,
            SyncCollection.SAVED_DECKS,
            SyncCollection.CAMPAIGN_RUNS,
        )
    }
}
