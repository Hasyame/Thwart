package com.hasyame.marvelchampions.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hasyame.marvelchampions.data.security.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Who this device is signed in as, and how far through the account it has read.
 *
 * Its own store rather than a corner of [com.hasyame.marvelchampions.data.settings.AppPreferences],
 * for one reason that matters: a backup file is written from the preferences and
 * handed to other people. A bearer token has no business travelling in one, and
 * the surest way to keep it out is to keep it somewhere the exporter cannot
 * reach.
 *
 * The token itself is encrypted through [SecretStore], the same Keystore-backed
 * path as the BoardGameGeek password. That key does not survive a device
 * restore, so decryption returning null is the ordinary outcome of restoring a
 * phone from a backup — the device is simply signed out and signs in again,
 * which is the right answer for a credential that should not have travelled.
 */
private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync_session")

/** What the app knows about the account on this device. */
data class SyncSession(
    val instanceUrl: String = SyncEndpoints.DEFAULT_BASE_URL,
    val accountId: String = "",
    val handle: String = "",
    /**
     * The address on the account, or empty.
     *
     * Empty for an account made before addresses existed, and from a server
     * that does not send one. It is not synced as a record: it belongs to the
     * account rather than to the data, and the server is the only copy that
     * matters.
     */
    val email: String = "",
    /**
     * Null when signed out, and also when the stored ciphertext could not be
     * read — after a device restore, say. The two are the same thing to every
     * caller: there is no usable credential, so ask for one.
     */
    val token: String? = null,
    /**
     * False until the user turns sync on, which is a separate act from signing
     * in. Signing in answers *who are you*; this answers *should this device
     * stay in step*. Firing a merge at somebody who only wanted to sign in is
     * how a feature earns a reputation before it has done anything.
     */
    val enabled: Boolean = false,
    /** The highest revision this device has read. Zero means nothing yet. */
    val cursor: Long = 0,
    /** When the account's recovery code was issued, RFC 3339, for the reminder. */
    val recoveryIssuedAt: String = "",
    /** Epoch millis of the last completed sync, or zero. */
    val lastSyncedAt: Long = 0,
    /**
     * The id of a batch that was sent and never confirmed.
     *
     * Blank almost always. When it is set, the next push reuses it for its
     * first batch rather than minting a new one: the server keeps
     * `(account, batchId)` and its response for 24 hours, so a batch that in
     * fact arrived and whose reply was lost replays instead of applying twice.
     * A fresh id on a retry is precisely the duplicate this exists to prevent.
     */
    val inFlightBatchId: String = "",
) {
    val isSignedIn: Boolean get() = !token.isNullOrBlank() && accountId.isNotBlank()

    /** The header value, or null when there is nothing to send. */
    val authorization: String? get() = token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
}

