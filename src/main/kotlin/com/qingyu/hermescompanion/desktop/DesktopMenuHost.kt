package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.*
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.math.ceil

internal data class DesktopMenuAction(
    val id:String,val label:String,val icon:String,val checked:Boolean?=null,
    val dividerBefore:Boolean=false,val invoke:()->Unit,
)

/** Screen coordinates are AWT logical coordinates, including negative monitor origins. */
internal fun desktopMenuBounds(anchor:Point,size:Dimension,areas:List<Rectangle>):Rectangle {
    require(areas.isNotEmpty())
    val area=areas.firstOrNull {it.contains(anchor)}?:areas.minBy {r->
        val dx=anchor.x.toLong()-anchor.x.coerceIn(r.x,r.x+r.width)
        val dy=anchor.y.toLong()-anchor.y.coerceIn(r.y,r.y+r.height)
        dx*dx+dy*dy
    }
    val width=size.width.coerceIn(1,area.width);val height=size.height.coerceIn(1,area.height)
    val x=if(anchor.x+6+width<=area.x+area.width)anchor.x+6 else anchor.x-width-6
    val y=if(anchor.y+6+height<=area.y+area.height)anchor.y+6 else anchor.y-height-6
    return Rectangle(x.coerceIn(area.x,area.x+area.width-width),y.coerceIn(area.y,area.y+area.height-height),width,height)
}

/** A tray has no Compose owner. A lazy utility window gives it the same menu UI and bundled font
 * as the app, even while the main window is hidden. Only one desktop menu can be open at a time. */
internal class DesktopMenuHost(
    private val c:DesktopController,private val subtitle:String,
    private val actions:()->List<DesktopMenuAction>,
):AutoCloseable {
    private var frame:ComposeWindow?=null
    private var screenKey=""
    private var focusedGeneration=-1
    private var anchor=Point()
    private var generation by mutableStateOf(0)
    private var availableHeight by mutableStateOf(600)
    private var disposed=false
    private var globalMouse:AutoCloseable?=null
    private val outsidePress=AWTEventListener {event->
        if(event is MouseEvent&&event.id==MouseEvent.MOUSE_PRESSED&&visible) {
            val owner=event.component?.let {SwingUtilities.getWindowAncestor(it)}
            if(owner!==frame&&event.component!==frame)dismiss()
        }
    }
    val visible get()=frame?.isVisible==true

    fun show(point:Point) {
        check(SwingUtilities.isEventDispatchThread())
        if(disposed)return
        active?.dismiss()
        anchor=Point(point)
        val areas=desktopWorkAreas()
        availableHeight=desktopMenuBounds(anchor,Dimension(280,Int.MAX_VALUE),areas).height
        val config=GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .map {it.defaultConfiguration}.firstOrNull {it.bounds.contains(point)}
        val nextScreenKey="${config?.device?.getIDstring()}:${config?.bounds}:${config?.defaultTransform}"
        // Recreate on another monitor so Windows uses that monitor's DPI from the first frame.
        // A transparent window can expose a different GraphicsConfiguration object on the same display.
        if(frame!=null&&screenKey!=nextScreenKey){frame?.dispose();frame=null}
        screenKey=nextScreenKey
        val window=frame?:create(config).also {frame=it}
        val items=actions()
        val estimate=ceil(129+36*(c.textScale-1)+items.size*40+items.count {it.dividerBefore}*13.0).toInt()
        window.bounds=desktopMenuBounds(anchor,Dimension(304,estimate),areas)
        generation++;focusedGeneration=-1
        active=this
        Toolkit.getDefaultToolkit().addAWTEventListener(outsidePress,AWTEvent.MOUSE_EVENT_MASK)
        window.isVisible=true;window.toFront();window.requestFocus()
        globalMouse=WindowsDesktopMenuMouse.watch(window,::dismiss)
    }

    private fun create(config:java.awt.GraphicsConfiguration?)=ComposeWindow(config).apply {
        isUndecorated=true;isTransparent=true;isResizable=false;isAlwaysOnTop=true
        type=Window.Type.UTILITY;title="Hermes · "+subtitle
        defaultCloseOperation=WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object:WindowAdapter(){override fun windowClosing(e:WindowEvent){dismiss()}})
        addWindowFocusListener(object:WindowAdapter(){
            override fun windowGainedFocus(e:WindowEvent){focusedGeneration=generation}
            override fun windowLostFocus(e:WindowEvent){
                val lostGeneration=focusedGeneration
                SwingUtilities.invokeLater {if(lostGeneration==generation&&frame===this@apply&&!isFocused)dismiss()}
            }
        })
        setContent {
            HermesTheme(c) {
                key(generation) {
                    val density=LocalDensity.current.density
                    DesktopMenuSurface(subtitle,actions(),::dismiss,
                        maxHeight=(availableHeight-48).coerceAtLeast(1).dp,
                        modifier=Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top,unbounded=true).onSizeChanged {size->
                            val height=ceil(size.height/density).toInt()
                            if(height>0&&height!=this@apply.height) {
                                bounds=desktopMenuBounds(anchor,Dimension(width,height),desktopWorkAreas())
                            }
                        })
                }
            }
        }
    }

    fun dismiss() {
        globalMouse?.close();globalMouse=null
        Toolkit.getDefaultToolkit().removeAWTEventListener(outsidePress)
        frame?.isVisible=false
        if(active===this)active=null
    }
    override fun close(){if(disposed)return;disposed=true;dismiss();frame?.dispose();frame=null}
    companion object {
        private var active:DesktopMenuHost?=null
        fun dismissActive(){active?.dismiss()}
    }
}

