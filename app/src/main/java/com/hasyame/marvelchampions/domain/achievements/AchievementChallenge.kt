package com.hasyame.marvelchampions.domain.achievements

/** A suggestion advances one remaining requirement, without awarding anything. */
@kotlinx.serialization.Serializable
data class AchievementChallenge(
    val hero: String? = null,
    val scenario: String? = null,
    val aspect: String? = null,
    val players: Int = 1,
    val distinctAspects: Boolean = false,
    val expert: Boolean = false,
    val destination: String = "game",
)

fun achievementChallenge(input: DeriveInput, definition: AchievementDefinition): AchievementChallenge? {
    val predicate = definition.predicate
    if (predicate is Predicate.LossCount) return null
    val targets = AchievementDetails.targets(input, definition)
    val target = targets?.firstOrNull { it.completedBy == null && (it.pack == null || it.pack in input.ownedPacks) }
    if (targets != null && target == null) return null
    val level = when (predicate) {
        is Predicate.ScenariosWon -> predicate.minDifficulty
        is Predicate.HeroesWon -> predicate.minDifficulty
        is Predicate.AspectsWon -> predicate.minDifficulty
        is Predicate.FirstWin -> predicate.minDifficulty
        is Predicate.Campaign -> predicate.minDifficulty
        else -> null
    }
    var challenge = AchievementChallenge(expert = level == DifficultyLevel.EXPERT)
    challenge = when (target?.kind) {
        TargetKind.HERO -> challenge.copy(hero = target.key)
        TargetKind.SCENARIO -> challenge.copy(scenario = target.key)
        TargetKind.ASPECT -> challenge.copy(aspect = target.key, scenario = (predicate as Predicate.AspectsWon).scenario)
        null -> challenge
    }
    return when (predicate) {
        is Predicate.TableWin -> challenge.copy(players = predicate.players, distinctAspects = predicate.distinctAspects)
        is Predicate.Campaign -> challenge.copy(destination = "campaign")
        is Predicate.ModeWin -> if (predicate.mode in listOf("draft", "sealed")) challenge.copy(destination = predicate.mode) else null
        is Predicate.Count -> if (predicate.what == CountWhat.HEROES_PLAYED) {
            val played = input.facts.flatMap { it.seats.map { seat -> seat.heroCode } }.toSet()
            input.catalogue.heroes.firstOrNull { it.packCode in input.ownedPacks && it.code !in played }
                ?.let { challenge.copy(hero = it.code) }
        } else challenge
        else -> challenge
    }
}
