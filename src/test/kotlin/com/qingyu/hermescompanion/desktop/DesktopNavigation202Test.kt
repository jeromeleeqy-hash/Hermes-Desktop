@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.today.TodayBoard
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class DesktopNavigation202Test {
    @Test fun workbenchAttentionModuleOpensAndReturnsAndSidebarShowsEightConversations()=runBlocking<Unit>(Dispatchers.Swing){
        val c=DesktopController(true);c.reduceMotion=true;c.page=Page.HOME;c.sidebarCollapsed=false;c.project=null;setDesktopLanguage("zh")
        c.sessions=(1..10).map {HermesSession("s$it","会话 $it",isPinned=it==10,profile=c.profile)}
        c.today.preview(TodayBoard.decode(File("src/main/resources/today/hermes-today-examples.json").readText(),"/work"),false)
        val scene=ImageComposeScene(1280,1000,coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopBackdrop{DesktopFrame{DesktopWorkspace(c)}}}}
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        fun nodes()=scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
        fun tagged(tag:String)=nodes().filter {it.config.getOrNull(SemanticsProperties.TestTag)==tag}
        suspend fun settle(){repeat(5){scene.render(System.nanoTime()).close();delay(30)}}
        fun click(tag:String){val p=tagged(tag).single().boundsInRoot.center;scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press,p,button=androidx.compose.ui.input.pointer.PointerButton.Primary);scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release,p,button=androidx.compose.ui.input.pointer.PointerButton.Primary)}
        try {
            settle();assertFalse(c.today.attentionOpen);assertEquals(1,tagged("attention-module").size)
            assertEquals(8,nodes().count {it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("recent-shortcut:")==true})
            assertTrue(nodes().none {it.config.getOrNull(SemanticsProperties.Text)?.any {text->text.text in listOf("简洁首页","深入首页")}==true})
            click("open-attention");settle();assertTrue(c.today.attentionOpen);assertEquals(1,tagged("back-to-workbench").size)
            click("back-to-workbench");settle();assertFalse(c.today.attentionOpen);assertEquals(1,tagged("home-composer").size)
        }finally{scene.close();c.close()}
    }
    @Test fun unreadCountMatchesCurrentWorkspaceProjectAndVisibleConversations()=runBlocking<Unit>(Dispatchers.Swing){
        val c=DesktopController(true);c.project=null
        try {
            val visible=HermesSession("visible","普通会话",profile=c.profile)
            val background=HermesSession("background","后台整理",profile=c.profile)
            val other=HermesSession("other","其他空间",profile="other")
            val cron=HermesSession("cron","定时任务",source="cron",profile=c.profile)
            c.sessions=listOf(visible,background,other,cron);c.today.background[background.scopedId]="test"
            c.unread.clear();c.unread.addAll(listOf(visible.scopedId,background.scopedId,other.scopedId,cron.scopedId,"default::deleted"))
            assertEquals(setOf(visible.scopedId),c.conversationUnread)
            c.reconcileUnreadSessions(listOf(visible,background,cron),emptyMap())
            assertFalse("default::deleted" in c.unread);assertTrue(other.scopedId in c.unread)
            c.readMessageCounts[visible.scopedId]=10;c.unread.remove(visible.scopedId)
            c.reconcileUnreadSessions(listOf(visible.copy(messageCount=12),background,cron),mapOf(visible.scopedId to visible.copy(messageCount=10)))
            assertEquals(setOf(visible.scopedId),c.conversationUnread)
        }finally{c.close()}
    }
    @Test fun notificationRouteUsesConversationIdentityAndRejectsAnotherAccount()=runBlocking<Unit>(Dispatchers.Swing){
        val c=DesktopController(true)
        try {
            c.page=Page.HOME
            val s=HermesSession("mobile-daily","日常助理",profile=c.profile)
            val target=c.notificationTarget(s)
            assertEquals(target,DesktopNotificationTarget.decode(target.encode()))
            c.openNotification(target);assertEquals(Page.CHAT,c.page);assertEquals(s.id,c.currentSession?.id)
            c.navigate(Page.HOME);c.openNotification(target.copy(account="other-account"));assertEquals(Page.HOME,c.page)
            assertNull(DesktopNotificationTarget.decode("not a target"))
        }finally{c.close()}
    }
}
