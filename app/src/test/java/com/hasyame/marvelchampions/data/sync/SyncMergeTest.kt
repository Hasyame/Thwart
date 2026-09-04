package com.hasyame.marvelchampions.data.sync

import com.hasyame.marvelchampions.data.backup.BackupSettings
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four places last-write-wins is not the right answer.
 *
 * The server applies it to whole records and knows nothing about the data,
 * which is what keeps it small and lets Android add an entity without a server
 * release. The price is that the client has to be right about the handful of
 * fields where the blunt rule loses something, and each of those is a quiet
 * loss: nothing errors, the number is simply wrong afterwards.
 */
class SyncMergeTest {

    // --- the one that escapes the app ---------------------------------------

    @Test
    fun `a game already posted to BoardGameGeek stays posted`() {
        // The only field in the whole system whose wrong merge has an effect
        // outside the app: a stale false over a true means the game is
        // submitted to BGG a second time, under somebody's real account.
        val posted = play().copy(reportedToBgg = true)
        val stale = play().copy(reportedToBgg = false)

        assertTrue(SyncMerge.play(incoming = stale, local = posted).reportedToBgg)
        assertTrue(SyncMerge.play(incoming = posted, local = stale).reportedToBgg)
    }

    @Test
    fun `a game nobody has posted is not marked as posted`() {
        val merged = SyncMerge.play(play(), play())

        assertFalse(merged.reportedToBgg)
    }

    // --- favourites ---------------------------------------------------------

    @Test
    fun `a card keeps the day it was actually starred`() {
        val early = FavouriteCardEntity(cardCode = "01001", addedAt = 100)
        val late = FavouriteCardEntity(cardCode = "01001", addedAt = 900)

        assertEquals(100, SyncMerge.favourite(incoming = late, local = early).addedAt)
        assertEquals(100, SyncMerge.favourite(incoming = early, local = late).addedAt)
    }

    @Test
    fun `unstarring is not undone by an older date`() {
        // A tombstone has no starring date worth keeping. Taking the earlier
        // one from the row it replaces would resurrect a moment that has been
        // deliberately ended.
        val removed = FavouriteCardEntity(cardCode = "01001", addedAt = 900, deletedAt = 1_000)
        val local = FavouriteCardEntity(cardCode = "01001", addedAt = 100)

        val merged = SyncMerge.favourite(incoming = removed, local = local)

        assertEquals(1_000L, merged.deletedAt)
        assertEquals(900, merged.addedAt)
    }

    // --- the clock ----------------------------------------------------------

    @Test
    fun `a campaign pulled from another device does not start this one's clock`() {
        // The timer columns are excluded from the body, so an incoming record
        // carries the entity's defaults rather than anything true. Taking them
        // would have a phone counting a session sitting on somebody else's
        // table, and would throw away the time this one has actually played.
        val playing = run().copy(timerAccumulatedMillis = 42 * 60_000, timerRunningSince = 5_000)
        val fromAccount = run().copy(name = "renamed")

        val merged = SyncMerge.campaignRun(incoming = fromAccount, local = playing)

        assertEquals("renamed", merged.name)
        assertEquals(42 * 60_000, merged.timerAccumulatedMillis)
        assertEquals(5_000L, merged.timerRunningSince)
    }

    // --- packs --------------------------------------------------------------

    @Test
    fun `owning a pack on either device means owning it`() {
        val two = OwnedPackEntity(packCode = "core", quantity = 2)
        val one = OwnedPackEntity(packCode = "core", quantity = 1)

        assertEquals(2, SyncMerge.ownedPackOnFirstMerge(incoming = one, local = two).quantity)
        assertEquals(2, SyncMerge.ownedPackOnFirstMerge(incoming = two, local = one).quantity)
    }

    @Test
    fun `a pack given away on one device is not resurrected by the merge`() {
        // Only the first merge is inclusive, and only over quantities. A
        // tombstone is a decision, not a lower number.
        val gone = OwnedPackEntity(packCode = "core", quantity = 1, deletedAt = 500)
        val held = OwnedPackEntity(packCode = "core", quantity = 1)

        assertEquals(held, SyncMerge.ownedPackOnFirstMerge(incoming = gone, local = held))
    }

