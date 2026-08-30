package com.hasyame.marvelchampions.data.backup

import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.ExcludedModularSetEntity
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import kotlinx.serialization.Serializable

/**
 * Everything the app cannot rebuild for itself.
 *
 * The card database is deliberately absent: it is a cache of MarvelCDB and is
 * re-downloadable on any device, so including it would turn a small file into a
 * several-megabyte one that goes stale. What is here is what the player made —
 * their collection, decks, campaigns and play history — and none of it exists
 * anywhere else, because this app has no account and no server.
 */
@Serializable
data class Backup(
    /** Bumped only when a later version can no longer read an earlier file. */
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** When the file was written, epoch milliseconds. For the restore summary. */
    val createdAt: Long,
    /** The app that wrote it, so a confusing restore can be traced. */
    val appVersion: String = "",

    val ownedPacks: List<OwnedPackEntity> = emptyList(),
    /**
     * Added after format 1 shipped. The version is deliberately not bumped: an
     * older build ignores unknown keys, so it can still read a newer file and
     * merely loses the exclusions, which beats refusing the backup outright.
     */
    val excludedModularSets: List<ExcludedModularSetEntity> = emptyList(),
    val decks: List<SavedDeckEntity> = emptyList(),
    val campaignRuns: List<CampaignRunEntity> = emptyList(),
    val campaignEvents: List<CampaignEventEntity> = emptyList(),
    val plays: List<PlayEntity> = emptyList(),
    val randomizerHistory: List<RandomizerHistoryEntity> = emptyList(),
    val favouriteCards: List<FavouriteCardEntity> = emptyList(),
    /**
     * Names of the table photographs travelling with this backup.
     *
     * Empty unless the player ticked the box, and empty in every backup written
     * before that box existed. The files themselves are beside this document in
     * the archive; this list is what says they should be there, so a restore
     * can report what it found and what it did not.
     */
    val photos: List<String> = emptyList(),
) {
    /** What a restore is about to bring in, for the confirmation. */
    fun summary(): BackupSummary = BackupSummary(
        createdAt = createdAt,
        ownedPacks = ownedPacks.size,
        decks = decks.size,
        campaigns = campaignRuns.size,
        plays = plays.size,
        photos = photos.size,
    )

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** The counts shown before a restore, so nobody replaces data blindly. */
data class BackupSummary(
    val createdAt: Long,
    val ownedPacks: Int,
    val decks: Int,
    val campaigns: Int,
    val plays: Int,
    val photos: Int = 0,
)

/** What came of reading or writing a backup, in terms the screen can show. */
sealed interface BackupResult {
    data class Exported(val bytes: Long) : BackupResult
    data class Restored(val summary: BackupSummary) : BackupResult

    /** The file was read but is not a backup, or is one this build cannot read. */
    data class Unreadable(val detail: String) : BackupResult

    data class Failed(val detail: String) : BackupResult
}
