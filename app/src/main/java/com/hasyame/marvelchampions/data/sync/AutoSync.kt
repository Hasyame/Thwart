package com.hasyame.marvelchampions.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private const val LEAVING_THE_TABLE = 3L
private const val TINKERING = 15L

/**
 * A moment worth syncing at, and how long to wait before doing it.
 *
 * The wait is not a delay for its own sake: it is what turns a burst into one
 * sync. Ticking twenty packs off a collection list is twenty writes in ten
 * seconds, and twenty pushes would be twenty round trips to say what one says.
 *
 * The two waits are different because the moments are different. Leaving the
 * table means somebody may pick up a tablet in a minute and expect to carry on,
 * so those go almost at once. Tinkering with a collection or starring cards is
 * done in handfuls and nobody is waiting on the other end, so those settle
 * first.
 */
enum class SyncTrigger(internal val settleSeconds: Long) {
    /** A scenario ended, standalone or inside a campaign. */
    SCENARIO_FINISHED(LEAVING_THE_TABLE),

    /** A campaign was seen through to the end. */
    CAMPAIGN_FINISHED(LEAVING_THE_TABLE),

    /** A game was filed away to be come back to. */
    LONG_BREAK(LEAVING_THE_TABLE),

    /**
     * The app came to the foreground.
     *
     * The one trigger that is not a write. It asks what happened elsewhere
     * rather than announcing what happened here, which is the direction the
     * other six cannot cover: a phone with nothing new of its own would
     * otherwise never ask.
     */
    RETURNED_TO_APP(LEAVING_THE_TABLE),

    DECK_ADDED(TINKERING),
    COLLECTION_CHANGED(TINKERING),
    CARD_FAVOURITED(TINKERING),
}

/**
 * Syncing without being asked, at the handful of moments where it is wanted.
 *
 * Off unless the player turns it on, and inert unless sync itself is on and
 * this device is signed in. [after] is safe to call from anywhere that writes
 * something: when the conditions are not met it reads one preference and
 * returns, and it never throws, never blocks and never reports anything to the
 * screen. An automatic sync that interrupts is worse than no automatic sync.
 *
 * The work goes through WorkManager rather than a coroutine for the reason the
 * feature exists at all: three of the six moments are somebody putting the
 * phone down and leaving. A coroutine dies with the screen that started it; a
 * WorkManager job survives the app being closed, and waits for a network
 * instead of failing without one.
 */
@Singleton
class AutoSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessions: SyncSessionStore,
) {

    /**
     * Asks for a sync shortly after [trigger], if this device does that.
     *
     * Suspending so it can read the preference itself: every caller is already
     * in a coroutine, and a fire-and-forget version would need a scope of its
     * own and would enqueue a job for the majority of players who have never
     * signed in.
     */
    suspend fun after(trigger: SyncTrigger) {
        // Nothing here may break the write that called it. Starring a card has
        // to keep working on a phone whose preference file has gone bad, and
        // the honest answer to "should this sync" when the preferences cannot
        // be read is no.
        val armed = try {
            sessions.autoSyncArmed.first()
        } catch (_: IOException) {
            false
        }
        if (armed) {
            enqueue(trigger.settleSeconds)
        }
    }

    private fun enqueue(settleSeconds: Long) {
        val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(settleSeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // KEEP, so a burst of writes produces one sync rather than one each.
        // The job already queued will send whatever is dirty by the time it
        // runs, so dropping the later triggers loses nothing; the only cost is
        // that the first trigger's wait is the one that applies, which at worst
        // makes a prompt moment wait the longer of the two.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }

    internal companion object {
        /** The unique work name, so two triggers cannot become two syncs. */
        const val NAME: String = "auto-sync"
    }
}
