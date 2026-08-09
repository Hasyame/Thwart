package com.hasyame.marvelchampions.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunPromptTest {

    @Test
    fun `answers once and never again`() {
        val prompt = FirstRunPrompt()

        assertTrue("the first ask on a new install opens the collection", prompt.consume(true))

        // Every later ask is a rebuilt composition asking the same question of
        // the same view model: a fold, an unfold, a rotation, a theme change,
        // an app language change. Reported from a Galaxy Z Fold 7, where the
        // player was dropped into the collection screen every time the phone
        // was opened and could not switch tabs afterwards.
        repeat(5) {
            assertFalse("configuration changes must not re-open it", prompt.consume(true))
        }
    }

    @Test
    fun `stays silent when it is not a first run`() {
        val prompt = FirstRunPrompt()

        repeat(3) { assertFalse(prompt.consume(false)) }
    }

    @Test
    fun `a first run detected late is still answered`() {
        // Startup is asynchronous, so the first composition can ask before the
        // database has been looked at. That must not spend the one answer.
        val prompt = FirstRunPrompt()

        assertFalse(prompt.consume(false))
        assertTrue(prompt.consume(true))
        assertFalse(prompt.consume(true))
    }
}
