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
            "s6_caid" to listOf(25, 28),
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
    fun `every scheme accelerates by one a round, per player`() {
        // What every main scheme in the card database carries: escalation 1,
        // and not fixed, which in MarvelCDB's spelling means multiply. Without
        // it the tracker would hold the threat still between rounds and call
        // a scheme safe long after it was not.
        val sides = template().tracker!!.schemes.values.flatten()

        assertEquals(listOf(1), sides.map { it.escalation }.distinct())
        assertEquals(listOf(true), sides.map { it.escalationPerPlayer }.distinct())
    }

    @Test
    fun `the racket job is knowingly left uncounted`() {
        // Not for want of numbers: its four cards all read 10 threat, no
        // per-player icon, plus one a round. The problem is that Fear No Evil
        // deals a main scheme *to each player*, so a three-handed game has
        // three of them going at once, each completing on its own.
        //
        // The tracker counts one main scheme. Folding three into one would
        // report a limit nobody is playing to, and a single bar cannot say
        // which player's scheme is nearly done. Counting nothing is the honest
        // answer until the tracker can hold more than one, and the villain is
        // still counted for this job like any other.
        val schemes = template().tracker!!.schemes

        assertTrue(
            "s3_racket now has a scheme; the tracker must handle one per player first",
            "s3_racket" !in schemes,
        )
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
