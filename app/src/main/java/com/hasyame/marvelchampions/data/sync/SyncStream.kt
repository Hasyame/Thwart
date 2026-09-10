package com.hasyame.marvelchampions.data.sync

import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/** What the stream carries: a revision number and nothing else. */
@Serializable
internal data class RevisionEventDto(val revision: Long = 0)

/** Whether this device currently has a live channel open. */
enum class StreamState { CLOSED, CONNECTING, LISTENING }

/** What arrives on the channel, in the order it happened. */
private sealed interface StreamMessage {
    data class Behind(val revision: Long) : StreamMessage
    data class Ended(val unauthorized: Boolean) : StreamMessage
}

/**
 * The live channel: the account telling this device there is something to fetch.
 *
 * **The stream is an optimisation on top of the catch-up endpoint and never the
 * only way to learn about a change.** That is the load-bearing rule of the
 * protocol and everything here follows from it. Every connection begins with an
 * ordinary sync, the cursor from that sync is where the stream starts, and a
 * stream that never connects at all costs freshness rather than correctness:
 * the six write triggers and the foreground catch-up still work, which is the
 * behaviour that shipped before this file existed.
 *
 * **The event is an invalidation, not the record.** The server sends
 * `{"revision":N}` and nothing else. When N is ahead of this device's cursor
 * the existing [SyncEngine.sync] fetches and applies it, so there is one apply
 * path rather than two that can drift apart, and the channel carries nothing
 * worth leaking if it were ever pointed at the wrong account.
 *
 * **Foreground only, and no service.** [connect] on becoming visible,
 * [disconnect] on leaving. A socket held open behind a dozing app would buy a
 * few seconds of latency at a background cost this app has no business asking
 * for, and a foreground service for it would be worse: a permanent notification
 * and a Play Store justification, for a card game companion.
 *
 * Unlike a browser's `EventSource` nothing here reconnects by itself, so the
 * backoff is ours to write. That is also why this client sends a proper
 * `Authorization` header and never puts a token in a query string, which is the
 * concession the protocol makes for browsers alone.
 */
