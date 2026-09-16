@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/** Real Compose render and interaction checks. Does not emulate native macOS window controls. */
object RenderUiRevision3 {
    private fun nodes(scene:ImageComposeScene):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    private suspend fun settle(scene:ImageComposeScene){repeat(20){scene.render(System.nanoTime()).close();delay(15)}}
    private fun save(scene:ImageComposeScene,file:File){file.writeBytes(scene.render(System.nanoTime()).use {it.encodeToData()!!.use {d->d.bytes}})}
    @JvmStatic fun main(args:Array<String>) {
        try {runBlocking(Dispatchers.Swing) {
            val out=File(args.firstOrNull()?:"docs/previews/desktop-1.8.3-ui3").apply {mkdirs()}
            val temp=Files.createTempDirectory("hermes-ui3")
            val c=DesktopController(true,SecureConfigStore(temp){ByteArray(32){72}},autoConnect=false)
            try {
                c.notifications=false;c.reduceMotion=true;c.connected=true;c.page=Page.TASKS;c.decisions.clear();c.runs.clear()
                c.sidebarCollapsed=true;c.setFilesPanel(false)
                c.completions=listOf(RunCompletionSummary(c.profile+"::demo","日常助理","今天的工作已整理好。"))
                val cases=listOf(Triple("Mac-Matte",true,"轻盈办公"),Triple("Mac-Paper",true,"纸间留白"),Triple("Mac-Glass",true,"流光玻璃"),Triple("Windows-Matte",false,"轻盈办公"))
                for((name,mac,skin) in cases) for(scale in listOf(1f,1.3f)) {
                    c.skin=skin;c.textScale=scale
                    val width=if(scale==1f)1440 else 1200;val height=if(scale==1f)900 else 760
                    val scene=ImageComposeScene(width,height,density=Density(1f),coroutineContext=Dispatchers.Swing) {
                        HermesTheme(c) {CompositionLocalProvider(LocalMacWindowDragArea provides if(mac)({m:Modifier->Box(m)})else null) {
                            DesktopBackdrop {DesktopWorkspace(c)}
                        }}
                    }
                    try {
                        settle(scene);save(scene,File(out,"$name-${(scale*100).toInt()}.png"));val all=nodes(scene)
                        fun tag(value:String)=all.first {it.config.getOrNull(SemanticsProperties.TestTag)==value}
                        val panel=tag("workspace-main-panel").boundsInRoot
                        val inset=if(skin=="纸间留白")12f else 8f
                        check(abs(panel.top-inset)<.1f&&abs(width-panel.right-inset)<.1f&&abs(height-panel.bottom-inset)<.1f){"Unequal window edges: $panel"}
                        if(mac)check(abs(tag("mac-traffic-light-space").boundsInRoot.bottom-46f)<.1f)
                        for(title in listOf("待你确认","正在执行")) {
                            val body=tag("lane-empty-$title").boundsInRoot
                            val text=if(title=="待你确认")"需要你审批或补充说明时，会出现在这里。"else"在对话中交代一件事，进度会同步到这里。"
                            val node=all.first {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text==text}==true}
                            val layouts=mutableListOf<TextLayoutResult>()
                            check(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)==true)
                            // The reported regression is vertical clipping. Horizontal paragraph metrics
                            // from centered wrap-content Text are not its final painted coordinates.
                            check(layouts.all {layout->!layout.didOverflowHeight&&
                                layout.getLineBottom(layout.lineCount-1)<=layout.size.height+1f}){"Vertically clipped text in $name at $scale"}
                            check(node.boundsInRoot.bottom<=body.bottom+.1f&&node.boundsInRoot.top>=body.top-.1f){"Empty detail falls outside the card: ${node.boundsInRoot}, $body"}
                        }
                        save(scene,File(out,"$name-${(scale*100).toInt()}.png"))
                        println("PASS $name ${scale*100}%: full empty-state text, equal $inset px edges, control reservation")
                    } finally {scene.close()}
                }
                c.skin="轻盈办公";c.textScale=1f
                var value by mutableStateOf("teacher")
                val picker=ImageComposeScene(660,860,density=Density(1f),coroutineContext=Dispatchers.Swing) {
                    HermesTheme(c){DesktopBackdrop {Column(Modifier.padding(40.dp)){ReplyStyleSelector(value,"{}"){value=it}}}}
                }
                try {
                    settle(picker)
                    nodes(picker).first {it.config.getOrNull(SemanticsProperties.TestTag)=="reply-style-picker"}.config[SemanticsActions.OnClick].action!!.invoke()
                    settle(picker);save(picker,File(out,"Reply-Style-Dropdown.png"))
                    val option=nodes(picker).first {n->n.config.getOrNull(SemanticsProperties.Text)?.any {it.text=="简洁直接"}==true}
                    var clickable:SemanticsNode?=option
                    while(clickable!=null&&clickable.config.getOrNull(SemanticsActions.OnClick)==null)clickable=clickable.parent
                    check(clickable!=null);clickable.config[SemanticsActions.OnClick].action!!.invoke()
                    settle(picker);check(value=="concise")
                    check(nodes(picker).first {it.config.getOrNull(SemanticsProperties.TestTag)=="reply-style-picker"}.config[SemanticsProperties.StateDescription]=="简洁直接")
                    save(picker,File(out,"Reply-Style-Selected.png"));println("PASS dropdown: opens, selects canonical concise value, closes and updates label")
                } finally {picker.close()}
            } finally {c.close();temp.toFile().deleteRecursively()}
        };System.exit(0)}catch(e:Throwable){e.printStackTrace();System.exit(1)}
    }
}
