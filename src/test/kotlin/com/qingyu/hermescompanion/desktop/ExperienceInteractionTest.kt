@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.platform.DesktopHost
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.*

class ExperienceInteractionTest {
    private var priorLanguage="zh"
    @Before fun language(){priorLanguage=com.qingyu.hermescompanion.i18n.desktopLanguage;setDesktopLanguage("zh")}
    @After fun restoreLanguage(){setDesktopLanguage(priorLanguage)}
    private fun nodes(scene:ImageComposeScene):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    private fun tag(scene:ImageComposeScene,name:String)=nodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)==name}
    private fun label(scene:ImageComposeScene,text:String)=nodes(scene).first {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text==text}==true}
    private fun click(scene:ImageComposeScene,at:Offset) {
        scene.sendPointerEvent(PointerEventType.Move,at)
        scene.sendPointerEvent(PointerEventType.Press,at,button=PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release,at,button=PointerButton.Primary)
    }
    private suspend fun frames(scene:ImageComposeScene,count:Int=8) {repeat(count){scene.render(System.nanoTime()).close();delay(25)}}
    private fun press(scene:ImageComposeScene,key:Key,ctrl:Boolean=false) {
        scene.sendKeyEvent(KeyEvent(key,KeyEventType.KeyDown,isCtrlPressed=ctrl&&!DesktopHost.isMac,isMetaPressed=ctrl&&DesktopHost.isMac))
        scene.sendKeyEvent(KeyEvent(key,KeyEventType.KeyUp,isCtrlPressed=ctrl&&!DesktopHost.isMac,isMetaPressed=ctrl&&DesktopHost.isMac))
    }
    @Test fun sourceSelectionFormattingUndoSplitPreviewAndCloseKeepExactDraft()=runBlocking<Unit>(Dispatchers.Swing) {
        val c=DesktopController(true);c.page=Page.FILES;c.reduceMotion=true;c.setFilesPanel(false)
        val tab=c.document!!;val original=tab.document.content
        val scene=ImageComposeScene(1600,900,coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopWorkspace(c)}}
        try {
            frames(scene);click(scene,label(scene,"编辑").boundsInRoot.center);frames(scene)
            val raw="# 我的文档\n\n保留这段原文\n\n```js\nconst x = '<tag>';\n```"
            assertTrue(tag(scene,"markdown-source").config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(raw)))
            frames(scene)
            assertTrue(tag(scene,"markdown-source").config[SemanticsActions.SetSelection].action!!.invoke(9,11,false))
            frames(scene);click(scene,tag(scene,"markdown-bold").boundsInRoot.center);frames(scene)
            val changed=c.documentText();assertEquals(raw.substring(0,9)+"**"+raw.substring(9,11)+"**"+raw.substring(11),changed)
            press(scene,Key.Z,true);frames(scene);assertEquals(raw,c.documentText())
            press(scene,Key.Y,true);frames(scene);assertEquals(changed,c.documentText())
            click(scene,label(scene,"双栏").boundsInRoot.center);frames(scene,12)
            assertNotNull(tag(scene,"markdown-source"));assertNotNull(tag(scene,"document-content"))
            click(scene,label(scene,"查看修改").boundsInRoot.center);frames(scene)
            assertTrue(nodes(scene).any {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text.contains("const x = '<tag>';")}==true})
            assertEquals(original,tab.document.content)
            c.closeDocument(tab);assertEquals(changed,c.edits[c.documentKey(tab)])
            c.reopenDraft(c.documentKey(tab));frames(scene);assertEquals(changed,c.documentText())
        }finally {scene.close();c.close()}
    }
    @Test fun markdownSearchTakesFocusAndReplacingDoesNotRewriteTheServerVersion()=runBlocking<Unit>(Dispatchers.Swing) {
        val c=DesktopController(true);c.page=Page.FILES;c.reduceMotion=true;c.setFilesPanel(false)
        val original=c.document!!.document.content
        val scene=ImageComposeScene(1920,990,density=Density(1.5f),coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopWorkspace(c)}}
        try {
            frames(scene);click(scene,label(scene,"编辑").boundsInRoot.center);frames(scene)
            val sourceRange=tag(scene,"markdown-source-scroll").config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals("Entering Edit must keep the first line visible",0f,sourceRange.value(),1f)
            assertEquals(true,tag(scene,"markdown-source").config.getOrNull(SemanticsProperties.Focused))
            press(scene,Key.MoveEnd,true);frames(scene)
            assertEquals(original.length,tag(scene,"markdown-source").config[SemanticsProperties.TextSelectionRange].end)
            assertTrue("The cursor must follow the end of the document (max=${sourceRange.maxValue()})",sourceRange.value()>0)
            press(scene,Key.MoveHome,true);frames(scene)
            assertEquals("Returning to the first line must reveal its top padding",0f,sourceRange.value(),1f)
            press(scene,Key.F,true);frames(scene)
            assertEquals(true,tag(scene,"markdown-search-field").config.getOrNull(SemanticsProperties.Focused))
            assertEquals(original,c.documentText())
            tag(scene,"markdown-search-field").config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("桌面"));frames(scene)
            click(scene,label(scene,"替换").boundsInRoot.center);frames(scene)
            click(scene,label(scene,"全部替换").boundsInRoot.center);frames(scene)
            assertNotEquals(original,c.documentText())
            assertEquals(original.replace("桌面","",ignoreCase=true),c.documentText())
            assertEquals(original,c.document!!.document.content)
            click(scene,tag(scene,"markdown-source").boundsInRoot.center);press(scene,Key.Z,true);frames(scene)
            assertEquals(original,c.documentText())
        }finally {scene.close();c.close()}
    }
    @Test fun wheelOverHomeComposerReachesBottomAt150PercentAndCanReturn()=runBlocking<Unit>(Dispatchers.Swing) {
        val c=DesktopController(true);c.page=Page.HOME;c.reduceMotion=true
        val scene=ImageComposeScene(1920,990,density=Density(1.5f),coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopWorkspace(c)}}
        try {
            frames(scene)
            val field=nodes(scene).first {it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("交办新任务")==true}
            val range=nodes(scene).mapNotNull {it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)}.maxBy {it.maxValue()}
            val point=field.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Move,point)
            scene.sendPointerEvent(PointerEventType.Scroll,point,scrollDelta=Offset(0f,80f));frames(scene,16)
            assertTrue("Composer swallowed the wheel",range.value()>0)
            scene.sendPointerEvent(PointerEventType.Scroll,Offset(1200f,600f),scrollDelta=Offset(0f,800f));frames(scene,20)
            val end=tag(scene,"home-end").boundsInRoot
            assertTrue(end.height>0&&end.bottom<990)
            scene.sendPointerEvent(PointerEventType.Scroll,Offset(1200f,600f),scrollDelta=Offset(0f,-800f));frames(scene,20)
            assertEquals(0f,range.value(),1f)
        }finally {scene.close();c.close()}
    }
    @Test fun compactMarkdownOutlineRemainsAccessibleAndMenuKeyboardWorks()=runBlocking<Unit>(Dispatchers.Swing) {
        val c=DesktopController(true);c.page=Page.FILES;c.reduceMotion=true;c.setFilesPanel(false)
        c.page=Page.CHAT;c.documentSplit=true
        val scene=ImageComposeScene(1120,760,coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopWorkspace(c)}}
        try {
            frames(scene)
            click(scene,tag(scene,"markdown-outline").boundsInRoot.center);frames(scene)
            assertNotNull(label(scene,"本页目录"))
            press(scene,Key.DirectionDown);frames(scene)
            assertTrue("Arrow navigation did not focus a menu row",nodes(scene).any {it.config.getOrNull(SemanticsProperties.Focused)==true})
            press(scene,Key.Enter);frames(scene)
            assertFalse(nodes(scene).any {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text=="本页目录"}==true})
            click(scene,tag(scene,"workspace-switcher").boundsInRoot.center);frames(scene)
            assertNotNull(label(scene,"工作空间"))
            press(scene,Key.Escape);frames(scene)
            assertFalse(nodes(scene).any {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text=="工作空间"}==true})
        }finally {scene.close();c.close()}
    }
}
