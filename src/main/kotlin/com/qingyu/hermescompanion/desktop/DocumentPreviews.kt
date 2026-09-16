@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlin.math.*

@Composable private fun PreviewLoading(text:String="正在准备预览…") {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(26.dp),strokeWidth=2.dp);Caption(text)}}
}
@Composable private fun PreviewFailure(failure:Throwable,onRetry:()->Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Glyph("document",Modifier.size(32.dp),MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp));Heading("暂时无法预览")
        Caption(failure.message?.take(220)?:"请重新打开文件，或下载到本机查看。",Modifier.padding(vertical=12.dp))
        SmallButton("重新加载",onRetry)
    }
}

@Composable private fun <T:AutoCloseable> previewResource(key:Any?,loader:()->T):State<Result<T>?> = produceState<Result<T>?>(null,key) {
    var resource:T?=null
    try {
        value=null
        withContext(Dispatchers.IO){resource=loader()}
        value=Result.success(resource!!)
        awaitCancellation()
    }catch(e:CancellationException){throw e}catch(e:Exception){value=Result.failure(e)}catch(e:LinkageError){value=Result.failure(IllegalStateException("预览组件不完整，请使用完整安装包重新安装。",e))}
    finally {withContext(NonCancellable+Dispatchers.IO){runCatching {resource?.close()}}}
}

internal fun fitImageScale(imageWidth:Int,imageHeight:Int,viewport:IntSize,quarterTurns:Int):Float {
    val w=if(quarterTurns%2==0)imageWidth else imageHeight;val h=if(quarterTurns%2==0)imageHeight else imageWidth
    return min((viewport.width-40).coerceAtLeast(1).toFloat()/w.coerceAtLeast(1),(viewport.height-40).coerceAtLeast(1).toFloat()/h.coerceAtLeast(1)).coerceAtMost(1f)
}
internal fun clampImagePan(pan:Offset,width:Int,height:Int,viewport:IntSize,scale:Float,rotation:Int):Offset {
    val w=if(rotation%2==0)width else height;val h=if(rotation%2==0)height else width
    val dx=(w*scale-viewport.width+40).coerceAtLeast(0f)/2
    val dy=(h*scale-viewport.height+40).coerceAtLeast(0f)/2
    return Offset(pan.x.coerceIn(-dx,dx),pan.y.coerceIn(-dy,dy))
}
@Composable internal fun ImageCanvas(bitmap:ImageBitmap,modifier:Modifier=Modifier,rotationAllowed:Boolean=true,preferWidth:Boolean=false) {
    var viewport by remember {mutableStateOf(IntSize(1,1))}
    var manualScale by remember(bitmap){mutableStateOf<Float?>(null)}
    var pan by remember(bitmap){mutableStateOf(Offset.Zero)}
    var rotation by remember(bitmap){mutableStateOf(0)}
    var fitWidth by remember(bitmap){mutableStateOf(preferWidth)}
    val fit=if(fitWidth)((viewport.width-64).coerceAtLeast(1).toFloat()/bitmap.width).coerceAtMost(1f)else fitImageScale(bitmap.width,bitmap.height,viewport,rotation)
    val scale=manualScale?:fit
    LaunchedEffect(bitmap,viewport,fitWidth){if(manualScale==null)pan=if(fitWidth)Offset(0f,((bitmap.height*fit-viewport.height+40)/2).coerceAtLeast(0f))else Offset.Zero}
    val currentScale by rememberUpdatedState(scale)
    val boundPan by rememberUpdatedState<(Offset)->Offset>({clampImagePan(it,bitmap.width,bitmap.height,viewport,scale,rotation)})
    val colors=MaterialTheme.colorScheme
    fun changeZoom(next:Float,anchor:Offset=Offset(viewport.width/2f,viewport.height/2f)) {
        val target=next.coerceIn(.05f,6f);val center=Offset(viewport.width/2f,viewport.height/2f)
        pan=(pan+center-anchor)*(target/scale)+anchor-center;manualScale=target
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Hint("缩小"){DeskIconButton(onClick={changeZoom(scale/1.25f)}){Glyph("minus",Modifier.size(17.dp))}}
            Text("${(scale*100).roundToInt()}%",Modifier.width(52.dp),fontSize=12.sp)
            Hint("放大"){DeskIconButton(onClick={changeZoom(scale*1.25f)}){Glyph("add",Modifier.size(17.dp))}}
            DeskTextButton(onClick={fitWidth=false;manualScale=null;pan=Offset.Zero}){Text(tr(if(rotationAllowed)"适应窗口"else"整页"),fontSize=12.sp)}
            if(!rotationAllowed)DeskTextButton(onClick={fitWidth=true;manualScale=null;pan=Offset(0f,((bitmap.height*((viewport.width-64).coerceAtLeast(1).toFloat()/bitmap.width).coerceAtMost(1f)-viewport.height+40)/2).coerceAtLeast(0f))}){Text(tr("适应宽度"),fontSize=12.sp)}
            DeskTextButton(onClick={manualScale=1f;pan=Offset.Zero}){Text("1:1",fontSize=12.sp)}
            if(rotationAllowed)DeskTextButton(onClick={rotation=(rotation+1)%4;manualScale=null;pan=Offset.Zero}){Text(tr("旋转"),fontSize=12.sp)}
            Spacer(Modifier.width(10.dp));Caption(if(rotationAllowed)"${bitmap.width} × ${bitmap.height}"else"${shortcutKey()} + 滚轮缩放")
        }
        Canvas(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(colors.surfaceVariant.copy(alpha=.6f))
            .semantics {contentDescription=tr("图片预览；拖动平移，${shortcutKey()} 加滚轮缩放，双击切换原始大小")}
            .onSizeChanged {viewport=it}
            .onPointerEvent(PointerEventType.Scroll){event->
                val change=event.changes.firstOrNull()?:return@onPointerEvent
                if(com.qingyu.hermescompanion.platform.DesktopHost.primaryModifier(event.keyboardModifiers.isMetaPressed,event.keyboardModifiers.isCtrlPressed))changeZoom(currentScale*exp(-change.scrollDelta.y*.15f),change.position)
                else if(manualScale!=null||fitWidth)pan=boundPan(pan+Offset(-change.scrollDelta.x*36,-change.scrollDelta.y*36))
                change.consume()
            }
            .pointerInput(bitmap){detectDragGestures {change,drag->change.consume();pan=boundPan(pan+drag)}}
            .pointerInput(bitmap){detectTapGestures(onDoubleTap={if(manualScale==null){manualScale=1f;pan=Offset.Zero}else{manualScale=null;pan=Offset.Zero}})}) {
            val rotatedWidth=if(rotation%2==0)bitmap.width else bitmap.height
            val rotatedHeight=if(rotation%2==0)bitmap.height else bitmap.width
            val dx=(rotatedWidth*scale-size.width+40).coerceAtLeast(0f)/2
            val dy=(rotatedHeight*scale-size.height+40).coerceAtLeast(0f)/2
            val offset=Offset(pan.x.coerceIn(-dx,dx),pan.y.coerceIn(-dy,dy))
            withTransform({translate(size.width/2+offset.x,size.height/2+offset.y);rotate(rotation*90f,Offset.Zero);scale(scale,scale,Offset.Zero)}) {
                drawImage(bitmap,dstOffset=IntOffset(-bitmap.width/2,-bitmap.height/2),dstSize=IntSize(bitmap.width,bitmap.height),filterQuality=FilterQuality.High)
            }
        }
    }
}

