package com.qingyu.hermescompanion.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Null means unknown, empty means explicitly unsupported. Never guess from a model name. */
internal fun advertisedReasoningOptions(info:JSONObject):List<String>? {
    val caps=info.optJSONObject("capabilities")?:info
    if((caps.has("supports_reasoning")&&!caps.optBoolean("supports_reasoning"))||
        (caps.opt("reasoning") is Boolean&&!caps.optBoolean("reasoning")))return emptyList()
    val options=caps.optJSONArray("reasoning_efforts")?:info.optJSONArray("reasoning_efforts")?:return null
    val values=(0 until options.length()).mapNotNull {i->
        (options.opt(i) as? String)?.lowercase()?.trim()?.takeIf {it in setOf("none","minimal","low","medium","high","xhigh","max","ultra","default")}
    }.distinct()
    return if(caps.has("thinking_off")&&!caps.optBoolean("thinking_off"))values.filterNot {it=="none"}else values
}

/** Only server-advertised web destinations; no file:, scripts, userinfo, or control characters. */
internal fun safeActionUrl(raw:String?):String? = raw?.takeIf {it.length<=4096&&!it.any {c->c.isISOControl()}}?.let {
    runCatching {URI(it)}.getOrNull()?.takeIf {uri->
        uri.userInfo==null&&!uri.host.isNullOrBlank()&&
            (uri.scheme.equals("https",true)||(uri.scheme.equals("http",true)&&uri.host in setOf("localhost","127.0.0.1","[::1]")))
    }?.toASCIIString()
}
