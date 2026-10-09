package com.qingyu.hermescompanion.model

/** Missing provider metadata may be filled only when the model resolves unambiguously. */
fun ModelCatalog.providerFor(session: HermesSession): ModelProvider? {
    providers.firstOrNull {it.slug.equals(session.provider,true) && session.provider.isNotBlank()}?.let {return it}
    val model=session.model.ifBlank {currentModel}
    val matches=providers.filter {model in it.models}
    return matches.singleOrNull() ?: matches.firstOrNull {session.provider.isBlank() && it.slug==currentProvider && model==currentModel}
}
