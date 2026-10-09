package com.qingyu.hermescompanion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class VoiceModelSpec(
    val id: String, val title: String, val source: String, val archiveRoot: String,
    val url: String, val bytes: Long, val sha256: String,
    val required: List<String>, val expandedLimit: Long = 450L * 1024 * 1024,
)

object LocalVoiceModels {
    val recognition = VoiceModelSpec("sense-voice-int8", "SenseVoice · 语音识别", "阿里通义 · SenseVoiceSmall",
        "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2",
        163002883L, "7d1efa2138a65b0b488df37f8b89e3d91a60676e416f515b952358d83dfd347e",
        listOf("model.int8.onnx", "tokens.txt"))
    val playback = VoiceModelSpec("kokoro-int8", "Kokoro · 语音朗读", "hexgrad · Kokoro 1.1",
        "kokoro-int8-multi-lang-v1_1",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2",
        147031220L, "a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6",
        listOf("model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-zh.txt", "lexicon-us-en.txt", "espeak-ng-data/phontab"))
    val all = listOf(recognition, playback)
    fun find(id: String) = all.firstOrNull { it.id == id }
}

data class VoiceModelState(val installed: Boolean = false, val phase: String = "idle", val downloaded: Long = 0,
    val message: String = "") {
    val busy: Boolean get() = phase in setOf("download", "import", "verify", "install", "remove")
}

/** Dedicated public-download client. Never sends gateway cookies, credentials, or recorded audio. */
class VoiceModelStore(val root: File, private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(25, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()) {
    fun directory(spec: VoiceModelSpec) = File(root, spec.id)
    private fun partial(spec: VoiceModelSpec) = File(root, "${spec.id}.part")
    fun installed(spec: VoiceModelSpec): Boolean = runCatching {
        val dir = directory(spec)
        File(dir, "installed.sha256").readText() == spec.sha256 &&
            spec.required.all { File(dir, it).isFile && File(dir, it).length() > 0 }
    }.getOrDefault(false)
    fun states() = LocalVoiceModels.all.associate { spec -> spec.id to VoiceModelState(installed(spec),
        downloaded = partial(spec).length().coerceAtMost(spec.bytes)) }

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    suspend fun download(spec: VoiceModelSpec, progress: (VoiceModelState) -> Unit) = withContext(Dispatchers.IO) {
        root.mkdirs()
        val archive = partial(spec)
        if (archive.length() > spec.bytes) archive.delete()
        val previous = installed(spec)
        check(root.usableSpace >= spec.bytes - archive.length() + spec.expandedLimit + 20L * 1024 * 1024) {
            "可用空间不足，请至少预留 800 MB 后重试"
        }
        val jobContext = currentCoroutineContext()
        if (archive.length() < spec.bytes) {
            val offset = archive.length()
            val request = Request.Builder().url(spec.url).header("Accept-Encoding", "identity").apply {
                if (offset > 0) header("Range", "bytes=$offset-")
            }.build()
            val call = client.newCall(request)
            val cancelHandle = jobContext[kotlinx.coroutines.Job]?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
                if (cause != null) call.cancel()
            }
            try {
                call.execute().use { response ->
                    check(response.isSuccessful) { "下载未完成（${response.code}），可稍后继续" }
                    val append = response.code == 206 && offset > 0
                    if (response.code == 206) require(validResumeRange(response.header("Content-Range"), offset, spec.bytes)) {
                        "下载源返回了错误的续传位置，请重试"
                    }
                    val body = response.body ?: throw IOException("下载内容为空")
                    var count = if (append) offset else 0L
                    var lastUpdate = 0L
                    archive.outputStream(append).buffered().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                jobContext.ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                count += n
                                require(count <= spec.bytes) { "语音包大小不符，请重新下载" }
                                output.write(buffer, 0, n)
                                val now = System.nanoTime()
                                if (now - lastUpdate > 150_000_000) {
                                    progress(VoiceModelState(previous, "download", count)); lastUpdate = now
                                }
                            }
                        }
                    }
                    require(count == spec.bytes) { "下载中断，已保留进度，点按可继续" }
                }
            } finally { cancelHandle?.dispose() }
        }
        installArchive(spec,archive,previous,progress)
    }

    /** Import an existing official archive without changing or deleting the user's file. */
    suspend fun importArchive(spec:VoiceModelSpec,file:File,progress:(VoiceModelState)->Unit)=withContext(Dispatchers.IO) {
        require(file.isFile&&file.length()==spec.bytes) {"请选择对应的完整 .tar.bz2 模型包，当前文件大小不符"}
        root.mkdirs()
        check(root.usableSpace>=spec.bytes+spec.expandedLimit+20L*1024*1024) {"可用空间不足，请至少预留 800 MB 后重试"}
        val previous=installed(spec)
        val copy=File.createTempFile(spec.id+"-import-",".tar.bz2",root)
        try {
            val context=currentCoroutineContext();var count=0L;var lastUpdate=0L
            file.inputStream().buffered().use {input->copy.outputStream().buffered().use {output->
                val buffer=ByteArray(128*1024)
                while(true) {
                    context.ensureActive();val n=input.read(buffer);if(n<0)break
                    count+=n;require(count<=spec.bytes) {"模型包在导入期间发生了变化"}
                    output.write(buffer,0,n)
                    val now=System.nanoTime()
                    if(now-lastUpdate>150_000_000){progress(VoiceModelState(previous,"import",count));lastUpdate=now}
                }
            }}
            require(count==spec.bytes) {"模型包不完整，请重新选择"}
            installArchive(spec,copy,previous,progress)
        }finally{copy.delete()}
    }

    private suspend fun installArchive(spec:VoiceModelSpec,archive:File,previous:Boolean,progress:(VoiceModelState)->Unit) {
        val jobContext=currentCoroutineContext()
        progress(VoiceModelState(previous, "verify", spec.bytes))
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) { jobContext.ensureActive(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != spec.sha256) {
            archive.delete(); throw IOException("语音包校验未通过，请重新下载")
        }
        progress(VoiceModelState(previous, "install", spec.bytes))
        val staging = File(root, "${spec.id}.installing")
        staging.deleteRecursively(); staging.mkdirs()
        try {
            extractVoiceArchive(archive, staging, spec) { jobContext.ensureActive() }
            require(spec.required.all { File(staging, it).isFile && File(staging, it).length() > 0 }) { "语音包缺少必需文件" }
            File(staging, "installed.sha256").writeText(spec.sha256)
            jobContext.ensureActive()
            // Commit is non-suspending. Keep a previous complete pack until its replacement is ready.
            val target = directory(spec)
            val backup = File(root, "${spec.id}.previous")
            backup.deleteRecursively()
            if (target.exists()) check(target.renameTo(backup)) { "无法替换语音包" }
            if (!staging.renameTo(target)) { backup.renameTo(target); error("无法启用语音包，请重试") }
            backup.deleteRecursively(); archive.delete()
            progress(VoiceModelState(true))
        } finally { staging.deleteRecursively() }
    }

    fun delete(spec: VoiceModelSpec) {
        check(directory(spec).deleteRecursively()) { "无法移除语音包" }
        partial(spec).delete(); File(root, "${spec.id}.installing").deleteRecursively()
    }
}

