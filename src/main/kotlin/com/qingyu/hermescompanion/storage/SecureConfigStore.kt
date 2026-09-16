package com.qingyu.hermescompanion.storage

import com.sun.jna.NativeLibrary
import com.qingyu.hermescompanion.platform.DesktopHost
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM state; macOS protects the key with Keychain, Windows with current-user DPAPI. */
class SecureConfigStore(
    val directory: Path = defaultDirectory(),
    private val keyProvider: (() -> ByteArray)? = null,
) {
    private val file = directory.resolve("state.enc")
    private val key by lazy { (keyProvider ?: { loadMasterKey() })() }
    private var state: JSONObject? = null
    @Synchronized private fun data(): JSONObject {
        state?.let { return it }
        val loaded = if (Files.exists(file)) {
            val bytes = Files.readAllBytes(file)
            require(bytes.size > 28) { "本机配置文件不完整，请先保留备份。" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        } else JSONObject()
        state = loaded
        return loaded
    }
    @Synchronized fun get(name: String, fallback: String = ""): String = data().optString(name, fallback)
    @Synchronized fun put(name: String, value: String?) {
        val previous = data().opt(name)
        if (value == null) data().remove(name) else data().put(name, value)
        try { persist() } catch (e: Exception) {
            if (previous == null) data().remove(name) else data().put(name, previous)
            throw e
        }
    }
    /** Encrypted content-addressed blobs keep large attachments out of the preferences file. */
    @Synchronized fun saveBlob(bytes: ByteArray): String {
        val id=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val path=blobPath(id)
        if(Files.exists(path)) return id
        Files.createDirectories(path.parent); restrict(path.parent,"rwx------")
        val nonce=ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce))
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        val temp=Files.createTempFile(path.parent,"blob-",".tmp")
        try {
            restrict(temp,"rw-------")
            Files.write(temp,nonce+cipher.doFinal(bytes))
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
        return id
    }
    @Synchronized fun readBlob(id:String):ByteArray {
        val bytes=Files.readAllBytes(blobPath(id)); require(bytes.size>=28) { "本机附件文件不完整。" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
    }
    private fun blobPath(id:String):Path {
        require(id.matches(Regex("[a-f0-9]{64}"))) { "无效的本机附件标识。" }
        return directory.resolve("blobs").resolve("$id.enc")
    }
    @Synchronized private fun persist() {
        Files.createDirectories(directory)
        restrict(directory, "rwx------")
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val temp = Files.createTempFile(directory, "state-", ".tmp")
        try {
            restrict(temp, "rw-------")
            Files.write(temp, nonce + cipher.doFinal(data().toString().toByteArray(Charsets.UTF_8)))
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
    fun readCookies(): String? = get("cookies").ifBlank { null }
    fun saveCookies(raw: String) = put("cookies", raw)
    fun clearCookies() = put("cookies", null)
    fun pendingVoiceReasoning(server: String, profile: String, session: String): String? = get("voice:$server:$profile:$session").ifBlank { null }
    fun savePendingVoiceReasoning(server: String, profile: String, session: String, value: String?) = put("voice:$server:$profile:$session", value)
    private fun loadMasterKey(): ByteArray {
        if (DesktopHost.isMac) return MacKeychain.masterKey()
        if (DesktopHost.isWindows) return WindowsMasterKey.load(directory)
        // Linux developer builds only; Windows and macOS always use their OS key protection.
        Files.createDirectories(directory)
        restrict(directory, "rwx------")
        val path = directory.resolve("development.key")
        if (Files.exists(path)) return Files.readAllBytes(path)
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        Files.write(path, bytes)
        return bytes
    }
    companion object {
        fun defaultDirectory(): Path = DesktopHost.dataDirectory()
        private fun restrict(path: Path, mode: String) { if (path.fileSystem.supportedFileAttributeViews().contains("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode)) }
    }
}

private object MacKeychain {
    private val security by lazy { NativeLibrary.getInstance("/System/Library/Frameworks/Security.framework/Security") }
    fun masterKey(): ByteArray {
        val service = "com.qingyu.hermes.desktop.master-key".toByteArray()
        val account = "local-v1".toByteArray()
        val length = IntByReference()
        val data = PointerByReference()
        val status = security.getFunction("SecKeychainFindGenericPassword").invokeInt(arrayOf(null, service.size, service, account.size, account, length, data, null))
        if (status == 0) {
            try { return data.value.getByteArray(0, length.value).also { require(it.size == 32) } }
            finally { security.getFunction("SecKeychainItemFreeContent").invokeInt(arrayOf(null, data.value)) }
        }
        check(status == -25300) { "无法读取 macOS 钥匙串（$status）。请允许 Hermes 访问自己的登录配置。" }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val added = security.getFunction("SecKeychainAddGenericPassword").invokeInt(arrayOf(null, service.size, service, account.size, account, bytes.size, bytes, null))
        check(added == 0) { "无法保存 macOS 钥匙串（$added）。" }
        return bytes
    }
}
