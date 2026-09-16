package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.ui.format.conversationPreview
import com.qingyu.hermescompanion.platform.DesktopHost

internal fun shortcutKey()=if(DesktopHost.isWindows||System.getProperty("os.name").contains("Linux"))"Ctrl"else"⌘"

@Composable internal fun WorkspaceSidebar(c:DesktopController,expanded:Boolean,onSearch:()->Unit) {
    val colors=MaterialTheme.colorScheme
    val macDrag=LocalMacWindowDragArea.current
    Column(Modifier.width(if(expanded)184.dp else if(macDrag!=null)MacWindowChrome.SIDEBAR_WIDTH.dp else 64.dp).fillMaxHeight().padding(start=if(expanded)10.dp else 8.dp,end=if(expanded)10.dp else 8.dp,top=4.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        macDrag?.invoke(Modifier.fillMaxWidth().height(MacWindowChrome.CONTROLS_HEIGHT.dp-4.dp-LocalDesktopDesign.current.inset).semantics {testTag="mac-traffic-light-space"})
        SidebarIdentity(c,expanded)
        Spacer(Modifier.height(5.dp))
        NavigationItem("search","搜索",false,expanded,shortcut=shortcutKey()+" K",onClick=onSearch)
        Spacer(Modifier.height(9.dp))
        NavigationItem("history","会话",c.page in listOf(Page.CHAT,Page.SESSIONS),expanded,badge=c.unread.size){c.navigate(if(c.currentSession!=null)Page.CHAT else Page.SESSIONS)}
        NavigationItem("dashboard","工作台",c.page==Page.HOME,expanded){c.navigate(Page.HOME)}
        NavigationItem("tasks","任务中心",c.page==Page.TASKS,expanded,badge=c.decisions.values.count {it.profile==c.profile}){c.navigate(Page.TASKS)}
        Spacer(Modifier.height(14.dp))
        NavigationItem("add",if(c.creatingSession)"正在创建…"else"新对话",false,expanded,primary=true,shortcut=shortcutKey()+" N",enabled=!c.creatingSession){c.newSession()}
        RecentSessionShortcuts(c,expanded,Modifier.weight(1f))
        NavigationItem("settings","设置",c.page==Page.PROFILE,expanded,shortcut=shortcutKey()+" ,"){c.loadSettings()}
        if(expanded)Row(Modifier.fillMaxWidth().desktopClick {c.settingsSection="网关";c.loadSettings()}.padding(horizontal=10.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            val okay=c.sessionsLoadError==null
            Box(Modifier.size(6.dp).background(if(okay)Color(0xFF23A17B)else colors.error,CircleShape))
            SubtleText(if(c.demo)"演示工作空间"else if(c.sessionsLoading)"同步中…"else if(okay)"已连接"else"同步需要重试",Modifier.weight(1f))
            Hint("收起导航"){DeskIconButton(onClick={c.toggleSidebar()}){Glyph("sidebar",Modifier.size(15.dp),colors.onSurfaceVariant)}}
        }else Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            Hint("展开导航"){DeskIconButton(modifier=Modifier.semantics {testTag="sidebar-expand"},onClick={c.toggleSidebar()}){Glyph("sidebar",Modifier.size(18.dp),colors.onSurfaceVariant)}}
        }
    }
}

/** Brand, personal account and current workspace have distinct jobs in one navigation masthead. */
@Composable private fun SidebarIdentity(c:DesktopController,expanded:Boolean) {
    val colors=MaterialTheme.colorScheme
    Hint("返回工作台") {
        Row(Modifier.fillMaxWidth().height(58.dp).desktopClick {c.navigate(Page.HOME)}
            .semantics {testTag="sidebar-brand";contentDescription=tr("返回工作台")}
            .padding(horizontal=if(expanded)6.dp else 0.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(expanded)Arrangement.spacedBy(9.dp)else Arrangement.Center) {
            Image(painterResource("icon.png"),null,Modifier.size(32.dp))
            if(expanded)Column(Modifier.weight(1f)) {
                Text("Hermes",fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.3).sp)
                Text(tr("你的 AI 工作伙伴"),fontSize=10.sp,lineHeight=17.sp,color=colors.onSurfaceVariant,maxLines=1)
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(colors.surface.copy(alpha=.52f),RoundedCornerShape(10.dp))
        .border(1.dp,colors.surface.copy(alpha=.38f),RoundedCornerShape(10.dp)).padding(3.dp)) {
        Hint("外观与账户 · "+c.nickname) {
            Row(Modifier.fillMaxWidth().height(38.dp).desktopClick(shape=RoundedCornerShape(7.dp)) {c.settingsSection="外观与账户";c.loadSettings()}
                .semantics {testTag="sidebar-profile";contentDescription=tr("外观与账户")}
                .padding(horizontal=if(expanded)7.dp else 0.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(expanded)Arrangement.spacedBy(8.dp)else Arrangement.Center) {
                Avatar(c,true,Modifier.size(24.dp))
                if(expanded) {
                    Text(c.nickname,Modifier.weight(1f),fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Glyph("chevron-right",Modifier.size(12.dp),colors.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal=8.dp,vertical=2.dp),color=colors.outline.copy(alpha=.48f))
        WorkspaceSelector(c,expanded,integrated=true)
    }
}

/** A small continuation shelf remains useful when the full conversation column isn't visible. */
@Composable private fun RecentSessionShortcuts(c:DesktopController,expanded:Boolean,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    val recent=c.sessions.filter {it.profile==c.profile&&(c.project==null||it.workspacePath==c.project?.primaryPath)}
        .sortedWith(compareByDescending<HermesSession>{it.isPinned}.thenByDescending {parseDesktopInstant(it.updatedAt)}).take(4)
    var open by remember(c.profile,c.project?.id,expanded){mutableStateOf(false)}
    if(expanded)Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top=12.dp)) {
        if(recent.isNotEmpty()) {
            Text(tr("最近会话"),Modifier.padding(start=11.dp,bottom=8.dp),fontSize=11.sp,color=colors.onSurfaceVariant)
            recent.forEach {s->
                Hint(s.title) {
                    Row(Modifier.fillMaxWidth().height(34.dp).desktopClick {c.openSession(s)}
                        .semantics {testTag="recent-shortcut:"+s.scopedId}.padding(horizontal=11.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Glyph(if(s.isPinned)"pin"else"history",Modifier.size(14.dp),colors.onSurfaceVariant)
                        Text(s.title,Modifier.weight(1f),fontSize=12.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(s.scopedId in c.unread)Box(Modifier.size(5.dp).background(colors.primary,CircleShape))
                    }
                }
            }
        }
    }else Box(modifier.fillMaxWidth().padding(top=7.dp),contentAlignment=Alignment.TopCenter) {
        if(recent.isNotEmpty())Box {
            Hint("最近会话") {
                DeskIconButton(modifier=Modifier.semantics {testTag="sidebar-recents"},onClick={open=true}){Glyph("clock",Modifier.size(18.dp),colors.onSurfaceVariant)}
            }
            DeskMenu(open,{open=false},modifier=Modifier.width(250.dp)) {
                MenuSectionLabel("最近会话")
                recent.forEach {s->DeskMenuItem(text={Text(s.title,maxLines=1,overflow=TextOverflow.Ellipsis)},
                    leadingIcon={Glyph(if(s.isPinned)"pin"else"history",Modifier.size(17.dp))},onClick={open=false;c.openSession(s)})}
            }
        }
    }
}

@Composable internal fun ConversationSidebar(c:DesktopController,modifier:Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    var filter by remember(c.profile){mutableStateOf("全部")}
    Column(modifier.desktopPanel().semantics {testTag="conversation-sidebar"}) {
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(tr("会话"),Modifier.weight(1f),fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            Hint("管理全部会话"){DeskIconButton(onClick={c.navigate(Page.SESSIONS)}){Glyph("more",Modifier.size(18.dp),colors.onSurfaceVariant)}}
        }
        Column(Modifier.padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            CompactInput(c.sidebarQuery,{c.sidebarQuery=it},"搜索会话")
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("全部","未读","置顶").forEach {label->
                    DeskTextButton(onClick={filter=label},modifier=Modifier.background(if(filter==label)colors.primary.copy(alpha=.09f)else Color.Transparent,RoundedCornerShape(6.dp))) {
                        Text(tr(label),fontSize=12.sp,color=if(filter==label)colors.primary else colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                if(c.sessionsLoading)CircularProgressIndicator(Modifier.size(13.dp),strokeWidth=1.5.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        val recent=c.sessions.filter {(c.project==null||it.workspacePath==c.project?.primaryPath)&&
            (c.sidebarQuery.isBlank()||it.title.contains(c.sidebarQuery,true)||(c.sessionSummaries[it.scopedId]?:it.preview).contains(c.sidebarQuery,true))&&
            when(filter){"未读"->it.scopedId in c.unread;"置顶"->it.isPinned;else->true}}
            .sortedWith(compareByDescending<HermesSession>{it.isPinned}.thenByDescending {parseDesktopInstant(it.updatedAt)})
        val state=rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal=8.dp),state=state,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                recent.groupBy(::sessionGroup).forEach {(group,values)->
                    item(key="group:"+group){Text(tr(group),Modifier.padding(start=8.dp,top=10.dp,bottom=5.dp),fontSize=11.sp,color=colors.onSurfaceVariant)}
                    items(values,key={it.scopedId}) {session->ConversationListRow(c,session)}
                }
                if(recent.isEmpty())item {
                    PanelEmpty("history",if(c.sessionsLoadError!=null)"会话读取未完成"else if(c.sessionsLoading)"正在同步"else"这里还没有会话",if(c.sidebarQuery.isNotBlank()||filter!="全部")"试试其他关键词，或切回全部。"else"开始一段对话，进展会保留在这里。")
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp))
        }
        if(c.sessionsLoadError!=null)DeskTextButton(onClick={c.refresh()},modifier=Modifier.padding(8.dp),enabled=!c.sessionsLoading){Glyph("refresh",Modifier.size(15.dp));Spacer(Modifier.width(6.dp));Text(tr("重新同步"),fontSize=12.sp)}
    }
}

@Composable private fun ConversationListRow(c:DesktopController,s:HermesSession) {
    val colors=MaterialTheme.colorScheme
    val active=c.currentSession?.scopedId==s.scopedId
    val preview=conversationPreview(visibleUserText(c.sessionSummaries[s.scopedId].orEmpty().ifBlank {s.preview})).ifBlank {if(s.scopedId in c.runs)"Hermes 正在处理…"else"点击继续对话"}
    ContextMenuArea(items={listOf(ContextMenuItem(tr(if(s.isPinned)"取消置顶"else"置顶")){c.request(s.profile,{it.setSessionPinned(s.id,!s.isPinned)}){c.refresh()}})}) {
        Row(Modifier.fillMaxWidth().desktopClick(selected=active){c.openSession(s)}.semantics {testTag="sidebar-session:"+s.id}.padding(horizontal=9.dp,vertical=13.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            SessionEmblem(s.title,Modifier.size(34.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(s.title,Modifier.weight(1f),fontSize=13.sp,fontWeight=if(active)FontWeight.Medium else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(s.updatedAt.isNotBlank())Text(conversationTime(s.updatedAt),fontSize=10.sp,color=colors.onSurfaceVariant,maxLines=1)
                    if(s.isPinned)Glyph("pin",Modifier.size(11.dp),colors.onSurfaceVariant)
                    if(s.scopedId in c.unread)Box(Modifier.size(6.dp).background(colors.primary,CircleShape))
                }
                Text(tr(preview),fontSize=11.sp,lineHeight=17.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

private fun conversationTime(value:String):String {
    val date=parseDesktopInstant(value)?.atZone(java.time.ZoneId.systemDefault())?:return ""
    return date.format(java.time.format.DateTimeFormatter.ofPattern(if(date.toLocalDate()==java.time.LocalDate.now())"HH:mm"else"MM/dd"))
}

@Composable internal fun SessionEmblem(title:String,modifier:Modifier=Modifier) {
    val palette=listOf(Color(0xFF4A7BDF),Color(0xFF7B6BCC),Color(0xFF3D9F91),Color(0xFFB78157))
    val tone=palette[(title.hashCode() and Int.MAX_VALUE)%palette.size]
    Box(modifier.background(tone.copy(alpha=.10f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Glyph("history",Modifier.size(18.dp),tone)}
}

@Composable internal fun WorkspaceSelector(c:DesktopController,expanded:Boolean,integrated:Boolean=false) {
    var open by remember {mutableStateOf(false)}
    var query by remember {mutableStateOf("")}
    val colors=MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        Hint("切换工作空间 · "+c.profile) {
            Row(Modifier.fillMaxWidth().height(if(expanded)53.dp else 38.dp).background(if(integrated)Color.Transparent else colors.surface.copy(alpha=.45f),RoundedCornerShape(9.dp))
                .desktopClick {open=true}.semantics {testTag="workspace-switcher";contentDescription=tr("切换工作空间")}.padding(horizontal=if(expanded)10.dp else 0.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(expanded)Arrangement.spacedBy(8.dp)else Arrangement.Center) {
                Glyph("workspace",Modifier.size(18.dp).semantics {testTag="workspace-switcher-icon"},colors.primary)
                if(expanded) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(if(c.profile=="default")tr("默认工作空间")else c.profile,fontSize=13.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        SubtleText(c.project?.name?:"全部项目")
                    }
                    Glyph("chevron-down",Modifier.size(12.dp),colors.onSurfaceVariant)
                }
            }
        }
        LaunchedEffect(open){if(!open)query=""}
        DeskMenu(open,{open=false},modifier=Modifier.width(260.dp).heightIn(max=430.dp)) {
            Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(tr("工作空间"),fontSize=14.sp,fontWeight=FontWeight.SemiBold);CompactInput(query,{query=it},"搜索工作空间")}
            c.profiles.filter {it.name.contains(query,true)}.forEach {p->
                DeskMenuItem(text={Text(if(p.name=="default")tr("默认工作空间")else p.name)},leadingIcon={Glyph("workspace",Modifier.size(17.dp))},
                    trailingIcon={if(p.name==c.profile)Glyph("check",Modifier.size(16.dp))},onClick={open=false;if(p.name!=c.profile){val change={c.loadProfile(p.name)};c.flushEditor?.invoke(change)?:change()}})
            }
            if(c.projects.isNotEmpty()&&query.isBlank()) {
                MenuDivider();MenuSectionLabel("当前空间的项目")
                (listOf<HermesProject?>(null)+c.projects).forEach {p->DeskMenuItem(text={Text(p?.name?:tr("全部项目"))},leadingIcon={Glyph(if(p==null)"dashboard"else"folder",Modifier.size(18.dp))},trailingIcon={if(p?.id==c.project?.id)Glyph("check",Modifier.size(16.dp))},onClick={open=false;c.chooseProject(p,navigate=false)})}
            }
        }
    }
}
@Composable private fun NavigationItem(icon:String,label:String,selected:Boolean,expanded:Boolean,primary:Boolean=false,shortcut:String="",badge:Int=0,enabled:Boolean=true,onClick:()->Unit) {
    val colors=MaterialTheme.colorScheme;val shape=RoundedCornerShape(9.dp)
    Hint(if(shortcut.isBlank())label else "$label · $shortcut") {
        Row(Modifier.fillMaxWidth().height(if(primary)38.dp else 43.dp).clip(shape)
            .background(when {selected->colors.surface.copy(alpha=.88f);primary->colors.primary.copy(alpha=.09f);icon=="search"->colors.onSurface.copy(alpha=.045f);else->Color.Transparent})
            .semantics {contentDescription=tr(label);this.selected=selected}.desktopClick(selected=false,enabled=enabled,shape=shape,onClick=onClick)
            .padding(horizontal=if(expanded)11.dp else 0.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(expanded)Arrangement.spacedBy(10.dp)else Arrangement.Center) {
            if(icon in listOf("history","dashboard","tasks","settings"))NavigationGlyph(icon,selected,Modifier.size(22.dp))else Glyph(icon,Modifier.size(20.dp),if(primary)colors.primary else colors.onSurfaceVariant)
            if(expanded) {
                Text(tr(label),Modifier.weight(1f),fontSize=14.sp,fontWeight=if(selected)FontWeight.Medium else FontWeight.Normal,color=if(primary)colors.primary else colors.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(badge>0)CountBadge(badge)else if(shortcut.isNotBlank()&&icon=="search")Text(shortcut,fontSize=9.sp,color=colors.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun NavigationGlyph(name:String,selected:Boolean,modifier:Modifier) {
    if(selected)Icon(painterResource("icons/nav-$name.svg"),null,modifier,tint=MaterialTheme.colorScheme.primary)else Glyph(name,modifier,MaterialTheme.colorScheme.onSurfaceVariant)
}

internal data class QuickEntry(val id:String,val title:String,val detail:String,val icon:String,val group:String,val action:()->Unit)
internal fun quickEntries(c:DesktopController,query:String):List<QuickEntry> {
    val q=query.trim()
    val commands=listOf(
        QuickEntry("new","新对话","${shortcutKey()} N","add","操作"){c.newSession()},
        QuickEntry("home","返回工作台","交办任务、继续工作","dashboard","操作"){c.navigate(Page.HOME)},
        QuickEntry("tasks","任务中心","进度、待确认与定时任务","tasks","操作"){c.navigate(Page.TASKS)},
        QuickEntry("files","最近文件","查看对话产物","files","操作"){c.showRecentFiles()},
        QuickEntry("settings","设置","外观、模型与连接","settings","操作"){c.loadSettings()},
        QuickEntry("focus",if(c.focusMode)"退出专注模式"else"专注模式","${shortcutKey()} Shift F","panel","操作"){c.focusMode=!c.focusMode},
    )
    fun matches(title:String,detail:String)=q.isBlank()||title.contains(q,true)||detail.contains(q,true)||tr(title).contains(q,true)
    return buildList {
        addAll(commands.filter {matches(it.title,it.detail)})
        addAll(c.sessions.filter {matches(it.title,c.sessionSummaries[it.scopedId]?:it.preview)}.sortedWith(compareByDescending<HermesSession>{it.isPinned}.thenByDescending {parseDesktopInstant(it.updatedAt)}).take(8).map {s->
            QuickEntry("session:${s.scopedId}",s.title,conversationPreview(visibleUserText(c.sessionSummaries[s.scopedId]?:s.preview)),"history","会话"){c.openSession(s)}
        })
        addAll(c.recentArtifacts.filter {it.profile==c.profile&&matches(it.name,it.sessionTitle)}.take(6).mapIndexed {i,a->
            QuickEntry("file:$i:${a.path}",a.name,a.sessionTitle,"document","文件"){c.openArtifact(a)}
        })
        if(q.isNotBlank())add(QuickEntry("search","搜索会话内容：$q","继续查找消息正文","search","深入搜索"){c.sessionQuery=q;c.search(q);c.navigate(Page.SESSIONS)})
    }
}

@Composable internal fun QuickSwitch(c:DesktopController,onDismiss:()->Unit) {
    HermesDialog(onDismissRequest=onDismiss,title={Text(tr("快速查找"))},text={QuickSwitchContent(c,onDismiss)},confirmButton={},showFooter=false)
}

@Composable internal fun QuickSwitchContent(c:DesktopController,onDismiss:()->Unit) {
    var field by remember {mutableStateOf(TextFieldValue(""))}
    val entries=quickEntries(c,field.text)
    var selected by remember {mutableStateOf(0)}
    val focus=remember {FocusRequester()};val scroll=rememberLazyListState();val colors=MaterialTheme.colorScheme
    LaunchedEffect(Unit){focus.requestFocus()}
    LaunchedEffect(field.text){selected=0}
    LaunchedEffect(selected){if(entries.isNotEmpty()){val index=selected.coerceIn(entries.indices);if(c.reduceMotion)scroll.scrollToItem(index)else scroll.animateScrollToItem(index)}}
    Column(Modifier.width(560.dp).heightIn(max=500.dp).onPreviewKeyEvent {e->
        if(e.type!=KeyEventType.KeyDown||field.composition!=null)false else when(e.key) {
            Key.DirectionDown->{if(entries.isNotEmpty())selected=(selected+1).coerceAtMost(entries.lastIndex);true}
            Key.DirectionUp->{selected=(selected-1).coerceAtLeast(0);true}
            Key.Enter->{entries.getOrNull(selected)?.let {onDismiss();it.action()};true}
            Key.Escape->{onDismiss();true}
            else->false
        }
    },verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().background(colors.primary.copy(alpha=.05f),RoundedCornerShape(10.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Glyph("search",Modifier.size(20.dp),colors.primary)
            BasicTextField(field,{field=it;selected=0},modifier=Modifier.weight(1f).focusRequester(focus).semantics {contentDescription=tr("搜索操作、会话与文件")},singleLine=true,textStyle=MaterialTheme.typography.bodyMedium.copy(color=colors.onSurface),cursorBrush=SolidColor(colors.primary),decorationBox={inner->Box {if(field.text.isBlank())Text(tr("搜索操作、会话与文件"),color=colors.onSurfaceVariant);inner()}})
        }
        LazyColumn(state=scroll,modifier=Modifier.weight(1f,fill=false).heightIn(min=240.dp,max=380.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            itemsIndexed(entries,key={_,item->item.id}) {i,item->
                Row(Modifier.fillMaxWidth().desktopClick(selected=i==selected){onDismiss();item.action()}.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Glyph(item.icon,Modifier.size(19.dp),colors.primary)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(tr(item.title),fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis);if(item.detail.isNotBlank())SubtleText(item.detail)}
                    SubtleText(item.group)
                }
            }
        }
        HorizontalDivider(color=colors.outline)
        SubtleText("↑ ↓ 选择    Enter 打开    Esc 关闭 · 当前工作空间")
    }
}
