@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/** Renders real Compose layout on Linux; the native macOS traffic lights are deliberately absent. */
object RenderMacAppearance {
    private fun nodes(scene:ImageComposeScene):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    @JvmStatic fun main(args:Array<String>) {
        try {runBlocking(Dispatchers.Swing) {
            val out=File(args.firstOrNull()?:"docs/previews/macos-1.8.3-appearance").apply {mkdirs()}
            val temp=Files.createTempDirectory("hermes-mac-layout")
            val c=DesktopController(true,SecureConfigStore(temp){ByteArray(32){72}},autoConnect=false)
            try {
                c.notifications=false;c.reduceMotion=true;c.connected=true;c.page=Page.CHAT;c.decisions.clear();c.runs.clear()
                for(expanded in listOf(false,true)) {
                    c.sidebarCollapsed=!expanded;c.sidebarForcedOpen=expanded
                    val scene=ImageComposeScene(1440,900,density=Density(1f),coroutineContext=Dispatchers.Swing) {
                        HermesTheme(c) {CompositionLocalProvider(LocalMacWindowDragArea provides {modifier->Box(modifier)}) {
                            DesktopBackdrop {DesktopFrame {DesktopWorkspace(c)}}
                        }}
                    }
                    try {
                        repeat(30){scene.render(System.nanoTime()).close();delay(25)}
                        val all=nodes(scene)
                        val slot=all.first {it.config.getOrNull(SemanticsProperties.TestTag)=="mac-traffic-light-space"}.boundsInRoot
                        val brand=all.first {it.config.getOrNull(SemanticsProperties.TestTag)=="sidebar-brand"}.boundsInRoot
                        val heading=all.first {it.config.getOrNull(SemanticsProperties.TestTag)=="mac-header-drag"}.boundsInRoot
                        check(slot.top==12f&&slot.bottom<=46)
                        check(brand.top>=46)
                        check(heading.top<12) {"Header still has a separate title bar inset"}
                        check(all.none {it.config.getOrNull(SemanticsProperties.TestTag)=="window-chrome"})
                        val name=if(expanded)"Expanded"else"Collapsed"
                        File(out,"Linux-Compose-$name.png").writeBytes(scene.render(System.nanoTime()).use {it.encodeToData()!!.use {d->d.bytes}})
                        println("PASS $name: native control space $slot, brand $brand, header $heading")
                    } finally {scene.close()}
                }
                val sheet=BufferedImage(640,160,BufferedImage.TYPE_INT_ARGB)
                val g=sheet.createGraphics()
                try {
                    for((index,bg) in listOf(Color(245,245,247),Color(28,31,37)).withIndex()) {
                        g.color=bg;g.fillRect(index*320,0,320,160)
                        for((i,size) in listOf(18,36,54).withIndex()) {
                            val glyph=MacStatusIcon.render(size)
                            if(index==1)for(y in 0 until size)for(x in 0 until size)glyph.setRGB(x,y,(glyph.getRGB(x,y) and -0x1000000) or 0xffffff)
                            g.drawImage(glyph,index*320+45+i*88,80-size/2,null)
                        }
                    }
                } finally {g.dispose()}
                ImageIO.write(sheet,"png",File(out,"Template-Icon-Light-Dark.png"))
                println("PASS template icon sample: 18/36/54 pixels, alpha mask rendered on light/dark backgrounds (not an AppKit screenshot)")
            } finally {c.close();temp.toFile().deleteRecursively()}
        };System.exit(0)}catch(e:Throwable){e.printStackTrace();System.exit(1)}
    }
}
