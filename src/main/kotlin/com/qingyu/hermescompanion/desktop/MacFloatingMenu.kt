package com.qingyu.hermescompanion.desktop

import java.awt.CheckboxMenuItem
import java.awt.Component
import java.awt.MenuItem
import java.awt.Point
import java.awt.PopupMenu
import javax.swing.SwingUtilities

/** AWT's macOS peer uses NSMenu tracking, including desktop clicks and Escape, without permissions. */
internal class MacFloatingMenu(private val actions:()->List<DesktopMenuAction>):AutoCloseable {
    private var popup:PopupMenu?=null
    private var owner:Component?=null
    fun show(component:Component,screenPoint:Point) {
        check(SwingUtilities.isEventDispatchThread())
        dismiss()
        if(!component.isShowing)return
        val next=PopupMenu()
        actions().forEach {action->
            if(action.dividerBefore)next.addSeparator()
            val item=if(action.checked!=null)CheckboxMenuItem(tr(action.label),action.checked).apply {
                addItemListener {dismiss();action.invoke()}
            }else MenuItem(tr(action.label)).apply {addActionListener {dismiss();action.invoke()}}
            next.add(item)
        }
        val local=Point(screenPoint);SwingUtilities.convertPointFromScreen(local,component)
        component.add(next);owner=component;popup=next
        next.show(component,local.x,local.y)
    }
    fun dismiss(){popup?.let {owner?.remove(it)};popup=null;owner=null}
    override fun close()=dismiss()
}
