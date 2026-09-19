package com.hasyame.marvelchampions.domain.achievements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The achievements, as the shared specification declares them
 * (docs/spec/achievements in the web repository, the master copy).
 *
 * Everything here is either read from the definitions file or derived from
 * the game history; nothing is stored. The names are the specification's,
 * so this and the web implementation can be read side by side.
 */

/** The difficulty scale, `difficultyScaleVersion` 1: an explicit total order. */
@Serializable
enum class DifficultyLevel(val rank: Int) {
    @SerialName("unknown") UNKNOWN(0),
    @SerialName("standard") STANDARD(1),
    @SerialName("expert") EXPERT(2),
    ;

    fun atLeast(min: DifficultyLevel?): Boolean = rank >= (min ?: UNKNOWN).rank

    companion object {
        const val SCALE_VERSION = 1

        /**
         * The level a difficulty string names. Trimmed, lowercased, dashes
         * and spaces folded to underscores; then by prefix. Never Standard
         * by default: a play with nothing usable recorded is `unknown`, so
         * an old play can never wrongly satisfy a difficulty achievement.
         */
        fun of(difficulty: String?): DifficultyLevel {
            val folded = difficulty.orEmpty().trim().lowercase().replace(Regex("[-\\s]+"), "_")
            return when {
                folded.startsWith("expert") -> EXPERT
                folded.startsWith("standard") -> STANDARD
                else -> UNKNOWN
            }
        }
    }
}

@Serializable
enum class AchievementCategory {
    @SerialName("coverage") COVERAGE,
    @SerialName("difficulty") DIFFICULTY,
    @SerialName("volume") VOLUME,
    @SerialName("table") TABLE,
    @SerialName("campaign") CAMPAIGN,
    @SerialName("mode") MODE,
}

@Serializable
enum class AchievementScope {
    @SerialName("owned") OWNED,
    @SerialName("global") GLOBAL,
}

@Serializable
enum class TierName {
    @SerialName("bronze") BRONZE,
    @SerialName("silver") SILVER,
    @SerialName("gold") GOLD,
    @SerialName("platinum") PLATINUM,
}

@Serializable
data class Tier(val tier: TierName, val n: Int)

/** The four classic aspects, the ones "all four" ranges over; 'pool' never counts. */
val CLASSIC_ASPECTS: List<String> = listOf("aggression", "justice", "leadership", "protection")

/** The modes a play may carry; anything else is read as an ordinary game. */
val PLAY_MODES: Set<String> = setOf("draft", "sealed", "daily", "shared")

/**
 * What an achievement asks of the history. Every predicate is data; the
 * derivation interprets these kinds and nothing else, and a file naming
 * another kind is refused at load.
 */
sealed interface Predicate {
    data class ScenariosWon(val pack: String, val minDifficulty: DifficultyLevel? = null) : Predicate
    data class HeroesWon(val pack: String, val minDifficulty: DifficultyLevel? = null) : Predicate
    data class AspectsWon(val scenario: String? = null, val minDifficulty: DifficultyLevel? = null) : Predicate
    data class FirstWin(val minDifficulty: DifficultyLevel) : Predicate
    data class Count(val what: CountWhat) : Predicate
    data class TableWin(val players: Int, val distinctAspects: Boolean = false) : Predicate
    data class Campaign(val noDefeat: Boolean = false, val minDifficulty: DifficultyLevel? = null) : Predicate
    data class ModeWin(val mode: String, val n: Int = 1) : Predicate
    data class LossCount(val n: Int) : Predicate
}

enum class CountWhat { PLAYS, WINS, HEROES_PLAYED, DISTINCT_DAYS }

data class AchievementDefinition(
    val id: String,
    val category: AchievementCategory,
    val scope: AchievementScope,
    val hidden: Boolean,
    /** Counting achievements only; thresholds strictly ascending. */
    val tiers: List<Tier> = emptyList(),
    val predicate: Predicate,
)

data class DefinitionsFile(
    val schemaVersion: Int,
    val definitionsVersion: Int,
    val difficultyScaleVersion: Int,
    val achievements: List<AchievementDefinition>,
)

// --- the input of the derivation ------------------------------------------------

@Serializable
data class HeroRef(val code: String, val packCode: String)

@Serializable
data class ScenarioRef(val key: String, val packCode: String)

@Serializable
data class Catalogue(val heroes: List<HeroRef>, val scenarios: List<ScenarioRef>)

@Serializable
data class Seat(
    val heroCode: String,
    /** Lowercase codes, deduplicated, sorted. */
    val aspects: List<String>,
    val isOwner: Boolean,
)

@Serializable
data class PlayFact(
    val id: String,
    val playedAt: Long,
    val scenarioKey: String,
    val level: DifficultyLevel,
    val won: Boolean,
    val players: Int,
    val seats: List<Seat>,
    val campaignRunId: String? = null,
    val mode: String? = null,
) {
    /** The owner's seat, or the first when none is flagged (normalisation guarantees one). */
    val owner: Seat? get() = seats.firstOrNull { it.isOwner } ?: seats.firstOrNull()
}

@Serializable
data class RunFact(
    val id: String,
    val templateId: String,
    val level: DifficultyLevel,
    val finished: Boolean,
    val lost: Boolean,
)

data class DeriveInput(
    val definitions: List<AchievementDefinition>,
    val definitionsVersion: Int,
    val catalogue: Catalogue,
    val ownedPacks: Set<String>,
    val facts: List<PlayFact>,
    val runs: List<RunFact>,
)

// --- the derived state, never stored --------------------------------------------

@Serializable
enum class CellBest {
    @SerialName("played") PLAYED,
    @SerialName("won") WON,
}

/** One tally of a cell: the owner's seat, or every seat at the table. */
@Serializable
data class Tally(
    val attempts: Int,
    val wins: Int,
    val best: CellBest?,
    val bestLevelWon: DifficultyLevel?,
    val firstWonAt: Long?,
    val lastPlayedAt: Long?,
)

@Serializable
data class Cell(
    val heroCode: String,
    val scenarioKey: String,
    val attempts: Int,
    val wins: Int,
    val best: CellBest?,
    val bestLevelWon: DifficultyLevel?,
    val firstWonAt: Long?,
    val lastPlayedAt: Long?,
    val anySeat: Tally,
) {
    val owner: Tally get() = Tally(attempts, wins, best, bestLevelWon, firstWonAt, lastPlayedAt)
}

@Serializable
enum class AchievementStatusKind {
    @SerialName("locked") LOCKED,
    @SerialName("unlocked") UNLOCKED,
    @SerialName("unavailable") UNAVAILABLE,
}

@Serializable
data class Progress(val current: Int, val target: Int)

@Serializable
data class AchievementStatus(
    val id: String,
    val status: AchievementStatusKind,
    val progress: Progress,
    val tier: TierName?,
    val unlockedAt: Long?,
    val unlockedByPlayId: String?,
)

@Serializable
data class Unlock(val id: String, val unlockedAt: Long, val unlockedByPlayId: String?)

@Serializable
data class Completion(val won: Int, val cells: Int)

@Serializable
data class CompletionViews(val owned: Completion, val global: Completion)

@Serializable
data class AchievementState(
    val definitionsVersion: Int,
    val scaleVersion: Int,
    val cells: List<Cell>,
    val achievements: List<AchievementStatus>,
    val completion: CompletionViews,
    val recent: List<Unlock>,
)
