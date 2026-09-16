package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.data.ChatInsightParser
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Composable fun VoiceDialog(c:DesktopController) {
    HermesDialog(onDismissRequest={c.voice.dismiss()},title={Text(tr("语音对话"))},text={VoicePanel(c)},confirmButton={SmallButton("结束对话",{c.voice.dismiss()})})
}
@Composable internal fun VoicePanel(c:DesktopController) {
    Column(Modifier.widthIn(max=500.dp).fillMaxWidth().heightIn(max=460.dp).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(180.dp),contentAlignment=Alignment.Center) {
            val color=MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color.copy(alpha=.06f),radius=size.minDimension*.48f)
                drawCircle(color.copy(alpha=.12f),radius=size.minDimension*(.31f+c.voice.level*.13f))
                drawCircle(color.copy(alpha=.18f),radius=size.minDimension*.25f)
            }
            Avatar(c,false,Modifier.size(88.dp).clip(CircleShape))
        }
        Heading(tr(when(c.voice.phase){VoicePhase.LISTENING->"我在听";VoicePhase.TRANSCRIBING->"正在识别";VoicePhase.THINKING->"正在回应";VoicePhase.SPEAKING->"正在朗读";VoicePhase.ERROR->"需要处理";else->"随时可以开始"}))
        Text(tr(c.voice.message),fontSize=14.sp)
        if(c.voice.transcript.isNotBlank())Text(c.voice.transcript.take(160),maxLines=4,color=MaterialTheme.colorScheme.onSurfaceVariant)
        when(c.voice.phase) {
            VoicePhase.LISTENING->if(c.voice.holdToTalk)SmallButton("取消本次发送",{c.voice.cancelHoldToTalk()})else SmallButton("说完了",{c.voice.stopCapture()},true)
            VoicePhase.TRANSCRIBING->SmallButton("取消识别",{c.voice.pause()})
            VoicePhase.SPEAKING,VoicePhase.THINKING->SmallButton("打断",{c.voice.interrupt()},true)
            else->if(c.voice.holdToTalk)Caption("按住悬浮球继续说话 · 松开发送")else SmallButton("继续说",{c.voice.startCapture(true)},true)
        }
        Caption("关闭窗口后，文字对话仍会保留。")
    }
}

@Composable fun Avatar(c:DesktopController,user:Boolean,modifier:Modifier=Modifier.size(34.dp)) {
    val ref=if(user)c.userAvatar else c.hermesAvatar
    val bitmap by produceState<ImageBitmap?>(null,ref) {
        value=if(ref.isBlank())null else withContext(Dispatchers.IO) { runCatching { org.jetbrains.skia.Image.makeFromEncoded(c.store.readBlob(ref)).toComposeImageBitmap() }.getOrNull() }
    }
    if(bitmap!=null)Image(bitmap!!,if(user)c.nickname else c.hermesName,modifier.clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)
    else if(user)Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=.15f)),contentAlignment=Alignment.Center){Text(c.nickname.take(1),fontWeight=FontWeight.SemiBold)}
    else Image(painterResource("avatar.png"),c.hermesName,modifier.clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)
}

