package com.hasyame.marvelchampions.core.util

import kotlin.coroutines.cancellation.CancellationException

/** Cancellation and VM errors must never be converted into a successful fallback. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
