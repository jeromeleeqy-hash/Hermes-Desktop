@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class DesktopMaterial { MATTE, PAPER, GLASS }
fun skinDisplayName(name:String)=when(name){"温暖灵动","纸间留白"->"纸间留白";"液态玻璃","流光玻璃"->"流光玻璃";else->"轻盈办公"}
data class DesktopDesign(val material:DesktopMaterial=DesktopMaterial.MATTE,val compact:Boolean=true) {
    val paper get()=material==DesktopMaterial.PAPER
    val glass get()=material==DesktopMaterial.GLASS
    val radius get()=if(glass)18.dp else if(paper)6.dp else 12.dp
    val pagePadding get()=if(compact)28.dp else 36.dp
    val sectionGap get()=if(compact)18.dp else 24.dp
    val inset get()=if(paper)12.dp else 8.dp
    val gap get()=if(paper)1.dp else if(glass)10.dp else 8.dp
    val shape get()=RoundedCornerShape(radius)
    val controlShape get()=RoundedCornerShape(if(paper)5.dp else 8.dp)
    val composerShape get()=RoundedCornerShape(if(paper)8.dp else if(glass)20.dp else 12.dp)
}
val LocalDesktopDesign=staticCompositionLocalOf { DesktopDesign() }
val LocalSettingsSurface=staticCompositionLocalOf {false}
val LocalPrimaryAction=staticCompositionLocalOf {false}

@Composable fun HermesTheme(c:DesktopController,content:@Composable ()->Unit) {
    val design=remember(c.skin,c.compact) { DesktopDesign(when(skinDisplayName(c.skin)){"纸间留白"->DesktopMaterial.PAPER;"流光玻璃"->DesktopMaterial.GLASS;else->DesktopMaterial.MATTE},c.compact) }
    val density=LocalDensity.current
    val dark=c.appearance=="深色" || (c.appearance=="跟随系统"&&isSystemInDarkTheme())
    val accent=when(design.material){DesktopMaterial.PAPER->Color(0xFF8D6146);DesktopMaterial.GLASS->Color(0xFF6359D7);else->Color(0xFF2E65F5)}
    val colors=desktopColors(dark,accent).let {base->
        val bg=when {design.paper->if(dark)Color(0xFF211F1C)else Color(0xFFF0EBE2);design.glass->if(dark)Color(0xFF171B2B)else Color(0xFFEAF0FC);else->base.background}
        val surface=when {design.paper->if(dark)Color(0xFF282521)else Color(0xFFFFFDF8);design.glass->if(dark)Color(0xFF24283C)else Color(0xFFFCFDFF);else->base.surface}
        base.copy(background=bg,surface=surface,surfaceContainer=surface,surfaceContainerHigh=surface,surfaceContainerHighest=surface,surfaceContainerLow=surface,surfaceTint=Color.Transparent)
    }
    MaterialTheme(colorScheme=colors,typography=desktopTypography(),shapes=Shapes(small=design.controlShape,medium=design.shape,large=design.shape,extraLarge=design.shape)) {
        CompositionLocalProvider(LocalReduceMotion provides c.reduceMotion,LocalDensity provides Density(density.density,density.fontScale*c.textScale),LocalContentColor provides colors.onSurface,LocalTextStyle provides MaterialTheme.typography.bodyMedium,LocalDesktopDesign provides design,LocalRippleConfiguration provides null,LocalMinimumInteractiveComponentSize provides 32.dp,LocalContextMenuRepresentation provides HermesContextMenu) {content()}
    }
}

@Composable fun DesktopBackdrop(content:@Composable BoxScope.()->Unit) {
    val d=LocalDesktopDesign.current;val colors=MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Canvas(Modifier.matchParentSize()) {
            if(d.glass) {
                drawRect(Brush.linearGradient(listOf(colors.primary.copy(alpha=.12f),Color.Transparent,Color(0xFF5CAFC0).copy(alpha=.14f))))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFB095EE).copy(alpha=.21f),Color.Transparent),center=Offset(size.width*.82f,size.height*.22f),radius=size.width*.6f),radius=size.width*.6f,center=Offset(size.width*.82f,size.height*.22f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF76CFCF).copy(alpha=.16f),Color.Transparent),center=Offset(size.width*.1f,size.height*.72f),radius=size.width*.5f),radius=size.width*.5f,center=Offset(size.width*.1f,size.height*.72f))
            }else if(d.paper) {
                for(y in 0..size.height.toInt() step 11)for(x in 0..size.width.toInt() step 17) {
                    val shift=((x*17+y*13)%7).toFloat()
                    drawLine(colors.onSurface.copy(alpha=.035f),Offset(x+shift,y.toFloat()),Offset(x+shift+2f,y+.6f),.6f)
                }
            }else drawRect(Brush.linearGradient(listOf(colors.primary.copy(alpha=.045f),Color.Transparent,Color(0xFF939CDF).copy(alpha=.05f))))
        }
        content()
    }
}

