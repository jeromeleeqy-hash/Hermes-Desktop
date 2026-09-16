package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import com.qingyu.hermescompanion.model.*
import org.json.JSONObject
import org.json.JSONArray

/** Retained by the account-scoped controller, not by an individual settings tab. */
class SettingDraft<T>(server:T,private val write:(String)->Unit,private val encode:(T)->JSONObject,decode:(JSONObject)->T,stored:String="") {
    var value by mutableStateOf(server)
    var baseline by mutableStateOf(server)
        private set
    var latest by mutableStateOf(server)
        private set
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var conflict by mutableStateOf(false)
        private set
    val rawInputs=mutableStateMapOf<String,String>()
    val fieldErrors=mutableStateMapOf<String,String>()
    val dirty get()=value!=baseline||fieldErrors.isNotEmpty()
    operator fun getValue(thisRef:Any?,property:kotlin.reflect.KProperty<*>)=value
    operator fun setValue(thisRef:Any?,property:kotlin.reflect.KProperty<*>,next:T){value=next}
    fun useLatestBaseline(){baseline=latest;conflict=false;persist()}
    init {
        if(stored.isNotBlank())runCatching {val o=JSONObject(stored);value=decode(o.getJSONObject("value"));baseline=decode(o.getJSONObject("base"));o.optJSONObject("inputs")?.let {rawInputs.putAll(SettingCodec.text(it))};o.optJSONObject("errors")?.let {fieldErrors.putAll(SettingCodec.text(it))};conflict=baseline!=server&&dirty}
            .onFailure {error="上次的设置草稿无法读取，当前显示服务器设置。";value=server;baseline=server}
    }
    fun rebase(server:T) {
        latest=server
        if(!dirty){value=server;baseline=server;conflict=false;rawInputs.clear();fieldErrors.clear()}
        else conflict=server!=baseline
    }
    fun commit(submitted:T,saved:T,submittedInputs:Map<String,String> = emptyMap()) {
        if(value==submitted&&rawInputs==submittedInputs){value=saved;rawInputs.clear();fieldErrors.clear()}
        baseline=saved;latest=saved;conflict=false;error=null;persist()
    }
    fun reset(){value=latest;baseline=latest;conflict=false;error=null;rawInputs.clear();fieldErrors.clear();persist()}
    fun persist(){write(if(dirty)JSONObject().put("value",encode(value)).put("base",encode(baseline)).put("inputs",JSONObject(rawInputs.toMap())).put("errors",JSONObject(fieldErrors.toMap())).toString()else "")}
}

@Composable fun <T> settingDraft(c:DesktopController,id:String,initial:T,encode:(T)->JSONObject,decode:(JSONObject)->T):SettingDraft<T> {
    val key=c.storageKey("settingsDraft:${c.profile}:$id")
    @Suppress("UNCHECKED_CAST")
    val draft=remember(key) { c.settingsDrafts.getOrPut(key) {SettingDraft(initial,{if(!c.demo)c.store.put(key,it)},encode,decode,if(c.demo)""else c.store.get(key))} as SettingDraft<T> }
    LaunchedEffect(initial){draft.rebase(initial)}
    LaunchedEffect(draft.value,draft.rawInputs.toMap(),draft.fieldErrors.toMap()){kotlinx.coroutines.delay(350);runCatching {draft.persist()}.onFailure {draft.error="草稿保存失败：${it.message}"}}
    DisposableEffect(key){onDispose {runCatching {draft.persist()}.onFailure {c.error="草稿保存失败：${it.message}"}}}
    return draft
}

object SettingCodec {
    fun model(v:ServerModelSettings)=JSONObject().put("provider",v.provider).put("model",v.model).put("effort",v.reasoningEffort).put("context",v.contextLength)
        .put("aux",JSONObject().apply {v.auxiliary.forEach {(k,m)->put(k,JSONObject().put("provider",m.provider).put("model",m.model))}})
        .put("fallback",JSONArray().apply {v.fallbackModels.forEach {put(JSONObject().put("provider",it.provider).put("model",it.model))}})
        .put("refs",JSONArray(v.moaReferenceModels)).put("aggregator",v.moaAggregatorModel)
    fun model(o:JSONObject)=ServerModelSettings(o.optString("provider"),o.optString("model"),o.optString("effort"),o.optInt("context"),
        o.optJSONObject("aux")?.let {v->v.keys().asSequence().associateWith {k->v.getJSONObject(k).let {ModelChoice(it.optString("provider"),it.optString("model"))}}}.orEmpty(),
        o.optJSONArray("fallback").objects().map {FallbackModel(it.optString("provider"),it.optString("model"))},o.optJSONArray("refs").strings(),o.optString("aggregator"))
    fun conversation(v:ConversationStyleSettings)=JSONObject().put("personality",v.personality).put("timezone",v.timezone).put("reasoning",v.showReasoning)
    fun conversation(o:JSONObject)=ConversationStyleSettings(o.optString("personality"),o.optString("timezone"),o.optBoolean("reasoning",true))
    fun approval(v:ApprovalSettings)=JSONObject().put("mode",v.mode).put("timeout",v.timeoutSeconds)
    fun approval(o:JSONObject)=ApprovalSettings(o.optString("mode","smart"),o.optInt("timeout",60))
    fun memory(v:MemoryContextSettings)=JSONObject().put("enabled",v.memoryEnabled).put("profile",v.userProfileEnabled).put("limit",v.memoryCharLimit).put("userLimit",v.userCharLimit)
        .put("compress",v.compressionEnabled).put("threshold",v.compressionThreshold).put("target",v.compressionTargetRatio).put("protect",v.protectLastMessages)
    fun memory(o:JSONObject)=MemoryContextSettings(o.optBoolean("enabled",true),o.optBoolean("profile",true),o.optInt("limit",2200),o.optInt("userLimit",1375),o.optBoolean("compress",true),o.optDouble("threshold",.5),o.optDouble("target",.2),o.optInt("protect",20))
    fun voice(v:ServerVoiceSettings)=JSONObject().put("enabled",v.stt.enabled).put("sttProvider",v.stt.provider).put("sttModel",v.stt.model).put("language",v.stt.language)
        .put("ttsProvider",v.tts.provider).put("ttsModel",v.tts.model).put("voice",v.tts.voice)
    fun voice(o:JSONObject)=ServerVoiceSettings(ServerSttSettings(o.optBoolean("enabled",true),o.optString("sttProvider"),o.optString("sttModel"),o.optString("language")),ServerTtsSettings(o.optString("ttsProvider"),o.optString("ttsModel"),o.optString("voice")))
    fun text(v:Map<String,String>)=JSONObject(v)
    fun text(o:JSONObject)=o.keys().asSequence().associateWith {o.getString(it)}
}