    // --- decks --------------------------------------------------------------

    @Test
    fun `two devices editing the same imported deck fork rather than choose`() {
        val mine = deck().copy(locallyEdited = true, slots = "01001=3")
        val theirs = deck().copy(locallyEdited = true, slots = "01002=3")

        assertTrue(SyncMerge.deckForks(incoming = theirs, local = mine))
    }

    @Test
    fun `the same deck imported twice is one deck`() {
        // The happy accident the shared id buys: two devices importing
        // decklist 12345 produce the same row, and neither has edited it.
        val mine = deck()
        val theirs = deck()

        assertFalse(SyncMerge.deckForks(incoming = theirs, local = mine))
    }

    @Test
    fun `a deck only one side edited is not a fork`() {
        val mine = deck().copy(locallyEdited = true, slots = "01001=3")
        val theirs = deck().copy(locallyEdited = false, slots = "01002=3")

        assertFalse(SyncMerge.deckForks(incoming = theirs, local = mine))
    }

    @Test
    fun `a forked deck keeps the work and stops claiming to be the import`() {
        val mine = deck().copy(name = "Spider-Man", locallyEdited = true, slots = "01001=3")

        val forked = SyncMerge.forkedDeck(mine, newId = "local-abc", suffix = "(2)")

        assertEquals("local-abc", forked.id)
        assertEquals("Spider-Man (2)", forked.name)
        assertEquals("01001=3", forked.slots)
        // A later import of decklist 12345 must not find this row and think it
        // is the deck it is refreshing.
        assertEquals(0, forked.marvelCdbId)
        assertNotEquals(mine.kind, forked.kind)
    }

    // --- settings -----------------------------------------------------------

    @Test
    fun `two devices changing two different settings both keep theirs`() {
        val fromAccount = BackupSettings(cardLocale = "en", themeChoice = "dark")
        val here = BackupSettings(cardLocale = "fr", themeChoice = "")

        val merged = SyncMerge.settings(fromAccount, here, preferLocal = false)

        assertEquals("en", merged.cardLocale)
        assertEquals("dark", merged.themeChoice)
    }

    @Test
    fun `a pack turned down anywhere stays turned down`() {
        // The one union in the settings. Re-offering a pack on the other phone
        // is exactly the nagging that dismissing it was meant to stop.
        val fromAccount = BackupSettings(dismissedPacks = listOf("mts"))
        val here = BackupSettings(dismissedPacks = listOf("core"))

        val merged = SyncMerge.settings(fromAccount, here, preferLocal = false)

        assertEquals(listOf("core", "mts"), merged.dismissedPacks)
    }

    @Test
    fun `on a first merge the device in front of the person wins`() {
        // There is no history to adjudicate with, and the settings somebody is
        // looking at are the better guess at the ones they want.
        val fromAccount = BackupSettings(cardLocale = "en", trackEncounter = false)
        val here = BackupSettings(cardLocale = "fr", trackEncounter = true)

        val merged = SyncMerge.settings(fromAccount, here, preferLocal = true)

        assertEquals("fr", merged.cardLocale)
        assertTrue(merged.trackEncounter)
    }

    // --- fixtures -----------------------------------------------------------

    private fun play() = PlayEntity(
        id = "play",
        playedAt = 1,
        scenarioCode = "01001",
        scenarioName = "Rhino",
        difficulty = "standard_i",
        heroCode = "01001a",
        heroName = "Spider-Man",
        aspects = "justice",
        won = true,
    )

    private fun run() = CampaignRunEntity(
        id = "run",
        templateId = "fne",
        templateName = "Fear No Evil",
        difficulty = "standard",
        createdAt = 1,
        templateJson = "{}",
    )

    private fun deck() = SavedDeckEntity(
        id = "decklist-12345",
        marvelCdbId = 12345,
        kind = "DECKLIST",
        url = "https://marvelcdb.com/decklist/view/12345",
        name = "Spider-Man",
        heroCode = "01001a",
        heroName = "Spider-Man",
        aspects = "justice",
        slots = "01001=3",
        ignoreDeckLimitSlots = "",
        descriptionMd = null,
        version = null,
        tags = null,
        rawJson = "{}",
        lastSyncedAt = 1,
    )
}
