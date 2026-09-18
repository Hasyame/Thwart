package com.hasyame.marvelchampions.domain.draft

import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidator
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import kotlin.random.Random

/**
 * The draft, pack by pack. Pure: every function takes the state and gives
 * the next one, and every draw comes from the seed, so a draft written down
 * and reopened finds the same packs, and a test gets the same draft twice.
 *
 * A draft is a stack of packs built in advance. Once the table is settled,
 * the engine builds each player one pack per card they still need, from
 * the collection: a copy owned once goes into one pack only, a card a deck
 * may hold once appears once across a player's packs, and no pack holds
 * the same title twice. The players then open their packs one at a time,
 * taking a single card from each; what they leave goes back on the shelf.
 * A pack is always full: when the shelf could not fill every pack, the
 * engine builds as many full ones as it can, and builds full packs again
 * from what came back once those are opened, so the draft carries on.
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
     * The cards a player's packs may be built from, out of what is left on
     * the shelf: the chosen aspects and basic, plus whatever the identity's
     * own allowances admit; never another identity's cards; and, when asked,
     * nothing the identity cannot play.
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
     * Copies of [card] one deck may hold: one of a unique card or of a card
     * printed "max 1 per deck", whatever the shelf holds of it, and the
     * copy limit that applies to this identity otherwise.
     */
    fun copyLimit(player: DraftPlayer, card: DraftCard, context: DraftContext): Int {
        val rules = context.rules[player.heroCode] ?: return 0
        return when {
            card.info.isUnique -> 1
            else -> rules.copyLimitOverride ?: card.info.deckLimit ?: DEFAULT_COPY_LIMIT
        }
    }

    /**
     * Copies of [card] one deck can still draft: the shelf's, capped by the
     * copy limit. A Core Set alone has copies enough for two Justice decks
     * on paper, and not once each deck may take three of a title at most,
     * which is what this caps.
     */
    fun usableCopies(player: DraftPlayer, card: DraftCard, stock: Map<String, Int>, context: DraftContext): Int =
        minOf(stock[card.canonicalCode] ?: 0, copyLimit(player, card, context))

    /**
     * Whether the shelf can fill every deck: each player's own pool, counted
     * as copies their deck may hold, against what they still need; then
     * everybody's needs against the shelf as a whole, since one copy drafted
     * by one player is one copy fewer for the rest. An estimate on the safe
     * side of "enough", not a proof; a pack never holds a card its player
     * may not take, whatever this says.
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

    // --- the packs ----------------------------------------------------------------

    /**
     * Builds every player their packs: one per card still to take, of
     * [DraftSettings.offerSize] distinct titles each, from the shelf.
     *
     * The shelf is one for the table, so the packs are built a round at a
     * time, one for each player who still needs one, rather than a whole
     * player's at once: with three players on one Core Set, the first would
     * otherwise take the shelf and the third get nothing. A title goes into
     * a player's packs no more times than their deck may hold it, counting
     * what they have already picked and what waits in their other packs; a
     * card a deck may hold once therefore appears once, whatever the shelf
     * holds of it. Every card put in a pack leaves the shelf until the pack
     * is opened.
     *
     * A pack is always full. When the shelf has too few titles left to fill
     * one, the player gets fewer packs, never a smaller one, and [deal]
     * builds again from what opened packs give back. The one exception is a
     * player with no pack at all and a shelf that cannot fill one even so:
     * they open what there is rather than nothing. Seeded by the build
     * count, so a draft reopened finds the same packs.
     *
     * [fit] narrows what may go into a player's packs beyond the deck's
     * limits; [deal] uses it to build, for a deck down to its last aspect,
     * packs of cards that deck can take now.
     */
    fun buildPacks(
        state: DraftState,
        context: DraftContext,
        fit: (DraftPlayer, DraftCard) -> Boolean = { _, _ -> true },
    ): DraftState {
        val random = Random(state.seed + BUILD_STRIDE * (state.builds + 1))
        val stock = state.stock.toMutableMap()
        val packs = state.players.indices.map { state.packsOf(it).toMutableList() }
        val held = state.players.map { player ->
            (player.picks + packs[player.index].flatten()).groupingBy { it }.eachCount().toMutableMap()
        }
        // Whose packs are short, and how many each still needs.
        val wanted = state.players.map { (it.remaining - packs[it.index].size).coerceAtLeast(0) }.toMutableList()
        var building = true
        while (building) {
            building = false
            state.players.forEach { player ->
                val at = player.index
                if (wanted[at] <= 0) {
                    return@forEach
                }
                val candidates = playerPool(state.copy(stock = stock), player, context)
                    .filter { card -> (held[at][card.canonicalCode] ?: 0) < copyLimit(player, card, context) && fit(player, card) }
                if (candidates.isEmpty()) {
                    // Nothing on the shelf this player may take: no more packs for them.
                    wanted[at] = 0
                    return@forEach
                }
                if (candidates.size < state.settings.offerSize && packs[at].isNotEmpty()) {
                    // Too few titles left for a full pack: this player opens
                    // what they have, and the shelf is fuller by then.
                    wanted[at] = 0
                    return@forEach
                }
                val pack = candidates.shuffled(random).take(state.settings.offerSize).map { it.canonicalCode }
                pack.forEach { code ->
                    stock[code] = (stock[code] ?: 1) - 1
                    held[at][code] = (held[at][code] ?: 0) + 1
                }
                packs[at] += pack
                wanted[at] -= 1
                building = true
            }
        }
        return state.copy(stock = stock, packs = packs.map { it.toList() }, builds = state.builds + 1)
    }

    // --- picks ----------------------------------------------------------------

    /**
     * Whether the deck can still be finished legally with [card] in it.
     *
     * The validator judges the deck as it would stand, and anything it
     * reports beyond the size and the balance rules out the card: a fourth
     * copy, a second unique, an allowance spent, Adam Warlock's second copy
     * of anything. Size is the point of drafting, and balance is judged by
     * whether the picks left can still even the aspects out: a card is
     * refused when taking it would leave more deficit than picks.
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

    /** Whether the current player may take [canonicalCode] from the pack on the table. */
    fun takeable(state: DraftState, canonicalCode: String, context: DraftContext): Boolean {
        val card = context.pool[canonicalCode] ?: return false
        return canonicalCode in state.offer && canTake(state.currentPlayer, card, context)
    }

    /**
     * Opens the current player's next pack onto the table. When they have
     * none left and cards still to take, the packs are built again from the
     * shelf, which by now holds what opened packs gave back; when even that
     * gives them nothing, the table is left empty and the deck stops short.
     *
     * When what they have holds nothing the deck may take, as when the
     * packs are down to one aspect and the deck needs another to balance,
     * the packs go back to the shelf and are built once more, this time
     * of cards the deck can take as it stands; if even the shelf holds
     * none, the table is left empty and the deck stops short.
     */
    fun deal(state: DraftState, context: DraftContext): DraftState {
        val at = state.current
        var next = state
        if (next.packsOf(at).isEmpty() && !next.currentPlayer.isFull) {
            next = buildPacks(next, context)
        }
        if (!useful(next, at, context)) {
            next = buildPacks(returnPacks(next, at), context) { player, card ->
                player.index != at || canTake(player, card, context)
            }
            if (!useful(next, at, context)) {
                return returnPacks(next, at).copy(offer = emptyList())
            }
        }
        return next.copy(offer = next.packsOf(at).first())
    }

    /** Whether any pack of the player holds a card their deck may take. */
    private fun useful(state: DraftState, at: Int, context: DraftContext): Boolean {
        val player = state.players[at]
        return state.packsOf(at).any { pack ->
            pack.any { code -> context.pool[code]?.let { canTake(player, it, context) } == true }
        }
    }

    /** A player's packs, every card back on the shelf. */
    private fun returnPacks(state: DraftState, at: Int): DraftState = state.copy(
        stock = giveBack(state.stock, state.packsOf(at).flatten()),
        packs = state.packs.mapIndexed { index, packs -> if (index == at) emptyList() else packs },
    )

    /**
     * The current player takes [canonicalCode] from the pack. The rest of
     * the pack goes back on the shelf, the pack is spent, and the turn
     * passes to the next player who still has room; when nobody has room
     * the draft is over.
     */
    fun pick(state: DraftState, canonicalCode: String, context: DraftContext): DraftState {
        require(canonicalCode in state.offer) { "not on the table: $canonicalCode" }
        require(takeable(state, canonicalCode, context)) { "not allowed in this deck: $canonicalCode" }
        val players = state.players.toMutableList()
        val player = players[state.current]
        players[state.current] = player.copy(picks = player.picks + canonicalCode)
        val picked = state.copy(
            players = players,
            stock = giveBack(state.stock, state.offer - canonicalCode),
            packs = spendPack(state),
            pickCount = state.pickCount + 1,
            offer = emptyList(),
        )
        return nextTurn(picked, context)
    }

    /**
     * The pack on the table holds nothing the player may take, as can
     * happen when only one aspect is left to balance: the whole pack goes
     * back on the shelf and the next one is opened.
     */
    fun skipPack(state: DraftState, context: DraftContext): DraftState {
        val skipped = state.copy(
            stock = giveBack(state.stock, state.offer),
            packs = spendPack(state),
            offer = emptyList(),
        )
        return nextTurn(skipped, context)
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

    /** The first turn, once every identity is settled: the packs, then the first one open. */
    fun start(state: DraftState, context: DraftContext): DraftState {
        val first = state.players.indexOfFirst { !it.isFull }
        if (first < 0) {
            return state.copy(phase = DraftPhase.FINISH, current = 0)
        }
        val built = buildPacks(state.copy(packs = state.players.map { emptyList() }), context)
        return deal(built.copy(current = first), context).copy(phase = DraftPhase.PICK)
    }

    /**
     * A player whose shelf has run dry: no pack could be built for them,
     * which the stock check makes rare but not impossible once the others
     * have drafted. Their deck stops where it is, short, and the draft goes
     * on without them; the finish page then refuses to save it, by design.
     */
    fun skipCurrent(state: DraftState, context: DraftContext): DraftState {
        val players = state.players.toMutableList()
        val player = players[state.current]
        players[state.current] = player.copy(deckSize = player.cardCount)
        return nextTurn(state.copy(players = players, stock = giveBack(state.stock, state.offer), offer = emptyList()), context)
    }

    /** The cards of an opened pack that were not taken, back on the shelf. */
    private fun giveBack(stock: Map<String, Int>, cards: List<String>): Map<String, Int> {
        val next = stock.toMutableMap()
        cards.forEach { next[it] = (next[it] ?: 0) + 1 }
        return next
    }

    /** The current player's packs without the one on the table. */
    private fun spendPack(state: DraftState): List<List<List<String>>> =
        state.packs.mapIndexed { index, packs -> if (index == state.current) packs.drop(1) else packs }

    private fun identityRandom(state: DraftState): Random =
        Random(state.seed + IDENTITY_STRIDE * (state.current + 1) + state.rolls)

    private const val BASIC_FACTION = "basic"
    private const val DEFAULT_COPY_LIMIT = 3
    private const val BUILD_STRIDE = 1_000L
    private const val IDENTITY_STRIDE = 7_919L
}
