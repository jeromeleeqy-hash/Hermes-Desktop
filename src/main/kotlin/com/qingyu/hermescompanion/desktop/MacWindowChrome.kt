package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qingyu.hermescompanion.platform.DesktopHost
import com.sun.jna.Callback
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.Timer

internal val LocalMacWindowDragArea = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

/** Native controls occupy only the sidebar; the content panels extend to the top of the window. */
internal object MacWindowChrome {
    const val SIDEBAR_WIDTH = 80
    const val CONTROLS_HEIGHT = 46

    fun configure(root:JRootPane) {
        root.putClientProperty("apple.awt.fullWindowContent",true)
        root.putClientProperty("apple.awt.transparentTitleBar",true)
        root.putClientProperty("apple.awt.windowTitleVisible",false)
    }

    fun install(window:JFrame):AutoCloseable {
        if(!DesktopHost.isMac||window.isUndecorated)return AutoCloseable{}
        configure(window.rootPane)
        val closed=java.util.concurrent.atomic.AtomicBoolean(false)
        val identifier="com.qingyu.hermes.main."+java.util.UUID.randomUUID()
        // AppKit relays out its titlebar after resize, activation and full-screen transitions.
        // Debounce those events, then do all NSView work on the AppKit main queue, never the EDT.
        val timer=Timer(120,null).apply {isRepeats=false}
        timer.addActionListener {
            if(!closed.get()&&window.isShowing)runCatching {
                val title=window.title
                MacAppKit.enqueue {
                    if(!closed.get()&&window.isDisplayable)MacAppKit.positionTrafficLights(title,identifier)
                }
            }.onFailure {System.err.println("Hermes: native window layout: ${it.message}")}
        }
        val component=object:ComponentAdapter() {
            override fun componentShown(e:ComponentEvent){timer.restart()}
            override fun componentResized(e:ComponentEvent){timer.restart()}
        }
        val listener=object:WindowAdapter() {
            override fun windowActivated(e:WindowEvent){timer.restart()}
            override fun windowStateChanged(e:WindowEvent){timer.restart()}
        }
        window.addComponentListener(component);window.addWindowListener(listener);window.addWindowStateListener(listener)
        timer.start()
        return AutoCloseable {
            closed.set(true);timer.stop();window.removeComponentListener(component)
            window.removeWindowListener(listener);window.removeWindowStateListener(listener)
        }
    }
}

/** Public Cocoa geometry uses logical points, independently of Retina scale. */
@Structure.FieldOrder("x","y","width","height")
internal class CocoaRect:Structure(),Structure.ByValue {
    @JvmField var x=0.0
    @JvmField var y=0.0
    @JvmField var width=0.0
    @JvmField var height=0.0
}

@Structure.FieldOrder("x","y")
internal class CocoaPoint:Structure(),Structure.ByValue {
    @JvmField var x=0.0
    @JvmField var y=0.0
}

internal data class MacTrafficLightGeometry(val containerHeight:Double,val containerY:Double,val buttonY:Double,val x:List<Double>)
internal fun macTrafficLightGeometry(windowHeight:Double,buttonWidth:Double,buttonHeight:Double,spacing:Double):MacTrafficLightGeometry {
    val height=maxOf(MacWindowChrome.CONTROLS_HEIGHT.toDouble(),buttonHeight+20.0)
    return MacTrafficLightGeometry(height,windowHeight-height,(height-buttonHeight)/2,List(3){12.0+it*spacing.coerceAtLeast(buttonWidth+4.0)})
}

internal interface AppKitJob:Callback {fun invoke(context:Pointer?)}

