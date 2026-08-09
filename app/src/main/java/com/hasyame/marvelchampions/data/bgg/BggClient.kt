package com.hasyame.marvelchampions.data.bgg

import com.hasyame.marvelchampions.domain.model.BggPlay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** What came back from BoardGameGeek, in terms the UI can show a player. */
sealed interface BggResult {
    data object Success : BggResult

    /** Username or password rejected. */
    data object BadCredentials : BggResult

    /** Reached BGG, but it refused or returned something unexpected. */
    data class Rejected(val detail: String) : BggResult

    /** Never reached BGG at all. */
    data class Offline(val detail: String) : BggResult
}

/**
 * Logs plays to BoardGameGeek.
 *
 * **BGG publishes no write API.** Its XML API is read-only, so logging a play
 * goes through `geekplay.php`, the same endpoint the website's own form posts
 * to. That has consequences worth stating plainly rather than burying:
 *
 *  - It needs the account **password**, not a token. There is no OAuth and no
 *    app-specific credential to issue, so there is no version of this feature
 *    that avoids holding one.
 *  - It is undocumented and carries no compatibility promise. It can change
 *    without notice, and when it does this stops working rather than degrading.
 *  - Every failure is therefore reported to the player rather than swallowed. A
 *    play silently not appearing is worse than one that says why.
 *
 * The session is not persisted: each report logs in, posts, and forgets the
 * cookies. It is a handful of requests a week at most, and it means no session
 * token sits on disk alongside the password.
 */
@Singleton
class BggClient @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** Checks a username and password without recording anything. */
    suspend fun verify(username: String, password: String): BggResult =
        withContext(ioDispatcher) {
            val client = newClient()
            when (val login = logIn(client, username, password)) {
                is BggResult.Success -> BggResult.Success
                else -> login
            }
        }

    /** Logs one play of Marvel Champions. */
    suspend fun reportPlay(
        username: String,
        password: String,
        play: BggPlay,
    ): BggResult = withContext(ioDispatcher) {
        val client = newClient()

        when (val login = logIn(client, username, password)) {
            is BggResult.Success -> Unit
            else -> return@withContext login
        }

        val form = FormBody.Builder()
            .add("ajax", "1")
            .add("action", "save")
            .add("version", "2")
            .add("objecttype", "thing")
            .add("objectid", MARVEL_CHAMPIONS_ID)
            .add("playdate", play.playedOn)
            .add("length", play.lengthMinutes.toString())
            .add("location", play.location)
            .add("quantity", "1")

            .add("incomplete", "0")
            .add("nowinstats", "0")
            .add("comments", play.comment)
            .apply {
                // Player rows, not a count. A play posted with only a number
                // lands in BGG with nobody in it — which is exactly what
                // happened — and never shows as the account holder's play.
                play.players.forEachIndexed { index, player ->
                    add("players[$index][username]", player.username)
                    add("players[$index][userid]", "0")
                    add("players[$index][name]", player.name)
                    add("players[$index][score]", player.score.toString())
                    add("players[$index][win]", if (player.won) "1" else "0")
                    add("players[$index][new]", "0")
                    add("players[$index][rating]", "0")
                    // BGG shows colour beside the name, which is where the hero
                    // belongs: it is what distinguishes one seat from another.
                    add("players[$index][color]", player.color)
                }
            }
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/geekplay.php")
            .post(form)
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    val body = it.body.string()
                    when {
                        !it.isSuccessful -> BggResult.Rejected("HTTP ${it.code}")
                        // A rejected save comes back 200 with an error in the
                        // body, so the status code alone cannot be trusted.
                        body.contains("\"error\"", ignoreCase = true) ->
                            BggResult.Rejected(body.take(ERROR_DETAIL_LIMIT))

                        else -> BggResult.Success
                    }
                }
            },
            onFailure = { BggResult.Offline(it.message ?: "no connection") },
        )
    }

    private fun logIn(
        client: OkHttpClient,
        username: String,
        password: String,
    ): BggResult {
        // Sent as JSON because that is what BGG's own login form posts; a form
        // body is rejected.
        val payload = buildJsonLogin(username, password)
        val request = Request.Builder()
            .url("$BASE_URL/login/api/v1")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    when {
                        it.isSuccessful -> BggResult.Success
                        it.code == HTTP_UNAUTHORIZED -> BggResult.BadCredentials
                        else -> BggResult.Rejected("HTTP ${it.code}")
                    }
                }
            },
            onFailure = { BggResult.Offline(it.message ?: "no connection") },
        )
    }

    /**
     * Built by hand rather than through a serializer, so the password is never
     * copied into an intermediate object that might end up in a log or a crash
     * report.
     */
    private fun buildJsonLogin(username: String, password: String): String =
        """{"credentials":{"username":"${username.escapeJson()}",""" +
            """"password":"${password.escapeJson()}"}}"""

    /**
     * A client per report, with cookies held only in memory.
     *
     * Deliberately not the app's shared client: this one carries a session for
     * somebody's BGG account, and it should not outlive the request that needs
     * it.
     */
    private fun newClient(): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(InMemoryCookieJar())
        .build()

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.removeAll { existing -> cookies.any { it.name == existing.name } }
            this.cookies += cookies
        }

        /**
         * Only the cookies that belong to [url]. Returning the whole jar was
         * the same thing as long as BGG answered every request itself, but
         * OkHttp follows a redirect wherever it points, including off the
         * host — and the cookie held here is a live session for somebody's
         * account. [Cookie.matches] applies the domain, path and secure rules
         * the cookie was set with, so a redirect elsewhere now leaves with
         * nothing attached.
         */
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.filter { it.matches(url) }
    }

    private companion object {
        const val BASE_URL = "https://boardgamegeek.com"

        /** Marvel Champions: The Card Game. */
        const val MARVEL_CHAMPIONS_ID = "285774"

        const val USER_AGENT = "MarvelChampionsCompanion/1.0 (github.com/Hasyame)"
        const val HTTP_UNAUTHORIZED = 401
        const val ERROR_DETAIL_LIMIT = 200
    }
}

/**
 * The backslash and the quote, and the control characters a JSON string is
 * not allowed to carry raw.
 *
 * The first two were always escaped; the rest were not, and a password
 * holding a tab or a line break — which a password manager will happily
 * produce — built a body BGG could only reject. The player was then told
 * their credentials were wrong, which was both untrue and unfixable from
 * their side.
 *
 * Top level and internal so it can be tested without a client: the whole
 * point is the characters nobody types on purpose.
 */
internal fun String.escapeJson(): String = buildString(length) {
    for (character in this@escapeJson) {
        when {
            character == '\\' -> append("\\\\")
            character == '\"' -> append("\\\"")
            character == '\n' -> append("\\n")
            character == '\r' -> append("\\r")
            character == '\t' -> append("\\t")
            character == '\b' -> append("\\b")
            character == '\u000C' -> append("\\f")
            character < ' ' ->
                append("\\u").append(character.code.toString(16).padStart(4, '0'))

            else -> append(character)
        }
    }
}
