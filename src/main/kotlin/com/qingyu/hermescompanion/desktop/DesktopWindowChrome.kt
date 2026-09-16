package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*

/** The same caption buttons are used in the native window and interaction previews. */
data class DesktopWindowControls(
    val maximized:Boolean=false,
    val minimize:()->Unit={},
    val toggleMaximize:()->Unit={},
    val close:()->Unit={},
    val dragArea:@Composable (Modifier)->Unit={Box(it)},
)
val LocalDesktopWindowControls=staticCompositionLocalOf<DesktopWindowControls?>{null}

@Composable internal fun DesktopFrame(content:@Composable ()->Unit) {
    Column(Modifier.fillMaxSize()) {
        LocalDesktopWindowControls.current?.let {DesktopWindowChrome(it)}
        Box(Modifier.weight(1f)){content()}
    }
}

@Composable internal fun DesktopWindowChrome(controls:DesktopWindowControls) {
    Row(Modifier.fillMaxWidth().height(32.dp).semantics {testTag="window-chrome"},verticalAlignment=Alignment.CenterVertically) {
        controls.dragArea(Modifier.weight(1f).fillMaxHeight().semantics {contentDescription=tr("拖动窗口，双击最大化或还原");testTag="window-drag-area"})
        CaptionButton("window-minimize","最小化",false,controls.minimize)
        CaptionButton(if(controls.maximized)"window-restore"else"window-maximize",if(controls.maximized)"还原窗口"else"最大化",false,controls.toggleMaximize)
        CaptionButton("close","关闭窗口",true,controls.close)
    }
}

@Composable private fun CaptionButton(icon:String,label:String,close:Boolean,action:()->Unit) {
    val source=remember {MutableInteractionSource()};val hovered by source.collectIsHoveredAsState()
    val colors=MaterialTheme.colorScheme
    Box(Modifier.width(44.dp).fillMaxHeight().background(if(hovered&&close)Color(0xFFC8363D)else Color.Transparent)
        .hoverable(source).desktopClick(fillHover=!close,shape=RoundedCornerShape(0.dp),onClick=action)
        .semantics {testTag=when {close->"window-close";icon=="window-minimize"->"window-minimize";else->"window-maximize"};contentDescription=tr(label)},contentAlignment=Alignment.Center) {
        Glyph(icon,Modifier.size(14.dp),if(hovered&&close)Color.White else colors.onSurfaceVariant)
    }
}
