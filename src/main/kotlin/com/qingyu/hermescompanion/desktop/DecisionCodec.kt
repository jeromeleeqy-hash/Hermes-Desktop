package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import org.json.JSONArray
import org.json.JSONObject

object DecisionCodec {
    fun encode(values:List<PendingDecision>):String = JSONArray().apply {
        values.forEach { d ->
            val r=d.request
            val request=JSONObject().put("id",r.requestId).put("runtime",r.runtimeSessionId)
                .put("conversation",r.conversationId).put("type",r.type.name).put("title",r.title).put("detail",r.detail)
                .put("multiple",r.allowMultiple).put("session",r.allowSession).put("permanent",r.allowPermanent)
                .put("serverRpcId",r.serverRpcId).put("method",r.method)
                .put("questions",JSONArray().apply {r.questions.forEach {q->put(JSONObject().put("id",q.id).put("title",q.title).put("multiple",q.allowMultiple).put("lockedAnswer",q.lockedAnswer)
                    .put("choices",JSONArray().apply {q.choices.forEach {c->put(JSONObject().put("label",c.label).put("value",c.value).put("description",c.description))}}))}})
                .put("choices",JSONArray().apply { r.choices.forEach { c -> put(JSONObject().put("label",c.label).put("value",c.value).put("description",c.description)) } })
            put(JSONObject().put("profile",d.profile).put("session",d.session.id).put("title",d.session.title).put("cwd",d.session.workspacePath).put("request",request))
        }
    }.toString()
    fun decode(text:String):List<PendingDecision> {
        val array=JSONArray(text)
        return (0 until array.length()).map { index ->
            val obj=array.getJSONObject(index);val r=obj.getJSONObject("request");val p=obj.getString("profile")
            val s=HermesSession(obj.getString("session"),obj.getString("title"),workspacePath=obj.optString("cwd"),profile=p)
            val choices=r.getJSONArray("choices")
            val request=AgentRequest(
                requestId=r.getString("id"),runtimeSessionId=r.getString("runtime"),conversationId=r.optString("conversation"),
                type=AgentRequestType.valueOf(r.getString("type")),method=r.optString("method"),title=r.getString("title"),detail=r.optString("detail"),
                choices=(0 until choices.length()).map { choices.getJSONObject(it).let { c -> AgentRequestChoice(c.getString("label"),c.getString("value"),c.optString("description")) } },
                allowMultiple=r.optBoolean("multiple"),allowSession=r.optBoolean("session"),allowPermanent=r.optBoolean("permanent"),
                serverRpcId=r.optString("serverRpcId").takeIf {it.isNotBlank()&&it!="null"},
                questions=r.optJSONArray("questions")?.let {qs->(0 until qs.length()).map {i->qs.getJSONObject(i).let {q->
                    val options=q.getJSONArray("choices")
                    AgentQuestion(q.getString("id"),q.getString("title"),(0 until options.length()).map {j->options.getJSONObject(j).let {o->AgentRequestChoice(o.getString("label"),o.getString("value"),o.optString("description"))}},q.optBoolean("multiple"),
                        if(q.has("lockedAnswer")&&!q.isNull("lockedAnswer"))q.getString("lockedAnswer")else null)
                }}}?:emptyList()
            )
            PendingDecision(p,s,request)
        }
    }
}
