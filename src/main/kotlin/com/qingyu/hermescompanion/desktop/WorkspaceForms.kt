package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyu.hermescompanion.model.*

@Composable fun ProjectEditor(c:DesktopController,onDismiss:()->Unit) {
    val draft=settingDraft(c,"new-project",mapOf("name" to "","path" to ""),SettingCodec::text,SettingCodec::text)
    var value by draft
    HermesDialog(onDismissRequest={if(!draft.saving)onDismiss()},title={Text(tr("新建项目"))},text={Column(Modifier.width(500.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FormInput("项目名称",value["name"].orEmpty(),{value=value+("name" to it)},enabled=!draft.saving)
        FormInput("服务器目录的完整路径",value["path"].orEmpty(),{value=value+("path" to it)},enabled=!draft.saving)
        SubtleText("填写 Hermes 服务器上的目录；例如 /workspace/my-project。",maxLines=2)
        draft.error?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={SmallButton(if(draft.saving)"正在创建…"else"创建",{
        val p=c.profile;val submitted=value;draft.saving=true;draft.error=null
        c.request(p,{it.createProject(submitted.getValue("name").trim(),submitted.getValue("path").trim())},finished={draft.saving=false},failed={draft.error=it}) {project->
            draft.reset();if(c.profile==p){c.refresh();c.chooseProject(project)};c.notice="项目已创建";onDismiss()
        }
    },true,enabled=!draft.saving&&value["name"].orEmpty().isNotBlank()&&value["path"].orEmpty().isNotBlank())},dismissButton={DeskTextButton(onClick=onDismiss,enabled=!draft.saving){Text(tr("稍后再写"))}})
}

@Composable fun SessionRenameDialog(c:DesktopController,session:HermesSession,onDismiss:()->Unit) {
    val draft=settingDraft(c,"rename:${session.id}",mapOf("name" to session.title),SettingCodec::text,SettingCodec::text)
    HermesDialog(onDismissRequest={if(!draft.saving)onDismiss()},title={Text(tr("重命名会话"))},text={Column(Modifier.width(440.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FormInput("会话名称",draft.value["name"].orEmpty(),{draft.value=mapOf("name" to it)},enabled=!draft.saving)
        draft.error?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={SmallButton(if(draft.saving)"正在保存…"else"保存",{
        val submitted=draft.value;val name=submitted.getValue("name").trim();draft.saving=true;draft.error=null
        c.request(session.profile,{it.renameSession(session.id,name)},finished={draft.saving=false},failed={draft.error=it}) {
            draft.commit(submitted,mapOf("name" to name))
            if(c.profile==session.profile){if(c.currentSession?.scopedId==session.scopedId)c.currentSession=c.currentSession?.copy(title=name);c.refresh()}
            c.notice="会话已重命名";onDismiss()
        }
    },true,enabled=!draft.saving&&draft.value["name"].orEmpty().isNotBlank())},dismissButton={DeskTextButton(onClick=onDismiss,enabled=!draft.saving){Text(tr("取消"))}})
}
