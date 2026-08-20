package com.hasyame.marvelchampions.domain.campaign.engine

import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.allSetupSteps
import com.hasyame.marvelchampions.domain.campaign.template.CounterScope
import com.hasyame.marvelchampions.domain.campaign.template.DrawDefinition
import com.hasyame.marvelchampions.domain.campaign.template.Effect
import com.hasyame.marvelchampions.domain.campaign.template.EffectOp
import com.hasyame.marvelchampions.domain.campaign.template.Outcome
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate

/**
 * Extension point for the genuinely bespoke.
 *
 * A scenario names a handler id and the engine looks it up here. This is a last
 * resort — if two scenarios need the same shape, that shape belongs in the
 * declarative schema instead.
 */
interface ScenarioHandler {
    val id: String

    /** Runs after the template's own victory effects. */
    fun onVictory(state: CampaignState, scenario: ScenarioTemplate, answers: AnswerSet): CampaignState =
        state

    fun onDefeat(state: CampaignState, scenario: ScenarioTemplate, answers: AnswerSet): CampaignState =
        state
}

/**
 * Derives campaign state from the event log.
 *
 * Pure: same template, same events, same hero stats, same state. That is what
 * makes undo, replay-after-template-fix and two-device merging tractable.
 */
class CampaignEngine(
    private val handlers: Map<String, ScenarioHandler> = emptyMap(),
) {

    fun fold(
        template: CampaignTemplate,
        events: List<CampaignEvent>,
        heroStats: Map<String, HeroCardStats> = emptyMap(),
    ): CampaignState {
        // Revocations are applied first so a superseded result never takes
        // effect, however late the revocation was appended.
        val revoked = events.filterIsInstance<CampaignEvent.EventRevoked>()
            .map { it.revokedEventId }
            .toSet()
        val refunded = events.filterIsInstance<CampaignEvent.MarketRefund>()
            .map { it.purchaseEventId }
            .toSet()

        var state = CampaignState(templateId = template.id)

        for (event in events.sortedBy { it.timestamp }) {
            if (event.id in revoked) {
                continue
            }
            state = when (event) {
                is CampaignEvent.CampaignStarted -> applyStart(template, event)
                is CampaignEvent.ScenarioCompleted -> applyScenario(template, state, event, heroStats)
                is CampaignEvent.MarketPurchase ->
                    if (event.id in refunded) state else applyPurchase(state, event)

                is CampaignEvent.SetupActionTaken -> applySetupAction(template, state, event, heroStats)

                // Moving on from a result the players could have reconsidered.
                // Fear No Evil's lost jobs settle here rather than when the
                // defeat is filed, because until this the table may go back.
                is CampaignEvent.OutcomeContinued -> {
                    val scenario = template.scenarios.firstOrNull { it.id == event.scenarioId }
                    val outcome = if (event.victory) scenario?.onVictory else scenario?.onDefeat
                    applyEffects(
                        template,
                        state,
                        outcome?.onContinue.orEmpty(),
                        event.scenarioId,
                        AnswerSet(),
                        heroStats,
                    )
                }
                is CampaignEvent.SetupDrawn -> applyDraw(template, state, event)
                // The kept card replaces the offer, so everything downstream —
                // conditions, effects, the chips on the briefing — reads one
                // card without knowing a choice happened. The cards not kept
                // were never struck, so they are still in the pool.
                is CampaignEvent.SetupChoiceMade -> state.copy(
                    draws = state.draws + (
                        event.scenarioId to (
                            state.draws[event.scenarioId].orEmpty() +
                                (event.drawId to listOf(event.cardCode))
                            )
                        ),
                )
                is CampaignEvent.EnvironmentsOffered -> applyEnvironmentOffer(template, state, event)

                // The rotation has been read. The pressure was applied when the
                // two were dealt, so nothing about the board changes here —
                // this only clears the offer so the next reload does not deal
                // another pair.
                is CampaignEvent.EnvironmentChosen -> state.copy(
                    environmentOffer = emptyList(),
                    environmentPicked = true,
                )

                is CampaignEvent.CampaignConceded ->
                    state.copy(campaignLost = true, finished = true, awaitingChoice = false)

                is CampaignEvent.ScenarioChosen ->
                    state.copy(currentScenarioId = event.scenarioId, awaitingChoice = false)

                is CampaignEvent.ManualAdjustment -> applyManual(state, event)
                is CampaignEvent.TimeRecorded ->
                    state.copy(totalPlayTimeMillis = state.totalPlayTimeMillis + event.elapsedMillis)

                is CampaignEvent.EventRevoked, is CampaignEvent.MarketRefund -> state
            }
        }
        return state
    }

    private fun applyStart(
        template: CampaignTemplate,
        event: CampaignEvent.CampaignStarted,
    ): CampaignState {
        var state = CampaignState(
            templateId = template.id,
            difficulty = event.difficulty,
            heroes = event.heroes,
            started = true,
            awaitingChoice = template.chooseFirstScenario,
            currentScenarioId = event.startScenarioId.takeUnless { template.chooseFirstScenario },
        )

        for (counter in template.counters) {
            state = when (counter.counterScope) {
                CounterScope.CAMPAIGN ->
                    state.copy(counters = state.counters + (counter.id to counter.initial))

                CounterScope.HERO -> state.copy(
                    heroCounters = state.heroCounters + (
                        counter.id to event.heroes.associate { it.id to counter.initial }
                        ),
                )
            }
        }
        return state
    }

    private fun applyScenario(
        template: CampaignTemplate,
        state: CampaignState,
        event: CampaignEvent.ScenarioCompleted,
        heroStats: Map<String, HeroCardStats>,
    ): CampaignState {
        val scenario = template.scenarios.firstOrNull { it.id == event.scenarioId }
            ?: return state
        val outcome = if (event.victory) scenario.onVictory else scenario.onDefeat

        var next = state.copy(
            eliminatedInScenario = state.eliminatedInScenario + (
                event.scenarioId to eliminatedHeroes(event.answers)
                ),
        )

        if (outcome != null) {
            next = applyEffects(
                template = template,
                state = next,
                effects = outcome.effects,
                scenarioId = event.scenarioId,
                answers = event.answers,
                heroStats = heroStats,
            )
        }

        scenario.handlerId?.let { handlerId ->
            handlers[handlerId]?.let { handler ->
                next = if (event.victory) {
                    handler.onVictory(next, scenario, event.answers)
                } else {
                    handler.onDefeat(next, scenario, event.answers)
                }
            }
        }

        val advanced = resolveNext(outcome, next, event.scenarioId, event.answers)

        return next.copy(
            completedScenarios = next.completedScenarios + ScenarioResult(
                eventId = event.id,
                scenarioId = event.scenarioId,
                victory = event.victory,
                answers = event.answers,
                elapsedMillis = event.elapsedMillis,
                timestamp = event.timestamp,
            ),
            totalPlayTimeMillis = next.totalPlayTimeMillis + event.elapsedMillis,
            // A replay is a fresh setup, so the scenario's own setup draws go.
            // What the campaign assigned from outside that setup does not: Fear
            // No Evil notes which subordinate is behind a job before it is ever
            // played, and the book keeps that name once noted. Dropping the lot
            // dealt a different villain every time a job was retried.
            draws = next.draws.replayed(template, event.scenarioId),
            currentScenarioId = advanced.scenarioId,
            finished = advanced.finished,
            awaitingChoice = advanced.awaitingChoice,
            // A new rotation: the villains get to pick their next two places.
            environmentPicked = false,
        )
    }


    /**
     * Records what a draw came up with, and raises any counter that card feeds.
     *
     * The counters are declared on the draw rather than written by an effect,
     * because a draw happens during setup where no effects run.
     */
    private fun applyDraw(
        template: CampaignTemplate,
        state: CampaignState,
        event: CampaignEvent.SetupDrawn,
    ): CampaignState {
        // The environment draw is campaign-scoped and keys its rounds by their
        // own ids, so its counts are found on the template rather than in any
        // scenario's setup.
        val counts = (
            template.scenarios
                .flatMap { it.campaignSetup }
                .mapNotNull { it.draw }
                .firstOrNull { it.id == event.drawId }
                ?: template.environmentDraw?.takeIf { event.scenarioId == ENVIRONMENT_DRAW_SCENARIO }
            )
            ?.counts
            .orEmpty()

        var counters = state.counters
        for (code in event.cardCodes) {
            val counterId = counts[code] ?: continue
            counters = counters + (counterId to clamp((counters[counterId] ?: 0) + 1, template, counterId))
        }

        return state.copy(
            counters = counters,
            draws = state.draws + (
                event.scenarioId to
                    (state.draws[event.scenarioId].orEmpty() + (event.drawId to event.cardCodes))
                ),
        )
    }

    /**
     * Deals the rotation's environments and pushes the places they name.
     *
     * Two dealt, one tick each. One dealt — the last place still in the pile —
     * takes two, which is the rule for a lone environment and the reason the
     * end of a campaign closes in fast.
     *
     * A place pushed to its limit is out of the campaign: it stops being
     * choosable, and the last villain is harder for every job left unfinished.
     * A template may instead end the run there, but Fear No Evil does not —
     * two ticks land every rotation into a pile that shrinks as jobs are
     * settled, so ending on the first fall makes the campaign unwinnable.
     */
    private fun applyEnvironmentOffer(
        template: CampaignTemplate,
        state: CampaignState,
        event: CampaignEvent.EnvironmentsOffered,
    ): CampaignState {
        val counts = template.environmentDraw?.counts.orEmpty()
        val perEnvironment = if (event.offered.size == 1) 2 else 1

        var counters = state.counters
        for (environment in event.offered) {
            val counterId = counts[environment] ?: continue
            counters = counters + (
                counterId to clamp((counters[counterId] ?: 0) + perEnvironment, template, counterId)
                )
        }

        val pushed = state.copy(counters = counters, environmentOffer = event.offered)
        if (!template.losesWhenScenarioFails) {
            return pushed
        }

        // Only a job still in play can fall. One the players already saw
        // through is settled, whatever number is left beside its name — and
        // ending a campaign over a finished job is a defeat nobody could have
        // prevented.
        val settled = pushed.completedScenarios.map { it.scenarioId }.toSet()
        val fallen = template.scenarios.any { scenario ->
            scenario.id !in settled && scenario.failedWhen != null &&
                ConditionEvaluator.evaluate(
                    scenario.failedWhen,
                    EvaluationContext(state = pushed, scenarioId = scenario.id),
                )
        }
        return if (fallen) pushed.copy(campaignLost = true, finished = true) else pushed
    }

    private data class Advance(
        val scenarioId: String?,
        val finished: Boolean,
        val awaitingChoice: Boolean = false,
    )

    /**
     * `next` is a guarded list evaluated in order, so a branch is data. The
     * engine never assumes the next scenario in the array.
     */
    private fun resolveNext(
        outcome: Outcome?,
        state: CampaignState,
        scenarioId: String,
        answers: AnswerSet,
    ): Advance {
        val context = EvaluationContext(state = state, scenarioId = scenarioId, answers = answers)
        val step = outcome?.next?.firstOrNull { ConditionEvaluator.evaluate(it.condition, context) }
            ?: return Advance(scenarioId, finished = false)
        return when {
            step.end -> Advance(null, finished = true)
            // Nothing is current while the players decide, so the run has no
            // scenario to render until they have.
            step.choose -> Advance(null, finished = false, awaitingChoice = true)
            step.goto != null -> Advance(step.goto, finished = false)
            else -> Advance(scenarioId, finished = false)
        }
    }

    private fun eliminatedHeroes(answers: AnswerSet): Set<String> =
        answers.perHeroBooleans[ELIMINATED_PROMPT_ID]
            ?.filterValues { it }
            ?.keys
            .orEmpty()

    fun applyEffects(
        template: CampaignTemplate,
        state: CampaignState,
        effects: List<Effect>,
        scenarioId: String?,
        answers: AnswerSet,
        heroStats: Map<String, HeroCardStats>,
        actingHeroId: String? = null,
    ): CampaignState {
        var current = state
        for (effect in effects) {
            current = applyEffect(
                template, current, effect, scenarioId, answers, heroStats, actingHeroId,
            )
        }
        return current
    }

    private fun applyEffect(
        template: CampaignTemplate,
        state: CampaignState,
        effect: Effect,
        scenarioId: String?,
        answers: AnswerSet,
        heroStats: Map<String, HeroCardStats>,
        actingHeroId: String?,
    ): CampaignState {
        val baseContext = EvaluationContext(
            state = state,
            scenarioId = scenarioId,
            answers = answers,
            heroId = actingHeroId,
        )

        return when (effect.operation) {
            EffectOp.ADD_COUNTER, EffectOp.SUBTRACT_COUNTER, EffectOp.SET_COUNTER -> {
                val counterDef = template.counters.firstOrNull { it.id == effect.counter }
                    // The validator rejects undeclared counters, so this only
                    // happens when an old event log is replayed against a
                    // template that has since dropped one. Resurrecting it would
                    // put a counter on screen that the campaign no longer has.
                    ?: return state
                val delta = resolveValue(effect, answers) ?: return state

                if (counterDef.counterScope == CounterScope.HERO) {
                    // The condition is judged per hero inside, not here: a
                    // per-hero condition has no answer without a hero, so
                    // testing it once up front would fail for everyone.
                    applyPerHeroCounter(template, state, effect, delta, scenarioId, answers, heroStats, actingHeroId)
                } else {
                    if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                        return state
                    }
                    val id = effect.counter ?: return state
                    val existing = state.counter(id)
                    val raw = when (effect.operation) {
                        EffectOp.ADD_COUNTER -> existing + delta
                        EffectOp.SUBTRACT_COUNTER -> existing - delta
                        else -> delta
                    }
                    state.copy(counters = state.counters + (id to clamp(raw, template, id)))
                }
            }

            EffectOp.ADD_HERO_COUNTER, EffectOp.SET_HERO_COUNTER -> {
                if (template.counters.none { it.id == effect.counter }) {
                    return state
                }
                val delta = resolveValue(effect, answers)
                applyPerHeroCounter(
                    template, state, effect, delta, scenarioId, answers, heroStats, actingHeroId,
                )
            }

            EffectOp.SET_FLAG -> {
                if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                    return state
                }
                val flagId = effect.flag ?: return state
                // An answer that is missing means "no", never "yes". A switch
                // the player never touches puts nothing in the answer map, so
                // falling through to a default of true silently marked the box
                // for every scenario it was left alone in — which is how one
                // check produced three cards of setup.
                val value = when {
                    effect.boolValue != null -> effect.boolValue
                    effect.from != null -> answers.booleans[effect.from] == true
                    else -> true
                }
                // Split the same way conditions read it: `set.key` names its
                // own key, a bare id is keyed by the scope its set declares.
                // A campaign-scoped flag keyed by scenario reads back only in
                // the scenario that set it, which is not what campaign means.
                val parts = flagId.split('.', limit = 2)
                val setId = parts.first()
                val key = when {
                    parts.size == 2 -> parts[1]
                    template.flagSets.firstOrNull { it.id == setId }?.scope == PER_SCENARIO ->
                        scenarioId ?: ""

                    else -> ""
                }
                val existing = state.flags[setId].orEmpty()
                state.copy(flags = state.flags + (setId to (existing + (key to value))))
            }

            EffectOp.ADD_CARD -> {
                if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                    return state
                }
                val listId = effect.cardList ?: return state
                val code = effect.cardCode ?: return state
                if (effect.perHero || actingHeroId != null) {
                    val heroId = actingHeroId ?: return state
                    addHeroCards(state, listId, heroId, listOf(code))
                } else {
                    state.copy(
                        cardLists = state.cardLists +
                            (listId to (state.cardLists[listId].orEmpty() + code)),
                    )
                }
            }

            EffectOp.ADD_CARDS_FROM_ANSWER -> {
                if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                    return state
                }
                val listId = effect.cardList ?: return state
                // A per-hero answer folds into the same list, distinctly: what
                // later scenarios need from "each player chose one" is which
                // cards are in play, not who holds which. Who holds which stays
                // in the answer, for the log.
                val codes = answers.cardLists[effect.from].orEmpty()
                    .ifEmpty { answers.perHeroCards[effect.from].orEmpty().values.flatten() }
                    .distinct()
                state.copy(
                    cardLists = state.cardLists +
                        (listId to (state.cardLists[listId].orEmpty() + codes)),
                )
            }

            EffectOp.ADD_DRAWN_CARD -> {
                if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                    return state
                }
                val listId = effect.cardList ?: return state
                val drawId = effect.from ?: return state
                // Read against the scenario being resolved, so a strike records
                // what that scenario drew rather than whatever is current now.
                val drawn = state.draws[scenarioId].orEmpty()[drawId].orEmpty()
                val already = state.cardLists[listId].orEmpty()
                val fresh = drawn.filterNot { it in already }
                if (fresh.isEmpty()) {
                    return state
                }
                state.copy(cardLists = state.cardLists + (listId to (already + fresh)))
            }

            EffectOp.ELIMINATE_HERO -> {
                val heroId = actingHeroId ?: return state
                val key = scenarioId ?: ""
                state.copy(
                    eliminatedInScenario = state.eliminatedInScenario +
                        (key to (state.eliminatedInScenario[key].orEmpty() + heroId)),
                )
            }

            EffectOp.UNKNOWN -> state
        }
    }

    /**
     * Applies a hero-scoped counter change.
     *
     * A hero eliminated in this scenario is skipped, which is the rule the brief
     * calls out: they take no part in the victory rewards but rejoin next time.
     */
    private fun applyPerHeroCounter(
        template: CampaignTemplate,
        state: CampaignState,
        effect: Effect,
        literalDelta: Int?,
        scenarioId: String?,
        answers: AnswerSet,
        heroStats: Map<String, HeroCardStats>,
        actingHeroId: String?,
    ): CampaignState {
        val counterId = effect.counter ?: return state
        val eliminated = state.eliminatedInScenario[scenarioId ?: ""].orEmpty()
        val targets = actingHeroId?.let { listOf(it) }
            ?: state.heroes.map { it.id }.filter { it !in eliminated }

        val perHeroAnswers = answers.perHeroNumbers[effect.from]
            // A yes/no per hero doubles as a marker: "which hero holds this?"
            // becomes 1 for that hero and 0 for the rest.
            ?: answers.perHeroBooleans[effect.from]?.mapValues { if (it.value) 1 else 0 }
        val current = state.heroCounters[counterId].orEmpty().toMutableMap()

        for (heroId in targets) {
            val context = EvaluationContext(state, scenarioId, answers, heroId)
            if (!ConditionEvaluator.evaluate(effect.condition, context)) {
                continue
            }
            val value = perHeroAnswers?.get(heroId) ?: literalDelta ?: continue
            val capped = effect.max?.let { minOf(value, it) } ?: value
            val floored = effect.min?.let { maxOf(capped, it) } ?: capped

            val existing = current[heroId] ?: 0
            val raw = if (effect.operation == EffectOp.SET_HERO_COUNTER ||
                effect.operation == EffectOp.SET_COUNTER
            ) {
                floored
            } else {
                existing + floored
            }
            current[heroId] = clampHero(raw, template, counterId, heroStats[heroId])
        }
        return state.copy(heroCounters = state.heroCounters + (counterId to current))
    }

    private fun resolveValue(effect: Effect, answers: AnswerSet): Int? {
        val raw = effect.from?.let { answers.numbers[it] } ?: effect.value ?: return null
        // Division first, so "for every 2, gain 1, up to 3" reads in that order.
        val divided = effect.divideBy?.takeIf { it > 0 }?.let { raw / it } ?: raw
        val capped = effect.max?.let { minOf(divided, it) } ?: divided
        return effect.min?.let { maxOf(capped, it) } ?: capped
    }

    private fun clamp(value: Int, template: CampaignTemplate, counterId: String): Int {
        val def = template.counters.firstOrNull { it.id == counterId } ?: return value
        var result = value
        def.min?.let { result = maxOf(result, it) }
        def.max?.let { result = minOf(result, it) }
        return result
    }

    /** Hit points on Expert cap at the hero's printed health, read from the card database. */
    private fun clampHero(
        value: Int,
        template: CampaignTemplate,
        counterId: String,
        stats: HeroCardStats?,
    ): Int {
        val def = template.counters.firstOrNull { it.id == counterId } ?: return value
        var result = value
        def.min?.let { result = maxOf(result, it) }
        def.max?.let { result = minOf(result, it) }
        if (def.maxFrom == HERO_HEALTH_REFERENCE) {
            stats?.printedHealth?.let { result = minOf(result, it) }
        }
        return result
    }

    private fun addHeroCards(
        state: CampaignState,
        listId: String,
        heroId: String,
        codes: List<String>,
    ): CampaignState {
        val list = state.heroCardLists[listId].orEmpty()
        val forHero = list[heroId].orEmpty() + codes
        return state.copy(
            heroCardLists = state.heroCardLists + (listId to (list + (heroId to forHero))),
        )
    }

    private fun applyPurchase(
        state: CampaignState,
        event: CampaignEvent.MarketPurchase,
    ): CampaignState {
        val credits = state.heroCounters[MARKET_COUNTER_FALLBACK].orEmpty().toMutableMap()
        credits[event.heroId] = (credits[event.heroId] ?: 0) - event.cost
        val withCard = addHeroCards(state, event.cardListId, event.heroId, listOf(event.cardCode))
        return withCard.copy(
            heroCounters = withCard.heroCounters + (MARKET_COUNTER_FALLBACK to credits),
            purchases = withCard.purchases + Purchase(
                eventId = event.id,
                heroId = event.heroId,
                cardCode = event.cardCode,
                cost = event.cost,
                cardListId = event.cardListId,
            ),
        )
    }

    private fun applySetupAction(
        template: CampaignTemplate,
        state: CampaignState,
        event: CampaignEvent.SetupActionTaken,
        heroStats: Map<String, HeroCardStats>,
    ): CampaignState {
        val scenario = template.scenarios.firstOrNull { it.id == event.scenarioId } ?: return state
        val action = scenario.campaignSetup.mapNotNull { it.action }
            .firstOrNull { it.id == event.actionId } ?: return state

        var next = state
        action.cost?.let { cost ->
            val heroId = event.heroId
            if (heroId != null) {
                val counters = next.heroCounters[cost.counterId].orEmpty().toMutableMap()
                counters[heroId] = (counters[heroId] ?: 0) - cost.amount
                next = next.copy(
                    heroCounters = next.heroCounters + (cost.counterId to counters),
                )
            } else {
                next = next.copy(
                    counters = next.counters +
                        (cost.counterId to next.counter(cost.counterId) - cost.amount),
                )
            }
        }

        next = applyEffects(
            template = template,
            state = next,
            effects = action.effects,
            scenarioId = event.scenarioId,
            answers = AnswerSet(),
            heroStats = heroStats,
            actingHeroId = event.heroId,
        )

        val key = "${event.scenarioId}:${event.heroId.orEmpty()}"
        return next.copy(
            setupActionsTaken = next.setupActionsTaken +
                (key to (next.setupActionsTaken[key].orEmpty() + event.actionId)),
        )
    }

    private fun applyManual(
        state: CampaignState,
        event: CampaignEvent.ManualAdjustment,
    ): CampaignState {
        var next = state
        event.counterId?.let { counterId ->
            val value = event.value ?: return@let
            next = if (event.heroId != null) {
                val counters = next.heroCounters[counterId].orEmpty().toMutableMap()
                counters[event.heroId] = value
                next.copy(heroCounters = next.heroCounters + (counterId to counters))
            } else {
                next.copy(counters = next.counters + (counterId to value))
            }
        }
        event.flagId?.let { flagId ->
            val parts = flagId.split('.', limit = 2)
            val setId = parts[0]
            val key = parts.getOrNull(1) ?: ""
            val existing = next.flags[setId].orEmpty()
            next = next.copy(
                flags = next.flags + (setId to (existing + (key to (event.boolValue ?: true)))),
            )
        }
        return next
    }

    companion object {

        /**
         * The candidates a draw may still come up with.
         *
         * Anything already recorded in the excluding list is spent. If that
         * empties the pool the full set comes back: a scenario that needs a
         * card must get one, and an empty setup step would read as a bug.
         */
        fun drawPool(draw: DrawDefinition, state: CampaignState): List<String> {
            val spent = draw.excluding?.let { state.cardLists[it].orEmpty() }.orEmpty().toSet()
            return draw.from.filterNot { it in spent }.ifEmpty { draw.from }
        }


        /**
         * The scenarios the players may still pick.
         *
         * A scenario is spent once it has been played, won or lost — this
         * campaign does not replay them. The finale is held back until it is
         * the only thing left, which is what makes it the finale.
         */
        fun choosableScenarios(
            template: CampaignTemplate,
            state: CampaignState,
        ): List<ScenarioTemplate> {
            // Resolved, not merely played. Fear No Evil says a lost scenario
            // has not failed and may be attempted again — only winning it or
            // letting the villains push it to its limit settles it. Treating
            // any completed scenario as done took a defeat and quietly struck
            // the job off, which is neither the rule nor what a table expects.
            val won = state.completedScenarios.filter { it.victory }.map { it.scenarioId }.toSet()
            val remaining = template.scenarios.filterNot {
                it.id in won ||
                    it.id == template.finaleScenarioId ||
                    // Lost without ever being played: a place pushed three times
                    // is gone. Offering it again would let a table undo the one
                    // decision the campaign asks of them.
                    ConditionEvaluator.evaluate(
                        it.failedWhen,
                        EvaluationContext(state = state, scenarioId = it.id),
                    ) && it.failedWhen != null
            }
            return remaining.ifEmpty {
                template.scenarios.filter { it.id == template.finaleScenarioId && it.id !in won }
            }
        }

        /** What a scenario has already drawn, in the order drawn. */
        fun drawnCards(state: CampaignState, scenarioId: String?, drawId: String): List<String> =
            state.draws[scenarioId].orEmpty()[drawId].orEmpty()
        /**
         * The sentinel the campaign-scoped environment draw records under, so
         * it is never confused with a real scenario's draws and never cleared
         * when a scenario finishes.
         */
        const val ENVIRONMENT_DRAW_SCENARIO: String = "__environments__"

        /** Flag-set scope that keys each flag by the scenario that set it. */
        private const val PER_SCENARIO = "perScenario"

        /** Prompt id the engine treats as "was this hero eliminated". */
        const val ELIMINATED_PROMPT_ID: String = "eliminated"
        const val HERO_HEALTH_REFERENCE: String = "heroCard.health"

        /** Counter a market purchase spends when the template does not say. */
        const val MARKET_COUNTER_FALLBACK: String = "credits"
    }
}

/**
 * This scenario's draws as a replay should find them.
 *
 * Setup draws are made again — that is what a fresh setup means — while
 * anything the campaign assigned outside the scenario's setup survives.
 */
private fun Map<String, Map<String, List<String>>>.replayed(
    template: CampaignTemplate,
    scenarioId: String,
): Map<String, Map<String, List<String>>> {
    val setupDrawIds = template.scenarios.firstOrNull { it.id == scenarioId }
        ?.allSetupSteps().orEmpty()
        .mapNotNull { it.draw?.id }
        .toSet()
    val kept = this[scenarioId].orEmpty().filterKeys { it !in setupDrawIds }
    return if (kept.isEmpty()) this - scenarioId else this + (scenarioId to kept)
}
