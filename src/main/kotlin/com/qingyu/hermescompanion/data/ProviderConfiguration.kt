package com.qingyu.hermescompanion.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Safe configuration metadata: existing credential values are deliberately not retained. */
data class ProviderCredential(val key:String,val provider:String,val name:String,val configured:Boolean)
data class CustomProviderConfiguration(
    val id:String="",val name:String="",val baseUrl:String="",val model:String="",val models:List<String> = emptyList(),
    val hasKey:Boolean=false,val isCurrent:Boolean=false,val discoverModels:Boolean=true,val contextLength:Int=0,
)
data class CustomProviderCatalog(val items:List<CustomProviderConfiguration>,val nativeApi:Boolean)
data class ProviderProbe(val ok:Boolean,val reachable:Boolean,val models:List<String>)

internal fun providerModelNames(value:Any?):List<String> = when(value) {
    is JSONObject->value.keys().asSequence().toList()
    is JSONArray->(0 until value.length()).mapNotNull {i->when(val item=value.opt(i)){is String->item;is JSONObject->item.optString("id").ifBlank {item.optString("model")};else->null}}
    else->emptyList()
}.map(String::trim).filter(String::isNotBlank).distinct()

internal fun parseProviderCredentials(root:JSONObject):List<ProviderCredential> {
    val known=mapOf("OPENAI_API_KEY" to "OpenAI","ANTHROPIC_API_KEY" to "Anthropic","DEEPSEEK_API_KEY" to "DeepSeek","OPENROUTER_API_KEY" to "OpenRouter","GEMINI_API_KEY" to "Gemini","KIMI_API_KEY" to "Kimi","MOONSHOT_API_KEY" to "Kimi","MINIMAX_API_KEY" to "MiniMax","ZAI_API_KEY" to "Z.ai")
    val rows=root.optJSONObject("env")?:root
    return rows.keys().asSequence().mapNotNull {key->
        val row=rows.optJSONObject(key)?:return@mapNotNull null
        val provider=row.optString("provider")
        if(key !in known&&(row.optString("category")!="provider"||!row.optBoolean("is_password")))return@mapNotNull null
        if(row.optBoolean("channel_managed"))return@mapNotNull null
        ProviderCredential(key,provider.ifBlank {key.removeSuffix("_API_KEY").lowercase()},row.optString("provider_label").ifBlank {known[key]?:provider.ifBlank {key}},row.optBoolean("is_set"))
    }.sortedWith(compareByDescending<ProviderCredential>{it.configured}.thenBy {it.name.lowercase()}).toList()
}
internal fun parseCustomProviders(root:JSONObject,native:Boolean):CustomProviderCatalog {
    val config=root.optJSONObject("config")?:root
    val providers=config.optJSONObject("providers")?:JSONObject()
    val current=(root.optJSONObject("current")?:config.optJSONObject("model"))?.optString("provider").orEmpty()
    val rows=if(native) {
        val endpoints=root.optJSONArray("endpoints")?:throw ApiException(502,"服务器没有返回供应商配置列表。")
        (0 until endpoints.length()).mapNotNull {endpoints.optJSONObject(it)}
    }else providers.keys().asSequence().mapNotNull {id->providers.optJSONObject(id)?.let {JSONObject(it.toString()).put("id",id)}}.toList()
    return CustomProviderCatalog(rows.mapNotNull {row->
        val id=row.optString("id");val url=row.optString("base_url").ifBlank {row.optString("api").ifBlank {row.optString("url")}}
        if(id.isBlank()||url.isBlank())return@mapNotNull null
        val model=row.optString("model").ifBlank {row.optString("default_model")}
        CustomProviderConfiguration(id,row.optString("name").ifBlank {id},url,model, (listOf(model)+providerModelNames(row.opt("models"))).filter(String::isNotBlank).distinct(),
            row.optBoolean("has_api_key")||row.optString("key_env").isNotBlank()||row.optString("api_key").isNotBlank(),row.optBoolean("is_current")||id==current,row.optBoolean("discover_models",true),row.optInt("context_length"))
    },native)
}
internal fun validateCustomProvider(value:CustomProviderConfiguration):String? = when {
    !Regex("[a-z0-9][a-z0-9_-]{0,63}").matches(value.id)->"标识请使用小写字母、数字、短横线或下划线。"
    value.name.isBlank()->"请填写供应商名称。"
    runCatching {val u=URI(value.baseUrl);u.scheme !in listOf("https","http")||u.host.isNullOrBlank()||u.userInfo!=null||u.query!=null||u.fragment!=null}.getOrDefault(true)->"请输入完整的 HTTP / HTTPS API 地址，不含账号、查询参数或片段。"
    value.model.isBlank()->"请填写默认模型标识。"
    value.models.size>200->"最多填写 200 个模型。"
    else->null
}
internal fun customProviderPayload(value:CustomProviderConfiguration,key:String,makeDefault:Boolean):JSONObject {
    require(validateCustomProvider(value)==null){validateCustomProvider(value).orEmpty()}
    require(!key.contains('\n')&&!key.contains('\r')){"API Key 不能包含换行。"}
    return JSONObject().put("id",value.id).put("name",value.name.trim()).put("base_url",value.baseUrl.trim().trimEnd('/'))
        .put("model",value.model.trim()).put("models",JSONArray(value.models)).put("discover_models",value.discoverModels)
        .put("context_length",value.contextLength.takeIf {it>0}).put("make_default",makeDefault)
        // The server interprets an empty string as DELETE. Omit a blank field to preserve the key.
        .apply {if(key.isNotBlank())put("api_key",key.trim())}
}
