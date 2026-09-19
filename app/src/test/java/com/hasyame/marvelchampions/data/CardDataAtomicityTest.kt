package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbApi
import com.hasyame.marvelchampions.data.marvelcdb.dto.CardDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import com.hasyame.marvelchampions.data.repository.CardDataRepository
import com.hasyame.marvelchampions.data.seed.CardSeedSource
import com.hasyame.marvelchampions.data.settings.AppPreferences
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CardDataAtomicityTest {
    private lateinit var database: MarvelChampionsDatabase
    private lateinit var repository: CardDataRepository
    private var failFrench = false

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MarvelChampionsDatabase::class.java)
            .allowMainThreadQueries().build()
        val api = Proxy.newProxyInstance(MarvelCdbApi::class.java.classLoader, arrayOf(MarvelCdbApi::class.java)) { _, method, args ->
            when (method.name) {
                "getPacksAt" -> listOf(PackDto(1, "core", "Synthetic", 1, "", 1, 1))
                "getAllCardsAt" -> {
                    if (failFrench && args[0].toString().contains("fr.marvelcdb")) throw IOException("synthetic interruption")
                    listOf(CardDto("test-card", "Synthetic", "Synthetic", 1, 1,
                        packCode = "core", packName = "Synthetic", packLegacy = false,
                        typeCode = "ally", typeName = "Ally", factionCode = "basic", factionName = "Basic"))
                }
                else -> error("Unexpected network call: ${method.name}")
            }
        } as MarvelCdbApi
        repository = CardDataRepository(api, database, CardSeedSource(context, Json { ignoreUnknownKeys = true }, Dispatchers.Unconfined),
            AppPreferences(context), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `packs without cards are not a ready catalogue`() = runTest {
        database.packDao().insertPacks(listOf(PackEntity("old", 1, 1, "", 1, 1, type = "core", wave = 0)))
        assertTrue(repository.isEmpty())
    }

    @Test
    fun `a failed second locale leaves the previous catalogue intact`() = runTest {
        database.packDao().insertPacks(listOf(PackEntity("old", 1, 1, "", 1, 1, type = "core", wave = 0)))
        failFrench = true
        val result = runCatching { repository.refreshFromNetwork() }
        assertTrue(result.isFailure)
        assertEquals(listOf("old"), database.packDao().getPacks().map { it.code })
        assertEquals(0, database.cardDao().countForLocale("en"))
        assertEquals(0, database.cardDao().countForLocale("fr"))
    }

    @Test
    fun `a successful refresh commits both locales and becomes ready`() = runTest {
        repository.refreshFromNetwork()
        assertFalse(repository.isEmpty())
        assertEquals(1, database.cardDao().countForLocale("en"))
        assertEquals(1, database.cardDao().countForLocale("fr"))
    }
}
