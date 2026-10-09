@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.runtime.*
import com.qingyu.hermescompanion.today.*
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File

object Render202Previews {
    @JvmStatic fun main(args:Array<String>)=runBlocking(Dispatchers.Swing){
        val out=File(args.firstOrNull()?:"build/previews-2.0.2").apply {mkdirs()}
        for(name in listOf("workbench","attention","conversations")){
            val c=DesktopController(true);c.reduceMotion=true;c.setFilesPanel(false);c.project=null;setDesktopLanguage("zh");c.page=if(name=="conversations")Page.CHAT else Page.HOME
            val main=c.currentSession!!
            c.sessions=listOf(main.copy(title="日常助理",isPinned=true))+listOf("小红书方法论落地","本周工作计划","设计文案整理","项目进展复盘","产品方案讨论","文件归档","差旅准备","工具使用笔记").mapIndexed {i,title->HermesSession("demo-$i",title,preview="继续处理这件事",profile=c.profile)}
            c.currentSession=c.sessions.first()
            c.unread.clear();c.unread.addAll(listOf("default::demo-0","default::demo-1"))
            c.today.preview(TodayBoard.decode(File("src/main/resources/today/hermes-today-examples.json").readText(),"/work"),name=="attention")
            val scene=ImageComposeScene(1440,1000,density=Density(1f),coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopBackdrop{
                CompositionLocalProvider(LocalDesktopWindowControls provides DesktopWindowControls()){DesktopFrame{DesktopWorkspace(c)}}
            }}}
            try{repeat(6){scene.render(System.nanoTime()).close();delay(50)};scene.render(System.nanoTime()).use {image->image.encodeToData()!!.use {File(out,"$name.png").writeBytes(it.bytes)}};println("Rendered $name")}
            finally{scene.close();c.close()}
        }
    }
}
