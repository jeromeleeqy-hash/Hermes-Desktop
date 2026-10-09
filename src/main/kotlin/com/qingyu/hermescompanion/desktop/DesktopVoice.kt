package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import com.qingyu.hermescompanion.data.*
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.Call
import java.nio.file.Files
import javax.swing.SwingUtilities
import com.qingyu.hermescompanion.platform.DesktopHost

/** One capture has a fixed account/profile/session and an encrypted retryable recording. */
class DesktopVoice(private val c:DesktopController,private val audio:DesktopAudio=DesktopAudio()) {
    var active by mutableStateOf(false);private set
    var fromCompanion by mutableStateOf(false);private set
    var holdToTalk by mutableStateOf(false);private set
    var phase by mutableStateOf(VoicePhase.IDLE);private set
    var level by mutableStateOf(0f);private set
    var message by mutableStateOf("");private set
    var transcript by mutableStateOf("");private set
    var provider by mutableStateOf("");private set
    var sessionKey:String?=null;private set
    val models by lazy {DesktopVoiceModels(c)}
    private var voiceText:StreamingVoiceText?=null
    private var sentences:Channel<String>?=null
    private var streamKey:String?=null
    private var streamFinished=false
    private var target:HermesSession?=null
    private var targetProfile="default"
    private var generation=0L
    private var job:Job?=null
    private var call:Call?=null
    private var holdPressed=false
    private var continuousCapture=false
    private var allowAutoSend=true
    private var awake:Process?=null
    private var windowsAwake:WindowsVoiceWakeLock?=null
    fun open(session:HermesSession?=c.currentSession,fromCompanion:Boolean=false,holdToTalk:Boolean=false) {
        if(active&&sessionKey==session?.scopedId&&!holdToTalk&&!this.holdToTalk)return
        if(active||phase!=VoicePhase.IDLE)close()
        if(session==null){if(!holdToTalk)c.newSession(true) { open(it,fromCompanion) };return}
        target=session;sessionKey=session.scopedId;targetProfile=session.profile;this.fromCompanion=fromCompanion
        this.holdToTalk=holdToTalk;holdPressed=holdToTalk
        if(c.demo){active=true;message=if(holdToTalk)"按住说话，松开发送"else"语音对话";return}
        active=true
        if(DesktopHost.isWindows)windowsAwake=WindowsVoiceWakeLock()
        if(System.getProperty("os.name").startsWith("Mac"))awake=runCatching { ProcessBuilder("/usr/bin/caffeinate","-d","-w",ProcessHandle.current().pid().toString()).start() }.getOrNull()
        val pref=c.voicePreferences
        if(pref.sttEngine=="local" || pref.ttsEngine=="local")c.scope.launch {
            runCatching {models.engine.warm(pref.sttEngine=="local",pref.ttsEngine=="local",pref.language)}
        }
        startCapture(!holdToTalk)
    }
    fun releaseHoldToTalk() {
        if(!holdToTalk||!holdPressed)return
        holdPressed=false;allowAutoSend=true
        // The 60-second limit may finish recognition before mouse-up; still wait for release.
        if(phase==VoicePhase.IDLE&&transcript.isNotBlank())target?.let(::sendHeldTranscript)
        else stopCapture()
    }
    fun cancelHoldToTalk() {
        if(!holdToTalk)return
        holdPressed=false;pause()
    }
    fun startCapture(continuous:Boolean=active,settingsTest:Boolean=false) {
        if(c.demo)return
        if(holdToTalk&&!holdPressed){message="按住悬浮球说话，松开后发送";return}
        if(!c.voicePreferences.enabled){onFailure("请先在语音设置中启用语音输入。");return}
        if(!c.connected && !(settingsTest&&c.voicePreferences.sttEngine in setOf("local","system"))){onFailure("请先连接网关；本地识别可在语音设置中离线测试。");return}
        if(c.voicePreferences.sttEngine=="local"&&!models.store.installed(LocalVoiceModels.recognition)){onFailure("请先在语音设置下载并启用本地识别模型。");return}
        if(phase==VoicePhase.LISTENING || phase==VoicePhase.TRANSCRIBING)return
        val captureSession=if(active)target else c.currentSession
        if(!settingsTest && captureSession==null){c.newSession(true) { startCapture(continuous) };return}
        audio.stopPlayback();job?.cancel();call?.cancel()
        target=if(settingsTest)null else captureSession;sessionKey=target?.scopedId;targetProfile=target?.profile?:c.profile
        continuousCapture=continuous&&!holdToTalk;allowAutoSend=!holdToTalk;val token=++generation;val started=System.currentTimeMillis()
        val detector=VoiceSilenceDetector(sensitivity=c.voicePreferences.noiseSensitivity)
        try {
            audio.start { sample -> SwingUtilities.invokeLater {
                if(generation==token && phase==VoicePhase.LISTENING) {
                    level=sample.displayLevel
                    val done=detector.sampleDb(sample.dbFs,System.currentTimeMillis())
                    message=if(holdToTalk)"松开鼠标，识别后发送"else if(detector.isCalibrating)"正在适应环境声音" else "我在听，说完后可以稍作停顿"
                    if((continuousCapture && done) || System.currentTimeMillis()-started>=60_000)stopCapture()
                }
            } }
            transcript="";phase=VoicePhase.LISTENING;message=if(holdToTalk)"松开鼠标，识别后发送"else"我在听";level=0f
        } catch(e:Exception){onFailure(e.message ?: "录音失败")}
    }
    fun stopCapture() {
        if(phase!=VoicePhase.LISTENING)return
        phase=VoicePhase.TRANSCRIBING;level=0f;message=if(holdToTalk&&allowAutoSend)"正在识别，完成后自动发送"else"正在识别"
        val token=generation;val epoch=c.epoch;val session=target;val profile=targetProfile
        val note=try {
            val file=audio.stop()
            try {VoiceNote(profile=profile,session=session,blob=c.store.saveBlob(file.readBytes()),engine=c.voicePreferences.sttEngine)}finally{file.delete()}
        } catch(e:Exception){onFailure(e.message ?: "录音失败");return}
        c.voiceNotes+=note
        job=c.scope.launch {
            try {
                c.saveCheckpoint()
                if(token==generation)transcribe(note,token,epoch)
            } catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation)onFailure(e.message ?: "录音失败")}
        }
    }
    fun retry(note:VoiceNote) {
        if(phase in setOf(VoicePhase.LISTENING,VoicePhase.TRANSCRIBING))return
        target=note.session;targetProfile=note.profile;sessionKey=note.session?.scopedId;continuousCapture=false;allowAutoSend=false
        val token=++generation;val epoch=c.epoch
        phase=VoicePhase.TRANSCRIBING;message="重新识别录音"
        job=c.scope.launch { try { transcribe(note,token,epoch) } catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation)onFailure(e.message ?: "识别失败")} }
    }
    private suspend fun transcribe(note:VoiceNote,token:Long,epoch:Int) {
        val preference=c.voicePreferences.let {if(note.engine.isBlank())it else it.copy(sttEngine=note.engine)}
        val api=if(preference.sttEngine in setOf("local","system"))null else c.client(note.profile)
        val result=withContext(Dispatchers.IO) {
            val file=Files.createTempFile("hermes-retry-",".wav").toFile()
            try {
                file.writeBytes(c.store.readBlob(note.blob))
                if(preference.sttEngine=="local") models.engine.transcribe(file,preference.language)
                else if(preference.sttEngine=="system") NativeSpeech.transcribe(file,voiceRecognitionLanguage(preference.language,preference.transcriptScript))
                else try { requireNotNull(api).transcribeAudioFile(file,note.profile) { call=it } }
                catch(e:Exception) {
                    if(preference.sttEngine=="automatic" && e is ApiException && e.statusCode in setOf(404,501,503) && NativeSpeech.available())NativeSpeech.transcribe(file,voiceRecognitionLanguage(preference.language,preference.transcriptScript))
                    else throw e
                }
            } finally { file.delete();call=null }
        }
        if(token!=generation || epoch!=c.epoch)return
        val text=normalizeVoiceTranscript(result.transcript.trim(),preference.transcriptScript)
        require(text.isNotBlank()) { "没有识别到文字，录音已保留，可以重试。" }
        transcript=text;provider=result.provider
        val updated=note.copy(transcript=text,committed=true)
        val index=c.voiceNotes.indexOfFirst { it.id==note.id };if(index>=0)c.voiceNotes[index]=updated
        // Text and the committed marker enter the same checkpoint, preventing duplicate insertion.
        val s=note.session
        if(s!=null && !note.committed)c.setDraft(s.scopedId,(c.drafts[s.scopedId].orEmpty()+" "+text).trim())
        c.saveCheckpoint()
        if(token!=generation || epoch!=c.epoch)return
        phase=VoicePhase.IDLE;message="识别完成"
        if(s==null)c.showDetails("识别结果",text)
        else if(holdToTalk)sendHeldTranscript(s)
        else if(allowAutoSend && continuousCapture && active && sessionKey==s.scopedId&&targetProfile==c.profile) {
            if(c.runs.containsKey(s.scopedId)){message="文字已保留，等待当前任务结束";return}
            phase=VoicePhase.THINKING;message="Hermes 正在回应"
            c.startRun(s,c.drafts[s.scopedId].orEmpty(),c.attachments[s.scopedId].orEmpty(),voiceTurn=true)
        } else if(allowAutoSend && preference.autoSend && c.currentSession?.scopedId==s.scopedId)c.sendToSession(s)
    }
    private fun sendHeldTranscript(s:HermesSession) {
        if(!allowAutoSend||!active||sessionKey!=s.scopedId||targetProfile!=c.profile){message="文字已保留在草稿中";return}
        val blocked=when {
            !c.connected->"连接已断开，文字已保留在草稿中"
            c.modelSwitching[s.scopedId]==true->"模型正在切换，文字已保留在草稿中"
            (c.attachmentLoads[s.scopedId]?:0)>0->"附件正在准备，文字已保留在草稿中"
            else->null
        }
        if(blocked!=null){message=blocked;return}
        if(c.runs.containsKey(s.scopedId)) {
            c.sendToSession(s,queue=true);message="已加入队列，当前任务完成后发送"
        }else {
            c.startRun(s,c.drafts[s.scopedId].orEmpty(),c.attachments[s.scopedId].orEmpty(),voiceTurn=true)
            if(c.runs.containsKey(s.scopedId)){phase=VoicePhase.THINKING;message="Hermes 正在回应"}
            else message=c.sendErrors[s.scopedId]?:"文字已保留在草稿中"
        }
    }
    fun reuse(note:VoiceNote) { note.session?.let { s->c.setDraft(s.scopedId,(c.drafts[s.scopedId].orEmpty()+"\n"+note.transcript).trim());c.openSession(s) } }
    fun remove(note:VoiceNote) { c.voiceNotes.removeAll { it.id==note.id } }
    fun beginTurn(key:String) {
        if(!active || sessionKey!=key || !c.voicePreferences.autoRead)return
        beginPlayback(key,restart=!holdToTalk)
    }
    fun updateReply(key:String,text:String) {
        if(streamKey!=key || streamFinished)return
        enqueueSpeech(text,false)
    }
    private fun enqueueSpeech(text:String,finished:Boolean) {
        val pieces=voiceText?.update(text,finished).orEmpty()
        for(piece in pieces)if(sentences?.trySend(piece)?.isSuccess!=true){
            pause();message="朗读暂未跟上回复，完整内容已保留，可手动朗读";return
        }
        if(finished){streamFinished=true;sentences?.close()}
    }
    fun onReply(key:String,text:String) {
        if(!active || sessionKey!=key)return
        if(phase==VoicePhase.LISTENING||phase==VoicePhase.TRANSCRIBING)return
        if(c.voicePreferences.autoRead){
            if(streamKey!=key&&!beginPlayback(key,restart=!holdToTalk))return
            enqueueSpeech(text,true)
        }else {phase=VoicePhase.IDLE;message="回复已完成";if(!holdToTalk&&(c.voicePreferences.continuous||fromCompanion))startCapture(true)}
    }
    fun speak(text:String,restart:Boolean=false) {
        if(c.demo)return
        if(phase==VoicePhase.LISTENING || phase==VoicePhase.TRANSCRIBING){message="请先结束录音或识别，再朗读。";return}
        if(beginPlayback(null,restart))enqueueSpeech(text,true)
    }
    private data class PreparedSpeech(val text:String,val audio:SpeechAudio?,val rate:Float)
    private fun beginPlayback(key:String?,restart:Boolean):Boolean {
        generation++;job?.cancel();sentences?.cancel();audio.stopPlayback()
        voiceText=null;sentences=null;streamKey=null
        val pref=c.voicePreferences
        if(pref.ttsEngine=="local"&&!models.store.installed(LocalVoiceModels.playback)){onFailure("请先在语音设置下载并启用本地朗读模型。");return false}
        if(pref.ttsEngine !in setOf("local","system")&&!c.connected){onFailure("服务器语音需要连接网关；也可以在语音设置使用本地朗读。");return false}
        voiceText=StreamingVoiceText();streamKey=key;streamFinished=false
        val queue=Channel<String>(64);sentences=queue
        val token=++generation;val epoch=c.epoch;val profile=if(active)targetProfile else c.profile
        val api=if(pref.ttsEngine in setOf("local","system"))null else c.client(profile)
        phase=VoicePhase.THINKING;message="正在准备回应"
        job=c.scope.launch {
            try {
                coroutineScope {
                    // One prepared chunk ahead: synthesis overlaps playback, with bounded memory.
                    val prepared=Channel<PreparedSpeech>(1)
                    val producer=launch(Dispatchers.IO) {
                        try {
                            val serverVoice=if(pref.ttsEngine=="automatic")runCatching {requireNotNull(api).voiceSettings(profile).tts}.getOrNull()else null
                            for(chunk in queue){
                                ensureActive()
                                val system=pref.ttsEngine=="system" || (pref.ttsEngine=="automatic" && speechLanguage(chunk,pref.language).startsWith("zh") && serverVoice!=null && !agentVoiceSupportsChinese(serverVoice))
                                val sound=if(system)null else try {
                                    if(pref.ttsEngine=="local")models.engine.synthesize(chunk,pref.localSpeaker,pref.speechRate)
                                    else requireNotNull(api).synthesizeSpeech(chunk,profile)
                                }catch(e:CancellationException){throw e}catch(e:Exception){ensureActive();if(pref.ttsEngine=="automatic")null else throw e}
                                ensureActive()
                                prepared.send(PreparedSpeech(chunk,sound,if(pref.ttsEngine=="local")1f else pref.speechRate))
                            }
                        }finally{prepared.close()}
                    }
                    for(chunk in prepared){
                        ensureActive();if(token!=generation || epoch!=c.epoch)return@coroutineScope
                        phase=VoicePhase.SPEAKING;message="正在朗读"
                        withContext(Dispatchers.IO){ensureActive();if(chunk.audio!=null)audio.play(chunk.audio,chunk.rate)else audio.systemSpeak(chunk.text,speechLanguage(chunk.text,pref.language),pref.speechRate)}
                        if(token==generation && !streamFinished){phase=VoicePhase.THINKING;message="正在继续回应"}
                    }
                    producer.join()
                }
                if(token==generation){
                    phase=VoicePhase.IDLE;message=if(holdToTalk)"朗读结束，按住悬浮球可以继续说话"else"朗读结束"
                    if(restart && active && !holdToTalk && (pref.continuous||fromCompanion))startCapture(true)
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation)onFailure(e.message?:"朗读失败")}
        }
        return true
    }
    fun interrupt() {
        val s=target
        pause()
        if(s!=null && c.runs.containsKey(s.scopedId))c.stop(s){if(active && sessionKey==s.scopedId && targetProfile==c.profile)startCapture(true)}
        else startCapture(active)
    }
    fun pause() {
        allowAutoSend=false
        if(phase==VoicePhase.LISTENING){stopCapture();return}
        generation++;job?.cancel();sentences?.cancel();sentences=null;voiceText=null;streamKey=null;call?.cancel();NativeSpeech.cancel();audio.stopPlayback();phase=VoicePhase.IDLE;level=0f;message="已暂停"
    }
    fun onFailure(text:String) { phase=VoicePhase.ERROR;message=text;level=0f }
    fun onRunFailure(key:String,text:String) {
        // A finishing/cancelled older task must not overwrite a newer capture's phase.
        if(active&&sessionKey==key&&phase in setOf(VoicePhase.THINKING,VoicePhase.SPEAKING)){pause();onFailure(text)}
    }
    fun dismiss() { active=false;fromCompanion=false;holdPressed=false;continuousCapture=false;pause();holdToTalk=false;awake?.destroy();awake=null;windowsAwake?.close();windowsAwake=null }
    fun close() {
        active=false;fromCompanion=false;holdPressed=false;holdToTalk=false;generation++;job?.cancel();sentences?.cancel();sentences=null;voiceText=null;streamKey=null;call?.cancel();NativeSpeech.cancel()
        if(phase==VoicePhase.LISTENING)runCatching { val file=audio.stop();try { val blob=c.store.saveBlob(file.readBytes());c.voiceNotes+=VoiceNote(profile=targetProfile,session=target,blob=blob,engine=c.voicePreferences.sttEngine) } finally { file.delete() } }
        audio.close();awake?.destroy();awake=null;windowsAwake?.close();windowsAwake=null;phase=VoicePhase.IDLE
    }
}

