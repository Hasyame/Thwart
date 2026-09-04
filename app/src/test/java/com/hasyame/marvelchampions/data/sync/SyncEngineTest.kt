package com.hasyame.marvelchampions.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.security.SecretStore
import com.hasyame.marvelchampions.data.settings.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The three failures the brief says to write tests for before writing the code.
 *
 * Each of them is silent when it goes wrong. A duplicated batch looks like a
 * busy weekend of play; a cursor that skipped a revision looks like a device
 * that is up to date; an adoption that wrote before it asked looks fine to
 * everyone except the person whose campaign log it ate.
 *
 * Run against [FakeSyncApi] rather than a mock, because what is being tested is
 * a *sequence*: what the client sends after a failure, and whether the thing it
 * sends second time is the same batch or a new one.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var context: Context
    private lateinit var database: MarvelChampionsDatabase
    private lateinit var api: FakeSyncApi
    private lateinit var sessions: SyncSessionStore
    private lateinit var engine: SyncEngine

    /** No Android Keystore on a JVM, so the token is kept as it is given. */
    private class PlainSecretStore : SecretStore() {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(encoded: String): String = encoded
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MarvelChampionsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeSyncApi()
        sessions = SyncSessionStore(context, PlainSecretStore())
        // A clean slate between tests: the preference store outlives one.
        sessions.signOut()
        val codec = SyncRecordCodec(database.syncRecordDao(), AppPreferences(context), json)
        engine = SyncEngine(
            client = SyncClient(api, sessions, json, Dispatchers.Unconfined),
            codec = codec,
            sessions = sessions,
            syncState = database.syncStateDao(),
            database = database,
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
        sessions.signedIn(
            AuthResponseDto(accountId = "account", handle = "tester", token = "token"),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- batches ------------------------------------------------------------

    @Test
    fun `a batch that fails stops the run and the next one is not sent`() = runTest {
        givenLocalPlays(5)

        // Two records to a batch, so five plays are three batches.
        api.failOnAttempt = 2

        val failure = runCatching { engine.sync() }.exceptionOrNull()

        assertTrue("the run should have failed", failure is SyncException)
        // Two sent, the third never attempted: a client that carried on could
        // land two edits of one record in an order it did not intend.
        assertEquals(2, api.batchIds.size)
        assertEquals(1, api.applied)
    }

    @Test
    fun `a batch whose answer was lost is retried under the same id, and applies once`() = runTest {
        givenLocalPlays(4)

        // The failure that actually happens: it was written, the reply was not
        // received. The client cannot tell this from a batch that never landed.
        api.loseResponseOnAttempt = 1

        runCatching { engine.sync() }
        val lost = api.batchIds.single()
        assertEquals(
            "the batch id has to survive the failure or the retry is a fresh write",
            lost,
            sessions.current().inFlightBatchId,
        )

        api.loseResponseOnAttempt = null
        engine.sync()

        assertEquals("the retry must carry the same id", lost, api.batchIds[1])
        // Four plays, whatever the network did. Applying the first batch twice
        // would be six.
        assertEquals(4, api.countIn(SyncCollection.PLAYS.key))
    }

    @Test
    fun `nothing is marked clean until the server has said so by name`() = runTest {
        givenLocalPlays(2)
        api.failOnAttempt = 1

        runCatching { engine.sync() }

        // The two plays and the settings record: nothing at all was confirmed,
        // so nothing at all is clean.
        assertEquals("the records must still be waiting", 3, engine.pendingCount())
    }

    @Test
    fun `what this device sent does not come straight back`() = runTest {
        // The push writes revisions above the cursor, and the cursor cannot
        // tell they are its own. Left alone, every upload is re-downloaded and
        // rewritten on the next sync: harmless, since the records are the same,
        // and wasteful enough on a real account to be worth getting right.
        givenLocalPlays(3)
        engine.sync()

        val second = engine.sync()

        assertEquals("nothing new to read", 0, second.pulled)
        assertEquals("nothing left to send", 0, second.pushed)
    }

    @Test
    fun `a gap left by another device is not stepped over`() = runTest {
        // If something else wrote while this device was pushing, the revisions
        // it was given are not contiguous from where its cursor stood. Jumping
        // to the highest one would skip the other device's records for good, so
        // the cursor stays put and the next pull picks them up.
        api.seed(SyncCollection.PLAYS.key, "server-1", playBody("server-1"))
        engine.sync()
        givenLocalPlays(1, prefix = "local")
        // Another device's write lands between this device's cursor and its
        // own next revision.
        api.seed(SyncCollection.PLAYS.key, "server-2", playBody("server-2"))

        engine.sync()

        assertNotNull("the other device's record must survive", database.syncRecordDao().play("server-2"))
    }

    // --- the tombstone horizon ----------------------------------------------

    @Test
    fun `a cursor below the horizon resyncs, and local rows survive it`() = runTest {
        api.seed(SyncCollection.PLAYS.key, "server-1", playBody("server-1"))
        engine.sync()
        val cursor = sessions.current().cursor
        assertTrue("the first sync should have read something", cursor > 0)

        // Four more games on the account, and a horizon that has moved above
        // where this device got to: it has been away longer than the server can
        // account for, so its cursor is no longer usable.
        //
        // The page size is what makes the resync itself possible. A resync pages
        // with the same `since` values as any other pull, so it only works while
        // its page boundaries land at or above the horizon — see the test below
        // for what happens when they do not.
        repeat(4) { index ->
            api.seed(SyncCollection.PLAYS.key, "server-$index", playBody("server-$index"))
        }
        api.limits = api.limits.copy(pageSize = 3)
        api.minCursor = cursor + 1
        // And this phone has a game the account has never seen.
        givenLocalPlays(1, prefix = "local")

        val outcome = engine.sync()

        assertTrue("it should have started again from nothing", outcome.fullResync)
        // The whole point of a full resync being a merge: the row the server
        // never had is still here, and now it has it.
        assertNotNull(database.syncRecordDao().play("local-0"))
        assertNotNull(api.stored(SyncCollection.PLAYS.key, "local-0"))
        assertNotNull(database.syncRecordDao().play("server-3"))
    }

    @Test
    fun `a resync pages through, below the horizon and all`() = runTest {
        // A resync pages with `since = the last revision of the previous page`,
        // and those boundaries sit below the tombstone horizon whenever the
        // account holds live records written before the last sweep. Saying so
        // is what lets the server serve them: a rebuild from nothing has no
        // deletion to miss, which is the same reason since=0 was always exempt.
        repeat(6) { index ->
            api.seed(SyncCollection.PLAYS.key, "server-$index", playBody("server-$index"))
        }
        api.minCursor = 5

        val outcome = engine.sync()

        assertFalse("nothing should have been left behind", outcome.incomplete)
        assertNotNull(database.syncRecordDao().play("server-0"))
        assertNotNull(database.syncRecordDao().play("server-5"))
    }

    @Test
    fun `a server that will not page a resync stops us rather than skipping`() = runTest {
        // An instance from before the flag existed. The records between the
        // page boundary and the horizon have never reached this device, so
        // stepping over them would lose them for good: it stops, keeps what it
        // applied, and says it did not get everything.
        repeat(6) { index ->
            api.seed(SyncCollection.PLAYS.key, "server-$index", playBody("server-$index"))
        }
        api.minCursor = 5
        api.ignoresResync = true

        val outcome = engine.sync()

        assertTrue("it must say it did not get everything", outcome.incomplete)
        assertNotNull(database.syncRecordDao().play("server-0"))
    }

    // --- adoption -----------------------------------------------------------

    @Test
    fun `planning an adoption writes nothing at all`() = runTest {
        api.seed(SyncCollection.PLAYS.key, "server-1", playBody("server-1"))
        api.seed(SyncCollection.PLAYS.key, "server-2", playBody("server-2"))
        givenLocalPlays(3, prefix = "local")

        val plan = engine.planAdoption()

        assertEquals(2, plan.counts.server(SyncCollection.PLAYS))
        assertEquals(3, plan.counts.localOnly(SyncCollection.PLAYS))
        // Cancel is simply not going on, so this is what cancelling leaves
        // behind: exactly what was there.
        assertEquals(3, database.syncRecordDao().plays().size)
        assertNull(database.syncRecordDao().play("server-1"))
        assertEquals(0, sessions.current().cursor)
    }

    @Test
    fun `merging keeps both sides`() = runTest {
        api.seed(SyncCollection.PLAYS.key, "server-1", playBody("server-1"))
        givenLocalPlays(2, prefix = "local")

        val outcome = engine.adoptMerging(engine.planAdoption())

        assertEquals(3, database.syncRecordDao().plays().size)
        // And the account now holds what the phone brought.
        assertNotNull(api.stored(SyncCollection.PLAYS.key, "local-0"))
        assertNotNull(api.stored(SyncCollection.PLAYS.key, "local-1"))
        assertEquals(3, api.countIn(SyncCollection.PLAYS.key))
        // The two plays, plus this device's settings, which are also new to
        // the account.
        assertEquals(3, outcome.pushed)
    }

    @Test
    fun `keeping only the server refuses without an export`() = runTest {
        api.seed(SyncCollection.PLAYS.key, "server-1", playBody("server-1"))
        givenLocalPlays(1, prefix = "local")
        val plan = engine.planAdoption()

        val refused = runCatching { engine.adoptKeepingServerOnly(plan, exported = false) }

        assertTrue(refused.exceptionOrNull() is IllegalArgumentException)
        assertEquals("nothing may be discarded", 1, database.syncRecordDao().plays().size)
    }

    // --- fixtures -----------------------------------------------------------

    private suspend fun givenLocalPlays(count: Int, prefix: String = "local") {
        repeat(count) { index ->
            database.playDao().insert(play("$prefix-$index"))
        }
    }

    private fun play(id: String) = PlayEntity(
        id = id,
        playedAt = 1_700_000_000_000,
        scenarioCode = "01001",
        scenarioName = "Rhino",
        difficulty = "standard_i",
        heroCode = "01001a",
        heroName = "Spider-Man",
        aspects = "justice",
        won = true,
        updatedAt = 1_700_000_000_000,
    )

    /** What the server would hold for a play with this id. */
    private fun playBody(id: String) =
        json.encodeToJsonElement(PlayEntity.serializer(), play(id)) as kotlinx.serialization.json.JsonObject
}
