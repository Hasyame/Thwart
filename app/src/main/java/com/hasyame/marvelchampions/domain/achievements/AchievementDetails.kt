package com.hasyame.marvelchampions.domain.achievements

enum class TargetKind { HERO, SCENARIO, ASPECT }

data class AchievementTarget(
    val key: String,
    val kind: TargetKind,
    val pack: String?,
    val completedBy: PlayFact?,
)

/** Display-only requirements, using the same all-seat rule and ordering as derivation. */
object AchievementDetails {
    fun targets(input: DeriveInput, definition: AchievementDefinition): List<AchievementTarget>? {
        val predicate = definition.predicate
        val minimum = when (predicate) {
            is Predicate.ScenariosWon -> predicate.minDifficulty
            is Predicate.HeroesWon -> predicate.minDifficulty
            is Predicate.AspectsWon -> predicate.minDifficulty
            else -> return null
        }
        val wins = input.facts.filter { it.won && it.level.atLeast(minimum) }
            .sortedWith(compareBy<PlayFact> { it.playedAt }.thenBy { it.id })
        return when (predicate) {
            is Predicate.ScenariosWon -> input.catalogue.scenarios.associate { it.key to it.packCode }
                .filterValues { predicate.pack == "*" || it == predicate.pack }
                .map { (key, pack) -> AchievementTarget(key, TargetKind.SCENARIO, pack, wins.firstOrNull { it.scenarioKey == key }) }
            is Predicate.HeroesWon -> input.catalogue.heroes.associate { it.code to it.packCode }
                .filterValues { predicate.pack == "*" || it == predicate.pack }
                .map { (key, pack) -> AchievementTarget(key, TargetKind.HERO, pack, wins.firstOrNull { fact -> fact.seats.any { it.heroCode == key } }) }
            is Predicate.AspectsWon -> CLASSIC_ASPECTS.map { key ->
                AchievementTarget(key, TargetKind.ASPECT,
                    input.catalogue.scenarios.firstOrNull { it.key == predicate.scenario }?.packCode,
                    wins.firstOrNull { fact ->
                        (predicate.scenario == null || fact.scenarioKey == predicate.scenario) &&
                            fact.seats.any { key in it.aspects }
                    },
                )
            }
        }
    }
}
