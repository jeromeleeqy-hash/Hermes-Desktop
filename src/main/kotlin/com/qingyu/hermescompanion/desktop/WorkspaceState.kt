package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class DraftRecord(val text:String="", val attachments:List<PendingAttachment> = emptyList())
data class QueuedMessage(val id:String=java.util.UUID.randomUUID().toString(), val session:HermesSession, val prompt:String, val attachments:List<PendingAttachment> = emptyList())
data class RunRecord(
    val session:HermesSession, val prompt:String, val originalPrompt:String=prompt,
    val baseline:String="", val baselineUser:String="", val assistantId:String=java.util.UUID.randomUUID().toString(),
    val userMessageId:String="", val started:Long=System.currentTimeMillis(), val runtimeId:String?=null,
    val attachments:List<PendingAttachment> = emptyList(), val council:String="off", val voice:Boolean=false,
    val attempted:Boolean=false, val stopping:Boolean=false,
)
data class EditedDocument(val profile:String,val path:String,val name:String,val mime:String,val baseline:String,val text:String,val source:HermesSession?=null)
data class VoiceNote(val id:String=java.util.UUID.randomUUID().toString(),val profile:String,val session:HermesSession?,val blob:String,val transcript:String="",val committed:Boolean=false)
data class WorkspaceState(
    val revision:Long=0, val drafts:Map<String,DraftRecord> = emptyMap(),
    val queues:Map<String,List<QueuedMessage>> = emptyMap(), val runs:List<RunRecord> = emptyList(),
    val unread:Set<String> = emptySet(), val documents:List<EditedDocument> = emptyList(),
    val artifacts:List<RecentArtifact> = emptyList(), val voice:List<VoiceNote> = emptyList(),
    val decisions:List<PendingDecision> = emptyList(), val completions:List<RunCompletionSummary> = emptyList(),
    val selectedSession:HermesSession?=null,
    val indexVersions:Map<String,String> = emptyMap(),
    val summaries:Map<String,String> = emptyMap(),
)

fun accountScope(server:String,user:String):String = MessageDigest.getInstance("SHA-256")
    .digest("${server.trimEnd('/')}\u0000$user".toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }

