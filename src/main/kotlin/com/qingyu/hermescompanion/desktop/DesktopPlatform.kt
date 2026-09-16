package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.data.VoiceInputSample
import com.qingyu.hermescompanion.data.voiceInputSample
import com.qingyu.hermescompanion.platform.DesktopHost
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.*
import java.net.URI
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*
import javax.swing.JFileChooser

object DesktopFiles {
    internal fun pickerOwner(parent:Component?=null):Window? = parent as? Window
        ?: parent?.let {javax.swing.SwingUtilities.getWindowAncestor(it)}
        ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        ?: Window.getWindows().lastOrNull {it.isVisible && it is Dialog}
        ?: Window.getWindows().lastOrNull {it.isVisible && it is Frame}
    private fun nativePicker(parent:Component?,title:String,mode:Int):FileDialog {
        val owner=pickerOwner(parent)
        return if(owner is Dialog)FileDialog(owner,tr(title),mode)else FileDialog(owner as? Frame,tr(title),mode)
    }
    fun choose(parent:Component?=null):List<File> {
        if(!EventQueue.isDispatchThread()) {
            val task=java.util.concurrent.FutureTask {choose(parent)}
            EventQueue.invokeAndWait(task);return task.get()
        }
        if(DesktopHost.isMac || DesktopHost.isWindows) {
            val picker=nativePicker(parent,"添加文件",FileDialog.LOAD)
            return try {picker.isMultipleMode=true;picker.isVisible=true;picker.files.toList()}finally {picker.dispose()}
        }
        val picker=JFileChooser().apply { isMultiSelectionEnabled=true; dialogTitle=tr("添加文件") }
        return if(picker.showOpenDialog(pickerOwner(parent))==JFileChooser.APPROVE_OPTION) picker.selectedFiles.toList() else emptyList()
    }
    fun chooseImage(parent:Component?=null):File? {
        if(!EventQueue.isDispatchThread()) {
            val task=java.util.concurrent.FutureTask<File?> {chooseImage(parent)}
            EventQueue.invokeAndWait(task);return task.get()
        }
        if(DesktopHost.isMac || DesktopHost.isWindows) {
            val picker=nativePicker(parent,"选择头像图片",FileDialog.LOAD)
            return try {
                picker.isMultipleMode=false
                picker.filenameFilter=FilenameFilter {_,name->name.substringAfterLast('.').lowercase() in setOf("png","jpg","jpeg","gif","bmp","webp")}
                picker.isVisible=true
                picker.file?.let {File(picker.directory,it)}
            }finally {picker.dispose()}
        }
        val picker=JFileChooser().apply {
            dialogTitle=tr("选择头像图片");isMultiSelectionEnabled=false
            fileFilter=javax.swing.filechooser.FileNameExtensionFilter("图片 · PNG / JPEG / GIF / BMP / WebP","png","jpg","jpeg","gif","bmp","webp")
        }
        return if(picker.showOpenDialog(pickerOwner(parent))==JFileChooser.APPROVE_OPTION)picker.selectedFile else null
    }
    fun save(document:WorkspaceDocument,parent:Component?=null) {
        if(DesktopHost.isMac || DesktopHost.isWindows) {
            // Both platforms supply a native Save panel and existing-file confirmation.
            val picker=nativePicker(parent,"保存到本机",FileDialog.SAVE)
            val destination=try {picker.file=document.name;picker.isVisible=true;picker.file?.let {File(picker.directory,it)}}finally {picker.dispose()}
            destination?.writeBytes(document.bytes)
            return
        }
        val picker=JFileChooser().apply { selectedFile=File(document.name); dialogTitle="保存到本机" }
        if(picker.showSaveDialog(pickerOwner(parent))!=JFileChooser.APPROVE_OPTION)return
        if(picker.selectedFile.exists() && javax.swing.JOptionPane.showConfirmDialog(parent,"文件已存在，要替换吗？","确认替换",javax.swing.JOptionPane.YES_NO_OPTION)!=javax.swing.JOptionPane.YES_OPTION)return
        picker.selectedFile.writeBytes(document.bytes)
    }
    fun attachment(file:File):PendingAttachment {
        require(file.isFile) { "请选择具体文件：${file.name}" }
        require(file.length()<=12L*1024*1024) { "${file.name} 超过 12 MB，请压缩后添加。" }
        val mime=com.qingyu.hermescompanion.platform.ModernImageSupport.mime(file.name) ?: Files.probeContentType(file.toPath()) ?: when(file.extension.lowercase()) { "md","log","yaml","yml","kt","py","js","ts","json","csv" -> "text/plain"; else -> "application/octet-stream" }
        return when {
            mime.startsWith("image/") -> { require(file.length()<=8*1024*1024) { "图片不能超过 8 MB。" }; PendingAttachment(name=file.name,mimeType=mime,dataUrl="data:$mime;base64,${Base64.getEncoder().encodeToString(file.readBytes())}") }
            file.length()<=512*1024 && (mime.startsWith("text/") || file.extension.lowercase() in setOf("md","markdown","json","xml","yaml","yml","csv","tsv","kt","java","py","js","ts","html","css","sh","sql","log")) -> {
                PendingAttachment(name=file.name,mimeType=mime,textContent=file.readText())
            }
            else -> PendingAttachment(name=file.name,mimeType=mime,uploadDataUrl="data:$mime;base64,${Base64.getEncoder().encodeToString(file.readBytes())}")
        }
    }
    fun remoteAttachment(doc:WorkspaceDocument):PendingAttachment {
        val base=PendingAttachment(name=doc.name,mimeType=doc.mimeType,remotePath=doc.path)
        return when {
            doc.mimeType.startsWith("image/") && doc.bytes.size<=8*1024*1024 -> base.copy(dataUrl="data:${doc.mimeType};base64,${Base64.getEncoder().encodeToString(doc.bytes)}")
            doc.mimeType.startsWith("text/") && doc.bytes.size<=512*1024 -> base.copy(textContent=doc.content)
            else -> base
        }
    }
    fun copy(text:String) { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text),null) }
    fun openLink(url:String) {
        val uri=URI(url)
        require(uri.scheme.lowercase() in setOf("http","https","mailto")) { "不支持此链接类型。" }
        if(Desktop.isDesktopSupported()) Desktop.getDesktop().browse(uri)
    }
}

