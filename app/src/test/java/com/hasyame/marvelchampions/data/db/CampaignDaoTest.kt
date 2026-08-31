package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.CampaignDao
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The event log is the only place campaign state exists, so anything that can
 * silently empty it is the worst class of bug this app can have.
 *
 * It happened: the run table used `@Insert(onConflict = REPLACE)`, which SQLite
 * implements as DELETE then INSERT. `campaign_events` references the run with
 * ON DELETE CASCADE, so every timer tick wiped the entire campaign. These tests
 * exist to stop that returning.
 */
@RunWith(RobolectricTestRunner::class)
class CampaignDaoTest {

    /** One fixed instant, so a test reads about the change and not the clock. */
    private val NOW = 1_700_000_000_000L

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: CampaignDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.campaignDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun run(id: String = "run-1") = CampaignRunEntity(
        id = id,
        templateId = "gmw",
        templateName = "The Galaxy's Most Wanted",
        name = "My campaign",
        difficulty = "standard",
        createdAt = 1L,
        templateJson = "{}",
    )

    private fun event(id: String, runId: String = "run-1", at: Long = 1L) = CampaignEventEntity(
        id = id,
        runId = runId,
        timestamp = at,
        payload = """{"type":"setup"}""",
    )

    private suspend fun seed() {
        dao.insertRun(run())
        dao.appendEvents(listOf(event("e0"), event("e1", at = 2L), event("e2", at = 3L)))
    }

    @Test
    fun `updating the timer keeps the event log`() = runTest {
        seed()

        dao.updateTimer("run-1", accumulated = 5_000, runningSince = null, scenarioId = "s1")

        assertEquals(3, dao.countEvents("run-1"))
        assertEquals(5_000L, dao.getRun("run-1")?.timerAccumulatedMillis)
    }

    @Test
    fun `marking a run finished keeps the event log`() = runTest {
        seed()

        dao.setFinished("run-1", true, NOW)

        assertEquals(3, dao.countEvents("run-1"))
        assertEquals(true, dao.getRun("run-1")?.finished)
    }

    @Test
    fun `replacing the stored template keeps the event log`() = runTest {
        seed()

        dao.setTemplateJson("run-1", """{"id":"gmw"}""", NOW)

        assertEquals(3, dao.countEvents("run-1"))
        assertEquals("""{"id":"gmw"}""", dao.getRun("run-1")?.templateJson)
    }

    @Test
    fun `a whole-row update keeps the event log`() = runTest {
        seed()

        dao.updateRun(run().copy(name = "Renamed", finished = true))

        assertEquals(3, dao.countEvents("run-1"))
        assertEquals("Renamed", dao.getRun("run-1")?.name)
    }

    @Test
    fun `appending the same event twice is a no-op`() = runTest {
        // Ids are stable so a two-device merge can re-append safely.
        seed()

        dao.appendEvent(event("e1", at = 99L))

        assertEquals(3, dao.countEvents("run-1"))
        assertEquals(2L, dao.getEvents("run-1").first { it.id == "e1" }.timestamp)
    }

    @Test
    fun `events come back in timestamp order`() = runTest {
        dao.insertRun(run())
        dao.appendEvents(
            listOf(event("late", at = 30L), event("early", at = 10L), event("mid", at = 20L)),
        )

        assertEquals(listOf("early", "mid", "late"), dao.getEvents("run-1").map { it.id })
    }

    @Test
    fun `deleting a run takes its events out of sight with it`() = runTest {
        // The events are not removed any more — a tombstoned run fires no
        // cascade — but nothing can read them, which is the property the app
        // relies on and the one a second device has to agree with.
        seed()

        dao.deleteRun("run-1", NOW)

        assertEquals(0, dao.countEvents("run-1"))
        assertNull(dao.getRun("run-1"))
    }

    @Test
    fun `two runs keep their own logs`() = runTest {
        seed()
        dao.insertRun(run("run-2"))
        dao.appendEvent(event("other", runId = "run-2"))

        dao.updateTimer("run-2", accumulated = 1, runningSince = null, scenarioId = null)

        assertEquals(3, dao.countEvents("run-1"))
        assertEquals(1, dao.countEvents("run-2"))
        assertNotNull(dao.getRun("run-1"))
    }
}