/** Shared by both native desktop entry points; also rendered directly for visual verification. */
@Composable internal fun DesktopMenuSurface(
    subtitle:String,actions:List<DesktopMenuAction>,onDismiss:()->Unit,
    modifier:Modifier=Modifier,maxHeight:Dp=600.dp,
) {
    val colors=MaterialTheme.colorScheme
    val inset=with(LocalDensity.current){24.dp.toPx()}
    Box(modifier.pointerInput(onDismiss) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            if(down.position.x<inset||down.position.y<inset||down.position.x>size.width-inset||down.position.y>size.height-inset)onDismiss()
        }
    }.padding(24.dp)) {
        Surface(Modifier.fillMaxWidth().dropShadow(RoundedCornerShape(10.dp),Shadow(radius=16.dp,color=Color.Black.copy(alpha=.14f),offset=DpOffset(0.dp,2.dp))).arrive(),shape=RoundedCornerShape(10.dp),
            color=colors.surface,tonalElevation=0.dp,shadowElevation=0.dp,
            border=BorderStroke(1.dp,colors.outline.copy(alpha=.5f))) {
            MenuKeyboardContent(onDismiss,Modifier.fillMaxWidth().heightIn(max=maxHeight).verticalScroll(rememberScrollState()).padding(vertical=6.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Image(painterResource("icon.png"),null,Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hermes",fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
                        Text(tr(subtitle),fontSize=11.sp,lineHeight=16.sp,color=colors.onSurfaceVariant)
                    }
                }
                MenuDivider()
                actions.forEach {action->
                    if(action.dividerBefore)MenuDivider()
                    DeskMenuItem(text={Text(tr(action.label),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},leadingIcon={Glyph(action.icon,Modifier.size(18.dp))},
                        trailingIcon=if(action.checked!=null){{if(action.checked)Glyph("check",Modifier.size(16.dp))}}else null,
                        modifier=Modifier.height(40.dp).semantics {
                            testTag="desktop-menu:"+action.id
                            if(action.checked!=null){role=Role.Checkbox;toggleableState=if(action.checked)androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off}
                        },onClick={onDismiss();action.invoke()})
                }
            }
        }
    }
}
