@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.qingyu.hermescompanion.today.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.io.File

object Render200Previews {
    @JvmStatic fun main(args:Array<String>)=runBlocking(Dispatchers.Swing){
        val out=File(args.firstOrNull()?:"build/previews-2.0.0").apply {mkdirs()}
        val raw=File("src/main/resources/today/hermes-today-examples.json").readText()
        val interactions=TodayBoard.decode(File("src/main/resources/today/hermes-today-interactions.json").readText(),"/work")
        val cases=listOf("simple","deep","deep-narrow","deep-paper","deep-glass","deep-dark","settings","interaction","voice")
        for(name in cases){
            val c=DesktopController(true);c.reduceMotion=true;c.page=if(name=="voice")Page.PROFILE else Page.HOME;c.setFilesPanel(false)
            c.today.preview(TodayBoard.decode(raw,"/work"),name!="simple")
            if(name=="voice")c.settingsSection="语音"
            if(name.contains("paper"))c.skin="纸间留白"
            if(name.contains("glass"))c.skin="流光玻璃"
            if(name.contains("dark"))c.appearance="深色"
            val narrow=name.endsWith("narrow");if(narrow){c.sidebarCollapsed=true;c.textScale=1.2f}
            val width=if(narrow)980 else 1440;val height=if(narrow)680 else 900
            val scene=ImageComposeScene(width,height,density=Density(1f),coroutineContext=Dispatchers.Swing){
                HermesTheme(c){DesktopBackdrop{
                    CompositionLocalProvider(LocalDesktopWindowControls provides DesktopWindowControls()){
                        DesktopFrame {DesktopWorkspace(c)}
                    }
                    if(name=="settings")Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){TodaySettings(c,true){}}
                    if(name=="interaction")Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){TodayDetail(c,interactions.cards.first {it.presentation.interaction?.type==TodayLayout.MEETING_ACTIONS},true){}}
                }}
            }
            try{
                repeat(8){scene.render(System.nanoTime()).close();delay(100)}
                scene.render(System.nanoTime()).use {image->image.encodeToData()!!.use {File(out,"$name.png").writeBytes(it.bytes)}}
                println("Rendered $name ${width}x$height")
            }finally{scene.close();c.close()}
        }
    }
}