class DesktopAudio {
    private val fxPlayback = FxAudioPlayback()
    private var line:TargetDataLine?=null
    @Volatile private var raw:File?=null
    private var writer:Thread?=null
    private val format=AudioFormat(16000f,16,1,true,false)
    @Volatile private var recording=false
    @Volatile private var writerFailure:Throwable?=null
    @Volatile private var playback:Process?=null
    @Volatile private var clip:Clip?=null
    fun start(onSample:(VoiceInputSample)->Unit={}) {
        check(line==null) { "已经在录音。" }
        val active=AudioSystem.getTargetDataLine(format)
        try {
            active.open(format);active.start()
            val file=Files.createTempFile("hermes-capture-",".pcm").toFile()
            line=active;raw=file;recording=true;writerFailure=null
            writer=Thread({
                try { file.outputStream().use { output ->
                    val buffer=ByteArray(1024)
                    while(recording) {
                        val count=active.read(buffer,0,buffer.size)
                        if(count>0) {
                            output.write(buffer,0,count)
                            var peak=0
                            for(i in 0 until count-1 step 2) { val value=((buffer[i].toInt() and 255) or (buffer[i+1].toInt() shl 8)).toShort().toInt();peak=maxOf(peak,kotlin.math.abs(value).coerceAtMost(32767)) }
                            onSample(voiceInputSample(peak))
                        }
                    }
                } } catch(e:Exception) { if(recording)writerFailure=e }
                finally { if(raw!==file)file.delete() }
            },"hermes-microphone").apply { isDaemon=true;start() }
        } catch(e:Exception){active.close();throw IllegalStateException("无法使用麦克风，请检查系统授权和输入设备。${e.message}",e)}
    }
    fun stop():File {
        val active=line ?: error("没有正在进行的录音。")
        recording=false;line=null;active.stop();active.close();writer?.join(5000)
        check(writer?.isAlive!=true) { "录音设备尚未停止，请重试。" };writer=null
        val source=raw ?: error("录音文件不存在。");raw=null
        val wave=Files.createTempFile("hermes-voice-",".wav").toFile()
        try {
            writerFailure?.let { throw IllegalStateException("录音中断：${it.message}",it) }
            require(source.length()>=3200) { "录音太短，请重试。" }
            AudioInputStream(source.inputStream(),format,source.length()/format.frameSize).use { AudioSystem.write(it,AudioFileFormat.Type.WAVE,wave) }
            return wave
        } catch(e:Exception){wave.delete();throw e}finally{source.delete()}
    }
    fun play(audio:SpeechAudio,rate:Float=1f) {
        val ext=if(audio.mimeType.contains("wav"))"wav" else if(audio.mimeType.contains("mp4"))"m4a" else "mp3"
        val file=Files.createTempFile("hermes-playback-",".$ext").toFile()
        var ownProcess:Process?=null
        var ownClip:Clip?=null
        try {
            file.writeBytes(audio.bytes)
            if(DesktopHost.isMac) {
                val process=ProcessBuilder("/usr/bin/afplay","-r",rate.coerceIn(.5f,2f).toString(),file.absolutePath).redirectError(ProcessBuilder.Redirect.DISCARD).start();playback=process;ownProcess=process
                check(process.waitFor(300,TimeUnit.SECONDS)) { "语音播放超时" }
                check(process.exitValue()==0) { "无法播放服务器返回的音频。" }
            } else if(DesktopHost.isWindows) fxPlayback.play(file,rate)
            else AudioSystem.getAudioInputStream(file).use { input -> val c=AudioSystem.getClip();clip=c;ownClip=c;c.open(input);c.start();while(c.isOpen && c.framePosition<c.frameLength)Thread.sleep(40);c.close() }
        } finally {ownProcess?.destroy();ownClip?.close();if(playback===ownProcess)playback=null;if(clip===ownClip)clip=null;file.delete()}
    }
    fun systemSpeak(text:String,language:String,rate:Float) {
        check(DesktopHost.isMac || DesktopHost.isWindows) { "系统朗读只在 macOS 和 Windows 上可用。" }
        val file=Files.createTempFile("hermes-speech-",".txt").toFile()
        val result=if(DesktopHost.isWindows)Files.createTempFile("hermes-speech-result-",".json").toFile() else null
        var ownProcess:Process?=null
        try {
            file.writeText(text)
            val process=if(DesktopHost.isWindows) WindowsSpeech.start("speak",file,result,language,rate) else {
                val args=mutableListOf("/usr/bin/say","-r",(175*rate).toInt().coerceIn(80,320).toString(),"-f",file.absolutePath)
                if(language.startsWith("zh"))args.addAll(listOf("-v","Tingting"))
                ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            }
            playback=process;ownProcess=process
            check(process.waitFor(300,TimeUnit.SECONDS)&&process.exitValue()==0) {
                "系统语音不可用，请检查系统音色，或选择服务器引擎。" + (result?.let {runCatching {org.json.JSONObject(it.readText(Charsets.UTF_8)).optString("error")}.getOrDefault("")} ?: "")
            }
        } finally {ownProcess?.destroy();ownProcess?.waitFor(3,TimeUnit.SECONDS);if(playback===ownProcess)playback=null;file.delete();result?.delete()}
    }
    fun stopPlayback() { playback?.destroy();playback=null;clip?.close();clip=null;fxPlayback.stop() }
    fun close() {
        recording=false
        val abandoned=raw;raw=null
        line?.stop();line?.close();line=null;stopPlayback()
        try {writer?.join(5000)}finally {writer=null;abandoned?.delete()}
        // The capture thread also deletes an abandoned file after closing its stream.
        // Windows cannot unlink the file while that stream is still open.
    }
}

