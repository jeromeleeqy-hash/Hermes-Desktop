package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qingyu.hermescompanion.BuildConfig
import com.qingyu.hermescompanion.platform.DesktopHost
import com.qingyu.hermescompanion.update.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File

class DesktopUpdates(private val c: DesktopController) {
    var open by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var status by mutableStateOf("尚未检查更新")
    var progress by mutableStateOf(0f)
    var release by mutableStateOf<UpdateRelease?>(null)
    var downloaded by mutableStateOf<File?>(null)
    var autoCheck by mutableStateOf(c.store.get("desktopAutoUpdateCheck","true") == "true")
    var autoDownload by mutableStateOf(c.store.get("desktopAutoUpdateDownload","true") == "true")
    var installAction: (() -> Unit)? = null
    private var installTicket: File? = null
    fun confirmExit() { installTicket?.writeText("ready");installTicket=null }
    private val directory=DesktopHost.dataDirectory().resolve("updates").toFile()
    private val platform get() = when {
        DesktopHost.isMac && System.getProperty("os.arch") in listOf("aarch64","arm64") -> "macos-arm64"
        DesktopHost.isWindows && System.getProperty("os.arch") in listOf("amd64","x86_64") -> "windows-x64"
        else -> "unsupported"
    }
    suspend fun monitor() {
        if(c.demo)return
        val result=File(directory,"install-result.txt")
        if(result.isFile){status=result.readText().take(2000);result.delete();open=true}
        delay(15000)
        while(currentCoroutineContext().isActive) {
            if(autoCheck)check(false)
            delay(6*60*60*1000L)
        }
    }
    fun check(manual:Boolean=true) {
        if(manual)open=true
        if(busy)return
        busy=true
        c.scope.launch {
            try {
                status="正在检查 GitHub 正式版本…"
                val next=withContext(Dispatchers.IO){ReleaseUpdates.check(BuildConfig.VERSION_NAME,platform)}
                release=next
                downloaded=next?.let { candidate ->
                    File(directory,candidate.name).takeIf { ReleaseUpdates.verify(it,candidate) }
                }
                progress=if(downloaded!=null)1f else 0f
                status=if(next==null)"当前已是最新正式版本（${BuildConfig.VERSION_NAME}）" else "发现新版本 ${next.version}"
                if(next!=null){
                    open=true
                    if(downloaded!=null)status="${next.version} 已下载并通过校验，可以重启更新"
                    else if(autoDownload)fetch(next)
                }
            } catch(e:CancellationException){throw e} catch(e:Exception){status="检查更新失败：${e.message}。可以稍后重试。"}
            finally{busy=false}
        }
    }
    private suspend fun fetch(next:UpdateRelease) {
        status="正在后台下载安装包…"
        downloaded=withContext(Dispatchers.IO) {
            ReleaseUpdates.download(next,directory){done,total->c.scope.launch(Dispatchers.Swing){progress=(done.toDouble()/total).toFloat()}}
        }
        status="${next.version} 已下载并通过校验，可以重启更新"
    }
    fun download() {
        val next=release ?: return
        if(busy)return
        busy=true
        c.scope.launch {
            try{fetch(next)}catch(e:CancellationException){throw e}catch(e:Exception){status="下载失败：${e.message}。请重试。"}finally{busy=false}
        }
    }
    fun prepareInstall(onPrepared:()->Unit) {
        if(busy)return
        if(c.runs.isNotEmpty() || c.recording || c.voice.active || c.queued.values.any {it.isNotEmpty()}) {
            status="请先结束正在进行的回复、录音和待发送队列，再重启更新";return
        }
        val file=downloaded ?: return
        val next=release ?: return
        val prepare={
            if(c.flushCheckpoint()) {
                busy=true;status="正在准备安装…"
                c.scope.launch {
                    try {
                        installTicket=withContext(Dispatchers.IO){UpdateInstaller.launch(file,next,directory)}
                        if(c.runs.isNotEmpty() || c.recording || c.voice.active) { installTicket=null;status="安装准备已暂停，请在任务结束后重试";busy=false;return@launch }
                        onPrepared()
                    }catch(e:Exception){status="无法自动安装：${e.message}";busy=false}
                }
            }
            Unit
        }
        c.flushEditor?.invoke(prepare) ?: prepare()
    }
}

@Composable fun DesktopUpdateSettings(c:DesktopController) {
    val u=c.desktopUpdates
    SettingCard("软件更新","当前版本 ${BuildConfig.VERSION_NAME} · 更新来自 Hermes-Desktop 的 GitHub 正式 Release") {
        Toggle("自动检查新版本",u.autoCheck){u.autoCheck=it;c.savePreference("desktopAutoUpdateCheck",it.toString())}
        Toggle("发现更新后自动下载",u.autoDownload){u.autoDownload=it;c.savePreference("desktopAutoUpdateDownload",it.toString())}
        Text(u.status)
        SmallButton("检查更新",{u.check()})
        if(u.downloaded!=null)SmallButton("查看已下载的更新",{u.open=true})
    }
}

@Composable fun DesktopUpdateDialog(c:DesktopController) {
    val u=c.desktopUpdates
    if(!u.open)return
    AlertDialog(onDismissRequest={u.open=false},title={Text(u.release?.let {"Hermes ${it.version}"} ?: "软件更新")},
        text={Column(Modifier.widthIn(max=520.dp).heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(u.status)
            if(u.busy){LinearProgressIndicator(Modifier.fillMaxWidth());if(u.progress>0)Text("${(u.progress*100).toInt()}%")}
            u.release?.let {Text(it.notes.ifBlank {"此版本未提供更新说明"})}
            Text("安装前会保存本机草稿。下载可以在后台进行，安装需要退出并重新打开 Hermes。")
        }},
        confirmButton={TextButton(enabled=!u.busy,onClick={when {u.downloaded!=null->u.installAction?.invoke();u.release!=null->u.download();else->u.check()}}){Text(when {u.downloaded!=null->"重启更新";u.release!=null->"下载更新";else->"重新检查"})}},
        dismissButton={TextButton(onClick={u.open=false}){Text("稍后")}})
}
