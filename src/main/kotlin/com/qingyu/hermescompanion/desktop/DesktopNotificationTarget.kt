package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.HermesSession
import org.json.JSONObject

/** Notification identity survives app restarts without embedding commands or URLs. */
data class DesktopNotificationTarget(val account:String,val profile:String,val sessionId:String="",val title:String="") {
    fun session()=sessionId.takeIf {it.isNotBlank()}?.let {HermesSession(it,title.ifBlank {"对话"},profile=profile)}
    fun encode()=JSONObject().put("account",account).put("profile",profile).put("session",sessionId).put("title",title).toString()
    companion object {
        fun decode(raw:String):DesktopNotificationTarget?=runCatching {
            val o=JSONObject(raw)
            DesktopNotificationTarget(o.getString("account"),o.getString("profile"),o.optString("session"),o.optString("title"))
                .takeIf {it.account.isNotBlank()&&it.profile.isNotBlank()}
        }.getOrNull()
    }
}
