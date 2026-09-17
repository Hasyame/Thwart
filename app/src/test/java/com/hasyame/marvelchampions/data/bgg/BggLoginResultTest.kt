package com.hasyame.marvelchampions.data.bgg

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a BGG login answer is read. The endpoint is undocumented, so what it
 * says on a wrong password is a fact checked against it (2026-09-12, by the
 * web's relay): a 400 with a message, not a 401.
 */
class BggLoginResultTest {

    @Test
    fun `a session is a success`() {
        assertEquals(BggResult.Success, BggClient.loginResult(200, ""))
    }

    @Test
    fun `a 401 is bad credentials`() {
        assertEquals(BggResult.BadCredentials, BggClient.loginResult(401, ""))
    }

    @Test
    fun `a 400 naming the password is bad credentials too`() {
        assertEquals(
            BggResult.BadCredentials,
            BggClient.loginResult(400, """{"errors":{"message":"Invalid username or password"}}"""),
        )
    }

    @Test
    fun `any other 400 is BGG refusing the request`() {
        assertEquals(BggResult.Rejected("HTTP 400"), BggClient.loginResult(400, """{"errors":{"message":"Missing credentials"}}"""))
    }

    @Test
    fun `a server error is a rejection, with the code`() {
        assertEquals(BggResult.Rejected("HTTP 503"), BggClient.loginResult(503, "down"))
    }
}
