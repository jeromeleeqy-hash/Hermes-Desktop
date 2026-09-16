@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.qingyu.hermescompanion.platform.DesktopHost
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.*
import java.awt.event.*
import java.awt.geom.RoundRectangle2D
import javax.swing.SwingUtilities

internal class CaptionClickTracker(private val interval:Long=500,private val distance:Float=6f) {
    private var previousTime:Long?=null
    private var previousPosition=Offset.Zero
    fun release(time:Long,position:Offset,dragged:Boolean):Boolean {
        if(dragged){previousTime=null;return false}
        val previous=previousTime
        if(previous!=null&&time-previous in 1..interval&&(position-previousPosition).getDistance()<=distance) {
            previousTime=null;return true
        }
        previousTime=time;previousPosition=position;return false
    }
}

/** Work-area coordinates are relative to the monitor, including monitors left/above the primary. */
internal fun maximizeGeometry(monitor:Rectangle,work:Rectangle):Rectangle = Rectangle(
    work.x-monitor.x,work.y-monitor.y,work.width.coerceAtLeast(1),work.height.coerceAtLeast(1))

/** Observe the native mouse stream: Compose local coordinates change while the window is moving. */
internal class CaptionDragHandler(
    private val window:Frame,private val caption:()->Rectangle,private val maximized:()->Boolean,
    private val restore:()->Unit,private val toggle:()->Unit,
):AWTEventListener,AutoCloseable {
    private var down:Point?=null
    private var origin=Point()
    private var moved=false
    private var restoreBounds=window.bounds
    private val interval=(Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int)?.toLong()?:500L
    private val clicks=CaptionClickTracker(interval,6f)
    private val component=object:ComponentAdapter(){override fun componentResized(e:ComponentEvent){if(!maximized())restoreBounds=window.bounds}}
    init {window.addComponentListener(component);Toolkit.getDefaultToolkit().addAWTEventListener(this,AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)}
    override fun eventDispatched(event:AWTEvent) {
        val e=event as? MouseEvent?:return
        val target=e.component?:return
        if((target as? Window ?: SwingUtilities.getWindowAncestor(target))!==window)return
        when(e.id) {
            MouseEvent.MOUSE_PRESSED -> if(e.button==MouseEvent.BUTTON1&&caption().contains(SwingUtilities.convertPoint(target,e.point,window))) {
                down=e.locationOnScreen;origin=window.location;moved=false
                if(!maximized())restoreBounds=window.bounds
            }
            MouseEvent.MOUSE_DRAGGED -> {
                val start=down?:return;val now=e.locationOnScreen
                if(!moved&&start.distance(now)<5)return
                if(!moved&&maximized()) {
                    val ratio=((start.x-window.x).toDouble()/window.width.coerceAtLeast(1)).coerceIn(0.05,.95)
                    val size=Dimension(restoreBounds.width.coerceAtLeast(window.minimumSize.width),restoreBounds.height.coerceAtLeast(window.minimumSize.height))
                    restore();window.extendedState=Frame.NORMAL
                    origin=Point(now.x-(size.width*ratio).toInt(),now.y-caption().height/2)
                    window.bounds=Rectangle(origin,size);down=now
                } else window.setLocation(origin.x+now.x-start.x,origin.y+now.y-start.y)
                moved=true
            }
            MouseEvent.MOUSE_RELEASED -> if(e.button==MouseEvent.BUTTON1&&down!=null) {
                down=null
                if(clicks.release(e.`when`,Offset(e.xOnScreen.toFloat(),e.yOnScreen.toFloat()),moved))toggle()
            }
        }
    }
    override fun close(){Toolkit.getDefaultToolkit().removeAWTEventListener(this);window.removeComponentListener(component)}
}

@Composable internal fun CaptionGestureArea(window:Frame,state:WindowState,modifier:Modifier,onDoubleClick:()->Unit) {
    val toggle by rememberUpdatedState(onDoubleClick)
    val scale=LocalDensity.current.density
    var bounds by remember {mutableStateOf(Rectangle())}
    DisposableEffect(window) {
        val handler=CaptionDragHandler(window,{bounds},{state.placement==WindowPlacement.Maximized},
            {state.placement=WindowPlacement.Floating},{toggle()})
        onDispose {handler.close()}
    }
    Box(modifier.onGloballyPositioned {coordinates->val r=coordinates.boundsInWindow();bounds=Rectangle((r.left/scale).toInt(),(r.top/scale).toInt(),(r.width/scale).toInt(),(r.height/scale).toInt())})
}

private interface DwmApi:StdCallLibrary {fun DwmSetWindowAttribute(hwnd:HWND,attribute:Int,value:Pointer,size:Int):Int}
private interface NativeWindowProc:StdCallLibrary.StdCallCallback {fun invoke(hwnd:HWND,msg:Int,w:WPARAM,l:LPARAM):LRESULT}
private interface FrameUserApi:StdCallLibrary {
    fun SetWindowLongPtrW(hwnd:HWND,index:Int,value:Pointer):Pointer?
    fun GetWindowLongPtrW(hwnd:HWND,index:Int):Pointer?
    fun CallWindowProcW(previous:Pointer,hwnd:HWND,msg:Int,w:WPARAM,l:LPARAM):LRESULT
}

