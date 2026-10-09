package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.hermescompanion.data.*
import com.qingyu.hermescompanion.model.VoicePhase
import kotlinx.coroutines.*

class DesktopVoiceModels(private val c:DesktopController){
    val store=VoiceModelStore(c.store.directory.resolve("voice-models").toFile())
    val engine=LocalSpeechEngine(store)
    val states=mutableStateMapOf<String,VoiceModelState>().apply {putAll(store.states())}
    val activateWhenReady=mutableStateMapOf<String,Boolean>()
    private val jobs=mutableMapOf<String,Job>()
    private val generations=mutableMapOf<String,Int>()
    val voiceWorking get()=c.voice.phase in setOf(VoicePhase.LISTENING,VoicePhase.TRANSCRIBING,VoicePhase.THINKING,VoicePhase.SPEAKING)
    fun selected(spec:VoiceModelSpec)=if(spec.id==LocalVoiceModels.recognition.id)c.voicePreferences.sttEngine=="local"else c.voicePreferences.ttsEngine=="local"
    private fun setEngine(spec:VoiceModelSpec,value:String){
        val pref=c.voicePreferences
        c.saveVoicePreferences(if(spec.id==LocalVoiceModels.recognition.id)pref.copy(sttEngine=value)else pref.copy(ttsEngine=value))
    }
    fun selectEngine(spec:VoiceModelSpec,value:String){
        if(voiceWorking){c.notice="请先结束当前语音，再切换引擎";return}
        activateWhenReady[spec.id]=false
        if(value=="local"&&!store.installed(spec)){download(spec);return}
        setEngine(spec,value)
    }
    fun download(spec:VoiceModelSpec){
        if(jobs[spec.id]?.isActive==true){activateWhenReady[spec.id]=true;return}
        run(spec,"download",true){progress->store.download(spec,progress)}
    }
    fun importModel(spec:VoiceModelSpec){
        if(jobs[spec.id]?.isActive==true)return
        c.scope.launch {
            val file=withContext(Dispatchers.IO){DesktopFiles.chooseVoiceModel(spec.title)}?:return@launch
            if(jobs[spec.id]?.isActive!=true)run(spec,"import",true){progress->store.importArchive(spec,file,progress)}
        }
    }
    private fun run(spec:VoiceModelSpec,phase:String,activate:Boolean,work:suspend ((VoiceModelState)->Unit)->Unit){
        val token=(generations[spec.id]?:0)+1;generations[spec.id]=token
        activateWhenReady[spec.id]=activate
        states[spec.id]=(states[spec.id]?:VoiceModelState()).copy(phase=phase,message="")
        jobs[spec.id]=c.scope.launch {
            try{
                work {state->c.scope.launch {if(generations[spec.id]==token)states[spec.id]=state}}
                generations[spec.id]=token+1
                val installed=store.installed(spec)
                states[spec.id]=VoiceModelState(installed=installed,message=if(installed)"模型已就绪"else"模型已移除")
                if(installed&&activateWhenReady.remove(spec.id)==true){
                    if(voiceWorking)states[spec.id]=VoiceModelState(true,message="模型已就绪，结束当前语音后可启用")
                    else setEngine(spec,"local")
                }
            }catch(e:CancellationException){
                generations[spec.id]=token+1
                states[spec.id]=store.states().getValue(spec.id).copy(message="已暂停，稍后可以继续下载")
                throw e
            }catch(e:Exception){
                generations[spec.id]=token+1
                states[spec.id]=store.states().getValue(spec.id).copy(message=e.message?:"安装未完成，请重试",phase="error")
            }finally{jobs.remove(spec.id)}
        }
    }
    fun pause(spec:VoiceModelSpec){jobs[spec.id]?.cancel()}
    fun remove(spec:VoiceModelSpec){
        if(voiceWorking){c.notice="请先停止语音，再移除模型";return}
        if(jobs[spec.id]?.isActive==true)return
        // Keep the user's local routing preference; never silently upload to a server.
        run(spec,"remove",false){engine.remove(spec)}
    }
}

@Composable internal fun LocalVoiceModelsSettings(c:DesktopController){
    val models=c.voice.models
    LocalVoiceModels.all.forEach {spec->
        val recognition=spec.id==LocalVoiceModels.recognition.id
        val state=models.states[spec.id]?:VoiceModelState()
        val selected=models.selected(spec)
        SettingCard(if(recognition)"本地语音识别 · STT"else"本地语音朗读 · TTS",
            if(recognition)"把你说的话转成文字，在这台电脑上完成。"else"用电脑本地模型朗读回复，可离线试听。"){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text(if(recognition)"SenseVoice"else"Kokoro",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    SubtleText(spec.source+" · 约 "+(spec.bytes/1024/1024)+" MB",maxLines=2)
                }
                StatusPill(when{state.busy->"准备中";state.installed&&selected->"正在使用";state.installed->"已安装";else->"未安装"},muted=!selected)
            }
            if(state.busy){
                if(state.phase in setOf("download","import"))LinearProgressIndicator(progress={(state.downloaded.toFloat()/spec.bytes).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                SubtleText(when(state.phase){
                    "verify"->"正在校验模型完整性…";"install"->"正在安装…";"remove"->"正在移除…"
                    else->(if(state.phase=="import")"已导入 "else"已下载 ")+(state.downloaded/1024/1024)+" / "+(spec.bytes/1024/1024)+" MB"
                })
                if(models.activateWhenReady[spec.id]==true)SubtleText("安装完成后自动启用本地"+if(recognition)"识别"else"朗读")
            }
            if(state.message.isNotBlank())Text(state.message,fontSize=12.sp,color=if(state.phase=="error")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                when{
                    state.busy->SmallButton("暂停",{models.pause(spec)},enabled=state.phase!="remove")
                    !state.installed->SmallButton(if(state.downloaded>0)"继续下载并启用"else"下载并启用",{models.download(spec)},true)
                    !selected->SmallButton(if(recognition)"使用本地识别"else"使用本地朗读",{models.selectEngine(spec,"local")},true,enabled=!models.voiceWorking)
                }
                if(state.installed){
                    SmallButton(if(recognition&&c.recording)"完成试录"else if(recognition)"试录一句"else"试听声音",{
                        if(recognition&&c.recording)c.voice.stopCapture()
                        else {models.selectEngine(spec,"local");if(recognition)c.voice.startCapture(false,true)else c.speak("你好，我是 Hermes。现在听到的声音，来自这台电脑的本地模型。")}
                    },enabled=!state.busy&&(!models.voiceWorking||(recognition&&c.recording)))
                    if(!recognition&&selected&&models.voiceWorking)SmallButton("停止试听",{c.voice.pause()})
                }
                if(!state.installed&&!state.busy)DeskTextButton(onClick={models.importModel(spec)}){Text("导入模型包",fontSize=12.sp)}
                if((state.installed||state.downloaded>0)&&!state.busy)DeskTextButton(onClick={models.remove(spec)},enabled=!models.voiceWorking){Text("移除",fontSize=12.sp)}
                if(!state.installed)DeskTextButton(onClick={runCatching {DesktopFiles.openLink(spec.url)}.onFailure {c.error=it.message}}){Text("手动下载",fontSize=12.sp)}
            }
            if(selected&&c.voice.message.isNotBlank())SubtleText(c.voice.message,maxLines=3)
        }
    }
}
