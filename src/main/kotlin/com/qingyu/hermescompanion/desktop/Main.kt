@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import com.qingyu.hermescompanion.ui.format.conversationPreview
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.qingyu.hermescompanion.model.*
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.qingyu.hermescompanion.platform.DesktopHost

val Accent=Color(0xFF2E65F5)
val Ink=Color(0xFF202631)
val Muted=Color(0xFF7A8494)
val Hairline=Color(0xFFE2E6EC)

fun main(args:Array<String>) {
    if(DesktopHost.isMac)System.setProperty("apple.awt.enableTemplateImages","true")
    application {
    val controller=remember { DesktopController("--demo" in args) }
    val screen=remember {java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds}
    val maxWidth=(screen.width-16).coerceAtLeast(640);val maxHeight=(screen.height-16).coerceAtLeast(480)
    val minWidth=minOf(if(DesktopHost.isWindows)1040 else 1160,maxWidth);val minHeight=minOf(if(DesktopHost.isWindows)640 else 760,maxHeight)
    val defaultWidth=if(DesktopHost.isWindows)1280 else 1440;val defaultHeight=if(DesktopHost.isWindows)820 else 900
    val state=rememberWindowState(width=(if(controller.demo)defaultWidth else controller.store.get("windowWidth",defaultWidth.toString()).toIntOrNull() ?: defaultWidth).coerceIn(minWidth,maxWidth).dp,height=(if(controller.demo)defaultHeight else controller.store.get("windowHeight",defaultHeight.toString()).toIntOrNull() ?: defaultHeight).coerceIn(minHeight,maxHeight).dp)
    
    var floatingSize by remember {mutableStateOf(state.size)}
    LaunchedEffect(state.size,state.placement){if(state.placement==WindowPlacement.Floating)floatingSize=state.size}
    var searchOpen by remember { mutableStateOf(false) }
    var mainVisible by remember {mutableStateOf(true)}
    var restoreWindow by remember {mutableStateOf<()->Unit>({mainVisible=true;state.isMinimized=false})}
    var floatingHost by remember {mutableStateOf<DesktopFloatingHost?>(null)}
    var macHooks by remember {mutableStateOf<MacApplicationHooks?>(null)}
    val exitNow={if(controller.close()){floatingHost?.close();DesktopNotifications.close();FxRuntime.shutdown();exitApplication()}else restoreWindow();Unit}
    val quitApplication={controller.flushEditor?.invoke(exitNow)?:exitNow();Unit}
    val hideWindow={
        if(controller.flushCheckpoint()) {
            if(DesktopNotifications.installed||macHooks?.canReopen==true)mainVisible=false else state.isMinimized=true
        }
        Unit
    }
    val closeWindow={controller.flushEditor?.invoke(hideWindow)?:hideWindow();Unit}
    val frameless=DesktopHost.isWindows||"--frameless" in args
    Window(onCloseRequest=closeWindow,visible=mainVisible,undecorated=frameless,resizable=true,state=state,title="Hermes",icon=painterResource("icon.png"),onPreviewKeyEvent={ e ->
        val mod=DesktopHost.primaryModifier(e.isMetaPressed,e.isCtrlPressed) && !e.isAltPressed
        if(e.type==KeyEventType.KeyDown&&mod) when(e.key) {
            Key.N -> { controller.newSession();true }
            Key.K -> { searchOpen=!searchOpen;true }
            Key.B -> { if(controller.editing&&(controller.page==Page.FILES||controller.documentSplit))false else {controller.toggleSidebar();true} }
            Key.F -> { if(e.isShiftPressed){controller.focusMode=!controller.focusMode;true}else false }
            Key.S -> { if(controller.page==Page.FILES||controller.documentSplit){controller.saveWithEditor();true}else false }
            Key.Comma -> {controller.loadSettings();true}
            Key.P -> {controller.commandsOpen=true;true}
            Key.W -> {if(controller.document!=null&&(controller.page==Page.FILES||controller.documentSplit)){controller.flushEditor?.invoke {controller.document?.let {controller.closeDocument(it)}} ?: controller.document?.let {controller.closeDocument(it)}}else closeWindow();true}
            else -> false
        } else false
    }) {
        if(!DesktopHost.isWindows) MenuBar {
            Menu(tr("文件")) {
                Item(tr("新对话"),onClick={controller.newSession()})
                Item(tr("添加附件"),onClick={controller.chooseFiles()},enabled=controller.currentSession!=null)
                Item(tr("保存文档"),onClick={controller.saveWithEditor()},enabled=controller.document!=null)
                Separator()
                Item(tr("设置"),onClick={controller.loadSettings()})
            }
            Menu(tr("对话")) {
                Item(tr("搜索"),onClick={searchOpen=true})
                Item(tr("快捷命令"),onClick={controller.commandsOpen=true})
                Item(tr("提示词片段"),onClick={controller.snippetsOpen=true})
                Item(tr("语音对话"),onClick={controller.voice.open()})
                Item(tr("停止任务"),onClick={controller.currentSession?.let {controller.stop(it)}})
            }
            Menu(tr("窗口")) {
                Item(tr("打开主窗口"),onClick={restoreWindow()})
                CheckboxItem(tr("桌面悬浮球"),checked=controller.floatingAssistantEnabled,onCheckedChange={enabled->controller.floatingAssistantEnabled=enabled;controller.savePreference("floatingAssistantEnabled",enabled.toString())})
                Item(tr("随时问"),onClick={controller.floatingAssistantEnabled=true;controller.savePreference("floatingAssistantEnabled","true");controller.companion.open()})
            }
            Menu(tr("帮助")) {Item(tr("快捷键"),onClick={controller.settingsSection="帮助";controller.loadSettings()})}
        }
        LaunchedEffect(controller.unread.size,controller.decisions.size,controller.notificationBadge) {
            DesktopNotifications.updateBadge(if(controller.notificationBadge)controller.unread.size+controller.decisions.size else 0)
            runCatching {if(java.awt.Taskbar.isTaskbarSupported())java.awt.Taskbar.getTaskbar().setIconBadge(if(controller.notificationBadge)(controller.unread.size+controller.decisions.size).takeIf {it>0}?.toString() else null)}
        }
        DisposableEffect(Unit) {
            val macChrome=MacWindowChrome.install(window)
            window.minimumSize=Dimension(minWidth,minHeight)
            restoreWindow={mainVisible=true;state.isMinimized=false;java.awt.EventQueue.invokeLater {window.isVisible=true;window.toFront();window.requestFocus()}}
            controller.companion.showMain={restoreWindow()}
            DesktopNotifications.install(controller,onOpen={restoreWindow()},onExit=quitApplication,onToggleFloating={enabled->controller.floatingAssistantEnabled=enabled;controller.savePreference("floatingAssistantEnabled",enabled.toString())})
            if(frameless||DesktopHost.isMac)floatingHost=DesktopFloatingHost(controller)
            if(DesktopHost.isMac&&java.awt.Desktop.isDesktopSupported())macHooks=MacApplicationHooks(
                java.awt.Desktop.getDesktop(),onOpen={restoreWindow()},onQuit=quitApplication,
                onSettings={restoreWindow();controller.loadSettings()},
                onAbout={restoreWindow();controller.loadSettings();controller.settingsSection="帮助"},
                onBackground={DesktopMenuHost.dismissActive()},
            )
            val focusListener=object:java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e:java.awt.event.WindowEvent) {controller.appFocused=true;controller.currentSession?.takeIf {controller.page==Page.CHAT}?.let {controller.unread.remove(it.scopedId)}}
                override fun windowLostFocus(e:java.awt.event.WindowEvent) {controller.appFocused=false}
            }
            window.addWindowFocusListener(focusListener)
            val initial=args.firstOrNull { it.startsWith("--page=") }?.substringAfter('=')
            initial?.let { controller.page=runCatching { Page.valueOf(it.uppercase()) }.getOrDefault(Page.HOME) }
            onDispose {macChrome.close();macHooks?.close();macHooks=null;controller.savePreference("windowWidth",floatingSize.width.value.toInt().toString());controller.savePreference("windowHeight",floatingSize.height.value.toInt().toString());window.removeWindowFocusListener(focusListener);floatingHost?.close();controller.companion.showMain=null;DesktopNotifications.close();controller.close();FxRuntime.shutdown()}
        }
        LaunchedEffect(floatingHost) {
            snapshotFlow {listOf(controller.floatingAssistantEnabled,controller.companion.panelOpen,controller.voice.active,controller.voice.phase,controller.reduceMotion,controller.connected,controller.profile)}.collectLatest {
                DesktopNotifications.syncFloating(controller.floatingAssistantEnabled)
                floatingHost?.sync()
            }
        }
        if(frameless)RememberWindowCorners(window,state)
        HermesTheme(controller) {
            val toggleMaximize={WindowsWindowFrame.refreshWorkArea(window);state.placement=if(state.placement==WindowPlacement.Maximized)WindowPlacement.Floating else WindowPlacement.Maximized}
            val controls=if(frameless)DesktopWindowControls(state.placement==WindowPlacement.Maximized,
                minimize={state.isMinimized=true},toggleMaximize=toggleMaximize,close=closeWindow,
                dragArea={modifier->CaptionGestureArea(window,state,modifier,toggleMaximize)})else null
            val macDrag:(@Composable (Modifier)->Unit)?=if(DesktopHost.isMac&&!frameless) { modifier->
                CaptionGestureArea(window,state,modifier) {
                    if(state.placement!=WindowPlacement.Fullscreen)state.placement=if(state.placement==WindowPlacement.Maximized)WindowPlacement.Floating else WindowPlacement.Maximized
                }
            }else null
            CompositionLocalProvider(LocalDesktopWindowControls provides controls,LocalMacWindowDragArea provides macDrag) {
                DesktopBackdrop {
                    DesktopFrame {
                        if(!controller.connected)ConnectionView(controller)
                        else Column(Modifier.fillMaxSize()) {
                            ConnectionRecoveryBanner(controller)
                            Box(Modifier.weight(1f)){DesktopWorkspace(controller,searchOpen){searchOpen=it}}
                        }
                    }
                }
            }
            controller.error?.let { message -> HermesDialog(onDismissRequest={controller.error=null},title={Text(tr("需要留意"))},text={Text(tr(message),Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()))},confirmButton={DeskTextButton(onClick={controller.error=null}) { Text(tr("知道了")) }}) }
            controller.detailsText?.let { message -> HermesDialog(onDismissRequest={controller.detailsText=null},title={Text(tr(controller.detailsTitle))},text={androidx.compose.foundation.text.selection.SelectionContainer {Text(message,Modifier.width(540.dp).heightIn(max=440.dp).verticalScroll(rememberScrollState()))}},confirmButton={DeskTextButton(onClick={controller.detailsText=null}) { Text(tr("关闭")) }},dismissButton={DeskTextButton(onClick={DesktopFiles.copy("Hermes ${com.qingyu.hermescompanion.BuildConfig.VERSION_NAME}\n${controller.detailsTitle}\n$message");controller.notice="详情已复制"}) {Text(tr("复制详情"))}}) }
            if(controller.voice.active&&!controller.voice.fromCompanion)VoiceDialog(controller)
            if(controller.commandsOpen)CommandsDialog(controller)
            if(controller.snippetsOpen)HermesDialog(onDismissRequest={controller.snippetsOpen=false},title={Text(tr("提示词片段"))},text={SnippetsPanel(controller,Modifier.width(600.dp).heightIn(max=480.dp).verticalScroll(rememberScrollState()))},confirmButton={DeskTextButton(onClick={controller.snippetsOpen=false}){Text(tr("关闭"))}})

        }
    }
}

}