/** Immutable checkpoints have revisions: a slow old save cannot replace a newer checkpoint. */
class WorkspaceRepository(private val store:SecureConfigStore, private val account:String) {
    private var savedRevision=-1L
    private val attachmentRefs=mutableMapOf<PendingAttachment,JSONObject>()
    @Synchronized fun save(state:WorkspaceState) {
        if(state.revision<=savedRevision) return
        val root=JSONObject().put("schema",2).put("revision",state.revision)
        root.put("drafts",JSONObject().apply { state.drafts.forEach { (key,draft) -> put(key,JSONObject().put("text",draft.text).put("files",files(draft.attachments))) } })
        root.put("queues",JSONObject().apply { state.queues.forEach { (key,queue) -> put(key,JSONArray().apply { queue.forEach { q -> put(JSONObject().put("id",q.id).put("session",sessionJson(q.session)).put("prompt",q.prompt).put("files",files(q.attachments))) } }) } })
        root.put("runs",JSONArray().apply { state.runs.forEach { r -> put(JSONObject().put("session",sessionJson(r.session)).put("prompt",r.prompt).put("original",r.originalPrompt).put("baseline",r.baseline).put("baselineUser",r.baselineUser).put("assistant",r.assistantId).put("userMessage",r.userMessageId).put("started",r.started).put("runtime",r.runtimeId).put("files",files(r.attachments)).put("council",r.council).put("voice",r.voice).put("attempted",r.attempted).put("stopping",r.stopping)) } })
        root.put("unread",JSONArray(state.unread.toList()))
        root.put("documents",JSONArray().apply { state.documents.forEach { d -> put(JSONObject().put("profile",d.profile).put("path",d.path).put("name",d.name).put("mime",d.mime).put("baseline",d.baseline).put("text",d.text).put("source",d.source?.let(::sessionJson))) } })
        root.put("artifacts",JSONArray().apply { state.artifacts.take(300).forEach { a -> put(JSONObject().put("profile",a.profile).put("session",a.sessionId).put("title",a.sessionTitle).put("message",a.messageId).put("path",a.path).put("name",a.name).put("kind",a.kind).put("cwd",a.workspacePath).put("at",a.seenAtMillis).put("sourcePath",a.sourcePath)) } })
        root.put("voice",JSONArray().apply { state.voice.forEach { v -> put(JSONObject().put("id",v.id).put("profile",v.profile).put("session",v.session?.let(::sessionJson)).put("blob",v.blob).put("text",v.transcript).put("committed",v.committed)) } })
        root.put("decisions",JSONArray(DecisionCodec.encode(state.decisions)))
        root.put("completions",JSONArray().apply { state.completions.take(100).forEach { c -> put(JSONObject().put("session",c.sessionId).put("title",c.title).put("summary",c.summary).put("at",c.completedAtMillis).put("artifacts",JSONArray().apply {c.artifacts.forEach {a->put(JSONObject().put("path",a.path).put("name",a.name).put("kind",a.kind))}})) } })
        root.put("selected",state.selectedSession?.let(::sessionJson))
        root.put("indexVersions",JSONObject(state.indexVersions));root.put("summaries",JSONObject(state.summaries))
        store.put("$account:workspace-v2",root.toString())
        savedRevision=state.revision
    }
    @Synchronized fun load():WorkspaceState {
        val raw=store.get("$account:workspace-v2")
        if(raw.isBlank())return WorkspaceState()
        val o=JSONObject(raw);require(o.getInt("schema")==2) { "当前数据由不同版本创建，请保留后再升级。" }
        val drafts=o.getJSONObject("drafts").let { values -> values.keys().asSequence().associateWith { key -> val d=values.getJSONObject(key);DraftRecord(d.optString("text"),readFiles(d.optJSONArray("files"))) } }
        val queues=o.getJSONObject("queues").let { values -> values.keys().asSequence().associateWith { key -> values.getJSONArray(key).objects().map { q -> QueuedMessage(q.getString("id"),readSession(q.getJSONObject("session")),q.getString("prompt"),readFiles(q.optJSONArray("files"))) } } }
        val runs=o.optJSONArray("runs").objects().map { r -> RunRecord(readSession(r.getJSONObject("session")),r.getString("prompt"),r.optString("original",r.getString("prompt")),r.optString("baseline"),r.optString("baselineUser"),r.getString("assistant"),r.optString("userMessage"),r.getLong("started"),r.optString("runtime").takeIf(String::isNotBlank),readFiles(r.optJSONArray("files")),r.optString("council","off"),r.optBoolean("voice"),r.optBoolean("attempted"),r.optBoolean("stopping")) }
        val documents=o.optJSONArray("documents").objects().map { d -> EditedDocument(d.getString("profile"),d.getString("path"),d.getString("name"),d.getString("mime"),d.getString("baseline"),d.getString("text"),d.optJSONObject("source")?.let(::readSession)) }
        val artifacts=o.optJSONArray("artifacts").objects().map { a -> RecentArtifact(a.getString("profile"),a.getString("session"),a.getString("title"),a.optString("message"),a.getString("path"),a.getString("name"),a.getString("kind"),a.optString("cwd"),a.optLong("at"),a.optString("sourcePath")) }
        val voice=o.optJSONArray("voice").objects().map { v -> VoiceNote(v.getString("id"),v.getString("profile"),v.optJSONObject("session")?.let(::readSession),v.getString("blob"),v.optString("text"),v.optBoolean("committed")) }
        val done=o.optJSONArray("completions").objects().map { d -> RunCompletionSummary(d.getString("session"),d.getString("title"),d.getString("summary"),d.optJSONArray("artifacts").objects().map {a->ChatArtifact(a.getString("path"),a.getString("name"),a.optString("kind","document"))},completedAtMillis=d.getLong("at")) }
        val revision=o.getLong("revision");savedRevision=revision
        fun strings(key:String):Map<String,String> = o.optJSONObject(key)?.let {v->v.keys().asSequence().associateWith {v.getString(it)}}.orEmpty()
        return WorkspaceState(revision,drafts,queues,runs,o.optJSONArray("unread").strings().toSet(),documents,artifacts,voice,DecisionCodec.decode(o.optJSONArray("decisions")?.toString()?:"[]"),done,o.optJSONObject("selected")?.let(::readSession),strings("indexVersions"),strings("summaries"))
    }
    private fun files(values:List<PendingAttachment>)=JSONArray().apply { values.forEach { a -> put(attachmentRefs.getOrPut(a) {
        JSONObject().put("id",a.id).put("name",a.name).put("mime",a.mimeType).put("remote",a.remotePath)
            .put("dataBlob",a.dataUrl?.let { store.saveBlob(it.toByteArray(Charsets.UTF_8)) })
            .put("textBlob",a.textContent?.let { store.saveBlob(it.toByteArray(Charsets.UTF_8)) })
            .put("uploadBlob",a.uploadDataUrl?.let { store.saveBlob(it.toByteArray(Charsets.UTF_8)) })
    }) } }
    private fun readFiles(array:JSONArray?):List<PendingAttachment> = array.objects().map { a ->
        fun blob(key:String):String?=a.optString(key).takeIf(String::isNotBlank)?.let { store.readBlob(it).toString(Charsets.UTF_8) }
        PendingAttachment(a.getString("id"),a.getString("name"),a.getString("mime"),blob("dataBlob"),blob("textBlob"),a.optString("remote").takeIf(String::isNotBlank),blob("uploadBlob"))
    }
}
internal fun sessionJson(s:HermesSession)=JSONObject().put("id",s.id).put("title",s.title).put("preview",s.preview).put("profile",s.profile).put("cwd",s.workspacePath).put("model",s.model).put("provider",s.provider).put("runtime",s.runtimeId).put("effort",s.reasoningEffort).put("updated",s.updatedAt).put("source",s.source).put("count",s.messageCount).put("pinned",s.isPinned)
internal fun readSession(o:JSONObject)=HermesSession(o.getString("id"),o.getString("title"),o.optString("preview"),o.optString("updated"),o.optString("source","dashboard"),o.optInt("count"),o.optString("model"),o.optString("provider"),o.optBoolean("pinned"),o.optString("cwd"),o.optString("runtime").takeIf(String::isNotBlank),o.optString("effort").takeIf(String::isNotBlank),o.optString("profile","default"))
internal fun JSONArray?.objects():List<JSONObject> = if(this==null)emptyList() else (0 until length()).map { getJSONObject(it) }
internal fun JSONArray?.strings():List<String> = if(this==null)emptyList() else (0 until length()).map { getString(it) }

/** A reply must follow this submitted user turn; old replies must never release its queue. */
internal fun recoveredReply(messages:List<ChatMessage>,record:RunRecord):ChatMessage? {
    if(messages.lastOrNull()?.role!=MessageRole.ASSISTANT)return null
    val visible=messages.filter { it.role==MessageRole.USER || it.role==MessageRole.ASSISTANT }
    val prompt=record.prompt.trim()
    val userIndex=visible.indexOfLast { it.role==MessageRole.USER && (if(prompt.isNotBlank()) it.content.trim().startsWith(prompt) else record.attachments.any { a -> it.content.contains(a.name) }) }
    if(userIndex<0)return null
    val user=visible[userIndex]
    if(record.baselineUser.isNotBlank() && user.recoverySignature()==record.baselineUser)return null
    // A later user turn belongs to someone else; no guesses about which run it completes.
    if(visible.drop(userIndex+1).any { it.role==MessageRole.USER })return null
    return visible.drop(userIndex+1).lastOrNull { it.role==MessageRole.ASSISTANT }?.takeIf {
        !it.isStreaming && (it.content.isNotBlank() || it.images.isNotEmpty()) && it.recoverySignature()!=record.baseline
    }
}
