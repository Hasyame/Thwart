package com.hasyame.marvelchampions.domain.draft

import com.hasyame.marvelchampions.domain.search.SearchNormalizer

/**
 * A pack as the draft's collection page shows it: what it is called, what
 * kind of box it is, and how many the saved collection has.
 */
data class DraftPack(
    val code: String,
    val name: String,
    /** [com.hasyame.marvelchampions.domain.model.PackType]'s stored name. */
    val type: String,
    /** Copies in the saved collection, which the session's count is compared with. */
    val owned: Int,
)

/**
 * The collection a draft is played from, as its page reads it.
 *
 * A draft may be played from a collection adjusted for the evening: a box
 * somebody brought, or one left at home. The saved collection is never
 * touched, so every question the page asks is "what does this draft have,
 * and how does that differ from what I own".
 *
 * Kept apart from the composable so the counting and the filtering can be
 * tested without a screen.
 */
object SessionCollection {

    /** Copies of [pack] this draft has: its own count, or none. */
    fun copies(collection: Map<String, Int>?, pack: DraftPack): Int = collection?.get(pack.code) ?: 0

    /**
     * Whether a pack is on the table, which is what decides whether its
     * identities can be drafted and its cards can be drawn.
     */
    fun holds(collection: Map<String, Int>?, packCode: String): Boolean = (collection?.get(packCode) ?: 0) > 0

    /** Packs this draft has at least one of. */
    fun onTable(packs: List<DraftPack>, collection: Map<String, Int>?): Int =
        packs.count { copies(collection, it) > 0 }

    /** Packs whose count differs from the saved collection: what the summary line reports. */
    fun changed(packs: List<DraftPack>, collection: Map<String, Int>?): Int =
        packs.count { copies(collection, it) != it.owned }

    /**
     * The packs the page lists.
     *
     * A search looks through every pack, because the point of searching is
     * usually a box somebody else brought; without one the list is what is
     * on the table, plus what the saved collection has, unless the switch
     * asks for everything.
     */
    fun shown(
        packs: List<DraftPack>,
        collection: Map<String, Int>?,
        query: String,
        everyPack: Boolean,
    ): List<DraftPack> {
        val needle = SearchNormalizer.normalize(query.trim())
        return packs.filter { pack ->
            if (needle.isNotEmpty()) {
                SearchNormalizer.normalize(pack.name).contains(needle)
            } else {
                everyPack || copies(collection, pack) > 0 || pack.owned > 0
            }
        }
    }
}
