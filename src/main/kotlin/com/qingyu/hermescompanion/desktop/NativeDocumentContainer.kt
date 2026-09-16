package com.qingyu.hermescompanion.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Panel
import javax.swing.JPanel

/** Give embedded document views their own native surface beside Skia's canvas.
 * Lightweight-only Swing children can be obscured by the canvas on fallback renderers.
 * The native parent keeps document painting and input on the same visible surface.
 */
internal fun nativeDocumentContainer(content:Component):JPanel = JPanel(BorderLayout()).apply {
    background=Color.WHITE
    add(Panel(BorderLayout()).apply {background=Color.WHITE;add(content,BorderLayout.CENTER)},BorderLayout.CENTER)
}