object DesktopNotifications {
    private var tray:TrayIcon?=null
    private var menu:DesktopMenuHost?=null
    private var macFloatingItem:CheckboxMenuItem?=null
    val installed get()=tray!=null
    fun install(c:DesktopController,onOpen:()->Unit,onExit:()->Unit,onToggleFloating:(Boolean)->Unit) {
        if((!DesktopHost.isWindows&&!DesktopHost.isMac) || !SystemTray.isSupported() || tray!=null)return
        runCatching {
            if(DesktopHost.isMac) {
                // Native status menus support macOS menu tracking and dismiss on outside clicks.
                val popup=PopupMenu()
                fun item(label:String,action:()->Unit){popup.add(MenuItem(tr(label)).apply {addActionListener {action()}})}
                item("打开 Hermes",onOpen)
                macFloatingItem=CheckboxMenuItem(tr("桌面悬浮球"),c.floatingAssistantEnabled).apply {
                    addItemListener {onToggleFloating(state)};popup.add(this)
                }
                item("设置"){onOpen();c.loadSettings()}
                popup.addSeparator();item("退出 Hermes",onExit)
                val icon=TrayIcon(MacStatusIcon.image(),"Hermes",popup).apply {
                    isImageAutoSize=true;addActionListener {onOpen()}
                }
                SystemTray.getSystemTray().add(icon);tray=icon
                return@runCatching
            }
            menu=DesktopMenuHost(c,"桌面助手") {
                listOf(
                    DesktopMenuAction("open","打开 Hermes","panel",invoke=onOpen),
                    DesktopMenuAction("floating","桌面悬浮球","pin",checked=c.floatingAssistantEnabled){onToggleFloating(!c.floatingAssistantEnabled)},
                    DesktopMenuAction("settings","设置","settings"){onOpen();c.loadSettings()},
                    DesktopMenuAction("exit","退出 Hermes","close",dividerBefore=true,invoke=onExit),
                )
            }
            val icon=TrayIcon(javax.imageio.ImageIO.read(javaClass.getResource("/icon.png")),"Hermes").apply {
                isImageAutoSize=true
                addActionListener {menu?.dismiss();onOpen()}
                addMouseListener(object:java.awt.event.MouseAdapter(){
                    override fun mouseClicked(e:java.awt.event.MouseEvent){if(e.button==java.awt.event.MouseEvent.BUTTON1&&e.clickCount==1){menu?.dismiss();onOpen()}}
                    override fun mouseReleased(e:java.awt.event.MouseEvent){if(e.isPopupTrigger||e.button==java.awt.event.MouseEvent.BUTTON3)menu?.show(MouseInfo.getPointerInfo()?.location?:e.point)}
                })
            }
            SystemTray.getSystemTray().add(icon);tray=icon
        }.onFailure {menu?.close();menu=null;java.util.logging.Logger.getLogger("Hermes.Tray").log(java.util.logging.Level.WARNING,"System tray unavailable; close will minimize the main window.",it)}
    }
    fun updateBadge(count:Int) { tray?.toolTip=if(count>0)"Hermes · $count ${tr("未读")}" else "Hermes" }
    fun syncFloating(enabled:Boolean){macFloatingItem?.state=enabled}
    fun close() {menu?.close();menu=null;tray?.let {SystemTray.getSystemTray().remove(it)};tray=null;macFloatingItem=null}
    fun show(title:String,message:String,sound:Boolean=true) {
        if(DesktopHost.isWindows) {
            javax.swing.SwingUtilities.invokeLater {tray?.displayMessage(title,message,TrayIcon.MessageType.INFO)}
            return
        }
        if(!DesktopHost.isMac)return
        Thread {
            // Arguments are passed as process arguments, never interpolated into AppleScript source.
            val script="on run argv\nif item 3 of argv is \"true\" then\ndisplay notification (item 2 of argv) with title (item 1 of argv) sound name \"Glass\"\nelse\ndisplay notification (item 2 of argv) with title (item 1 of argv)\nend if\nend run"
            runCatching { ProcessBuilder("/usr/bin/osascript","-e",script,title,message,sound.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor(5,TimeUnit.SECONDS) }
        }.apply { isDaemon=true; start() }
    }
}
