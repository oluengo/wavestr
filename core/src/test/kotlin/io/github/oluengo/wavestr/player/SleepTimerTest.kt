package io.github.oluengo.wavestr.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private fun setup(): Triple<TestScope, SleepTimer, MutableList<String>> {
        val scope = TestScope()
        val log = mutableListOf<String>()
        val timer = SleepTimer(scope, setVolume = { log += "v%.2f".format(java.util.Locale.ROOT, it) }, stop = { log += "stop" }, now = { scope.testScheduler.currentTime })
        return Triple(scope, timer, log)
    }

    @Test
    fun stopsAfterTheTimeWithFadeOut() {
        val (scope, timer, log) = setup()
        timer.start(1)
        scope.advanceTimeBy(29_000); scope.runCurrent()
        assertTrue("sin bajar antes de los últimos 30 s", log.none { it.startsWith("v0.") })
        scope.advanceTimeBy(16_000); scope.runCurrent()
        assertTrue("baja el volumen", log.any { it.startsWith("v0.") })
        scope.advanceTimeBy(16_000); scope.runCurrent()
        assertTrue(log.contains("stop"))
        assertEquals("v1.00", log.last())
        assertNull(timer.mode.value)
    }

    @Test
    fun extendAndCancel() {
        val (scope, timer, log) = setup()
        timer.start(1)
        timer.extend(10)
        assertEquals(11 * 60_000L, timer.remainingMs())
        timer.cancel()
        scope.advanceTimeBy(20 * 60_000L); scope.runCurrent()
        assertTrue(log.none { it == "stop" })
    }

    @Test
    fun streamEndOnlyStopsInThatMode() {
        val (_, timer, log) = setup()
        timer.onStreamEnded()
        assertTrue(log.none { it == "stop" })
        timer.untilStreamEnd()
        timer.onStreamEnded()
        assertTrue(log.contains("stop"))
        assertNull(timer.mode.value)
    }
}

class BufferPolicyTest {
    @Test
    fun marginByMode() {
        assertEquals(0L, BufferPolicy.marginMs(BufferMode.LIVE, true))
        assertEquals(0L, BufferPolicy.marginMs(BufferMode.AUTO, false))
        assertEquals(20_000L, BufferPolicy.marginMs(BufferMode.AUTO, true))
        assertEquals(30_000L, BufferPolicy.marginMs(BufferMode.S30, false))
    }
}
