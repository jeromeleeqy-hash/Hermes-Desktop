package com.qingyu.hermescompanion.data
import java.io.File
import kotlinx.coroutines.runBlocking

/** Optional real-model integration check; has no microphone, server or external speech API. */
object LocalVoiceSmoke {
    @JvmStatic fun main(args:Array<String>)=runBlocking {
        val root=File(args.first());val store=VoiceModelStore(root)
        for(spec in LocalVoiceModels.all){
            if(!store.installed(spec)){
                val archive=File(root,"${spec.id}.tar.bz2")
                store.importArchive(spec,archive){}
                check(archive.isFile&&store.installed(spec))
            }
        }
        val engine=LocalSpeechEngine(store)
        try{
            val result=engine.synthesize("你好，这是桌面版的本地语音测试。",0,1f)
            val wave=File(root,"local-voice-smoke.wav");wave.writeBytes(result.bytes)
            check(result.bytes.size>32000);val text=engine.transcribe(wave,"zh-CN")
            check(text.transcript.isNotBlank());println("Local TTS produced ${result.bytes.size} bytes; STT result: ${text.transcript}")
        }finally{engine.release()}
    }
}
