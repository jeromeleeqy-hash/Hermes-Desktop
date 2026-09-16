package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import com.qingyu.hermescompanion.data.*
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
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
        if(!c.connected){onFailure("请先连接网关。");return}
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
            try {VoiceNote(profile=profile,session=session,blob=c.store.saveBlob(file.readBytes()))}finally{file.delete()}
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
        val api=c.client(note.profile)
        val preference=c.voicePreferences
        val result=withContext(Dispatchers.IO) {
            val file=Files.createTempFile("hermes-retry-",".wav").toFile()
            try {
                file.writeBytes(c.store.readBlob(note.blob))
                if(preference.engine=="system") NativeSpeech.transcribe(file,voiceRecognitionLanguage(preference.language,preference.transcriptScript))
                else try { api.transcribeAudioFile(file,note.profile) { call=it } }
                catch(e:Exception) {
                    if(preference.engine=="automatic" && e is ApiException && e.statusCode in setOf(404,501,503) && NativeSpeech.available())NativeSpeech.transcribe(file,voiceRecognitionLanguage(preference.language,preference.transcriptScript))
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
    fun onReply(key:String,text:String) {
        if(!active || sessionKey!=key)return
        if(phase==VoicePhase.LISTENING||phase==VoicePhase.TRANSCRIBING)return
        if(c.voicePreferences.autoRead)speak(text,!holdToTalk)
        else { phase=VoicePhase.IDLE;message=if(holdToTalk)"回复已完成，按住悬浮球可以继续说话"else"回复已完成";if(!holdToTalk&&(c.voicePreferences.continuous||fromCompanion))startCapture(true) }
    }
    fun speak(text:String,restart:Boolean=false) {
        if(c.demo)return
        if(phase==VoicePhase.LISTENING){message="请先结束录音，再朗读。";return}
        if(phase==VoicePhase.TRANSCRIBING){message="请先完成或取消识别。";return}
        job?.cancel();audio.stopPlayback();val token=++generation;val epoch=c.epoch;val profile=if(active)targetProfile else c.profile;val api=c.client(profile)
        val pref=c.voicePreferences;val chunks=speechChunks(spokenReply(text))
        if(chunks.isEmpty())return
        phase=VoicePhase.SPEAKING;message="正在朗读"
        job=c.scope.launch {
            try {
                val serverVoice=if(pref.engine=="automatic")withContext(Dispatchers.IO) {runCatching {api.voiceSettings(profile).tts}.getOrNull()}else null
                for(chunk in chunks) {
                    ensureActive();if(token!=generation || epoch!=c.epoch)return@launch
                    withContext(Dispatchers.IO) {
                        ensureActive()
                        if(pref.engine=="system" || (pref.engine=="automatic" && speechLanguage(chunk,pref.language).startsWith("zh") && serverVoice!=null && !agentVoiceSupportsChinese(serverVoice)))audio.systemSpeak(chunk,speechLanguage(chunk,pref.language),pref.speechRate)
                        else try { val result=api.synthesizeSpeech(chunk,profile);ensureActive();audio.play(result,pref.speechRate) }
                        catch(e:Exception) { if(pref.engine=="automatic" && isActive)audio.systemSpeak(chunk,speechLanguage(chunk,pref.language),pref.speechRate) else throw e }
                    }
                }
                if(token==generation) {phase=VoicePhase.IDLE;message=if(holdToTalk)"朗读结束，按住悬浮球可以继续说话"else"朗读结束";if(restart && active && !holdToTalk && (pref.continuous||fromCompanion))startCapture(true)}
            } catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation)onFailure(e.message ?: "朗读失败")}
        }
    }
    fun interrupt() {
        val s=target
        pause()
        if(s!=null && c.runs.containsKey(s.scopedId))c.stop(s)
        else startCapture(active)
    }
    fun pause() {
        allowAutoSend=false
        if(phase==VoicePhase.LISTENING){stopCapture();return}
        generation++;job?.cancel();call?.cancel();NativeSpeech.cancel();audio.stopPlayback();phase=VoicePhase.IDLE;level=0f;message="已暂停"
    }
    fun onFailure(text:String) { phase=VoicePhase.ERROR;message=text;level=0f }
    fun onRunFailure(key:String,text:String) {
        // A finishing/cancelled older task must not overwrite a newer capture's phase.
        if(active&&sessionKey==key&&phase==VoicePhase.THINKING)onFailure(text)
    }
    fun dismiss() { active=false;fromCompanion=false;holdPressed=false;continuousCapture=false;pause();holdToTalk=false;awake?.destroy();awake=null;windowsAwake?.close();windowsAwake=null }
    fun close() {
        active=false;fromCompanion=false;holdPressed=false;holdToTalk=false;generation++;job?.cancel();call?.cancel();NativeSpeech.cancel()
        if(phase==VoicePhase.LISTENING)runCatching { val file=audio.stop();try { val blob=c.store.saveBlob(file.readBytes());c.voiceNotes+=VoiceNote(profile=targetProfile,session=target,blob=blob) } finally { file.delete() } }
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
