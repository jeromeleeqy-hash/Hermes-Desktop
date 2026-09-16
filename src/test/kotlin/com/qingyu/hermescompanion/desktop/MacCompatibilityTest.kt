package com.qingyu.hermescompanion.desktop

import java.awt.Canvas
import java.awt.Desktop
import java.awt.desktop.*
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor

class MacCompatibilityTest {
    @Test fun dockReopenAndNativeQuitUseQueuedAppActionsAndDisposeSuppressesStaleEvents() {
        val desktop=mock(Desktop::class.java)
        `when`(desktop.isSupported(any(Desktop.Action::class.java))).thenReturn(true)
        val queue=mutableListOf<()->Unit>();val actions=mutableListOf<String>()
        val hooks=MacApplicationHooks(desktop,{actions+="open"},{actions+="quit"},{actions+="settings"},{actions+="about"},{actions+="background"},{queue+=it})
        assertTrue(hooks.canReopen)
        val listeners=ArgumentCaptor.forClass(SystemEventListener::class.java)
        verify(desktop,times(2)).addAppEventListener(listeners.capture())
        val reopen=listeners.allValues.filterIsInstance<AppReopenedListener>().single()
        reopen.appReopened(mock(AppReopenedEvent::class.java))
        assertTrue(actions.isEmpty());queue.removeAt(0).invoke();assertEquals(listOf("open"),actions)
        val quit=ArgumentCaptor.forClass(QuitHandler::class.java)
        verify(desktop).setQuitHandler(quit.capture())
        val response=mock(QuitResponse::class.java)
        quit.value.handleQuitRequestWith(mock(QuitEvent::class.java),response)
        verify(response).cancelQuit();verify(response,never()).performQuit()
        queue.removeAt(0).invoke();assertEquals(listOf("open","quit"),actions)
        reopen.appReopened(mock(AppReopenedEvent::class.java));hooks.close();hooks.close()
        queue.removeAt(0).invoke();assertEquals(listOf("open","quit"),actions)
        listeners.allValues.forEach {verify(desktop).removeAppEventListener(it)}
        verify(desktop).setQuitHandler(null)
    }
    @Test fun unsupportedDockReopenDoesNotPromiseAHiddenWindowRecoveryPath() {
        val desktop=mock(Desktop::class.java)
        val hooks=MacApplicationHooks(desktop,{},{},{},{},{})
        assertFalse(hooks.canReopen);hooks.close()
        verify(desktop,never()).addAppEventListener(any(SystemEventListener::class.java))
    }
    @Test fun screenshotPermissionIsRequestedOnlyWhenNeededAndDenialStopsCapture() {
        var requests=0
        requireScreenAccess({true},{requests++;false});assertEquals(0,requests)
        requireScreenAccess({false},{requests++;true});assertEquals(1,requests)
        val failure=runCatching {requireScreenAccess({false},{requests++;false})}.exceptionOrNull()
        assertTrue(failure is IllegalStateException);assertTrue(failure!!.message!!.contains("录制屏幕"));assertEquals(2,requests)
    }
    @Test fun macControlClickIsContextMenuAndNeverStartsTheLeftButtonVoiceGesture() {
        fun event(mod:Int,button:Int,popup:Boolean=false)=MouseEvent(Canvas(),MouseEvent.MOUSE_PRESSED,1,mod,10,10,1,popup,button)
        val control=event(InputEvent.CTRL_DOWN_MASK,MouseEvent.BUTTON1)
        assertTrue(isFloatingContextClick(control,mac=true));assertFalse(isFloatingContextClick(control,mac=false))
        assertFalse(isFloatingContextClick(event(0,MouseEvent.BUTTON1),mac=true))
        assertTrue(isFloatingContextClick(event(0,MouseEvent.BUTTON3),mac=true))
        assertTrue(isFloatingContextClick(event(0,MouseEvent.BUTTON1,true),mac=true))
    }
}
