package com.hasyame.marvelchampions.data.sync

import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format, held to the shape the server declares.
 *
 * The server's push decoder calls `DisallowUnknownFields`, so a field added to
 * [PushRecordDto] that Go's `IncomingRecord` does not have fails the whole batch
 * with `malformed_record`. That is a failure nobody would predict from reading
 * the Kotlin, and it would arrive as "sync stopped working" rather than as
 * anything pointing at the field that caused it.
 *
 * The collection names are the same kind of contract and worse, because they
 * fail *quietly*: the server stores whatever key it is handed, so a name this
 * client spells differently from the web one builds a second set of records
 * that syncs perfectly and that the other client never sees.
 *
 * Both lists are written out here by hand. It is duplication on purpose: this
 * file is where a change to either has to be noticed.
 */
class SyncWireFormatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `a pushed record carries exactly the fields the server declares`() {
        val encoded = json.encodeToJsonElement(
            PushRecordDto.serializer(),
            PushRecordDto(
                collection = "plays",
                id = "p1",
                updatedAt = "2026-09-04T10:00:00Z",
                deleted = false,
                baseRevision = 41,
                body = buildJsonObject { put("won", true) },
            ),
        ) as JsonObject

        assertEquals(
            setOf("collection", "id", "updatedAt", "deleted", "baseRevision", "body"),
            encoded.keys,
        )
    }

    @Test
    fun `a record new to the server sends no baseRevision at all`() {
        // Absent and zero are different claims. Zero says "I saw revision
        // zero", which no record has, and the server would read it as a
        // conflict check against a revision that never existed.
        val encoded = json.encodeToJsonElement(
            PushRecordDto.serializer(),
            PushRecordDto(
                collection = "plays",
                id = "p1",
                updatedAt = "2026-09-04T10:00:00Z",
                deleted = false,
                baseRevision = null,
                body = buildJsonObject { put("won", true) },
            ),
        ) as JsonObject

        assertFalse("baseRevision" in encoded)
    }

    @Test
    fun `a tombstone carries no body`() {
        val encoded = json.encodeToJsonElement(
            PushRecordDto.serializer(),
            PushRecordDto(
                collection = "plays",
                id = "p1",
                updatedAt = "2026-09-04T10:00:00Z",
                deleted = true,
                body = null,
            ),
        ) as JsonObject

        assertFalse("body" in encoded)
        assertTrue(encoded.getValue("deleted").toString().toBoolean())
    }

    @Test
    fun `the collection names are the ones the server allows`() {
        // Copied from the allow-list in the server's sync.go, which in turn
        // maps each name to a field of this app's own Backup document so that
        // the account export restores through the import path that already
        // exists.
        val server = setOf(
            "owned_packs",
            "excluded_modular_sets",
            "excluded_scenarios",
            "saved_decks",
            "campaign_runs",
            "campaign_events",
            "plays",
            "randomizer_history",
            "favourite_cards",
            "settings",
        )

        assertEquals(server, SyncCollection.entries.map { it.key }.toSet())
    }

    @Test
    fun `the settings record is one record with a known id`() {
        // Not a row per key: the server declares this collection `single`, and
        // Backup declares `settings` as one nullable object. A list here makes
        // the app refuse the whole export file.
        assertEquals("app", SyncCollection.SETTINGS_ID)
    }

    @Test
    fun `only the campaign log is append-only`() {
        // The server refuses a tombstone on an append-only collection, so this
        // has to agree with it or a delete becomes a failed batch.
        assertEquals(
            listOf(SyncCollection.CAMPAIGN_EVENTS),
            SyncCollection.entries.filter { it.isAppendOnly },
        )
    }

    @Test
    fun `deleting an account carries the password`() {
        // Found on a device: the client sent no body at all, and the server
        // refuses that, so erasure could never work. The token alone is not
        // enough on purpose — it is a bearer credential that can be stolen, and
        // this is the one call in the API that cannot be undone.
        val encoded = json.encodeToJsonElement(
            DeleteAccountDto.serializer(),
            DeleteAccountDto("a passphrase"),
        ) as JsonObject

        assertEquals(setOf("password"), encoded.keys)
    }

    @Test
    fun `creating an account sends an address and a pseudonym`() {
        val encoded = json.encodeToJsonElement(
            RegisterDto.serializer(),
            RegisterDto(handle = "benoit", email = "b@example.com", password = "x", deviceName = "d"),
        ) as JsonObject

        assertEquals(setOf("handle", "email", "password", "deviceName"), encoded.keys)
    }

    @Test
    fun `signing in sends one identifier, in the field both servers understand`() {
        // The brief says to send what was typed in *both* `email` and `handle`,
        // on the grounds that it costs nothing. Against the build deployed at
        // thwart.app it costs sign-in itself: every account endpoint decodes
        // with DisallowUnknownFields and that build has no `email` field on
        // this request, so a body carrying one comes back malformed_record.
        //
        // Verified against the running server rather than assumed. Sending only
        // `handle` reaches the same account either side of the deployment: the
        // newer server falls back to it and resolves an address through it.
        val encoded = json.encodeToJsonElement(
            SignInDto.serializer(),
            SignInDto(handle = "b@example.com", password = "x", deviceName = "d"),
        ) as JsonObject

        assertEquals(setOf("handle", "password", "deviceName"), encoded.keys)
    }

    @Test
    fun `an account with no address is still an account`() {
        // Older servers do not send one, and the first account on an instance
        // predates addresses. Absent, not broken.
        val response = json.decodeFromString(
            AuthResponseDto.serializer(),
            """{"accountId":"a","handle":"benoit","token":"t"}""",
        )

        assertEquals("", response.email)
    }

    @Test
    fun `an error envelope is read for its code`() {
        val refusal = """
            {"error":{"code":"cursor_too_old","message":"too old","details":{"minCursor":1204}}}
        """.trimIndent()

        val envelope = json.decodeFromString(ErrorEnvelopeDto.serializer(), refusal)

        assertEquals(SyncException.CURSOR_TOO_OLD, envelope.error.code)
        assertEquals("1204", envelope.error.details.getValue("minCursor").toString())
    }

    @Test
    fun `timestamps survive the trip out and back`() {
        // RFC 3339 on the wire, epoch milliseconds in the tables. The server
        // parses this field and rejects a record whose stamp it cannot read.
        val millis = 1_757_707_200_000

        assertEquals("2025-09-12T20:00:00Z", millis.toRfc3339())
        assertEquals(millis, millis.toRfc3339().toEpochMillis())
    }

    @Test
    fun `a stamp that will not parse sorts as old rather than failing a sync`() {
        assertEquals(0, "not a date".toEpochMillis())
    }
}
