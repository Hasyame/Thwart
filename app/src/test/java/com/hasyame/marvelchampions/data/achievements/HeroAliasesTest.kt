package com.hasyame.marvelchampions.data.achievements

import com.hasyame.marvelchampions.data.db.dao.HeroCardRef
import com.hasyame.marvelchampions.domain.achievements.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HeroAliasesTest {
    private fun fact(vararg codes: String) = PlayFact(
        id = "synthetic", playedAt = 1L, scenarioKey = "scenario",
        level = DifficultyLevel.EXPERT, won = true, players = codes.size,
        seats = codes.mapIndexed { index, code -> Seat(code, listOf("justice"), index == 0) },
    )

    @Test
    fun `set-based games fill the canonical coverage cell without rewriting their facts`() {
        val original = fact("hero_set")
        val resolved = HeroAliases(listOf(HeroCardRef("hero_card", "pack", "hero_set"))).resolve(original)
        val state = AchievementDerivation.derive(DeriveInput(
            definitions = emptyList(), definitionsVersion = 1,
            catalogue = Catalogue(listOf(HeroRef("hero_card", "pack")), listOf(ScenarioRef("scenario", "pack"))),
            ownedPacks = setOf("pack"), facts = listOf(resolved), runs = emptyList(),
        ))
        assertEquals(Completion(1, 1), state.completion.owned)
        assertEquals("hero_card", state.cells.single().heroCode)
        assertEquals(DifficultyLevel.EXPERT, state.cells.single().bestLevelWon)
        assertEquals("hero_set", original.owner!!.heroCode)
    }

    @Test
    fun `unknown ambiguous and canonical codes are preserved`() {
        val aliases = HeroAliases(listOf(
            HeroCardRef("one", "pack", "multiple"), HeroCardRef("two", "pack", "multiple"),
            HeroCardRef("three", "pack", "one"), HeroCardRef("four", "pack", null),
        ))
        val original = fact("one", "multiple", "unknown", "")
        assertEquals(original, aliases.resolve(original))
    }

    @Test
    fun `every seat resolves while ownership aspects and duplicate locale rows are preserved`() {
        val card = HeroCardRef("card", "pack", "set")
        val original = fact("set", "set")
        val resolved = HeroAliases(listOf(card, card)).resolve(original)
        assertEquals(listOf("card", "card"), resolved.seats.map { it.heroCode })
        assertEquals(original.seats.map { it.isOwner }, resolved.seats.map { it.isOwner })
        assertEquals(original.seats.map { it.aspects }, resolved.seats.map { it.aspects })
    }
}