@Composable fun Glyph(name:String,modifier:Modifier=Modifier.size(20.dp),tint:Color=LocalContentColor.current) {
    Icon(painterResource("icons/$name.svg"),null,modifier,tint=tint)
}
@Composable fun SmallButton(text:String,onClick:()->Unit,primary:Boolean=false,enabled:Boolean=true) {
    val colors=MaterialTheme.colorScheme;val shape=LocalDesktopDesign.current.controlShape
    Surface(shape=shape,color=if(primary)colors.primary.copy(alpha=if(enabled)1f else .35f)else Color.Transparent,
        border=if(primary)null else BorderStroke(1.dp,colors.outline)) {
        Row(Modifier.heightIn(min=32.dp).desktopClick(enabled=enabled,onClick=onClick).padding(horizontal=12.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(tr(text),fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(primary)colors.onPrimary else colors.onSurface.copy(alpha=if(enabled)1f else .35f))
        }
    }
}
@Composable fun Caption(text:String,modifier:Modifier=Modifier) = Text(tr(text),modifier,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f),fontSize=13.sp)
@Composable fun Heading(text:String,modifier:Modifier=Modifier) = Text(tr(text),modifier,fontSize=19.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
@Composable fun EmptyState(title:String,detail:String="") { Column(Modifier.fillMaxSize().padding(48.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { Heading(title);if(detail.isNotBlank()) { Spacer(Modifier.height(12.dp));Caption(detail) } } }
@Composable fun <T> Picker(label:String,values:List<T>,text:(T)->String,onSelect:(T)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.heightIn(min=32.dp).desktopClick {expanded=true}.padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr(optionLabel(label)),fontSize=13.sp);Glyph("chevron-down",Modifier.size(14.dp))
        }
        DeskMenu(expanded,{expanded=false}) { values.forEach { value -> DeskMenuItem(text={Text(tr(text(value)))},trailingIcon={if(text(value)==label)Glyph("check",Modifier.size(16.dp))},onClick={expanded=false;onSelect(value)}) } }
    }
}

