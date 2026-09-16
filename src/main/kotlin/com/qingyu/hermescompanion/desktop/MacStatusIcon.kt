package com.qingyu.hermescompanion.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage

/** A small headset assistant mark. AppKit tints the alpha mask for light/dark and selected menus. */
internal object MacStatusIcon {
    fun image()=BaseMultiResolutionImage(*intArrayOf(18,36,54).map(::render).toTypedArray())
    fun render(size:Int):BufferedImage=BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB).apply {
        val g=createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            g.scale(size/18.0,size/18.0);g.color=Color.BLACK
            g.stroke=BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
            g.draw(Arc2D.Double(3.0,2.0,12.0,12.0,0.0,180.0,Arc2D.OPEN))
            g.fill(RoundRectangle2D.Double(1.75,7.0,3.0,6.0,2.0,2.0))
            g.fill(RoundRectangle2D.Double(13.25,7.0,3.0,6.0,2.0,2.0))
            g.draw(Path2D.Double().apply {moveTo(5.25,7.75);lineTo(5.25,11.25);curveTo(5.25,16.0,12.75,16.0,12.75,11.25);lineTo(12.75,7.75)})
            g.fill(Ellipse2D.Double(6.35,8.7,1.6,1.9));g.fill(Ellipse2D.Double(10.05,8.7,1.6,1.9))
        } finally {g.dispose()}
    }
}
