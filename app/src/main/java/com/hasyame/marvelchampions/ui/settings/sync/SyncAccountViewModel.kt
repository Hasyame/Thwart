package com.hasyame.marvelchampions.ui.settings.sync

import android.content.Context
import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.backup.BackupRepository
import com.hasyame.marvelchampions.data.backup.BackupResult
import com.hasyame.marvelchampions.data.sync.AdoptionCounts
import com.hasyame.marvelchampions.data.sync.AdoptionPlan
import com.hasyame.marvelchampions.data.sync.DeviceDto
import com.hasyame.marvelchampions.data.sync.SyncClient
import com.hasyame.marvelchampions.data.sync.SyncEndpoints
import com.hasyame.marvelchampions.data.sync.SyncEngine
import com.hasyame.marvelchampions.data.sync.SyncException
import com.hasyame.marvelchampions.data.sync.SyncSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A line of feedback.
 *
 * Usually one of the app's own strings. Sometimes the server's: it writes its
 * refusals in English and French and picks by `Accept-Language`, and for the
 * password rule that is the only correct source. Two copies of a password
 * policy is two policies, and the one on the phone would be the stale one.
 */
sealed interface SyncMessage {
    data class Resource(
        @param:StringRes val text: Int,
        val args: List<Any> = emptyList(),
    ) : SyncMessage

    /**
      * A count, which French and English both inflect.
      *
      * Its own shape rather than a string with a number in it: "1 decks" is the
      * kind of thing that makes an app look machine-made, and the plural rules
      * are the platform's to know rather than this file's.
      */
    data class Plural(@param:PluralsRes val id: Int, val count: Int) : SyncMessage

    /** What the server said, in the reader's language. */
    data class FromServer(val text: String) : SyncMessage
}

data class SyncAccountUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val instanceUrl: String = SyncEndpoints.DEFAULT_BASE_URL,
    val registrationOpen: Boolean = false,
    val instanceReachable: Boolean? = null,
    val signedIn: Boolean = false,
    val handle: String = "",
    val email: String = "",
    val enabled: Boolean = false,
    val pending: Int = 0,
    val lastSyncedAt: Long = 0,
    val devices: List<DeviceDto> = emptyList(),
    val message: SyncMessage? = null,
    /**
     * Shown once and never again, because the server keeps only its hash. The
     * screen refuses to move on until it has been acknowledged.
     */
    val recoveryCode: String? = null,
    /** Set while the merge question is on screen. Nothing has been written. */
    val adoption: AdoptionCounts? = null,
)

/**
 * The account screen's state.
 *
 * Two rules from the design shape almost everything here:
 *
 * **Signing in is not switching sync on.** Signing in answers *who are you* and
 * stops. The switch answers *should this device stay in step*, and it is the
 * switch that asks the merge question. Firing a merge dialogue at somebody who
 * only wanted to sign in is how a feature earns a reputation before it has done
 * anything.
 *
 * **Nothing is written before the question is answered.** [setEnabled] plans the
 * merge and shows the counts; the plan holds the records it counted, so
 * answering yes applies exactly what was described rather than whatever a second
 * pull would have found.
 *
 * Every write to [state] goes through [update], which is a compare-and-set
 * rather than an assignment. That is not fussiness: the session collector and a
 * running action both read this state, change one field and write it back, and
 * with plain assignment the slower of the two puts back the field the other had
 * just cleared. It showed up as the merge dialogue staying on screen after the
 * merge had finished, and it would have shown up again anywhere else two things
 * touched the state at once.
 */