@Composable fun DesktopWorkspace(c:DesktopController,searchOpen:Boolean=false,onSearchChange:(Boolean)->Unit={}) {
    val colors=MaterialTheme.colorScheme;val design=LocalDesktopDesign.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density=LocalDensity.current.density
        val split=c.page==Page.CHAT&&c.documentSplit&&c.document!=null
        val side=c.page in listOf(Page.CHAT,Page.FILES)&&(c.filesPanelOpen||(c.assistantPanel&&c.page==Page.CHAT))&&!split&&!c.focusMode
        val expanded=!c.sidebarCollapsed&&!c.focusMode&&(c.sidebarForcedOpen||(maxWidth>=1160.dp&&!split&&(!side||maxWidth>=1480.dp)))
        SideEffect {c.sidebarIsExpanded=expanded}
        val panelLimit=(maxWidth.value*.40f).coerceAtLeast(260f)
        val width=c.panelWidth.coerceIn(260f,panelLimit)
        val showConversationList=c.page==Page.CHAT&&!c.focusMode&&!c.sidebarCollapsed&&maxWidth>=1080.dp&&!split&&(!side||maxWidth>=1560.dp)
        val sessionListWidth=if(maxWidth>=1600.dp)280.dp else 244.dp
        Row(Modifier.fillMaxSize().padding(top=design.inset,end=design.inset,bottom=design.inset)) {
            WorkspaceSidebar(c,expanded){onSearchChange(true)}
            if(showConversationList) {
                ConversationSidebar(c,Modifier.width(sessionListWidth).fillMaxHeight())
                Spacer(Modifier.width(design.gap))
            }
            Column(Modifier.weight(1f).fillMaxHeight().desktopPanel().semantics {testTag="workspace-main-panel"}) {
                Header(c) {onSearchChange(true)}
                HorizontalDivider(color=colors.outline.copy(alpha=.6f))
                if(c.documentLoading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                if(c.page !in listOf(Page.HOME,Page.TASKS,Page.CHAT))RunBanner(c)
                Box(Modifier.weight(1f).fillMaxWidth().arrive(c.page)) {when(c.page) {
                    Page.HOME->HomeView(c)
                    Page.SESSIONS->SessionsView(c,false){}
                    Page.CHAT->if(split)Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(.95f).fillMaxHeight()){ChatView(c)}
                        VerticalDivider(color=colors.outline)
                        DocumentPanel(c,c.document!!,Modifier.weight(1.05f).fillMaxHeight())
                    }else ChatView(c)
                    Page.TASKS->TasksView(c)
                    Page.FILES->FilesView(c)
                    Page.PROFILE->SettingsView(c)
                }}
            }
            if(side) {
                Box(Modifier.width(5.dp).fillMaxHeight()
                    .semantics {testTag="files-divider"}.pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                    .pointerInput(panelLimit){detectHorizontalDragGestures(onDragEnd={c.savePreference("panelWidth",c.panelWidth.toString())}) {change,drag->change.consume();c.panelWidth=(c.panelWidth-drag/density).coerceIn(260f,panelLimit)}})
                Column(Modifier.width(width.dp).fillMaxHeight().desktopPanel()) {
                    Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        DeskTextButton(onClick={c.assistantPanel=false;c.setFilesPanel(true)}){Text(tr("文件"),fontWeight=if(!c.assistantPanel)FontWeight.SemiBold else FontWeight.Normal)}
                        if(c.page==Page.CHAT)DeskTextButton(onClick={c.assistantPanel=true}){Text(tr("助理"),fontWeight=if(c.assistantPanel)FontWeight.SemiBold else FontWeight.Normal)}
                        Spacer(Modifier.weight(1f))
                        Hint("收起侧栏"){DeskIconButton(onClick={c.assistantPanel=false;c.setFilesPanel(false)}){Glyph("close",Modifier.size(16.dp))}}
                    }
                    HorizontalDivider(color=colors.outline.copy(alpha=.6f))
                    if(c.assistantPanel&&c.page==Page.CHAT)AssistantPanel(c,Modifier.weight(1f))else FileBrowserPanel(c,Modifier.weight(1f))
                }
            }
        }
        if(c.sessionDrawer) {
            Box(Modifier.fillMaxSize().padding(start=if(LocalMacWindowDragArea.current!=null)MacWindowChrome.SIDEBAR_WIDTH.dp else 64.dp).background(Color.Black.copy(alpha=.12f)).clickable(interactionSource=remember {androidx.compose.foundation.interaction.MutableInteractionSource()},indication=null){c.sessionDrawer=false})
            Surface(Modifier.padding(start=if(LocalMacWindowDragArea.current!=null)(MacWindowChrome.SIDEBAR_WIDTH+4).dp else 68.dp,top=52.dp,bottom=12.dp).width(300.dp).fillMaxHeight(),shape=LocalDesktopDesign.current.shape,color=colors.surface,shadowElevation=8.dp,border=BorderStroke(1.dp,colors.outline)) {
                RecentSessionDrawer(c)
            }
        }
        NoticeOverlay(c,Modifier.align(Alignment.BottomCenter).padding(18.dp))
        if(searchOpen)QuickSwitch(c){onSearchChange(false)}
    }
}

