package com.hasyame.marvelchampions.domain.ratings

import com.hasyame.marvelchampions.data.db.entity.PlayEntity

/**
 * What a rating is about, and the key that names it.
 *
 * The key is the record's id on both clients and on the server, so it is
 * spelled here once and nowhere else. A scenario is rated by its villain set;
 * a modular set by the set **and** the scenario it was played with, because a
 * set's difficulty is mostly what it is paired with; a campaign by its
 * template, once finished. `docs/spec/ratings-and-modular-sets.md` §2.2.
 */
data class RatingSubject(
    val key: String,
    val kind: Kind,
    /** The set or template the subject is about. */
    val code: String,
    /** For a modular set: the scenario it was paired with. */
    val pairedWith: String? = null,
) {
    enum class Kind { SCENARIO, MODULAR, CAMPAIGN }

    companion object {
        fun scenario(setCode: String) = RatingSubject("scenario:$setCode", Kind.SCENARIO, setCode)

        fun modular(setCode: String, scenarioCode: String) = RatingSubject(
            key = "modular:$setCode@$scenarioCode",
            kind = Kind.MODULAR,
            code = setCode,
            pairedWith = scenarioCode,
        )

        fun campaign(templateId: String) = RatingSubject("campaign:$templateId", Kind.CAMPAIGN, templateId)

        /**
         * The bare per-set view the summary endpoint serves, the mean over
         * every pairing. Not a subject anyone rates; asked for when a set's
         * pairing with the scenario in view has too few ratings to show.
         */
        fun modularOverallKey(setCode: String): String = "modular:$setCode"

        /**
         * What a recorded game can be rated on: its scenario, then each modular
         * set that was on the table, paired with that scenario.
         *
         * [scenarioSetCode] is the villain set the game was played against. For
         * a one-off game that is the play's own `scenarioCode`; a campaign's
         * scenario is logged under the campaign's id and has to be resolved
         * through its template first, and a campaign whose villain is drawn
         * at the table (Fear No Evil) has no set to name, so nothing to rate.
         */
        fun ofPlay(play: PlayEntity, scenarioSetCode: String? = play.scenarioCode): List<RatingSubject> {
            val scenario = scenarioSetCode?.takeIf { it.isNotBlank() } ?: return emptyList()
            val sets = play.modularSets.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
            return listOf(scenario(scenario)) + sets.map { modular(it, scenario) }
        }
    }
}
