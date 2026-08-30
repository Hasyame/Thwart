package com.hasyame.marvelchampions.data

import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import com.hasyame.marvelchampions.data.sync.CardUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app decides to ask about when it opens.
 *
 * The question has to be worth interrupting somebody for: a player opening the
 * app wants to play, not to be told about a pack they already own.
 */
class CardUpdateCheckerTest {

    private fun remote(code: String, name: String, known: Int) =
        PackDto(id = 1, code = code, name = name, position = 1, available = "", known = known, total = known)

    private fun local(code: String, known: Int) = PackEntity(
        code = code,
        marvelCdbId = 1,
        position = 1,
        available = "",
        known = known,
        total = known,
        type = "core",
        wave = 1,
    )

    @Test
    fun `a pack this device has never seen is offered`() {
        val updates = CardUpdateChecker.findUpdates(
            remote = listOf(remote("core", "Core Set", 100), remote("sm", "Sinister Motives", 200)),
            local = listOf(local("core", 100)),
            dismissed = emptySet(),
        )

        assertEquals(1, updates.size)
        assertEquals("Sinister Motives", updates.first().name)
        assertTrue(updates.first().isNewPack)
    }

    /**
     * MarvelCDB fills packs in over time: a set can be here already and still
     * be missing half its cards, which is invisible without comparing counts.
     */
    @Test
    fun `a pack that has gained cards is offered too`() {
        val updates = CardUpdateChecker.findUpdates(
            remote = listOf(remote("core", "Core Set", 120)),
            local = listOf(local("core", 100)),
            dismissed = emptySet(),
        )

        assertEquals(1, updates.size)
        assertEquals(false, updates.first().isNewPack)
    }

    @Test
    fun `nothing is said when this device is up to date`() {
        val updates = CardUpdateChecker.findUpdates(
            remote = listOf(remote("core", "Core Set", 100)),
            local = listOf(local("core", 100)),
            dismissed = emptySet(),
        )

        assertEquals(emptyList<Any>(), updates)
    }

    /** A local count ahead of the remote one is not a reason to download. */
    @Test
    fun `a pack with more here than there is left alone`() {
        val updates = CardUpdateChecker.findUpdates(
            remote = listOf(remote("core", "Core Set", 90)),
            local = listOf(local("core", 100)),
            dismissed = emptySet(),
        )

        assertEquals(emptyList<Any>(), updates)
    }

    @Test
    fun `saying no once is not asked again`() {
        val updates = CardUpdateChecker.findUpdates(
            remote = listOf(remote("sm", "Sinister Motives", 200)),
            local = emptyList(),
            dismissed = setOf("sm"),
        )

        assertEquals(emptyList<Any>(), updates)
    }

    /** Turning one pack down does not silence the next one to be released. */
    @Test
    fun `a pack released after the refusal is still offered`() {
        val updates = CardUpdateChecker.findUpdates(
            remote = listOf(
                remote("sm", "Sinister Motives", 200),
                remote("mg", "Mutant Genesis", 180),
            ),
            local = emptyList(),
            dismissed = setOf("sm"),
        )

        assertEquals(listOf("Mutant Genesis"), updates.map { it.name })
    }

    @Test
    fun `several are listed in a settled order`() {
        val updates = CardUpdateChecker.findUpdates(
            remote = listOf(
                remote("sm", "Sinister Motives", 200),
                remote("aoa", "Age of Apocalypse", 190),
                remote("mg", "Mutant Genesis", 180),
            ),
            local = emptyList(),
            dismissed = emptySet(),
        )

        assertEquals(
            listOf("Age of Apocalypse", "Mutant Genesis", "Sinister Motives"),
            updates.map { it.name },
        )
    }
}
