package com.qingyu.hermescompanion.update

import java.io.File
import com.qingyu.hermescompanion.platform.DesktopHost

object UpdateInstaller {
    fun installedApp(): File? {
        val location=runCatching { File(javaClass.protectionDomain.codeSource.location.toURI()) }.getOrNull() ?: return null
        val exe=System.getProperty("jpackage.app-path")?.let(::File) ?: location
        if(DesktopHost.isWindows) return generateSequence(exe) {it.parentFile}.map {File(it,"Hermes.exe")}.firstOrNull {it.isFile}
        if(DesktopHost.isMac) return generateSequence(exe) {it.parentFile}.firstOrNull {it.name == "Hermes.app" && it.isDirectory}
        return null
    }
    fun launch(packageFile: File, release: UpdateRelease, directory: File): File {
        require(ReleaseUpdates.verify(packageFile,release)) { "安装包已变化，请重新下载" }
        val app=installedApp() ?: error("开发模式暂不支持自动安装，请使用正式安装版")
        val attempt=java.nio.file.Files.createTempDirectory(directory.toPath(),"install-").toFile()
        val ready=File(attempt,"ready")
        val armed=File(attempt,"armed")
        val result=File(directory,"install-result.txt")
        val pid=ProcessHandle.current().pid().toString()
        val name=if(DesktopHost.isMac) "install-macos.sh" else "install-windows.ps1"
        if(DesktopHost.isMac) {
            require(!app.absolutePath.startsWith("/Volumes/") && !app.absolutePath.contains("/AppTranslocation/")) { "请先将 Hermes 移到应用程序文件夹，再使用自动更新" }
            require(app.canWrite() && app.parentFile.canWrite()) { "没有替换应用的权限，请手动安装新版" }
        }
        val helper=File(attempt,name)
        helper.writeBytes(requireNotNull(javaClass.getResourceAsStream("/update/$name")).use {it.readBytes()})
        val command=if(DesktopHost.isMac) listOf("/bin/bash",helper.absolutePath,pid,packageFile.absolutePath,app.absolutePath,release.version.toString(),release.sha256,result.absolutePath,ready.absolutePath,armed.absolutePath)
        else listOf("powershell.exe","-NoProfile","-NonInteractive","-File",helper.absolutePath,"-ParentPid",pid,"-Package",packageFile.absolutePath,"-AppExe",app.absolutePath,"-ExpectedHash",release.sha256,"-Result",result.absolutePath,"-Ready",ready.absolutePath,"-Armed",armed.absolutePath)
        val process=ProcessBuilder(command).redirectErrorStream(true).redirectOutput(File(directory,"install-helper.log")).start()
        val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(3)
        while(!ready.isFile && process.isAlive && System.nanoTime()<deadline) Thread.sleep(200)
        if(!ready.isFile){process.destroy();error("系统未能准备更新程序；当前应用继续运行，请手动安装或查看更新日志")}
        return armed
    }
}
