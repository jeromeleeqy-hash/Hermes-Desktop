@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.qingyu.hermescompanion.model.HermesSession
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/** Render the actual narrow sidebar at both DPIs; compare control centers in physical pixels. */
object Navigation182Check {
    @JvmStatic fun main(args:Array<String>)=runBlocking(Dispatchers.Swing) {
        val out=File(args.firstOrNull()?:"build/verification182/navigation").apply {mkdirs()}
        val dir=Files.createTempDirectory("hermes-navigation182")
        val c=DesktopController(true,SecureConfigStore(dir){ByteArray(32){9}},autoConnect=false)
        try {
            c.reduceMotion=true;c.page=Page.HOME;c.project=null
            c.sessions=listOf("日常助理","秋季内容运营方案","本周工作安排","阅读与灵感").mapIndexed {i,title->
                HermesSession("recent-$i",title,profile=c.profile,updatedAt="2026-09-14T12:0${4-i}:00Z",isPinned=i==0)
            }
            for(scale in listOf(1f,2f))for(expanded in listOf(false,true)) {
                val width=if(expanded)184 else 64
                val scene=ImageComposeScene((width*scale).toInt(),(640*scale).toInt(),density=Density(scale),coroutineContext=Dispatchers.Swing) {
                    HermesTheme(c){DesktopBackdrop{WorkspaceSidebar(c,expanded){}}}
                }
                try {
                    repeat(10){scene.render(System.nanoTime()).close();delay(25)}
                    fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
                    val nodes=scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
                    fun tagged(tag:String)=nodes.first {it.config.getOrNull(SemanticsProperties.TestTag)==tag}
                    if(!expanded) {
                        for(tag in listOf("workspace-switcher-icon","sidebar-expand","sidebar-recents")) {
                            val bounds=tagged(tag).boundsInRoot
                            check(abs(bounds.center.x-32*scale)<=.5f){"$tag not centered at $scale: $bounds"}
                            check(bounds.height>0&&bounds.bottom<=640*scale){"$tag clipped: $bounds"}
                        }
                    }else check(nodes.count {it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("recent-shortcut:")==true}==4)
                    val file=File(out,"Navigation-${if(expanded)"Expanded"else"Collapsed"}-${scale.toInt()}x.png")
                    scene.render(System.nanoTime()).use {image->image.encodeToData()!!.use {file.writeBytes(it.bytes)}}
                    println("PASS navigation expanded=$expanded scale=$scale: $file")
                }finally {scene.close()}
            }
        }finally {c.close();dir.toFile().deleteRecursively()}
    }
}
