package com.hasyame.marvelchampions.domain.campaign.engine

import com.hasyame.marvelchampions.domain.campaign.template.Condition

/** The context a condition is judged against. */
data class EvaluationContext(
    val state: CampaignState,
    val scenarioId: String? = null,
    val answers: AnswerSet = AnswerSet(),
    /** Set when evaluating per hero, so hero counters resolve to that hero. */
    val heroId: String? = null,
)

/**
 * Evaluates a template [Condition].
 *
 * Every field present must hold; [Condition.any] is the escape hatch for
 * alternatives. A condition with no fields set is vacuously true, which is what
 * makes `when` optional throughout the schema.
 */
object ConditionEvaluator {

    fun evaluate(condition: Condition?, context: EvaluationContext): Boolean {
        if (condition == null) {
            return true
        }
        val state = context.state

        condition.difficulty?.let {
            if (!state.difficulty.equals(it, ignoreCase = true)) return false
        }

        condition.answer?.let {
            if (context.answers.booleans[it] != true) return false
        }
        condition.notAnswer?.let {
            if (context.answers.booleans[it] == true) return false
        }

        // Per-hero answers are read for whichever hero is being evaluated, so a
        // reward can land on one player and not another.
        condition.heroAnswer?.let { promptId ->
            val heroId = context.heroId ?: return false
            if (context.answers.perHeroBooleans[promptId]?.get(heroId) != true) return false
        }
        condition.notHeroAnswer?.let { promptId ->
            val heroId = context.heroId ?: return false
            if (context.answers.perHeroBooleans[promptId]?.get(heroId) == true) return false
        }

        condition.cardList?.let { listId ->
            val recorded = state.cardLists[listId].orEmpty()
            condition.contains?.let { if (it !in recorded) return false }
            condition.notContains?.let { if (it in recorded) return false }
            condition.minSize?.let { if (recorded.size < it) return false }
        }

        condition.flag?.let {
            if (!resolveFlag(it, state, context.scenarioId)) return false
        }
        condition.notFlag?.let {
            if (resolveFlag(it, state, context.scenarioId)) return false
        }

        condition.drawIs?.let { reference ->
            val (drawId, code) = reference.split(':', limit = 2).let {
                it.first() to it.getOrElse(1) { "" }
            }
            if (code !in state.draws[context.scenarioId].orEmpty()[drawId].orEmpty()) {
                return false
            }
        }

        condition.countTrue?.let { setId ->
            val count = state.countTrue(setId)
            condition.countAtLeast?.let { if (count < it) return false }
            condition.countAtMost?.let { if (count > it) return false }
            // Without a bound, "countTrue" alone means at least one.
            if (condition.countAtLeast == null && condition.countAtMost == null && count < 1) {
                return false
            }
        }

        condition.counter?.let { counterId ->
            val value = context.heroId
                ?.takeIf { state.heroCounters.containsKey(counterId) }
                ?.let { state.heroCounter(counterId, it) }
                ?: state.counter(counterId)
            condition.atLeast?.let { if (value < it) return false }
            condition.atMost?.let { if (value > it) return false }
            condition.equals?.let { if (value != it) return false }
        }

        condition.choice?.let { promptId ->
            val expected = condition.choiceIs
            if (expected != null && context.answers.choices[promptId] != expected) {
                return false
            }
        }

        condition.anyHero?.let { perHero ->
            val holds = state.heroes.any { hero ->
                evaluate(perHero, context.copy(heroId = hero.id))
            }
            if (!holds) return false
        }

        if (condition.all.isNotEmpty() && condition.all.any { !evaluate(it, context) }) {
            return false
        }
        if (condition.any.isNotEmpty() && condition.any.none { evaluate(it, context) }) {
            return false
        }

        return true
    }

    /**
     * `trackerDefeated.s1_badoon` reads one scenario's flag;
     * `trackerDefeated` reads the current scenario's, falling back to the
     * campaign-scoped slot.
     */
    private fun resolveFlag(
        reference: String,
        state: CampaignState,
        currentScenarioId: String?,
    ): Boolean {
        val parts = reference.split('.', limit = 2)
        return if (parts.size == 2) {
            state.flag(parts[0], parts[1])
        } else {
            state.flag(reference, currentScenarioId ?: "") || state.flag(reference, "")
        }
    }
}