/** Lazy so Windows/Linux never load Cocoa. Strong callback ownership lasts for the process. */
private object MacAppKit {
    private val objc=NativeLibrary.getInstance("objc")
    private val system=NativeLibrary.getInstance("System")
    private val send=objc.getFunction("objc_msgSend")
    private val selectors=ConcurrentHashMap<String,Pointer>()
    private fun selector(name:String)=selectors.computeIfAbsent(name){objc.getFunction("sel_registerName").invokePointer(arrayOf(it))}
    private fun pointer(target:Pointer,name:String,vararg args:Any):Pointer?=send.invokePointer(arrayOf(target,selector(name),*args))
    private fun rect(target:Pointer)=send.invoke(CocoaRect::class.java,arrayOf(target,selector("frame"))) as CocoaRect
    private fun call(target:Pointer,name:String,vararg args:Any){send.invokeVoid(arrayOf(target,selector(name),*args))}
    private fun type(name:String)=objc.getFunction("objc_getClass").invokePointer(arrayOf(name))
    private fun text(value:Pointer?):String?=value?.let {pointer(it,"UTF8String")?.getString(0,"UTF-8")}
    private val jobs=ConcurrentHashMap<Long,()->Unit>()
    private val sequence=AtomicLong()
    private val callback=object:AppKitJob {
        override fun invoke(context:Pointer?) {
            val job=jobs.remove(Pointer.nativeValue(context))?:return
            runCatching {
                val pool=pointer(pointer(type("NSAutoreleasePool"),"alloc")!!,"init")!!
                try {job()} finally {call(pool,"drain")}
            }.onFailure {System.err.println("Hermes: AppKit window layout: ${it.message}")}
        }
    }
    fun enqueue(action:()->Unit) {
        val id=sequence.incrementAndGet();jobs[id]=action
        try {system.getFunction("dispatch_async_f").invokeVoid(arrayOf(system.getGlobalVariableAddress("_dispatch_main_q"),Pointer(id),callback))}
        catch(e:Throwable){jobs.remove(id);throw e}
    }
    private fun findWindow(title:String,identifier:String):Pointer? {
        // Enumerate on AppKit's queue: no JAWT NSView pointer escapes its drawing-surface lock.
        // Claim only the one titled, full-content main window; never select an ambiguous dialog.
        val app=pointer(type("NSApplication"),"sharedApplication")?:return null
        val windows=pointer(app,"windows")?:return null
        val count=send.invokeLong(arrayOf(windows,selector("count")))
        val candidates=mutableListOf<Pointer>()
        for(i in 0L until count) {
            val window=pointer(windows,"objectAtIndex:",i)?:continue
            val id=text(pointer(window,"identifier"))
            if(id==identifier)return window
            val style=send.invokeLong(arrayOf(window,selector("styleMask")))
            if(id==null&&style and 1L!=0L&&style and (1L shl 15)!=0L&&text(pointer(window,"title"))==title)candidates+=window
        }
        return candidates.singleOrNull()?.also {window->
            call(window,"setIdentifier:",pointer(type("NSString"),"stringWithUTF8String:",identifier)!!)
        }
    }
    fun positionTrafficLights(title:String,identifier:String) {
        // Structure return ABI below is Apple Silicon; other JVMs keep the normal native position.
        if(System.getProperty("os.arch")!="aarch64")return
        val window=findWindow(title,identifier)?:return
        val style=send.invokeLong(arrayOf(window,selector("styleMask")))
        if(style and (1L shl 14)!=0L)return // Leave full-screen menu-bar controls entirely to macOS.
        val buttons=(0L..2L).map {pointer(window,"standardWindowButton:",it)?:return}
        val parent=pointer(buttons[0],"superview")?:return
        val container=pointer(parent,"superview")?:return
        val first=rect(buttons[0]);val second=rect(buttons[1])
        val layout=macTrafficLightGeometry(rect(window).height,first.width,first.height,second.x-first.x)
        val bounds=rect(container).apply {height=layout.containerHeight;y=layout.containerY}
        call(container,"setFrame:",bounds)
        buttons.forEachIndexed {i,button->
            call(button,"setFrameOrigin:",CocoaPoint().apply {x=layout.x[i];y=layout.buttonY})
        }
    }
}
