@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.Color
import com.qingyu.hermescompanion.data.ArtifactLookupException
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File

object RenderArtifactLookup205 {
    @JvmStatic fun main(args:Array<String>)=runBlocking(Dispatchers.Swing){
        val out=File(args.firstOrNull()?:"build/previews-2.0.5").apply {mkdirs()}
        for(small in listOf(false,true)){
            val c=DesktopController(true);c.reduceMotion=true;c.textScale=if(small)1.3f else 1f
            val session=c.currentSession!!
            val item=RecentArtifact(c.profile,session.id,"工作日报","m1","工作简报.md","工作简报.md","Markdown","/root/workspace")
            val candidates=(1..8).map {WorkspaceEntry("工作简报.md","/root/workspace/项目资料/第${it}个项目/日报/工作简报.md",false)}
            c.artifactLookupPrompt=ArtifactLookupPrompt(item,session,ArtifactLookupException("找到多个同名文件，请根据所在目录选择要打开的文件。",candidates,"目录查找示例"))
            val width=if(small)720 else 1280;val height=if(small)480 else 900
            val scene=ImageComposeScene(width,height,density=Density(1f),coroutineContext=Dispatchers.Swing){
                HermesTheme(c){Box(Modifier.fillMaxSize().background(Color(0xffe9edf4)),contentAlignment=Alignment.Center){ArtifactLookupDialog(c,true)}}
            }
            try{
                repeat(5){scene.render(System.nanoTime()).close();delay(80)}
                val nodes=scene.semanticsOwners.flatMap {owner->buildList<SemanticsNode>{fun visit(n:SemanticsNode){add(n);n.children.forEach(::visit)};visit(owner.rootSemanticsNode)}}
                for(label in listOf("复制排查信息","关闭","打开路径","粘贴服务器完整路径")){
                    val node=nodes.first {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text==label}==true}
                    check(node.boundsInRoot.top>=0 && node.boundsInRoot.bottom<=height){"Footer is outside the viewport: $label"}
                }
                scene.render(System.nanoTime()).use {image->image.encodeToData()!!.use {File(out,if(small)"file-location-small.png"else "file-location.png").writeBytes(it.bytes)}}
                println("PASS artifact lookup ${width}x${height}: choices scroll, footer visible")
            }finally{scene.close();c.close()}
        }
    }
}
