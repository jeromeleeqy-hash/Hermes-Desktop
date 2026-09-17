@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class ComposerAtomicImeTest {
    private class Editor {
        val processor = EditProcessor()
        val changes = mutableListOf<TextFieldValue>()
        val request: PlatformTextInputMethodRequest
        init {
            processor.apply(listOf(CommitTextCommand("前文后文",1), SetSelectionCommand(2,2)))
            val upstream = mock(PlatformTextInputMethodRequest::class.java)
            `when`(upstream.onEditCommand).thenReturn { commands -> changes += processor.apply(commands) }
            request = atomicComposerImeRequest(upstream)
        }
        fun preedit(text: String, committed: String = ""): TextFieldValue {
            changes.clear()
            request.editText { commitText(committed,1); if(text.isNotEmpty()) setComposingText(text,1) }
            assertEquals("One native update must produce exactly one editor change",1,changes.size)
            return changes.single()
        }
    }
    @Test fun deletingPinyinNeverPublishesAnEmptyIntermediateDraft() {
        val e=Editor();val pinyin="nihaoshijie"
        e.preedit(pinyin)
        for(length in pinyin.length-1 downTo 1) {
            val value=e.preedit(pinyin.take(length))
            assertEquals("前文${pinyin.take(length)}后文",value.text)
            assertEquals(TextRange(2,2+length),value.composition)
            assertEquals(TextRange(2+length),value.selection)
        }
        val cancelled=e.preedit("")
        assertEquals("前文后文",cancelled.text);assertNull(cancelled.composition)
    }
    @Test fun partialCommitKeepsRemainingCompositionAndFinalCommitKeepsSuffix() {
        val e=Editor();e.preedit("nihaoshijie")
        val partial=e.preedit("shijie","你好")
        assertEquals("前文你好shijie后文",partial.text)
        assertEquals(TextRange(4,10),partial.composition)
        val final=e.preedit("","世界")
        assertEquals("前文你好世界后文",final.text);assertNull(final.composition)
    }
    @Test fun abortedNativeEditCannotPublishHalfAnUpdate() {
        val e=Editor();e.preedit("nihao");e.changes.clear()
        try {e.request.editText {commitText("",1);error("abort")};fail("Expected abort")}
        catch(expected:IllegalStateException){assertEquals("abort",expected.message)}
        assertTrue(e.changes.isEmpty())
        val next=e.preedit("niha")
        assertEquals("前文niha后文",next.text)
    }
}
