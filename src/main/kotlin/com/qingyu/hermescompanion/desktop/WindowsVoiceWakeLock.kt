package com.qingyu.hermescompanion.desktop

import com.sun.jna.platform.win32.Kernel32

/** The execution state belongs to one thread, and must be released on that same thread. */
internal class WindowsVoiceWakeLock : AutoCloseable {
    private val release = java.util.concurrent.CountDownLatch(1)
    private val worker = Thread({
        try {
            // ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED
            Kernel32.INSTANCE.SetThreadExecutionState(0x80000003.toInt())
            release.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally { runCatching { Kernel32.INSTANCE.SetThreadExecutionState(0x80000000.toInt()) } }
    }, "hermes-voice-awake").apply { isDaemon = true; start() }
    override fun close() { release.countDown() }
}
