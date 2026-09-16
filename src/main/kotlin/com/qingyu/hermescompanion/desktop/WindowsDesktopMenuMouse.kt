package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.platform.DesktopHost
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingUtilities

/** One open menu owns one signal. Closing it invalidates even an already queued EDT callback. */
internal class MenuDismissSignal(private val dispatch:((()->Unit)->Unit),private val dismiss:()->Unit):AutoCloseable {
    private val closed=AtomicBoolean(false)
    private val queued=AtomicBoolean(false)
    fun outsidePress() {
        if(!closed.get()&&queued.compareAndSet(false,true))dispatch {if(!closed.get())dismiss()}
    }
    override fun close(){closed.set(true)}
}

private interface PhysicalMouseUser32:StdCallLibrary {
    fun WindowFromPhysicalPoint(point:POINT.ByValue):HWND?
}

/** AWT sees only our JVM's mouse events; focus loss misses non-activating windows and the shell.
 * Observe Windows presses only while a desktop menu is open. Always pass input to its target.
 * https://learn.microsoft.com/en-us/windows/win32/winmsg/lowlevelmouseproc
 */
internal object WindowsDesktopMenuMouse {
    private val log=Logger.getLogger("Hermes.DesktopMenus")
    fun watch(window:Window,onOutsidePress:()->Unit):AutoCloseable {
        if(!DesktopHost.isWindows||!window.isDisplayable)return AutoCloseable{}
        return runCatching {
            Watch(HWND(Native.getWindowPointer(window)),onOutsidePress)
        }.getOrElse {log.log(Level.WARNING,"无法启动桌面菜单鼠标监听",it);AutoCloseable{}}
    }

    private class Watch(private val menu:HWND,onOutsidePress:()->Unit):AutoCloseable {
        private val closed=AtomicBoolean(false)
        private val signal=MenuDismissSignal({task->SwingUtilities.invokeLater(task)},onOutsidePress)
        private val lifecycle=Any()
        private var threadId=0

        init {
            Thread(::listen,"Hermes-menu-mouse").apply {isDaemon=true;start()}
        }

        private fun listen() {
            var hook:WinUser.HHOOK?=null
            var callback:WinUser.LowLevelMouseProc?=null
            try {
                val user=User32.INSTANCE
                val physical=Native.load("user32",PhysicalMouseUser32::class.java)
                val message=WinUser.MSG()
                // Create the queue before publishing the ID: close() may race with startup.
                user.PeekMessage(message,null,0,0,0)
                synchronized(lifecycle) {
                    if(closed.get())return
                    threadId=Kernel32.INSTANCE.GetCurrentThreadId()
                }
                callback=WinUser.LowLevelMouseProc {code,event,data->
                    try {
                        if(code>=0&&event.toInt()==0x0201&&!closed.get()) { // WM_LBUTTONDOWN
                            // Low-level hook points are physical pixels. Native hit testing avoids
                            // AWT logical-coordinate errors on 125–200% and mixed-DPI monitors.
                            val point=POINT.ByValue().apply {x=data.pt.x;y=data.pt.y}
                            val target=physical.WindowFromPhysicalPoint(point)
                            val root=target?.let {user.GetAncestor(it,WinUser.GA_ROOT)}
                            if(root!=menu)signal.outsidePress()
                        }
                    }catch(e:Exception) {
                        log.log(Level.WARNING,"桌面菜单鼠标事件处理失败",e)
                    }
                    // Do not eat the click: the desktop/other app must receive the same press.
                    user.CallNextHookEx(null,code,event,LPARAM(Pointer.nativeValue(data.pointer)))
                }
                if(closed.get())return
                hook=user.SetWindowsHookEx(WinUser.WH_MOUSE_LL,callback,Kernel32.INSTANCE.GetModuleHandle(null),0)
                check(hook!=null){"SetWindowsHookEx failed: ${Native.getLastError()}"}
                while(!closed.get()) {
                    val result=user.GetMessage(message,null,0,0)
                    if(result==0)break
                    check(result>0){"GetMessage failed: ${Native.getLastError()}"}
                    user.TranslateMessage(message);user.DispatchMessage(message)
                }
            }catch(e:Throwable) {
                log.log(Level.WARNING,"桌面菜单全局鼠标监听已停止",e)
            }finally {
                // The callback must stay strongly reachable until the native hook is removed.
                hook?.let {runCatching {User32.INSTANCE.UnhookWindowsHookEx(it)}}
                Reference.reachabilityFence(callback)
                synchronized(lifecycle){threadId=0}
            }
        }

        override fun close() {
            if(!closed.compareAndSet(false,true))return
            signal.close()
            // Wake GetMessage without blocking the UI or waiting for the hook thread to join.
            synchronized(lifecycle) {
                if(threadId!=0)User32.INSTANCE.PostThreadMessage(threadId,WinUser.WM_QUIT,WPARAM(0),LPARAM(0))
            }
        }
    }
}
