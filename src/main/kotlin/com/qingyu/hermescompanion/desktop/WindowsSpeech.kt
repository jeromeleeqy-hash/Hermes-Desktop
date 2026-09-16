package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.platform.DesktopHost
import java.io.File
import java.util.Base64

/** Fixed script only. Speech text and paths are passed as data, never interpolated into code. */
internal object WindowsSpeech {
    private val script by lazy {
        checkNotNull(javaClass.getResourceAsStream("/platform/windows-speech.ps1")) { "Windows 语音组件缺失。" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    private fun executable(): File = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/WindowsPowerShell/v1.0/powershell.exe")
    fun available(): Boolean = DesktopHost.isWindows && executable().isFile
    fun start(mode: String, input: File, output: File?, language: String, rate: Float = 1f): Process {
        check(available()) { "Windows 系统语音不可用，请在语音设置中选择服务器引擎。" }
        return prepare(mode, input, output, language, rate).start()
    }
    internal fun prepare(mode: String, input: File, output: File?, language: String, rate: Float = 1f): ProcessBuilder {
        require(mode in setOf("speak", "transcribe"))
        val command = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return ProcessBuilder(executable().absolutePath, "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", command)
            .apply {
                environment().putAll(mapOf("HERMES_SPEECH_MODE" to mode, "HERMES_SPEECH_INPUT" to input.absolutePath,
                    "HERMES_SPEECH_OUTPUT" to (output?.absolutePath ?: ""), "HERMES_SPEECH_LANGUAGE" to language,
                    "HERMES_SPEECH_RATE" to rate.coerceIn(.5f, 2f).toString()))
                redirectOutput(ProcessBuilder.Redirect.DISCARD)
                redirectError(ProcessBuilder.Redirect.DISCARD)
            }
    }
}
