package com.hasyame.marvelchampions.domain.randomizer

import kotlin.random.Random

/**
 * Draws a scenario setup from the packs the user owns.
 *
 * Pure and deterministic given a [Random], so every rule below is testable
 * without a device.
 *
 * The locking model is the whole point: [draw] keeps the value of every field
 * in `locked` and rerolls the rest, so "reroll just the hero" is a call with
 * every other field locked.
 */
object ScenarioRandomizer {

    /** Deadpool's aspect is playable only by Deadpool. */
    const val POOL_ASPECT: String = "pool"
    private const val DEADPOOL_HERO_CODE = "deadpool"

    fun draw(
        pools: RandomizerPools,
        rules: Map<String, ScenarioRule>,
        filters: RandomizerFilters = RandomizerFilters(),
        previous: RandomizerDraw = RandomizerDraw(),
        locked: Set<DrawField> = emptySet(),
        random: Random = Random.Default,
    ): RandomizerDraw {
        // A pack is restricted exactly when some scenario names it as its own
        // pool. Derived from the rules rather than passed in, so the two cannot
        // disagree about which packs those are.
        val restrictedPacks = rules.values.flatMap { it.modularPacks }.toSet()

        val scenarioCode = if (DrawField.SCENARIO in locked) {
            previous.scenarioCode
        } else {
            pools.scenarios
                .filter { it.code !in filters.excludedScenarios }
                // A scenario that demands a set the player cannot field is not
                // playable, so it is not offered. Drawing it and flagging the
                // gap would only make them roll again by hand.
                .filter { scenario ->
                    rules[scenario.code]?.mandatoryModulars.orEmpty()
                        .none { it in filters.excludedModularSets }
                }
                .randomOrNull(random)
                ?.code
        }

        val difficulty = if (DrawField.DIFFICULTY in locked) {
            previous.difficulty
        } else {
            Difficulty.entries
                .filter { it in filters.allowedDifficulties }
                // Owning the pack a difficulty came in is not a preference, so
                // it is checked here rather than left to the filter.
                .filter { it in pools.difficulties }
                .randomOrNull(random)
                // Nothing ownable and allowed at once means the filter and the
                // collection disagree. The collection wins: a draw the player
                // cannot put on the table is not a draw.
                ?: pools.difficulties.randomOrNull(random)
        }

        // Extra difficulty sets, only when asked for. Anything from none to all
        // of them: the point of the switch is a game that might be harder than
        // the box, and a fixed number would make it the same harder every time.
        // The one already drawn is excluded, since you cannot shuffle in a set
        // that is already in the deck.
        val extraDifficulties = if (!filters.includeExtraDifficulty) {
            emptyList()
        } else {
            pools.difficulties
                .filter { it != difficulty && it in filters.allowedDifficulties }
                .shuffled(random)
                .take(random.nextInt(0, pools.difficulties.size.coerceAtLeast(1)))
                .sorted()
        }

        val playerCount = if (DrawField.PLAYER_COUNT in locked) {
            previous.playerCount
        } else {
            val low = filters.minPlayers.coerceAtLeast(1)
            val high = filters.maxPlayers.coerceAtLeast(low)
            random.nextInt(low, high + 1)
        }

        val rule = scenarioCode?.let { rules[it] }
        val mandatory = rule?.mandatoryModulars.orEmpty()
            // A mandatory set from a pack the user does not own cannot be
            // played, so it is dropped rather than silently pretended.
            .filter { code -> pools.modularSets.any { it.code == code } }

        val modularSetCodes = if (DrawField.MODULAR_SETS in locked && previous.modularSetCodes.isNotEmpty()) {
            previous.modularSetCodes
        } else {
            drawModularSets(
                pools.copy(
                    modularSets = pools.modularSets.filter {
                        it.code !in filters.excludedModularSets &&
                            isLegalFor(it, rule, restrictedPacks)
                    },
                ),
                rule,
                mandatory,
                random,
            )
        }

        val heroes = if (DrawField.HEROES in locked && DrawField.ASPECTS in locked) {
            previous.heroes.take(playerCount)
        } else {
            drawHeroes(
                pools = pools,
                filters = filters,
                playerCount = playerCount,
                previous = previous,
                locked = locked,
                random = random,
            )
        }

        return RandomizerDraw(
            scenarioCode = scenarioCode,
            difficulty = difficulty,
            extraDifficulties = extraDifficulties,
            modularSetCodes = modularSetCodes,
            playerCount = playerCount,
            heroes = heroes,
            mandatoryModularCodes = mandatory,
        )
    }