internal object WindowsWindowFrame {
    private val dwm by lazy {runCatching {Native.load("dwmapi",DwmApi::class.java)}.getOrNull()}
    private val user by lazy {Native.load("user32",FrameUserApi::class.java)}
    private fun handle(window:Window)=HWND(Native.getWindowPointer(window))
    // Strong references must outlive the HWND. Never let a native callback be garbage-collected.
    private val hooks=java.util.concurrent.ConcurrentHashMap<Long,NativeWindowProc>()
    fun installWorkArea(window:Frame):AutoCloseable {
        refreshWorkArea(window)
        if(!DesktopHost.isWindows||!window.isDisplayable)return AutoCloseable{}
        val hwnd=handle(window);val id=Pointer.nativeValue(hwnd.pointer)
        if(hooks.containsKey(id))return AutoCloseable{}
        var previous:Pointer?=null
        val callback=object:NativeWindowProc {
            override fun invoke(h:HWND,msg:Int,w:WPARAM,l:LPARAM):LRESULT {
                val result=previous?.let {user.CallWindowProcW(it,h,msg,w,l)}?:User32.INSTANCE.DefWindowProc(h,msg,w,l)
                if(msg==0x0024&&l.toLong()!=0L)runCatching { // WM_GETMINMAXINFO
                    val monitor=User32.INSTANCE.MonitorFromWindow(h,WinUser.MONITOR_DEFAULTTONEAREST)
                    val info=WinUser.MONITORINFO()
                    if(User32.INSTANCE.GetMonitorInfo(monitor,info).booleanValue()) {
                        val m=info.rcMonitor;val a=info.rcWork
                        val rect=maximizeGeometry(Rectangle(m.left,m.top,m.right-m.left,m.bottom-m.top),Rectangle(a.left,a.top,a.right-a.left,a.bottom-a.top))
                        val data=Pointer(l.toLong())
                        data.setInt(8,rect.width);data.setInt(12,rect.height) // ptMaxSize
                        data.setInt(16,rect.x);data.setInt(20,rect.y) // ptMaxPosition
                    }
                }
                if(msg==0x0082)hooks.remove(id) // WM_NCDESTROY, after the original procedure
                return result
            }
        }
        val pointer=com.sun.jna.CallbackReference.getFunctionPointer(callback)
        previous=user.SetWindowLongPtrW(hwnd,-4,pointer)
        check(previous!=null){"无法设置窗口边界"}
        hooks[id]=callback
        return AutoCloseable {
            if(window.isDisplayable&&user.GetWindowLongPtrW(hwnd,-4)==pointer)previous?.let {user.SetWindowLongPtrW(hwnd,-4,it)}
            hooks.remove(id)
        }
    }
    fun refreshWorkArea(window:Frame) {
        val gc=window.graphicsConfiguration?:return
        val screen=gc.bounds;val insets=Toolkit.getDefaultToolkit().getScreenInsets(gc)
        window.maximizedBounds=Rectangle(screen.x+insets.left,screen.y+insets.top,
            screen.width-insets.left-insets.right,screen.height-insets.top-insets.bottom)
    }
    fun updateCorners(window:Window,maximized:Boolean) {
        if(!DesktopHost.isWindows||!window.isDisplayable)return
        val preference=IntByReference(if(maximized)1 else 2)
        val applied=runCatching {dwm?.DwmSetWindowAttribute(handle(window),33,preference.pointer,4)==0}.getOrDefault(false)
        if(applied){runCatching {window.shape=null};return}
        runCatching {
            if(window.graphicsConfiguration.device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))
                window.shape=if(maximized)null else RoundRectangle2D.Double(0.0,0.0,window.width.toDouble(),window.height.toDouble(),16.0,16.0)
        }
    }
}

@Composable internal fun RememberWindowCorners(window:Frame,state:WindowState) {
    val maximized=state.placement!=WindowPlacement.Floating
    val latestMaximized by rememberUpdatedState(maximized)
    LaunchedEffect(maximized){WindowsWindowFrame.updateCorners(window,maximized)}
    DisposableEffect(window) {
        var hook:AutoCloseable?=null
        fun attach(){if(hook==null&&window.isDisplayable)hook=runCatching {WindowsWindowFrame.installWorkArea(window)}.getOrNull()}
        val listener=object:ComponentAdapter() {
            override fun componentMoved(e:ComponentEvent){if(!latestMaximized)WindowsWindowFrame.refreshWorkArea(window)}
            override fun componentResized(e:ComponentEvent){WindowsWindowFrame.updateCorners(window,latestMaximized)}
            override fun componentShown(e:ComponentEvent){attach();WindowsWindowFrame.updateCorners(window,latestMaximized)}
        }
        attach();window.addComponentListener(listener)
        onDispose {window.removeComponentListener(listener);runCatching {hook?.close()}}
    }
}
