package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.util.Comparator

class DesktopLayoutStateTest {
    @Test fun browsingAndHidingFilesPreserveTheChatDraftAndOpenDocument() {
        val c=DesktopController(true)
        try {
            val session=c.currentSession!!;val doc=c.document!!
            c.page=Page.CHAT;c.setDraft(session.scopedId,"尚未发送的内容")
            c.setDocumentText(doc,"本机修改")
            c.browse("/another/folder",navigate=false)
            c.chooseProject(c.projects.last(),navigate=false)
            c.setFilesPanel(false);c.setFilesPanel(true)
            assertEquals(Page.CHAT,c.page);assertEquals(session,c.currentSession)
            assertEquals("尚未发送的内容",c.drafts[session.scopedId])
            assertEquals(doc,c.document);assertEquals("本机修改",c.documentText())
            // The file entry on a non-work page returns to chat, keeping both drafts.
            c.navigate(Page.PROFILE);c.setFilesPanel(false);c.toggleFileBrowser()
            assertEquals(Page.CHAT,c.page);assertTrue(c.filesPanelOpen)
            assertEquals(session,c.currentSession);assertEquals("尚未发送的内容",c.drafts[session.scopedId])
            assertEquals(doc,c.document);assertEquals("本机修改",c.documentText())
            c.toggleFileBrowser();assertFalse(c.filesPanelOpen)
            c.currentSession=null;c.navigate(Page.HOME);c.toggleFileBrowser()
            assertEquals(Page.FILES,c.page);assertTrue(c.filesPanelOpen)
        } finally {c.close()}
    }
    @Test fun openingAnotherDocumentWaitsForTheRichEditorAndRetainsItsUnsavedText() {
        val c=DesktopController(true)
        try {
            val old=c.document!!;c.page=Page.FILES;c.editing=true
            val next=DocumentTab(WorkspaceDocument("next.md","/next.md","text/markdown","next"),c.profile)
            var complete:(()->Unit)?=null
            c.flushEditor={done->complete={c.setDocumentText(old,"刚输入的富文本");done()}}
            c.selectDocument(next)
            assertEquals(old,c.document)
            complete!!.invoke()
            assertEquals(next,c.document);assertEquals(Page.FILES,c.page)
            assertEquals("刚输入的富文本",c.edits[c.documentKey(old)])
            assertTrue(c.openDocuments.containsKey(c.documentKey(old)))
        } finally {c.close()}
    }
    @Test fun returningToChatWaitsForTheEditorFlush() {
        val c=DesktopController(true)
        try {
            c.page=Page.FILES
            var complete:(()->Unit)?=null
            c.flushEditor={complete=it}
            c.navigate(Page.CHAT)
            assertEquals(Page.FILES,c.page)
            complete!!.invoke()
            assertEquals(Page.CHAT,c.page)
            assertNotNull(c.document)
        } finally {c.close()}
    }
    @Test fun theFilePanelVisibilitySurvivesRestart() {
        val dir=Files.createTempDirectory("hermes-layout-test");val key=ByteArray(32){9}
        try {
            val first=DesktopController(false,SecureConfigStore(dir){key},autoConnect=false)
            first.setFilesPanel(false);first.close()
            val second=DesktopController(false,SecureConfigStore(dir){key},autoConnect=false)
            try {assertFalse(second.filesPanelOpen);assertNull(second.error)}finally {second.close()}
        } finally {Files.walk(dir).use {it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}}
    }
}
