package com.hasyame.marvelchampions.data.sync

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The eleven endpoints, as calls.
 *
 * Every one takes an absolute [Url] rather than a path against a fixed base,
 * for the same reason the MarvelCDB client does: the host is not known at
 * construction. Here it is because the instance is a setting — thwart.app for
 * most people, something on a shelf at home for anyone who would rather.
 * [SyncEndpoints] builds the URLs so no caller assembles one by hand.
 *
 * Responses come back as [Response] rather than as the decoded body, because a
 * refusal carries a body of its own — the error envelope — and Retrofit throws
 * that away when the call is typed. [SyncClient] unwraps both.
 */
interface SyncApi {

    @GET
    suspend fun version(@Url url: String): Response<VersionDto>

    // --- accounts -----------------------------------------------------------

    /**
     * The account calls carry `Accept-Language`.
     *
     * The server writes its own refusals in English and French and picks by
     * that header. The password rule in particular is the server's to state —
     * two copies of a password policy is two policies, and the one on the phone
     * would be the stale one — so the client has to be able to print what it
     * says, in the reader's language.
     */
    @POST
    suspend fun register(
        @Url url: String,
        @Header("Accept-Language") language: String,
        @Body body: RegisterDto,
    ): Response<AuthResponseDto>

    @POST
    suspend fun login(
        @Url url: String,
        @Header("Accept-Language") language: String,
        @Body body: SignInDto,
    ): Response<AuthResponseDto>

    @POST
    suspend fun recover(
        @Url url: String,
        @Header("Accept-Language") language: String,
        @Body body: RecoverDto,
    ): Response<AuthResponseDto>

    @POST
    suspend fun changePassword(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: ChangePasswordDto,
    ): Response<Unit>

    @GET
    suspend fun devices(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): Response<DevicesResponseDto>

    @DELETE
    suspend fun deleteDevice(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): Response<Unit>

    /**
     * Written as `@HTTP` rather than `@DELETE` because it carries a body, and
     * the password in that body is what the server checks before erasing
     * anything.
     */
    @HTTP(method = "DELETE", hasBody = true)
    suspend fun deleteAccount(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: DeleteAccountDto,
    ): Response<Unit>

    // --- sync ---------------------------------------------------------------

    /**
     * [resync] says this page belongs to a rebuild that started at revision
     * zero.
     *
     * A claim only the client can make: the server is stateless between
     * requests and cannot tell "resuming from an old cursor" from "paging
     * through a resync". Without it, a resync of a large account is refused on
     * its own second page, because that page resumes from a revision that can
     * sit below the tombstone horizon. It is not a privilege — claiming it
     * falsely only serves this device an incomplete feed.
     */
    @GET
    suspend fun pull(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Query("since") since: Long,
        @Query("limit") limit: Int,
        @Query("resync") resync: Boolean,
    ): Response<PullResponseDto>

    @POST
    suspend fun push(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: PushRequestDto,
    ): Response<PushResponseDto>
}

/**
 * Where the endpoints live on one instance.
 *
 * The base is stored with a trailing slash removed once, here, so nothing
 * downstream has to think about whether the user typed one.
 */
class SyncEndpoints(baseUrl: String) {

    private val base = baseUrl.trimEnd('/')

    val version: String get() = "$base/v1/version"
    val register: String get() = "$base/v1/auth/register"
    val login: String get() = "$base/v1/auth/login"
    val recover: String get() = "$base/v1/auth/recover"
    val password: String get() = "$base/v1/auth/password"
    val devices: String get() = "$base/v1/auth/devices"
    val changes: String get() = "$base/v1/sync/changes"
    val account: String get() = "$base/v1/account"

    fun device(id: String): String = "$base/v1/auth/devices/$id"

    companion object {
        /**
         * The instance the app talks to unless told otherwise.
         *
         * A setting rather than a constant because the server is small enough
         * to run at home, and because a household that does will not want its
         * data going anywhere else.
         */
        const val DEFAULT_BASE_URL = "https://thwart.app/api"
    }
}
