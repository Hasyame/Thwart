package com.hasyame.marvelchampions.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.security.SecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Nothing is sent on anybody's behalf without three separate yeses.
 *
 * The hazard is one-directional and worth being blunt about: a bug that makes
 * this too eager uploads somebody's games to a server they never agreed to use.
 * A bug that makes it too shy costs a tap on "Sync now". So every condition is
 * checked on its own here, and each test turns exactly one of them off.
 *
 * The last two tests are the ones that will still earn their place in a year.
 * They read the app's own sources and refuse a moment that was named and never
 * wired up, or a trigger that quietly lost its call site in a refactor — both of
 * which fail silently, as a sync that simply never happens.
 */
@RunWith(RobolectricTestRunner::class)
class AutoSyncTest {

    private lateinit var context: Context
    private lateinit var sessions: SyncSessionStore

    /** No Android Keystore on a JVM, so the token is kept as it is given. */
    private class PlainSecretStore : SecretStore() {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(encoded: String): String = encoded
    }

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        sessions = SyncSessionStore(context, PlainSecretStore())
        // The preference store outlives a single test.
        sessions.signOut()
    }

    private suspend fun signIn() = sessions.signedIn(
        AuthResponseDto(accountId = "account", handle = "tester", token = "token"),
    )

    private suspend fun armed(): Boolean = sessions.autoSyncArmed.first()

    @Test
    fun `a phone nobody has signed in on never syncs by itself`() = runTest {
        assertFalse(armed())
    }

    @Test
    fun `signing in is not enough`() = runTest {
        signIn()
        assertFalse(armed())
    }

    @Test
    fun `turning sync on is not enough either`() = runTest {
        signIn()
        sessions.setEnabled(true)
        assertFalse(armed())
    }

    @Test
    fun `asking for it, signed in and with sync on, is enough`() = runTest {
        signIn()
        sessions.setEnabled(true)
        sessions.setAutoSync(true)
        assertTrue(armed())
    }

    @Test
    fun `asking for it without an account does nothing`() = runTest {
        // The switch is only reachable from a signed-in screen, so this is a
        // guard against a future path rather than against today's UI.
        sessions.setAutoSync(true)
        assertFalse(armed())
    }

    @Test
    fun `switching sync off switches this off with it`() = runTest {
        signIn()
        sessions.setEnabled(true)
        sessions.setAutoSync(true)

        sessions.setEnabled(false)

        assertFalse(armed())
        // Not merely inert: forgotten. Turning sync back on months later must
        // not silently resume a background job.
        assertFalse(sessions.current().autoSync)
    }

    @Test
    fun `signing out forgets it`() = runTest {
        signIn()
        sessions.setEnabled(true)
        sessions.setAutoSync(true)

        sessions.signOut()

        assertFalse(armed())
        assertFalse(sessions.current().autoSync)
    }

    @Test
    fun `signing in as somebody else forgets it`() = runTest {
        signIn()
        sessions.setEnabled(true)
        sessions.setAutoSync(true)

        sessions.signedIn(
            AuthResponseDto(accountId = "other", handle = "other", token = "token"),
        )

        assertFalse(armed())
    }

    // --- the moments are wired up ------------------------------------------

    @Test
    fun `every moment named is a moment that happens`() {
        val sources = mainSources().joinToString("\n") { it.readText() }
        val unused = SyncTrigger.entries.filter { trigger ->
            !sources.contains("SyncTrigger.${trigger.name}")
        }
        assertEquals(
            "these moments are declared and never reached, so they never sync",
            emptyList<SyncTrigger>(),
            unused,
        )
    }

    @Test
    fun `each moment is asked for where the user described it`() {
        val calls = mainSources().flatMap { file ->
            Regex("""autoSync\.after\(SyncTrigger\.(\w+)\)""")
                .findAll(file.readText())
                .map { file.name to it.groupValues[1] }
        }.toSet()

        val expected = setOf(
            // The end of a scenario, standalone and in a campaign.
            "PlayRepository.kt" to "SCENARIO_FINISHED",
            "CampaignRepository.kt" to "SCENARIO_FINISHED",
            // The end of a campaign.
            "CampaignRepository.kt" to "CAMPAIGN_FINISHED",
            // A game put away for later, from either kind of session.
            "GameSessionViewModel.kt" to "LONG_BREAK",
            "CampaignRunViewModel.kt" to "LONG_BREAK",
            // A deck saved, a collection changed, a card starred.
            "DeckRepository.kt" to "DECK_ADDED",
            "CollectionRepository.kt" to "COLLECTION_CHANGED",
            "FavouriteRepository.kt" to "CARD_FAVOURITED",
        )

        assertEquals(
            "a moment lost its call site",
            emptySet<Pair<String, String>>(),
            expected - calls,
        )
    }

    /**
     * Every Kotlin source in the app, found by walking up from wherever the
     * test happens to be run.
     */
    private fun mainSources(): List<File> {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            for (candidate in listOf(SOURCE_ROOT, SOURCE_ROOT.removePrefix("app/"))) {
                val folder = File(directory, candidate)
                if (folder.isDirectory) {
                    val sources = folder.walkTopDown().filter { it.extension == "kt" }.toList()
                    // A search that finds nothing passes by saying nothing, so
                    // the guards above would go quiet the day this path moves.
                    assertTrue("no sources under $folder", sources.size > 100)
                    return sources
                }
            }
            directory = directory.parentFile
        }
        error("could not find the app sources from ${File("").absolutePath}")
    }

    private companion object {
        const val SOURCE_ROOT = "app/src/main/java/com/hasyame/marvelchampions"
    }
}
