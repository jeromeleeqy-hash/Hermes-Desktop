package com.qingyu.hermescompanion.desktop

import java.awt.Desktop
import java.awt.desktop.*
import javax.swing.SwingUtilities

/** Native app-menu actions share the same checkpoint/quit path as our own menu. */
internal class MacApplicationHooks(
    private val desktop:Desktop,
    onOpen:()->Unit,
    onQuit:()->Unit,
    onSettings:()->Unit,
    onAbout:()->Unit,
    onBackground:()->Unit,
    private val dispatch:((()->Unit)->Unit)={action->SwingUtilities.invokeLater(action)},
):AutoCloseable {
    @Volatile private var closed=false
    private val cleanup=mutableListOf<()->Unit>()
    var canReopen=false;private set
    private fun enqueue(action:()->Unit){dispatch {if(!closed)action()}}
    init {
        fun register(action:Desktop.Action,add:()->Unit,remove:()->Unit) {
            if(desktop.isSupported(action)) {
                runCatching {add();cleanup+=remove}.onFailure {
                    java.util.logging.Logger.getLogger("Hermes.Mac").log(java.util.logging.Level.WARNING,"Unable to install $action",it)
                }
            }
        }
        val reopen=AppReopenedListener {enqueue(onOpen)}
        register(Desktop.Action.APP_EVENT_REOPENED,{desktop.addAppEventListener(reopen);canReopen=true},{desktop.removeAppEventListener(reopen)})
        val foreground=object:AppForegroundListener {
            override fun appRaisedToForeground(e:AppForegroundEvent){}
            override fun appMovedToBackground(e:AppForegroundEvent){enqueue(onBackground)}
        }
        register(Desktop.Action.APP_EVENT_FOREGROUND,{desktop.addAppEventListener(foreground)},{desktop.removeAppEventListener(foreground)})
        register(Desktop.Action.APP_QUIT_HANDLER,{
            desktop.setQuitHandler {_,response->
                // The controller may need to save an editor or let the user resolve a conflict.
                response.cancelQuit();enqueue(onQuit)
            }
        },{desktop.setQuitHandler(null)})
        register(Desktop.Action.APP_PREFERENCES,{desktop.setPreferencesHandler {enqueue(onSettings)}},{desktop.setPreferencesHandler(null)})
        register(Desktop.Action.APP_ABOUT,{desktop.setAboutHandler {enqueue(onAbout)}},{desktop.setAboutHandler(null)})
    }
    override fun close(){if(closed)return;closed=true;cleanup.asReversed().forEach {runCatching(it)};cleanup.clear()}
}
