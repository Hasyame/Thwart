package com.hasyame.marvelchampions.data.sync

import android.os.Build
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The transport: one call per endpoint, with the token attached and every
 * refusal turned into a [SyncException].
 *
 * Everything above this layer works in terms of the app's own types and the
 * server's error codes. Nothing above it sees an HTTP status, a `Response`, or
 * a URL.
 *
 * Two failures are deliberately made to look the same to callers and different
 * to the user: a server that refused, which carries a code worth acting on, and
 * a request that never arrived, which is [SyncException.OFFLINE] and means try
 * again later rather than anything is wrong.
 */
@Singleton
class SyncClient @Inject constructor(
    private val api: SyncApi,
    private val sessions: SyncSessionStore,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** The name this phone appears under in the account's device list. */
    private val deviceName: String
        get() = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android" }
            .take(64)

    /**
     * What the reader reads in, for the server's own error messages.
     *
     * The device's language rather than the card language: these are the app's
     * own words, not a card's.
     */
    private val language: String
        get() = Locale.getDefault().toLanguageTag()

    private suspend fun endpoints(): SyncEndpoints =
        SyncEndpoints(sessions.current().instanceUrl)

    private suspend fun authorization(): String =
        sessions.current().authorization
            ?: throw SyncException(SyncException.UNAUTHORIZED)

    // --- instance -----------------------------------------------------------

    /**
     * What the instance says about itself.
     *
     * Read before the account screen offers a registration form, and again for
     * the batch limits, which are published rather than hardcoded so a client
     * does not have to guess and an instance can change its mind.
     */
    suspend fun version(instanceUrl: String? = null): VersionDto = call {
        val base = instanceUrl?.takeIf { it.isNotBlank() } ?: sessions.current().instanceUrl
        api.version(SyncEndpoints(base).version)
    }

    // --- accounts -----------------------------------------------------------

    suspend fun register(handle: String, email: String, password: String): AuthResponseDto {
        val response = call {
            api.register(
                endpoints().register,
                language,
                RegisterDto(handle.trim(), email.trim(), password, deviceName),
            )
        }
        sessions.signedIn(response)
        return response
    }

    /** [identifier] is whatever was typed: an address, or an older pseudonym. */
    suspend fun login(identifier: String, password: String): AuthResponseDto {
        val response = call {
            api.login(
                endpoints().login,
                language,
                SignInDto(identifier.trim(), password, deviceName),
            )
        }
        sessions.signedIn(response)
        return response
    }

    suspend fun recover(
        identifier: String,
        recoveryCode: String,
        newPassword: String,
    ): AuthResponseDto {
        val response = call {
            api.recover(
                endpoints().recover,
                language,
                RecoverDto(identifier.trim(), recoveryCode, newPassword, deviceName),
            )
        }
        sessions.signedIn(response)
        return response
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        callEmpty {
            api.changePassword(
                endpoints().password,
                authorization(),
                ChangePasswordDto(currentPassword, newPassword),
            )
        }
    }

    suspend fun devices(): List<DeviceDto> =
        call { api.devices(endpoints().devices, authorization()) }.devices

    suspend fun revokeDevice(id: String) {
        callEmpty { api.deleteDevice(endpoints().device(id), authorization()) }
    }

    /**
     * Deletes the account and everything in it, on the server.
     *
     * Real, and irreversible. Local data is untouched, so the phone keeps
     * everything and simply stops having anywhere to sync it to.
     *
     * The password is asked for again because a device token can be stolen and
     * this is the one call that cannot be undone.
     */
    suspend fun deleteAccount(password: String) {
        callEmpty {
            api.deleteAccount(endpoints().account, authorization(), DeleteAccountDto(password))
        }
    }

    // --- sync ---------------------------------------------------------------

    /** [resync] marks a page of a rebuild that started at revision zero. */
    suspend fun pull(since: Long, limit: Int, resync: Boolean = false): PullResponseDto = call {
        api.pull(endpoints().changes, authorization(), since, limit, resync)
    }

    suspend fun push(batchId: String, records: List<PushRecordDto>): PushResponseDto = call {
        api.push(endpoints().changes, authorization(), PushRequestDto(batchId, records))
    }

    // --- plumbing -----------------------------------------------------------

    /**
     * Runs a call and returns its body, or throws.
     *
     * A successful response with no body is a server that has gone wrong: every
     * call routed through here is typed as returning something. The endpoints
     * that genuinely answer with nothing go through [callEmpty] instead, so the
     * missing body is never quietly accepted where one was expected.
     */
    private suspend fun <T : Any> call(request: suspend () -> Response<T>): T {
        val response = attempt(request)
        return response.body()
            ?: throw SyncException(SyncException.SERVER_ERROR, "empty body", response.code())
    }

    /** The same, for the endpoints whose success is the status alone. */
    private suspend fun callEmpty(request: suspend () -> Response<Unit>) {
        attempt(request)
    }

    private suspend fun <T> attempt(request: suspend () -> Response<T>): Response<T> =
        withContext(ioDispatcher) {
            val response = try {
                request()
            } catch (offline: IOException) {
                throw SyncException(SyncException.OFFLINE, cause = offline)
            }
            if (!response.isSuccessful) {
                throw response.toException(json)
            }
            response
        }
}

/**
 * Turns a refusal into the code the client acts on.
 *
 * The envelope is parsed leniently on purpose: a proxy, a captive portal or a
 * misconfigured instance can return HTML with a 502, and that must arrive as a
 * plain server error rather than as a parse exception with no code at all.
 */
internal fun <T> Response<T>.toException(json: Json): SyncException {
    val raw = runCatching { errorBody()?.string() }.getOrNull().orEmpty()
    val envelope = runCatching {
        json.decodeFromString(ErrorEnvelopeDto.serializer(), raw)
    }.getOrNull()
    val code = envelope?.error?.code?.takeIf { it.isNotBlank() }
        ?: when (code()) {
            401, 403 -> SyncException.UNAUTHORIZED
            409 -> SyncException.CURSOR_TOO_OLD
            413 -> SyncException.BATCH_TOO_LARGE
            429 -> SyncException.RATE_LIMITED
            else -> SyncException.SERVER_ERROR
        }
    return SyncException(
        code = code,
        serverMessage = envelope?.error?.message.orEmpty(),
        status = code(),
        details = envelope?.error?.details.orEmpty(),
    )
}
