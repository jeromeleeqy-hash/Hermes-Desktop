package com.qingyu.hermescompanion.desktop

import org.junit.Test
import org.junit.Assert.*

class ChatPresentationTest {
    private val header="[System: The active model for this chat has changed to deepseek-v4-pro via provider deepseek. From this point forward, use this runtime metadata when answering questions about what model/provider is active.]"
    @Test fun runtimeHeaderBecomesANoticeAndTheQuestionStaysIntact() {
        val original=header+"\n\n请整理今天的计划。"
        val result=presentUserText(original)
        assertEquals("请整理今天的计划。",result.body)
        assertEquals("已切换模型 · deepseek-v4-pro",result.notices.single().label)
        assertEquals(header,result.notices.single().raw)
        assertTrue(original.startsWith(header))
    }
    @Test fun quotedOrSimilarUserTextIsNeverStripped() {
        listOf("请解释：\n"+header,"```text\n"+header+"\n```","[System: this is my own note] 你好").forEach {text->
            assertEquals(text,presentUserText(text).body)
            assertTrue(presentUserText(text).notices.isEmpty())
        }
    }
    @Test fun onlyVerifiedLeadingImageReferencesAreReplacedByImageAttachments() {
        val path="/root/.hermes/attachments/image.png"
        assertEquals("请看这张图",presentUserText("@image:$path\n请看这张图",listOf(path)).body)
        listOf("@image:$path\n请看这张图","```\n@image:$path\n```","请解释 @image:$path","@image:${path}.other").forEach {raw->
            val sources=if(raw.startsWith("@image:$path\n"))emptyList()else listOf(path)
            assertEquals(raw,presentUserText(raw,sources).body)
        }
    }
    @Test fun multipleChangesKeepAllNoticesWithoutDuplicatingQuestion() {
        val result=presentUserText(header+"\n"+header+"\n你好")
        assertEquals("你好",result.body);assertEquals(2,result.notices.size)
    }
    @Test fun transportInterruptionIsExplainedButCodeExamplesRemainVisible() {
        val marker="Operation interrupted: waiting for model response (85.6s elapsed)."
        val result=presentAssistantText("已整理第一部分。\n"+marker)
        assertEquals("已整理第一部分。",result.body);assertTrue(result.notices.single().problem)
        val example="```text\n"+marker+"\n```"
        assertEquals(example,presentAssistantText(example).body)
        val nested="````text\n```\n"+marker+"\n```\n````"
        assertEquals(nested,presentAssistantText(nested).body)
        assertEquals("例如："+marker,presentAssistantText("例如："+marker).body)
    }
}
