package com.hasyame.marvelchampions.core.util

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchingTest {
    @Test(expected = CancellationException::class)
    fun `cancellation escapes instead of becoming an ordinary failure`() {
        runCatchingCancellable { throw CancellationException("cancelled") }
    }

    @Test(expected = OutOfMemoryError::class)
    fun `fatal errors escape instead of becoming an ordinary failure`() {
        runCatchingCancellable { throw OutOfMemoryError("synthetic") }
    }

    @Test
    fun `recoverable exceptions remain available to the caller`() {
        val failure = java.io.IOException("synthetic")
        val result = runCatchingCancellable { throw failure }
        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }
}
