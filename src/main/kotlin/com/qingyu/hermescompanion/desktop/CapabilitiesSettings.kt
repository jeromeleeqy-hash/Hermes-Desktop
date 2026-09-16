package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*

@Composable fun CapabilitiesSettings(c:DesktopController,section:String) {
    var query by remember(section){mutableStateOf("")};var filter by remember(section){mutableStateOf("全部")}
    val pending=remember(c.profile,section){mutableStateMapOf<String,Boolean>()};val errors=remember(c.profile,section){mutableStateMapOf<String,String>()}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            CompactInput(query,{query=it},"搜索名称或用途",Modifier.weight(1f))
            Picker(filter,listOf("全部","已启用","未启用"),{it}){filter=it}
        }
        SubtleText(when(section){"Skills"->"技能规定做事的方法；工具集提供实际执行能力。";"MCP"->"连接外部服务，状态与工具数量以服务器返回为准。";else->"这些工具在 Hermes 服务器上运行。展开可查看具体工具。"},maxLines=2)
        fun matches(name:String,description:String,enabled:Boolean)= (query.isBlank()||name.contains(query,true)||description.contains(query,true))&&(filter=="全部"||(filter=="已启用")==enabled)
        var count=0
        when(section) {
            "Skills"->{
                val visible=c.skills.filter {matches(it.name,it.description,it.enabled)};count=visible.size
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {CountBadge(c.skills.count {it.enabled});SubtleText("已启用 · 共 ${c.skills.size} 个技能")}
                BoxWithConstraints {
                    val columns=if(maxWidth>=780.dp)2 else 1
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {visible.chunked(columns).forEach {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {row.forEach {skill->
                            Box(Modifier.weight(1f)) {SettingCard(skill.name,trailing={Switch(skill.enabled,{v->
                        val p=c.profile;pending[skill.name]=true
                        c.request(p,{it.setSkillEnabled(skill.name,v)},finished={pending.remove(skill.name)},failed={errors[skill.name]=it}){if(c.profile==p){c.skills=c.skills.map {if(it.name==skill.name)it.copy(enabled=v)else it};errors.remove(skill.name)}}
                    },enabled=pending[skill.name]!=true)}) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){Glyph("bookmark",Modifier.size(15.dp),MaterialTheme.colorScheme.primary);SubtleText(skill.category);Spacer(Modifier.weight(1f));SubtleText(if(skill.enabled)"已启用"else"未启用")}
                    Text(skill.description,Modifier.heightIn(min=40.dp),fontSize=12.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    errors[skill.name]?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        DeskTextButton(onClick={c.request(block={it.skillContent(skill.name)}){c.showDetails("技能说明 · ${skill.name}",it)}}){Text(tr("查看完整说明"))}
                        Spacer(Modifier.weight(1f));if(pending[skill.name]==true)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                    }
                }}
                        };if(row.size<columns)Spacer(Modifier.weight(1f))}
                    }}
                }
            }
            "工具集"->c.toolsets.filter {matches(it.label,it.description+it.tools.joinToString(),it.enabled)}.forEach {tool->count++
                val label=mapOf("Web Search & Scraping" to "网页搜索与提取","Browser Automation" to "浏览器操作","Terminal & Processes" to "终端与进程","File Operations" to "文件操作","Code Execution" to "代码执行","Vision / Image Analysis" to "图像分析","Video Analysis" to "视频分析")[tool.label]?:tool.label
                SettingCard(label,trailing={Switch(tool.enabled,{v->val p=c.profile;pending[tool.name]=true
                            c.request(p,{it.setToolsetEnabled(tool.name,v)},finished={pending.remove(tool.name)},failed={errors[tool.name]=it}){if(c.profile==p){c.toolsets=c.toolsets.map {if(it.name==tool.name)it.copy(enabled=v)else it};errors.remove(tool.name)}}
                        },enabled=pending[tool.name]!=true)}) {
                    SubtleText(tool.description,maxLines=2)
                    if(!tool.configured)StatusPill("缺少服务器配置",error=true)
                    errors[tool.name]?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                    Disclosure("包含 ${tool.tools.size} 个工具") {tool.tools.forEach {name->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Glyph("tool",Modifier.size(14.dp),MaterialTheme.colorScheme.primary);Text(toolLabel(name),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
                }
            }
            else->c.mcpServers.filter {matches(it.name,it.transport,it.enabled)}.forEach {server->count++
                SettingCard(server.name,trailing={Switch(server.enabled,{v->val p=c.profile;pending[server.name]=true
                        c.request(p,{it.setMcpServerEnabled(server.name,v)},finished={pending.remove(server.name)},failed={errors[server.name]=it}){if(c.profile==p)c.loadSettings()}
                    },enabled=pending[server.name]!=true)}) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Glyph("connect",Modifier.size(17.dp),MaterialTheme.colorScheme.primary)
                        StatusPill(mcpStateLabel(server),error=mcpNeedsAttention(server))
                        SubtleText(server.transport.uppercase())
                    }
                    if(server.detail.isNotBlank())Caption(safeDiagnostic(server.detail))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        SmallButton("刷新状态",{val p=c.profile;c.request(p,{it.listMcpServers()},failed={errors[server.name]=it}){if(c.profile==p)c.mcpServers=it}})
                        server.authorizationUrl?.let {url->SmallButton("重新授权",{runCatching {DesktopFiles.openLink(url)}.onFailure {errors[server.name]=it.message?:"无法打开授权页面"}})}
                    }
                    if(mcpNeedsAttention(server)&&server.authorizationUrl==null)Caption("服务器没有提供授权入口。请在 Hermes 服务端完成配置或授权，然后刷新状态。")
                    SubtleText("服务器报告 ${server.toolCount} 个工具 · ${if(server.enabled)"已启用"else"已停用"}")
                    if(server.enabled&&server.toolCount==0)Caption("已启用配置，尚未发现可调用工具。请检查服务器运行状态。")
                    errors[server.name]?.let {Text(safeDiagnostic(it),color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                }
            }
        }
        if(count==0)PanelEmpty("search",if(query.isBlank()&&filter=="全部")"当前没有配置"else"没有匹配项",if(query.isBlank())"刷新服务器配置后再查看。"else"试试其他关键词，或切换筛选条件。")
    }
}

