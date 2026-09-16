package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import com.qingyu.hermescompanion.model.*
import java.io.File
import java.util.Base64
import java.util.UUID

enum class CompanionAction { ASK, CAPTURE, VOICE, HOLD_TO_TALK }

/** Quick questions share a session's real drafts and runs, without changing the main window's page. */
class DesktopCompanion(private val c:DesktopController) {
    var panelOpen by mutableStateOf(false);private set
    var voicePanel by mutableStateOf(false);private set
    var preparing by mutableStateOf(false);private set
    var holdingToTalk by mutableStateOf(false);private set
    var failure by mutableStateOf<String?>(null);private set
    private var selected by mutableStateOf<HermesSession?>(null)
    val session:HermesSession? get()=selected?.let {s->c.sessions.firstOrNull {it.scopedId==s.scopedId}?:s}
    var capture:((HermesSession)->Unit)?=null
    var showMain:(()->Unit)?=null
    private var generation=0
    private var afterReady=CompanionAction.ASK
    private val pendingFiles=mutableListOf<File>()
    val pendingFileCount get()=pendingFiles.size

    fun open(action:CompanionAction=CompanionAction.ASK,files:List<File> = emptyList()) {
        if(action!=CompanionAction.HOLD_TO_TALK)cancelHoldToTalk()
        val wasOpen=panelOpen
        pendingFiles.addAll(files)
        panelOpen=true;failure=null;afterReady=action
        if(!c.connected){preparing=false;failure="请先连接 Hermes 网关。";return}
        if(preparing)return
        // A fresh invocation starts in the daily assistant. While the panel is open,
        // dropped files and screenshots remain attached to the user's selected session.
        if(wasOpen&&session?.profile==c.profile){ready(session!!);return}
        prepareDaily()
    }
    fun beginHoldToTalk() {
        if(holdingToTalk)return
        if(c.voice.phase==VoicePhase.TRANSCRIBING){failure="上一段语音正在识别，请稍等。";panelOpen=true;return}
        holdingToTalk=true
        open(CompanionAction.HOLD_TO_TALK)
    }
    fun releaseHoldToTalk() {
        if(!holdingToTalk)return
        holdingToTalk=false
        // A delayed session lookup must never open the microphone after mouse-up.
        if(afterReady==CompanionAction.HOLD_TO_TALK){afterReady=CompanionAction.ASK;if(preparing)failure="会话还在准备，请稍后再次按住悬浮球说话。"}
        c.voice.releaseHoldToTalk()
    }
    fun cancelHoldToTalk() {
        if(afterReady==CompanionAction.HOLD_TO_TALK)afterReady=CompanionAction.ASK
        if(!holdingToTalk)return
        holdingToTalk=false;c.voice.cancelHoldToTalk()
    }
    private fun prepareDaily() {
        val token=++generation;val p=c.profile
        preparing=true;voicePanel=false
        if(c.demo) {
            val s=c.sessions.firstOrNull {it.profile==p&&it.title=="日常助理"}?:HermesSession(UUID.randomUUID().toString(),"日常助理",profile=p)
            c.sessions=(listOf(s)+c.sessions).distinctBy {it.scopedId};preparing=false;ready(s);return
        }
        c.request(p,{c.resolveDailySession(it,p)},finished={if(token==generation)preparing=false},failed={if(token==generation)failure=it}) {s->
            if(token==generation&&p==c.profile&&panelOpen) {
                c.sessions=(listOf(s)+c.sessions).distinctBy {it.scopedId};ready(s)
            }
        }
    }
    private fun ready(s:HermesSession) {
        selected=s;c.loadSessionMessages(s)
        if(pendingFiles.isNotEmpty()){c.addFilesToDraft(s.scopedId,pendingFiles.toList());pendingFiles.clear()}
        when(afterReady) {
            CompanionAction.CAPTURE->{voicePanel=false;c.voice.dismiss();capture?.invoke(s)}
            CompanionAction.VOICE->{voicePanel=true;c.voice.open(s,fromCompanion=true)}
            CompanionAction.HOLD_TO_TALK->{if(holdingToTalk){voicePanel=true;c.voice.open(s,fromCompanion=true,holdToTalk=true)}}
            CompanionAction.ASK->{voicePanel=false;if(c.voice.fromCompanion)c.voice.dismiss()}
        }
        afterReady=CompanionAction.ASK
    }
    fun select(s:HermesSession) {
        if(s.profile!=c.profile)return
        cancelHoldToTalk()
        c.voice.dismiss();voicePanel=false;selected=s;failure=null;c.loadSessionMessages(s)
    }
    fun addCapture(s:HermesSession,bytes:ByteArray) {
        if(s.profile!=c.profile||!panelOpen)return
        selected=s
        if(bytes.size>8*1024*1024){failure="截图超过 8 MB，请选择小一点的范围。";return}
        val key=s.scopedId
        if(c.attachments[key].orEmpty().size>=10){failure="每次最多添加 10 个附件，请先移除一个附件。";return}
        val name="截图-"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".png"
        c.attachments[key]=c.attachments[key].orEmpty()+PendingAttachment(name=name,mimeType="image/png",dataUrl="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes))
    }
    fun send(){session?.let {c.sendToSession(it,queue=c.runs.containsKey(it.scopedId))}}
    fun report(message:String){failure=message}
    fun retry(){failure=null;if(c.connected&&!preparing)prepareDaily()else showMain?.invoke()}
    fun openInMain(){session?.let {c.openSession(it)};close();showMain?.invoke()}
    fun close(){cancelHoldToTalk();generation++;preparing=false;panelOpen=false;voicePanel=false;if(c.voice.fromCompanion)c.voice.dismiss()}
    fun reset(){close();selected=null;pendingFiles.clear();failure=null}
}