@HiltViewModel
class SyncAccountViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: SyncClient,
    private val engine: SyncEngine,
    private val sessions: SyncSessionStore,
    private val backups: BackupRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val state = MutableStateFlow(SyncAccountUiState())
    val uiState: StateFlow<SyncAccountUiState> = state.asStateFlow()

    /** Held between the question and the answer. Never written to disk. */
    private var plan: AdoptionPlan? = null

    private fun update(change: SyncAccountUiState.() -> SyncAccountUiState) {
        state.update { it.change() }
    }

    init {
        viewModelScope.launch {
            sessions.session.collect { session ->
                update {
                    copy(
                        loading = false,
                        instanceUrl = session.instanceUrl,
                        signedIn = session.isSignedIn,
                        handle = session.handle,
                        email = session.email,
                        enabled = session.enabled,
                        lastSyncedAt = session.lastSyncedAt,
                    )
                }
                refreshPending()
            }
        }
        viewModelScope.launch { checkInstance() }
    }

    private suspend fun refreshPending() {
        val waiting = runCatching { engine.pendingCount() }.getOrDefault(0)
        update { copy(pending = waiting) }
    }

    /** Asks the instance what it is, so a form is not offered that would be refused. */
    fun checkInstance(url: String? = null) {
        viewModelScope.launch {
            val version = runCatching { client.version(url) }.getOrNull()
            update {
                copy(
                    registrationOpen = version?.registrationOpen == true,
                    instanceReachable = version != null,
                )
            }
        }
    }

    fun setInstanceUrl(url: String) {
        viewModelScope.launch {
            sessions.setInstanceUrl(url)
            checkInstance(url)
        }
    }

    // --- accounts -----------------------------------------------------------

    fun register(handle: String, email: String, password: String) = attempt {
        val response = client.register(handle.trim(), email.trim(), password)
        update { copy(recoveryCode = response.recoveryCode) }
    }

    /** [identifier] is whatever was typed: an address, or an older pseudonym. */
    fun signIn(identifier: String, password: String) = attempt {
        client.login(identifier.trim(), password)
    }

    fun recover(identifier: String, code: String, newPassword: String) = attempt {
        val response = client.recover(identifier.trim(), code.trim(), newPassword)
        update { copy(recoveryCode = response.recoveryCode) }
    }

    fun changePassword(current: String, replacement: String) = attempt {
        client.changePassword(current, replacement)
        update { copy(message = SyncMessage.Resource(R.string.sync_change_password)) }
    }

    fun acknowledgeRecoveryCode() {
        update { copy(recoveryCode = null) }
    }

    /** Writes the recovery code to a file the user picked. */
    fun saveRecoveryCode(destination: Uri) {
        val code = state.value.recoveryCode ?: return
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching {
                    context.contentResolver.openOutputStream(destination)?.use {
                        it.write(code.toByteArray())
                    }
                }
            }
        }
    }

    fun loadDevices() = attempt {
        val devices = client.devices()
        update { copy(devices = devices) }
    }

    fun revokeDevice(id: String) = attempt {
        client.revokeDevice(id)
        val devices = client.devices()
        update { copy(devices = devices) }
    }

    /**
     * Signs out. **Local data is untouched**, and this must never be the thing
     * that deletes it: the device simply becomes anonymous again with
     * everything intact.
     */
    fun signOut() {
        viewModelScope.launch {
            sessions.signOut()
            engine.forgetSyncState()
            update { copy(devices = emptyList(), adoption = null) }
        }
    }

    fun deleteAccount(password: String) = attempt {
        client.deleteAccount(password)
        sessions.signOut()
        engine.forgetSyncState()
    }

    // --- the switch ---------------------------------------------------------

    /**
     * Turning sync on asks first, and turning it off is instant.
     *
     * The question is the whole point of the switch: it is where somebody with
     * two years of play history finds out what merging would mean, in their own
     * terms, while nothing has yet been written.
     */
    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch { sessions.setEnabled(false) }
            return
        }
        attempt {
            val prepared = engine.planAdoption()
            plan = prepared
            update { copy(adoption = prepared.counts) }
        }
    }

    fun cancelAdoption() {
        plan = null
        update { copy(adoption = null) }
    }

    fun merge() = attempt {
        val prepared = plan ?: return@attempt
        val outcome = engine.adoptMerging(prepared)
        sessions.setEnabled(true)
        plan = null
        update {
            copy(
                adoption = null,
                message = SyncMessage.Resource(
                    R.string.sync_result,
                    listOf(outcome.pulled, outcome.pushed),
                ),
            )
        }
    }

    /**
     * Takes the account's data and discards this phone's, having written it to a
     * backup file first.
     *
     * The export is not a courtesy. It is what makes the destructive answer
     * recoverable, and the engine refuses to run without it.
     */
    fun keepServerOnly(exportTo: Uri) = attempt {
        val prepared = plan ?: return@attempt
        val exported = backups.export(exportTo, includePhotos = true)
        if (exported !is BackupResult.Exported) {
            update { copy(message = SyncMessage.Resource(R.string.sync_error_server)) }
            return@attempt
        }
        val outcome = engine.adoptKeepingServerOnly(prepared, exported = true)
        sessions.setEnabled(true)
        plan = null
        update {
            copy(
                adoption = null,
                message = SyncMessage.Resource(R.string.sync_result, listOf(outcome.pulled, 0)),
            )
        }
    }

    // --- syncing ------------------------------------------------------------

    fun syncNow() = attempt {
        val outcome = engine.sync()
        val result = when {
            outcome.incomplete -> SyncMessage.Resource(R.string.sync_error_incomplete)

            outcome.forkedDecks.isNotEmpty() -> SyncMessage.Plural(
                R.plurals.sync_result_forked,
                outcome.forkedDecks.size,
            )

            outcome.fullResync -> SyncMessage.Resource(R.string.sync_error_cursor_too_old)

            else -> SyncMessage.Resource(
                R.string.sync_result,
                listOf(outcome.pulled, outcome.pushed),
            )
        }
        update { copy(message = result) }
    }

    fun dismissMessage() {
        update { copy(message = null) }
    }

    /**
     * Runs one thing, reporting whatever it refuses with.
     *
     * Every failure that reaches a person here is a server code translated by
     * the app rather than a sentence written by a program that does not know
     * what the data is.
     */
    private fun attempt(block: suspend () -> Unit) {
        viewModelScope.launch {
            update { copy(busy = true, message = null) }
            try {
                block()
            } catch (refused: SyncException) {
                update { copy(message = refused.asMessage()) }
            } finally {
                update { copy(busy = false) }
                refreshPending()
            }
        }
    }
}

