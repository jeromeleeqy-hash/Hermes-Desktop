@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Assert.*
import org.junit.Test

class ComposerInputRegressionTest {
    private class Editor {
        var key by mutableStateOf("a")
        var draft by mutableStateOf("abcdef中文XYZ")
        lateinit var field: MutableState<TextFieldValue>
        fun edit(value:TextFieldValue) { field.value=value;draft=value.text }
        @Composable fun Content() {
            field=rememberComposerValue(key,draft)
            BasicTextField(field.value,::edit,Modifier.width(500.dp).height(120.dp).semantics {testTag="editor"})
        }
    }
    private fun nodes(scene:ImageComposeScene):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    private fun node(scene:ImageComposeScene)=nodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="editor"}
    private suspend fun frames(scene:ImageComposeScene) {repeat(3){scene.render(System.nanoTime()).close();delay(15)}}

    @Test fun pendingDraftEchoCannotOverwriteANewerDeletionOrSelection()=runBlocking<Unit>(Dispatchers.Swing) {
        val e=Editor();val scene=ImageComposeScene(600,200,coroutineContext=Dispatchers.Swing){e.Content()}
        try {
            frames(scene)
            repeat(20) {
                e.edit(TextFieldValue("abcdef中文XYZ",TextRange(8)))
                scene.render(System.nanoTime()).close()
                // Another input arrives before effects from the preceding frame can run.
                val latest=TextFieldValue("abcdef中XYZ",TextRange(7))
                e.edit(latest)
                yield();delay(10)
                assertEquals("A delayed draft echo rewrote the editor",latest,e.field.value)
                frames(scene)
                assertEquals(latest,e.field.value)
            }
        }finally {scene.close()}
    }
    @Test fun matchingDraftKeepsImeCompositionAndExternalReplacementAppliesBeforeRender()=runBlocking<Unit>(Dispatchers.Swing) {
        val e=Editor();val scene=ImageComposeScene(600,200,coroutineContext=Dispatchers.Swing){e.Content()}
        try {
            frames(scene)
            val composing=TextFieldValue("你好pinyin",TextRange(8),TextRange(2,8))
            e.edit(composing);frames(scene);assertEquals(composing,e.field.value)
            e.edit(composing.copy(selection=TextRange(3,6)));frames(scene)
            assertEquals(TextRange(3,6),e.field.value.selection)
            assertEquals(TextRange(2,8),e.field.value.composition)
            e.draft="外部插入的引用";scene.render(System.nanoTime()).close()
            assertEquals(e.draft,e.field.value.text);assertNull(e.field.value.composition)
            e.key="b";e.draft="另一个会话";frames(scene)
            assertEquals(e.draft,e.field.value.text)
            e.draft="";frames(scene);assertEquals(TextFieldValue(""),e.field.value)
        }finally {scene.close()}
    }
    @Test fun rapidBackspaceInTheMiddleDoesNotDeleteTheSuffix()=runBlocking<Unit>(Dispatchers.Swing) {
        val e=Editor();val scene=ImageComposeScene(600,200,coroutineContext=Dispatchers.Swing){e.Content()}
        try {
            frames(scene)
            val at=node(scene).boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press,at,button=PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release,at,button=PointerButton.Primary)
            frames(scene)
            node(scene).config[SemanticsActions.SetSelection].action!!.invoke(8,8,false)
            frames(scene)
            repeat(5) { i->
                scene.sendKeyEvent(KeyEvent(Key.Backspace,KeyEventType.KeyDown))
                scene.sendKeyEvent(KeyEvent(Key.Backspace,KeyEventType.KeyUp))
                if(i%2==0)scene.render(System.nanoTime()).close()
            }
            frames(scene)
            assertEquals("abcXYZ",e.draft)
            assertEquals(TextRange(3),e.field.value.selection)
        }finally {scene.close()}
    }
}
