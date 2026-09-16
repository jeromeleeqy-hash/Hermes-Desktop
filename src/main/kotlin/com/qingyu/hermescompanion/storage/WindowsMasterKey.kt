package com.qingyu.hermescompanion.storage

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom

internal interface KeyProtection {
    fun protect(cleartext: ByteArray): ByteArray
    fun unprotect(protected: ByteArray): ByteArray
}

/** Current-user DPAPI: never use CRYPTPROTECT_LOCAL_MACHINE or fall back to a plaintext key. */
internal object WindowsDataProtection : KeyProtection {
    override fun protect(cleartext: ByteArray): ByteArray = Crypt32Util.cryptProtectData(cleartext, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
    override fun unprotect(protected: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(protected, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
}

internal object WindowsMasterKey {
    // JVM monitor + file lock also serialize first launch in two application processes.
    @Synchronized fun load(directory: Path, protection: KeyProtection = WindowsDataProtection): ByteArray {
        Files.createDirectories(directory)
        FileChannel.open(directory.resolve("master-key.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                val path = directory.resolve("master-key.dpapi")
                if (Files.exists(path)) {
                    return try {
                        protection.unprotect(Files.readAllBytes(path)).also { require(it.size == 32) }
                    } catch (e: Exception) {
                        throw IllegalStateException("无法解密 Windows 登录配置。请使用创建它的 Windows 用户；已有数据已保留。", e)
                    }
                }
                check(!Files.exists(directory.resolve("state.enc"))) { "本机加密密钥缺失。请先保留 HermesDesktop 数据目录，不要覆盖原有配置。" }
                val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
                try {
                    val wrapped = protection.protect(key)
                    check(wrapped.isNotEmpty()) { "Windows 未能保护本机密钥。" }
                    val temp = Files.createTempFile(directory, "master-key-", ".tmp")
                    try {
                        FileChannel.open(temp, StandardOpenOption.WRITE).use { output ->
                            val buffer = java.nio.ByteBuffer.wrap(wrapped)
                            while (buffer.hasRemaining()) output.write(buffer)
                            output.force(true)
                        }
                        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE)
                    } finally { Files.deleteIfExists(temp) }
                    return key
                } catch (e: Exception) {
                    key.fill(0)
                    throw IllegalStateException("无法保存 Windows 加密密钥；未使用明文保存登录信息。", e)
                }
            }
        }
    }
}
