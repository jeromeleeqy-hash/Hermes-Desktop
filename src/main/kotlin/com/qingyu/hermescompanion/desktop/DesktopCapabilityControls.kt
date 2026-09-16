package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*

internal fun reasoningLabel(value:String?):String=when(value) {
    null,"","default"->"默认";"none"->"关闭";"minimal"->"极简";"low"->"快速";"medium"->"标准"
    "high"->"深入";"xhigh"->"更深入";"max"->"最大";"ultra"->"最高";else->value
}

@Composable internal fun ComposerReasoningPicker(c:DesktopController,s:HermesSession) {
    var open by remember(s.scopedId,s.model,s.provider){mutableStateOf(false)}
    LaunchedEffect(s.profile,s.model,s.provider){c.loadModelCatalog()}
    val options=c.modelCatalog.providers.firstOrNull {it.slug==s.provider}?.reasoningOptions?.get(s.model)
    val busy=c.modelSwitching[s.scopedId]==true||c.runs.containsKey(s.scopedId)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Box {
            DeskTextButton(onClick={open=true}){Text("思考：${reasoningLabel(s.reasoningEffort)}",fontSize=12.sp);Glyph("chevron-down",Modifier.size(12.dp))}
            DeskMenu(open,{open=false}) {
                if(options.isNullOrEmpty())DeskMenuItem(text={Text(if(options==null)"服务器尚未提供该模型的档位"else"该模型不支持调节思考强度")},onClick={open=false})
                options.orEmpty().forEach {effort->
                    DeskMenuItem(text={Text(reasoningLabel(effort))},onClick={open=false;if(!busy)c.setReasoning(s,effort)},enabled=!busy)
                }
                DeskMenuItem(text={Text("重新读取模型能力")},onClick={open=false;c.loadModelCatalog(force=true)})
            }
        }
        SubtleText(if(busy)"任务结束后可调整"else"仅影响当前对话",Modifier.weight(1f))
    }
}

@Composable internal fun ConnectionRecoveryBanner(c:DesktopController) {
    if(c.connectionHealth!="online")Surface(color=MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(c.connectionHealth=="auth")"登录已过期，草稿和附件已保留"else"连接暂时中断，正在尝试恢复 · 不会重复发送任务",Modifier.weight(1f),fontSize=12.sp)
            SmallButton(if(c.connectionHealth=="auth")"重新登录"else if(c.connectionChecking)"检查中…"else"立即检查",{
                if(c.connectionHealth=="auth")c.reauthenticationOpen=true else c.checkConnection()
            },enabled=!c.connectionChecking)
        }
    }
    if(c.reauthenticationOpen) {
        var password by remember {mutableStateOf("")}
        HermesDialog(onDismissRequest={if(!c.connectionChecking)c.reauthenticationOpen=false},title={Text("恢复登录")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("${c.baseUrl}\n${c.username}")
                OutlinedTextField(password,{password=it},singleLine=true,label={Text("密码")},visualTransformation=PasswordVisualTransformation())
                Text("当前对话、草稿和附件会保留。密码仅用于本次登录。",fontSize=12.sp)
            }
        },confirmButton={DeskTextButton(onClick={val value=password;password="";c.reauthenticate(value)},enabled=password.isNotBlank()&&!c.connectionChecking){Text("登录")}},
            dismissButton={DeskTextButton(onClick={password="";c.reauthenticationOpen=false},enabled=!c.connectionChecking){Text("稍后")}})
    }
}

internal fun mcpStateLabel(s:McpServerInfo):String=if(!s.enabled)"已停用"else when(s.status.lowercase()) {
    "auth_required","unauthorized","reauth_required","needs_auth","expired","oauth_expired"->"需要重新授权"
    "connected","ready","available"->"可用"
    "configured"->"已配置，等待连接"
    "unconfigured","not_configured","missing_config"->"未配置"
    "error","failed","disconnected"->"连接失败"
    else->"状态待确认"
}
internal fun mcpNeedsAttention(s:McpServerInfo)=s.enabled&&mcpStateLabel(s) in setOf("需要重新授权","未配置","连接失败")
internal fun safeDiagnostic(raw:String)=raw
    .replace(Regex("(?i)(bearer\\s+)[^\\s,;]+"),"$1[隐藏]")
    .replace(Regex("(?i)((?:access_token|refresh_token|api_key|password|secret|token)\\s*[:=]\\s*)[^\\s,;&]+"),"$1[隐藏]").take(500)
