package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.awt.ComposeWindow
import com.qingyu.hermescompanion.model.HermesSession
import com.qingyu.hermescompanion.model.VoicePhase
import com.qingyu.hermescompanion.platform.DesktopHost
import kotlinx.coroutines.*
import java.awt.*
import java.awt.dnd.*
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.*

internal fun desktopWorkAreas():List<Rectangle> = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map {d->
    val c=d.defaultConfiguration;val b=Rectangle(c.bounds);val i=Toolkit.getDefaultToolkit().getScreenInsets(c)
    Rectangle(b.x+i.left,b.y+i.top,(b.width-i.left-i.right).coerceAtLeast(1),(b.height-i.top-i.bottom).coerceAtLeast(1))
}

internal fun isFloatingContextClick(event:MouseEvent,mac:Boolean=DesktopHost.isMac):Boolean =
    event.isPopupTrigger||SwingUtilities.isRightMouseButton(event)||(mac&&event.isControlDown&&SwingUtilities.isLeftMouseButton(event))

/** Native unowned windows survive hiding the main frame; all callbacks run on the AWT event thread. */
internal class DesktopFloatingHost(private val c:DesktopController):AutoCloseable {
    private val icon:BufferedImage=ImageIO.read(javaClass.getResource("/icon.png"))
    private val ball=JWindow().apply {background=Color(0,0,0,0);isAlwaysOnTop=true;focusableWindowState=false;setSize(84,84)}
    private val bubble=BallPanel()
    private var quick:ComposeWindow?=null
    private var selection:ScreenSelection?=null
    private var captureJob:Job?=null
    private var capturing=false
    private var disposed=false
    private fun contextActions()=listOf(
            DesktopMenuAction("ask","提问","history"){c.companion.open();sync()},
            DesktopMenuAction("capture","框选截图","capture"){c.companion.open(CompanionAction.CAPTURE);sync()},
            DesktopMenuAction("voice","连续语音","mic"){c.companion.open(CompanionAction.VOICE);sync()},
            DesktopMenuAction("open","打开主窗口","panel",dividerBefore=true){c.companion.showMain?.invoke()},
            DesktopMenuAction("hide-floating","关闭悬浮球","eye-off"){c.floatingAssistantEnabled=false;c.savePreference("floatingAssistantEnabled","false");sync()},
        )
    private val contextMenu=if(DesktopHost.isMac)null else DesktopMenuHost(c,"悬浮助手",::contextActions)
    private val macContextMenu=if(DesktopHost.isMac)MacFloatingMenu(::contextActions)else null
    private fun dismissContextMenu(){contextMenu?.dismiss();macContextMenu?.dismiss()}
    init {
        ball.contentPane=bubble
        val area=desktopWorkAreas().last()
        val initial=Point(c.store.get("floatingX",(area.x+area.width-110).toString()).toIntOrNull()?:area.x+area.width-110,c.store.get("floatingY",(area.y+area.height/2).toString()).toIntOrNull()?:area.y+area.height/2)
        ball.location=clampFloatingBounds(initial,84,84,desktopWorkAreas())
        c.companion.capture=::capture
    }
    fun sync() {
        if(disposed)return
        if(capturing&&!c.companion.panelOpen){captureJob?.cancel();captureJob=null;val active=selection;selection=null;capturing=false;active?.close()}
        if(!c.floatingAssistantEnabled) {
            dismissContextMenu()
            if(c.companion.panelOpen)c.companion.close()
            selection?.close();selection=null;captureJob?.cancel();capturing=false
            ball.isVisible=false;quick?.isVisible=false;bubble.stop();return
        }
        ball.isVisible=!capturing
        if(ball.isVisible)bubble.refresh()else bubble.stop()
        if(c.companion.panelOpen&&!capturing) {
            val panel=quick?:createQuick().also {quick=it}
            panel.focusableWindowState=!c.companion.holdingToTalk
            if(!panel.isVisible) {
                val areas=desktopWorkAreas();val area=areas.firstOrNull {it.contains(ball.location)}?:areas.first()
                panel.setSize(minOf(518,area.width),minOf(668,area.height))
                val x=if(ball.x-panel.width-6>=area.x)ball.x-panel.width-6 else ball.x+ball.width+6
                panel.location=clampFloatingBounds(Point(x,ball.y-panel.height/2+42),panel.width,panel.height,areas)
                panel.isVisible=true
                if(!c.companion.holdingToTalk){panel.toFront();panel.requestFocus()}
            }
        }else quick?.isVisible=false
    }
    private fun createQuick()=ComposeWindow().apply {
        isUndecorated=true;isTransparent=true;isResizable=false;isAlwaysOnTop=true;type=Window.Type.UTILITY
        title="Hermes · 随时问";iconImage=icon;defaultCloseOperation=WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object:WindowAdapter(){override fun windowClosing(e:WindowEvent){c.companion.close()}})
        setContent {HermesTheme(c){FloatingQuickPanel(c)}}
    }
    private fun capture(session:HermesSession) {
        if(capturing||disposed)return
        capturing=true;dismissContextMenu();ball.isVisible=false;quick?.isVisible=false;bubble.stop()
        val epoch=c.epoch
        captureJob=c.scope.launch {
            try {
                delay(160) // Let the desktop repaint after removing our two surfaces.
                val screens=withContext(Dispatchers.IO){captureDesktopScreens()}
                ensureActive();if(disposed)return@launch
                selection=ScreenSelection(screens){rectangle->
                    selection=null
                    if(rectangle==null){capturing=false;sync()}
                    else captureJob=c.scope.launch {
                        try {
                            val bytes=withContext(Dispatchers.Default){composeScreenSelection(screens,rectangle)}
                            if(epoch==c.epoch&&!disposed)c.companion.addCapture(session,bytes)
                        }catch(e:CancellationException){throw e}catch(e:Exception){c.companion.report(e.message?:"截图失败，请重试。")}
                        finally {capturing=false;sync()}
                    }
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){capturing=false;c.companion.report(e.message?:"无法截取屏幕，请检查系统权限。" );sync()}
        }
    }
    override fun close(){if(disposed)return;disposed=true;contextMenu?.close();macContextMenu?.close();captureJob?.cancel();selection?.close();selection=null;bubble.stop();ball.dispose();quick?.dispose();quick=null;c.companion.capture=null}

    private inner class BallPanel:JPanel() {
        private val gesture=FloatingGestures((Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int?:300).toLong().coerceIn(180,500))
        private var hover=false
        private var dropping=false
        private var pressed=false
        private var contextPressed=false
        private var dragging=false
        private var down=Point()
        private var origin=Point()
        private var emphasis=0f
        private var lastNanos=System.nanoTime()
        private val timer=Timer(16){tick()}
        init {
            isOpaque=false
            // A native tooltip can cover this unowned window and synthesize exit/enter events.
            accessibleContext.accessibleDescription="单击提问 · 双击截图 · 按住说话，松开发送 · 拖动移动"
            val listener=object:MouseAdapter() {
                override fun mouseEntered(e:MouseEvent){updateHover(pointerInside()?:true)}
                override fun mouseExited(e:MouseEvent){updateHover(pointerInside()?:false)}
                override fun mousePressed(e:MouseEvent) {
                    if(isFloatingContextClick(e)){gesture.cancel();c.companion.cancelHoldToTalk();pressed=false;contextPressed=true;return}
                    if(!SwingUtilities.isLeftMouseButton(e))return
                    pressed=true;dragging=false;down=e.locationOnScreen;origin=ball.location;gesture.press(down,e.`when`);refresh()
                }
                override fun mouseDragged(e:MouseEvent) {
                    if(!pressed)return
                    val point=e.locationOnScreen
                    if(gesture.move(point)==FloatingGestures.Result.DRAG) {
                        dragging=true;ball.location=clampFloatingBounds(Point(origin.x+point.x-down.x,origin.y+point.y-down.y),ball.width,ball.height,desktopWorkAreas())
                    }
                }
                override fun mouseReleased(e:MouseEvent) {
                    if(contextPressed||isFloatingContextClick(e)){contextPressed=false;menu(e);return}
                    if(!SwingUtilities.isLeftMouseButton(e))return
                    pressed=false;dispatch(gesture.release(e.locationOnScreen,e.`when`))
                    if(dragging){c.savePreference("floatingX",ball.x.toString());c.savePreference("floatingY",ball.y.toString())}
                    dragging=false;refresh()
                }
            }
            addMouseListener(listener);addMouseMotionListener(listener)
            dropTarget=DropTarget(this,DnDConstants.ACTION_COPY,object:DropTargetAdapter(){
                override fun dragEnter(e:DropTargetDragEvent){if(hasFileTransfer(e.transferable)){e.acceptDrag(DnDConstants.ACTION_COPY);dropping=true;refresh()}else e.rejectDrag()}
                override fun dragExit(e:DropTargetEvent){dropping=false;refresh()}
                override fun drop(e:DropTargetDropEvent) {
                    dropping=false;gesture.cancel();c.companion.cancelHoldToTalk()
                    try {
                        if(!hasFileTransfer(e.transferable)){e.rejectDrop();return}
                        e.acceptDrop(DnDConstants.ACTION_COPY);val files=transferredFiles(e.transferable);e.dropComplete(files.isNotEmpty())
                        if(files.isNotEmpty())SwingUtilities.invokeLater {c.companion.open(files=files);sync()}
                    }catch(_:Exception){c.companion.open();c.companion.report("文件没有添加成功，请重新拖入。")}
                    refresh()
                }
            })
        }
        private fun dispatch(action:FloatingGestures.Result) {
            when(action){
                FloatingGestures.Result.ASK->c.companion.open()
                FloatingGestures.Result.CAPTURE->c.companion.open(CompanionAction.CAPTURE)
                FloatingGestures.Result.VOICE->c.companion.beginHoldToTalk()
                FloatingGestures.Result.VOICE_RELEASE->c.companion.releaseHoldToTalk()
                else->return
            }
            sync()
        }
        // Use screen coordinates, not animated image alpha or synthetic native enter/exit.
        private fun pointerInside():Boolean?=try {
            MouseInfo.getPointerInfo()?.location?.let {ball.isVisible&&ball.bounds.contains(it)}
        }catch(_:SecurityException){null}
        private fun updateHover(value:Boolean){if(hover!=value){hover=value;refresh()}}
        fun stop(){timer.stop();gesture.cancel();c.companion.cancelHoldToTalk();pressed=false;hover=false;dropping=false;emphasis=0f}
        fun refresh(){timer.delay=16;if(!timer.isRunning){lastNanos=System.nanoTime();timer.start()};repaint()}
        private fun tick() {
            dispatch(gesture.tick(System.currentTimeMillis()))
            if(!ball.isVisible){stop();return}
            pointerInside()?.let {hover=it}
            val now=System.nanoTime();val dt=((now-lastNanos)/1e9).coerceAtMost(.05);lastNanos=now
            val target=if(hover||dropping||pressed)1f else 0f
            emphasis=if(c.reduceMotion)target else (emphasis+(target-emphasis)*(1-exp(-dt*18))).toFloat()
            repaint()
            val settled=!gesture.waiting&&abs(emphasis-target)<.003&&(!c.voice.active||c.reduceMotion)
            // Keep a cheap pointer check while hovered: some platforms omit the real exit
            // after sending a synthetic exit over a translucent surface.
            if(settled&&!hover)timer.stop()
            timer.delay=if(settled&&hover)80 else 16
        }
        override fun paintComponent(graphics:Graphics) {
            val g=graphics.create() as Graphics2D
            try {
                // Alpha zero is click-through on some native compositors. A constant nonzero
                // backing keeps the whole 84px input surface stable throughout the animation.
                g.color=Color(0,0,0,1);g.fillRect(0,0,width,height)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                val pulse=if(c.voice.active&&!c.reduceMotion)((sin(System.nanoTime()/1e9*3)+1)/2).toFloat()else 0f
                val size=(64+emphasis*3).toInt();val x=(width-size)/2;val y=(height-size)/2-(emphasis*2).toInt()
                g.color=Color(35,59,107,25);g.fillRoundRect(x-1,y+4,size+2,size,22,22)
                if(dropping||c.voice.active) {
                    g.color=if(c.voice.phase==VoicePhase.ERROR)Color(202,65,72,100)else Color(58,115,248,(55+pulse*45).toInt())
                    g.stroke=BasicStroke(2f+if(c.voice.active)c.voice.level*3f else 0f);g.drawRoundRect(x-4,y-4,size+8,size+8,26,26)
                }
                g.drawImage(icon,x,y,size,size,null)
                if(c.voice.active){g.color=if(c.voice.phase==VoicePhase.LISTENING)Color(20,168,127)else Color(50,105,240);g.fillOval(x+size-11,y+size-11,11,11)}
            }finally {g.dispose()}
        }
        private fun menu(e:MouseEvent) {
            gesture.cancel();c.companion.cancelHoldToTalk();pressed=false
            if(macContextMenu!=null)macContextMenu.show(bubble,e.locationOnScreen)else contextMenu?.show(e.locationOnScreen)
        }
    }
}
