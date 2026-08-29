package com.hasyame.marvelchampions.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type safe Navigation Compose routes.
 *
 * Every tab is a nested graph ([CardsGraph] and friends) wrapping a start
 * destination ([CardsRoute] and friends). The extra level of nesting is what
 * gives each tab an independent back stack.
 */

@Serializable
data object CardsGraph

@Serializable
data object CardsRoute

/** A single card. Reached from the card list on a narrow screen. */
@Serializable
data class CardDetailRoute(val code: String)

@Serializable
data object DecksGraph

@Serializable
data object DecksRoute

/** One imported deck. */
@Serializable
data class DeckDetailRoute(val deckId: String)

/** Hero and aspect picker for a deck built in the app. */
@Serializable
data object NewDeckRoute

/** The card-by-card editor for a locally built deck. */
@Serializable
data class DeckEditorRoute(val deckId: String)

@Serializable
data object CampaignGraph

@Serializable
data object CampaignRoute

/** Page 0: choose campaign, name, roster and difficulty. */
@Serializable
data object StartCampaignRoute

/** A finished campaign, read only. */
@Serializable
data class CampaignRecordRoute(val runId: String)

/** One campaign run. */
@Serializable
data class CampaignRunRoute(val runId: String)

/**
 * Everything that starts a game: a random draw, a setup the player builds, and
 * campaigns. Campaign used to be its own tab; it is a way to play, so it lives
 * here now.
 */
@Serializable
data object PlayGraph

/** The hub itself. */
@Serializable
data object PlayRoute

/** The Rules Reference, looked up during a game. */
@Serializable
data object RulesGraph

@Serializable
data object RulesRoute

/** The play history and what it adds up to. */
@Serializable
data object StatsGraph

@Serializable
data object RandomizerGraph

@Serializable
data object RandomizerRoute

@Serializable
data object VersusRoute

@Serializable
data object SettingsGraph

@Serializable
data object SettingsRoute

/**
 * The collection. A full screen of its own rather than a section inside the
 * settings list, because it is the source of truth for the randomiser and for
 * deck legality.
 */
@Serializable
data object CollectionRoute

/** Who made this, why, and what it is not. */
@Serializable
data object AboutRoute

/** Logged games and what they add up to. */
@Serializable
data object PlaysRoute

/**
 * A game the player sets up and the app times.
 *
 * The arguments let a randomiser draw hand its result straight over, so
 * "play this now" does not mean "type all of that in again". Heroes travel as
 * `code:aspect` pairs separated by commas, which survives a route argument
 * where a list would not.
 */
@Serializable
data class GameSessionRoute(
    val scenarioCode: String? = null,
    val difficulty: String? = null,
    val heroes: String? = null,
    val modularSets: String? = null,
    /**
     * The Standard set an Expert difficulty is played with.
     *
     * Carried because an Expert game is not a complete setup without it: the
     * session refuses to start, and a draw that arrives incomplete lands the
     * player back on the setup page having chosen nothing.
     */
    val standardSet: String? = null,
    /**
     * True when the game is already decided and the clock should simply start.
     *
     * A draw arrives complete — scenario, difficulty, heroes, modular sets —
     * so showing the setup page would ask the player to confirm choices the
     * randomiser just made for them.
     */
    val autoStart: Boolean = false,
    /**
     * The paused game to pick up, when the player is coming back to one.
     *
     * Everything else on this route stays null then: the saved game holds its
     * own scenario, heroes and difficulty, and reading them from two places is
     * how they end up disagreeing.
     */
    val resumeId: String? = null,
)
