package com.hasyame.marvelchampions.domain.achievements

/**
 * The derivation, docs/spec/achievements/algorithm.md, step for step.
 *
 * Pure and total: the same input gives the same state, field for field, in
 * the same order, whatever order the records came in. No clock, no random
 * draw, no storage; the shared test vectors are the contract with the web.
 * Anything the app wants to remember about this state is a cache.
 */
object AchievementDerivation {

    private const val DAY_MILLIS = 86_400_000L

    /** Â§3: facts by (playedAt, id); every "first" below is first in this order. */
    private val byPlay = compareBy<PlayFact> { it.playedAt }.thenBy { it.id }

    private fun later(a: PlayFact, b: PlayFact): PlayFact = if (byPlay.compare(a, b) >= 0) a else b

    private class MutableTally {
        var attempts = 0
        var wins = 0
        var best: CellBest? = null
        var bestLevelWon: DifficultyLevel? = null
        var firstWonAt: Long? = null
        var lastPlayedAt: Long? = null

        fun touch(fact: PlayFact) {
            attempts += 1
            if (fact.won) {
                wins += 1
                if (bestLevelWon == null || fact.level.rank > bestLevelWon!!.rank) {
                    bestLevelWon = fact.level
                }
                if (firstWonAt == null) {
                    firstWonAt = fact.playedAt
                }
            }
            best = if (wins > 0) CellBest.WON else CellBest.PLAYED
            lastPlayedAt = fact.playedAt
        }

        fun frozen() = Tally(attempts, wins, best, bestLevelWon, firstWonAt, lastPlayedAt)
    }

    private class MutableCell(val scenarioKey: String, val heroCode: String) {
        val owner = MutableTally()
        val any = MutableTally()
    }

    /** What a predicate says of the history: done or not, how far, and the play that did it. */
    private data class Verdict(
        val done: Boolean,
        val current: Int,
        val target: Int,
        val unlock: PlayFact?,
        val tier: TierName? = null,
    )

    /** Coverage over targets, each met by the first fact that satisfies it; the unlock is the last met. */
    private fun <T> coverage(targets: List<T>, metBy: (T) -> PlayFact?): Verdict {
        val met = targets.mapNotNull(metBy)
        val done = targets.isNotEmpty() && met.size == targets.size
        return Verdict(done, met.size, targets.size, if (done) met.reduce(::later) else null)
    }