@Singleton
class SyncStream @Inject constructor(
    private val client: OkHttpClient,
    private val engine: SyncEngine,
    private val sessions: SyncSessionStore,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow(StreamState.CLOSED)

    /** For the account screen, and for tests to wait on. */
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var connection: Job? = null

    /**
     * Opens the channel, if this device is one that syncs by itself.
     *
     * Safe to call when already connected, when signed out, and when the player
     * has never made an account: the gate is the same single preference read
     * the write triggers use, so the anonymous path is one file read and a
     * return, with no network and nothing on the main thread.
     */
    fun connect() {
        if (connection?.isActive == true) {
            return
        }
        connection = scope.launch(ioDispatcher) {
            if (armed()) {
                listen()
            }
        }
    }

    /** Closes it: the app went to the background, or signed out. */
    fun disconnect() {
        connection?.cancel()
        connection = null
        _state.value = StreamState.CLOSED
    }

    private suspend fun armed(): Boolean = try {
        sessions.autoSyncArmed.first()
    } catch (_: IOException) {
        false
    }

    /**
     * Catch up, listen, and when the connection ends wait and go round again.
     *
     * The catch-up runs before every connection, not only the first. A
     * reconnection means the link was down for an unknown stretch and the
     * events from it are gone; asking the endpoint is the only way to learn
     * what was missed, and it is the same call the app would have made anyway.
     */
    private suspend fun listen() {
        var attempt = 0
        while (coroutineContext.isActive) {
            _state.value = StreamState.CONNECTING

            val cursor = runCatching {
                engine.sync()
                sessions.current().cursor
            }.getOrNull()

            if (cursor == null) {
                // Offline, or the server refused. Nothing is broken: the write
                // triggers still run and the next attempt catches up.
                attempt = waitBeforeRetrying(attempt)
                continue
            }

            val unauthorized = runCatching { follow(cursor) }.getOrDefault(false)
            _state.value = StreamState.CLOSED
            if (unauthorized) {
                // Signed out, or this device was revoked from another one.
                // Retrying spends battery on a question already answered.
                return
            }
            attempt = waitBeforeRetrying(attempt)
        }
    }

    /**
     * Holds the connection open until it ends. True when it ended unauthorized.
     *
     * `Last-Event-ID` carries where this device has got to. The server reads it
     * on connect and, if the account has moved on since, says so at once rather
     * than leaving the device idle until somebody else happens to write.
     */
    private suspend fun follow(cursor: Long): Boolean {
        val session = sessions.current()
        val authorization = session.authorization ?: return true
        val request = Request.Builder()
            .url(SyncEndpoints(session.instanceUrl).stream(cursor))
            .header("Authorization", authorization)
            .header("Accept", "text/event-stream")
            .header("Last-Event-ID", cursor.toString())
            .build()

        val messages = Channel<StreamMessage>(capacity = Channel.BUFFERED)
        val source = EventSources.createFactory(streamingClient)
            .newEventSource(request, listener(messages))

        try {
            _state.value = StreamState.LISTENING
            while (true) {
                when (val message = messages.receive()) {
                    is StreamMessage.Ended -> return message.unauthorized
                    is StreamMessage.Behind -> {
                        // Coalesce whatever else arrived while the last sync
                        // ran. An invalidation says only "you are behind", so
                        // five of them are five copies of one fact and acting
                        // on the newest catches up everything the others meant.
                        var revision = message.revision
                        while (true) {
                            val next = messages.tryReceive().getOrNull() ?: break
                            when (next) {
                                is StreamMessage.Ended -> return next.unauthorized
                                is StreamMessage.Behind ->
                                    revision = maxOf(revision, next.revision)
                            }
                        }
                        applyIfBehind(revision)
                    }
                }
            }
        } finally {
            source.cancel()
        }
    }

    /**
     * One sync, and only when the number is news.
     *
     * A device that has just pushed is told about its own revision, so without
     * this comparison every write would cost a pointless round trip straight
     * back to fetch what it had just sent.
     */
    private suspend fun applyIfBehind(revision: Long) {
        if (revision <= sessions.current().cursor) {
            return
        }
        runCatching { engine.sync() }
    }

    private fun listener(messages: Channel<StreamMessage>) = object : EventSourceListener() {

        override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
            // Heartbeats arrive as comments and never reach here. Anything with
            // an unexpected name is a protocol addition this version predates,
            // and ignoring it is how the client stays compatible.
            if (type != null && type != CHANGED_EVENT) {
                return
            }
            val revision = runCatching {
                json.decodeFromString(RevisionEventDto.serializer(), data).revision
            }.getOrNull() ?: return
            messages.trySend(StreamMessage.Behind(revision))
        }

        override fun onClosed(source: EventSource) {
            messages.trySend(StreamMessage.Ended(unauthorized = false))
        }

        override fun onFailure(source: EventSource, t: Throwable?, response: Response?) {
            messages.trySend(
                StreamMessage.Ended(unauthorized = response?.code == UNAUTHORIZED_CODE),
            )
        }
    }

    /**
     * How long to wait before trying again, and why it is not a plain doubling.
     *
     * The jitter is the point. Without it every device that lost the connection
     * to the same restarting server comes back at the same instant and knocks
     * it over a second time. The floor matches the server's own `retry:` hint
     * and the ceiling stops a phone that is simply out of signal from retrying
     * all evening.
     */
    private suspend fun waitBeforeRetrying(attempt: Int): Int {
        val step = MIN_BACKOFF_MILLIS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT)
        val capped = step.coerceAtMost(MAX_BACKOFF_MILLIS)
        delay(capped / 2 + Random.nextLong(capped / 2 + 1))
        return attempt + 1
    }

    /**
     * The shared client, with the one setting a stream needs changed.
     *
     * A read timeout that suits a three megabyte card download would cut an
     * idle stream off mid-life. The server's twenty-second heartbeat is what
     * proves this connection is alive instead.
     */
    private val streamingClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(Duration.ZERO).build()
    }

    private companion object {
        const val CHANGED_EVENT = "changed"
        const val UNAUTHORIZED_CODE = 401
        const val MIN_BACKOFF_MILLIS = 3_000L
        const val MAX_BACKOFF_MILLIS = 300_000L
        const val MAX_BACKOFF_SHIFT = 7
    }
}
