package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JEditorPane

/** Verify the renderer inside a real Compose/Swing window, not only an offscreen document. */
object NativeDocumentCheck {
    @JvmStatic fun main(args:Array<String>) {
        try {runBlocking(Dispatchers.Swing) {
            val out=File("docs/previews/windows-1.8.0").apply {mkdirs()}
            fun editors(component:Component):List<JEditorPane> = (if(component is JEditorPane)listOf(component)else emptyList())+(if(component is Container)component.components.flatMap(::editors)else emptyList())
            val word=withContext(Dispatchers.IO){wordPreview(OfficeDisplayFixtures.word(),false)}
            val sheet=withContext(Dispatchers.IO){WorkbookPreview(OfficeDisplayFixtures.workbook()).use {it.html(0)}}
            for((name,html) in listOf("Native-Word" to word,"Native-Excel" to sheet,"Native-HTML" to "<html><body style='background:white;color:#25314a;font-family:&quot;Noto Sans CJK SC&quot;,&quot;Microsoft YaHei&quot;,sans-serif;padding:35px'><h1>Hermes HTML 预览</h1><p>网页组件在真实 Compose 窗口中显示中文与表格。</p><table border='1'><tr><td>任务</td><td>完成</td></tr></table></body></html>")) {
                val window=ComposeWindow().apply {setSize(1120,800);setLocation(30,30);title=name}
                try {
                    window.setContent {if(name=="Native-HTML")WebDocument(html,Modifier.fillMaxSize())else CompatibleDocument(html,Modifier.fillMaxSize())}
                    window.isVisible=true
                    if(name!="Native-HTML")withTimeout(10_000){while(editors(window).none {it.document.length>20})delay(100)}else delay(3000)
                    delay(1500)
                    val image=Robot().createScreenCapture(window.bounds)
                    ImageIO.write(image,"png",File(out,"$name.png"))
                    var ink=0
                    for(y in 40 until image.height step 4)for(x in 10 until image.width-10 step 4){val c=Color(image.getRGB(x,y));if(c.red<150&&c.green<150&&c.blue<150)ink++}
                    check(ink>100){"$name remained blank: $ink"}
                    println("PASS: $name inside real Compose window, $ink content samples")
                    if(name=="Native-Excel") {
                        val editor=editors(window).single()
                        val viewport=editor.parent as javax.swing.JViewport
                        val robot=Robot();robot.mouseMove(window.x+250,window.y+350);robot.mouseWheel(8)
                        withTimeout(4000){while(viewport.viewPosition.y==0)delay(100)}
                        println("PASS: native Excel mouse-wheel scrolling")
                    }

                }finally {window.dispose()}
            }
            FxRuntime.shutdown()
        };kotlin.system.exitProcess(0)}catch(e:Throwable){e.printStackTrace();kotlin.system.exitProcess(1)}
    }
}