internal fun cropAvatar(source:BufferedImage,zoom:Float,x:Float,y:Float):ByteArray {
    val side=(minOf(source.width,source.height)/zoom.coerceIn(1f,4f)).toInt().coerceAtLeast(1)
    val left=((source.width-side)*(x.coerceIn(-1f,1f)+1)/2).toInt()
    val top=((source.height-side)*(y.coerceIn(-1f,1f)+1)/2).toInt()
    val result=BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB)
    val g=result.createGraphics()
    try { g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,0,0,512,512,left,top,left+side,top+side,null) } finally {g.dispose()}
    return ByteArrayOutputStream().use { ImageIO.write(result,"png",it);it.toByteArray() }
}
internal fun readAvatarImage(file:java.io.File):BufferedImage {
    require(file.isFile && file.length() in 1..30L*1024*1024){"请选择不超过 30 MB 的图片。"}
    ImageIO.createImageInputStream(file).use {stream->
        val reader=ImageIO.getImageReaders(stream).asSequence().firstOrNull()
        if(reader!=null)try {
            reader.input=stream
            val w=reader.getWidth(0);val h=reader.getHeight(0)
            require(w.toLong()*h<=40_000_000){"图片尺寸过大，请选择 4000 万像素以内的图片。"}
            val options=reader.defaultReadParam
            val sample=(maxOf(w,h)/4096).coerceAtLeast(1)
            options.setSourceSubsampling(sample,sample,0,0)
            return reader.read(0,options)
        }finally {reader.dispose()}
    }
    // Skia adds WebP support to ImageIO's native PNG/JPEG/GIF readers.
    org.jetbrains.skia.Data.makeFromBytes(file.readBytes()).use {data->
        org.jetbrains.skia.Codec.makeFromData(data).use {codec->
            require(codec.imageInfo.width.toLong()*codec.imageInfo.height<=40_000_000){"图片尺寸过大。"}
        }
        org.jetbrains.skia.Image.makeFromEncoded(data.bytes).use {image->
            image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.use {png->
                return ImageIO.read(png.bytes.inputStream())?:error("无法读取这张图片。")
            }
        }
    }
    error("无法读取这张图片，请选择 PNG、JPEG 或 WebP 图片。")
}

@Composable fun AvatarEditor(c:DesktopController,user:Boolean,onDismiss:()->Unit) {
    var source by remember {mutableStateOf<BufferedImage?>(null)}
    var zoom by remember {mutableStateOf(1f)};var x by remember {mutableStateOf(0f)};var y by remember {mutableStateOf(0f)}
    var failure by remember {mutableStateOf("")};var busy by remember {mutableStateOf(false)};var name by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    val bytes by produceState<ByteArray?>(null,source,zoom,x,y) {
        val original=source?:return@produceState
        delay(35);value=withContext(Dispatchers.Default){cropAvatar(original,zoom,x,y)}
    }
    val preview=remember(bytes){bytes?.let {org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()}}
    fun select() {
        if(busy)return
        scope.launch {
            failure=""
            try {
                val file=DesktopFiles.chooseImage()?:return@launch
                busy=true
                source=withContext(Dispatchers.IO){readAvatarImage(file)}
                zoom=1f;x=0f;y=0f;name=file.name
            }catch(e:Exception){if(e is CancellationException)throw e;failure=e.message?:"图片读取失败，请重试。"}
            finally{busy=false}
        }
    }
    HermesDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(tr(if(user)"设置我的头像" else "设置 Hermes 头像"))},text={
        Column(Modifier.width(390.dp).heightIn(max=480.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Hint("点击选择头像图片") {
                Box(Modifier.size(180.dp).clip(RoundedCornerShape(32.dp)).desktopClick(enabled=!busy,onClick=::select),contentAlignment=Alignment.Center) {
                    if(preview!=null)Image(preview!!,tr("裁剪预览"),Modifier.fillMaxSize())else Avatar(c,user,Modifier.fillMaxSize())
                    if(busy)Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha=.65f)),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(28.dp))}
                }
            }
            SmallButton(if(source==null)"选择图片" else "换一张图片",::select,enabled=!busy)
            if(name.isNotBlank())Caption(name)
            Caption("缩放");Slider(zoom,{zoom=it},enabled=source!=null&&!busy,valueRange=1f..4f)
            Caption("水平位置");Slider(x,{x=it},enabled=source!=null&&!busy,valueRange=-1f..1f)
            Caption("垂直位置");Slider(y,{y=it},enabled=source!=null&&!busy,valueRange=-1f..1f)
            if(failure.isNotBlank())Text(failure,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={DeskTextButton(onClick={
        val original=source?:return@DeskTextButton
        scope.launch {
            busy=true;failure=""
            try {
                // Save the exact latest crop, even when the preview is still being debounced.
                val ref=withContext(Dispatchers.IO){c.store.saveBlob(cropAvatar(original,zoom,x,y))}
                if(user){c.userAvatar=ref;c.savePreference("userAvatar",ref)}else{c.hermesAvatar=ref;c.savePreference("hermesAvatar",ref)}
                c.notice="头像已更新";onDismiss()
            }catch(e:Exception){if(e is CancellationException)throw e;failure=e.message?:"保存失败，请重试。"}
            finally{busy=false}
        }
    },enabled=source!=null&&!busy){Text(tr(if(busy)"正在处理…" else "保存头像"))}},dismissButton={DeskTextButton(onClick=onDismiss,enabled=!busy){Text(tr("取消"))}})
}

