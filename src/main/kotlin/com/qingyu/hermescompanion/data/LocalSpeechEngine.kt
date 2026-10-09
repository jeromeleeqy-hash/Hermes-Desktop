package com.qingyu.hermescompanion.data

import com.k2fsa.sherpa.onnx.*
import com.qingyu.hermescompanion.model.SpeechAudio
import com.qingyu.hermescompanion.model.SpeechTranscription
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Native calls and model deletion share a mutex; cancellation cannot free a running inference. */
class LocalSpeechEngine(private val store:VoiceModelStore){
    private val mutex=Mutex()
    private var recognizer:OfflineRecognizer?=null
    private var language=""
    private var tts:OfflineTts?=null
    suspend fun release()=withContext(Dispatchers.IO){mutex.withLock{releaseLocked()}}
    suspend fun remove(spec:VoiceModelSpec)=withContext(Dispatchers.IO){mutex.withLock{releaseLocked();store.delete(spec)}}
    suspend fun warm(stt:Boolean,read:Boolean,lang:String)=guarded{if(stt)recognition(lang);if(read)playback();Unit}
    suspend fun transcribe(file:File,lang:String):SpeechTranscription=guarded{
        val active=recognition(lang);val context=currentCoroutineContext();val decoded=decodeVoiceFile(file)
        val result=mutableListOf<String>();var start=0
        while(start<decoded.samples.size){
            context.ensureActive();val end=speechSliceEnd(decoded.samples,start,decoded.sampleRate);val stream=active.createStream()
            try{stream.acceptWaveform(decoded.samples.copyOfRange(start,end),decoded.sampleRate);active.decode(stream);context.ensureActive()
                active.getResult(stream).text.replace(Regex("<\\|[^>]+\\|>"),"").trim().takeIf {it.isNotBlank()}?.let(result::add)
            }finally{stream.release()}
            start=end
        }
        check(result.isNotEmpty()){ "没有识别到清晰的话音，录音已保留，可重试" }
        SpeechTranscription(result.joinToString(" "),"电脑本地 · SenseVoice")
    }
    suspend fun synthesize(text:String,speaker:Int,rate:Float):SpeechAudio=guarded{
        val active=playback();val context=currentCoroutineContext()
        val audio=active.generateWithCallback(text,speaker.coerceIn(0,active.numSpeakers-1),rate.coerceIn(.6f,1.6f),
            OfflineTtsCallback { _ -> if(context.isActive)1 else 0 })
        context.ensureActive();check(audio.samples.isNotEmpty()){ "本地朗读未生成声音，请换一段文字重试" }
        SpeechAudio(pcmWave(audio.samples,audio.sampleRate),"audio/wav","电脑本地 · Kokoro")
    }
    private fun recognition(lang:String):OfflineRecognizer{
        check(store.installed(LocalVoiceModels.recognition)){"请先在语音设置下载 SenseVoice 识别包"}
        val normalized=lang.substringBefore('-').lowercase().takeIf {it in setOf("zh","en","ja","ko","yue")}?:"auto"
        if(recognizer!=null && language==normalized)return recognizer!!
        recognizer?.release();recognizer=null
        val dir=store.directory(LocalVoiceModels.recognition)
        val sense=OfflineSenseVoiceModelConfig.builder().setModel(File(dir,"model.int8.onnx").path).setLanguage(normalized).setInverseTextNormalization(true).build()
        val model=OfflineModelConfig.builder().setSenseVoice(sense).setTokens(File(dir,"tokens.txt").path).setNumThreads(2).setProvider("cpu").setDebug(false).build()
        return OfflineRecognizer(OfflineRecognizerConfig.builder().setOfflineModelConfig(model).build()).also {recognizer=it;language=normalized}
    }
    private fun playback():OfflineTts{
        check(store.installed(LocalVoiceModels.playback)){"请先在语音设置下载 Kokoro 朗读包"}
        tts?.let {return it};val dir=store.directory(LocalVoiceModels.playback);fun path(name:String)=File(dir,name).path
        val kokoro=OfflineTtsKokoroModelConfig.builder().setModel(path("model.int8.onnx")).setVoices(path("voices.bin"))
            .setTokens(path("tokens.txt")).setDataDir(path("espeak-ng-data")).setLexicon(listOf("lexicon-us-en.txt","lexicon-zh.txt").joinToString(",",transform=::path)).build()
        val model=OfflineTtsModelConfig.builder().setKokoro(kokoro).setNumThreads(2).setProvider("cpu").setDebug(false).build()
        return OfflineTts(OfflineTtsConfig.builder().setModel(model).setRuleFsts(listOf("date-zh.fst","number-zh.fst","phone-zh.fst").joinToString(",",transform=::path)).build()).also {tts=it}
    }
    private suspend fun <T> guarded(block:suspend ()->T):T=withContext(Dispatchers.IO){mutex.withLock{
        ensureActive()
        try{block()}catch(e:OutOfMemoryError){releaseLocked();throw IllegalStateException("可用内存不足，请关闭大型应用或改用系统／服务端语音",e)}
        catch(e:LinkageError){throw IllegalStateException("本地语音运行组件不可用，请重新安装完整桌面版，或使用系统／服务端语音",e)}
    }}
    private fun releaseLocked(){recognizer?.release();recognizer=null;tts?.release();tts=null}
}
internal data class DecodedVoice(val samples:FloatArray,val sampleRate:Int)
internal fun decodeVoiceFile(file:File):DecodedVoice{
    require(file.length() in 44..10_000_000){"录音文件大小不符"}
    AudioSystem.getAudioInputStream(file).use {source->
        val format=AudioFormat(16000f,16,1,true,false)
        AudioSystem.getAudioInputStream(format,source).use {input->
            val bytes=input.readNBytes(16000*2*301+1);require(bytes.size<=16000*2*301 && bytes.size%2==0){"录音超过 5 分钟"}
            val pcm=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return DecodedVoice(FloatArray(bytes.size/2){pcm.short/32768f},16000)
        }
    }
}

/** Bound attention memory and prefer the quietest 100 ms in the last five seconds of each slice. */
internal fun speechSliceEnd(samples: FloatArray, start: Int, rate: Int): Int {
    val maximum = minOf(start + rate * 20, samples.size)
    if (maximum == samples.size) return maximum
    val window = rate / 10
    var best = maximum; var lowest = Double.MAX_VALUE
    var cursor = start + rate * 15
    while (cursor + window <= maximum) {
        var energy = 0.0
        for (i in cursor until cursor + window) energy += samples[i] * samples[i]
        if (energy < lowest) { lowest = energy; best = cursor + window / 2 }
        cursor += window
    }
    return best
}

internal fun pcmWave(samples: FloatArray, rate: Int): ByteArray {
    val size = samples.size * 2
    val buffer = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray()).putInt(16)
        .putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        .put("data".toByteArray()).putInt(size)
    samples.forEach { buffer.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
    return buffer.array()
}
