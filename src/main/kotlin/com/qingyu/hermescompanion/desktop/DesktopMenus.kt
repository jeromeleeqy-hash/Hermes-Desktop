package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

/** One menu surface and row rhythm for workspace, model, editor and context actions. */
@Composable fun DeskMenu(expanded:Boolean,onDismissRequest:()->Unit,modifier:Modifier=Modifier,
    shape:Shape=RoundedCornerShape(10.dp),containerColor:Color=MaterialTheme.colorScheme.surface,
    shadowElevation:Dp=8.dp,content:@Composable ColumnScope.()->Unit) {
    DropdownMenu(expanded,onDismissRequest,modifier.widthIn(min=180.dp),shape=shape,
        containerColor=containerColor,tonalElevation=0.dp,shadowElevation=shadowElevation,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.50f))) {
        MenuKeyboardContent(onDismissRequest,Modifier.fillMaxWidth(),content)
    }
}

/** Resolve focus inside the popup's owner, never through the window underneath it. */
@Composable internal fun MenuKeyboardContent(onDismiss:()->Unit,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val focus=remember {FocusRequester()}
    val manager=LocalFocusManager.current
    LaunchedEffect(Unit){focus.requestFocus()}
    Column(modifier.onPreviewKeyEvent {event->
        if(event.type!=KeyEventType.KeyDown)false else when(event.key) {
            Key.Escape->{onDismiss();true}
            Key.DirectionDown->manager.moveFocus(FocusDirection.Next)
            Key.DirectionUp->manager.moveFocus(FocusDirection.Previous)
            else->false
        }
    }.focusRequester(focus).focusable(),content=content)
}

@Composable fun DeskMenuItem(text:@Composable ()->Unit,onClick:()->Unit,modifier:Modifier=Modifier,
    leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,enabled:Boolean=true) {
    val colors=MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(horizontal=6.dp).heightIn(min=36.dp)
        .desktopClick(enabled=enabled,shape=RoundedCornerShape(6.dp),onClick=onClick)
        .padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant.copy(alpha=if(enabled)1f else .35f)) {
            if(leadingIcon!=null)Box(Modifier.size(20.dp),contentAlignment=Alignment.Center){leadingIcon()}
        }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface.copy(alpha=if(enabled)1f else .35f)) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontSize=13.sp,lineHeight=20.sp)){text()}
            }
        }
        if(trailingIcon!=null)Box(Modifier.size(18.dp),contentAlignment=Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides colors.primary){trailingIcon()}
        }
    }
}
@Composable internal fun MenuSectionLabel(text:String) {
    Text(tr(text),Modifier.fillMaxWidth().padding(start=16.dp,end=12.dp,top=10.dp,bottom=6.dp),
        fontSize=11.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun MenuDivider()=HorizontalDivider(Modifier.padding(horizontal=12.dp,vertical=6.dp),color=MaterialTheme.colorScheme.outline.copy(alpha=.55f))
