package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.*
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

/** Only document text crosses this bridge. No filesystem or application APIs are exposed. */
class EditorBridge(private val changed:(String)->Unit) {
    fun onChanged(markdown:String) {if(SwingUtilities.isEventDispatchThread())changed(markdown) else SwingUtilities.invokeLater { changed(markdown) } }
}
class WebEditorHandle {
    @Volatile var engine:WebEngine?=null
    @Volatile var onChange:((String)->Unit)?=null
    fun command(name:String) {
        if(name !in setOf("bold","italic","ul","ol","quote","hr","h1","h2","h3","p"))return
        val api=engine?:return
        runCatching {Platform.runLater {if(engine===api)api.executeScript("window.hermesCommand('$name')")}}
    }
    fun flush(done:()->Unit) {
        val api=engine?:run {SwingUtilities.invokeLater(done);return}
        val completed=AtomicBoolean(false)
        fun finish(text:String?=null) {SwingUtilities.invokeLater {
            if(completed.compareAndSet(false,true)){if(text!=null)onChange?.invoke(text);done()}
        }}
        // Closing a document must remain possible if the renderer has stopped responding.
        val timeout=Timer(1800){finish()}.apply {isRepeats=false;start()}
        runCatching {Platform.runLater {
            val text=runCatching {if(engine===api)api.executeScript("window.hermesMarkdown()") as? String else null}.getOrNull()
            finish(text);SwingUtilities.invokeLater {timeout.stop()}
        }}.onFailure {timeout.stop();finish()}
    }
}

/** All waits for JavaFX startup happen away from the AWT / Compose event thread. */
private class WebSurface(val editable:Boolean,val bridge:EditorBridge,val handle:WebEditorHandle?,val state:(String? ,Boolean)->Unit) {
    private val closed=AtomicBoolean(false)
    private val host=JPanel(BorderLayout())
    @Volatile private var api:WebEngine?=null
    @Volatile private var view:WebView?=null
    @Volatile private var fx:JFXPanel?=null
    @Volatile private var html=""
    @Volatile private var zoom=1.0
    private var loaded=false // JavaFX thread only
    private var timeout:Timer?=null // AWT thread only
    private fun ui(action:()->Unit)=SwingUtilities.invokeLater {if(!closed.get())action()}
    private fun fail(message:String)=ui {timeout?.stop();state(message,false);close()}
    fun panel():JPanel {
        timeout=Timer(15000){fail("网页预览没有及时响应。可以重新加载，或切换到源码查看内容。")}.apply {isRepeats=false;start()}
        // JFXPanel's first constructor starts JavaFX synchronously. Do not do that
        // inside SwingPanel.factory, where Compose may still hold its render lock.
        CompletableFuture.runAsync {
            try {
                FxRuntime.start()
                Platform.runLater {ui {
                    try {
                        val panel=JFXPanel();fx=panel
                        host.add(panel,BorderLayout.CENTER);host.revalidate();host.repaint()
                        Platform.runLater {
                            if(closed.get())return@runLater
                            try {
                                val web=WebView();view=web;val engine=web.engine;api=engine
                                engine.isJavaScriptEnabled=editable;engine.createPopupHandler=null
                                engine.loadWorker.stateProperty().addListener {_,_,value->
                                    when(value) {
                                        Worker.State.SUCCEEDED->{
                                            if(editable)(engine.executeScript("window") as? JSObject)?.setMember("HermesEditor",bridge)
                                            else engine.document?.getElementsByTagName("a")?.let {links->for(i in 0 until links.length) {
                                                val link=links.item(i) as? org.w3c.dom.Element?:continue
                                                val href=link.getAttribute("href")
                                                if(href.startsWith("https://")||href.startsWith("http://"))(link as? org.w3c.dom.events.EventTarget)?.addEventListener("click",{event->
                                                    event.preventDefault();ui {runCatching {DesktopFiles.openLink(href)}}
                                                },false)
                                            }}
                                            ui {timeout?.stop();state(null,true)}
                                        }
                                        Worker.State.FAILED->fail("这个网页暂时无法显示。可以重新加载，或切换到源码查看内容。")
                                        else->Unit
                                    }
                                }
                                engine.locationProperty().addListener {_,_,url->if(!url.isNullOrBlank()&&!url.startsWith("about:blank"))engine.loadWorker.cancel()}
                                if(closed.get())return@runLater
                                panel.scene=Scene(web)
                                if(editable){handle?.engine=engine;handle?.onChange=bridge::onChanged}
                                load()
                            }catch(e:Throwable){webDiagnostic(e);fail("网页组件无法启动，请重新加载或切换到源码查看。")}
                        }
                    }catch(e:Throwable){webDiagnostic(e);fail("网页组件无法启动，请重新加载或切换到源码查看。")}
                }}
            }catch(e:Throwable){webDiagnostic(e);fail("网页组件无法启动，请重新加载或切换到源码查看。")}
        }
        return nativeDocumentContainer(host)
    }
    fun update(content:String,scale:Double) {
        val changed=html!=content;html=content;zoom=scale
        if(api!=null)runCatching {Platform.runLater {if(!closed.get()) {view?.zoom=zoom;if(changed)load()}}}
    }
    private fun load() { // JavaFX thread
        if(closed.get())return
        view?.zoom=zoom
        if(html.isBlank()||editable&&loaded)return
        loaded=true
        ui {state(null,false);timeout?.restart()}
        api?.loadContent(html)
    }
    fun close() {
        if(!closed.compareAndSet(false,true))return
        SwingUtilities.invokeLater {timeout?.stop()}
        val engine=api
        if(handle?.engine===engine){handle?.engine=null;handle?.onChange=null}
        if(engine!=null)runCatching {Platform.runLater {engine.loadWorker.cancel();engine.load(null);fx?.scene=null;api=null;view=null}}
    }
}

private fun webDiagnostic(error:Throwable) {
    // Record the startup failure, never document contents or gateway credentials.
    java.util.logging.Logger.getLogger("Hermes.WebPreview").log(java.util.logging.Level.WARNING,"Embedded renderer failed",error)
}

@Composable fun WebDocument(html:String,modifier:Modifier=Modifier,editable:Boolean=false,handle:WebEditorHandle?=null,zoom:Double=1.0,onChanged:(String)->Unit={}) {
    val changed by rememberUpdatedState(onChanged)
    var attempt by remember {mutableIntStateOf(0)}
    var failure by remember(attempt){mutableStateOf<String?>(null)}
    var ready by remember(attempt){mutableStateOf(false)}
    val surface=remember(editable,handle,attempt){WebSurface(editable,EditorBridge {changed(it)},handle){error,loaded->failure=error;ready=loaded}}
    DisposableEffect(surface){onDispose {surface.close()}}
    SideEffect {surface.update(html,zoom)}
    Box(modifier) {
        if(failure==null) {
            SwingPanel(modifier=Modifier.fillMaxSize(),factory=surface::panel)
            if(!ready)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
        }else if(!editable)Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Caption("已切换为兼容预览",Modifier.weight(1f))
                SmallButton("重试网页组件",{attempt++})
            }
            CompatibleDocument(html,Modifier.weight(1f).fillMaxWidth(),zoom)
        }else Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Heading("暂时无法显示网页")
            Caption(failure.orEmpty(),Modifier.padding(vertical=14.dp))
            SmallButton("重新加载",{attempt++})
        }
    }
}
