package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.VoicePreferences
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest

class LocalVoice394Test {
    @Test fun legacySettingsAndMixedRoutesStayIndependent() {
        val legacy = VoicePreferences(engine = "system")
        assertEquals("system", legacy.sttEngine)
        assertEquals("system", legacy.ttsEngine)
        val mixed = legacy.copy(sttEngine = "local", ttsEngine = "server")
        assertEquals("local", mixed.sttEngine)
        assertEquals("server", mixed.ttsEngine)
        assertEquals("system", mixed.engine)
        assertEquals("local", mixed.copy(ttsEngine = "system").sttEngine)
    }

    @Test fun streamingSpeaksCompleteSentencesBeforeFinalAndNeverReplaysThem() {
        val text = StreamingVoiceText()
        assertTrue(text.update("我来查").isEmpty())
        assertEquals(listOf("我来查一下。"), text.update("我来查一下。"))
        assertTrue(text.update("我来查一下。结果").isEmpty())
        assertEquals(listOf("结果是 12.5%。"), text.update("我来查一下。结果是 12.5%。"))
        assertEquals(listOf("还有三项待确认"), text.update("我来查一下。结果是 12.5%。还有三项待确认", true))
        assertTrue(text.update("我来查一下。结果是 12.5%。还有三项待确认", true).isEmpty())
    }

    @Test fun codeLinksAndIncompleteFormattingAreNotReadAsInstructions() {
        val t = StreamingVoiceText()
        assertEquals(listOf("已完成。"), t.update("**已完成。**\n```sh\nrm"))
        val next = t.update("**已完成。**\n```sh\nrm -rf example\n```\n[查看文件](https://example.com/a)。", true).joinToString("")
        assertFalse(next.contains("rm")); assertFalse(next.contains("https")); assertTrue(next.contains("查看文件"))
        val link = StreamingVoiceText()
        assertTrue(link.update("[查看文件](https://examp").isEmpty())
        assertEquals(listOf("查看文件。"), link.update("[查看文件](https://example.com)。"))
    }

    @Test fun rewrittenRecoveryDoesNotSpeakOldAnswerAgain() {
        val t = StreamingVoiceText()
        assertEquals(listOf("原回复。"), t.update("原回复。"))
        assertTrue(t.update("修订后的回复。", true).isEmpty())
    }

    @Test fun silenceAndLongRecordingSlicesAreBoundedWithoutLosingSamples() {
        assertEquals(700L, voicePauseMillis("quick", "balanced"))
        assertEquals(1250L, voicePauseMillis("balanced", "noisy"))
        assertEquals(1600L, voicePauseMillis("patient", "balanced"))
        val rate = 16000
        val samples = FloatArray(rate * 43) { .5f }
        samples.fill(0f, rate * 17, rate * 17 + rate / 2)
        val first = speechSliceEnd(samples, 0, rate)
        assertTrue(first in rate * 17..rate * 18)
        var index = 0; var total = 0
        while (index < samples.size) { val end = speechSliceEnd(samples, index, rate); assertTrue(end > index); total += end - index; index = end }
        assertEquals(samples.size, total)
    }

    @Test fun ownedWaveRoundTripsAndUploadsWithWaveMimeType() {
        val dir=Files.createTempDirectory("local-wave").toFile()
        try {
            val file=File(dir,"capture.wav");file.writeBytes(pcmWave(floatArrayOf(0f,.5f,-.5f,1f),16000))
            val decoded=decodeVoiceFile(file)
            assertEquals(16000,decoded.sampleRate);assertArrayEquals(floatArrayOf(0f,.5f,-.5f,1f),decoded.samples,.001f)
            val body=AudioFileRequestBody(file.apply {appendBytes(ByteArray(128))});val sink=Buffer();body.writeTo(sink)
            assertTrue(sink.readUtf8().contains("data:audio/wav;base64,"))
        }finally{dir.deleteRecursively()}
    }

    private fun archive(vararg names: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(bytes)).use { out ->
            names.forEach { name ->
                val data = "A small model test file".toByteArray()
                out.putArchiveEntry(TarArchiveEntry(name).apply { size = data.size.toLong() }); out.write(data); out.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }
    private fun spec(bytes: ByteArray, url: String) = VoiceModelSpec("test", "Test", "test", "pack", url, bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, listOf("model.onnx"), 1_000_000)

    @Test fun resumedPackIsVerifiedInstalledAndInterruptedDownloadDoesNotEnableIt() = runBlocking {
        val dir = Files.createTempDirectory("voice-download").toFile()
        val server = MockWebServer(); server.start()
        try {
            val archive = archive("pack/model.onnx")
            val spec = spec(archive, server.url("/model").toString())
            val half = archive.size / 2
            File(dir, "test.part").writeBytes(archive.copyOf(half))
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $half-${archive.size - 1}/${archive.size}")
                .setBody(Buffer().write(archive, half, archive.size - half)))
            val states = mutableListOf<VoiceModelState>(); val store = VoiceModelStore(dir)
            store.download(spec) { states += it }
            assertEquals("bytes=$half-", server.takeRequest().getHeader("Range"))
            assertTrue(store.installed(spec)); assertTrue(states.last().installed)
            assertFalse(File(dir, "test.part").exists())
            assertTrue(File(store.directory(spec), "model.onnx").length() > 0)
            store.delete(spec); assertFalse(store.installed(spec))
        } finally { server.shutdown(); dir.deleteRecursively() }
    }