internal fun validResumeRange(header: String?, offset: Long, total: Long): Boolean {
    val m = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header.orEmpty()) ?: return false
    val (start, end, size) = m.destructured
    return start.toLongOrNull() == offset && size.toLongOrNull() == total && end.toLongOrNull() == total - 1
}

internal fun extractVoiceArchive(archive: File, target: File, spec: VoiceModelSpec, checkActive: () -> Unit = {}) {
    var expanded = 0L; var files = 0
    val paths = hashSetOf<String>()
    TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
        while (true) {
            checkActive()
            val entry = tar.nextEntry ?: break
            require(++files <= 4000 && (entry.isFile || entry.isDirectory) && !entry.isSymbolicLink && !entry.isLink) { "语音包包含不支持的文件" }
            val name = entry.name.removeSuffix("/")
            if (name == spec.archiveRoot && entry.isDirectory) continue
            require(name.startsWith(spec.archiveRoot + "/")) { "语音包目录不符" }
            val relative = name.removePrefix(spec.archiveRoot + "/")
            require(relative.isNotBlank() && !relative.contains('\\') && relative.split('/').none { it == ".." || it == "." }) { "语音包路径不合法" }
            val file = File(target, relative)
            require(file.canonicalPath.startsWith(target.canonicalPath + File.separator) && paths.add(relative)) { "语音包文件路径不合法" }
            if (entry.isDirectory) file.mkdirs() else {
                expanded += entry.size
                require(entry.size >= 0 && expanded <= spec.expandedLimit) { "语音包解压体积超出限制" }
                file.parentFile?.mkdirs()
                file.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) { checkActive(); val n = tar.read(buffer); if (n < 0) break; count += n; require(count <= entry.size); output.write(buffer, 0, n) }
                    require(count == entry.size) { "语音包文件不完整" }
                }
            }
        }
    }
}

private fun File.outputStream(append: Boolean) = java.io.FileOutputStream(this, append)