@Singleton
class SyncSessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secrets: SecretStore,
) {

    val session: Flow<SyncSession> = context.syncStore.data.map { preferences ->
        SyncSession(
            instanceUrl = preferences[KEY_INSTANCE].orEmpty()
                .ifBlank { SyncEndpoints.DEFAULT_BASE_URL },
            accountId = preferences[KEY_ACCOUNT_ID].orEmpty(),
            handle = preferences[KEY_HANDLE].orEmpty(),
            email = preferences[KEY_EMAIL].orEmpty(),
            token = preferences[KEY_TOKEN]?.let { secrets.decrypt(it) },
            enabled = preferences[KEY_ENABLED] == true,
            cursor = preferences[KEY_CURSOR] ?: 0,
            recoveryIssuedAt = preferences[KEY_RECOVERY_ISSUED].orEmpty(),
            lastSyncedAt = preferences[KEY_LAST_SYNCED] ?: 0,
            inFlightBatchId = preferences[KEY_IN_FLIGHT].orEmpty(),
        )
    }

    suspend fun current(): SyncSession = session.first()

    /**
     * Records a sign-in.
     *
     * Deliberately does not touch [SyncSession.enabled] or the cursor: signing
     * in is not switching sync on, and the cursor belongs to the account rather
     * than to the act of authenticating. Signing in to a *different* account
     * does reset the cursor, since a revision number from one account means
     * nothing in another.
     */
    suspend fun signedIn(response: AuthResponseDto) {
        val encrypted = secrets.encrypt(response.token)
        context.syncStore.edit { preferences ->
            val previous = preferences[KEY_ACCOUNT_ID]
            preferences[KEY_ACCOUNT_ID] = response.accountId
            preferences[KEY_HANDLE] = response.handle
            if (response.email.isBlank()) {
                preferences.remove(KEY_EMAIL)
            } else {
                preferences[KEY_EMAIL] = response.email
            }
            if (encrypted != null) {
                preferences[KEY_TOKEN] = encrypted
            } else {
                preferences.remove(KEY_TOKEN)
            }
            preferences[KEY_RECOVERY_ISSUED] = response.recoveryCodeIssuedAt
            if (previous != null && previous != response.accountId) {
                preferences.remove(KEY_CURSOR)
                preferences.remove(KEY_LAST_SYNCED)
                preferences[KEY_ENABLED] = false
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.syncStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setInstanceUrl(url: String) {
        context.syncStore.edit { preferences ->
            val cleaned = url.trim().trimEnd('/')
            if (cleaned.isBlank() || cleaned == SyncEndpoints.DEFAULT_BASE_URL) {
                preferences.remove(KEY_INSTANCE)
            } else {
                preferences[KEY_INSTANCE] = cleaned
            }
        }
    }

    /**
     * Stores how far this device has read.
     *
     * Only ever moves forward. A pull that returns nothing hands back the
     * cursor it was given, and a stale write from a slow request must not drag
     * the device backwards into re-reading what it has already merged.
     */
    suspend fun setCursor(cursor: Long) {
        context.syncStore.edit { preferences ->
            val known = preferences[KEY_CURSOR] ?: 0
            if (cursor > known) {
                preferences[KEY_CURSOR] = cursor
            }
        }
    }

    /** Puts the cursor back to zero, for a full resync. */
    suspend fun resetCursor() {
        context.syncStore.edit { it.remove(KEY_CURSOR) }
    }

    suspend fun recordSync(at: Long) {
        context.syncStore.edit { it[KEY_LAST_SYNCED] = at }
    }

    /** Remembers a batch id before it is sent, so a retry can reuse it. */
    suspend fun beginBatch(batchId: String) {
        context.syncStore.edit { it[KEY_IN_FLIGHT] = batchId }
    }

    /** Forgets it, once the server has answered. */
    suspend fun endBatch() {
        context.syncStore.edit { it.remove(KEY_IN_FLIGHT) }
    }

    /**
     * Forgets the account. **Local data is not touched.**
     *
     * Signing out returns the device to being anonymous with everything intact.
     * Erasing local data is a separate, clearly labelled action for a shared
     * phone, never a side effect of this one.
     *
     * The instance URL survives, because it describes which server this
     * household uses rather than who is using it, and making somebody retype it
     * to sign back in helps nobody.
     */
    suspend fun signOut() {
        context.syncStore.edit { preferences ->
            preferences.remove(KEY_ACCOUNT_ID)
            preferences.remove(KEY_HANDLE)
            preferences.remove(KEY_EMAIL)
            preferences.remove(KEY_TOKEN)
            preferences.remove(KEY_CURSOR)
            preferences.remove(KEY_RECOVERY_ISSUED)
            preferences.remove(KEY_LAST_SYNCED)
            preferences.remove(KEY_IN_FLIGHT)
            preferences[KEY_ENABLED] = false
        }
    }

    private companion object {
        val KEY_INSTANCE = stringPreferencesKey("instance_url")
        val KEY_ACCOUNT_ID = stringPreferencesKey("account_id")
        val KEY_HANDLE = stringPreferencesKey("handle")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_CURSOR = longPreferencesKey("cursor")
        val KEY_RECOVERY_ISSUED = stringPreferencesKey("recovery_issued_at")
        val KEY_LAST_SYNCED = longPreferencesKey("last_synced_at")
        val KEY_IN_FLIGHT = stringPreferencesKey("in_flight_batch")
    }
}
