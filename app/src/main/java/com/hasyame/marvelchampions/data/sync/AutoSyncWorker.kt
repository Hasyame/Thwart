package com.hasyame.marvelchampions.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * One unattended run of [SyncEngine.sync].
 *
 * Nothing here decides *when*: [AutoSync] does that. This decides what to do
 * when it does not work, and the answer is always quiet. There is no
 * notification, no dialogue and no error on any screen. The account page
 * already says when the last sync was and how much is waiting, which is the
 * honest report, and a player who has told the app to do this in the background
 * has said they do not want to hear about it.
 *
 * The preference is read again here rather than trusted from the moment the job
 * was queued. A job can sit for hours waiting for a network, and sync may have
 * been switched off, or this device signed out, in between.
 */
@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val sessions: SyncSessionStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!sessions.autoSyncArmed.first()) {
            // Not a failure: the answer to "should this run" simply changed
            // while it was queued.
            return Result.success()
        }
        return try {
            val outcome = engine.sync()
            // Something was written while this run was in flight, so it was not
            // in the set that went up. A retry rather than a second job: unique
            // work cannot enqueue itself while it is running, and the backoff is
            // exactly the short wait wanted here. Guarded on having pushed
            // something, so a record the server will never accept cannot turn
            // this into a loop.
            if (outcome.pushed > 0 && runAttemptCount + 1 < MAX_ATTEMPTS &&
                engine.pendingCount() > 0
            ) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (refused: SyncException) {
            if (refused.isWorthRetrying && runAttemptCount + 1 < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (_: Exception) {
            // A database or a serialisation fault, which trying again will not
            // mend. It is caught rather than left to escape because this runs
            // unattended: the run is over either way, and the difference is
            // whether it is over quietly or as a report of a crash nobody saw.
            Result.failure()
        }
    }

    /**
     * Whether trying again could plausibly work.
     *
     * Signed out or refused credentials will not fix itself, and retrying it
     * only spends battery on a request that is already answered. Everything
     * else here is a network or a server having a bad minute.
     */
    private val SyncException.isWorthRetrying: Boolean
        get() = when (code) {
            SyncException.OFFLINE,
            SyncException.SERVER_ERROR,
            SyncException.RATE_LIMITED,
            -> true

            else -> false
        }

    private companion object {
        /** Attempts, including the first. Past this it waits for a real trigger. */
        const val MAX_ATTEMPTS = 4
    }
}