    fun derive(input: DeriveInput): AchievementState {
        val facts = input.facts.sortedWith(byPlay)
        val runs = input.runs.sortedBy { it.id }
        val owned = input.ownedPacks

        // 4.1 cells
        val cells = LinkedHashMap<String, MutableCell>()
        facts.forEach { fact ->
            fact.seats.forEach { seat ->
                val cell = cells.getOrPut(fact.scenarioKey + "\u0000" + seat.heroCode) { MutableCell(fact.scenarioKey, seat.heroCode) }
                cell.any.touch(fact)
                if (seat.isOwner) {
                    cell.owner.touch(fact)
                }
            }
        }

        // 4.2 completion
        val heroes = input.catalogue.heroes.associate { it.code to it.packCode }
        val scenarios = input.catalogue.scenarios.associate { it.key to it.packCode }
        val ownedHeroes = heroes.filterValues { it in owned }.keys
        val ownedScenarios = scenarios.filterValues { it in owned }.keys
        var ownedWon = 0
        var globalWon = 0
        cells.values.forEach { cell ->
            if (cell.any.wins > 0) {
                if (cell.heroCode in heroes && cell.scenarioKey in scenarios) {
                    globalWon += 1
                }
                if (cell.heroCode in ownedHeroes && cell.scenarioKey in ownedScenarios) {
                    ownedWon += 1
                }
            }
        }

        val wins = facts.filter { it.won }

        // 4.3 predicates
        fun verdictOf(definition: AchievementDefinition): Verdict = when (val p = definition.predicate) {
            is Predicate.ScenariosWon -> {
                val targets = scenarios.filterValues { p.pack == "*" || it == p.pack }.keys.toList()
                coverage(targets) { key -> wins.firstOrNull { it.scenarioKey == key && it.level.atLeast(p.minDifficulty) } }
            }

            is Predicate.HeroesWon -> {
                val targets = heroes.filterValues { p.pack == "*" || it == p.pack }.keys.toList()
                coverage(targets) { code -> wins.firstOrNull { fact -> fact.seats.any { it.heroCode == code } && fact.level.atLeast(p.minDifficulty) } }
            }

            is Predicate.AspectsWon -> coverage(CLASSIC_ASPECTS) { aspect ->
                wins.firstOrNull {
                    (p.scenario == null || it.scenarioKey == p.scenario) &&
                        it.seats.any { seat -> aspect in seat.aspects } &&
                        it.level.atLeast(p.minDifficulty)
                }
            }

            is Predicate.FirstWin -> {
                val fact = wins.firstOrNull { it.level.atLeast(p.minDifficulty) }
                Verdict(fact != null, if (fact == null) 0 else 1, 1, fact)
            }

            is Predicate.Count -> {
                val tiers = definition.tiers
                val top = tiers.lastOrNull()?.n ?: 1
                var value = 0
                val seen = HashSet<Any>()
                var reached: PlayFact? = null
                facts.forEach { fact ->
                    when (p.what) {
                        CountWhat.PLAYS -> value += 1
                        CountWhat.WINS -> if (fact.won) value += 1
                        CountWhat.HEROES_PLAYED -> {
                            seen.addAll(fact.seats.map { it.heroCode })
                            value = seen.size
                        }
                        CountWhat.DISTINCT_DAYS -> {
                            seen += Math.floorDiv(fact.playedAt, DAY_MILLIS)
                            value = seen.size
                        }
                    }
                    if (reached == null && value >= top) {
                        reached = fact
                    }
                }
                Verdict(
                    done = value >= top,
                    current = minOf(value, top),
                    target = top,
                    unlock = reached,
                    tier = tiers.lastOrNull { value >= it.n }?.tier,
                )
            }

            is Predicate.TableWin -> {
                val fact = facts.firstOrNull { f ->
                    f.won && f.players == p.players && (!p.distinctAspects || distinctAspects(f))
                }
                Verdict(fact != null, if (fact == null) 0 else 1, 1, fact)
            }

            is Predicate.Campaign -> {
                val run = runs.firstOrNull { run ->
                    run.finished && !run.lost && run.level.atLeast(p.minDifficulty) &&
                        (!p.noDefeat || facts.none { it.campaignRunId == run.id && !it.won })
                }
                if (run == null) {
                    Verdict(false, 0, 1, null)
                } else {
                    // The last play of the run dates the unlock; a run with
                    // none is done all the same, and undated.
                    val last = facts.filter { it.campaignRunId == run.id }.reduceOrNull(::later)
                    Verdict(true, 1, 1, last)
                }
            }

            is Predicate.ModeWin -> {
                val matching = wins.filter { it.mode == p.mode }
                Verdict(matching.size >= p.n, minOf(matching.size, p.n), p.n, matching.getOrNull(p.n - 1))
            }
            is Predicate.LossCount -> {
                val matching = facts.filter { !it.won }
                Verdict(matching.size >= p.n, minOf(matching.size, p.n), p.n, matching.getOrNull(p.n - 1))
            }
        }

        // 4.4 status
        fun unavailable(definition: AchievementDefinition): Boolean {
            if (definition.scope != AchievementScope.OWNED) {
                return false
            }
            return when (val p = definition.predicate) {
                is Predicate.ScenariosWon -> if (p.pack == "*") scenarios.values.any { it !in owned } else p.pack !in owned
                is Predicate.HeroesWon -> if (p.pack == "*") heroes.values.any { it !in owned } else p.pack !in owned
                is Predicate.AspectsWon -> p.scenario != null && scenarios[p.scenario].let { it == null || it !in owned }
                else -> false
            }
        }

        val achievements = input.definitions.map { definition ->
            val verdict = verdictOf(definition)
            val dated = verdict.done && verdict.unlock != null
            AchievementStatus(
                id = definition.id,
                status = when {
                    verdict.done -> AchievementStatusKind.UNLOCKED
                    unavailable(definition) -> AchievementStatusKind.UNAVAILABLE
                    else -> AchievementStatusKind.LOCKED
                },
                progress = Progress(verdict.current, verdict.target),
                tier = verdict.tier,
                unlockedAt = if (dated) verdict.unlock!!.playedAt else null,
                unlockedByPlayId = if (dated) verdict.unlock!!.id else null,
            )
        }

        // 4.5 recent
        val recent = achievements
            .filter { it.status == AchievementStatusKind.UNLOCKED && it.unlockedAt != null }
            .map { Unlock(it.id, it.unlockedAt!!, it.unlockedByPlayId) }
            .sortedWith(compareByDescending<Unlock> { it.unlockedAt }.thenBy { it.id })

        // 5 output ordering
        val outCells = cells.values
            .sortedWith(compareBy<MutableCell> { it.scenarioKey }.thenBy { it.heroCode })
            .map { cell ->
                val owner = cell.owner.frozen()
                Cell(
                    heroCode = cell.heroCode,
                    scenarioKey = cell.scenarioKey,
                    attempts = owner.attempts,
                    wins = owner.wins,
                    best = owner.best,
                    bestLevelWon = owner.bestLevelWon,
                    firstWonAt = owner.firstWonAt,
                    lastPlayedAt = owner.lastPlayedAt,
                    anySeat = cell.any.frozen(),
                )
            }

        return AchievementState(
            definitionsVersion = input.definitionsVersion,
            scaleVersion = DifficultyLevel.SCALE_VERSION,
            cells = outCells,
            achievements = achievements,
            completion = CompletionViews(
                owned = Completion(ownedWon, ownedHeroes.size * ownedScenarios.size),
                global = Completion(globalWon, heroes.size * scenarios.size),
            ),
            recent = recent,
        )
    }

    /** Every seat has an aspect, and no two seats share one. */
    private fun distinctAspects(fact: PlayFact): Boolean {
        val sets = fact.seats.map { it.aspects.toSet() }
        if (sets.any { it.isEmpty() }) {
            return false
        }
        for (i in sets.indices) {
            for (j in i + 1 until sets.size) {
                if (sets[i].any { it in sets[j] }) {
                    return false
                }
            }
        }
        return true
    }

    /** Which achievements went from not unlocked to unlocked: what a toast celebrates. */
    fun newlyUnlocked(before: AchievementState?, after: AchievementState): List<Unlock> {
        val was = before?.achievements.orEmpty()
            .filter { it.status == AchievementStatusKind.UNLOCKED }
            .map { it.id }
            .toSet()
        return after.achievements
            .filter { it.status == AchievementStatusKind.UNLOCKED && it.id !in was }
            .map { Unlock(it.id, it.unlockedAt ?: 0L, it.unlockedByPlayId) }
    }
}