@Composable fun ImageBytes(bytes:ByteArray,name:String="image") {
    var attempt by remember(bytes){mutableStateOf(0)}
    val decoded by produceState<Result<ImageBitmap>?>(null,bytes,attempt) {
        value=null;value=withContext(Dispatchers.IO){runCatching {decodeCompatibleImage(bytes,name)}}
    }
    decoded?.fold(onSuccess={ImageCanvas(it)},onFailure={PreviewFailure(it){attempt++}})?:PreviewLoading()
}

@Composable internal fun PagedDocumentPreview(bytes:ByteArray,pdf:Boolean) {
    var attempt by remember(bytes){mutableStateOf(0)};var password by remember(bytes){mutableStateOf("")};var entered by remember(bytes){mutableStateOf("")}
    val result by previewResource(listOf(bytes,attempt,entered)){loadPagedPreview(bytes,pdf,entered)}
    val failure=result?.exceptionOrNull()
    if(failure!=null) {
        if(failure is org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException||failure is org.apache.poi.EncryptedDocumentException)Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Heading("输入文档密码")
            OutlinedTextField(password,{password=it},Modifier.width(280.dp).padding(vertical=16.dp),label={Text(tr("密码"))},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            SmallButton("打开文档",{entered=password;attempt++},true,enabled=password.isNotBlank())
        }else PreviewFailure(failure){attempt++}
        return
    }
    val document=result?.getOrNull()?:run {PreviewLoading();return}
    if(document.count==0){EmptyState("文件中没有页面");return}
    var page by remember(document){mutableStateOf(0)};var input by remember(document){mutableStateOf("1")};var thumbnails by remember {mutableStateOf(false)}
    var textMode by remember(document){mutableStateOf(false)}
    LaunchedEffect(page){input=(page+1).toString()}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            DeskTextButton(onClick={thumbnails=!thumbnails}){Glyph("sidebar",Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text(tr("缩略图"),fontSize=12.sp)}
            Hint("上一页"){DeskIconButton(onClick={page--},enabled=page>0){Glyph("chevron-left",Modifier.size(16.dp))}}
            androidx.compose.foundation.text.BasicTextField(input,{input=it.filter(Char::isDigit).take(5)},Modifier.width(44.dp).border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(5.dp)).padding(6.dp).onPreviewKeyEvent {e->if(e.type==KeyEventType.KeyDown&&e.key==Key.Enter){page=((input.toIntOrNull()?:page+1)-1).coerceIn(0,document.count-1);input=(page+1).toString();true}else false},singleLine=true,textStyle=LocalTextStyle.current.copy(color=MaterialTheme.colorScheme.onSurface,fontSize=12.sp))
            Caption("/ ${document.count}")
            DeskTextButton(onClick={page=((input.toIntOrNull()?:1)-1).coerceIn(0,document.count-1)}){Text(tr("跳转"),fontSize=12.sp)}
            Hint("下一页"){DeskIconButton(onClick={page++},enabled=page<document.count-1){Glyph("chevron-right",Modifier.size(16.dp))}}
            Caption(if(pdf)"PDF" else "幻灯片")
            DeskTextButton(onClick={textMode=!textMode}){Text(tr(if(textMode)"页面预览"else"查看文字"),fontSize=12.sp)}
        }
        HorizontalDivider()
        Row(Modifier.weight(1f)) {
            if(thumbnails)LazyColumn(Modifier.width(132.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.4f)),contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(document.count){index->
                    val thumbnail by produceState<ImageBitmap?>(null,document,index){value=withContext(Dispatchers.IO){runCatching {org.jetbrains.skia.Image.makeFromEncoded(document.page(index,210)).toComposeImageBitmap()}.getOrNull()}}
                    Column(Modifier.fillMaxWidth().desktopClick(selected=page==index){page=index}.padding(5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        thumbnail?.let {Image(it,"${index+1}",Modifier.fillMaxWidth().heightIn(max=155.dp))}?:Spacer(Modifier.height(70.dp))
                        Caption("${index+1}")
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                var pageAttempt by remember(page){mutableStateOf(0)}
                val bitmap by produceState<Result<ImageBitmap>?>(null,document,page,pageAttempt) {
                    value=null;value=withContext(Dispatchers.IO){runCatching {org.jetbrains.skia.Image.makeFromEncoded(document.page(page,1600)).toComposeImageBitmap()}}
                }
                if(textMode||bitmap?.isFailure==true) {
                    val text by produceState<String?>(null,document,page){value=withContext(Dispatchers.IO){runCatching {document.text(page)}.getOrDefault("")}}
                    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(bitmap?.isFailure==true&&!textMode) {Caption("此页有暂不支持的排版，已显示可读取的文字。");SmallButton("重试页面预览",{pageAttempt++})}
                        androidx.compose.foundation.text.selection.SelectionContainer {Text(text?:"正在读取文字…")}
                        if(text=="")Caption("此页没有可提取的文字，可以下载原文件查看。")
                    }
                }else bitmap?.getOrNull()?.let {ImageCanvas(it,rotationAllowed=false,preferWidth=pdf)}?:PreviewLoading("正在绘制第 ${page+1} 页…")
            }
        }
    }
}

