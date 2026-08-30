package com.hasyame.marvelchampions.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A game put down mid-play, with enough of the table written down to rebuild it.
 *
 * A short pause is the timer stopping. This is the other kind: the table is
 * cleared, or left for a week, and what matters is not the clock but where
 * everything stood. So it is a row in the database rather than something held
 * in a view model, because the point of it is surviving the app being closed.
 *
 * One at a time, by design. Two saved games would need naming, choosing between
 * and tidying up, which is a filing system for a thing that happens when
 * somebody has to go and eat.
 */
@Entity(tableName = "paused_games")
data class PausedGameEntity(
    @PrimaryKey val id: String,
    val savedAt: Long,

    /** Enough to set the same game up again. */
    val scenarioCode: String,
    val scenarioName: String,
    val difficulty: String,
    /** Hero codes and names, as `code|name` entries, comma separated. */
    val heroes: String,
    /** Modular set codes, comma separated. */
    val modularSetCodes: String = "",
    val elapsedMillis: Long = 0,

    /**
     * Which phase the game stopped in, as a [PausedPhase] name.
     *
     * The villain phase has steps of its own and the player phase does not,
     * which is why [villainStep] is separate and empty for the other.
     */
    val phase: String = PausedPhase.PLAYER.name,
    /** The villain phase step, as a [VillainStep] name, or empty. */
    val villainStep: String = "",

    /** Hit points left, as `heroName|points` entries, comma separated. */
    val heroLives: String = "",
    val villainLife: Int = 0,
    /** Which villain card is face up: 1, 2 or 3. */
    val villainStage: Int = 1,

    /** Photographs of the table, by file name, comma separated. */
    @ColumnInfo(defaultValue = "")
    val photos: String = "",

    /**
     * The campaign run this game belongs to, or empty for a game of its own.
     *
     * A campaign scenario is a game like any other and gets put away for the
     * same reasons, so it is saved here rather than in a second place: one list
     * of games waiting to be picked up, whatever they were started from.
     * Resuming reads this to decide whether to reopen the campaign or a plain
     * session, since a campaign scenario has a log to go back to.
     */
    @ColumnInfo(defaultValue = "")
    val campaignRunId: String = "",
)

/** The two halves of a round a game can be stopped in. */
enum class PausedPhase {
    PLAYER,
    VILLAIN,
}

/**
 * The steps of the villain phase, in the order they are resolved.
 *
 * Written down because coming back to a table after a week, the question is
 * never "whose turn" but "how far through the villain's turn were we".
 */
enum class VillainStep {
    PLACE_THREAT,
    ACTIVATE_MINIONS,
    DEAL_ENCOUNTERS,
    REVEAL_ENCOUNTERS,
    PASS_FIRST_PLAYER,
}
