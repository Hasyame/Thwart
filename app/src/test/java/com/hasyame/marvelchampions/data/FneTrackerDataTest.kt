package com.hasyame.marvelchampions.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Fear No Evil numbers, checked against the cards they were read from.
 *
 * These were transcribed by hand from photographs of the box, because MarvelCDB
 * has entered 68 of that pack's 276 cards and none of its villains. A hand
 * transcription is exactly the kind of data that rots quietly: a digit changed
 * by accident would have the tracker count down to a defeat that has not
 * happened, and the table would believe it, because a number on a screen looks
 * more certain than a card on a table.
 *
 * So every value is written out again here. The test is deliberately a second
 * copy: it is worth the duplication for the one campaign whose numbers no
 * server can check.
 */
@RunWith(RobolectricTestRunner::class)
class FneTrackerDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun template(): CampaignTemplate {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/fne.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(CampaignTemplate.serializer(), text)
    }

    @Test
    fun `every villain in the pool has its health`() {
        val tracker = template().tracker
        assertNotNull("Fear No Evil carries no tracker data", tracker)

        // The five subordinates the campaign deals, plus the finale.
        val expected = mapOf(
            "fne_villain_hammerhead" to listOf(14, 15, 16),
            "fne_villain_bullseye" to listOf(12, 16, 18),
            "fne_villain_electro" to listOf(15, 17, 20),
            "fne_villain_homme_pourpre" to listOf(13, 15, 17),
            "fne_villain_mary_typhoide" to listOf(10, 13),
            "s6_caid" to listOf(25, 25, 28, 28),
        )

        assertEquals(expected.keys, tracker!!.villains.keys)
        expected.forEach { (id, health) ->
            assertEquals(
                "health for $id",
                health,
                tracker.villains.getValue(id).map { it.value },
            )
        }
    }

    @Test
    fun `every subordinate the campaign can deal is one the tracker knows`() {
        // The pool is what the draw picks from. A villain in the pool with no
        // numbers is a scenario where the tracker silently gives up, which is
        // the state this whole exercise exists to end.
        val template = template()
        val missing = template.villainPool - template.tracker?.villains?.keys.orEmpty()

        assertEquals("villains dealt but not measured", emptyList<String>(), missing)
    }

    @Test
    fun `a subordinate is dealt two of its three stages, by difficulty`() {
        // The campaign deals each subordinate as a pair: I and II on a
        // standard campaign, II and III on an expert one, exactly as the
        // villain deck line in the setup says. The tracker counted from stage
        // I whatever was being played, so an expert table was counting down to
        // a number printed on a card not on the table.
        listOf(
            "fne_villain_hammerhead",
            "fne_villain_bullseye",
            "fne_villain_electro",
            "fne_villain_homme_pourpre",
        ).forEach { id ->
            assertEquals("standard stages for $id", listOf("I", "II"), stagesOn(id, "standard"))
            assertEquals("expert stages for $id", listOf("II", "III"), stagesOn(id, "expert"))
        }
    }

    @Test
    fun `the finale faces one card per difficulty, and turns it over`() {
        // Read off the cards. A1 and A2 are the two sides of Le Caid (1/22),
        // played on a standard campaign; B1 and B2 the two sides of (2/22), on
        // an expert one. The health is printed on the card rather than on the
        // side, so it does not change when he turns over: 25 per player for
        // the first card, 28 for the second.
        assertEquals(listOf("A1", "A2"), stagesOn("s6_caid", "standard"))
        assertEquals(listOf("B1", "B2"), stagesOn("s6_caid", "expert"))
    }

    @Test
    fun `Mary is not split by difficulty, because she flips in play`() {
        // Her two faces alternate at the end of every villain phase, so both
        // are on the table whatever the campaign is being played. Only the
        // face she starts on depends on the difficulty, and that is a setup
        // step rather than a number to count. Splitting her for symmetry with
        // the others would leave the tracker counting one face of a villain
        // that spends half the game on the other.
        val mary = "fne_villain_mary_typhoide"

        assertEquals(listOf("A", "B"), stagesOn(mary, "standard"))
        assertEquals(listOf("A", "B"), stagesOn(mary, "expert"))
    }

    /** The sides a run of this difficulty actually deals, as the tracker reads them. */
    private fun stagesOn(id: String, difficulty: String) =
        template().tracker!!.villains.getValue(id)
            .filter { it.onlyOn == null || it.onlyOn == difficulty }
            .map { it.stage }

    @Test
    fun `villain health is per player, as the cards print it`() {
        // Every Fear No Evil villain card carries the small figure beside its
        // health. Marking one flat would have the tracker offer a solo total at
        // a four-player table.
        val tracker = template().tracker!!
        val flat = tracker.villains.values.flatten().filterNot { it.perPlayer }

        assertEquals(emptyList<String>(), flat.map { "${it.name} ${it.stage}" })
    }

    @Test
    fun `the schemes carry their limit and their starting threat`() {
        val schemes = template().tracker!!.schemes

        // Limit, then starting threat, per player on both.
        val expected = mapOf(
            "s1_musee" to listOf(9 to 1),
            "s2_poursuite" to listOf(9 to 2),
            // The racket cards print flat numbers and no starting threat: what
            // they start with comes from the pressure on the job instead.
            "s3_racket" to listOf(10 to 0),
            "s4_raft" to listOf(11 to 2),
            "s5_rotatives" to listOf(9 to 1),
            // The finale turns over to a second scheme.
            "s6_caid" to listOf(11 to 3, 7 to 0),
        )

        assertEquals(expected.keys, schemes.keys)
        expected.forEach { (id, sides) ->
            assertEquals(
                "scheme numbers for $id",
                sides,
                schemes.getValue(id).map { it.value to it.startingThreat },
            )
        }
    }

    @Test
    fun `each scheme accelerates as its own card prints it`() {
        // Read off the 1B faces rather than assumed to be one a round, which
        // is what they were until the cards were to hand. La Poursuite takes
        // two, and the museum prints a star: its acceleration is the number of
        // ART attachments on the villain plus one, which the app cannot see,
        // so it adds nothing and says so in the briefing.
        val schemes = template().tracker!!.schemes

        fun accelerationOf(id: String) = schemes.getValue(id).first().let {
            if (it.escalationVariable) "variable" else "${it.escalation}"
        }

        assertEquals("variable", accelerationOf("s1_musee"))
        assertEquals("2", accelerationOf("s2_poursuite"))
        assertEquals("1", accelerationOf("s3_racket"))
        assertEquals("1", accelerationOf("s4_raft"))
        assertEquals("1", accelerationOf("s5_rotatives"))
        assertEquals("1", accelerationOf("s6_caid"))
    }

    @Test
    fun `a starred acceleration adds nothing rather than a guess`() {
        // The whole point of marking it: two per player is right at setup and
        // too low the moment a second ART lands, and a tracker that is quietly
        // low is worse than one that says it cannot know.
        val musee = template().tracker!!.schemes.getValue("s1_musee").first()

        assertTrue(musee.escalationVariable)
        assertEquals(0, musee.escalation)
    }

    @Test
    fun `the starting threats are the ones printed on the cards`() {
        // Confirmed against the 1B faces. These were read off a photograph of
        // the box before the cards were to hand, and were the numbers in the
        // app least worth trusting; four of the five were right.
        val schemes = template().tracker!!.schemes

        fun startOf(id: String) = schemes.getValue(id).first().startingThreat

        assertEquals(1, startOf("s1_musee"))
        assertEquals(2, startOf("s2_poursuite"))
        assertEquals(0, startOf("s3_racket"))
        assertEquals(2, startOf("s4_raft"))
        assertEquals(1, startOf("s5_rotatives"))
    }

    @Test
    fun `only the racket prints its numbers flat`() {
        // Every other job carries the small figure beside its threat limit, so
        // it scales with the table. The five racket cards carry none: ten
        // threat and one a round each, whoever is playing. Marking it per
        // player would have a three-handed game counting three times as fast
        // as it plays.
        val schemes = template().tracker!!.schemes
        val flat = schemes.filterValues { sides -> sides.none { it.perPlayer } }

        assertEquals(setOf("s3_racket"), flat.keys)
    }

    @Test
    fun `the racket job is counted once per player`() {
        // Fear No Evil deals this job a main scheme *to each player*: five
        // markets, everyone works a different one, and they finish at
        // different times. It is the only job in the campaign that does, and
        // the only one the tracker draws more than one counter for.
        val tracker = template().tracker!!

        assertEquals(listOf("s3_racket"), tracker.perPlayerSchemes)
        assertTrue("the racket scheme must exist to be counted", "s3_racket" in tracker.schemes)
    }

    @Test
    fun `a job under pressure starts its scheme where the table starts it`() {
        // Pressure is threat already on the scheme before anybody plays a
        // card: one token per box ticked, doubled on an expert campaign. The
        // tracker has to start there, or it counts to the right limit from the
        // wrong place all game.
        val schemes = template().tracker!!.schemes
        val fromPressure = schemes.filterValues { sides ->
            sides.any { it.startingThreatFrom != null }
        }

        // The two jobs whose extra threat lands on the main scheme. La
        // Poursuite's lands on the tanker, which is not a main scheme, and the
        // other two jobs are not paid in threat at all.
        assertEquals(setOf("s1_musee", "s3_racket"), fromPressure.keys)

        val racket = schemes.getValue("s3_racket").first().startingThreatFrom!!
        assertEquals(0, racket.amountFor(counterValue = 0, expert = false))
        assertEquals(2, racket.amountFor(counterValue = 2, expert = false))
        assertEquals(4, racket.amountFor(counterValue = 2, expert = true))
    }

    @Test
    fun `the racket job still tracks its villain`() {
        // The half that does work. Whichever subordinate is dealt to this job
        // is one of the five, and all five are measured.
        val template = template()
        val pool = template.villainPool

        assertTrue(pool.isNotEmpty())
        assertTrue(pool.all { it in template.tracker!!.villains })
    }

    @Test
    fun `no other campaign carries curated numbers`() {
        // The card database is the source for every campaign whose cards are in
        // it. Curating those by hand as well would be two sources for one fact,
        // and the hand-written one would rot first.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val curated = context.assets.list("campaigns").orEmpty()
            .filter { it.endsWith(".json") }
            .filter { name ->
                val text = context.assets.open("campaigns/$name").bufferedReader()
                    .use { it.readText() }
                json.decodeFromString(CampaignTemplate.serializer(), text).tracker != null
            }

        assertEquals(listOf("fne.json"), curated)
    }
}
