@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop
import androidx.compose.ui.ImageComposeScene
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File
object RenderComposer186 {
    @JvmStatic fun main(args:Array<String>) {
        try {runBlocking(Dispatchers.Swing) {
            val c=DesktopController(true);c.reduceMotion=true;c.appearance="深色";c.page=Page.CHAT;c.sidebarCollapsed=true;c.setFilesPanel(true)
            try {
                for(appearance in listOf("浅色","深色")) for(skin in listOf("轻盈办公","纸间留白","流光玻璃")) {
                    c.skin=skin;c.appearance=appearance
                    val scene=ImageComposeScene(1600,920,coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopBackdrop{DesktopWorkspace(c)}}}
                    try {
                        repeat(8){scene.render(System.nanoTime()).close();delay(25)}
                        val out=File("build/reports/visual-1.8.7/$appearance-$skin.png");out.parentFile.mkdirs()
                        out.writeBytes(scene.render(System.nanoTime()).use {it.encodeToData()!!.use {data->data.bytes}})
                    }finally {scene.close()}
                }
            }finally {c.close()}
        };System.exit(0)}catch(e:Throwable){e.printStackTrace();System.exit(1)}
    }
}
