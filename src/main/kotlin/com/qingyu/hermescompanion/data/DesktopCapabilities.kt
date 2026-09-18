package com.qingyu.hermescompanion.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Null means unknown, empty means explicitly unsupported. Never guess from a model name. */
internal fun advertisedReasoningOptions(info:JSONObject):List<String>? {
    val caps=info.optJSONObject("capabilities")?:info
    if((caps.has("supports_reasoning")&&!caps.optBoolean("supports_reasoning"))||
        (caps.opt("reasoning") is Boolean&&!caps.optBoolean("reasoning")))return emptyList()
    // Hermes Agent's dashboard contract advertises a boolean `reasoning`
    // capability (and optionally `can_disable_reasoning`), while older
    // gateways expose an explicit `reasoning_efforts`/`supported_efforts`
    // array. Accept both shapes.
    val reasoningObject=caps.optJSONObject("reasoning")
    val options=caps.optJSONArray("reasoning_efforts")
        ?:caps.optJSONArray("supported_efforts")
        ?:reasoningObject?.optJSONArray("supported_efforts")
        ?:reasoningObject?.optJSONArray("efforts")
        ?:info.optJSONArray("reasoning_efforts")
    if(reasoningObject?.has("supported") == true && !reasoningObject.optBoolean("supported"))return emptyList()
    val supportsReasoning=caps.opt("supports_reasoning") is Boolean ||
        caps.opt("reasoning") is Boolean || (reasoningObject!=null && reasoningObject.optBoolean("supported",true))
    val values=(options?.let {array -> (0 until array.length()).mapNotNull {i->
        (array.opt(i) as? String)?.lowercase()?.trim()?.takeIf {it in setOf("none","minimal","low","medium","high","xhigh","max","ultra","default")}
    }} ?: if(supportsReasoning) {
        listOf("none","minimal","low","medium","high","xhigh","max","ultra")
    } else return null).distinct()
    return if((caps.has("thinking_off")&&!caps.optBoolean("thinking_off"))||
        (caps.has("can_disable_reasoning")&&!caps.optBoolean("can_disable_reasoning"))||
        (caps.has("mandatory")&&caps.optBoolean("mandatory"))||
        (reasoningObject?.optBoolean("mandatory",false)==true)) values.filterNot {it=="none"} else values
}

/** Only server-advertised web destinations; no file:, scripts, userinfo, or control characters. */
internal fun safeActionUrl(raw:String?):String? = raw?.takeIf {it.length<=4096&&!it.any {c->c.isISOControl()}}?.let {
    runCatching {URI(it)}.getOrNull()?.takeIf {uri->
        uri.userInfo==null&&!uri.host.isNullOrBlank()&&
            (uri.scheme.equals("https",true)||(uri.scheme.equals("http",true)&&uri.host in setOf("localhost","127.0.0.1","[::1]")))
    }?.toASCIIString()
}
