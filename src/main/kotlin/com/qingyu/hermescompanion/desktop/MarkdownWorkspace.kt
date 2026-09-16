@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.platform.DesktopHost
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.Node

internal val LocalDocumentReading=staticCompositionLocalOf {false}
internal val LocalMarkdownImage=staticCompositionLocalOf<(@Composable (String)->Unit)?> {null}
// This editor tracks the caret itself. Automatic focus scrolling targets the whole
// multi-line field and can hide its first line while the outer viewport is settling.
private val SourceEditorBringIntoView=object:BringIntoViewSpec {
    override fun calculateScrollDistance(offset:Float,size:Float,containerSize:Float)=0f
}

@Composable internal fun MarkdownWorkspace(c:DesktopController,tab:DocumentTab,modifier:Modifier=Modifier) {
    val key=c.documentKey(tab)
    val colors=MaterialTheme.colorScheme
    val text=c.edits[key]?:tab.document.content
    var field by remember(key){mutableStateOf(TextFieldValue(text))}
    val history=remember(key){MarkdownUndoHistory()}
    val richHandle=remember(key){WebEditorHandle()}
    val focus=remember(key){FocusRequester()}
    val latestField by rememberUpdatedState(field)
    var toolbarSelection by remember(key){mutableStateOf<TextFieldValue?>(null)}
    var mode by remember(key){mutableStateOf(if(c.editing)"编辑"else"预览")}
    var changes by remember(key){mutableStateOf(false)}
    var discard by remember(key){mutableStateOf(false)}
    var outline by remember(key){mutableStateOf(true)}
    var outlineMenu by remember(key){mutableStateOf(false)}
    var contentWidth by remember {mutableStateOf(0.dp)}
    val searchFocus=remember(key){FocusRequester()}
    var search by remember(key){mutableStateOf(false)}
    var replace by remember(key){mutableStateOf(false)}
    var replacement by remember(key){mutableStateOf("")}
    var query by remember(key){mutableStateOf("")}
    var matchIndex by remember(key){mutableIntStateOf(0)}
    var zoom by remember(key){mutableFloatStateOf(1f)}
    val sourceScroll=rememberScrollState();val reading=rememberLazyListState();val scope=rememberCoroutineScope()
    var syncScroll by remember {mutableStateOf(c.store.get("markdownSyncScroll","true").toBoolean())}
    var scrollOwner by remember(key){mutableStateOf("source")}
    var sourceLineTops by remember(key){mutableStateOf<List<Float>>(emptyList())}
    val blockHeights=remember(key){mutableMapOf<Int,Int>()}
    var rendered by remember(key){mutableStateOf(text)}
    LaunchedEffect(text){if(mode!="预览")delay(160);rendered=text}
    LaunchedEffect(text){if(field.text!=text)field=TextFieldValue(text,TextRange(field.selection.start.coerceAtMost(text.length),field.selection.end.coerceAtMost(text.length)))}
    val nodes by produceState<List<Node>>(emptyList(),rendered){value=withContext(Dispatchers.Default){generateSequence(markdownParser.parse(rendered).firstChild){it.next}.toList()}}
    val anchors=remember(nodes,sourceLineTops){markdownBlockAnchors(nodes,sourceLineTops)}
    LaunchedEffect(mode,contentWidth,syncScroll,anchors) {
        if(mode!="双栏"||contentWidth<620.dp||!syncScroll||anchors.isEmpty())return@LaunchedEffect
        launch {
            snapshotFlow {sourceScroll.value}.collectLatest {y->
                if(scrollOwner!="source")return@collectLatest
                if(y==0){reading.scrollToItem(0);return@collectLatest}
                if(sourceScroll.maxValue>0&&y>=sourceScroll.maxValue){reading.scrollToItem(nodes.size+1);return@collectLatest}
                val block=sourceToMarkdownBlock(y.toFloat(),anchors)?:return@collectLatest
                val index=block.index+1
                val known=blockHeights[block.index]
                reading.scrollToItem(index,((known?:0)*block.fraction).roundToInt())
                if(known==null)reading.layoutInfo.visibleItemsInfo.firstOrNull {it.index==index}?.let {item->
                    reading.scrollToItem(index,(item.size*block.fraction).roundToInt())
                }
            }
        }
        launch {
            snapshotFlow {reading.firstVisibleItemIndex to reading.firstVisibleItemScrollOffset}.collectLatest {(index,offset)->
                if(scrollOwner!="preview")return@collectLatest
                if(!reading.canScrollBackward){sourceScroll.scrollTo(0);return@collectLatest}
                if(!reading.canScrollForward){sourceScroll.scrollTo(sourceScroll.maxValue);return@collectLatest}
                if(index==0){sourceScroll.scrollTo(0);return@collectLatest}
                val height=reading.layoutInfo.visibleItemsInfo.firstOrNull {it.index==index}?.size?:return@collectLatest
                val position=MarkdownBlockPosition(index-1,offset.toFloat()/height.coerceAtLeast(1))
                sourceScroll.scrollTo(markdownBlockToSource(position,anchors).roundToInt().coerceIn(0,sourceScroll.maxValue))
            }
        }
    }
    fun Modifier.ownsScroll(owner:String)=pointerInput(owner) {awaitPointerEventScope {
        while(true){val event=awaitPointerEvent(PointerEventPass.Initial)
            if(event.type==PointerEventType.Scroll||event.type==PointerEventType.Press)scrollOwner=owner
        }
    }}.onPreviewKeyEvent {scrollOwner=owner;false}
    fun nodeText(node:Node):String=if(node is org.commonmark.node.Text)node.literal else generateSequence(node.firstChild){it.next}.joinToString(""){nodeText(it)}
    val headings=remember(nodes){nodes.mapIndexedNotNull {index,node->if(node is org.commonmark.node.Heading)Triple(index,node.level,nodeText(node))else null}}
    val matches=remember(text,query){markdownMatches(text,query)}
    LaunchedEffect(query){matchIndex=0}
    LaunchedEffect(search){if(search){withFrameNanos{};runCatching {searchFocus.requestFocus()}}}
    LaunchedEffect(mode){if(mode in setOf("编辑","双栏")&&!search){withFrameNanos{};runCatching {focus.requestFocus()}}}
    fun openLink(target:String) {
        if(target.startsWith('#')) {
            scrollOwner="preview"
            val anchor=java.net.URLDecoder.decode(target.removePrefix("#"),"UTF-8")
            val heading=headings.firstOrNull {it.third==anchor||it.third.lowercase().replace(Regex("[^\\p{L}\\p{N} _-]"),"").replace(' ','-')==anchor}
            if(heading!=null)scope.launch {reading.animateScrollToItem(heading.first+1)}
        }else if(target.startsWith("https://")||target.startsWith("http://"))runCatching {DesktopFiles.openLink(target)}.onFailure {c.notice="链接无法打开"}
        else relativeDocumentPath(tab.document.path,target)?.let {c.openDocument(it,tab.sourceSession,tab.profile)}
    }
    val dirty=text!=tab.document.content
    val line=text.take(field.selection.start).count {it=='\n'}+1
    val column=field.selection.start-(text.lastIndexOf('\n',(field.selection.start-1).coerceAtLeast(-1)))
    fun update(value:TextFieldValue,record:Boolean=true) {
        if(record)history.record(field,value)
        field=value;c.setDocumentText(tab,value.text)
    }
    fun flushed(action:()->Unit){c.flushEditor?.invoke(action)?:action()}
    fun selectMode(value:String) {flushed {changes=false;mode=value;c.editing=value!="预览"}}
    fun format(action:String) {if(mode=="预览")selectMode("编辑");val target=toolbarSelection?.takeIf {it.text==field.text}?:field;toolbarSelection=null;update(formatMarkdown(target,action));scope.launch {delay(30);runCatching {focus.requestFocus()}}}
    fun find(delta:Int) {
        if(matches.isEmpty())return
        scrollOwner="source"
        matchIndex=Math.floorMod(matchIndex+delta,matches.size)
        val match=matches[matchIndex];selectMode(if(mode=="双栏")mode else"编辑")
        field=field.copy(selection=TextRange(match.first,match.last+1),composition=null)
        scope.launch {delay(30);runCatching {focus.requestFocus()}}
    }
    // Source edits are already committed to the local draft; no asynchronous web editor to flush.
    DisposableEffect(key,mode,changes) {
        val flush:((()->Unit)->Unit)?=if(mode=="排版编辑"&&!changes)richHandle::flush else null
        c.flushEditor=flush
        onDispose {if(c.flushEditor===flush)c.flushEditor=null}
    }

    Column(modifier.background(colors.surface).onPreviewKeyEvent {event->
        val mod=DesktopHost.primaryModifier(event.isMetaPressed,event.isCtrlPressed)&&!event.isAltPressed
        if(event.type!=KeyEventType.KeyDown)false else when {
            mod&&event.key==Key.F->{search=true;true}
            event.key==Key.Escape&&search->{search=false;true}
            field.composition!=null->false
            mod&&event.key==Key.B&&mode in setOf("编辑","双栏")->{format("bold");true}
            mod&&event.key==Key.H->{search=true;replace=true;true}
            mod&&event.key==Key.X&&event.isShiftPressed&&mode in setOf("编辑","双栏")->{format("strike");true}
            mod&&event.key==Key.I&&mode in setOf("编辑","双栏")->{format("italic");true}
            mod&&event.key==Key.Z&&mode in setOf("编辑","双栏")->{(if(event.isShiftPressed)history.redo(field)else history.undo(field))?.let {update(it,false)};true}
            mod&&event.key==Key.Y&&mode in setOf("编辑","双栏")->{history.redo(field)?.let {update(it,false)};true}
            else->false
        }
    }) {
        val tabs=c.openDocuments.values.filter {it.profile==tab.profile}
        if(tabs.size>1)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            tabs.forEach {other->DeskChip(key==c.documentKey(other),{c.selectDocument(other)},label={Glyph("document",Modifier.size(14.dp));Spacer(Modifier.width(5.dp));Text(other.document.name,Modifier.widthIn(max=175.dp),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)})}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            FileEmblem(tab.document.name,Modifier.size(32.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(tab.document.name,fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                SubtleText(if(c.savingDocument)"正在保存到服务器…"else if(dirty)"本机草稿已保留"else"已与服务器同步")
            }
            Hint("保存 · ${shortcutKey()} S"){SmallButton(if(c.savingDocument)"保存中…"else"保存",{c.saveWithEditor()},true,enabled=dirty&&!c.savingDocument)}
            Hint("收起文档"){DeskIconButton(onClick={flushed {if(c.isDirty())discard=true else {c.closeDocument(tab);if(c.document==null)c.documentSplit=false}}}){Glyph("close",Modifier.size(18.dp))}}
        }
        HorizontalDivider(color=colors.outline.copy(alpha=.55f))
        FlowRow(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            SegmentTabs(listOf("预览","编辑","双栏"),if(changes)""else mode,onSelect=::selectMode)
            if(mode=="双栏")Hint("双向同步滚动，按文档段落对齐") {
                DeskTextButton(onClick={syncScroll=!syncScroll;c.savePreference("markdownSyncScroll",syncScroll.toString())},modifier=Modifier.semantics {testTag="markdown-sync-toggle"}) {
                    Glyph("link",Modifier.size(16.dp),if(syncScroll)colors.primary else colors.onSurfaceVariant);Spacer(Modifier.width(5.dp));Text(tr(if(syncScroll)"同步滚动"else"独立滚动"),fontSize=12.sp)
                }
            }
            Hint("查找文档 · ${shortcutKey()} F"){DeskIconButton(onClick={search=!search}){Glyph("search",Modifier.size(18.dp))}}
            Box {
                Hint(if(outline&&contentWidth>=820.dp&&mode=="预览")"收起目录"else"显示目录") {
                    DeskIconButton(onClick={if(contentWidth>=820.dp&&mode=="预览")outline=!outline else outlineMenu=true},enabled=headings.isNotEmpty(),modifier=Modifier.semantics {testTag="markdown-outline"}){Glyph("outline",Modifier.size(18.dp),if(outline)colors.primary else colors.onSurfaceVariant)}
                }
                DeskMenu(outlineMenu,{outlineMenu=false},Modifier.widthIn(max=300.dp).heightIn(max=380.dp)) {
                    MenuSectionLabel("本页目录")
                    headings.forEach {heading->DeskMenuItem(text={Text(heading.third,maxLines=2,overflow=TextOverflow.Ellipsis)},leadingIcon={Glyph("heading")},onClick={outlineMenu=false;selectMode("预览");scope.launch {delay(40);reading.animateScrollToItem(heading.first+1)}})}
                }
            }
            if(dirty)SmallButton(if(changes)"返回内容"else"查看修改",{flushed {changes=!changes}})
            Box {
                var more by remember {mutableStateOf(false)}
                Hint("文档操作"){DeskIconButton(onClick={more=true}){Glyph("more",Modifier.size(18.dp))}}
                DeskMenu(more,{more=false}) {
                    DeskMenuItem(text={Text(tr(if(dirty)"另存本机"else"下载文档"))},leadingIcon={Glyph("download")},onClick={more=false;saveLocalDocument(c,tab)})
                    DeskMenuItem(text={Text(tr("复制 Markdown"))},leadingIcon={Glyph("copy")},onClick={more=false;DesktopFiles.copy(text);c.notice="Markdown 已复制"})
                    DeskMenuItem(text={Text(tr("复制路径"))},leadingIcon={Glyph("link")},onClick={more=false;c.copyDocumentPath();c.notice="服务器路径已复制"})
                    DeskMenuItem(text={Text(tr("排版编辑 · 基础 Markdown"))},leadingIcon={Glyph("edit")},enabled=!requiresSourceEditor(text),onClick={more=false;selectMode("排版编辑")})
                    MenuDivider()
                    listOf(.9f,1f,1.15f,1.3f).forEach {scale->DeskMenuItem(text={Text("${(scale*100).toInt()}% · ${tr("阅读字号")}")},trailingIcon={if(zoom==scale)Glyph("check",Modifier.size(16.dp))},onClick={zoom=scale;more=false})}
                }
            }
            DeskTextButton(onClick={c.discussDocument()},enabled=!c.savingDocument){Glyph("history",Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text(tr("基于文档提问"))}
        }
        if(search)Row(Modifier.fillMaxWidth().background(colors.surfaceVariant.copy(alpha=.6f)).padding(horizontal=18.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CompactInput(query,{query=it},"查找文档内容",Modifier.weight(1f).focusRequester(searchFocus).semantics {testTag="markdown-search-field"}.onPreviewKeyEvent {e->if(e.type==KeyEventType.KeyDown&&e.key==Key.Enter){find(if(e.isShiftPressed)-1 else 1);true}else false})
            DeskTextButton(onClick={replace=!replace}){Text(tr("替换"),fontSize=12.sp)}
            SubtleText(if(matches.isEmpty())"0 / 0"else"${matchIndex.coerceAtMost(matches.lastIndex)+1} / ${matches.size}")
            Hint("上一个匹配"){DeskIconButton(onClick={find(-1)},enabled=matches.isNotEmpty()){Glyph("arrow-up",Modifier.size(16.dp))}}
            Hint("下一个匹配"){DeskIconButton(onClick={find(1)},enabled=matches.isNotEmpty()){Glyph("arrow-down",Modifier.size(16.dp))}}
            Hint("关闭查找"){DeskIconButton(onClick={search=false}){Glyph("close",Modifier.size(16.dp))}}
        }
        if(search&&replace)FlowRow(Modifier.fillMaxWidth().background(colors.surfaceVariant.copy(alpha=.6f)).padding(horizontal=18.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CompactInput(replacement,{replacement=it},"替换为",Modifier.widthIn(min=160.dp,max=300.dp))
            SmallButton("替换当前",{selectMode(if(mode=="双栏")mode else"编辑");update(replaceMarkdownMatch(field,query,replacement));c.notice="已替换，可撤销"},enabled=matches.isNotEmpty())
            SmallButton("全部替换",{val count=matches.size;selectMode(if(mode=="双栏")mode else"编辑");update(replaceMarkdownMatch(field,query,replacement,true));c.notice="已替换 $count 处，可撤销"},enabled=matches.isNotEmpty())
        }
        if(mode in setOf("编辑","双栏")&&!changes)Row(Modifier.pointerInput(key){awaitPointerEventScope {while(true){val event=awaitPointerEvent(PointerEventPass.Initial);if(event.type==PointerEventType.Press)toolbarSelection=latestField}}}.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            Picker("标题",listOf("h1","h2","h3","h4","h5","h6"),{"${it.last()} 级标题"}){format(it)}
            listOf(Triple("bold","加粗","bold"),Triple("italic","斜体","italic"),Triple("strike","删除线","strike"),Triple("link","链接","link"),Triple("list-bullet","无序列表","bullet"),Triple("list-number","有序列表","numbered"),Triple("check-square","任务清单","task"),Triple("quote","引用","quote"),Triple("code","代码块","code"),Triple("table","插入表格","table")).forEach {(icon,label,action)->
                Hint(label){DeskIconButton(onClick={format(action)},modifier=Modifier.semantics {testTag="markdown-$action"}){Glyph(icon,Modifier.size(18.dp),colors.onSurfaceVariant)}}
            }
            Spacer(Modifier.width(8.dp));SubtleText("Markdown")
        }
        if(mode=="排版编辑"&&!changes)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
            Picker("正文",listOf("p","h1","h2","h3"),{when(it){"h1"->"一级标题";"h2"->"二级标题";"h3"->"三级标题";else->"正文"}},richHandle::command)
            listOf(Triple("bold","加粗","bold"),Triple("italic","斜体","italic"),Triple("list-bullet","无序列表","ul"),Triple("list-number","有序列表","ol")).forEach {(icon,label,command)->Hint(label){DeskIconButton(onClick={richHandle.command(command)}){Glyph(icon,Modifier.size(18.dp))}}}
            SubtleText("排版编辑")
        }
        HorizontalDivider(color=colors.outline.copy(alpha=.4f))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            SideEffect {contentWidth=maxWidth}
            val wide=maxWidth>=820.dp
            val both=mode=="双栏"&&maxWidth>=620.dp
            @Composable fun Preview(mod:Modifier) {
                val baseDensity=LocalDensity.current
                CompositionLocalProvider(LocalDocumentReading provides true,LocalMarkdownImage provides {path->MarkdownAssetImage(c,tab,path)},LocalDensity provides Density(baseDensity.density,baseDensity.fontScale*zoom)) {
                    Box(mod.ownsScroll("preview").background(colors.surface),contentAlignment=Alignment.TopCenter) {
                        QuoteSelection("基于选中文字提问",{c.discussDocument(it)}) {
                            SelectionContainer {
                                LazyColumn(state=reading,modifier=Modifier.widthIn(max=820.dp).fillMaxSize().semantics {testTag="document-content"},
                                    contentPadding=PaddingValues(horizontal=if(both)28.dp else 48.dp,vertical=36.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                    item {Row(Modifier.padding(bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {SubtleText("文档");SubtleText("· ${text.length} 字符 · 约 ${(text.length/600+1)} 分钟")}}
                                    itemsIndexed(nodes){index,node->Box(Modifier.fillMaxWidth().onSizeChanged {blockHeights[index]=it.height}.semantics {testTag="markdown-block-$index"}){MarkdownNode(node,::openLink)}}
                                    item {Spacer(Modifier.height(80.dp))}
                                }
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(reading),Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical=8.dp).width(6.dp))
                    }
                }
            }
            if(changes)MarkdownChanges(tab.document.content,text,Modifier.fillMaxSize())
            else if(mode=="排版编辑")key(key,mode) {
                WebDocument(editableDocumentHtml(text,colors.surface.cssHex(),colors.onSurface.cssHex(),colors.onSurfaceVariant.cssHex(),colors.primary.cssHex(),colors.surfaceVariant.cssHex(),colors.outline.cssHex()),Modifier.fillMaxSize(),true,richHandle){c.setDocumentText(tab,it)}
            }
            else Row(Modifier.fillMaxSize()) {
                if(mode!="预览")MarkdownSourceEditor(field,{update(it)},focus,sourceScroll,Modifier.weight(1f).fillMaxHeight().ownsScroll("source"),query,onLines={sourceLineTops=it}) {format(it)}
                if(both)VerticalDivider(color=colors.outline.copy(alpha=.7f))
                if(mode=="预览"||both)Preview(Modifier.weight(1f).fillMaxHeight())
                if(outline&&headings.isNotEmpty()&&wide&&mode=="预览") {
                    VerticalDivider(color=colors.outline.copy(alpha=.45f))
                    Column(Modifier.width(176.dp).fillMaxHeight().padding(horizontal=12.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        SubtleText("本页目录",Modifier.padding(start=8.dp,bottom=12.dp))
                        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            val active=headings.lastOrNull {it.first+1<=reading.firstVisibleItemIndex}?:headings.first()
                            headings.forEach {heading->
                                Text(heading.third,Modifier.fillMaxWidth().desktopClick(selected=heading==active){scope.launch {reading.animateScrollToItem(heading.first+1)}}
                                    .padding(start=(8+(heading.second-1)*10).dp,end=8.dp,top=7.dp,bottom=7.dp),fontSize=12.sp,lineHeight=19.sp,
                                    color=if(heading==active)colors.primary else colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if(mode=="双栏"&&!both)Text(tr("窗口加宽后显示并排预览"),Modifier.align(Alignment.BottomEnd).padding(12.dp).background(colors.surface,RoundedCornerShape(6.dp)).padding(8.dp),fontSize=11.sp,color=colors.onSurfaceVariant)
        }
        HorizontalDivider(color=colors.outline.copy(alpha=.5f))
        Row(Modifier.fillMaxWidth().semantics {testTag="document-status"}.padding(horizontal=18.dp,vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(if(dirty)Color(0xFFDBA048)else Color(0xFF3AA78A),RoundedCornerShape(3.dp)))
            SubtleText(if(dirty)"本机草稿 · 尚未保存到服务器"else"服务器版本",Modifier.weight(1f))
            SubtleText(if(mode=="预览")"${text.length} 字符"else"第 $line 行，第 $column 列 · ${text.length} 字符")
        }
    }
    if(discard)HermesDialog(onDismissRequest={discard=false},title={Text(tr("文档尚未保存"))},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(tab.document.name,fontWeight=FontWeight.Medium);Text(tr("可以保留本机草稿，稍后从文件页继续编辑。"),color=colors.onSurfaceVariant)}},
        confirmButton={DeskTextButton(onClick={c.document=null;c.documentSplit=false;discard=false}){Text(tr("保留草稿并收起"))}},
        dismissButton={Row {DeskTextButton(onClick={discard=false}){Text(tr("继续编辑"))};DeskTextButton(onClick={c.closeDocument(tab,true);if(c.document==null)c.documentSplit=false;discard=false}){Text(tr("放弃修改"))}}})
}

@Composable private fun MarkdownSourceEditor(value:TextFieldValue,onChange:(TextFieldValue)->Unit,focus:FocusRequester,scroll:ScrollState,modifier:Modifier,query:String,onLines:(List<Float>)->Unit,format:(String)->Unit) {
    val colors=MaterialTheme.colorScheme
    var layout by remember {mutableStateOf<TextLayoutResult?>(null)}
    var focused by remember {mutableStateOf(false)}
    val density=LocalDensity.current
    val lines=remember(value.text){listOf(0)+value.text.indices.filter {value.text[it]=='\n'}.map {it+1}}
    val transform=remember(colors,query){VisualTransformation {raw->
        val result=AnnotatedString.Builder(raw)
        Regex("(?m)^#{1,6} .+$").findAll(raw.text).forEach {result.addStyle(SpanStyle(color=colors.primary,fontWeight=FontWeight.SemiBold),it.range.first,it.range.last+1)}
        Regex("(?m)^\\s*(?:>|[-*+] |\\d+\\. |```).*?$").findAll(raw.text).forEach {result.addStyle(SpanStyle(color=colors.onSurfaceVariant),it.range.first,it.range.last+1)}
        Regex("\\*\\*[^*\\n]+\\*\\*").findAll(raw.text).forEach {result.addStyle(SpanStyle(fontWeight=FontWeight.SemiBold),it.range.first,it.range.last+1)}
        markdownMatches(raw.text,query).forEach {result.addStyle(SpanStyle(background=colors.primary.copy(alpha=.16f)),it.first,it.last+1)}
        TransformedText(result.toAnnotatedString(),OffsetMapping.Identity)
    }}
    LaunchedEffect(value.selection,layout,focused,scroll.viewportSize) {
        // The first text layout can arrive before the scroll viewport is measured.
        // Scrolling against a zero-height viewport hid the first line on entering Edit.
        layout?.takeIf {focused&&scroll.viewportSize>0}?.let {result->
            val rect=result.getCursorRect(value.selection.end.coerceIn(0,value.text.length))
            val inset=with(density){24.dp.roundToPx()}
            val top=inset+rect.top.toInt()
            val bottom=inset+rect.bottom.toInt()+inset
            if(bottom>scroll.value+scroll.viewportSize)scroll.scrollTo((bottom-scroll.viewportSize).coerceAtLeast(0))
            else if(top<scroll.value+inset)scroll.scrollTo((top-inset).coerceAtLeast(0))
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides SourceEditorBringIntoView) {
    Box(modifier.background(colors.surface).semantics {testTag="markdown-source-pane"}) {
        Row(Modifier.fillMaxSize().verticalScroll(scroll).semantics {testTag="markdown-source-scroll"}.padding(top=24.dp,bottom=120.dp)) {
            Box(Modifier.width(42.dp).height(with(density){(layout?.size?.height?:0).toDp()})) {
                layout?.let {result->
                    fun sourceLine(y:Float):Int {
                        val offset=result.getLineStart(result.getLineForVerticalPosition(y))
                        val found=lines.binarySearch(offset)
                        return (if(found>=0)found else -found-2).coerceIn(0,lines.lastIndex)
                    }
                    val first=sourceLine((scroll.value-100).coerceAtLeast(0).toFloat())
                    val last=sourceLine((scroll.value+scroll.viewportSize+100).toFloat())
                    for(index in first..last){val offset=lines[index]
                    val y=with(density){result.getLineTop(result.getLineForOffset(offset.coerceAtMost(value.text.length))).toDp()}
                    Text((index+1).toString(),Modifier.align(Alignment.TopEnd).offset(y=y).padding(end=12.dp),fontSize=11.sp,lineHeight=25.sp,fontFamily=FontFamily.Monospace,color=colors.onSurfaceVariant.copy(alpha=.55f))
                }}
            }
            BasicTextField(value,onChange,modifier=Modifier.weight(1f).padding(end=24.dp).heightIn(min=240.dp).focusRequester(focus).onFocusChanged {focused=it.isFocused}
                .semantics {testTag="markdown-source";contentDescription=tr("Markdown 源码编辑器")}
                .onPreviewKeyEvent {e->when {
                    e.type!=KeyEventType.KeyDown||value.composition!=null->false
                    DesktopHost.primaryModifier(e.isMetaPressed,e.isCtrlPressed)&&!e.isAltPressed&&e.key in setOf(Key.MoveHome,Key.MoveEnd)->{
                        val destination=if(e.key==Key.MoveHome)0 else value.text.length
                        onChange(value.copy(selection=TextRange(if(e.isShiftPressed)value.selection.start else destination,destination),composition=null));true
                    }
                    e.key==Key.Tab&&!e.isCtrlPressed&&!e.isMetaPressed->{format(if(e.isShiftPressed)"outdent"else"indent");true}
                    e.key==Key.Enter&&!e.isShiftPressed&&!e.isCtrlPressed&&!e.isMetaPressed&&!e.isAltPressed->{onChange(continueMarkdownLine(value));true}
                    else->false
                }},
                textStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace,fontSize=14.sp,lineHeight=25.sp,color=colors.onSurface),
                cursorBrush=SolidColor(colors.primary),visualTransformation=transform,onTextLayout={result->
                    layout=result
                    val inset=with(density){24.dp.toPx()}
                    onLines(lines.map {offset->inset+result.getLineTop(result.getLineForOffset(offset.coerceAtMost(value.text.length)))}+listOf(inset+result.size.height))
                })
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical=8.dp).width(6.dp))
    }
    }
}

@Composable private fun MarkdownChanges(original:String,current:String,modifier:Modifier) {
    val diff=remember(original,current){documentDiff(original,current)};val colors=MaterialTheme.colorScheme;val scroll=rememberLazyListState()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SubtleText("与打开时的服务器版本比较");StatusPill("+${diff.count {it.kind=='+'}}");StatusPill("−${diff.count {it.kind=='-'}}",error=true)
        }
        Box(Modifier.weight(1f)) {
            SelectionContainer {LazyColumn(state=scroll,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp)) {items(diff) {line->
                val ink=when(line.kind){'+'->Color(0xFF238657);'-'->colors.error;else->colors.onSurfaceVariant}
                Text("${line.kind} ${line.text}",Modifier.fillMaxWidth().background(ink.copy(alpha=if(line.kind==' ')0f else .07f)).padding(horizontal=10.dp,vertical=4.dp),fontFamily=FontFamily.Monospace,fontSize=13.sp,lineHeight=22.sp,color=ink)
            }}}
            VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
        }
    }
}
