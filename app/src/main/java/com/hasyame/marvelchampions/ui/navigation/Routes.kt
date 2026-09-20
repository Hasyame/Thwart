package com.hasyame.marvelchampions.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type safe Navigation Compose routes.
 *
 * Every tab is a nested graph ([CardsGraph] and friends) wrapping a start
 * destination ([CardsRoute] and friends). The extra level of nesting is what
 * gives each tab an independent back stack.
 */

/** The home page: the version's notes, the links, and a menu. First in the bar. */
@Serializable
data object HomeGraph

@Serializable
data object HomeRoute

/** Every game played, as a shelf of tiles. Reached from Home. */
@Serializable
data object HistoryRoute

/**
 * What the history adds up to as things to earn: the grid and the named
 * achievements. Its own page, reached from Home, the Play hub, the
 * statistics and a game's result, in whichever graph the caller is.
 */
@Serializable
data object AchievementsRoute

/** One game of the history, read back. */
@Serializable
data class PlayDetailRoute(val playId: String)

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
data class StartCampaignRoute(val deckIds: String = "", val expert: Boolean = false)

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
data class RandomizerRoute(val deckIds: String = "")

@Serializable
data object VersusRoute

/** Decks built one pick at a time, from the collection. Resumes where it was left. */
@Serializable
data class DraftRoute(val sealed: Boolean = false)

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

/**
 * The account, and whether this device syncs with it.
 *
 * A screen of its own rather than a section of the settings list: it holds a
 * sign-in form, a device list and a question that must be read before it is
 * answered, none of which belong in a list of switches.
 */
@Serializable
/**
 * The account page. [create] opens it on the "create an account" form,
 * for the home page's button; otherwise it opens on sign-in.
 */
data class SyncAccountRoute(val create: Boolean = false)

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
    val challengeJson: String? = null,
    val deckIds: String = "",
    val randomScenario: Boolean = false,
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
    /**
     * The game in the history to set the table up like again.
     *
     * The setup is read from the play rather than spelled out here, for the
     * same reason [resumeId] is: a play knows its own heroes by name and
     * aspect, and squeezing that through the `code:aspect` string above would
     * lose the name and break on a deck that plays two aspects.
     */
    val replayId: String? = null,
)

/** Keep the selected game mode as a destination, not a custom-game prefill flag. */
fun limitedGameRoute(mode: String, ids: List<String>): Any {
    val decks = ids.joinToString(",")
    return when (mode) {
        "random" -> RandomizerRoute(decks)
        "campaign" -> StartCampaignRoute(decks)
        "own" -> GameSessionRoute(deckIds = decks)
        else -> error("Unknown limited game destination: $mode")
    }
}
