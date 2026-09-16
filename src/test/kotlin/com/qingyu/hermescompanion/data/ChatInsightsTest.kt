package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.ChatMessage
import com.qingyu.hermescompanion.model.MessageRole
import com.qingyu.hermescompanion.model.TodoStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatInsightsTest {
    @Test
    fun extractsArtifactsAndLatestTodoState() {
        val messages = listOf(
            ChatMessage(role = MessageRole.TOOL, content = "{\"todos\":[{\"id\":\"1\",\"content\":\"整理资料\",\"status\":\"in_progress\"}]}"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "已生成 `/root/workspace/日报.md` 和 [安装包](/root/workspace/Hermes.apk)"),
        )

        val result = ChatInsightParser.fromMessages(messages)

        assertEquals(listOf("日报.md", "Hermes.apk"), result.artifacts.map { it.name })
        assertEquals("整理资料", result.todos.single().content)
        assertEquals(TodoStatus.IN_PROGRESS, result.todos.single().status)
    }
}