@Composable internal fun SpreadsheetPreview(bytes:ByteArray) {
    var attempt by remember(bytes){mutableStateOf(0)}
    val result by previewResource(listOf(bytes,attempt)){WorkbookPreview(bytes)}
    result?.exceptionOrNull()?.let {PreviewFailure(it){attempt++};return}
    val book=result?.getOrNull()?:run {PreviewLoading("正在读取工作簿…");return}
    if(book.sheets.isEmpty()){EmptyState("工作簿中没有工作表");return}
    var sheet by remember(book){mutableStateOf(0)};var row by remember(book,sheet){mutableStateOf(0)};var column by remember(book,sheet){mutableStateOf(0)}
    val meta=book.sheets[sheet]
    val html by produceState<Result<String>?>(null,book,sheet,row,column){value=withContext(Dispatchers.Default){runCatching {book.html(sheet,row,column)}}}
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Caption("${meta.rows} 行 · ${meta.columns} 列")
            SmallButton("前 100 行",{row=(row-100).coerceAtLeast(0)},enabled=row>0)
            Caption(if(meta.rows==0)"空工作表"else"${row+1}–${min(row+100,meta.rows)} 行")
            SmallButton("后 100 行",{row+=100},enabled=row+100<meta.rows)
            if(meta.columns>30) {SmallButton("前 30 列",{column=(column-30).coerceAtLeast(0)},enabled=column>0);SmallButton("后 30 列",{column+=30},enabled=column+30<meta.columns)}
        }
        Box(Modifier.weight(1f).fillMaxWidth()){html?.fold(onSuccess={CompatibleDocument(it,Modifier.fillMaxSize())},onFailure={PreviewFailure(it){attempt++}})?:PreviewLoading()}
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            book.sheets.forEachIndexed {i,s->DeskChip(sheet==i,{sheet=i},label={Glyph("table",Modifier.size(15.dp));Spacer(Modifier.width(5.dp));Text(s.name,fontSize=12.sp)})}
        }
    }
}

