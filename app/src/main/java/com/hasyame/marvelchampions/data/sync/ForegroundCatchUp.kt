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
 * Asking what happened while the app was away.
 *
 * The six existing triggers all fire on a *write*: the app tells the account
 * about something the player just did. None of them fires on the opposite and
 * more common event — the player picks up the phone, and the tablet has moved
 * the campaign on since. Without this, a device only learns about somebody
 * else's change the next time it has a change of its own, which is the wrong
 * way round and is most of the staleness anybody actually notices.
 *
 * [ProcessLifecycleOwner] rather than an Activity: it fires once when the app
 * becomes visible and not again on a rotation, which is exactly the question
 * being asked. It also survives the Activity, so a configuration change or a
 * process death cannot leave this half-registered.
 *
 * **Nothing happens for a player with no account.** The trigger goes through
 * [AutoSync], which reads one preference and returns when the three conditions
 * are not met — no network call, no database read, nothing on the main thread.
 * That is the whole of the anonymous path.
 */
@Singleton
class ForegroundCatchUp @Inject constructor(
    private val autoSync: AutoSync,
) : DefaultLifecycleObserver {

    /**
     * Application-scoped, and deliberately not tied to any screen.
     *
     * The work itself is a WorkManager job; this scope only lives long enough
     * to read a preference and enqueue it, so it never holds anything.
     */
    private val scope = CoroutineScope(SupervisorJob())

    /** Called once from the Application, before any screen exists. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { autoSync.after(SyncTrigger.RETURNED_TO_APP) }
    }
}
