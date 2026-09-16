package com.qingyu.hermescompanion.platform

import java.nio.file.Path

enum class DesktopOS(val label: String) {
    MAC("macOS"), WINDOWS("Windows"), LINUX("Linux");
    companion object {
        fun fromName(name: String): DesktopOS = when {
            name.startsWith("Windows", ignoreCase = true) -> WINDOWS
            name.startsWith("Mac", ignoreCase = true) || name.equals("Darwin", ignoreCase = true) -> MAC
            else -> LINUX
        }
    }
}

object DesktopHost {
    val os: DesktopOS = DesktopOS.fromName(System.getProperty("os.name"))
    val isWindows get() = os == DesktopOS.WINDOWS
    val isMac get() = os == DesktopOS.MAC
    fun primaryModifier(meta: Boolean, control: Boolean): Boolean = if (isMac) meta else control
    fun shortcutLabel(text: String, platform: DesktopOS = os): String = if (platform == DesktopOS.MAC) text else text.replace("⌘", "Ctrl")
    fun dataDirectory(platform: DesktopOS = os, home: String = System.getProperty("user.home"), localAppData: String? = System.getenv("LOCALAPPDATA")): Path = when (platform) {
        DesktopOS.MAC -> Path.of(home, "Library", "Application Support", "HermesDesktop")
        DesktopOS.WINDOWS -> (localAppData?.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: Path.of(home, "AppData", "Local")).resolve("HermesDesktop")
        DesktopOS.LINUX -> Path.of(home, ".local", "share", "hermes-desktop")
    }
}
