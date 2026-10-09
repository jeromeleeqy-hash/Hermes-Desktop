package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.hermescompanion.data.isAbsoluteRemotePath

@Composable internal fun ArtifactLookupDialog(c:DesktopController,inlinePreview:Boolean=false) {
    val prompt=c.artifactLookupPrompt?.takeIf {it.item.profile==c.profile}?:return
    var path by remember(prompt) { mutableStateOf("") }
    HermesDialog(
        inlinePreview=inlinePreview,
        onDismissRequest={c.artifactLookupPrompt=null},
        title={Text(tr(if(prompt.problem.candidates.isEmpty())"文件暂时打不开"else "确认文件位置"))},
        text={Column(Modifier.widthIn(max=580.dp).heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(tr(prompt.problem.message))
            if(prompt.problem.candidates.isNotEmpty())Box(Modifier.weight(1f,false)){
            val scroll=rememberScrollState()
            Column(Modifier.fillMaxWidth().padding(end=10.dp).verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(10.dp)){
            prompt.problem.candidates.forEach {entry->
                Surface(shape=LocalDesktopDesign.current.controlShape,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                    Column(Modifier.fillMaxWidth().desktopClick {c.chooseArtifactLocation(entry.path)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(entry.name,fontSize=14.sp)
                        Text(entry.path,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(androidx.compose.ui.Alignment.CenterEnd).fillMaxHeight())
            }
            OutlinedTextField(value=path,onValueChange={path=it},label={Text(tr("粘贴服务器完整路径"))},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Caption("也可以到右侧文件目录中选择；这里的路径属于 Hermes 服务器。")
        }},
        confirmButton={DeskTextButton(enabled=isAbsoluteRemotePath(path.trim()),onClick={c.chooseArtifactLocation(path)}){Text(tr("打开路径"))}},
        dismissButton={Row{
            DeskTextButton(onClick={DesktopFiles.copy("Hermes ${com.qingyu.hermescompanion.BuildConfig.VERSION_NAME}\n${prompt.problem.details}")}){Text(tr("复制排查信息"))}
            DeskTextButton(onClick={c.artifactLookupPrompt=null}){Text(tr("关闭"))}
        }},
    )
}
