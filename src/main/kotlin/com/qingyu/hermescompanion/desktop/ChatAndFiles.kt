@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import com.qingyu.hermescompanion.data.ChatInsightParser
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import org.commonmark.node.Node
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.BulletList
import org.commonmark.node.OrderedList
import org.commonmark.node.BlockQuote
import org.commonmark.node.ThematicBreak
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Code
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Emphasis
import org.commonmark.node.Link
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Composable fun FilesView(c:DesktopController) {
    c.document?.let {DocumentPanel(c,it,Modifier.fillMaxSize())} ?: Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Glyph("document",Modifier.size(32.dp),MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp));Heading("选择右侧文件查看或编辑")
        if(!c.filesPanelOpen)SmallButton("展开文件栏",{c.setFilesPanel(true)})
        if(c.currentSession!=null)DeskTextButton(onClick={c.page=Page.CHAT}){Text(tr("返回聊天"))}
    }
}

@Composable fun FileBrowserPanel(c:DesktopController,modifier:Modifier=Modifier) {
    var query by remember {mutableStateOf("")};var section by c::fileSection
    var path by remember(c.listing?.path){mutableStateOf(c.listing?.path.orEmpty())}
    var addressEditing by remember {mutableStateOf(false)}
    var options by remember {mutableStateOf(false)}
    LaunchedEffect(c.profile,c.project?.id) {if(c.listing==null)c.browse(navigate=false)}
    Column(modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)){Picker(c.project?.name ?: "全部项目",listOf<HermesProject?>(null)+c.projects,{it?.name ?: "全部项目"}) {c.chooseProject(it,navigate=false)}}
            Hint("刷新文件"){DeskIconButton(onClick={c.browse(c.listing?.path,navigate=false)},enabled=!c.filesLoading){Glyph("refresh",Modifier.size(17.dp))}}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("目录","最近产物","本机草稿").forEach {tab->DeskChip(section==tab,{section=tab},label={Text(tr(tab),fontSize=11.sp)})}}
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CompactInput(query,{query=it},"搜索文件名",Modifier.weight(1f))
            Box {Hint("文件显示选项"){DeskIconButton(onClick={options=true}){Glyph("more",Modifier.size(18.dp))}}
                DeskMenu(options,{options=false}) {
                    DeskMenuItem(text={Text(tr(if(c.showHiddenFiles)"隐藏系统文件"else"显示隐藏文件"))},leadingIcon={Glyph(if(c.showHiddenFiles)"eye-off"else"eye")},onClick={c.showHiddenFiles=!c.showHiddenFiles;c.savePreference("showHiddenFiles",c.showHiddenFiles.toString());options=false})
                    DeskMenuItem(text={Text(tr("按名称排序"))},leadingIcon={Glyph("sort")},onClick={c.fileSort="名称";options=false})
                    DeskMenuItem(text={Text(tr("按类型排序"))},leadingIcon={Glyph("sort")},onClick={c.fileSort="类型";options=false})
                    DeskMenuItem(text={Text(tr("输入完整路径"))},leadingIcon={Glyph("folder")},onClick={addressEditing=true;options=false})
                }
            }
        }
        when(section) {
            "最近产物"->{
                val files=c.recentArtifacts.filter {it.profile==c.profile&&artifactMatchesProject(it,c.project)&&it.name.contains(query,true)}
                if(c.artifactsIndexing)SubtleText(c.artifactsIndexProgress,maxLines=2)
                c.artifactsIndexError?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp);SmallButton("重试整理",{c.syncArtifactIndex()})}
                if(files.isEmpty()&&!c.artifactsIndexing)Caption(if(query.isNotBlank())"没有匹配的文件"else "当前项目还没有已整理的产物。")
                LazyColumn(Modifier.weight(1f)) {items(files,key={it.sessionId+":"+it.path}) {file->FileListRow(file.name,file.sessionTitle,false,false,{c.openArtifact(file)})}}
            }
            "本机草稿"->{
                val files=c.openDocuments.values.filter {it.profile==c.profile&&c.edits[c.documentKey(it)]?.let {e->e!=it.document.content}==true&&it.document.name.contains(query,true)}
                if(files.isEmpty())Caption("没有未保存的文档。")
                LazyColumn(Modifier.weight(1f)){items(files,key={c.documentKey(it)}) {tab->FileListRow(tab.document.name,"未保存到服务器",false,c.documentKey(tab)==c.document?.let(c::documentKey),{c.flushEditor?.invoke {c.reopenDraft(c.documentKey(tab))} ?: c.reopenDraft(c.documentKey(tab))})}}
            }
            else->{
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    c.listing?.parent?.let {parent->Hint("上一级目录"){DeskIconButton(onClick={c.browse(parent,navigate=false)}){Glyph("back",Modifier.size(16.dp))}}}
                    if(addressEditing) {
                        CompactInput(path,{path=it},"服务器目录路径",Modifier.weight(1f).onPreviewKeyEvent {event->if(event.type==KeyEventType.KeyDown&&event.key==Key.Enter){c.browse(path.takeIf(String::isNotBlank),navigate=false);addressEditing=false;true}else false})
                        DeskTextButton(onClick={c.browse(path.takeIf(String::isNotBlank),navigate=false);addressEditing=false}){Text(tr("前往"),fontSize=12.sp)}
                    }else Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                        val parts=c.listing?.path.orEmpty().split('/').filter {it.isNotBlank()}
                        Hint(c.listing?.path.orEmpty()){DeskTextButton(onClick={addressEditing=true}){Text("/",fontSize=12.sp)}}
                        parts.forEachIndexed {index,part->
                            DeskTextButton(onClick={c.browse("/"+parts.take(index+1).joinToString("/"),navigate=false)}){Text(part,fontSize=12.sp)}
                            if(index<parts.lastIndex)Glyph("chevron-right",Modifier.size(12.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if(c.filesLoading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                c.filesLoadError?.let {message->
                    Text(message,color=MaterialTheme.colorScheme.error,fontSize=12.sp,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Row {SmallButton("重试读取",{c.browse(c.requestedFilesPath,navigate=false)});DeskTextButton(onClick={c.showDetails("读取文件失败",message)}){Text(tr("详情"))}}
                    if(c.listing!=null)SubtleText("下方仍显示上次成功读取的目录。",maxLines=2)
                }
                val entries=c.listing?.entries.orEmpty().filter {it.name.contains(query,true)&&(c.showHiddenFiles||!it.name.startsWith("."))}.sortedWith(compareByDescending<WorkspaceEntry>{it.isDirectory}.thenBy {if(c.fileSort=="类型")it.name.substringAfterLast('.',"")else ""}.thenBy {it.name.lowercase()})
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val state=rememberLazyListState()
                    LazyColumn(state=state,modifier=Modifier.fillMaxSize()) {
                        if(entries.isEmpty())item{Caption(if(c.filesLoading)"正在读取文件…"else if(query.isNotEmpty())"没有匹配的文件"else if(c.listing==null)"选择项目，或输入服务器目录。"else"此目录为空",Modifier.padding(vertical=12.dp))}
                        items(entries,key={it.path}) {entry->ContextMenuArea(items={listOf(ContextMenuItem(tr("复制路径")){DesktopFiles.copy(entry.path)})}) {
                            FileListRow(entry.name,if(entry.isDirectory)"文件夹"else "${entry.name.substringAfterLast('.').uppercase()} · ${fileSizeLabel(entry.size)}",entry.isDirectory,c.document?.document?.path==entry.path,{if(entry.isDirectory)c.browse(entry.path,navigate=false)else c.openDocument(entry.path,null)})
                        }}
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp))
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.6f))
                Caption("${entries.size} · ${tr("个项目")}")
            }
        }
    }
}
@Composable private fun FileListRow(name:String,detail:String,directory:Boolean,selected:Boolean,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().desktopClick(selected=selected,onClick=onClick).padding(horizontal=6.dp,vertical=if(LocalDesktopDesign.current.compact)6.dp else 10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Glyph(fileGlyph(name,directory),Modifier.size(18.dp),MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Hint(name){Text(name,fontSize=13.sp,lineHeight=18.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
            Text(tr(detail),fontSize=11.sp,lineHeight=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if(directory)Glyph("chevron-right",Modifier.size(14.dp),MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun CompactInput(value:String,onValueChange:(String)->Unit,placeholder:String,modifier:Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme;val shape=LocalDesktopDesign.current.controlShape
    var focused by remember{mutableStateOf(false)}
    val search=placeholder.contains("搜索")||placeholder.contains("查找")
    androidx.compose.foundation.text.BasicTextField(value,onValueChange,singleLine=true,
        modifier=modifier.fillMaxWidth().onFocusChanged {focused=it.isFocused}.background(if(focused)colors.surface else colors.surfaceVariant,shape)
            .border(1.dp,if(focused)colors.primary else Color.Transparent,shape).padding(horizontal=10.dp,vertical=8.dp),
        textStyle=MaterialTheme.typography.bodySmall.copy(color=colors.onSurface,fontSize=13.sp),cursorBrush=SolidColor(colors.primary),
        decorationBox={inner->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            if(search)Glyph("search",Modifier.size(15.dp),colors.onSurfaceVariant)
            Box(Modifier.weight(1f)){if(value.isEmpty())Text(tr(placeholder),fontSize=13.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis);inner()}
        }})
}

@Composable fun DocumentPanel(c:DesktopController,tab:DocumentTab,modifier:Modifier=Modifier) {
    if(tab.document.name.substringAfterLast('.',"").lowercase() in setOf("md","markdown")){MarkdownWorkspace(c,tab,modifier);return}
    val handle=remember(tab.profile,tab.document.path) {WebEditorHandle()}
    val name=tab.document.name.lowercase();val textFile=tab.document.mimeType.startsWith("text/")||name.substringAfterLast('.') in setOf("md","markdown","json","yaml","yml","xml","csv","log")
    val markdown=name.endsWith(".md")||name.endsWith(".markdown")
    var sourceMode by remember(tab.profile,tab.document.path){mutableStateOf(requiresSourceEditor(tab.document.content))}
    var changes by remember(tab.profile,tab.document.path){mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    var zoom by remember {mutableStateOf(1f)}
    val reading=rememberLazyListState();val scope=rememberCoroutineScope()
    val nodes=remember(c.documentText(),markdown){if(markdown)generateSequence(markdownParser.parse(c.documentText()).firstChild){it.next}.toList()else emptyList()}
    fun nodeText(node:Node):String=if(node is org.commonmark.node.Text)node.literal else generateSequence(node.firstChild){it.next}.joinToString(""){nodeText(it)}
    val headings=nodes.mapIndexedNotNull {index,node->if(node is org.commonmark.node.Heading)index to nodeText(node)else null}
    DisposableEffect(handle,c.editing,name,sourceMode,changes) {
        val flush:((()->Unit)->Unit)?=if(c.editing&&!changes&&!sourceMode&&markdown)handle::flush else null
        c.flushEditor=flush
        onDispose {if(c.flushEditor===flush)c.flushEditor=null}
    }
    fun flushed(action:()->Unit){c.flushEditor?.invoke(action)?:action()}
    val colors=MaterialTheme.colorScheme
    Column(modifier.background(colors.surface)) {
        val tabs=c.openDocuments.values.filter {it.profile==tab.profile}
        if(tabs.size>1)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            tabs.forEach {other->Hint(other.document.name){DeskChip(c.documentKey(other)==c.documentKey(tab),{c.selectDocument(other)},label={Text((if(c.edits[c.documentKey(other)]?.let {it!=other.document.content}==true)"● "else "")+other.document.name,Modifier.widthIn(max=190.dp),maxLines=1,overflow=TextOverflow.Ellipsis)})}}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Glyph("document",Modifier.size(18.dp),colors.primary)
            Text(tab.document.name,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Hint("收起文档"){DeskIconButton(onClick={flushed {if(c.isDirty())discard=true else {c.closeDocument(tab);if(c.document==null)c.documentSplit=false}}}){Glyph("close",Modifier.size(18.dp))}}
        }
        HorizontalDivider(color=colors.outline)
        FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(textFile)SegmentTabs(listOf("预览","编辑"),if(c.editing)"编辑"else"预览"){target->flushed {changes=false;c.editing=target=="编辑"}}
            if(c.editing&&markdown)SmallButton(if(sourceMode)"富文本"else"源码",{flushed {sourceMode=!sourceMode;changes=false}})
            if(textFile&&c.isDirty())SmallButton(if(changes)"返回内容"else"查看修改",{flushed {changes=!changes}})
            if(textFile&&c.isDirty())SmallButton(if(c.savingDocument)"保存中…"else"保存",{c.saveWithEditor()},true,enabled=!c.savingDocument)
            SmallButton(if(c.isDirty())"另存本机"else"下载",{flushed {saveLocalDocument(c,tab)}})
            if(!c.editing&&!changes&&textFile) {
                if(headings.isNotEmpty())Picker("目录",headings,{it.second}){scope.launch {reading.animateScrollToItem(it.first)}}
                Picker("${(zoom*100).toInt()}%",listOf(.9f,1f,1.1f,1.25f,1.5f),{"${(it*100).toInt()}%"}){zoom=it}
            }
            SmallButton("基于文档提问",{flushed {c.discussDocument()}},enabled=!c.savingDocument)
        }
        HorizontalDivider(color=colors.outline.copy(alpha=.5f))
        if(changes) {
            val diff=remember(tab.document.content,c.documentText()){documentDiff(tab.document.content,c.documentText())}
            SubtleText("与打开时的服务器版本比较 · +${diff.count {it.kind=='+'}} / −${diff.count {it.kind=='-'}}",Modifier.padding(12.dp),maxLines=2)
            val diffScroll=rememberLazyListState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer {LazyColumn(state=diffScroll,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp)) {items(diff) {line->
                    val color=when(line.kind){'+'->Color(0xFF238657);'-'->colors.error;else->colors.onSurfaceVariant}
                    Text("${line.kind} ${line.text}",Modifier.fillMaxWidth().background(color.copy(alpha=if(line.kind==' ').0f else .08f)).padding(horizontal=8.dp,vertical=2.dp),fontSize=13.sp,fontFamily=FontFamily.Monospace,color=color)
                }}}
                VerticalScrollbar(rememberScrollbarAdapter(diffScroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
            }
        }else if(c.editing&&textFile) {
            if(markdown&&!sourceMode) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Picker("正文",listOf("p","h1","h2","h3"),{when(it){"h1"->"一级标题";"h2"->"二级标题";"h3"->"三级标题";else->"正文"}},handle::command)
                    listOf("B" to "bold","I" to "italic","• 列表" to "ul","1. 列表" to "ol").forEach {(label,cmd)->DeskTextButton(onClick={handle.command(cmd)}){Text(label)}}
                }
                key(c.documentKey(tab),sourceMode) {WebDocument(editableDocumentHtml(c.documentText(),colors.surface.cssHex(),colors.onSurface.cssHex(),colors.onSurfaceVariant.cssHex(),colors.primary.cssHex(),colors.surfaceVariant.cssHex(),colors.outline.cssHex()),Modifier.weight(1f).fillMaxWidth(),true,handle){c.setDocumentText(tab,it)}}
            }else OutlinedTextField(c.documentText(),{c.setDocumentText(tab,it)},modifier=Modifier.weight(1f).fillMaxWidth().padding(12.dp),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace))
        }else Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                tab.document.mimeType.startsWith("image/")->ImageBytes(tab.document.bytes,tab.document.name)
                name.endsWith(".pdf")->PagedDocumentPreview(tab.document.bytes,true)
                name.substringAfterLast('.') in setOf("ppt","pptx")->PagedDocumentPreview(tab.document.bytes,false)
                name.substringAfterLast('.') in setOf("xls","xlsx")->SpreadsheetPreview(tab.document.bytes)
                name.substringAfterLast('.') in setOf("doc","docx")->HtmlFilePreview(c,tab,true)
                name.endsWith(".html")||name.endsWith(".htm")->HtmlFilePreview(c,tab)
                textFile->{
                    val baseDensity=androidx.compose.ui.platform.LocalDensity.current
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(baseDensity.density,baseDensity.fontScale*zoom)) {
                        QuoteSelection("基于选中文字提问",{c.discussDocument(it)}) {
                            SelectionContainer {
                                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
                                    LazyColumn(state=reading,modifier=Modifier.widthIn(max=1100.dp).fillMaxSize().semantics {testTag="document-content"},contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                        if(markdown)items(nodes) {node->MarkdownNode(node){target->if(target.startsWith("http://")||target.startsWith("https://"))DesktopFiles.openLink(target)else c.openDocument(target,tab.sourceSession,tab.profile)}}
                                        else item {Text(c.documentText(),fontFamily=FontFamily.Monospace)}
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(reading),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
                }
                else->EmptyState("此格式请下载查看",tab.document.name)
            }
        }
        HorizontalDivider(color=colors.outline)
        FlowRow(Modifier.fillMaxWidth().semantics {testTag="document-status"}.padding(horizontal=12.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            SubtleText(if(c.isDirty())"● 本机草稿 · 尚未保存到服务器"else"服务器版本",Modifier.padding(vertical=5.dp))
            tab.sourceSession?.let {source->DeskTextButton(onClick={flushed {c.openSession(source)}}){Text(tr("来源对话"))}}
            Hint(tab.document.path){DeskTextButton(onClick={c.copyDocumentPath();c.notice="服务器路径已复制"}){Text(tr("复制路径"))}}
        }
    }
    if(discard)HermesDialog(onDismissRequest={discard=false},title={Text(tr("文档尚未保存"))},text={Text(tr("可以保留本机草稿，稍后从文件页继续编辑。"))},confirmButton={DeskTextButton(onClick={c.document=null;c.documentSplit=false;discard=false}){Text(tr("保留草稿并收起"))}},dismissButton={Row{DeskTextButton(onClick={discard=false}){Text(tr("继续编辑"))};DeskTextButton(onClick={c.closeDocument(tab,true);if(c.document==null)c.documentSplit=false;discard=false}){Text(tr("放弃修改"))}}})
}

internal fun saveLocalDocument(c:DesktopController,tab:DocumentTab) {
    runCatching {
        val value=if(c.isDirty()) tab.document.copy(content=c.documentText(),bytes=c.documentText().toByteArray(Charsets.UTF_8)) else tab.document
        DesktopFiles.save(value)
    }.onFailure { c.error="无法保存到本机：${it.message}" }
}

internal val markdownParser=Parser.builder().includeSourceSpans(org.commonmark.parser.IncludeSourceSpans.BLOCKS).extensions(listOf(TablesExtension.create(),org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create())).build()
@Composable fun Markdown(text:String,onLink:(String)->Unit={}) {
    val root=remember(text){markdownParser.parse(text)}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { var node=root.firstChild;while(node!=null){MarkdownNode(node,onLink);node=node.next} }
}
@Composable internal fun MarkdownNode(node:Node,onLink:(String)->Unit) {
    when(node) {
        is org.commonmark.node.Heading -> {
            val reading=LocalDocumentReading.current
            Column(Modifier.fillMaxWidth().padding(top=if(reading&&node.previous!=null)when(node.level){1->24.dp;2->20.dp;else->12.dp}else 0.dp,bottom=if(reading)4.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Inline(node,onLink,if(reading)when(node.level){1->32;2->24;3->20;4->18;else->16}else when(node.level){1->27;2->21;else->18},if(node.level==1)FontWeight.Bold else FontWeight.SemiBold)
                if(reading&&node.level<=2)HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=if(node.level==1).65f else .45f))
            }
        }
        is org.commonmark.node.Paragraph -> {
            val imageRenderer=LocalMarkdownImage.current
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val imageOnly=imageRenderer!=null&&generateSequence(node.firstChild){it.next}.all {it is org.commonmark.node.Image || it is SoftLineBreak || it is org.commonmark.node.Text&&it.literal.isBlank()}
                if(!imageOnly)Inline(node,onLink)
                if(imageRenderer!=null) {
                    val images=remember(node){buildList<String>{node.accept(object:org.commonmark.node.AbstractVisitor(){override fun visit(image:org.commonmark.node.Image){add(image.destination)}})}}
                    images.forEach {imageRenderer(it)}
                }
            }
        }
        is FencedCodeBlock -> CodeBlock(node.literal,node.info.orEmpty())
        is IndentedCodeBlock -> CodeBlock(node.literal)
        is BulletList, is OrderedList -> {
            val entries=remember(node) { generateSequence(node.firstChild) { it.next }.toList() }
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                entries.forEachIndexed { index,entry ->
                    val task=(entry.firstChild?.firstChild as? org.commonmark.node.Text)?.literal?.let {Regex("^\\[([ xX])]\\s+").find(it)}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(task!=null)Glyph(if(task.groupValues[1].equals("x",true))"check-square"else"checkbox",Modifier.width(22.dp).height(26.dp),MaterialTheme.colorScheme.primary)
                        else Text(if(node is OrderedList)"${node.markerStartNumber + index}." else "•",Modifier.width(22.dp),lineHeight=if(LocalDocumentReading.current)28.sp else 23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) { var content=entry.firstChild;while(content!=null){if(task!=null&&content===entry.firstChild)Inline(content,onLink,stripTaskMarker=true)else MarkdownNode(content,onLink);content=content.next} }
                    }
                }
            }
        }
        is BlockQuote -> {
            val color=MaterialTheme.colorScheme.primary
            Column(Modifier.fillMaxWidth().background(color.copy(alpha=.045f),RoundedCornerShape(6.dp)).drawBehind {drawRect(color.copy(alpha=.55f),size=androidx.compose.ui.geometry.Size(3.dp.toPx(),size.height))}.padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                var child=node.firstChild;while(child!=null){MarkdownNode(child,onLink);child=child.next}
            }
        }
        is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical=if(LocalDocumentReading.current)12.dp else 0.dp),color=MaterialTheme.colorScheme.outline)
        is TableBlock -> BoxWithConstraints(Modifier.fillMaxWidth()) {
            val colors=MaterialTheme.colorScheme
            val columns=generateSequence(node.firstChild?.firstChild?.firstChild){it.next}.count().coerceAtLeast(1)
            val cellWidth=((maxWidth-(columns-1).dp)/columns).coerceIn(132.dp,380.dp)
            val shape=RoundedCornerShape(8.dp)
            Column(Modifier.horizontalScroll(rememberScrollState()).clip(shape).border(1.dp,colors.outline.copy(alpha=.75f),shape)) {
                var group=node.firstChild;var rowNumber=0
                while(group!=null){var row=group.firstChild;while(row!=null){
                    val heading=group is TableHead
                    Row(Modifier.height(IntrinsicSize.Min).background(if(heading)colors.surfaceVariant.copy(alpha=.8f)else if(rowNumber%2==0)colors.surfaceVariant.copy(alpha=.28f)else colors.surface)) {
                        var cell=row.firstChild;var cellNumber=0
                        while(cell!=null){
                            if(cellNumber++>0)VerticalDivider(color=colors.outline.copy(alpha=.45f))
                            Box(Modifier.width(cellWidth).fillMaxHeight().padding(horizontal=14.dp,vertical=12.dp)){Inline(cell,onLink,if(LocalDocumentReading.current)15 else 14,if(heading)FontWeight.SemiBold else FontWeight.Normal)}
                            cell=cell.next
                        }
                    }
                    if(row.next!=null||group.next!=null)HorizontalDivider(color=colors.outline.copy(alpha=.55f))
                    rowNumber++;row=row.next
                };group=group.next}
            }
        }
        else -> { var child=node.firstChild;while(child!=null){MarkdownNode(child,onLink);child=child.next} }
    }
}
internal fun markdownSoftBreak(node:SoftLineBreak):String {
    val before=(node.previous as? org.commonmark.node.Text)?.literal?.lastOrNull()
    val after=(node.next as? org.commonmark.node.Text)?.literal?.firstOrNull()
    fun cjk(char:Char?)=char!=null&&(char in '\u2e80'..'\u9fff'||char in '\uf900'..'\ufaff')
    return if(cjk(before)&&cjk(after))""else" "
}
@Composable private fun Inline(node:Node,onLink:(String)->Unit,size:Int=14,weight:FontWeight=FontWeight.Normal,stripTaskMarker:Boolean=false) {
    val effectiveSize=if(LocalDocumentReading.current&&size==14)16 else size
    val primary=MaterialTheme.colorScheme.primary
    val documentReading=LocalDocumentReading.current
    val imageInDocument=LocalMarkdownImage.current!=null
    val content=buildAnnotatedString {
        fun appendNode(n:Node) { when(n) {
            is org.commonmark.node.Text -> append(if(stripTaskMarker&&n===node.firstChild)n.literal.replaceFirst(Regex("^\\[([ xX])]\\s+"),"")else n.literal)
            is SoftLineBreak -> append(markdownSoftBreak(n))
            is HardLineBreak -> append("\n")
            is Code -> withStyle(SpanStyle(fontFamily=FontFamily.Monospace,fontSize=(effectiveSize*.9f).sp,color=primary,background=primary.copy(alpha=.07f))){append(n.literal)}
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight=FontWeight.SemiBold)){var c=n.firstChild;while(c!=null){appendNode(c);c=c.next}}
            is org.commonmark.ext.gfm.strikethrough.Strikethrough -> withStyle(SpanStyle(textDecoration=androidx.compose.ui.text.style.TextDecoration.LineThrough)){var c=n.firstChild;while(c!=null){appendNode(c);c=c.next}}
            is Emphasis -> withStyle(SpanStyle(fontStyle=FontStyle.Italic)){var c=n.firstChild;while(c!=null){appendNode(c);c=c.next}}
            is Link -> {pushStringAnnotation("link",n.destination);withStyle(SpanStyle(color=primary)){var c=n.firstChild;while(c!=null){appendNode(c);c=c.next}};pop()}
            is org.commonmark.node.Image -> {if(!imageInDocument){pushStringAnnotation("link",n.destination);append("查看图片");pop()}}
            else -> {var c=n.firstChild;while(c!=null){appendNode(c);c=c.next}}
        } }
        var child=node.firstChild;while(child!=null){appendNode(child);child=child.next}
    }
    ClickableText(content,style=LocalTextStyle.current.copy(color=MaterialTheme.colorScheme.onSurface,fontSize=effectiveSize.sp,lineHeight=(effectiveSize*if(documentReading&&node is org.commonmark.node.Heading)1.35 else if(documentReading)1.85 else 1.55).sp,fontWeight=weight),onClick={offset->content.getStringAnnotations("link",offset,offset).firstOrNull()?.let{onLink(it.item)}})
}
@Composable private fun CodeBlock(text:String,language:String="") {
    val colors=MaterialTheme.colorScheme
    var copied by remember(text){mutableStateOf(false)}
    LaunchedEffect(copied){if(copied){kotlinx.coroutines.delay(1800);copied=false}}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp,colors.outline.copy(alpha=.65f),RoundedCornerShape(8.dp)).background(colors.surfaceVariant.copy(alpha=.5f))) {
        Row(Modifier.fillMaxWidth().padding(start=14.dp,end=6.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            SubtleText(language.ifBlank {"代码"});Spacer(Modifier.weight(1f))
            DeskTextButton(onClick={DesktopFiles.copy(text);copied=true}){Glyph(if(copied)"check"else"copy",Modifier.size(14.dp));Spacer(Modifier.width(5.dp));Text(tr(if(copied)"已复制"else"复制代码"),fontSize=11.sp)}
        }
        HorizontalDivider(color=colors.outline.copy(alpha=.45f))
        SelectionContainer {Text(text.trimEnd(),Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),fontFamily=FontFamily.Monospace,fontSize=13.sp,lineHeight=22.sp)}
    }
}
@Composable internal fun ChatImageView(c:DesktopController,path:String,profile:String) {
    var bytes by remember(profile,path){mutableStateOf<ByteArray?>(null)}
    var failure by remember(profile,path){mutableStateOf<String?>(null)}
    var attempt by remember(profile,path){mutableStateOf(0)}
    var loading by remember(profile,path){mutableStateOf(true)}
    LaunchedEffect(profile,path,attempt){loading=true;failure=null;c.request(profile,{it.readImage(path)},finished={loading=false},failed={failure=it}){bytes=it.bytes}}
    val bitmap=remember(bytes){bytes?.let {runCatching {org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()}.getOrNull()}}
    val colors=MaterialTheme.colorScheme
    Column(Modifier.widthIn(max=420.dp).background(colors.surfaceVariant,RoundedCornerShape(10.dp)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        if(bitmap!=null)Image(bitmap,tr("对话图片"),Modifier.heightIn(max=280.dp).widthIn(max=400.dp).clip(RoundedCornerShape(6.dp)).desktopClick {c.openDocument(path,p=profile)},contentScale=ContentScale.Fit)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            if(loading)CircularProgressIndicator(Modifier.size(14.dp),strokeWidth=1.5.dp)else Glyph("image",Modifier.size(16.dp),colors.primary)
            Text(if(loading)tr("正在读取图片…")else if(path.startsWith("data:"))tr("图片")else path.substringBefore('?').substringAfterLast('/').substringAfterLast('\\'),Modifier.weight(1f),fontSize=12.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(!loading&&bitmap==null)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr("图片暂时无法显示"),Modifier.weight(1f),fontSize=12.sp,color=colors.onSurfaceVariant)
            DeskTextButton(onClick={attempt++}){Text(tr("重试"),fontSize=12.sp)}
            if(failure!=null)DeskTextButton(onClick={c.showDetails("图片读取详情",failure.orEmpty())}){Text(tr("详情"),fontSize=12.sp)}
        }
    }
}
internal fun requiresSourceEditor(text:String):Boolean = listOf(
    Regex("^\\s{2,}([-*+] |[0-9]+[.)] )",RegexOption.MULTILINE),
    Regex("^\\s*\\[[^]]+]:",RegexOption.MULTILINE),
    Regex("<[/!]?[A-Za-z][^>]*>"),Regex("(?m)^~~~|^\\$\\$"),
).any {it.containsMatchIn(text)}

internal fun Color.cssHex()="#%06X".format(toArgb() and 0xFFFFFF)