/**
 * What to show for a refusal.
 *
 * The server's own words for the four it validates, because those are the ones
 * whose rules live there — the password rule especially, which the client is
 * told not to re-implement. The app's own words for everything else, which read
 * better than a sentence written by a program that does not know what the data
 * is.
 */
fun SyncException.asMessage(): SyncMessage = when {
    code in SERVER_KNOWS_BEST && serverMessage.isNotBlank() -> SyncMessage.FromServer(serverMessage)
    else -> SyncMessage.Resource(messageRes())
}

private val SERVER_KNOWS_BEST = setOf(
    SyncException.WEAK_PASSWORD,
    SyncException.INVALID_EMAIL,
    SyncException.INVALID_HANDLE,
    SyncException.EMAIL_TAKEN,
)

/** The app's own words for a server code. */
@StringRes
fun SyncException.messageRes(): Int = when (code) {
    SyncException.UNAUTHORIZED -> R.string.sync_error_unauthorized
    SyncException.INVALID_CREDENTIALS -> R.string.sync_error_invalid_credentials
    SyncException.HANDLE_TAKEN -> R.string.sync_error_handle_taken
    SyncException.INVALID_HANDLE -> R.string.sync_error_invalid_handle
    SyncException.INVALID_EMAIL -> R.string.sync_error_invalid_email
    SyncException.EMAIL_TAKEN -> R.string.sync_error_email_taken
    SyncException.WEAK_PASSWORD -> R.string.sync_error_weak_password
    SyncException.INVALID_RECOVERY_CODE -> R.string.sync_error_invalid_recovery_code
    SyncException.REGISTRATION_CLOSED -> R.string.sync_registration_closed
    SyncException.CURSOR_TOO_OLD -> R.string.sync_error_cursor_too_old
    SyncException.RATE_LIMITED -> R.string.sync_error_rate_limited
    SyncException.OFFLINE -> R.string.sync_error_offline
    else -> R.string.sync_error_server
}
