package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The campaign keywords, and the regex that finds them.
 *
 * These tests run on the JVM, and the bug they guard against did not: a JVM
 * inline flag in the pattern compiled happily here and threw on the device,
 * where matching is done by ICU. The flag is gone, and the case that needed it
 * — a keyword with an accent in it — is asserted directly so nobody reaches for
 * it again.
 */
class CampaignTextTest {

    @Test
    fun `an accented keyword is set apart`() {
        val styled = campaignText("Mettez en jeu la carte, face ÉCHOUÉ, et résolvez sa mise en place.")

        val spans = styled.spanStyles
        assertEquals("exactly one keyword should be styled", 1, spans.size)
        val span = spans.single()
        assertEquals("ÉCHOUÉ", styled.text.substring(span.start, span.end))
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals(FontStyle.Italic, span.item.fontStyle)
    }

    @Test
    fun `both faces and the plain keywords are found`() {
        for (keyword in listOf("ACHEVÉ", "ÉCHOUÉ", "ACHIEVED", "FAILED", "MISSION")) {
            val styled = campaignText("the $keyword here")
            assertEquals("$keyword was not styled", 1, styled.spanStyles.size)
        }
    }

    @Test
    fun `a keyword inside a longer word is left alone`() {
        // The boundary is what stops MISSIONS and SUBMISSION being marked up as
        // though they were the campaign's own term.
        for (text in listOf("MISSIONS", "SUBMISSION", "ÉCHOUÉE")) {
            assertTrue("$text should not be styled", campaignText(text).spanStyles.isEmpty())
        }
    }

    @Test
    fun `the pattern carries no JVM-only flag`() {
        // `(?U)` compiles on the JVM and throws under ICU, so a test that only
        // ran the pattern would have passed while the app crashed on a device.
        val source = KEYWORD_PATTERN_FOR_TEST.pattern
        assertTrue("the pattern must not use a JVM inline flag: $source", "(?U)" !in source)
    }
}
