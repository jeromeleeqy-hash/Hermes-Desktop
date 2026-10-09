package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.*

class ArtifactIndex203Test {
    @Test fun reindexingAReplyReplacesItsTruncatedCardAndPreservesOtherRepliesAndProfiles() {
        val dir=Files.createTempDirectory("hermes-index-203")
        val c=DesktopController(true,SecureConfigStore(dir){ByteArray(32){16}},autoConnect=false)
        try {
            val s=HermesSession("s","原对话",profile="work",workspacePath="/root/workspace")
            val old=RecentArtifact("work","s",s.title,"m1","/活动/项目.md","项目.md","Markdown",s.workspacePath)
            val otherReply=old.copy(messageId="m2",path="/work/other.md",name="other.md")
            val otherProfile=old.copy(profile="personal")
            c.recentArtifacts.clear();c.recentArtifacts.addAll(listOf(old,otherReply,otherProfile))
            c.indexArtifacts(s,listOf(ChatMessage(id="m1",role=MessageRole.ASSISTANT,content="背景已写进 `03_主题/活动/项目.md`")))
            assertEquals(3,c.recentArtifacts.size)
            assertTrue(c.recentArtifacts.contains(otherReply));assertTrue(c.recentArtifacts.contains(otherProfile))
            assertEquals("03_主题/活动/项目.md",c.recentArtifacts.single {it.profile=="work"&&it.messageId=="m1"}.path)
        }finally {c.close();dir.toFile().deleteRecursively()}
    }
}