object NativeSpeech {
    @Volatile private var process:Process?=null
    private fun helper():java.io.File? {
        val resource=System.getProperty("compose.application.resources.dir")
        return listOfNotNull(resource?.let { java.io.File(it,"HermesSpeech") },java.io.File("packaging/native/HermesSpeech")).firstOrNull { it.canExecute() }
    }
    fun available()=if(DesktopHost.isWindows)WindowsSpeech.available() else helper()!=null
    fun transcribe(file:java.io.File,language:String):SpeechTranscription {
        val helper=if(DesktopHost.isWindows)null else helper() ?: error("系统识别组件尚未生成，请运行 Build-macOS.command，或在语音设置选择服务器识别。")
        val output=Files.createTempFile("hermes-stt-result-",".json").toFile()
        var ownProcess:Process?=null
        try {
            val p=if(DesktopHost.isWindows)WindowsSpeech.start("transcribe",file,output,language) else ProcessBuilder(helper!!.absolutePath,file.absolutePath,language).redirectOutput(output).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            process=p;ownProcess=p
            check(p.waitFor(95,java.util.concurrent.TimeUnit.SECONDS)) { "系统识别超时，录音已保留。" }
            check(output.length()>0) { "系统语音组件未能运行，请选择服务器识别，或检查系统语音组件是否可用。录音已保留。" }
            val value=org.json.JSONObject(output.readText(Charsets.UTF_8).removePrefix("\uFEFF"))
            check(p.exitValue()==0) { value.optString("error","系统语音识别不可用。") }
            return SpeechTranscription(value.getString("text"),DesktopHost.os.label)
        } finally { ownProcess?.destroy();ownProcess?.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);if(process===ownProcess)process=null;output.delete() }
    }
    fun cancel() { process?.destroy();process=null }
}
