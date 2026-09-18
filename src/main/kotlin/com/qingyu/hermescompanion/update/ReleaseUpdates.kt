package com.qingyu.hermescompanion.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Only production tags with numeric components are eligible. No prerelease downgrade. */
data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int): Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion) = compareValuesBy(this, other, {it.major}, {it.minor}, {it.patch})
    override fun toString() = "$major.$minor.$patch"
    companion object {
        fun parse(raw: String): ReleaseVersion? {
            val match = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(raw) ?: return null
            val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return ReleaseVersion(parts[0], parts[1], parts[2])
        }
    }
}
data class UpdateRelease(val version: ReleaseVersion, val notes: String, val name: String, val url: String, val size: Long, val sha256: String)
object ReleaseUpdates {
    const val REPO = "https://github.com/jeromeleeqy-hash/Hermes-Desktop"
    const val LATEST = "https://api.github.com/repos/jeromeleeqy-hash/Hermes-Desktop/releases/latest"
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    fun parse(json: JSONObject, current: String, platform: String): UpdateRelease? {
        if(json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val version = ReleaseVersion.parse(json.optString("tag_name")) ?: return null
        requireNotNull(ReleaseVersion.parse(current)) { "当前版本号无效" }
        if(version <= ReleaseVersion.parse(current)!!) return null
        val name = when(platform) {
            "macos-arm64" -> "Hermes-macOS-arm64-$version.dmg"
            "windows-x64" -> "Hermes-Windows-x64-$version.msi"
            else -> error("当前系统架构暂不支持自动安装")
        }
        val assets = json.optJSONArray("assets") ?: error("此版本尚未上传安装包")
        val matches = (0 until assets.length()).mapNotNull {assets.optJSONObject(it)}.filter {it.optString("name") == name}
        require(matches.size == 1) { "此版本尚未准备好当前系统的安装包" }
        val asset = matches.single()
        val url = asset.getString("browser_download_url")
        require(url == "$REPO/releases/download/v$version/$name") { "安装包地址不属于官方仓库" }
        val digest = asset.optString("digest")
        require(Regex("sha256:[0-9a-fA-F]{64}").matches(digest)) { "安装包缺少 SHA-256 校验信息，暂不自动安装" }
        val size = asset.optLong("size")
        require(size in 1..2_147_483_648L) { "安装包大小无效" }
        return UpdateRelease(version, json.optString("body").take(16000), name, url, size, digest.substringAfter(':').lowercase())
    }
    fun check(current: String, platform: String): UpdateRelease? = response(LATEST).use { response ->
        if(response.code == 404) return null
        require(response.isSuccessful) { if(response.code == 403 || response.code == 429) "GitHub 请求暂时受限，请稍后重试" else "检查更新失败（HTTP ${response.code}）" }
        val body = response.body ?: error("服务器没有返回版本信息")
        require(body.contentLength() <= 2_000_000) { "版本信息过大" }
        val bytes = body.byteStream().readNBytes(2_000_001)
        require(bytes.size <= 2_000_000) { "版本信息过大" }
        parse(JSONObject(String(bytes, Charsets.UTF_8)), current, platform)
    }
    private fun response(initial: String): okhttp3.Response {
        var url = initial
        repeat(6) {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1,443) &&
                uri.host in setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")) { "更新地址不受信任" }
            val r = client.newCall(Request.Builder().url(url).header("User-Agent","Hermes-Desktop-Updater")
                .header("Accept", if(uri.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream").build()).execute()
            if(r.code !in listOf(301,302,303,307,308)) return r
            val next = r.header("Location")
            r.close()
            url = uri.resolve(next ?: error("下载重定向无效")).toString()
        }
        error("下载重定向次数过多")
    }
    fun verify(file: File, release: UpdateRelease): Boolean {
        if(!file.isFile || file.length() != release.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer=ByteArray(131072); while(true) { val n=stream.read(buffer); if(n<0)break;digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) } == release.sha256
    }
    fun download(release: UpdateRelease, directory: File, progress: (Long,Long)->Unit): File {
        directory.mkdirs()
        val target = File(directory, release.name)
        if(verify(target, release)) return target
        val part = File.createTempFile("download-", ".part", directory)
        try {
            response(release.url).use { r ->
                require(r.isSuccessful) { "下载失败（HTTP ${r.code}）" }
                val body=r.body ?: error("安装包为空")
                body.byteStream().use { input -> part.outputStream().use { output ->
                    val buffer=ByteArray(131072);var total=0L;var last=0L
                    while(true) {
                        if(Thread.currentThread().isInterrupted) error("下载已取消")
                        val n=input.read(buffer);if(n<0)break
                        total+=n;require(total<=release.size) { "安装包大小与发布信息不符" };output.write(buffer,0,n)
                        if(System.currentTimeMillis()-last>250){progress(total,release.size);last=System.currentTimeMillis()}
                    }
                } }
            }
            require(verify(part,release)) { "安装包校验失败，请重新下载" }
            java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            progress(release.size,release.size)
            return target
        } finally { part.delete() }
    }
}
