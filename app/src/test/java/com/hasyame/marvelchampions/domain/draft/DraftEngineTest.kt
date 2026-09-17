package com.hasyame.marvelchampions.domain.draft

import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidator
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.ADAM_WARLOCK
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.SPIDER_MAN
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.adamWarlock
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.context
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.mariaHill
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.player
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.spiderMan
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.spiderWoman
import com.hasyame.marvelchampions.domain.draft.DraftFixtures.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftEngineTest {

    private val context = context()

    // --- offers -------------------------------------------------------------------

    @Test
    fun `an offer has the size asked for, from the player's aspects and basic`() {
        val start = DraftEngine.start(state(player(0, spiderMan, listOf("justice"))), context)

        assertEquals(DraftPhase.PICK, start.phase)
        assertEquals(5, start.offer.size)
        start.offer.forEach { code ->
            assertTrue(code, context.pool.getValue(code).factionCode in setOf("justice", "basic"))
        }
    }

    @Test
    fun `the same state deals the same offer, and another seed deals another`() {
        val once = DraftEngine.start(state(player(0, spiderMan, listOf("justice"))), context)
        val again = DraftEngine.start(state(player(0, spiderMan, listOf("justice"))), context)
        val other = DraftEngine.start(state(player(0, spiderMan, listOf("justice")), seed = 7L), context)

        assertEquals(once.offer, again.offer)
        assertNotEquals(once.offer, other.offer)
        // Reopening a saved draft deals what was on the table before.
        assertEquals(once.offer, DraftEngine.deal(once.copy(offer = emptyList()), context).offer)
    }

    @Test
    fun `fewer cards than asked are offered when fewer are left`() {
        val pool = mapOf("a" to DraftFixtures.card("a", "justice"), "b" to DraftFixtures.card("b", "justice"))
        val small = state(player(0, spiderMan, listOf("justice")), pool = pool, copies = 3)
        val start = DraftEngine.start(small, context(pool))
        assertEquals(setOf("a", "b"), start.offer.toSet())
    }

    // --- picks --------------------------------------------------------------------

    @Test
    fun `a pick joins the deck, leaves the shelf, and the table moves on`() {
        val start = DraftEngine.start(state(player(0, spiderMan, listOf("justice"))), context)
        val taken = start.offer.first()

        val next = DraftEngine.pick(start, taken, context)

        assertEquals(listOf(taken), next.players[0].picks)
        assertEquals(2, next.stock[taken])
        assertEquals(1, next.pickCount)
        assertEquals("straight to the next pick", DraftPhase.PICK, next.phase)
        assertEquals(5, next.offer.size)
    }

    @Test
    fun `a card off the table cannot be picked`() {
        val start = DraftEngine.start(state(player(0, spiderMan, listOf("justice"))), context)
        val off = context.pool.keys.first { it !in start.offer && it.startsWith("jus") }
        assertTrue(runCatching { DraftEngine.pick(start, off, context) }.isFailure)
    }

    @Test
    fun `with two players the table passes straight to the next`() {
        val two = state(player(0, spiderMan, listOf("justice")), player(1, spiderWoman, listOf("aggression", "protection")))
        val start = DraftEngine.start(two, context)
        assertEquals(DraftPhase.PICK, start.phase)
        assertEquals(0, start.current)

        val afterFirst = DraftEngine.pick(start, start.offer.first(), context)
        assertEquals(DraftPhase.PICK, afterFirst.phase)
        assertEquals("player two's turn", 1, afterFirst.current)
        afterFirst.offer.forEach { code ->
            assertTrue(code, context.pool.getValue(code).factionCode in setOf("aggression", "protection", "basic"))
        }
    }

    @Test
    fun `a full player is skipped and the draft ends when everyone is full`() {
        // Player one wants 40 cards, player two 41: the second keeps drafting alone.
        val two = state(player(0, spiderMan, listOf("justice")), player(1, spiderMan.copy(heroCode = "x", identityTitle = "X"), listOf("justice"), deckSize = 41))
        val rules = DraftFixtures.rules + ("x" to spiderMan.copy(heroCode = "x", identityTitle = "X"))
        val ctx = context.copy(rules = rules, identities = context.identities + ("x" to DraftFixtures.identities.getValue(SPIDER_MAN)), signatureCards = context.signatureCards + ("x" to DraftFixtures.signatureInfo(spiderMan)))
        var state = DraftEngine.start(two, ctx)
        var turns = 0
        while (state.phase != DraftPhase.FINISH) {
            state = DraftEngine.pick(state, state.offer.first(), ctx)
            turns++
        }
        assertEquals(35 + 36, turns)
        assertTrue(state.players.all { it.isFull })
        assertEquals(DraftPhase.FINISH, state.phase)
    }

    // --- legality -----------------------------------------------------------------

    @Test
    fun `a title is never offered past its copy limit`() {
        val me = player(0, spiderMan, listOf("justice"))
        val ctx = context
        assertTrue(DraftEngine.canTake(me, ctx.pool.getValue("jus1"), ctx))
        val three = me.copy(picks = listOf("jus1", "jus1", "jus1"))
        assertFalse("a fourth copy", DraftEngine.canTake(three, ctx.pool.getValue("jus1"), ctx))
        val one = me.copy(picks = listOf("single"))
        assertFalse("deck_limit 1", DraftEngine.canTake(one, ctx.pool.getValue("single"), ctx))
    }

    @Test
    fun `a unique card is refused once, and the identity's own name is one of them`() {
        val me = player(0, spiderMan, listOf("justice"))
        assertFalse("Spider-Man cannot bring the Spider-Man ally", DraftEngine.canTake(me, context.pool.getValue("spidey"), context))
        val warlock = player(0, adamWarlock, DraftFixtures.ASPECTS)
        assertTrue(DraftEngine.canTake(warlock, context.pool.getValue("spidey"), context))
        assertFalse(DraftEngine.canTake(warlock.copy(picks = listOf("spidey")), context.pool.getValue("spidey"), context))
    }

    @Test
    fun `Adam Warlock takes a single copy of anything`() {
        val warlock = player(0, adamWarlock, DraftFixtures.ASPECTS)
        assertTrue(DraftEngine.canTake(warlock, context.pool.getValue("jus1"), context))
        assertFalse(DraftEngine.canTake(warlock.copy(picks = listOf("jus1")), context.pool.getValue("jus1"), context))
    }

    @Test
    fun `an identity's allowance widens the pool, up to its limit`() {
        val hill = player(0, mariaHill, listOf("justice"))
        val offered = DraftEngine.playerPool(state(hill), hill, context).map { it.canonicalCode }
        assertTrue("a S.H.I.E.L.D. support from Leadership", "shield1" in offered)
        assertFalse("a plain Leadership card", "lea1" in offered)
        // Three admitted, the fourth refused: the allowance is spent.
        val full = hill.copy(picks = listOf("shield1", "shield1", "shield1"))
        assertFalse(DraftEngine.canTake(full, context.pool.getValue("shield2"), context))
    }

    @Test
    fun `the synergy option keeps out what the identity cannot play`() {
        val me = player(0, spiderMan, listOf("justice"))
        val without = state(me, settings = DraftSettings(synergyOnly = false))
        val with = state(me, settings = DraftSettings(synergyOnly = true))
        assertTrue("rocket" in DraftEngine.playerPool(without, me, context).map { it.canonicalCode })
        assertFalse("rocket" in DraftEngine.playerPool(with, me, context).map { it.canonicalCode })
        val warlock = player(0, adamWarlock, DraftFixtures.ASPECTS)
        assertTrue("a Guardian may", "rocket" in DraftEngine.playerPool(state(warlock, settings = DraftSettings(synergyOnly = true)), warlock, context).map { it.canonicalCode })
    }

    @Test
    fun `one physical copy is drafted once, whoever takes it`() {
        val pool = DraftFixtures.pool()
        val two = state(player(0, spiderMan, listOf("justice")), player(1, spiderWoman, listOf("justice", "protection")), pool = pool, copies = 1)
        var state = DraftEngine.start(two, context)
        val takenByOne = mutableSetOf<String>()
        repeat(6) {
            if (state.current == 0) {
                takenByOne += state.offer.first()
            } else {
                assertTrue("player two never sees player one's copies", state.offer.none { it in takenByOne })
            }
            state = DraftEngine.pick(state, state.offer.first(), context)
        }
    }

    // --- whole drafts ----------------------------------------------------------------

    private fun draftToTheEnd(start: DraftState, ctx: DraftContext): DraftState {
        var state = DraftEngine.start(start, ctx)
        var guard = 0
        while (state.phase != DraftPhase.FINISH) {
            check(state.offer.isNotEmpty()) { "nothing to offer at pick ${state.pickCount}" }
            state = DraftEngine.pick(state, state.offer.first(), ctx)
            check(++guard < 500)
        }
        return state
    }

    private fun assertLegal(state: DraftState, ctx: DraftContext) {
        state.players.forEach { player ->
            val validation = DeckValidator.validate(ctx.rules.getValue(player.heroCode!!), player.aspects, player.slots(), ctx.cardInfo)
            assertEquals("player ${player.index + 1}: ${validation.problems}", emptyList<Any>(), validation.problems)
            assertEquals(player.deckSize, validation.totalCards)
        }
    }

    @Test
    fun `a whole draft ends in a legal deck, for every size and seed tried`() {
        listOf(40, 45, 50).forEach { size ->
            (1L..5L).forEach { seed ->
                val end = draftToTheEnd(state(player(0, spiderMan, listOf("justice"), deckSize = size), seed = seed), context)
                assertLegal(end, context)
            }
        }
    }

    @Test
    fun `Adam Warlock ends balanced across four aspects, one copy of each card`() {
        (1L..5L).forEach { seed ->
            val end = draftToTheEnd(state(player(0, adamWarlock, DraftFixtures.ASPECTS, deckSize = 44), seed = seed), context)
            assertLegal(end, context)
            assertEquals(end.players[0].picks.size, end.players[0].picks.toSet().size)
        }
    }

    @Test
    fun `Spider-Woman ends balanced across her two aspects`() {
        (1L..5L).forEach { seed ->
            val end = draftToTheEnd(state(player(0, spiderWoman, listOf("aggression", "protection"), deckSize = 41), seed = seed), context)
            assertLegal(end, context)
        }
    }

    @Test
    fun `four players share one shelf and all end legal`() {
        val four = state(
            player(0, spiderMan, listOf("justice")),
            player(1, spiderWoman, listOf("aggression", "leadership"), deckSize = 43),
            player(2, adamWarlock, DraftFixtures.ASPECTS, deckSize = 44),
            player(3, mariaHill, listOf("protection"), deckSize = 50),
            seed = 3L,
        )
        val end = draftToTheEnd(four, context)
        assertLegal(end, context)
        // Nothing was drafted past what was on the shelf.
        assertTrue(end.stock.values.all { it >= 0 })
        val drafted = end.players.flatMap { it.picks }.groupingBy { it }.eachCount()
        drafted.forEach { (code, count) -> assertTrue(code, count <= 3) }
    }

    // --- the shelf ---------------------------------------------------------------

    @Test
    fun `a shelf too small for the decks asked is reported before anyone draws`() {
        // Twelve titles, three copies each: thirty-six a deck may hold.
        val twelve = (1..12).associate { "jus$it" to DraftFixtures.card("jus$it", "justice") }
        val fine = state(player(0, spiderMan, listOf("justice"), deckSize = 40), pool = twelve, copies = 3)
        assertEquals(emptyList<DraftEngine.Shortfall>(), DraftEngine.shortfalls(fine, context(twelve)))

        // Ten titles with five copies each: fifty on the shelf, but a deck may
        // take three of a title, so thirty is what it can hold.
        val ten = (1..10).associate { "jus$it" to DraftFixtures.card("jus$it", "justice") }
        val short = state(player(0, spiderMan, listOf("justice"), deckSize = 40), pool = ten, copies = 5)
        assertEquals(listOf(DraftEngine.Shortfall(0, 35, 30)), DraftEngine.shortfalls(short, context(ten)))

        // Two players who each fit alone but not together: thirty-six each on
        // paper, thirty-six on the shelf between them.
        val shared = state(player(0, spiderMan, listOf("justice")), player(1, spiderWoman, listOf("justice", "protection")), pool = twelve, copies = 3)
        assertEquals(listOf(DraftEngine.Shortfall(1, 70, 36)), DraftEngine.shortfalls(shared, context(twelve)))

        // Adam Warlock holds one of each, so titles are what count for him.
        val warlock = state(player(0, adamWarlock, DraftFixtures.ASPECTS, deckSize = 50), copies = 3)
        assertEquals(emptyList<DraftEngine.Shortfall>(), DraftEngine.shortfalls(warlock, context))
        val fewTitles = (1..10).associate { "jus$it" to DraftFixtures.card("jus$it", "justice") } +
            (1..10).associate { "agg$it" to DraftFixtures.card("agg$it", "aggression") } +
            (1..10).associate { "lea$it" to DraftFixtures.card("lea$it", "leadership") } +
            (1..10).associate { "pro$it" to DraftFixtures.card("pro$it", "protection") }
        val warlockShort = state(player(0, adamWarlock, DraftFixtures.ASPECTS, deckSize = 50), pool = fewTitles, copies = 3)
        assertEquals(listOf(DraftEngine.Shortfall(0, 45, 40)), DraftEngine.shortfalls(warlockShort, context(fewTitles)))
    }

    @Test
    fun `a player with nothing legal left is skipped, short`() {
        val pool = mapOf("single" to DraftFixtures.card("single", "justice", limit = 1))
        val ctx = context(pool)
        val me = state(player(0, spiderMan, listOf("justice")), pool = pool, copies = 3)
        val start = DraftEngine.start(me, ctx)
        val after = DraftEngine.pick(start, "single", ctx)
        assertEquals("the one card is taken, nothing else is legal", emptyList<String>(), after.offer)
        val skipped = DraftEngine.skipCurrent(after, ctx)
        assertEquals(DraftPhase.FINISH, skipped.phase)
        assertEquals(6, skipped.players[0].deckSize)
    }

    // --- identities -------------------------------------------------------------

    @Test
    fun `identities are drawn from the collection, never twice at one table`() {
        val owned = listOf(SPIDER_MAN, ADAM_WARLOCK, "a", "b", "c", "d", "e")
        val taken = state(player(0, spiderMan, listOf("justice")), DraftPlayer(1))
        assertFalse(SPIDER_MAN in DraftEngine.availableHeroes(taken, owned))
        val choices = DraftEngine.randomHeroChoices(taken.copy(current = 1), owned)
        assertEquals(5, choices.size)
        assertFalse(SPIDER_MAN in choices)
        assertEquals("the same draw twice", choices, DraftEngine.randomHeroChoices(taken.copy(current = 1), owned))
        assertNotEquals("a new roll", choices, DraftEngine.randomHeroChoices(taken.copy(current = 1, rolls = 1), owned))
        assertNull(DraftEngine.randomHero(taken, listOf(SPIDER_MAN)))
    }

    @Test
    fun `aspects are the four, 'Pool with the Deadpool pack, none when the rule imposes them`() {
        assertEquals(DraftRules.CLASSIC_ASPECTS, DraftEngine.aspectChoices(spiderMan, poolAvailable = false))
        assertEquals(DraftRules.CLASSIC_ASPECTS + "pool", DraftEngine.aspectChoices(spiderMan, poolAvailable = true))
        assertEquals(emptyList<String>(), DraftEngine.aspectChoices(adamWarlock, poolAvailable = true))
        assertEquals(DraftRules.CLASSIC_ASPECTS, DraftEngine.imposedAspects(adamWarlock))
        assertNull(DraftEngine.imposedAspects(spiderWoman))
        val two = DraftEngine.randomAspects(state(), spiderWoman, poolAvailable = true)
        assertEquals(2, two.size)
        assertEquals(2, two.toSet().size)
        assertEquals(DraftRules.CLASSIC_ASPECTS, DraftEngine.randomAspects(state(), adamWarlock, poolAvailable = true))
    }
}