@Composable internal fun HtmlFilePreview(c:DesktopController,tab:DocumentTab,word:Boolean=false) {
    var attempt by remember(tab.document){mutableStateOf(0)}
    val result by produceState<Result<String>?>(null,tab.document,attempt) {
        value=null
        val api=if(!word&&!c.demo)c.client(tab.profile)else null
        value=withContext(Dispatchers.IO){runCatching {
            if(word)wordPreview(tab.document.bytes,tab.document.name.endsWith(".doc",true))
            else webpagePreview(tab.document){path->requireNotNull(api).readWorkspaceDocumentForProfile(path,tab.profile,timeoutMillis=2000)}
        }}
    }
    var zoom by remember {mutableStateOf(1.0)}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Caption(if(word)"文档阅读"else"静态网页预览")
            Spacer(Modifier.weight(1f))
            if(!word)DeskTextButton(onClick={c.editing=true}){Glyph("code",Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text(tr("查看源码"),fontSize=12.sp)}
            Picker("${(zoom*100).toInt()}%",listOf(.8,1.0,1.2,1.5,2.0),{"${(it*100).toInt()}%"}){zoom=it}
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {result?.fold(onSuccess={if(word)CompatibleDocument(it,Modifier.fillMaxSize(),zoom)else WebDocument(it,Modifier.fillMaxSize(),zoom=zoom)},onFailure={PreviewFailure(it){attempt++}})?:PreviewLoading()}
    }
}

@Composable internal fun MarkdownAssetImage(c:DesktopController,tab:DocumentTab,target:String) {
    var attempt by remember(target){mutableStateOf(0)}
    val path=remember(tab.document.path,target){relativeDocumentPath(tab.document.path,target)}
    val result by produceState<Result<ImageBitmap>?>(null,tab.profile,target,attempt) {
        val api=if(!c.demo)c.client(tab.profile)else null
        value=withContext(Dispatchers.IO){runCatching {
            val data=if(target.startsWith("data:image/"))java.util.Base64.getDecoder().decode(target.substringAfter(','))
                else if(path!=null)requireNotNull(api).readWorkspaceDocumentForProfile(path,tab.profile).bytes
                else if(target.startsWith("https://")||target.startsWith("http://"))requireNotNull(api).readImage(target).bytes
                else error("无法读取此图片地址")
            org.jetbrains.skia.Image.makeFromEncoded(data).toComposeImageBitmap()
        }}
    }
    var expanded by remember {mutableStateOf(false)}
    val bitmap=result?.getOrNull()
    if(bitmap!=null)Image(bitmap,tr("文档图片；点击放大"),Modifier.fillMaxWidth().heightIn(max=440.dp).clip(RoundedCornerShape(8.dp)).desktopClick {expanded=true})
    else Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(8.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Caption(if(result==null)"正在加载图片…"else"图片暂时无法显示",Modifier.weight(1f))
        if(result!=null)SmallButton("重试",{attempt++})
    }
    if(expanded&&bitmap!=null)HermesDialog(onDismissRequest={expanded=false},title={Text(tr("文档图片"))},text={Box(Modifier.width(560.dp).height(430.dp)){ImageCanvas(bitmap)}},confirmButton={DeskTextButton(onClick={expanded=false}){Text(tr("关闭"))}})
}
