package com.hasyame.marvelchampions.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftNamingTest {

    @Test
    fun `the name is the identity folded, the aspect code, and a two-digit suffix`() {
        assertEquals("DRAFT-SPIDERMAN-AGGRESSION-01", DraftNaming.defaultName("Spider-Man", listOf("aggression"), DraftFixtures.spiderMan, emptyList()))
        assertEquals("DRAFT-MSMARVEL-POOL-01", DraftNaming.defaultName("Ms. Marvel", listOf("pool"), DraftFixtures.spiderMan, emptyList()))
        assertEquals("DRAFT-NEBULA-JUSTICE-01", DraftNaming.defaultName("Nébula", listOf("justice"), DraftFixtures.spiderMan, emptyList()))
        assertEquals("DRAFT-SPDR-PROTECTION-01", DraftNaming.defaultName("SP//dr", listOf("protection"), DraftFixtures.spiderMan, emptyList()))
    }

    @Test
    fun `the suffix steps past names already taken, whatever their case`() {
        val taken = listOf("DRAFT-SPIDERMAN-JUSTICE-01", "draft-spiderman-justice-02", "Something else")
        assertEquals("DRAFT-SPIDERMAN-JUSTICE-03", DraftNaming.defaultName("Spider-Man", listOf("justice"), DraftFixtures.spiderMan, taken))
    }

    @Test
    fun `imposed aspects read MULTI, chosen pairs read both in alphabetical order`() {
        assertEquals("DRAFT-ADAMWARLOCK-MULTI-01", DraftNaming.defaultName("Adam Warlock", DraftRules.CLASSIC_ASPECTS, DraftFixtures.adamWarlock, emptyList()))
        assertEquals("DRAFT-SPIDERWOMAN-JUSTICE-PROTECTION-01", DraftNaming.defaultName("Spider-Woman", listOf("protection", "justice"), DraftFixtures.spiderWoman, emptyList()))
    }
}
