package com.hasyame.marvelchampions.data.bgg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The escaping that goes around a BoardGameGeek password.
 *
 * BGG has no token and no OAuth, so logging a play posts the account password
 * inside a JSON body this app builds by hand — deliberately, to keep the
 * password out of any intermediate object. Building JSON by hand means owning
 * the escaping, and the first version owned only half of it: the quote and the
 * backslash, but none of the control characters a JSON string may not carry
 * raw. A password with a tab in it produced a body BGG could only reject, and
 * the player was told their credentials were wrong.
 *
 * Each case is checked by parsing the result back, which is the only assertion
 * that matters: whatever went in has to come out unchanged.
 */
class EscapeJsonTest {

    private fun roundTrip(value: String): String =
        Json.parseToJsonElement("\"${value.escapeJson()}\"").jsonPrimitive.content

    @Test
    fun `leaves an ordinary password alone`() {
        assertEquals("hunter2", roundTrip("hunter2"))
    }

    @Test
    fun `survives the quote and the backslash`() {
        assertEquals("""a"b\c""", roundTrip("""a"b\c"""))
    }

    @Test
    fun `survives a line break, a tab and the rest of the named escapes`() {
        val awkward = "a\nb\rc\td\be\u000Cf"

        assertEquals(awkward, roundTrip(awkward))
    }

    @Test
    fun `survives a control character with no name of its own`() {
        // U+0001 has no short escape, so it has to go out as \\u0001 rather
        // than as itself. Raw, it is not valid inside a JSON string at all.
        assertEquals("a\u0001b", roundTrip("a\u0001b"))
    }

    @Test
    fun `leaves accented and non-Latin text as it is`() {
        // Above U+001F nothing needs escaping, and a password is whatever
        // somebody typed — including an accent or an emoji.
        val accented = "mot-de-passé-Ω-🃏"

        assertEquals(accented, roundTrip(accented))
    }

    @Test
    fun `escapes rather than emits a raw control character`() {
        // The round trip alone would pass on a broken implementation that
        // emitted the character raw and a lenient parser that accepted it.
        assertEquals("""a\tb""", "a\tb".escapeJson())
    }
}