@Composable private fun RecentSessionDrawer(c:DesktopController) {
    var query by remember {mutableStateOf("")}
    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text(tr("最近会话"),Modifier.weight(1f),fontWeight=FontWeight.SemiBold);DeskIconButton(onClick={c.sessionDrawer=false}){Glyph("close")}}
        CompactInput(query,{query=it},"查找会话")
        val sessions=c.sessions.filter {it.title.contains(query,true)}.sortedByDescending {it.isPinned}
        LazyColumn(Modifier.weight(1f)) {items(sessions,key={it.scopedId}) {s->
            Column(Modifier.fillMaxWidth().desktopClick(selected=c.currentSession?.scopedId==s.scopedId){c.openSession(s)}.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text((if(s.isPinned)"• "else "")+s.title,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                SubtleText(conversationPreview(visibleUserText(c.sessionSummaries[s.scopedId]?:s.preview)))
                SubtleText(friendlyTime(s.updatedAt))
            }
        }}
        SmallButton("管理全部会话",{c.sessionDrawer=false;c.navigate(Page.SESSIONS)})
    }
}

@Composable fun Header(c:DesktopController,onSearch:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Hint("展开或收起导航 · "+shortcutKey()+" B"){DeskIconButton(onClick={c.toggleSidebar()}){Glyph("sidebar",Modifier.size(19.dp))}}
        Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.CenterStart) {
            LocalMacWindowDragArea.current?.invoke(Modifier.matchParentSize().semantics {testTag="mac-header-drag"})
            Heading(when(c.page){Page.HOME->"工作台";Page.SESSIONS->"会话";Page.CHAT->c.currentSession?.title?:"对话";Page.TASKS->"任务中心";Page.FILES->"文件";Page.PROFILE->"设置"})
        }
        if(c.page==Page.CHAT) {
            c.currentSession?.let {s->
                if(c.messageLoading[s.scopedId]==true)CircularProgressIndicator(Modifier.size(15.dp),strokeWidth=1.5.dp)
                Hint(if(c.showOutline)"收起会话大纲"else"会话大纲"){DeskIconButton(onClick={c.showOutline=!c.showOutline;c.savePreference("showOutline",c.showOutline.toString())},modifier=Modifier.semantics {testTag="toggle-outline"}){Glyph("outline",Modifier.size(19.dp),if(c.showOutline)colors.primary else colors.onSurfaceVariant)}}
            }
            if(c.documentSplit)SmallButton("收起文档",{c.flushEditor?.invoke {c.documentSplit=false}?:run {c.documentSplit=false}})
            Hint("专注模式 · "+shortcutKey()+" Shift F"){DeskIconButton(onClick={c.focusMode=!c.focusMode}){Glyph("focus",Modifier.size(19.dp),if(c.focusMode)colors.primary else colors.onSurfaceVariant)}}
        }
        if(c.page==Page.FILES&&c.currentSession!=null)SmallButton("边看边聊",{c.documentSplit=true;c.navigate(Page.CHAT)})
        if(c.page!=Page.CHAT&&c.focusMode)Hint("退出专注模式"){DeskIconButton(onClick={c.focusMode=false}){Glyph("focus",Modifier.size(19.dp),colors.primary)}}
        Hint("快速查找 · "+shortcutKey()+" K"){DeskIconButton(onClick=onSearch){Glyph("search",Modifier.size(19.dp))}}
        Hint("刷新当前内容"){DeskIconButton(onClick={
            when(c.page){Page.CHAT->{c.currentSession?.let {c.openSession(it)};c.refresh();c.loadModelCatalog(force=true)};Page.PROFILE->c.loadSettings();Page.FILES->c.browse(c.listing?.path,navigate=false);else->c.refresh()}
        },enabled=!c.sessionsLoading,modifier=Modifier.semantics {testTag="refresh-content"}){Glyph("refresh",Modifier.size(18.dp))}}
        Hint(if(c.filesPanelOpen&&c.page in listOf(Page.CHAT,Page.FILES)&&!c.documentSplit)"收起文件栏"else"文件与产物"){
            DeskIconButton(modifier=Modifier.semantics {testTag="toggle-files"},onClick={
                c.assistantPanel=false;c.focusMode=false
                if(c.page in listOf(Page.CHAT,Page.FILES)) {
                    if(c.documentSplit){c.documentSplit=false;c.setFilesPanel(true)}else c.setFilesPanel(!c.filesPanelOpen)
                }else c.showRecentFiles()
            }){Glyph("folder",Modifier.size(19.dp),if(c.filesPanelOpen&&c.page in listOf(Page.CHAT,Page.FILES)&&!c.documentSplit)colors.primary else colors.onSurfaceVariant)}
        }
    }
}
@Composable internal fun ConnectionView(c:DesktopController) {
    var url by remember { mutableStateOf(c.baseUrl) };var user by remember { mutableStateOf(c.username) };var password by remember { mutableStateOf("") };var insecure by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    LaunchedEffect(c.busy) { if(c.busy) passwordVisible=false }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=28.dp),contentAlignment=Alignment.Center) { Column(Modifier.width(460.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Image(painterResource("icon.png"),"Hermes",Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)))
        Heading("让 Hermes 来到你的桌面")
        Caption("连接你现有的 Hermes 网关，继续手机上的对话。")
        OutlinedTextField(url,{url=it;insecure=false},label={Text(tr("网关地址"))},placeholder={Text("https://hermes.example.com")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=!c.busy)
        OutlinedTextField(user,{user=it},label={Text(tr("用户名"))},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=!c.busy)
        OutlinedTextField(password,{password=it},label={Text(tr("密码"))},
            visualTransformation=if(passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled=false,keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),
            trailingIcon={DeskTextButton(onClick={passwordVisible=!passwordVisible},enabled=!c.busy){Text(tr(if(passwordVisible)"隐藏" else "显示"))}},
            modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=!c.busy)
        if(url.trim().startsWith("http://")) Row(verticalAlignment=Alignment.CenterVertically){Checkbox(insecure,{insecure=it},enabled=!c.busy);Caption("此地址未加密，我确认使用该网关。")}
        Button(onClick={passwordVisible=false;c.connect(url,user,password)},modifier=Modifier.fillMaxWidth().height(46.dp),shape=RoundedCornerShape(12.dp),enabled=!c.busy&&user.isNotBlank()&&password.isNotBlank()&&(url.trim().startsWith("https://")||insecure)) { Text(tr(if(c.busy)"正在连接…" else "连接 Hermes")) }
        c.loginFailure?.let { failure -> DeskTextButton(onClick={
            runCatching { DesktopFiles.copy(failure.diagnostic()) }
                .onSuccess { c.notice="已复制登录诊断，不含密码、Cookie 或令牌。" }
                .onFailure { c.error="无法复制登录诊断，请重试。" }
        },modifier=Modifier.align(Alignment.CenterHorizontally)){Text(tr("复制登录诊断"))} }
        Caption(if(DesktopHost.isWindows)"密码只用于本次登录。登录状态由 Windows 用户加密保护。" else "密码只用于本次登录。登录状态由 macOS 钥匙串保护。")
        Caption("${tr("客户端版本")}：${com.qingyu.hermescompanion.BuildConfig.VERSION_NAME}")
    } }
}

