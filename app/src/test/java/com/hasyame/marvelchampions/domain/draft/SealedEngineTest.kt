package com.hasyame.marvelchampions.domain.draft

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class SealedEngineTest {
    private val context = DraftFixtures.context()
    private fun state() = DraftFixtures.state(
        DraftFixtures.player(0, DraftFixtures.spiderMan, listOf("justice")),
        DraftFixtures.player(1, DraftFixtures.spiderWoman, listOf("aggression", "protection")),
        settings = DraftSettings(players = 2, sealed = true, synergyOnly = true),
    )

    @Test fun `deals sixty each without spending a physical copy twice`() {
        val before = state()
        val dealt = SealedEngine.deal(before, context)
        assertEquals(listOf(60, 60), dealt.sealedPools.map { it.size })
        context.initialStock.forEach { (code, count) ->
            assertEquals(count, dealt.stock.getValue(code) + dealt.sealedPools.flatten().count { it == code })
        }
        assertFalse(dealt.sealedPools[0].contains("rocket"))
        assertFalse(dealt.sealedPools[0].contains("spidey"))
        assertEquals(dealt, SealedEngine.deal(before, context))
        assertEquals(dealt, Json.decodeFromString<DraftState>(Json.encodeToString(DraftState.serializer(), dealt)))
    }

    @Test fun `selection is reversible and cannot take another players cards`() {
        val unopened = SealedEngine.deal(state(), context)
        assertEquals(unopened, SealedEngine.select(unopened, unopened.sealedPools[0].first(), true, context))
        assertEquals(unopened, SealedEngine.buildDeck(unopened))
        var opened = unopened
        repeat(6) { n ->
            opened = SealedEngine.openBooster(opened)
            assertEquals(n + 1, SealedEngine.openedBoosters(opened))
            assertEquals(10, opened.sealedPools[0].drop(n * 10).take(10).size)
            opened = Json.decodeFromString(Json.encodeToString(DraftState.serializer(), opened))
        }
        assertEquals(unopened.sealedPools, opened.sealedPools)
        assertEquals(0, opened.sealedOpened[1])
        val dealt = SealedEngine.buildDeck(opened)
        assertEquals(dealt, SealedEngine.openAll(unopened))
        assertEquals(dealt, SealedEngine.openAll(dealt))
        val code = dealt.sealedPools[0].first()
        val selected = SealedEngine.select(dealt, code, true, context)
        assertEquals(listOf(code), selected.players[0].picks)
        assertEquals(dealt, SealedEngine.select(selected, code, false, context))
        assertEquals(dealt, SealedEngine.select(dealt, "not-dealt", true, context))
        assertEquals(dealt.stock, selected.stock)
        assertEquals(dealt.sealedPools, selected.sealedPools)
    }

    @Test fun `limited inventory does not invent copies and Adam gets singletons`() {
        val adam = DraftFixtures.player(0, DraftFixtures.adamWarlock, DraftFixtures.ASPECTS)
        val dealt = SealedEngine.deal(DraftFixtures.state(adam, settings = DraftSettings(sealed = true)), context)
        assertEquals(60, dealt.sealedPools.single().size)
        assertEquals(60, dealt.sealedPools.single().toSet().size)
        val tiny = state().copy(stock = mapOf("bas1" to 1))
        assertEquals(1, SealedEngine.deal(tiny, context).sealedPools.flatten().size)
    }
}
