package com.hasyame.marvelchampions.data.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live channel's half of the contract, checked against what the server
 * actually writes.
 *
 * These are the bytes from `server/stream.go`, copied rather than described:
 *
 * ```
 * fmt.Fprintf(w, "id: %d\nevent: changed\ndata: {\"revision\":%d}\n\n", …)
 * ```
 *
 * A stream is the worst place for a format disagreement to show up. It fails
 * as *nothing happening*: no error, no crash, just a phone that quietly stops
 * being live and falls back to syncing when it happens to write something. That
 * is indistinguishable from the feature being off, which is why the shape is
 * pinned here rather than left to be noticed by somebody wondering why their
 * tablet is behind.
 */
class SyncStreamWireTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun revisionOf(data: String): Long =
        json.decodeFromString(RevisionEventDto.serializer(), data).revision

    @Test
    fun `the payload the server sends is read as a revision`() {
        assertEquals(1841L, revisionOf("""{"revision":1841}"""))
    }

    @Test
    fun `a field this version does not know is ignored rather than fatal`() {
        // The server may grow the payload. A client that refuses to parse the
        // addition would go silent against a newer server, which is the one
        // failure mode this channel must not have.
        assertEquals(7L, revisionOf("""{"revision":7,"reason":"push","account":"x"}"""))
    }

    @Test
    fun `a payload with no revision at all is zero rather than an exception`() {
        // Zero can never be ahead of a cursor, so a malformed event becomes a
        // no-op instead of a crash on a background thread.
        assertEquals(0L, revisionOf("{}"))
    }

    @Test
    fun `the stream url carries the cursor the protocol reads`() {
        val url = SyncEndpoints("https://thwart.app/api").stream(1841)

        assertEquals("https://thwart.app/api/v1/sync/stream?since=1841", url)
    }

    @Test
    fun `a self-hosted instance keeps its own base`() {
        val url = SyncEndpoints("http://192.168.1.10:8787").stream(0)

        assertTrue(url.startsWith("http://192.168.1.10:8787/v1/sync/stream"))
    }

    @Test
    fun `a trailing slash on the instance does not double up`() {
        // Somebody typing their own server's address will end it with a slash
        // about half the time, and a doubled slash is a 404 on some proxies.
        assertEquals(
            "https://thwart.app/api/v1/sync/stream?since=3",
            SyncEndpoints("https://thwart.app/api/").stream(3),
        )
    }
}