@Composable fun SessionRow(s:HermesSession,onClick:()->Unit,showDivider:Boolean=true,trailing:@Composable ()->Unit={}) {
    Row(Modifier.fillMaxWidth().desktopClick(onClick=onClick).padding(vertical=if(LocalDesktopDesign.current.compact)10.dp else 14.dp,horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Glyph(if(s.isPinned)"pin"else"history",Modifier.size(19.dp),MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) { Text(s.title,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis);SubtleText(conversationPreview(visibleUserText(s.preview)).ifBlank {"打开对话继续聊聊"}) }
        trailing()
    }
    if(showDivider)HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.6f))
}

@Composable fun SessionsView(c:DesktopController,searchOpen:Boolean,onSearchDone:()->Unit) {
    var search by c::sessionQuery;var rename by remember { mutableStateOf<HermesSession?>(null) };var deleting by remember { mutableStateOf<HermesSession?>(null) };var newProject by remember { mutableStateOf(false) }
    val focusRequester=remember {FocusRequester()}
    var period by c::sessionPeriod
    LaunchedEffect(searchOpen) {if(searchOpen){focusRequester.requestFocus();onSearchDone()}}
    DesktopPage(scrollable=false,tag="sessions-content") {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { CompactInput(search,{search=it;c.search(it)},"搜索会话与消息…",Modifier.weight(1f).focusRequester(focusRequester));SmallButton(if(c.showArchived)"返回会话" else "已归档",{c.showArchived=!c.showArchived;c.search(search);if(c.showArchived)c.loadArchived()});SmallButton("新建项目",{newProject=true}) }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(0 to "全部时间",1 to "今天",7 to "最近 7 天",30 to "最近 30 天").forEach {(days,label)->DeskChip(period==days,{period=days},label={Text(tr(label))})}
            if(c.searchBusy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
            if(search.isNotBlank())Caption("搜索最近 100 段会话，每段最多 200 条消息")
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("全部","已置顶","未读","执行中").forEach {filter->DeskChip(c.sessionFilter==filter,{c.sessionFilter=filter},label={Text(filter)})}
            if(search.isNotBlank()||period!=0||c.sessionFilter!="全部")DeskTextButton(onClick={search="";period=0;c.sessionFilter="全部";c.search("")}){Text("清除筛选")}
        }
        val candidates=c.searchResults?.map {it.session} ?: if(c.showArchived)c.archived else c.sessions.filter { c.project==null || it.workspacePath==c.project?.primaryPath }
        val list=candidates.filter {(period==0 || sessionWithinDays(it.updatedAt,period))&&(c.project==null||it.workspacePath==c.project?.primaryPath)&&when(c.sessionFilter){"已置顶"->it.isPinned;"未读"->it.scopedId in c.unread;"执行中"->it.scopedId in c.runs;else->true}}.distinctBy {it.scopedId}.sortedWith(compareByDescending<HermesSession>{it.isPinned}.thenByDescending {parseDesktopInstant(it.updatedAt)})
        Caption("${list.size} 段${if(c.showArchived)"归档"else""}会话 · ${c.project?.name?:"全部项目"}")
        val loading=if(c.showArchived)c.archivedLoading else c.sessionsLoading
        val syncError=if(c.showArchived)c.archivedError else c.sessionsLoadError
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        c.searchError?.let {Row(verticalAlignment=Alignment.CenterVertically){Text(it,Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.error);SmallButton("重试搜索",{c.search(search)})}}
        syncError?.let {SyncProblem(c,"暂时无法同步会话",it,loading){if(c.showArchived)c.loadArchived()else c.refresh()}}
        if(list.isEmpty()&&(loading||c.searchBusy))LoadingRows()
        else if(list.isEmpty()&&c.searchError==null&&syncError==null) EmptyState(if(search.isNotBlank()||period!=0||c.project!=null||c.sessionFilter!="全部")"没有匹配的会话"else if(c.showArchived)"还没有归档会话"else"还没有会话",if(search.isNotBlank()||period!=0||c.project!=null||c.sessionFilter!="全部")"试试其他关键词，或清除筛选条件。"else"点击左侧新对话，开始和 Hermes 聊聊。")
        else if(list.isNotEmpty())Box(Modifier.weight(1f)) {
            val listState=rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().padding(end=8.dp),state=listState) {list.groupBy(::sessionGroup).forEach {(group,rows)->
                item("group-$group"){Caption(group,Modifier.padding(top=12.dp,bottom=6.dp))}
                items(rows,key={it.scopedId}){s ->
            ContextMenuArea(items={ listOf(ContextMenuItem("打开"){c.openSession(s)},ContextMenuItem("重命名"){rename=s},ContextMenuItem("删除"){deleting=s}) }) {
                val hit=c.searchResults?.firstOrNull {it.session.scopedId==s.scopedId}
                SessionRow(s.copy(preview=hit?.snippet?:c.sessionSummaries[s.scopedId]?:s.preview),{c.openSession(s,hit?.messageId)}) {
                    SubtleText(friendlyTime(s.updatedAt))
                    if(s.scopedId in c.runs)StatusPill("执行中")
                    if(s.scopedId in c.unread)Text("●",color=MaterialTheme.colorScheme.primary)
                    var menu by remember { mutableStateOf(false) }
                    Box { Hint("会话操作"){DeskIconButton(onClick={menu=true}){Glyph("more",Modifier.size(19.dp))}};DeskMenu(menu,{menu=false}) {
                        DeskMenuItem(text={Text(tr("重命名"))},leadingIcon={Glyph("edit")},onClick={menu=false;rename=s})
                        DeskMenuItem(text={Text(tr("AI 起标题"))},leadingIcon={Glyph("model")},onClick={menu=false;c.request(s.profile,{it.generateSessionTitles(listOf(s))}){c.refresh()}})
                        DeskMenuItem(text={Text(tr(if(s.isPinned)"取消置顶" else "置顶"))},leadingIcon={Glyph("pin")},onClick={menu=false;c.request(s.profile,{it.setSessionPinned(s.id,!s.isPinned)}){c.refresh()}})
                        DeskMenuItem(text={Text(tr(if(c.showArchived)"恢复" else "归档"))},leadingIcon={Glyph("archive")},onClick={menu=false;c.request(s.profile,{if(c.showArchived)it.restoreSession(s.id) else it.archiveSession(s.id)}){c.refresh();c.archived=c.archived.filterNot { it.id==s.id }}})
                        c.projects.forEach { p -> DeskMenuItem(text={Text("移至 ${p.name}")},leadingIcon={Glyph("folder")},onClick={menu=false;c.request(s.profile,{it.moveSessionToProject(s,p)}){c.refresh()}}) }
                        DeskMenuItem(text={Text(tr("删除"))},leadingIcon={Glyph("delete")},onClick={menu=false;deleting=s})
                    } }
                }
            }
        } } }
            VerticalScrollbar(rememberScrollbarAdapter(listState),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp))
        }
    }
    rename?.let { s -> SessionRenameDialog(c,s){rename=null} }
    deleting?.let { s -> ConfirmDialog("删除这段会话？","“${s.title}”将从服务器删除，无法撤销。",{deleting=null}) { c.request(s.profile,{it.deleteSession(s.id)}){c.refresh()};deleting=null } }
    if(newProject)ProjectEditor(c){newProject=false}
}

