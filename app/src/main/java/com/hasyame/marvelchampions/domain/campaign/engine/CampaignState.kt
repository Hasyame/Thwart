package com.hasyame.marvelchampions.domain.campaign.engine

import com.hasyame.marvelchampions.domain.campaign.template.ComputedAmount

/**
 * Everything derived from the event log. Never stored — always folded.
 */
data class CampaignState(
    val templateId: String = "",
    val difficulty: String = "standard",
    val heroes: List<CampaignHero> = emptyList(),
    val started: Boolean = false,
    val finished: Boolean = false,

    /** Campaign-scoped counters. */
    val counters: Map<String, Int> = emptyMap(),
    /** Hero-scoped counters: counter id to hero id to value. */
    val heroCounters: Map<String, Map<String, Int>> = emptyMap(),

    /** Flag set id to scenario id (or "" for campaign scope) to value. */
    val flags: Map<String, Map<String, Boolean>> = emptyMap(),

    /** Campaign or per-scenario card lists. */
    val cardLists: Map<String, List<String>> = emptyMap(),
    /** Card list id to hero id to card codes. */
    val heroCardLists: Map<String, Map<String, List<String>>> = emptyMap(),

    /** Heroes eliminated in the scenario currently being resolved. */
    val eliminatedInScenario: Map<String, Set<String>> = emptyMap(),

    /**
     * Cards the app drew for a scenario's setup: scenario id to draw id to card
     * code. Cleared when the scenario is finished, so replaying after a defeat
     * draws afresh rather than repeating the setup that just went wrong.
     */
    val draws: Map<String, Map<String, List<String>>> = emptyMap(),
    /** What the table answered when the campaign was started, by choice id. */
    val choices: Map<String, String> = emptyMap(),

    /** True while the campaign is waiting for the players to pick what to play. */
    val awaitingChoice: Boolean = false,

    /**
     * Environments the app has dealt this rotation, waiting on the players.
     *
     * Fear No Evil opens each rotation by dealing two places the villains have
     * hit. Both take the pressure; the players keep one, which is then out of
     * the pile for good. Empty when nothing is on the table.
     */
    val environmentOffer: List<String> = emptyList(),

    /** Environments the players have kept, and which never come up again. */

    /**
     * True once this rotation's environment has been kept.
     *
     * A rotation deals its environments exactly once. Without this the app
     * looked at an empty table, decided nothing had been dealt yet, and dealt
     * again — every reload pushing two more places until one fell and took the
     * campaign with it. Cleared when a scenario finishes and the next rotation
     * begins.
     */
    val environmentPicked: Boolean = false,

    /**
     * True when a place fell and took the campaign with it.
     *
     * Distinct from [finished], which only says the campaign is over: a run
     * that ended this way is a defeat and is recorded as one.
     */
    val campaignLost: Boolean = false,

    val currentScenarioId: String? = null,
    val completedScenarios: List<ScenarioResult> = emptyList(),
    val setupActionsTaken: Map<String, Set<String>> = emptyMap(),
    val purchases: List<Purchase> = emptyList(),
    val totalPlayTimeMillis: Long = 0,
) {
    fun counter(id: String): Int = counters[id] ?: 0

    fun heroCounter(id: String, heroId: String): Int = heroCounters[id]?.get(heroId) ?: 0

    fun flag(setId: String, scenarioId: String = ""): Boolean =
        flags[setId]?.get(scenarioId) ?: false

    fun countTrue(setId: String): Int = flags[setId]?.values?.count { it } ?: 0

    fun heroCards(listId: String, heroId: String): List<String> =
        heroCardLists[listId]?.get(heroId).orEmpty()

    /** Cards bought anywhere in the campaign, for the cross-hero uniqueness rule. */
    fun allPurchasedCardCodes(): Set<String> = purchases.map { it.cardCode }.toSet()
}

data class ScenarioResult(
    val eventId: String,
    val scenarioId: String,
    val victory: Boolean,
    val answers: AnswerSet,
    val elapsedMillis: Long,
    val timestamp: Long,
)

data class Purchase(
    val eventId: String,
    val heroId: String,
    val cardCode: String,
    val cost: Int,
    val cardListId: String,
)

/**
 * Facts about a hero that come from the card database rather than the template,
 * so `maxFrom: "heroCard.health"` can cap hit points at printed health.
 */
data class HeroCardStats(
    val heroId: String,
    val printedHealth: Int?,
)

/**
 * An amount the campaign works out for itself, read against this run.
 *
 * Null when there is nothing to work out. Zero is a real answer and means the
 * rule does not apply yet, which is why the two are kept apart: a step with no
 * amount is shown as written, and a step whose amount is nothing is not shown.
 */
fun CampaignState.amountOf(amount: ComputedAmount?): Int? = amount?.amountFor(
    counterValue = when {
        amount.flagSet.isNotBlank() -> countTrue(amount.flagSet)
        else -> counter(amount.counter)
    },
    expert = difficulty.equals("expert", ignoreCase = true),
)
