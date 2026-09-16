package com.qingyu.hermescompanion.storage

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.util.Comparator
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

class DesktopStoreTest {
    @Test fun restartPreservesCookieAndDraftWithoutPlaintext() {
        val root=Files.createTempDirectory("hermes-store-test")
        try {
            val key=ByteArray(32){it.toByte()}
            val first=SecureConfigStore(root){key}
            first.put("draft:profile-a:one","Private unfinished draft")
            first.saveCookies("secret-session-token")
            val bytes=Files.readAllBytes(root.resolve("state.enc"))
            assertFalse(String(bytes).contains("secret-session-token"))
            assertFalse(String(bytes).contains("Private unfinished draft"))
            val second=SecureConfigStore(root){key}
            assertEquals("secret-session-token",second.readCookies())
            assertEquals("Private unfinished draft",second.get("draft:profile-a:one"))
        } finally { Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
    @Test fun corruptCiphertextDoesNotSilentlyEraseConnection() {
        val root=Files.createTempDirectory("hermes-store-corrupt")
        try {
            val key=ByteArray(32){7};SecureConfigStore(root){key}.put("account","test")
            val path=root.resolve("state.enc");val bytes=Files.readAllBytes(path);bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();Files.write(path,bytes)
            assertThrows(Exception::class.java){SecureConfigStore(root){key}.get("account")}
            assertArrayEquals(bytes,Files.readAllBytes(path))
        } finally { Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
    @Test fun hostOnlyCookieCannotLeakToAnotherGateway() {
        val root=Files.createTempDirectory("hermes-cookie-host")
        try {
            val store=SecureConfigStore(root){ByteArray(32){9}};val jar=SecureCookieJar(store)
            val origin="https://gateway.example.com/".toHttpUrl()
            jar.saveFromResponse(origin,listOf(Cookie.Builder().name("session").value("private").hostOnlyDomain(origin.host).path("/").secure().build()))
            assertEquals(1,jar.loadForRequest(origin).size)
            assertTrue(jar.loadForRequest("https://other.example.com/".toHttpUrl()).isEmpty())
            assertTrue(jar.loadForRequest("http://gateway.example.com/".toHttpUrl()).isEmpty())
        } finally { Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}
