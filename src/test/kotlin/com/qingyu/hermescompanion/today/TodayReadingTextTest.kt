package com.qingyu.hermescompanion.today

import org.junit.Assert.*
import org.junit.Test

class TodayReadingTextTest {
    @Test fun escapedParagraphsBecomeReadableHeadingsAndLists() {
        val raw="【进展】已完成第一轮。\\n\\n【下一步】\\n① 检查模型\\n② 继续验收"
        assertEquals("### 进展\n\n已完成第一轮。\n\n### 下一步\n\n\n1. 检查模型\n2. 继续验收",todayReadingText(raw))
        assertTrue(raw.contains("\\n"))
    }
    @Test fun codeAndPathsKeepTheirLiteralBackslashes() {
        val raw="`printf('\\n')`\n```python\nprint('\\n')\n【不是标题】\n```\nC:\\new\\notes.txt\nhttps://example.com/a\\new\n/var/notes\\new"
        assertEquals(raw,todayReadingText(raw))
    }
    @Test fun existingMarkdownAndOrdinaryParagraphsStayReadable() {
        val raw="## 已有标题\n\n**重要内容**\n\n- 第一项\n- 第二项"
        assertEquals(raw,todayReadingText(raw))
        assertEquals("第一段\n第二段",todayReadingText("第一段\\r\\n第二段"))
    }
}
