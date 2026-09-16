package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.data.*

@Composable internal fun ProviderSettings(c:DesktopController) {
    val profile=c.profile
    var credentials by remember(profile){mutableStateOf<List<ProviderCredential>>(emptyList())}
    var catalog by remember(profile){mutableStateOf<CustomProviderCatalog?>(null)}
    var keyLoading by remember(profile){mutableStateOf(false)}
    var endpointLoading by remember(profile){mutableStateOf(false)}
    var keyError by remember(profile){mutableStateOf<String?>(null)}
    var endpointError by remember(profile){mutableStateOf<String?>(null)}
    var query by remember {mutableStateOf("")}
    var keyEditor by remember(profile){mutableStateOf<ProviderCredential?>(null)}
    var endpointEditor by remember(profile){mutableStateOf<CustomProviderConfiguration?>(null)}
    fun refresh() {
        keyLoading=true;endpointLoading=true;keyError=null;endpointError=null
        c.request(profile,{it.providerCredentials()},finished={keyLoading=false},failed={keyError=it}){credentials=it}
        c.request(profile,{it.customProviders()},finished={endpointLoading=false},failed={endpointError=it}){catalog=it}
    }
    fun saved() {refresh();c.loadModelCatalog(force=true);c.request(profile,{it.serverSettings()},failed={}){if(c.profile==profile)c.settings=it}}
    LaunchedEffect(profile){refresh()}
    SettingCard("模型供应商","密钥保存在当前工作空间的网关。会话中的模型仍可在输入框切换。",trailing={Hint("重新读取配置"){DeskIconButton(onClick={refresh()},enabled=!keyLoading&&!endpointLoading){Glyph("refresh",Modifier.size(18.dp))}}}) {
        CompactInput(query,{query=it},"搜索供应商")
    }
    SettingCard("自定义供应商","连接你自己的 API 服务，或兼容 OpenAI 的模型端点。",trailing={SmallButton("添加供应商",{endpointEditor=CustomProviderConfiguration()},true,enabled=catalog!=null&&!endpointLoading)}) {
        if(endpointLoading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        endpointError?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp);SmallButton("重新读取",{refresh()})}
        val endpoints=catalog?.items.orEmpty().filter {query.isBlank()||it.name.contains(query,true)||it.baseUrl.contains(query,true)}
        if(endpoints.isEmpty()&&!endpointLoading&&endpointError==null)PanelEmpty("model","还没有自定义供应商","添加 API 地址与模型，便可在输入框选择。")
        endpoints.forEach {provider->
            Row(Modifier.fillMaxWidth().semantics {testTag="custom-provider-${provider.id}"}.desktopClick {endpointEditor=provider}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.07f),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){Glyph("model",Modifier.size(21.dp),MaterialTheme.colorScheme.primary)}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(provider.name,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    SubtleText(provider.baseUrl,maxLines=1)
                    SubtleText("${provider.models.size} 个模型 · ${if(provider.hasKey)"已配置凭据"else"未设置密钥"}")
                }
                if(provider.isCurrent)StatusPill("默认")
                Glyph("edit",Modifier.size(17.dp),MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    SettingCard("常用供应商","添加或更新 API Key。已保存的密钥不会回显。") {
        if(keyLoading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        keyError?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp);SmallButton("重新读取",{refresh()})}
        val rows=credentials.filter {query.isBlank()||it.name.contains(query,true)||it.key.contains(query,true)}
        if(rows.isEmpty()&&!keyLoading&&keyError==null)Caption("没有匹配的供应商。也可以在上方添加自定义 API。")
        rows.forEach {provider->
            Row(Modifier.fillMaxWidth().desktopClick {keyEditor=provider}.padding(horizontal=12.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Glyph("model",Modifier.size(21.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(provider.name,fontSize=14.sp,fontWeight=FontWeight.Medium)
                    SubtleText(provider.key,maxLines=1)
                }
                StatusPill(if(provider.configured)"已配置"else"待配置",muted=!provider.configured)
                Glyph("chevron-right",Modifier.size(16.dp),MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.4f))
        }
    }
    keyEditor?.let {provider->ProviderKeyEditor(c,provider,{keyEditor=null}){saved()}}
    endpointEditor?.let {provider->CustomProviderEditor(c,provider,catalog?.nativeApi==true,{endpointEditor=null}){saved()}}
}

@Composable private fun ProviderKeyEditor(c:DesktopController,provider:ProviderCredential,dismiss:()->Unit,saved:()->Unit) {
    var key by remember {mutableStateOf("")};var saving by remember {mutableStateOf(false)};var failure by remember {mutableStateOf<String?>(null)}
    val profile=remember {c.profile}
    HermesDialog(onDismissRequest={if(!saving)dismiss()},title={Text(provider.name)},text={Column(Modifier.width(450.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(tr(if(provider.configured)"输入新密钥以替换；关闭窗口会保留原有密钥。"else"填入供应商提供的 API Key，保存后刷新可选模型。"),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(key,{key=it},modifier=Modifier.fillMaxWidth().semantics {testTag="provider-key-input"},label={Text("API Key")},singleLine=true,visualTransformation=PasswordVisualTransformation(),enabled=!saving)
        failure?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
    }},confirmButton={SmallButton(if(saving)"保存中…"else"保存密钥",{saving=true;failure=null
        val submittedKey=key
        c.request(profile,{it.saveProviderCredential(provider.key,submittedKey)},finished={saving=false},failed={failure=it}){key="";c.notice="密钥已保存";saved();dismiss()}
    },true,enabled=!saving&&key.isNotBlank())},dismissButton={DeskTextButton(onClick=dismiss,enabled=!saving){Text(tr("取消"))}})
}

@Composable private fun CustomProviderEditor(c:DesktopController,initial:CustomProviderConfiguration,native:Boolean,dismiss:()->Unit,saved:()->Unit) {
    var value by remember {mutableStateOf(initial)};var key by remember {mutableStateOf("")};var models by remember {mutableStateOf(initial.models.joinToString("\n"))}
    var saving by remember {mutableStateOf(false)};var probing by remember {mutableStateOf(false)};var makeDefault by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf<String?>(null)};var result by remember {mutableStateOf<String?>(null)}
    val profile=remember {c.profile};val busy=saving||probing
    val proposed=value.copy(models=models.lines().map(String::trim).filter(String::isNotBlank).distinct())
    val validation=validateCustomProvider(proposed)
    HermesDialog(onDismissRequest={if(!busy)dismiss()},title={Text(tr(if(initial.id.isBlank())"添加供应商"else"编辑供应商"))},text={Column(Modifier.width(520.dp).heightIn(max=590.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FormInput("名称",value.name,{value=value.copy(name=it)})
        if(initial.id.isBlank())FormInput("标识，例如 my-provider",value.id,{value=value.copy(id=it.trim().lowercase())})
        FormInput("API 地址，包含服务要求的 /v1 等路径",value.baseUrl,{value=value.copy(baseUrl=it);result=null})
        OutlinedTextField(key,{key=it;result=null},label={Text(if(initial.hasKey)"API Key · 留空保留原密钥"else"API Key · 本地无密钥服务可留空")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        FormInput("默认模型标识",value.model,{value=value.copy(model=it)})
        Disclosure("更多模型",if(models.isBlank())"可选，每行填写一个模型标识"else"${proposed.models.size} 个模型") {Field("模型列表",models,{models=it},4)}
        Toggle("自动发现服务提供的模型",value.discoverModels){value=value.copy(discoverModels=it)}
        Toggle("设为新对话的默认供应商",makeDefault){makeDefault=it}
        if(native) {
            SmallButton(if(probing)"测试中…"else"测试连接并读取模型",{probing=true;failure=null;result=null
                val submitted=proposed;val submittedKey=key
                c.request(profile,{it.probeCustomProvider(submitted,submittedKey)},finished={probing=false},failed={failure="连接测试未完成：$it"}){probe->
                    result=if(probe.ok)"连接成功，服务返回 ${probe.models.size} 个模型。"else if(probe.reachable)"服务拒绝了请求，请检查地址和密钥。"else"暂时无法连接到该服务。"
                    if(probe.ok&&probe.models.isNotEmpty()&&value.baseUrl==submitted.baseUrl&&key==submittedKey)models=(models.lines().filter(String::isNotBlank)+probe.models).distinct().joinToString("\n")
                }
            },enabled=!busy&&validation==null&&(!initial.hasKey||key.isNotBlank()))
            if(initial.hasKey&&key.isBlank())SubtleText("已有密钥保持隐藏；测试连接需临时填入密钥，直接保存则无需重填。",maxLines=2)
        }
        result?.let {Caption(it)}
        failure?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        if(validation!=null&&value.name.isNotBlank())SubtleText(validation,maxLines=2)
    }},confirmButton={SmallButton(if(saving)"保存中…"else"保存供应商",{saving=true;failure=null
        val submitted=proposed;val submittedKey=key;val submittedDefault=makeDefault
        c.request(profile,{it.saveCustomProvider(submitted,submittedKey,submittedDefault,native)},finished={saving=false},failed={failure=it}){key="";c.notice="供应商已保存，正在刷新模型";saved();dismiss()}
    },true,enabled=!busy&&validation==null)},dismissButton={DeskTextButton(onClick=dismiss,enabled=!busy){Text(tr("取消"))}})
}
