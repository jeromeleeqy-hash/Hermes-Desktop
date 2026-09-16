package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

/** The outer Box releases incoming minimum constraints before applying a reading width. */
@Composable fun DesktopPage(
    maxContentWidth:Dp=1800.dp,
    scrollable:Boolean=true,
    tag:String="page-content",
    content:@Composable ColumnScope.()->Unit,
) {
    val scroll=rememberLazyListState()
    val d=LocalDesktopDesign.current
    Box(Modifier.fillMaxSize()) {
        if(scrollable) {
            // The viewport keeps finite height; the page content can grow independently.
            LazyColumn(state=scroll,modifier=Modifier.fillMaxSize().semantics {testTag="$tag-scroll"}) {
                item {
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
                        Column(Modifier.widthIn(max=maxContentWidth).fillMaxWidth()
                            .padding(horizontal=d.pagePadding,vertical=d.pagePadding).semantics {testTag=tag},
                            verticalArrangement=Arrangement.spacedBy(d.sectionGap),content=content)
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical=8.dp).width(8.dp),
                style=ScrollbarStyle(32.dp,8.dp,RoundedCornerShape(4.dp),150,MaterialTheme.colorScheme.onSurface.copy(alpha=.16f),MaterialTheme.colorScheme.onSurface.copy(alpha=.35f)))
        }else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=maxContentWidth).fillMaxWidth().fillMaxHeight().padding(d.pagePadding).semantics {testTag=tag},
                verticalArrangement=Arrangement.spacedBy(d.sectionGap),content=content)
        }
    }
}

@Composable fun SectionCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val d=LocalDesktopDesign.current;val colors=MaterialTheme.colorScheme
    if(LocalSettingsSurface.current) {
        Column(modifier.padding(bottom=10.dp)) {content();Spacer(Modifier.height(16.dp));HorizontalDivider(color=colors.outlineVariant.copy(alpha=.65f))}
    }else Column(modifier.then(if(d.glass)Modifier.shadow(5.dp,d.shape,clip=false,ambientColor=colors.primary.copy(alpha=.08f),spotColor=colors.primary.copy(alpha=.10f))else Modifier)
        .clip(d.shape).background(colors.surface.copy(alpha=if(d.glass).82f else 1f))
        .border(1.dp,if(d.glass)Color.White.copy(alpha=.5f)else colors.outline.copy(alpha=.55f),d.shape),content=content)
}

@Composable fun SectionTitle(title:String,detail:String="",trailing:@Composable ()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=if(LocalDesktopDesign.current.compact)12.dp else 16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(tr(title),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            if(detail.isNotBlank())Text(tr(detail),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable fun PanelEmpty(icon:String,title:String,detail:String,modifier:Modifier=Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        EmptyIllustration(icon,Modifier.size(88.dp,70.dp))
        Text(tr(title),fontSize=14.sp,fontWeight=FontWeight.Medium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Text(tr(detail),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable fun SegmentTabs(values:List<String>,selected:String,modifier:Modifier=Modifier,onSelect:(String)->Unit) {
    val colors=MaterialTheme.colorScheme;val shape=RoundedCornerShape(7.dp)
    Row(modifier.background(colors.onSurface.copy(alpha=.045f),RoundedCornerShape(9.dp)).padding(3.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        values.forEach {label->
            val active=label==selected
            Box(Modifier.clip(shape).background(if(active)colors.surface else Color.Transparent)
                .semantics {this.selected=active}.desktopClick(fillHover=false,shape=shape){onSelect(label)}.padding(horizontal=12.dp,vertical=6.dp)) {
                Text(tr(label),fontSize=13.sp,color=if(active)colors.primary else colors.onSurfaceVariant,fontWeight=if(active)FontWeight.Medium else FontWeight.Normal,maxLines=1)
            }
        }
    }
}

@Composable fun CountBadge(count:Int) {
    Text(count.toString(),Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha=.08f),RoundedCornerShape(5.dp)).padding(horizontal=7.dp,vertical=2.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
}

@Composable fun SubtleText(text:String,modifier:Modifier=Modifier,maxLines:Int=1) {
    Text(tr(text),modifier,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=maxLines,overflow=TextOverflow.Ellipsis)
}
