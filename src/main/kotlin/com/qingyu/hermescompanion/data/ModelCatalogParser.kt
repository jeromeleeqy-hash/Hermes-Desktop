package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.ModelCatalog
import com.qingyu.hermescompanion.model.ModelProvider
import org.json.JSONArray
import org.json.JSONObject

internal fun parseModelCatalog(root:JSONObject):ModelCatalog {
    fun text(o:JSONObject,vararg keys:String):String?=keys.firstNotNullOfOrNull {key->
        (o.opt(key) as? String)?.trim()?.takeIf {it.isNotEmpty()}
    }
    val body=root.optJSONObject("data")?.takeIf {it.has("providers")}?:root
    if((root.has("ok")&&!root.optBoolean("ok"))||(root.has("success")&&!root.optBoolean("success"))||
        (root.has("error")&&!root.isNull("error"))) {
        throw ApiException(503,text(root,"message","detail","error")?:"服务器未能提供模型目录。")
    }
    val providers=body.optJSONArray("providers")?:throw ApiException(502,"服务器返回的模型目录格式不完整，请重新读取。")
    val current=body.optJSONObject("current")?:body.optJSONObject("main")?:JSONObject()
    return ModelCatalog(
        currentModel=(text(body,"model","current_model","default_model")?:text(current,"model","default","current_model")).orEmpty(),
        currentProvider=(text(body,"provider","current_provider","default_provider")?:text(current,"provider","current_provider")).orEmpty(),
        providers=buildList {
            for(i in 0 until providers.length()) {
                val provider=providers.optJSONObject(i)?:continue
                if(provider.has("authenticated")&&!provider.optBoolean("authenticated"))continue
                val slug=text(provider,"slug","id")?:continue
                val models=provider.optJSONArray("models")?:JSONArray()
                val ids=buildList {
                    for(j in 0 until models.length()) {
                        val value=models.opt(j)
                        val id=when(value){is String->value.trim();is JSONObject->text(value,"id","model");else->null}
                        if(!id.isNullOrBlank())add(id)
                    }
                }.distinct()
                val options=buildMap<String,List<String>> {
                    val metadata=provider.optJSONObject("model_metadata")
                    val capabilities=provider.optJSONObject("capabilities")
                    for(j in 0 until models.length()) {
                        val value=models.opt(j)
                        val id=when(value){is String->value.trim();is JSONObject->text(value,"id","model");else->null}?:continue
                        val modelInfo=value as? JSONObject
                        val capabilityInfo=capabilities?.optJSONObject(id)
                        val metadataInfo=metadata?.optJSONObject(id)
                        // Current Hermes Agent responses may include both model_metadata and a
                        // separate capabilities map. Prefer an explicit capability result, but
                        // fall back when a metadata entry carries the older array contract.
                        sequenceOf(modelInfo,capabilityInfo,metadataInfo)
                            .filterNotNull()
                            .map {advertisedReasoningOptions(it)}
                            .firstOrNull {it!=null}
                            ?.let {put(id,it!!)}
                    }
                }
                if(ids.isNotEmpty())add(ModelProvider(slug,text(provider,"name")?:slug,ids,options))
            }
        },
    )
}
