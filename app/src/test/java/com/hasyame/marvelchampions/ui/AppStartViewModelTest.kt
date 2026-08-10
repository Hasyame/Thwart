package com.hasyame.marvelchampions.ui

import com.hasyame.marvelchampions.data.repository.FirstRunOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a first launch lands, per what the install actually has in it.
 *
 * There are two empty installs and they are not the same. One has the cards
 * bundled and needs the player to say which boxes they own; the other — the
 * build F-Droid produces, with no card snapshot in the APK — has no packs to
 * tick at all until a sync has run.
 *
 * Both used to open the collection screen. On the second the screen read
 * "0 of 0 packs owned" over an empty list, which said nothing about what was
 * wrong or what to do, and the one control that fixes it lives in Settings.
 */
class AppStartViewModelTest {

    private fun landing(outcome: FirstRunOutcome) = StartupState.Ready(
        openCollectionFirst = outcome == FirstRunOutcome.SEEDED,
        startInSettings = outcome != FirstRunOutcome.ALREADY_READY,
    )

    @Test
    fun `a seeded install is asked which packs it owns`() {
        val ready = landing(FirstRunOutcome.SEEDED)

        assertTrue("should open the collection", ready.openCollectionFirst)
        assertTrue("collection sits over Settings", ready.startInSettings)
    }

    @Test
    fun `a build with no bundled cards opens Settings, not an empty collection`() {
        val ready = landing(FirstRunOutcome.NEEDS_SYNC)

        assertFalse(
            "there are no packs to tick until a sync has run",
            ready.openCollectionFirst,
        )
        assertTrue("Settings holds the only button that helps", ready.startInSettings)
    }

    @Test
    fun `an install that already has cards is left where it was`() {
        val ready = landing(FirstRunOutcome.ALREADY_READY)

        assertFalse(ready.openCollectionFirst)
        assertFalse(ready.startInSettings)
    }

    @Test
    fun `every outcome is accounted for`() {
        // A new FirstRunOutcome must decide where it lands rather than
        // inheriting whatever the last branch happened to do.
        assertEquals(3, FirstRunOutcome.entries.size)
    }
}
