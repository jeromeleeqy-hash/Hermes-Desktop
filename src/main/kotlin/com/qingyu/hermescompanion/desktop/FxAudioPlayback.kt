package com.qingyu.hermescompanion.desktop

import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** JavaFX supplies Windows MP3/AAC/WAV decoding and playback speed, including cancellation. */
internal class FxAudioPlayback {
    private class Playback {
        val done = CompletableFuture<Unit>()
        var player: MediaPlayer? = null // accessed only on the JavaFX thread
    }
    private val active = AtomicReference<Playback?>()

    fun play(file: File, rate: Float) {
        FxRuntime.start()
        val operation = Playback()
        active.getAndSet(operation)?.done?.complete(Unit)
        Platform.runLater {
            if (operation.done.isDone) return@runLater
            try {
                val media = Media(file.toURI().toString())
                val player = MediaPlayer(media)
                operation.player = player
                val fail = { operation.done.completeExceptionally(IllegalStateException("无法播放服务器返回的音频。")) }
                media.setOnError { fail() }
                player.setOnError { fail() }
                player.setOnEndOfMedia { operation.done.complete(Unit) }
                player.setOnReady {
                    if (!operation.done.isDone) { player.rate = rate.toDouble().coerceIn(.5, 2.0); player.play() }
                }
                if (player.error != null || media.error != null) fail()
            } catch (e: Exception) { operation.done.completeExceptionally(e) }
        }
        try { operation.done.get(300, TimeUnit.SECONDS) }
        finally {
            operation.done.complete(Unit)
            active.compareAndSet(operation, null)
            val released = CompletableFuture<Unit>()
            Platform.runLater {
                try { operation.player?.dispose(); operation.player = null }
                finally { released.complete(Unit) }
            }
            // Windows keeps the audio file open until MediaPlayer.dispose has completed.
            released.get(10, TimeUnit.SECONDS)
        }
    }
    fun stop() { active.getAndSet(null)?.done?.complete(Unit) }
}

internal object FxRuntime {
    @Volatile private var started = false
    @Synchronized fun start() {
        if (started) return
        try { Platform.startup { Platform.setImplicitExit(false) } }
        catch (_: IllegalStateException) { Platform.runLater { Platform.setImplicitExit(false) } }
        started = true
    }
    fun shutdown(){if(started)runCatching {Platform.exit()}}
}
