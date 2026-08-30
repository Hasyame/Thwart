package com.hasyame.marvelchampions.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What the Settings screen shows about an in-flight or finished refresh. */
sealed interface CardSyncState {
    data object Idle : CardSyncState
    data class Running(val step: String?, val locale: String?) : CardSyncState
    data object Succeeded : CardSyncState
    data class Failed(val message: String?) : CardSyncState
    data object Cancelled : CardSyncState
}

/**
 * Starts, cancels and observes the card refresh. Manual only — the app never
 * syncs on its own.
 */
@Singleton
class CardSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val workManager get() = WorkManager.getInstance(context)

    fun start() {
        val request = OneTimeWorkRequestBuilder<CardSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        // KEEP rather than REPLACE: pressing the button twice should not
        // restart a download that is already half done.
        workManager.enqueueUniqueWork(CardSyncWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(CardSyncWorker.NAME)
    }

    fun observeState(): Flow<CardSyncState> =
        workManager.getWorkInfosForUniqueWorkFlow(CardSyncWorker.NAME).map { infos ->
            // WorkManager keeps the record of finished runs alongside the live
            // one and promises nothing about their order, so the last entry can
            // be last week's failure while today's download is running. A run
            // still in flight is always the one worth reporting; only when
            // nothing is pending does an outcome describe the present.
            val current = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
            when (val info = current) {
                null -> CardSyncState.Idle
                else -> when (info.state) {
                    WorkInfo.State.RUNNING -> CardSyncState.Running(
                        step = info.progress.getString(CardSyncWorker.KEY_STEP),
                        locale = info.progress.getString(CardSyncWorker.KEY_LOCALE),
                    )

                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        CardSyncState.Running(step = null, locale = null)

                    WorkInfo.State.SUCCEEDED -> CardSyncState.Succeeded
                    WorkInfo.State.CANCELLED -> CardSyncState.Cancelled
                    WorkInfo.State.FAILED -> CardSyncState.Failed(
                        info.outputData.getString(CardSyncWorker.KEY_ERROR),
                    )
                }
            }
        }
}
