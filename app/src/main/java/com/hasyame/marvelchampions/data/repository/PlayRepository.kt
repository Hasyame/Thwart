package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.bgg.BggAccount
import com.hasyame.marvelchampions.data.bgg.BggClient
import com.hasyame.marvelchampions.data.bgg.BggResult
import com.hasyame.marvelchampions.data.db.dao.PlayDao
import com.hasyame.marvelchampions.data.db.dao.SyncStateDao
import com.hasyame.marvelchampions.data.db.dao.WinRateRow
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.SyncCollection
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.BggPlay
import com.hasyame.marvelchampions.domain.model.BggPlayer
import com.hasyame.marvelchampions.domain.model.BggReportingMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when a play was recorded, so the UI can say something true. */
sealed interface PlayRecorded {
    /** Saved, and BoardGameGeek was not involved. */
    data object SavedOnly : PlayRecorded

    /** Saved and sent. */
    data object SavedAndReported : PlayRecorded

    /** Saved, but the player should be asked whether to send it. */
    data object SavedAskToReport : PlayRecorded

    /** Saved; sending failed and the play is still here to send later. */
    data class SavedReportFailed(val detail: String) : PlayRecorded
}

@Singleton
class PlayRepository @Inject constructor(
    private val playDao: PlayDao,
    private val syncStateDao: SyncStateDao,
    private val bggAccount: BggAccount,
    private val bggClient: BggClient,
    private val preferences: AppPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun observePlays(): Flow<List<PlayEntity>> = playDao.observePlays()

    fun observeByScenario(): Flow<List<WinRateRow>> = playDao.observeByScenario()

    fun observeByDifficulty(): Flow<List<WinRateRow>> = playDao.observeByDifficulty()

    fun observeBySoloOrGroup(): Flow<List<WinRateRow>> = playDao.observeBySoloOrGroup()

    /**
     * Win rate per hero, counting every seat at the table.
     *
     * A group game is one game and several heroes. The totals and the
     * solo/group split stay per game; this is per seat, because "how does this
     * hero do" is a question about the hero, not about whose turn came first.
     */
    fun observeByHero(): Flow<List<WinRateRow>> =
        playDao.observeStatsRows().map { PlayStats.heroes(it) }

    /**
     * Hero and aspect as one key, which is the pairing a player asks about.
     *
     * Only combinations played more than once are kept: a table full of
     * one-game 100% rows tells nobody anything and buries the rows that do.
     */
    fun observeByHeroAspect(): Flow<List<WinRateRow>> =
        playDao.observeStatsRows().map { PlayStats.heroAspects(it) }

    /** Aspects split out of the roster and counted per aspect. */
    fun observeByAspect(): Flow<List<WinRateRow>> =
        playDao.observeStatsRows().map { PlayStats.aspects(it) }

    fun newPlayId(): String = UUID.randomUUID().toString()

    /**
     * Saves a play, and reports it if the player has asked for that.
     *
     * Saving always happens first and never depends on the network: a recorded
     * game is the player's own history, and losing it because BoardGameGeek was
     * unreachable would be indefensible.
     */
    suspend fun record(play: PlayEntity): PlayRecorded = withContext(ioDispatcher) {
        // Stamped here rather than at the two call sites, because this is the
        // one door every finished game comes through — a timed game and a
        // campaign scenario both end up here — and a play that missed its
        // location because one caller forgot would be invisible until it
        // reached BoardGameGeek.
        val located = if (play.location.isBlank()) {
            play.copy(location = preferences.currentPlayLocation())
        } else {
            play
        }
        val stamped = located.copy(updatedAt = System.currentTimeMillis())
        playDao.insert(stamped)
        syncStateDao.markDirty(SyncCollection.PLAYS.key, stamped.id)

        when (bggAccount.currentMode()) {
            BggReportingMode.OFF -> PlayRecorded.SavedOnly
            BggReportingMode.ASK -> PlayRecorded.SavedAskToReport
            BggReportingMode.ALWAYS -> report(stamped.id)
        }
    }

    /**
     * Sends a saved play to BoardGameGeek.
     *
     * Only marked as reported on success, so a failure leaves it sendable
     * rather than silently dropped.
     */
    suspend fun report(playId: String): PlayRecorded = withContext(ioDispatcher) {
        val play = playDao.getPlay(playId) ?: return@withContext PlayRecorded.SavedOnly
        if (play.reportedToBgg) {
            return@withContext PlayRecorded.SavedAndReported
        }

        val credentials = bggAccount.credentialsForReporting()
            ?: return@withContext PlayRecorded.SavedOnly

        val result = bggClient.reportPlay(
            username = credentials.first,
            password = credentials.second,
            play = play.toBggPlay(credentials.first),
        )

        when (result) {
            is BggResult.Success -> {
                playDao.markReported(play.id, System.currentTimeMillis())
                syncStateDao.markDirty(SyncCollection.PLAYS.key, play.id)
                PlayRecorded.SavedAndReported
            }

            is BggResult.BadCredentials ->
                PlayRecorded.SavedReportFailed("BoardGameGeek rejected the saved credentials")

            is BggResult.Rejected -> PlayRecorded.SavedReportFailed(result.detail)
            is BggResult.Offline -> PlayRecorded.SavedReportFailed(result.detail)
        }
    }

    /**
     * Removes a play from the history.
     *
     * A tombstone rather than a removal: the photographs it names are swept
     * separately, and a second device that has not heard about this would
     * otherwise put the game back.
     */
    suspend fun delete(playId: String) = withContext(ioDispatcher) {
        playDao.delete(playId, System.currentTimeMillis())
        syncStateDao.markDirty(SyncCollection.PLAYS.key, playId)
    }
}
