package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.platform.DesktopHost
import com.qingyu.hermescompanion.platform.DesktopOS
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Path

class WindowsDesktopTest {
    @Test fun systemDetectionDoesNotMistakeDarwinForWindows() {
        assertEquals(DesktopOS.MAC, DesktopOS.fromName("Darwin"))
        assertEquals(DesktopOS.MAC, DesktopOS.fromName("Mac OS X"))
        assertEquals(DesktopOS.WINDOWS, DesktopOS.fromName("Windows 11"))
        assertEquals(DesktopOS.WINDOWS, DesktopOS.fromName("Windows 10"))
    }
    @Test fun redirectedWindowsAppDataAndUnicodePathsAreRespected() {
        assertEquals(Path.of("redirected 用户 & files!", "HermesDesktop"), DesktopHost.dataDirectory(DesktopOS.WINDOWS, "unused", "redirected 用户 & files!"))
        assertEquals(Path.of("user", "AppData", "Local", "HermesDesktop"), DesktopHost.dataDirectory(DesktopOS.WINDOWS, "user", ""))
        assertEquals(Path.of("user", "Library", "Application Support", "HermesDesktop"), DesktopHost.dataDirectory(DesktopOS.MAC, "user", null))
    }
    @Test fun shortcutsAdaptWithoutChangingUserContent() {
        assertEquals("Ctrl Enter", DesktopHost.shortcutLabel("⌘ Enter", DesktopOS.WINDOWS))
        assertEquals("⌘ Enter", DesktopHost.shortcutLabel("⌘ Enter", DesktopOS.MAC))
        setDesktopLanguage("zh")
        assertEquals("文件-⌘-笔记.md", tr("文件-⌘-笔记.md"))
        if (!DesktopHost.isMac) assertEquals("Ctrl Enter 发送", tr("⌘ Enter 发送"))
    }
    @Test fun speechTextIsDataNotPowershellCode() {
        val ordinary = WindowsSpeech.prepare("speak", java.io.File("recording.txt"), null, "zh-CN")
        val special = java.io.File("访谈 ' & notes ! (final).txt")
        val adversarial = WindowsSpeech.prepare("speak", special, null, "zh-CN; unexpected-command")
        assertEquals(ordinary.command(), adversarial.command())
        assertEquals(special.absolutePath, adversarial.environment()["HERMES_SPEECH_INPUT"])
        assertEquals("zh-CN; unexpected-command", adversarial.environment()["HERMES_SPEECH_LANGUAGE"])
        assertFalse(adversarial.command().joinToString(" ").contains("unexpected-command"))
        assertThrows(IllegalArgumentException::class.java) { WindowsSpeech.prepare("unexpected", special, null, "zh-CN") }
    }
}
