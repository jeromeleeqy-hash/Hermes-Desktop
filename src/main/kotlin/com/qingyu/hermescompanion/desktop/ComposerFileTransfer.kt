@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.input.key.*
import com.qingyu.hermescompanion.platform.DesktopHost
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI

/** OS file references only: ordinary pasted text stays a normal text edit. */
internal fun hasFileTransfer(data:Transferable):Boolean = data.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
    data.transferDataFlavors.any {it.isMimeTypeEqual("text/uri-list")}

internal fun transferredFiles(data:Transferable):List<File> {
    if(data.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        return (data.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)?.filterIsInstance<File>().orEmpty().distinctBy {it.absolutePath}
    val flavor=data.transferDataFlavors.firstOrNull {it.isMimeTypeEqual("text/uri-list")}?:return emptyList()
    return flavor.getReaderForText(data).use {it.readText()}.lineSequence().map(String::trim)
        .filter {it.isNotBlank()&&!it.startsWith('#')}.mapNotNull {line->
            runCatching {URI(line).takeIf {it.scheme.equals("file",true)&&it.authority.isNullOrBlank()}?.let(::File)}.getOrNull()
        }.distinctBy {it.absolutePath}.toList()
}

internal fun isFilePaste(event:KeyEvent):Boolean=event.type==KeyEventType.KeyDown&&!event.isAltPressed&&(
    event.key==Key.V&&DesktopHost.primaryModifier(event.isMetaPressed,event.isCtrlPressed)&&!event.isShiftPressed ||
    event.key==Key.Insert&&event.isShiftPressed&&!event.isMetaPressed&&!event.isCtrlPressed)

internal fun DesktopController.pasteFiles(key:String):Boolean {
    return try {
        val data=Toolkit.getDefaultToolkit().systemClipboard.getContents(null)?:return false
        if(!hasFileTransfer(data))return false
        val files=transferredFiles(data)
        if(files.isEmpty())false else {addFilesToDraft(key,files);true}
    }catch(_:Exception){attachmentErrors[key]="暂时无法读取剪贴板中的文件，请重试或点击添加文件。";false}
}

internal fun Modifier.composerFileDrop(c:DesktopController,key:String,onHover:(Boolean)->Unit):Modifier=composed {
    val hover by rememberUpdatedState(onHover)
    val target=remember(c,key){object:DragAndDropTarget {
        override fun onEntered(event:DragAndDropEvent){hover(true)}
        override fun onExited(event:DragAndDropEvent){hover(false)}
        override fun onEnded(event:DragAndDropEvent){hover(false)}
        override fun onDrop(event:DragAndDropEvent):Boolean {
            hover(false)
            return try {val files=transferredFiles(event.awtTransferable);if(files.isEmpty())false else {c.addFilesToDraft(key,files);true}}
            catch(_:Exception){c.attachmentErrors[key]="文件没有添加成功，请重新拖入或使用添加文件按钮。";false}
        }
    }}
    dragAndDropTarget(shouldStartDragAndDrop={runCatching {hasFileTransfer(it.awtTransferable)}.getOrDefault(false)},target=target)
}
