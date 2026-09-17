package com.hasyame.marvelchampions.domain.draft

import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidator
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import kotlin.random.Random

/**
 * The draft, turn by turn. Pure: every function takes the state and gives
 * the next one, and every draw comes from the seed, so a draft written down
 * and reopened offers the same cards, and a test gets the same draft twice.
 */
object DraftEngine {

    /** What stands between the table and a deck of the size asked for. */
    data class Shortfall(val playerIndex: Int, val needed: Int, val available: Int)

    // --- identities -----------------------------------------------------------

    /**
     * The identities a player may still take: the collection's, minus those
     * other players already hold. Two players never share a hero.
     */
    fun availableHeroes(state: DraftState, owned: List<String>): List<String> {
        val taken = state.players.mapNotNull { it.heroCode }.toSet()
        return owned.filter { it !in taken }
    }

    /** A random identity for the player about to choose. */
    fun randomHero(state: DraftState, owned: List<String>): String? =
        availableHeroes(state, owned).randomOrNull(identityRandom(state))

    /** Five random identities, or fewer when the collection is smaller. */
    fun randomHeroChoices(state: DraftState, owned: List<String>): List<String> =
        availableHeroes(state, owned).shuffled(identityRandom(state)).take(DraftRules.RANDOM_CHOICES)

    /**
     * The aspects an identity may choose from: the four, and 'Pool when the
     * Deadpool pack is on the shelf. Empty when the identity's own rule
     * settles the question, as Adam Warlock's does.
     */
    fun aspectChoices(rules: HeroDeckRules?, poolAvailable: Boolean): List<String> {
        if (rules != null && rules.aspectCount >= DraftRules.CLASSIC_ASPECTS.size) {
            return emptyList()
        }
        return DraftRules.CLASSIC_ASPECTS + listOfNotNull(DraftRules.POOL_ASPECT.takeIf { poolAvailable })
    }

    /** The aspects an identity's rule imposes, when it does; null otherwise. */
    fun imposedAspects(rules: HeroDeckRules?): List<String>? =
        if (rules != null && rules.aspectCount >= DraftRules.CLASSIC_ASPECTS.size) DraftRules.CLASSIC_ASPECTS else null

    /** A random legal choice of aspects for the identity. */
    fun randomAspects(state: DraftState, rules: HeroDeckRules?, poolAvailable: Boolean): List<String> {
        imposedAspects(rules)?.let { return it }
        val count = rules?.aspectCount ?: 1
        return aspectChoices(rules, poolAvailable).shuffled(identityRandom(state)).take(count)
    }

    // --- the shelf --------------------------------------------------------------

    /**
     * The cards a player may be offered from what is left on the shelf: the
     * chosen aspects and basic, plus whatever the identity's own allowances
     * admit; never another identity's cards; and, when asked, nothing the
     * identity cannot play.
     */
    fun playerPool(state: DraftState, player: DraftPlayer, context: DraftContext): List<DraftCard> {
        val rules = context.rules[player.heroCode] ?: return emptyList()
        val identity = context.identities[player.heroCode]
        val factions = player.aspects.toSet() + BASIC_FACTION
        return context.pool.values.filter { card ->
            (state.stock[card.canonicalCode] ?: 0) > 0 &&
                (card.factionCode in factions || rules.options.any { it.admits(card.info) }) &&
                (!state.settings.synergyOnly || identity == null || card.condition?.compatibleWith(identity) != false)
        }
    }

    /**
     * Copies of [card] one deck can hold: the shelf's, capped by the copy
     * limit that applies to this identity. A Core Set alone has copies enough
     * for two Justice decks on paper, and not once each deck may take three
     * of a title at most, which is what this caps.
     */
    fun usableCopies(player: DraftPlayer, card: DraftCard, stock: Map<String, Int>, context: DraftContext): Int {
        val rules = context.rules[player.heroCode] ?: return 0
        val limit = when {
            card.info.isUnique -> 1
            else -> rules.copyLimitOverride ?: card.info.deckLimit ?: DEFAULT_COPY_LIMIT
        }
        return minOf(stock[card.canonicalCode] ?: 0, limit)
    }

    /**
     * Whether the shelf can fill every deck: each player's own pool, counted
     * as copies their deck may hold, against what they still need; then
     * everybody's needs against the shelf as a whole, since one copy drafted
     * by one player is one copy fewer for the rest. An estimate on the safe
     * side of "enough", not a proof; the pick itself never offers an illegal
     * card, whatever this says.
     */
    fun shortfalls(state: DraftState, context: DraftContext): List<Shortfall> {
        val shortfalls = mutableListOf<Shortfall>()
        // What every deck together could take of each title, capped by the shelf.
        val sharedCapacity = mutableMapOf<String, Int>()
        var totalNeeded = 0
        state.players.forEach { player ->
            val pool = playerPool(state, player, context)
            var available = 0
            pool.forEach { card ->
                val usable = usableCopies(player, card, state.stock, context)
                available += usable
                sharedCapacity[card.canonicalCode] = (sharedCapacity[card.canonicalCode] ?: 0) + usable
            }
            val needed = player.remaining
            totalNeeded += needed
            if (available < needed) {
                shortfalls += Shortfall(player.index, needed, available)
            }
        }
        if (shortfalls.isEmpty()) {
            val shared = sharedCapacity.entries.sumOf { (code, capacity) -> minOf(capacity, state.stock[code] ?: 0) }
            if (shared < totalNeeded) {
                // Not one player's fault: say so against the last, whose
                // deck is the one the shelf runs out on.
                shortfalls += Shortfall(state.players.last().index, totalNeeded, shared)
            }
        }
        return shortfalls
    }

