package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import org.junit.Test
import org.junit.Assert.*

class WorkbenchTest {
    @Test fun homeDraftAndAttachmentsTransferTogetherWithoutChangingAnotherSession(){
        val c=DesktopController(true)
        try {
            c.page=Page.HOME;val previous=c.currentSession!!;c.setDraft(previous.scopedId,"原来的草稿")
            val key=c.homeDraftKey();c.setDraft(key,"分析这个文件")
            val a=PendingAttachment("file-1","notes.txt","text/plain",textContent="notes")
            c.attachments[key]=listOf(a);c.startFromHome()
            val opened=c.currentSession!!
            assertNotEquals(previous.scopedId,opened.scopedId);assertEquals(Page.CHAT,c.page)
            assertEquals("分析这个文件",c.drafts[opened.scopedId]);assertEquals(listOf(a),c.attachments[opened.scopedId])
            assertTrue(c.drafts[key].isNullOrEmpty());assertTrue(c.attachments[key].isNullOrEmpty())
            assertEquals("原来的草稿",c.drafts[previous.scopedId])
        } finally {c.close()}
    }
    @Test fun templatesAppendAndHomeDraftsAreIsolatedByProjectAndProfile(){
        val c=DesktopController(true)
        try {
            val key=c.homeDraftKey();c.setDraft(key,"我已经写了一半");c.addHomePrompt("请分析文件")
            assertEquals("我已经写了一半\n\n请分析文件",c.drafts[key])
            c.project=null;assertNotEquals(key,c.homeDraftKey())
            val withoutProject=c.homeDraftKey();c.profile="another";assertNotEquals(withoutProject,c.homeDraftKey())
            assertEquals("我已经写了一半\n\n请分析文件",c.drafts[key])
        } finally {c.close()}
    }
    @Test fun preparingAttachmentsBlocksPrematureSubmission(){
        val c=DesktopController(true)
        try {
            val previous=c.currentSession;val key=c.homeDraftKey();c.setDraft(key,"有附件的任务");c.attachmentLoads[key]=1
            c.startFromHome();assertEquals(previous,c.currentSession);assertEquals("有附件的任务",c.drafts[key])
        } finally {c.close()}
    }
    @Test fun quickLookupFiltersCurrentWorkspaceAndKeepsDocumentContext(){
        val c=DesktopController(true)
        try {
            val session=c.currentSession!!;val doc=c.document
            c.sessions=listOf(session.copy(title="独特的项目名称"))
            val result=quickEntries(c,"独特的项目名称").single {it.id.startsWith("session:")}
            result.action();assertEquals(session.scopedId,c.currentSession?.scopedId);assertEquals(doc,c.document)
            assertTrue(quickEntries(c,"不会匹配的词").any {it.id=="search"})
        } finally {c.close()}
    }
}
