package com.hasyame.marvelchampions.domain.draft

import com.hasyame.marvelchampions.domain.deckbuilder.DeckCardInfo
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import com.hasyame.marvelchampions.domain.deckbuilder.IdentityTraits
import com.hasyame.marvelchampions.domain.deckbuilder.SynergyCondition
import kotlinx.serialization.Serializable

/**
 * A draft, as it is written down between two taps.
 *
 * Everything here is what survives the app being closed: the settings, each
 * player's identity and picks, the packs built for them, the stock left on
 * the shelf, and the seed. The card data behind it is not written down;
 * [DraftContext] is rebuilt from the database each time. See
 * `docs/spec/synergie-et-draft.md`, phase 2.
 */
@Serializable
data class DraftState(
    val settings: DraftSettings = DraftSettings(),
    val players: List<DraftPlayer> = emptyList(),
    val phase: DraftPhase = DraftPhase.SETUP,
    /** Whose turn it is, on the identity pages and at the table. */
    val current: Int = 0,
    /**
     * Copies of each card on the shelf, by canonical code: not in a pack,
     * not in a deck. Shared by every player. Building packs takes from it;
     * opening one puts back what was not taken.
     */
    val stock: Map<String, Int> = emptyMap(),
    /** How many picks have been made in all. */
    val pickCount: Int = 0,
    /** The pack open on the table for the current player, by canonical code. */
    val offer: List<String> = emptyList(),
    /**
     * Each player's packs still to open, one list per player, in the order
     * they will be opened. Built before the first pick and again when a
     * player runs out with cards still to take. See [DraftEngine.buildPacks].
     */
    val packs: List<List<List<String>>> = emptyList(),
    /** How many times packs have been built, which seeds each build. */
    val builds: Int = 0,
    val seed: Long = 0L,
    /** Identity and aspect draws made so far, so "draw again" draws again. */
    val rolls: Int = 0,
) {
    val currentPlayer: DraftPlayer get() = players[current]

    val everyoneFull: Boolean get() = players.all { it.isFull }

    /** The packs a player has not opened yet. */
    fun packsOf(playerIndex: Int): List<List<String>> = packs.getOrNull(playerIndex).orEmpty()
}

@Serializable
data class DraftSettings(
    val players: Int = 1,
    /** Leave out cards the identity cannot play: the phase 1 rule. */
    val synergyOnly: Boolean = false,
    val identityMode: IdentityMode = IdentityMode.RANDOM,
    /** Cards in each pack, 2 to 10. */
    val offerSize: Int = DEFAULT_OFFER_SIZE,
) {
    companion object {
        const val MIN_PLAYERS = 1
        const val MAX_PLAYERS = 4
        const val MIN_OFFER_SIZE = 2
        const val MAX_OFFER_SIZE = 10
        const val DEFAULT_OFFER_SIZE = 5
    }
}

@Serializable
enum class IdentityMode { RANDOM, RANDOM_OF_FIVE, CHOICE }

@Serializable
enum class DraftPhase {
    SETUP,

    /** Identity, aspects and deck size, one player at a time. */
    IDENTITY,

    /** At the table. With several players the page simply changes hands. */
    PICK,

    /** Names, then everything is saved at once. */
    FINISH,
}

@Serializable
data class DraftPlayer(
    val index: Int,
    val heroCode: String? = null,
    val heroName: String = "",
    val heroSetCode: String? = null,
    /** Five identities to pick from, in the "random among five" mode. */
    val heroChoices: List<String> = emptyList(),
    val aspects: List<String> = emptyList(),
    val deckSize: Int = DraftRules.MIN_DECK_SIZE,
    /** The identity's own cards, by code and printed quantity, in from the start. */
    val signature: Map<String, Int> = emptyMap(),
    /** Canonical codes, in the order they were taken. */
    val picks: List<String> = emptyList(),
    /** The deck's name, once the player has settled it; the default until then. */
    val deckName: String? = null,
) {
    val signatureCount: Int get() = signature.values.sum()

    val cardCount: Int get() = signatureCount + picks.size

    val remaining: Int get() = (deckSize - cardCount).coerceAtLeast(0)

    val isFull: Boolean get() = cardCount >= deckSize

    val isReady: Boolean get() = heroCode != null && aspects.isNotEmpty()

    /** The deck as slots: the signature cards and every pick so far. */
    fun slots(): Map<String, Int> {
        val slots = signature.toMutableMap()
        picks.forEach { slots[it] = (slots[it] ?: 0) + 1 }
        return slots
    }
}

/** The table's constants. The deck bounds are the game's, restated for the draft. */
/*
 * Cited by the web client: `web/src/lib/draft/types.ts` mirrors these
 * values by name, and `engine.ts` and `naming.ts` port `DraftEngine` and
 * `DraftNaming` function for function. A change to the pack size, to how
 * packs are built and seeded, or to the naming is a change on both sides
 * at once, or a draft written down on one is dealt differently on the
 * other.
 */
object DraftRules {
    const val MIN_DECK_SIZE = 40
    const val MAX_DECK_SIZE = 50
    const val RANDOM_CHOICES = 5

    /** The four aspects every identity may pick from, in the order printed. */
    val CLASSIC_ASPECTS: List<String> = listOf("aggression", "justice", "leadership", "protection")
    const val POOL_ASPECT = "pool"

    /** The pack that brought the 'Pool aspect; without it there is nothing to draft. */
    const val POOL_PACK = "deadpool"
}

/**
 * A card on the shelf: one entry per printing family, since a reprint is the
 * same card. [info] is what the validator reads; the rest is for the screen.
 */
data class DraftCard(
    /** The original printing's code, which every reprint points to. */
    val canonicalCode: String,
    val info: DeckCardInfo,
    val condition: SynergyCondition?,
    val cost: Int?,
    val imageSrc: String?,
    val typeName: String,
    val factionName: String,
) {
    val name: String get() = info.name
    val factionCode: String get() = info.factionCode
}

/**
 * What the engine needs of the card data, rebuilt from the database whenever
 * the draft is opened. Nothing here is written down with the state.
 */
data class DraftContext(
    /** Every player card the collection holds, by canonical code. */
    val pool: Map<String, DraftCard>,
    /**
     * Copies of each on the shelf before anyone draws. Copied into the state
     * when the draft begins; the state's own count is what goes down.
     */
    val initialStock: Map<String, Int>,
    /** The rules of each identity in the draft, by hero code. */
    val rules: Map<String, HeroDeckRules>,
    /** The traits of each identity in the draft, by hero code. */
    val identities: Map<String, IdentityTraits>,
    /** The signature cards of each identity, by hero code and card code: in every deck from the start. */
    val signatureCards: Map<String, Map<String, DraftCard>>,
    /** True when the Deadpool pack is owned, which is what brings the 'Pool aspect. */
    val poolAspectAvailable: Boolean,
) {
    /** Every card the validator may be asked about. Built once: a deal asks hundreds of times. */
    val cardInfo: Map<String, DeckCardInfo> by lazy {
        pool.mapValues { it.value.info } + signatureCards.values.flatMap { it.entries }.associate { it.key to it.value.info }
    }
}
