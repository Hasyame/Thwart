package com.hasyame.marvelchampions.data.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What syncing does when the app is on screen, and what it stops doing when it
 * is not.
 *
 * Two things happen on becoming visible, and they are not the same thing twice.
 *
 * **A catch-up is asked for.** The six other triggers all fire on a *write*:
 * this device telling the account about something the player just did. None of
 * them covers the opposite and more common event, which is somebody picking the
 * phone up after the tablet has moved the campaign on. A device with no change
 * of its own never asked, so it learned about anybody else's change only when
 * it happened to have one. It goes through WorkManager like the others, and
 * that is the point of keeping it even though the stream below also catches up:
 * a WorkManager job outlives the app being closed and waits for a network to
 * come back, and those are exactly the cases where the stream cannot help.
 * When both do run, the second finds nothing and costs one empty round trip.
 *
 * **The live channel opens**, and closes again on leaving. Foreground only, and
 * that is a deliberate limit rather than an unfinished one: a socket held open
 * behind a dozing app buys a few seconds of latency for a background cost a
 * card game companion has no business asking for, and the foreground service it
 * would need is worse — a permanent notification, and a Play Store
 * justification nobody would enjoy writing.
 *
 * [ProcessLifecycleOwner] rather than an Activity: it fires once when the app
 * becomes visible and not again on a rotation, which is exactly the question
 * being asked, and it outlives the Activity, so a configuration change or a
 * process death cannot leave this half-registered.
 *
 * **Nothing here happens for a player with no account.** Both paths are gated
 * on the same single preference read the write triggers use: no network, no
 * database, nothing on the main thread, and no socket.
 */
@Singleton
class ForegroundSync @Inject constructor(
    private val autoSync: AutoSync,
    private val stream: SyncStream,
) : DefaultLifecycleObserver {

    /**
     * Application-scoped, and deliberately not tied to any screen.
     *
     * It lives long enough to read a preference and enqueue a job, so it holds
     * nothing that could keep a screen alive.
     */
    private val scope = CoroutineScope(SupervisorJob())

    /** Called once from the Application, before any screen exists. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { autoSync.after(SyncTrigger.RETURNED_TO_APP) }
        stream.connect()
    }

    override fun onStop(owner: LifecycleOwner) {
        stream.disconnect()
    }
}