    // --- picks ----------------------------------------------------------------

    /**
     * Whether the deck can still be finished legally with [card] in it.
     *
     * The validator judges the deck as it would stand, and anything it
     * reports beyond the size and the balance rules out the card: a fourth
     * copy, a second unique, Adam Warlock's second copy of anything. Size is
     * the point of drafting, and balance is judged by whether the picks
     * left can still even the aspects out: a card is refused when taking it
     * would leave more deficit than picks.
     */
    fun canTake(player: DraftPlayer, card: DraftCard, context: DraftContext): Boolean {
        val rules = context.rules[player.heroCode] ?: return false
        val slots = player.slots().toMutableMap()
        slots[card.canonicalCode] = (slots[card.canonicalCode] ?: 0) + 1
        val validation = DeckValidator.validate(rules, player.aspects, slots, context.cardInfo)
        val blocking = validation.problems.filterNot {
            it is DeckProblem.TooFewCards || it is DeckProblem.UnbalancedAspects
        }
        if (blocking.isNotEmpty()) {
            return false
        }
        if (!rules.aspectsMustBalance) {
            return true
        }
        val counts = player.aspects.associateWith { aspect ->
            slots.entries.sumOf { (code, quantity) ->
                val info = context.pool[code]?.info ?: return@sumOf 0
                if (info.factionCode == aspect) quantity else 0
            }
        }
        val deficit = counts.values.sumOf { (counts.values.max()) - it }
        val remainingAfter = player.deckSize - player.cardCount - 1
        return remainingAfter >= deficit
    }

    /** The cards the current player may be offered right now. */
    fun legalOffers(state: DraftState, context: DraftContext): List<DraftCard> {
        val player = state.currentPlayer
        return playerPool(state, player, context).filter { canTake(player, it, context) }
    }

    /**
     * Puts the current player's offer on the table: [DraftSettings.offerSize]
     * cards drawn at random from what they may take, or what is left when
     * that is fewer. Seeded by the pick count, so reopening the draft finds
     * the same cards on the table.
     */
    fun deal(state: DraftState, context: DraftContext): DraftState {
        val legal = legalOffers(state, context)
        val random = Random(state.seed + state.pickCount * PICK_STRIDE + state.current)
        val offer = legal.shuffled(random).take(state.settings.offerSize).map { it.canonicalCode }
        return state.copy(offer = offer)
    }

    /**
     * The current player takes [canonicalCode]. The copy leaves the shelf,
     * and the turn passes to the next player who still has room; when nobody
     * has room the draft is over.
     */
    fun pick(state: DraftState, canonicalCode: String, context: DraftContext): DraftState {
        require(canonicalCode in state.offer) { "not on the table: $canonicalCode" }
        val players = state.players.toMutableList()
        val player = players[state.current]
        players[state.current] = player.copy(picks = player.picks + canonicalCode)
        val stock = state.stock.toMutableMap()
        stock[canonicalCode] = (stock[canonicalCode] ?: 1) - 1
        val picked = state.copy(
            players = players,
            stock = stock,
            pickCount = state.pickCount + 1,
            offer = emptyList(),
        )
        return nextTurn(picked, context)
    }

    /** The next player with room takes the table, or the draft ends. */
    fun nextTurn(state: DraftState, context: DraftContext): DraftState {
        if (state.everyoneFull) {
            return state.copy(phase = DraftPhase.FINISH, current = 0, offer = emptyList())
        }
        var next = state.current
        do {
            next = (next + 1) % state.players.size
        } while (state.players[next].isFull)
        return deal(state.copy(current = next), context).copy(phase = DraftPhase.PICK)
    }

    /** The first turn, once every identity is settled. */
    fun start(state: DraftState, context: DraftContext): DraftState {
        val first = state.players.indexOfFirst { !it.isFull }
        if (first < 0) {
            return state.copy(phase = DraftPhase.FINISH, current = 0)
        }
        return deal(state.copy(current = first), context).copy(phase = DraftPhase.PICK)
    }

    /**
     * A player whose offer came up empty: nothing legal is left for them,
     * which the stock check makes rare but not impossible once the others
     * have drafted. Their deck stops where it is, short, and the draft goes
     * on without them; the finish page then refuses to save it, by design.
     */
    fun skipCurrent(state: DraftState, context: DraftContext): DraftState {
        val players = state.players.toMutableList()
        val player = players[state.current]
        players[state.current] = player.copy(deckSize = player.cardCount)
        return nextTurn(state.copy(players = players), context)
    }

    private fun identityRandom(state: DraftState): Random =
        Random(state.seed + IDENTITY_STRIDE * (state.current + 1) + state.rolls)

    private const val BASIC_FACTION = "basic"
    private const val DEFAULT_COPY_LIMIT = 3
    private const val PICK_STRIDE = 1_000L
    private const val IDENTITY_STRIDE = 7_919L
}