    /**
     * Whether a modular set may be used by this scenario.
     *
     * Civil War and She-Hulk share a pool that is only legal in their own
     * games. Everybody else may use anything owned, except those — which is why
     * the check runs both ways rather than only consulting the scenario's rule:
     * without the second half, Hell's Kitchen turns up in Rhino.
     */
    private fun isLegalFor(
        set: SetRef,
        rule: ScenarioRule?,
        restrictedPacks: Set<String>,
    ): Boolean {
        val allowed = rule?.modularPacks.orEmpty()
        if (allowed.isNotEmpty()) {
            return set.packCode in allowed
        }
        return set.packCode !in restrictedPacks
    }

    private fun drawModularSets(
        pools: RandomizerPools,
        rule: ScenarioRule?,
        mandatory: List<String>,
        random: Random,
    ): List<String> {
        if (rule == null) {
            return emptyList()
        }
        val chosen = mandatory.toMutableList()
        // Civil War takes three or four, decided at the table, so the count is
        // itself part of the draw. Everything else has max equal to count and
        // this settles on the one number.
        val wanted = if (rule.modularCountMax > rule.modularCount) {
            random.nextInt(rule.modularCount, rule.modularCountMax + 1)
        } else {
            rule.modularCount
        }
        // Mandatory sets already count towards the scenario's total, so only
        // the shortfall is drawn at random.
        val remaining = (wanted - chosen.size).coerceAtLeast(0)
        if (remaining == 0) {
            return chosen
        }
        val available = pools.modularSets
            .map { it.code }
            .filter { it !in chosen }
            .toMutableList()
        repeat(remaining) {
            if (available.isEmpty()) {
                return chosen
            }
            chosen += available.removeAt(random.nextInt(available.size))
        }
        return chosen
    }

    private fun drawHeroes(
        pools: RandomizerPools,
        filters: RandomizerFilters,
        playerCount: Int,
        previous: RandomizerDraw,
        locked: Set<DrawField>,
        random: Random,
    ): List<HeroAssignment> {
        val heroesLocked = DrawField.HEROES in locked
        val aspectsLocked = DrawField.ASPECTS in locked

        val availableHeroes = pools.heroes
            .map { it.code }
            .filter { it !in filters.excludedHeroes }
            .toMutableList()

        val assignments = mutableListOf<HeroAssignment>()
        for (index in 0 until playerCount) {
            val previousAssignment = previous.heroes.getOrNull(index)

            val heroCode = if (heroesLocked && previousAssignment != null) {
                previousAssignment.heroCode
            } else {
                if (availableHeroes.isEmpty()) {
                    return assignments
                }
                availableHeroes.removeAt(random.nextInt(availableHeroes.size))
            }
            // A hero picked for one player must not turn up again for another.
            availableHeroes.remove(heroCode)

            val aspect = if (aspectsLocked && previousAssignment != null) {
                previousAssignment.aspect
            } else {
                pickAspect(pools, filters, heroCode, random)
            } ?: continue

            assignments += HeroAssignment(heroCode = heroCode, aspect = aspect)
        }
        return assignments
    }

    private fun pickAspect(
        pools: RandomizerPools,
        filters: RandomizerFilters,
        heroCode: String,
        random: Random,
    ): String? = pools.aspects
        .filter { it !in filters.excludedAspects }
        .filter { aspect ->
            // 'Pool cards are Deadpool-only, so the aspect is offered to nobody
            // else. Without this the randomiser produces illegal decks.
            aspect != POOL_ASPECT || heroCode == DEADPOOL_HERO_CODE
        }
        .randomOrNull(random)

    private fun <T> List<T>.randomOrNull(random: Random): T? =
        if (isEmpty()) null else this[random.nextInt(size)]
}
