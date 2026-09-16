package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Test
import org.junit.Assert.*

class ExperiencePolishTest {
    @Test fun completedCaptionClicksToggleOnceAndDraggingBreaksTheSequence() {
        val tracker=CaptionClickTracker()
        assertFalse(tracker.release(100,Offset(120f,12f),false))
        assertTrue(tracker.release(250,Offset(121f,12f),false))
        assertFalse(tracker.release(400,Offset(120f,12f),false))
        assertFalse(tracker.release(500,Offset(250f,15f),true))
        assertFalse(tracker.release(600,Offset(250f,15f),false))
        assertTrue(tracker.release(720,Offset(250f,15f),false))
        assertFalse(tracker.release(1500,Offset(100f,15f),false))
        assertFalse(tracker.release(2300,Offset(100f,15f),false))
    }
    @Test fun markdownFormattingPreservesFrontMatterHtmlCodeAndSelectedChinese() {
        val original="---\ntitle: 文档\n---\n\n<div>保留</div>\n\n```js\nconst x = '<tag>';\n```\n\n本周计划"
        val start=original.indexOf("本周计划")
        val formatted=formatMarkdown(TextFieldValue(original,TextRange(start,start+4)),"bold")
        assertEquals(original.substring(0,start)+"**本周计划**",formatted.text)
        assertEquals("本周计划",formatted.text.substring(formatted.selection.min,formatted.selection.max))
        val history=MarkdownUndoHistory();history.record(TextFieldValue(original),formatted)
        assertEquals(original,history.undo(formatted)?.text)
        assertEquals(formatted.text,history.redo(TextFieldValue(original))?.text)
    }
    @Test fun chineseCompositionUndoesAsOneCommittedWordEvenWhenCommitOnlyChangesComposition() {
        val h=MarkdownUndoHistory();val original=TextFieldValue("写：",TextRange(2))
        val composing=TextFieldValue("写：ni",TextRange(4),TextRange(2,4))
        val candidate=TextFieldValue("写：你好",TextRange(4),TextRange(2,4))
        val committed=candidate.copy(composition=null)
        h.record(original,composing);h.record(composing,candidate);h.record(candidate,committed)
        assertEquals(original.text,h.undo(committed)?.text)
        assertNull(h.undo(original))
        assertEquals(committed.text,h.redo(original)?.text)
    }
    @Test fun selectionChangesDoNotEraseRedoButNewTypingDoes() {
        val h=MarkdownUndoHistory();val a=TextFieldValue("a");val b=TextFieldValue("ab")
        h.record(a,b);h.undo(b);h.record(a,a.copy(selection=TextRange(1)))
        assertEquals(b.text,h.redo(a)?.text)
        h.undo(b);h.record(a,TextFieldValue("ac"));assertNull(h.redo(TextFieldValue("ac")))
    }
    @Test fun indentationAddsOnRepeatedTabAndNumberedListTogglesAsABlock() {
        val v=TextFieldValue("一\n二",TextRange(0,3))
        val indented=formatMarkdown(formatMarkdown(v,"indent"),"indent")
        assertEquals("    一\n    二",indented.text)
        assertEquals("  一\n  二",formatMarkdown(indented,"outdent").text)
        assertEquals(v.text,formatMarkdown(formatMarkdown(v,"numbered"),"numbered").text)
        assertEquals(listOf(0..1,5..6),markdownMatches("Ab / ab","AB"))
    }
}