@Composable fun Modifier.desktopPanel():Modifier {
    val d=LocalDesktopDesign.current;val colors=MaterialTheme.colorScheme
    return this.then(if(d.glass)Modifier.shadow(5.dp,d.shape,clip=false,ambientColor=colors.primary.copy(alpha=.08f),spotColor=colors.primary.copy(alpha=.10f))else Modifier)
        .clip(d.shape).background(if(d.glass)colors.surface.copy(alpha=.88f)else colors.surface)
        .then(if(d.glass)Modifier.border(1.dp,Color.White.copy(alpha=.48f),d.shape)else Modifier)
}

/** Flat text actions change ink only. Rows and icon targets get a restrained, clipped fill. */
fun Modifier.desktopClick(enabled:Boolean=true,selected:Boolean=false,fillHover:Boolean=true,shape:Shape?=null,onClick:()->Unit):Modifier=composed {
    val source=remember {MutableInteractionSource()};val hovered by source.collectIsHoveredAsState();val focused by source.collectIsFocusedAsState();val pressed by source.collectIsPressedAsState()
    val colors=MaterialTheme.colorScheme;val targetShape=shape ?: LocalDesktopDesign.current.controlShape
    val keyboardFocus=focused&&LocalInputModeManager.current.inputMode==InputMode.Keyboard
    val target=when {enabled&&pressed&&fillHover->colors.primary.copy(alpha=.14f);selected->colors.primary.copy(alpha=.10f);enabled&&hovered&&fillHover->colors.onSurface.copy(alpha=.05f);else->Color.Transparent}
    val fill by animateColorAsState(target,tween(motionMillis(if(pressed)45 else 150),easing=androidx.compose.animation.core.CubicBezierEasing(.2f,0f,0f,1f)),label="hover-fill")
    clip(targetShape).background(fill)
        .then(if(keyboardFocus)Modifier.border(1.dp,colors.primary,targetShape)else Modifier)
        .pointerHoverIcon(if(enabled)PointerIcon.Hand else PointerIcon.Default)
        .clickable(interactionSource=source,indication=null,enabled=enabled,role=Role.Button,onClick=onClick)
}

@Composable fun DeskTextButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
    val source=remember {MutableInteractionSource()};val hovered by source.collectIsHoveredAsState();val focused by source.collectIsFocusedAsState()
    val colors=MaterialTheme.colorScheme;val shape=LocalDesktopDesign.current.controlShape
    val keyboardFocus=focused&&LocalInputModeManager.current.inputMode==InputMode.Keyboard
    val ink=if(LocalPrimaryAction.current)colors.onPrimary.copy(alpha=if(enabled)1f else .45f)else if(!enabled)colors.onSurface.copy(alpha=.35f)else if(hovered)lerp(colors.primary,colors.onSurface,.28f)else colors.primary
    CompositionLocalProvider(LocalContentColor provides ink,LocalTextStyle provides MaterialTheme.typography.labelLarge) {
        Row(modifier.heightIn(min=30.dp).clip(shape).then(if(keyboardFocus)Modifier.border(1.dp,colors.primary,shape)else Modifier)
            .pointerHoverIcon(if(enabled)PointerIcon.Hand else PointerIcon.Default).clickable(source,null,enabled,role=Role.Button,onClick=onClick)
            .padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center,content=content)
    }
}
@Composable fun DeskIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable ()->Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .35f)) {
        Box(modifier.size(32.dp).desktopClick(enabled=enabled,onClick=onClick),contentAlignment=Alignment.Center) {content()}
    }
}
@Composable fun DeskChip(selected:Boolean,onClick:()->Unit,label:@Composable ()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val colors=MaterialTheme.colorScheme;val shape=LocalDesktopDesign.current.controlShape
    CompositionLocalProvider(LocalContentColor provides if(selected)colors.primary else colors.onSurfaceVariant,LocalTextStyle provides MaterialTheme.typography.labelLarge) {
        Row(modifier.heightIn(min=30.dp).border(1.dp,if(selected)colors.primary.copy(alpha=.24f)else colors.outline,shape)
            .semantics {this.selected=selected}.desktopClick(enabled,selected,fillHover=false,onClick=onClick).padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){label()}
    }
}
@Composable fun DeskAttachChip(onClick:()->Unit,label:@Composable ()->Unit) = DeskChip(false,onClick,label)

@Composable fun Hint(label:String,content:@Composable ()->Unit) {
    TooltipArea(modifier=Modifier.semantics(mergeDescendants=true){contentDescription=tr(label)},tooltip={Surface(shape=LocalDesktopDesign.current.controlShape,color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),shadowElevation=4.dp) {Text(tr(label),Modifier.padding(horizontal=9.dp,vertical=5.dp),fontSize=12.sp)}},delayMillis=550,content=content)
}

