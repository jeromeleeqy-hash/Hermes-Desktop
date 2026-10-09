@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.qingyu.hermescompanion.today.*
import com.qingyu.hermescompanion.data.*
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File

object Render201Previews {
    @JvmStatic fun main(args:Array<String>)=runBlocking(Dispatchers.Swing){
        val out=File(args.firstOrNull()?:"build/previews-2.0.1").apply {mkdirs()}
        val raw=File("src/main/resources/today/hermes-today-examples.json").readText()
        for(name in listOf("simple","deep","approval","approval-small","detail","voice")){
            val c=DesktopController(true);c.reduceMotion=true;c.page=if(name=="voice")Page.PROFILE else Page.HOME;c.setFilesPanel(false)
            c.today.preview(TodayBoard.decode(raw,"/work"),name=="deep")
            if(name=="voice")c.settingsSection="语音"
            val small=name=="approval-small";if(small)c.textScale=1.3f
            val width=if(small)720 else 1280;val height=if(small)480 else 900
            val pending=c.decisions.values.first().let {it.copy(request=it.request.copy(
                title=(1..40).joinToString("\n") {i->"printf '验证项目 $i — 等待你确认后才会执行\\n'"},
                detail="这项操作需要你的确认。\n\n- **用途**：整理项目中的文档\n- **范围**：当前工作目录\n\n请确认命令内容和影响范围，再决定是否允许。",
                choices=listOf(AgentRequestChoice("拒绝","deny"),AgentRequestChoice("本会话允许","session"),AgentRequestChoice("允许一次","once"))
            ))}
            c.decisions.clear();c.decisions["demo"]=pending
            val card=TodayCard("layout-test","周报整理与发布安排","【当前进展】资料已经汇总，等待确认后继续。\\n\\n【待你确认】\\n① 确认本周重点事项\\n② 核对项目进度与负责人\\n③ 选择发布时间\\n\\n【补充说明】\\n请先阅读全部内容，再决定后续安排。\\n\\n**本次变化**：补齐了最新项目进展。",TodayKind.DECISION,
                domain=TodayDomain.GENERAL,whenLabel="今天",
                presentation=TodayPresentation(facts=listOf("3 项事项待确认","资料已汇总"),caption="本周资料已汇总，有 3 项安排需要你确认。"))
            val scene=ImageComposeScene(width,height,density=Density(1f),coroutineContext=Dispatchers.Swing){
                HermesTheme(c){DesktopBackdrop{
                    CompositionLocalProvider(LocalDesktopWindowControls provides DesktopWindowControls()){
                        DesktopFrame {DesktopWorkspace(c)}
                    }
                    if(name.startsWith("approval"))Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha=.12f)),contentAlignment=Alignment.Center){DecisionDialog(c,pending,true){}}
                    if(name=="detail")Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha=.12f)),contentAlignment=Alignment.Center){TodayDetail(c,card,true){}}
                }}
            }
            try{
                repeat(5){scene.render(System.nanoTime()).close();delay(100)}
                scene.render(System.nanoTime()).use {image->image.encodeToData()!!.use {File(out,"$name.png").writeBytes(it.bytes)}}
                println("Rendered $name ${width}x$height")
            }finally{scene.close();c.close()}
        }
    }
}
