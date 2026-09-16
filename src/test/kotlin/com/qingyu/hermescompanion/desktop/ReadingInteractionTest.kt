@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Test
import org.junit.Assert.*

class ReadingInteractionTest {
    private var previousLanguage="zh"
    @org.junit.Before fun retainLanguage(){previousLanguage=com.qingyu.hermescompanion.i18n.desktopLanguage}
    @org.junit.After fun restoreLanguage(){setDesktopLanguage(previousLanguage)}
    private fun nodes(scene:ImageComposeScene):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    private fun tag(scene:ImageComposeScene,name:String)=nodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)==name}
    private fun label(scene:ImageComposeScene,text:String)=nodes(scene).first {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text==text}==true}
    private fun click(scene:ImageComposeScene,point:Offset) {
        scene.sendPointerEvent(PointerEventType.Move,point);scene.sendPointerEvent(PointerEventType.Press,point,button=PointerButton.Primary);scene.sendPointerEvent(PointerEventType.Release,point,button=PointerButton.Primary)
    }
    private suspend fun frames(scene:ImageComposeScene,n:Int=14){repeat(n){scene.render(System.nanoTime()).close();delay(20)}}
    private suspend fun wheel(scene:ImageComposeScene,point:Offset,y:Float) {scene.sendPointerEvent(PointerEventType.Move,point);scene.sendPointerEvent(PointerEventType.Scroll,point,scrollDelta=Offset(0f,y));frames(scene,22)}
    @Test fun splitScrollFollowsBothDirectionsAndCanBeDisabledWithoutEditingContent()=runBlocking<Unit>(Dispatchers.Swing) {
        setDesktopLanguage("zh")
        val c=DesktopController(true);c.page=Page.FILES;c.reduceMotion=true;c.setFilesPanel(false);c.store.put("markdownSyncScroll","true")
        val text=(1..32).joinToString("\n\n"){"## 第 $it 节\n\n这是一段用于验证阅读与源码对齐的文字。段落换行和标题高度各不相同。\n\n```kotlin\nval section = $it\n```"}
        val tab=c.document!!;c.document=tab.copy(document=tab.document.copy(content=text,bytes=text.toByteArray()))
        val scene=ImageComposeScene(1600,900,coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopWorkspace(c)}}
        try {
            frames(scene);click(scene,label(scene,"双栏").boundsInRoot.center);frames(scene)
            val source=tag(scene,"markdown-source-scroll").config[SemanticsProperties.VerticalScrollAxisRange]
            val preview=tag(scene,"document-content").config[SemanticsProperties.VerticalScrollAxisRange]
            val left=tag(scene,"markdown-source-pane").boundsInRoot.center
            val right=tag(scene,"document-content").boundsInRoot.center
            wheel(scene,left,14f)
            assertTrue("source did not scroll",source.value()>20f)
            assertTrue("preview did not follow",preview.value()>0f)
            val before=source.value();wheel(scene,right,10f)
            assertTrue("source did not follow preview",source.value()>before)
            click(scene,tag(scene,"markdown-sync-toggle").boundsInRoot.center);frames(scene)
            val held=source.value();wheel(scene,right,8f)
            assertEquals("independent scrolling must leave the source still",held,source.value(),1f)
            assertEquals(text,c.documentText())
        }finally {scene.close();c.close()}
    }
    @Test fun userAvatarAppearsBesideUserMessageWithConfiguredIdentity()=runBlocking<Unit>(Dispatchers.Swing) {
        setDesktopLanguage("zh")
        val c=DesktopController(true);c.page=Page.CHAT;c.reduceMotion=true;c.setFilesPanel(false);c.nickname="Jerome"
        val session=c.currentSession!!
        c.messages[session.scopedId]=listOf(ChatMessage(role=MessageRole.USER,content="请帮我阅读这份文档。"),ChatMessage(role=MessageRole.ASSISTANT,content="可以，我们从第一部分开始。"))
        val scene=ImageComposeScene(1280,800,coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopWorkspace(c)}}
        try {frames(scene);assertTrue(tag(scene,"chat-user-avatar").boundsInRoot.width>0)}finally {scene.close();c.close()}
    }
}