@Composable fun CommandsDialog(c:DesktopController) {
    var query by remember { mutableStateOf("") };var category by remember { mutableStateOf("") }
    HermesDialog(onDismissRequest={c.commandsOpen=false},title={Text(tr("快捷命令"))},text={Column(Modifier.width(580.dp).heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(query,{query=it},placeholder={Text(tr("搜索命令或说明"))},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){(listOf("")+c.commands.map { it.category }.distinct()).distinct().forEach { item->DeskChip(category==item,{category=item},label={Text(if(item.isBlank())tr("全部")else item)}) }}
        val commands=c.commands.filter { (category.isBlank()||category==it.category) && (it.command.contains(query,true)||it.description.contains(query,true)) }
        LazyColumn(Modifier.weight(1f,false)) {
            if(commands.isEmpty())item{Caption(tr("没有匹配的命令。"))}
            items(commands,key={it.command+it.category}) { command->Column(Modifier.fillMaxWidth().desktopClick { c.currentSession?.let { c.setDraft(it.scopedId,command.command+" ") };c.commandsOpen=false }.padding(vertical=12.dp)) {Text(command.command,fontWeight=FontWeight.SemiBold);Caption(command.description);if(command.argsHint.isNotBlank())Caption(command.argsHint)} }
        }
    }},confirmButton={DeskTextButton(onClick={c.commandsOpen=false}){Text(tr("关闭"))}})
}

@Composable fun SnippetsPanel(c:DesktopController,modifier:Modifier=Modifier) {
    var editing by remember { mutableStateOf<PromptSnippet?>(null) };var deleting by remember {mutableStateOf<PromptSnippet?>(null)};var reset by remember {mutableStateOf(false)}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){SmallButton("新建提示词",{editing=PromptSnippet(java.util.UUID.randomUUID().toString(),"","")},true);SmallButton("恢复默认",{reset=true})}
        c.snippets.forEachIndexed { index,snippet ->
            Column(Modifier.fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(12.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(snippet.title,fontWeight=FontWeight.SemiBold);Text(snippet.text)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    DeskTextButton(onClick={c.insertSnippet(snippet.text);c.snippetsOpen=false;c.page=Page.CHAT},enabled=c.currentSession!=null){Text(tr("填入对话"))}
                    DeskTextButton(onClick={editing=snippet}){Text(tr("编辑"))}
                    DeskTextButton(onClick={val list=c.snippets.toMutableList();java.util.Collections.swap(list,index,index-1);c.saveSnippets(list)},enabled=index>0){Glyph("arrow-up",Modifier.size(16.dp))}
                    DeskTextButton(onClick={val list=c.snippets.toMutableList();java.util.Collections.swap(list,index,index+1);c.saveSnippets(list)},enabled=index<c.snippets.lastIndex){Glyph("arrow-down",Modifier.size(16.dp))}
                    DeskTextButton(onClick={deleting=snippet}){Text(tr("删除"))}
                }
            }
        }
    }
    editing?.let { old->var title by remember(old.id){mutableStateOf(old.title)};var body by remember(old.id){mutableStateOf(old.text)}
        HermesDialog(onDismissRequest={editing=null},title={Text(tr("提示词片段"))},text={Column(Modifier.width(500.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(title,{title=it},label={Text(tr("名称"))},modifier=Modifier.fillMaxWidth());OutlinedTextField(body,{body=it},label={Text(tr("内容"))},minLines=5,modifier=Modifier.fillMaxWidth())}},confirmButton={DeskTextButton(onClick={val value=old.copy(title=title.trim(),text=body.trim());c.saveSnippets(if(c.snippets.any { it.id==old.id })c.snippets.map { if(it.id==old.id)value else it }else c.snippets+value);editing=null},enabled=title.isNotBlank()&&body.isNotBlank()){Text(tr("保存"))}},dismissButton={DeskTextButton(onClick={editing=null}){Text(tr("取消"))}})
    }
    deleting?.let { value->ConfirmDialog(tr("删除提示词？"),value.title,{deleting=null}){c.saveSnippets(c.snippets.filterNot { it.id==value.id });deleting=null} }
    if(reset)ConfirmDialog(tr("恢复默认提示词？"),tr("当前自定义片段会被默认内容替换。"),{reset=false}){c.saveSnippets(DefaultPromptSnippets);reset=false}
}

