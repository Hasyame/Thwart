package com.hasyame.marvelchampions.domain.play

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.randomizer.RandomizerPools
import com.hasyame.marvelchampions.domain.randomizer.ScenarioRandomizer
import com.hasyame.marvelchampions.domain.randomizer.SetRef
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * Fear No Evil played outside its campaign, read from the bundled template:
 * the six scenarios, the five subordinates, the codes a game carries, the
 * tracker's numbers and the briefing. The web mirrors the codes.
 */
@RunWith(RobolectricTestRunner::class)
class FearNoEvilBoxTest {

    private val box: FearNoEvilBox by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/fne.json").bufferedReader().use { it.readText() }
        FearNoEvilBox(Json { ignoreUnknownKeys = true }.decodeFromString(CampaignTemplate.serializer(), text).expanded())
    }

    @Test
    fun `codes split and compose as the versus scenarios do`() {
        assertEquals("fne_s1_musee", FearNoEvil.codeOf("s1_musee"))
        assertEquals("fne_s1_musee__fne_villain_electro", FearNoEvil.compose("fne_s1_musee", "fne_villain_electro"))
        assertEquals("fne_s1_musee" to "fne_villain_electro", FearNoEvil.split("fne_s1_musee__fne_villain_electro"))
        assertEquals("fne_s6_caid" to null, FearNoEvil.split("fne_s6_caid"))
        assertEquals("s1_musee", FearNoEvil.scenarioIdOf("fne_s1_musee__fne_villain_electro"))
        assertTrue(FearNoEvil.isFne("fne_s1_musee"))
        assertFalse(FearNoEvil.isFne("rhino"))
        assertFalse(FearNoEvil.isFne(null))
    }

    @Test
    fun `five jobs draw a subordinate and the finale does not`() {
        val scenarios = box.scenarios("en")
        assertEquals(6, scenarios.size)
        assertEquals(5, scenarios.count { it.needsVillain })
        val finale = scenarios.single { !it.needsVillain }
        assertEquals("fne_s6_caid", finale.code)
        assertEquals("Kingpin", finale.name)
        assertEquals("Art Museum Heist", scenarios.first().name)
        assertEquals("Cambriolage du Musée d'Art", box.scenarios("fr").first().name)
    }

    @Test
    fun `the subordinates are the template's five, named in the reader's language`() {
        val villains = box.villains("en")
        assertEquals(5, villains.size)
        assertEquals("Purple Man", villains.single { it.id == "fne_villain_homme_pourpre" }.name)
        assertEquals("L'Homme Pourpre", box.villains("fr").single { it.id == "fne_villain_homme_pourpre" }.name)
    }

    @Test
    fun `every code a game can carry has a name`() {
        val names = box.names("en")
        assertEquals("Art Museum Heist", names["fne_s1_musee"])
        assertEquals("Art Museum Heist : Electro", names["fne_s1_musee__fne_villain_electro"])
        assertEquals("Kingpin", names["fne_s6_caid"])
        assertNull("the finale takes no subordinate", names["fne_s6_caid__fne_villain_electro"])
        // Six scenarios, and five jobs by five subordinates.
        assertEquals(6 + 25, names.size)
    }

    @Test
    fun `the tracker counts the villain's two stages for the difficulty, and the job's scheme`() {
        val standard = box.encounterSetup("fne_s1_musee__fne_villain_electro", players = 2, expert = false, localeCode = "en")
        assertEquals(listOf("I", "II"), standard.villain.map { it.stage })
        assertEquals(listOf("Electro", "Electro"), standard.villain.map { it.name })
        assertEquals(15, standard.villain.first().value)
        assertEquals(1, standard.scheme.size)
        assertEquals(2, standard.players)

        val expert = box.encounterSetup("fne_s1_musee__fne_villain_electro", players = 1, expert = true, localeCode = "en")
        assertEquals(listOf("II", "III"), expert.villain.map { it.stage })

        val kingpin = box.encounterSetup("fne_s6_caid", players = 1, expert = false, localeCode = "en")
        assertEquals(listOf("A1", "A2"), kingpin.villain.map { it.stage })

        // The racket deals a scheme to each player.
        assertEquals(3, box.encounterSetup("fne_s3_racket__fne_villain_bullseye", players = 3, expert = false, localeCode = "en").schemeCopies)

        assertFalse("a job without its villain has nothing to count", box.encounterSetup("fne_s1_musee", 1, false, "en").villain.isNotEmpty())
    }

    @Test
    fun `the briefing names the villain and its stages for the difficulty`() {
        val steps = box.briefing("fne_s1_musee__fne_villain_electro", difficulty = "Standard I", localeCode = "en")
        assertTrue(steps.toString(), steps.any { it.contains("Underling villain: \"Electro\"") })
        assertTrue(steps.toString(), steps.any { it.contains("\"Electro\" (I) and (II)") })
        assertFalse(steps.toString(), steps.any { it.contains("Hammerhead") })
        // As a one-off game names it: by set, not by the campaign's word.
        val expert = box.briefing("fne_s1_musee__fne_villain_electro", difficulty = "Expert II", localeCode = "en")
        assertTrue(expert.toString(), expert.any { it.contains("\"Electro\" (II) and (III)") })
        assertFalse("no braces are left in the prose", steps.any { it.contains("{") })
        // The environment card is the campaign's pressure board, not a one-off game's.
        assertFalse(steps.toString(), steps.any { it.contains("environment") })
        assertTrue(steps.toString(), steps.any { it.contains("Main scheme deck") })
    }

    @Test
    fun `the randomiser draws a subordinate with a job, and leaves the finale alone`() {
        val pools = RandomizerPools(
            scenarios = listOf(SetRef("fne_s1_musee", "fne"), SetRef("fne_s6_caid", "fne")),
            villainChoices = mapOf("fne_s1_musee" to listOf("fne_villain_electro", "fne_villain_bullseye")),
        )
        val drawn = ScenarioRandomizer.withVillain("fne_s1_musee", pools, Random(1))
        assertTrue(drawn, drawn.startsWith("fne_s1_musee__fne_villain_"))
        assertEquals("fne_s6_caid", ScenarioRandomizer.withVillain("fne_s6_caid", pools, Random(1)))
        assertEquals("rhino", ScenarioRandomizer.withVillain("rhino", pools, Random(1)))
        assertFalse(FearNoEvil.needsVillain(drawn, pools.villainChoices))
        assertTrue(FearNoEvil.needsVillain("fne_s1_musee", pools.villainChoices))
    }
}
