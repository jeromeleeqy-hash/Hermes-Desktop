package com.qingyu.hermescompanion.desktop

import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

internal data class ScreenSnapshot(val configuration:GraphicsConfiguration,val bounds:Rectangle,val image:BufferedImage)

internal fun captureDesktopScreens():List<ScreenSnapshot> {
    MacScreenAccess.ensureGranted()
    val devices=GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    require(devices.sumOf {it.defaultConfiguration.bounds.let {b->b.width.toLong()*b.height}}<=60_000_000){"屏幕总尺寸过大，暂时无法截图。"}
    return devices.map {device->
    val config=device.defaultConfiguration;val bounds=config.bounds
    require(bounds.width.toLong()*bounds.height<=60_000_000){"屏幕尺寸过大，暂时无法截图。"}
    ScreenSnapshot(config,Rectangle(bounds),Robot(device).createScreenCapture(bounds))
    }
}

internal fun composeScreenSelection(screens:List<ScreenSnapshot>,selection:Rectangle):ByteArray {
    require(selection.width>=4&&selection.height>=4){"请选择更大的截图范围。"}
    require(selection.width.toLong()*selection.height<=60_000_000){"截图范围过大，请缩小范围。"}
    val result=BufferedImage(selection.width,selection.height,BufferedImage.TYPE_INT_RGB)
    val g=result.createGraphics()
    try {
        g.color=Color.WHITE;g.fillRect(0,0,result.width,result.height)
        screens.forEach {screen->
            val area=screen.bounds.intersection(selection)
            if(!area.isEmpty) {
                val sx=screen.image.width.toDouble()/screen.bounds.width;val sy=screen.image.height.toDouble()/screen.bounds.height
                g.drawImage(screen.image,area.x-selection.x,area.y-selection.y,area.x-selection.x+area.width,area.y-selection.y+area.height,
                    ((area.x-screen.bounds.x)*sx).toInt(),((area.y-screen.bounds.y)*sy).toInt(),((area.x-screen.bounds.x+area.width)*sx).toInt(),((area.y-screen.bounds.y+area.height)*sy).toInt(),null)
            }
        }
    }finally {g.dispose()}
    return result.pngBytes()
}

/** Separate per-monitor overlays keep pointer coordinates correct on negative/DPI-mixed displays. */
internal class ScreenSelection(private val screens:List<ScreenSnapshot>,private val done:(Rectangle?)->Unit):AutoCloseable {
    private val completed=AtomicBoolean(false)
    private var start:Point?=null
    private var end:Point?=null
    // An unowned JWindow uses an invisible shared owner and cannot reliably acquire keyboard
    // focus on Windows. A utility frame remains focusable while the app and quick panel hide.
    private val windows=mutableListOf<JFrame>()
    private val keyboard=KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val escape=KeyEventDispatcher {event->
        if(event.id==KeyEvent.KEY_PRESSED&&event.keyCode==KeyEvent.VK_ESCAPE&&!completed.get()) {
            finish(null);true
        }else false
    }
    init {
        require(screens.isNotEmpty()){ "没有可用屏幕。" }
        keyboard.addKeyEventDispatcher(escape)
        try {
        screens.forEach {screen->
            val w=JFrame(screen.configuration).apply {
                isUndecorated=true;type=Window.Type.UTILITY;isAlwaysOnTop=true
                defaultCloseOperation=WindowConstants.DO_NOTHING_ON_CLOSE
                addWindowListener(object:WindowAdapter(){override fun windowClosing(e:WindowEvent){finish(null)}})
                bounds=screen.bounds;cursor=Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
            }
            val panel=object:JPanel() {
                override fun paintComponent(graphics:Graphics) {
                    val g=graphics.create() as Graphics2D
                    try {
                        g.drawImage(screen.image,0,0,width,height,null)
                        g.color=Color(9,18,34,115);g.fillRect(0,0,width,height)
                        val a=start;val b=end
                        if(a!=null&&b!=null) {
                            val r=selectionRectangle(a,b);val local=Rectangle(r.x-screen.bounds.x,r.y-screen.bounds.y,r.width,r.height)
                            val old=g.clip;g.clip=local;g.drawImage(screen.image,0,0,width,height,null);g.clip=old
                            g.color=Color(73,127,255);g.stroke=BasicStroke(2f);g.drawRect(local.x,local.y,local.width,local.height)
                            g.color=Color.WHITE;g.font=Font(Font.SANS_SERIF,Font.PLAIN,13);g.drawString("${r.width} × ${r.height}",local.x.coerceIn(16,(width-140).coerceAtLeast(16)),(local.y-12).coerceAtLeast(70))
                        }
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                        g.color=Color(25,32,46,235);g.fillRoundRect((width-380)/2,24,380,42,14,14)
                        g.color=Color.WHITE;g.font=Font(Font.SANS_SERIF,Font.PLAIN,14);g.drawString("拖动选择截图范围 · 松开完成 · Esc / 右键取消",(width-350)/2,50)
                    }finally {g.dispose()}
                }
            }
            val listener=object:MouseAdapter() {
                override fun mousePressed(e:MouseEvent) {
                    if(SwingUtilities.isRightMouseButton(e)){finish(null);return}
                    if(SwingUtilities.isLeftMouseButton(e)){start=e.locationOnScreen;end=start;windows.forEach {it.repaint()}}
                }
                override fun mouseDragged(e:MouseEvent){if(start!=null){end=e.locationOnScreen;windows.forEach {it.repaint()}}}
                override fun mouseReleased(e:MouseEvent) {
                    if(!SwingUtilities.isLeftMouseButton(e))return
                    start?.let {a->val r=selectionRectangle(a,e.locationOnScreen);if(r.width>=4&&r.height>=4)finish(r)else {start=null;end=null;windows.forEach {it.repaint()}}}
                }
            }
            panel.isFocusable=true;panel.addMouseListener(listener);panel.addMouseMotionListener(listener)
            panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"cancel")
            panel.actionMap.put("cancel",object:AbstractAction(){override fun actionPerformed(e:ActionEvent){finish(null)}})
            w.contentPane=panel;windows+=w
        }
        windows.forEach {it.isVisible=true}
        val mouse=MouseInfo.getPointerInfo()?.location
        (windows.firstOrNull {mouse!=null&&it.bounds.contains(mouse)}?:windows.firstOrNull())?.let {
            it.toFront();it.requestFocus();it.contentPane.requestFocusInWindow()
        }
        }catch(failure:Throwable){
            keyboard.removeKeyEventDispatcher(escape);windows.forEach {it.dispose()};windows.clear();throw failure
        }
    }
    private fun finish(rect:Rectangle?){if(completed.compareAndSet(false,true)){
        keyboard.removeKeyEventDispatcher(escape)
        windows.forEach {it.dispose()};windows.clear();done(rect)
    }}
    override fun close(){finish(null)}
}
