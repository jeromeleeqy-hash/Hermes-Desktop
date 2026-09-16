package com.qingyu.hermescompanion.desktop

/** A catalogue failure does not mean that the current conversation model is unavailable. */
data class ModelCatalogProblem(val title:String,val guidance:String,val raw:String,val restartRequired:Boolean=false)

fun modelCatalogProblem(raw:String):ModelCatalogProblem {
    val restart=raw.contains("Restart required",true) &&
        (raw.contains("checkout on disk",true)||raw.contains("stale-module",true)||raw.contains("restart this Hermes process",true))
    return if(restart)ModelCatalogProblem(
        "服务器需要完成重启",
        "服务器已更新，但网关仍在运行旧代码。请重启服务器上的 Hermes 网关，然后重新读取模型。关闭或重开这个桌面客户端不能完成服务器重启。",
        raw,true,
    )else ModelCatalogProblem("模型列表暂时无法读取","当前会话的模型保持不变。你可以重新读取，或到连接诊断查看原因。",raw)
}
