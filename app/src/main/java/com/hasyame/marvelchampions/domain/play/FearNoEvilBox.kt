package com.hasyame.marvelchampions.domain.play

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.TrackedSide

/** One of the box's scenarios, as a one-off game offers it. */
data class FneScenario(
    /** The one-off code, `fne_s1_musee`. */
    val code: String,
    val name: String,
    /** True for a job, which pairs with a subordinate; false for the finale. */
    val needsVillain: Boolean,
)

/** One of the box's subordinates. */
data class FneVillain(val id: String, val name: String)

/**
 * Fear No Evil's box, read for a game outside the campaign.
 *
 * The box is on no card database, so everything a one-off game needs is
 * read from the campaign template: the jobs and the finale, the five
 * subordinates a job is played against, the numbers the tracker counts,
 * and the setup text of the briefing. Pure, so a test can read the bundled
 * template and check every answer. See [FearNoEvil] for the codes.
 */
class FearNoEvilBox(private val template: CampaignTemplate) {

    val packCode: String? get() = template.packCode

    /** Every scenario of the box, jobs and finale, in the template's order. */
    fun scenarios(localeCode: String): List<FneScenario> {
        val fixed = template.tracker?.villains.orEmpty().keys
        return template.scenarios.map { scenario ->
            FneScenario(
                code = FearNoEvil.codeOf(scenario.id),
                name = scenario.name?.resolve(localeCode)?.takeIf { it.isNotBlank() } ?: scenario.id,
                // A scenario the tracker names a villain for is its own, as
                // Kingpin is; every other one is played against a subordinate.
                needsVillain = scenario.id !in fixed,
            )
        }
    }

    /** The subordinates a job can be played against. */
    fun villains(localeCode: String): List<FneVillain> =
        template.villainPool.map { id -> FneVillain(id, cardName(id, localeCode)) }

    /**
     * Every code a one-off game can carry, with its name: each scenario on
     * its own, and each job with each subordinate, "Art Museum Heist :
     * Electro", as the versus scenarios name their two halves.
     */
    fun names(localeCode: String): Map<String, String> {
        val villains = villains(localeCode)
        return buildMap {
            scenarios(localeCode).forEach { scenario ->
                put(scenario.code, scenario.name)
                if (scenario.needsVillain) {
                    villains.forEach { villain ->
                        put(FearNoEvil.compose(scenario.code, villain.id), "${scenario.name} : ${villain.name}")
                    }
                }
            }
        }
    }

    /**
     * The tracker's numbers for a one-off game: the villain's stages for the
     * difficulty, and the scenario's main scheme. Nothing when the code
     * names a job without its villain, since there is nothing to count yet.
     */
    fun encounterSetup(code: String, players: Int, expert: Boolean, localeCode: String): EncounterSetup {
        val tracker = template.tracker ?: return EncounterSetup()
        val (job, villain) = FearNoEvil.split(code)
        val scenarioId = job.removePrefix(FearNoEvil.PREFIX)
        val difficulty = if (expert) "expert" else "standard"
        fun List<TrackedSide>.forThisGame() =
            filter { it.onlyOn == null || it.onlyOn.equals(difficulty, ignoreCase = true) }
        val stages = (tracker.villains[villain] ?: tracker.villains[scenarioId])?.forThisGame().orEmpty()
        val scheme = tracker.schemes[scenarioId]?.forThisGame().orEmpty()
        // The tracker writes its names in French; the villain's is known in
        // both languages, so it is written in the reader's.
        val villainName = villain?.let { cardName(it, localeCode) }
        val seats = players.coerceAtLeast(1)
        return EncounterSetup(
            villain = stages.map { side -> side.toSide().let { if (villainName != null) it.copy(name = villainName) else it } },
            scheme = scheme.map { it.toSide() },
            players = seats,
            schemeCopies = if (scenarioId in tracker.perPlayerSchemes) seats else 1,
        )
    }

    /**
     * The scenario's setup, as the campaign briefs it, for the game's own
     * briefing: the steps that hold for this difficulty and this villain,
     * with the villain's and the cards' names written in. The campaign's
     * pressure and its records are not part of a one-off game, so nothing
     * is added on top of what the cards print, and the step about the
     * job's environment card is left out: that card is the campaign's
     * pressure board, turned over between jobs, and a game on its own has
     * no use for it.
     */
    fun briefing(code: String, difficulty: String?, localeCode: String): List<String> {
        val (job, villain) = FearNoEvil.split(code)
        val scenarioId = job.removePrefix(FearNoEvil.PREFIX)
        val scenario = template.scenarios.firstOrNull { it.id == scenarioId } ?: return emptyList()
        // A campaign state with only what a one-off game knows: the
        // difficulty, and the villain, as the draw the template reads. A
        // one-off game names its difficulty by set ("Expert II"); the
        // template knows the two words the campaign uses.
        val state = CampaignState(
            templateId = template.id,
            difficulty = if (difficulty?.contains("expert", ignoreCase = true) == true) "expert" else "standard",
            draws = villain?.let { mapOf(scenarioId to mapOf(VILLAIN_DRAW to listOf(it))) }.orEmpty(),
        )
        val context = EvaluationContext(state = state, scenarioId = scenarioId)
        // The environment card shares the scenario's id, which is how the
        // step about it is told from the rest.
        val environment = "{card:$scenarioId}"
        return scenario.preSetup
            .flatMap { step -> step.include?.let { template.setupFragments[it].orEmpty() } ?: listOf(step) }
            .filter { it.draw == null && ConditionEvaluator.evaluate(it.condition, context) }
            .filterNot { step -> listOfNotNull(step.text.en, step.text.fr).any { environment in it } }
            .map { step -> fill(step.text.resolve(localeCode), villain, localeCode) }
            .filter { it.isNotBlank() }
    }

    /** `{villain}` and `{card:x}` as names, in the reader's language. */
    private fun fill(text: String, villain: String?, localeCode: String): String =
        PLACEHOLDER.replace(text) { match ->
            val (cardPrefix, name) = match.destructured
            when {
                cardPrefix.isNotEmpty() -> "\"" + cardName(name, localeCode) + "\""
                name == VILLAIN_DRAW -> villain?.let { "\"" + cardName(it, localeCode) + "\"" }.orEmpty()
                else -> ""
            }
        }.replace("  ", " ").trim()

    /** A card the template names itself, since the database does not know it. */
    private fun cardName(id: String, localeCode: String): String =
        template.localCardNames[id]?.resolve(localeCode)?.takeIf { it.isNotBlank() } ?: id

    /** As the campaign counts it, with nothing carried over from earlier jobs. */
    private fun TrackedSide.toSide(): EncounterSide = EncounterSide(
        name = name,
        stage = stage,
        value = value?.coerceAtLeast(0),
        perPlayer = perPlayer,
        starred = starred,
        startingThreat = startingThreat.coerceAtLeast(0),
        startingThreatPerPlayer = startingThreatPerPlayer,
        escalation = escalation.coerceAtLeast(0),
        escalationPerPlayer = escalationPerPlayer,
        escalationVariable = escalationVariable,
    )

    private companion object {
        /** The draw the template files the villain under, as the campaign does. */
        const val VILLAIN_DRAW = "villain"

        /** Both braces escaped: Android's ICU rejects a bare closing brace. */
        val PLACEHOLDER = Regex("""\{(card:)?([A-Za-z0-9_]+)\}""")
    }
}
