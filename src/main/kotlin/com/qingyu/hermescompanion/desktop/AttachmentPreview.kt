package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import java.util.Base64

/** Read the bytes already in the draft; preview must never upload or consume an attachment. */
internal fun localAttachmentDocument(file:PendingAttachment):WorkspaceDocument? {
    val encoded=file.dataUrl?:file.uploadDataUrl
    val bytes=when {
        encoded!=null->Base64.getDecoder().decode(encoded.substringAfter(','))
        file.textContent!=null->file.textContent.toByteArray(Charsets.UTF_8)
        else->return null
    }
    return WorkspaceDocument(file.name,file.remotePath?:"attachment:${file.id}",file.mimeType,file.textContent.orEmpty(),bytes)
}

@Composable internal fun ComposerAttachment(c:DesktopController,key:String,file:PendingAttachment) {
    var preview by remember(key,file.id){mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    val thumbnail by produceState<ImageBitmap?>(null,file.id) {
        if(file.mimeType.startsWith("image/")&&file.dataUrl!=null) {
            value=withContext(Dispatchers.IO){runCatching {
                val bytes=Base64.getDecoder().decode(file.dataUrl.substringAfter(','))
                val small=com.qingyu.hermescompanion.platform.ModernImageSupport.thumbnail(bytes,file.name)
                org.jetbrains.skia.Image.makeFromEncoded(small).toComposeImageBitmap()
            }.getOrNull()}
        }
    }
    Row(Modifier.background(colors.primary.copy(alpha=.065f),RoundedCornerShape(8.dp)),verticalAlignment=Alignment.CenterVertically) {
        Hint("预览附件") {
            Row(Modifier.desktopClick {preview=true}.padding(start=10.dp,end=6.dp,top=9.dp,bottom=9.dp)
                .semantics {testTag="preview-attachment:${file.id}"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                if(thumbnail!=null)Image(thumbnail!!,contentDescription=null,modifier=Modifier.size(28.dp))
                else Glyph(if(file.mimeType.startsWith("image/"))"image"else"document",Modifier.size(14.dp),colors.primary)
                Text(file.name,Modifier.widthIn(max=180.dp),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        Hint("移除附件") {DeskIconButton(onClick={c.removeAttachment(key,file.id)},modifier=Modifier.semantics {testTag="remove-attachment:${file.id}"}){Glyph("close",Modifier.size(14.dp))}}
    }
    if(preview)AttachmentPreviewDialog(c,key,file){preview=false}
}

@Composable private fun AttachmentPreviewDialog(c:DesktopController,key:String,file:PendingAttachment,onClose:()->Unit) {
    val profile=c.sessions.firstOrNull {it.scopedId==key}?.profile
        ?:c.companion.session?.takeIf {it.scopedId==key}?.profile?:c.profile
    val loaded by produceState<Result<WorkspaceDocument>?>(null,file.id,profile) {
        value=try {Result.success(withContext(Dispatchers.IO) {
            localAttachmentDocument(file)?:c.client(profile).readWorkspaceDocumentForProfile(requireNotNull(file.remotePath),profile)
        })}catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
    }
    val doc=loaded?.getOrNull()
    HermesDialog(onDismissRequest=onClose,title={Text(file.name,maxLines=2,overflow=TextOverflow.Ellipsis)},text={
        Box(Modifier.width(540.dp).heightIn(min=180.dp,max=430.dp),contentAlignment=Alignment.Center) {
            when {
                loaded==null->CircularProgressIndicator()
                doc==null->Text("暂时无法预览：${loaded?.exceptionOrNull()?.message.orEmpty()}",color=MaterialTheme.colorScheme.error)
                doc.mimeType.startsWith("image/")->ImageBytes(doc.bytes,doc.name)
                doc.name.substringAfterLast('.').lowercase() in setOf("pdf","ppt","pptx")->PagedDocumentPreview(doc.bytes,doc.name.endsWith(".pdf",true))
                doc.name.substringAfterLast('.').lowercase() in setOf("xls","xlsx")->SpreadsheetPreview(doc.bytes)
                file.textContent!=null||doc.mimeType.startsWith("text/")->SelectionContainer {
                    Text(file.textContent?:doc.bytes.toString(Charsets.UTF_8),Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),fontSize=13.sp)
                }
                else->Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    FileEmblem(file.name,Modifier.size(48.dp))
                    Text("${file.name} · ${fileSizeLabel(doc.bytes.size.toLong())}")
                    Text("此格式可保存到本机，用对应软件查看。",color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    },confirmButton={DeskTextButton(onClick=onClose){Text("关闭")}},dismissButton={
        if(doc!=null)Row {
            if(com.qingyu.hermescompanion.platform.ModernImageSupport.isModern(doc.name)) {
                var converting by remember(file.id){mutableStateOf(false)}
                val scope=rememberCoroutineScope()
                DeskTextButton(enabled=!converting,onClick={
                    converting=true
                    val token=c.epoch
                    scope.launch {
                        try {
                            val png=withContext(Dispatchers.IO){compatiblePng(doc.bytes,doc.name)}
                            if(token!=c.epoch)return@launch
                            require(png.size<=8*1024*1024){"PNG 副本超过 8 MB，未自动压缩。请保存原图后自行处理。"}
                            require(c.attachments[key].orEmpty().size<10){"附件数量已达上限。"}
                            c.attachments[key]=c.attachments[key].orEmpty()+PendingAttachment(name=doc.name.substringBeforeLast('.')+"-副本.png",mimeType="image/png",dataUrl="data:image/png;base64,"+Base64.getEncoder().encodeToString(png))
                            c.notice="已添加 PNG 首帧副本，原图保留；预览及转换后的色彩请与原图核对。"
                        }catch(e:CancellationException){throw e}catch(e:Exception){c.error=e.message}finally{converting=false}
                    }
                }){Text(if(converting)"转换中…"else"添加 PNG 副本")}
            }
            DeskTextButton(onClick={runCatching {DesktopFiles.save(doc)}.onFailure {c.error=it.message}}){Text("保存原文件")}
        }
    })
}