private object HermesContextMenu:ContextMenuRepresentation {
    @Composable override fun Representation(state:ContextMenuState,items:()->List<ContextMenuItem>) {
        val open=state.status as? ContextMenuState.Status.Open ?: return
        val colors=MaterialTheme.colorScheme
        val position=remember(open.rect) {object:androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
                return IntOffset((anchorBounds.left+open.rect.left.toInt()).coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0)),(anchorBounds.top+open.rect.bottom.toInt()).coerceIn(0,(windowSize.height-popupContentSize.height).coerceAtLeast(0)))
            }
        }}
        androidx.compose.ui.window.Popup(popupPositionProvider=position,onDismissRequest={state.status=ContextMenuState.Status.Closed},properties=androidx.compose.ui.window.PopupProperties(focusable=true)) {
            Surface(shape=RoundedCornerShape(10.dp),color=colors.surface,border=BorderStroke(1.dp,colors.outline.copy(alpha=.5f)),shadowElevation=8.dp) {
                MenuKeyboardContent({state.status=ContextMenuState.Status.Closed},Modifier.widthIn(min=150.dp,max=300.dp).padding(5.dp)) {items().forEach {item->
                    val icon=when(item.label.lowercase()) {"cut","剪切"->"cut";"paste","粘贴"->"paste";"select all","全选"->"check-square";else->"copy"}
                    DeskMenuItem(text={Text(item.label)},leadingIcon={Glyph(icon,Modifier.size(18.dp))},onClick={state.status=ContextMenuState.Status.Closed;item.onClick()})
                }}
            }
        }
    }
}

@Composable fun SkinThumbnail(name:String,modifier:Modifier=Modifier) {
    val asset=when(skinDisplayName(name)){"纸间留白"->"paper";"流光玻璃"->"glass";else->"office"}
    Image(painterResource("themes/$asset.png"),tr("${skinDisplayName(name)}界面预览"),modifier.clip(RoundedCornerShape(7.dp)),contentScale=ContentScale.Crop)
}

@Composable fun HermesDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null,dismissButton:(@Composable ()->Unit)?=null,inlinePreview:Boolean=false,showFooter:Boolean=true) {
    val owner=LocalWindowInfo.current.containerSize
    val density=LocalDensity.current
    val width=with(density){owner.width.toDp()};val height=with(density){owner.height.toDp()}
    val content:@Composable ()->Unit={
        Box(Modifier.widthIn(max=if(width>48.dp)width-40.dp else 600.dp).heightIn(max=if(height>48.dp)height-40.dp else 680.dp).arrive()) {
            HermesDialogContent(title,text,confirmButton,dismissButton,onDismissRequest,showFooter)
        }
    }
    if(inlinePreview)content()else Dialog(onDismissRequest,properties=DialogProperties(usePlatformDefaultWidth=false),content=content)
}
/** Shared by the real modal and the offscreen UI review renderer. */
@Composable internal fun HermesDialogContent(title:(@Composable ()->Unit)?,text:(@Composable ()->Unit)?,confirmButton:@Composable ()->Unit,dismissButton:(@Composable ()->Unit)?=null,onDismiss:(()->Unit)?=null,showFooter:Boolean=true) {
    val d=LocalDesktopDesign.current;val colors=MaterialTheme.colorScheme
    Surface(Modifier.widthIn(min=360.dp,max=600.dp).heightIn(max=680.dp),shape=RoundedCornerShape(12.dp),color=colors.surface,
        border=BorderStroke(1.dp,colors.outline.copy(alpha=.55f)),shadowElevation=16.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start=24.dp,end=16.dp,top=18.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)){ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontSize=18.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold)){if(title!=null)title()else Text("Hermes")}}
                if(onDismiss!=null)DeskIconButton(onClick=onDismiss){Glyph("close",Modifier.size(18.dp),colors.onSurfaceVariant)}
            }
            Box(Modifier.weight(1f,false).padding(start=24.dp,end=24.dp,top=4.dp,bottom=22.dp)) {ProvideTextStyle(MaterialTheme.typography.bodyMedium){text?.invoke()}}
            if(showFooter) {
            HorizontalDivider(color=colors.outline.copy(alpha=.4f))
            FlowRow(Modifier.fillMaxWidth().background(colors.surfaceVariant.copy(alpha=.35f)).padding(horizontal=20.dp,vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                dismissButton?.invoke()
                Surface(shape=RoundedCornerShape(7.dp),color=colors.primary){CompositionLocalProvider(LocalPrimaryAction provides true){confirmButton()}}
            }
            }
        }
    }
}
