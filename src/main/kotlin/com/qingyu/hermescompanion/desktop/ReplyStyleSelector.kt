package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.json.JSONObject

internal data class ReplyStyleOption(val value:String,val label:String,val detail:String)

/** Names match Hermes Agent's built-ins; labels never become server configuration values. */
internal fun replyStyleOptions(rawConfig:String,current:String):List<ReplyStyleOption> {
    val choices=linkedMapOf(
        "none" to ReplyStyleOption("none","默认（跟随助理设定）","使用助理原有的说话方式。"),
        "helpful" to ReplyStyleOption("helpful","友好助理","自然友好，适合日常交流。"),
        "concise" to ReplyStyleOption("concise","简洁直接","先说重点，回答更精炼。"),
        "technical" to ReplyStyleOption("technical","技术专家","注重准确性与技术细节。"),
        "creative" to ReplyStyleOption("creative","创意发散","适合选题、灵感与头脑风暴。"),
        "teacher" to ReplyStyleOption("teacher","耐心讲解","循序渐进，用例子解释问题。"),
        "kawaii" to ReplyStyleOption("kawaii","可爱活泼","轻松活泼，带一点可爱表达。"),
        "catgirl" to ReplyStyleOption("catgirl","猫娘风格","带有猫咪语气的角色表达。"),
        "pirate" to ReplyStyleOption("pirate","海盗船长","冒险船长式的幽默口吻。"),
        "shakespeare" to ReplyStyleOption("shakespeare","莎士比亚风格","富有戏剧感与文学色彩。"),
        "surfer" to ReplyStyleOption("surfer","随性轻松","放松随和，像朋友一样聊天。"),
        "noir" to ReplyStyleOption("noir","黑色侦探","冷峻的侦探叙事口吻。"),
        "uwu" to ReplyStyleOption("uwu","软萌可爱","更夸张的软萌角色表达。"),
        "philosopher" to ReplyStyleOption("philosopher","哲学思辨","从问题背后展开思考。"),
        "hype" to ReplyStyleOption("hype","热情鼓励","充满能量的鼓励式表达。"),
    )
    val config=runCatching {JSONObject(rawConfig)}.getOrDefault(JSONObject())
    // Server definitions override built-ins; agent.personalities overrides top-level personalities.
    listOf(config.optJSONObject("personalities"),config.optJSONObject("agent")?.optJSONObject("personalities")).forEach {custom->
        custom?.keys()?.asSequence()?.filter {it.isNotBlank()&&it!="none"}?.sorted()?.forEach {name->
            val existing=choices[name]
            choices[name]=ReplyStyleOption(name,existing?.label?:name,"服务器自定义风格")
        }
    }
    if(current.isNotBlank()&&current !in choices)choices[current]=ReplyStyleOption(current,"当前自定义风格","保留当前服务器设置："+current)
    return choices.values.toList()
}

@Composable internal fun ReplyStyleSelector(value:String,rawConfig:String,onSelect:(String)->Unit) {
    val options=remember(rawConfig,value){replyStyleOptions(rawConfig,value)}
    val selected=options.first {it.value==value.ifBlank {"none"}}
    var expanded by remember {mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(tr("回复风格"),fontSize=13.sp)
        Box(Modifier.fillMaxWidth()) {
            Surface(shape=LocalDesktopDesign.current.controlShape,color=colors.surface,border=BorderStroke(1.dp,colors.outline),modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().heightIn(min=44.dp).semantics {testTag="reply-style-picker";stateDescription=tr(selected.label)}
                    .desktopClick {expanded=!expanded}.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(tr(selected.label),Modifier.weight(1f),fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Glyph("chevron-down",Modifier.size(16.dp))
                }
            }
            DeskMenu(expanded,{expanded=false},modifier=Modifier.widthIn(min=280.dp,max=480.dp)) {
                options.forEach {option->
                    DeskMenuItem(text={Text(tr(option.label),maxLines=2,overflow=TextOverflow.Ellipsis)},
                        trailingIcon={if(option.value==selected.value)Glyph("check",Modifier.size(16.dp))},
                        onClick={expanded=false;onSelect(option.value)})
                }
            }
        }
        Text(tr(selected.detail),fontSize=12.sp,lineHeight=19.sp,color=colors.onSurfaceVariant)
    }
}