@Composable fun AssistantPanel(c:DesktopController,modifier:Modifier=Modifier) {
    val s=c.currentSession ?: return
    val all=c.messages[s.scopedId].orEmpty();val insights=remember(all){ChatInsightParser.fromMessages(all)}
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        
        SmallButton("连续语音",{c.voice.open()},true)
        Text(tr("本次专家会审"),fontWeight=FontWeight.SemiBold)
        Picker(when(c.councilMode){"quick"->"快速会审";"deep"->"深度会审";else->"关闭"},listOf("off","quick","deep"),{when(it){"quick"->tr("快速会审");"deep"->tr("深度会审");else->tr("关闭")}}){c.councilMode=it}
        Caption(tr("深度会审使用真实子 Agent；快速会审使用服务器提供的 MoA 预设。"))
        HorizontalDivider()
        Text(tr("本次工具"),fontWeight=FontWeight.SemiBold)
        val run=c.runs[s.scopedId]
        run?.tools.orEmpty().forEach { tool->Text((when(tool.status){ToolStatus.RUNNING->"◌ ";ToolStatus.COMPLETED->"✓ ";ToolStatus.FAILED->"! "})+tool.name);Caption(tool.preview.take(200)) }
        val council=remember(all,run?.tools) {(all.map {it.content}+run?.tools.orEmpty().map {it.preview}).flatMap(::parseCouncilAgentMessages).distinctBy {it.name+it.content}}
        if(council.isNotEmpty()) {
            Text(tr("专家返回"),fontWeight=FontWeight.SemiBold)
            council.forEach {expert->var expanded by remember(expert.name){mutableStateOf(false)};DeskTextButton(onClick={expanded=!expanded}){Text(expert.name)};if(expanded){Caption(expert.task);Markdown(expert.content)}}
        }
        Text(tr("待办"),fontWeight=FontWeight.SemiBold)
        if(insights.todos.isEmpty())Caption(tr("当前对话暂无待办。"))
        insights.todos.forEach { todo->Text((if(todo.status==TodoStatus.COMPLETED)"✓ "else"○ ")+todo.content) }
        Text(tr("对话产物"),fontWeight=FontWeight.SemiBold)
        insights.artifacts.forEach { a->DeskTextButton(onClick={c.openDocument(a.path,s,s.profile);c.assistantPanel=false}){Text(a.name)} }
        Text(tr("排队消息"),fontWeight=FontWeight.SemiBold)
        c.queued[s.scopedId].orEmpty().forEach { q->Column {Text(q.prompt.ifBlank { q.attachments.joinToString { it.name } },maxLines=3);Row{DeskTextButton(onClick={c.removeQueued(s.scopedId,q.id,true)}){Text(tr("取回编辑"))};DeskTextButton(onClick={c.removeQueued(s.scopedId,q.id)}){Text(tr("移除"))}}} }
        if(run==null && c.queued[s.scopedId].orEmpty().isNotEmpty())SmallButton("继续发送队列",{c.resumeQueue(s)},true)
    }
}

@Composable fun RunBanner(c:DesktopController) {
    if(c.runs.isEmpty())return
    val focused=c.runs[c.currentSession?.scopedId] ?: c.runs.values.first()
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha=.06f)).desktopClick {c.page=Page.TASKS}.padding(horizontal=16.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("${c.runs.size} · ${focused.session.title}",Modifier.weight(1f),maxLines=1,fontSize=13.sp)
        Text(tr(focused.status),fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
        DeskTextButton(onClick={c.page=Page.TASKS}){Text(tr("查看任务"))}
    }
}