@Composable fun TextEditDialog(title:String,initial:String,onDismiss:()->Unit,onSave:(String)->Unit) { var text by remember { mutableStateOf(initial) };HermesDialog(onDismissRequest=onDismiss,title={Text(tr(title))},text={OutlinedTextField(text,{text=it},modifier=Modifier.width(440.dp),maxLines=8)},confirmButton={DeskTextButton(onClick={onSave(text)},enabled=text.isNotBlank()){Text(tr("保存"))}},dismissButton={DeskTextButton(onClick=onDismiss){Text(tr("取消"))}}) }
@Composable fun ConfirmDialog(title:String,message:String,onDismiss:()->Unit,onConfirm:()->Unit) { HermesDialog(onDismissRequest=onDismiss,title={Text(tr(title))},text={Text(tr(message),Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()))},confirmButton={DeskTextButton(onClick=onConfirm){Text(tr("确认"))}},dismissButton={DeskTextButton(onClick=onDismiss){Text(tr("取消"))}}) }

internal fun sessionWithinDays(value:String,days:Int):Boolean {
    val date=runCatching {java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.getOrElse {runCatching {LocalDate.parse(value.take(10))}.getOrNull()} ?: return false
    return !date.isBefore(LocalDate.now().minusDays((days-1).toLong()))
}