    @Test fun corruptChecksumNeverReplacesAnInstalledPack() = runBlocking {
        val dir = Files.createTempDirectory("voice-download").toFile(); val server = MockWebServer(); server.start()
        try {
            val bytes = archive("pack/model.onnx"); val spec = spec(bytes, server.url("/model").toString())
            val store = VoiceModelStore(dir); server.enqueue(MockResponse().setBody(Buffer().write(bytes))); store.download(spec) {}
            val original = File(store.directory(spec), "model.onnx").readText()
            val corrupt = bytes.copyOf().apply { this[10] = (this[10].toInt() xor 0xFF).toByte() }
            server.enqueue(MockResponse().setBody(Buffer().write(corrupt)))
            try { store.download(spec) {}; fail("checksum must fail") } catch (_: IOException) { }
            assertTrue(store.installed(spec)); assertEquals(original, File(store.directory(spec), "model.onnx").readText())
        } finally { server.shutdown(); dir.deleteRecursively() }
    }

    @Test fun importingVerifiedPackIsOfflineAndKeepsTheOriginalFile() = runBlocking {
        val dir=Files.createTempDirectory("voice-import").toFile()
        try {
            val bytes=archive("pack/model.onnx");val spec=spec(bytes,"https://unreachable.invalid/model")
            val original=File(dir,"original.tar.bz2").apply {writeBytes(bytes)}
            val store=VoiceModelStore(File(dir,"models"));val states=mutableListOf<VoiceModelState>()
            store.importArchive(spec,original){states+=it}
            assertTrue(store.installed(spec));assertArrayEquals(bytes,original.readBytes())
            assertTrue(states.any {it.phase=="verify"});assertTrue(states.last().installed)
            val installed=File(store.directory(spec),"model.onnx").readBytes()
            original.writeBytes(bytes.copyOf().apply {this[10]=(this[10].toInt() xor 255).toByte()})
            try {store.importArchive(spec,original){};fail("Corrupt import must fail")}catch(_:IOException){}
            assertTrue(original.exists());assertTrue(store.installed(spec))
            assertArrayEquals(installed,File(store.directory(spec),"model.onnx").readBytes())
            assertFalse(store.root.listFiles().orEmpty().any {it.name.contains("-import-")})
        }finally{dir.deleteRecursively()}
    }

    @Test fun cancelledImportNeverMarksPartialModelInstalled() = runBlocking {
        val dir=Files.createTempDirectory("voice-cancel-import").toFile()
        try {
            val bytes=archive("pack/model.onnx");val spec=spec(bytes,"https://unreachable.invalid/model")
            val original=File(dir,"original.tar.bz2").apply {writeBytes(bytes)}
            val store=VoiceModelStore(File(dir,"models"))
            try {store.importArchive(spec,original){if(it.phase=="verify")throw CancellationException("test")};fail("Must cancel")}
            catch(_:CancellationException){}
            assertFalse(store.installed(spec));assertArrayEquals(bytes,original.readBytes())
            assertFalse(store.root.listFiles().orEmpty().any {it.name.contains("-import-")})
        }finally{dir.deleteRecursively()}
    }

    @Test fun unsafeArchiveAndWrongResumePositionsAreRejected() {
        assertFalse(validResumeRange("bytes 1-19/20", 10, 20)); assertFalse(validResumeRange("bytes 10-19/30", 10, 20))
        assertTrue(validResumeRange("bytes 10-19/20", 10, 20))
        val dir = Files.createTempDirectory("voice-archive").toFile()
        try {
            for (name in listOf("pack/../../escape", "foreign/model.onnx", "pack/a/../model.onnx")) {
                val bytes = archive(name); val file = File(dir, "archive.bz2").apply { writeBytes(bytes) }
                try { extractVoiceArchive(file, File(dir, "out").apply { mkdirs() }, spec(bytes, "https://example.com")); fail("unsafe path $name") }
                catch (_: IllegalArgumentException) { }
            }
        } finally { dir.deleteRecursively() }
    }
}
