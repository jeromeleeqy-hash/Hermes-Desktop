package com.qingyu.hermescompanion.storage

import com.qingyu.hermescompanion.platform.DesktopHost
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WindowsMasterKeyTest {
    private fun <T> temporary(block: (Path) -> T): T {
        val root = Files.createTempDirectory("hermes-windows-key-test")
        try { return block(root) }
        finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    private class Protector : KeyProtection {
        var clear: ByteArray? = null
        var writes = 0
        override fun protect(cleartext: ByteArray): ByteArray { writes++; clear = cleartext.copyOf(); return "protected-key".toByteArray() }
        override fun unprotect(protected: ByteArray): ByteArray {
            check(String(protected) == "protected-key")
            return checkNotNull(clear).copyOf()
        }
    }
    @Test fun simultaneousFirstLaunchUsesOneProtectedKey() = temporary { root ->
        val protector = Protector()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (1..8).map { pool.submit<ByteArray> { WindowsMasterKey.load(root, protector) } }.map { it.get(10, TimeUnit.SECONDS) }
            results.forEach { assertArrayEquals(results.first(), it) }
            assertEquals(1, protector.writes)
            assertFalse(Files.exists(root.resolve("development.key")))
            assertFalse(Files.readAllBytes(root.resolve("master-key.dpapi")).contentEquals(results.first()))
        } finally { pool.shutdownNow() }
    }
    @Test fun protectionFailureNeverFallsBackToPlaintext() = temporary { root ->
        val protection = object : KeyProtection {
            override fun protect(cleartext: ByteArray): ByteArray = error("DPAPI unavailable")
            override fun unprotect(protected: ByteArray): ByteArray = error("not called")
        }
        assertThrows(IllegalStateException::class.java) { WindowsMasterKey.load(root, protection) }
        assertFalse(Files.exists(root.resolve("master-key.dpapi")))
        assertFalse(Files.exists(root.resolve("development.key")))
        assertFalse(Files.exists(root.resolve("state.enc")))
    }
    @Test fun corruptedKeyIsPreservedAndNotRegenerated() = temporary { root ->
        val protector = Protector()
        WindowsMasterKey.load(root, protector)
        val corrupted = "corrupted-protected-key".toByteArray()
        val file = root.resolve("master-key.dpapi")
        Files.write(file, corrupted)
        assertThrows(IllegalStateException::class.java) { WindowsMasterKey.load(root, protector) }
        assertEquals(1, protector.writes)
        assertArrayEquals(corrupted, Files.readAllBytes(file))
    }
    @Test fun missingKeyDoesNotOverwriteExistingEncryptedState() = temporary { root ->
        val state = "existing encrypted state".toByteArray()
        Files.write(root.resolve("state.enc"), state)
        val protector = Protector()
        assertThrows(IllegalStateException::class.java) { WindowsMasterKey.load(root, protector) }
        assertEquals(0, protector.writes)
        assertArrayEquals(state, Files.readAllBytes(root.resolve("state.enc")))
    }
    @Test fun nativeWindowsDpapiReopensCookiesDraftsAndAttachments() {
        assumeTrue("Requires an actual Windows user profile", DesktopHost.isWindows)
        temporary { root ->
            val original = SecureConfigStore(root)
            original.saveCookies("test-private-cookie")
            original.put("draft", "private draft")
            val blob = original.saveBlob("private voice recording".toByteArray())
            val restarted = SecureConfigStore(root)
            assertEquals("test-private-cookie", restarted.readCookies())
            assertEquals("private draft", restarted.get("draft"))
            assertEquals("private voice recording", String(restarted.readBlob(blob)))
            assertFalse(Files.exists(root.resolve("development.key")))
            assertFalse(String(Files.readAllBytes(root.resolve("state.enc"))).contains("private-cookie"))
        }
    }
    @Test fun nativeWindowsDpapiRejectsModifiedProtectedData() {
        assumeTrue("Requires actual Windows DPAPI", DesktopHost.isWindows)
        val protected = WindowsDataProtection.protect(ByteArray(32) { it.toByte() })
        protected[protected.lastIndex] = (protected.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { WindowsDataProtection.unprotect(protected) }
    }
}
