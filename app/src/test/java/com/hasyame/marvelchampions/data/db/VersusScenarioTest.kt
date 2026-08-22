package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Civil War and Synthezoid Smackdown are played as a leader plus a side.
 *
 * Neither half is a game: Captain Marvel is who you face, Resistance is how.
 * The card database models this with a set type of its own — `leader` — which
 * is why the versus packs looked empty when only villain sets were counted, and
 * why the app once invented its own list of these scenarios and got the pack
 * code wrong.
 */
@RunWith(RobolectricTestRunner::class)
class VersusScenarioTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.cardDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun card(code: String, set: String, setName: String, type: String, pack: String) =
        CardEntity(
            code = code,
            locale = "en",
            name = code,
            realName = code,
            position = 1,
            quantity = 1,
            packCode = pack,
            packName = pack,
            packLegacy = false,
            typeCode = type,
            typeName = type,
            factionCode = "encounter",
            factionName = "Encounter",
            cardSetCode = set,
            cardSetName = setName,
            cardSetTypeNameCode = if (type == "main_scheme") "main_scheme" else type,
            searchName = SearchNormalizer.normalize(code),
            searchText = "",
            searchTraits = "",
        )

    @Test
    fun `leaders and sides are found, and villains are not mistaken for them`() = runTest {
        dao.insertAll(
            listOf(
                card("1", "iron_man_leader", "Iron Man", "leader", "cw"),
                card("2", "captain_marvel_leader", "Captain Marvel", "leader", "cw"),
                card("3", "resistance", "Resistance", "main_scheme", "cw"),
                card("4", "registration", "Registration", "main_scheme", "cw"),
                // An ordinary scenario, which must not turn up as a leader.
                card("5", "rhino", "Rhino", "villain", "core"),
            ),
        )

        assertEquals(
            listOf("captain_marvel_leader", "iron_man_leader"),
            dao.getLeaders("en").map { it.code },
        )
        assertEquals(
            listOf("registration", "resistance"),
            dao.getVersusSides("en").map { it.code }.sorted(),
        )
        assertTrue(
            "a villain set is not a leader",
            dao.getLeaders("en").none { it.code == "rhino" },
        )
    }

    @Test
    fun `sides stay with their own pack`() = runTest {
        // Both versus packs call their sides Resistance and Registration, under
        // different codes. Pairing across packs would offer Civil War's side
        // with She-Hulk, which is not a game that exists.
        dao.insertAll(
            listOf(
                card("1", "resistance", "Resistance", "main_scheme", "cw"),
                card("2", "synthezoid_resistance", "Resistance", "main_scheme", "synthezoid"),
                card("3", "she_hulk_leader", "She-Hulk", "leader", "synthezoid"),
            ),
        )

        val sidesByPack = dao.getVersusSides("en").groupBy { it.packCode }
        assertEquals(listOf("resistance"), sidesByPack["cw"]?.map { it.code })
        assertEquals(
            listOf("synthezoid_resistance"),
            sidesByPack["synthezoid"]?.map { it.code },
        )
        assertEquals("synthezoid", dao.getLeaders("en").single().packCode)
    }

    @Test
    fun `a leader's stages are found the way a villain's are`() = runTest {
        // Civil War puts a leader where every other box puts a villain, and its
        // rulebook says the two behave identically. The scenario-sides query
        // asked for villains by type, so a Civil War game found nothing to
        // count and showed no health at all.
        dao.insertAll(
            listOf(
                card("56059", "iron_man_leader", "Iron Man", "leader", "cw")
                    .copy(stage = "I", health = 12),
                card("56060", "iron_man_leader", "Iron Man", "leader", "cw")
                    .copy(stage = "II", health = 16),
                card("56061", "iron_man_leader", "Iron Man", "leader", "cw")
                    .copy(stage = "III", health = 16),
            ),
        )

        val stages = dao.getScenarioSides("iron_man_leader", "en")
        assertEquals(3, stages.size)
        assertEquals(listOf(12, 16, 16), stages.map { it.health })
    }
}
