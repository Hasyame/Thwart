package com.hasyame.marvelchampions.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The wire format, field for field as the server declares it.
 *
 * These names are a contract with a program written separately, in another
 * language, that this one cannot see. Two of them are load-bearing in a way
 * that is easy to miss:
 *
 * **The push decoder refuses unknown fields.** `sync.go` calls
 * `DisallowUnknownFields`, so a field added here that the server does not know
 * fails the whole batch with `malformed_record` rather than being ignored. Add
 * nothing to [PushRecordDto] that is not in the server's `IncomingRecord`.
 *
 * **A collection name is never validated against anything the user sees.** The
 * server stores whatever key it is handed. A typo does not error: it quietly
 * builds a second set of records under a name no other client reads, and the
 * data appears to sync while going nowhere. That is why [SyncCollection] is an
 * enum and why nothing here takes a collection as a loose string.
 */

/** One record as the server hands it back. */
@Serializable
data class SyncRecordDto(
    val collection: String,
    val id: String,
    val revision: Long,
    /** RFC 3339. Displayed, and used to break ties on a first sign-in. */
    val updatedAt: String = "",
    val deleted: Boolean = false,
    /** Null exactly when [deleted]; a tombstone carries no body. */
    val body: JsonObject? = null,
)

/**
 * One record on its way up.
 *
 * [baseRevision] is what this device last saw for the record, and is **absent**
 * rather than zero when the record is new to the server. Absent and zero are
 * different claims: zero says "I saw revision zero", which no record has. The
 * app-wide `Json` omits nulls on encoding, which is what makes the null here
 * mean absent on the wire.
 */
@Serializable
data class PushRecordDto(
    val collection: String,
    val id: String,
    val updatedAt: String,
    val deleted: Boolean,
    val baseRevision: Long? = null,
    val body: JsonObject? = null,
)

@Serializable
data class PullResponseDto(
    val changes: List<SyncRecordDto> = emptyList(),
    val cursor: Long = 0,
    val hasMore: Boolean = false,
    /**
     * The tombstone horizon: the oldest revision the server can still account
     * for. A cursor below it means this device has been away longer than the
     * retention window, and the server refuses the pull rather than serving a
     * feed that would look complete and not be.
     */
    val minCursor: Long = 0,
)

@Serializable
data class PushRequestDto(
    /**
     * Identifies this batch for 24 hours, so a retry after a lost response
     * returns the stored result instead of applying every row a second time.
     * Generated once per batch and **kept across retries** — a fresh id on a
     * retry is exactly the duplicate this field exists to prevent.
     */
    val batchId: String,
    val records: List<PushRecordDto>,
)

@Serializable
data class PushResponseDto(
    val cursor: Long = 0,
    val results: List<RecordResultDto> = emptyList(),
)

@Serializable
data class RecordResultDto(
    val id: String,
    val collection: String,
    val revision: Long,
    /** `applied`, `applied_over_conflict`, or `already_present`. */
    val outcome: String = OUTCOME_APPLIED,
    /** Set only on `applied_over_conflict`: the revision this write replaced. */
    val supersededRevision: Long? = null,
) {
    companion object {
        const val OUTCOME_APPLIED = "applied"
        const val OUTCOME_OVER_CONFLICT = "applied_over_conflict"
        const val OUTCOME_ALREADY_PRESENT = "already_present"
    }
}

// --- accounts ---------------------------------------------------------------

/**
 * Creating an account takes both an address and a pseudonym.
 *
 * They answer different questions: the address is how you get back in, the
 * pseudonym is what you are called. Both are required, and a registration
 * without an address is refused with `invalid_email`.
 */
@Serializable
data class RegisterDto(
    val handle: String,
    val email: String,
    val password: String,
    /** Shown in the account's device list, so it names the phone, not the app. */
    val deviceName: String,
)

/**
 * Signing in, with whatever was typed in the one box on the screen.
 *
 * The brief says to send it in **both** `email` and `handle`, and on the server
 * as written that is free: it reads `email` first and falls back to `handle`,
 * and the lookup behind either resolves an address or a pseudonym.
 *
 * It is not free against the server that is actually deployed. Every account
 * endpoint decodes with `DisallowUnknownFields`, and the build on thwart.app
 * today has no `email` field on this request, so a body carrying one is refused
 * outright with `malformed_record` — verified against it rather than assumed.
 * Sending only `handle` reaches the same account on both: the new server falls
 * back to it and resolves an address through it, and the old one is the only
 * thing it understands.
 *
 * So one box, one field, and it works either side of the deployment.
 */
@Serializable
data class SignInDto(
    val handle: String,
    val password: String,
    val deviceName: String,
)

@Serializable
data class RecoverDto(
    val handle: String,
    val recoveryCode: String,
    val newPassword: String,
    val deviceName: String,
)

/**
 * Erasure asks for the password again.
 *
 * The token alone is not enough, and rightly: it is a bearer credential that
 * can be stolen, and this is the one irreversible call in the API. Sending no
 * body at all — which is what a `@DELETE` without one does — is simply refused,
 * so a client that forgets this cannot delete an account at all.
 */
@Serializable
data class DeleteAccountDto(val password: String)

@Serializable
data class ChangePasswordDto(
    val currentPassword: String,
    val newPassword: String,
)

/**
 * What register, login and recover all return.
 *
 * [recoveryCode] comes back from register and recover and **never again**: the
 * server keeps only its hash. There is no email address on these accounts, so
 * it is the only way back in, and the screen that receives one must refuse to
 * move on until it has been saved.
 */
@Serializable
data class AuthResponseDto(
    val accountId: String,
    val handle: String,
    /**
     * Empty from a server older than addresses, and from an account made before
     * they existed. Shown when there is one and simply absent when there is not,
     * rather than being treated as a account that is somehow incomplete.
     */
    val email: String = "",
    val token: String,
    val recoveryCode: String? = null,
    val recoveryCodeIssuedAt: String = "",
)

@Serializable
data class DevicesResponseDto(val devices: List<DeviceDto> = emptyList())

@Serializable
data class DeviceDto(
    val id: String,
    val name: String = "",
    /** True for the device asking, so the list can say "this one". */
    val current: Boolean = false,
    val createdAt: String = "",
    val lastSeen: String = "",
)

// --- instance ---------------------------------------------------------------

@Serializable
data class VersionDto(
    val protocol: Int = 1,
    val build: String = "",
    /**
     * False on an instance meant for one household. The form is not offered
     * when it is false, but the refusal is the server's: a client that stopped
     * drawing a form has changed nothing about who can register.
     */
    val registrationOpen: Boolean = false,
    val limits: LimitsDto = LimitsDto(),
)

/**
 * Batch limits, taken from the server rather than hardcoded.
 *
 * The defaults here are what thwart.app publishes today, used only when
 * `/version` could not be read. They are a floor to fall back to, not a
 * belief about the instance.
 */
@Serializable
data class LimitsDto(
    val batchRecords: Int = 500,
    val batchBytes: Long = 2L * 1024 * 1024,
    val recordBytes: Long = 256L * 1024,
    val pageSize: Int = 500,
)

// --- errors -----------------------------------------------------------------

@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto = ErrorBodyDto())

@Serializable
data class ErrorBodyDto(
    val code: String = "",
    /**
     * The server's own words, in the request's language. Shown only when the
     * client has nothing better: the machine-readable [code] is the contract,
     * and a message the app can translate itself reads better than one
     * translated by a program that does not know what the data is.
     */
    val message: String = "",
    val details: Map<String, JsonElement> = emptyMap(),
)

/**
 * A refusal from the server, carrying the code the client is meant to act on.
 *
 * Thrown rather than returned, because every caller in the sync engine wants to
 * abandon what it was doing; the one code that is really a control-flow signal,
 * [CURSOR_TOO_OLD], is caught deliberately in one place.
 */
class SyncException(
    val code: String,
    val serverMessage: String = "",
    val status: Int = 0,
    val details: Map<String, JsonElement> = emptyMap(),
    cause: Throwable? = null,
) : Exception("$code${if (serverMessage.isBlank()) "" else ": $serverMessage"}", cause) {

    companion object {
        const val UNAUTHORIZED = "unauthorized"
        const val INVALID_CREDENTIALS = "invalid_credentials"
        const val HANDLE_TAKEN = "handle_taken"
        const val INVALID_HANDLE = "invalid_handle"
        const val INVALID_EMAIL = "invalid_email"
        const val EMAIL_TAKEN = "email_taken"
        const val WEAK_PASSWORD = "weak_password"
        const val INVALID_RECOVERY_CODE = "invalid_recovery_code"
        const val REGISTRATION_CLOSED = "registration_closed"

        /** This device has been away longer than the tombstone retention. */
        const val CURSOR_TOO_OLD = "cursor_too_old"

        const val BATCH_TOO_LARGE = "batch_too_large"
        const val RECORD_TOO_LARGE = "record_too_large"
        const val MALFORMED_RECORD = "malformed_record"
        const val RATE_LIMITED = "rate_limited"
        const val SERVER_ERROR = "server_error"

        /** Not a server code: the request never got an answer. */
        const val OFFLINE = "offline"
    }
}
