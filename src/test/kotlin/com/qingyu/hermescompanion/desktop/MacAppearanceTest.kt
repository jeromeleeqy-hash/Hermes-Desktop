package com.qingyu.hermescompanion.desktop

import org.junit.Assert.*
import org.junit.Test

class MacAppearanceTest {
    @Test fun trafficLightsStayInsideCollapsedSidebarAtNativeSizesAndDuringResize() {
        for(width in listOf(12.0,14.0,16.0)) for(height in listOf(600.0,900.0,1200.0)) {
            val geometry=macTrafficLightGeometry(height,width,width,20.0)
            assertTrue(geometry.x.first()>=8)
            assertTrue(geometry.x.last()+width<=MacWindowChrome.SIDEBAR_WIDTH-8)
            assertTrue(geometry.buttonY>=10)
            assertTrue(geometry.buttonY+width<MacWindowChrome.CONTROLS_HEIGHT)
            assertEquals(height,geometry.containerY+geometry.containerHeight,0.0)
        }
    }
    @Test fun cocoaStructuresHaveThe64BitNativeGeometryLayout() {
        assertEquals(32,CocoaRect().size())
        assertEquals(16,CocoaPoint().size())
        val rect=CocoaRect().apply {x=12.0;y=23.0;width=80.0;height=46.0;write()}
        assertEquals(12.0,rect.pointer.getDouble(0),0.0)
        assertEquals(46.0,rect.pointer.getDouble(24),0.0)
    }
    @Test fun templateIconHasTransparentPaddingAndOnlyBlackAlphaAtEveryRetinaResolution() {
        val image=MacStatusIcon.image()
        assertEquals(listOf(18,36,54),image.resolutionVariants.map {it.getWidth(null)})
        image.resolutionVariants.forEach {variant->
            val b=variant as java.awt.image.BufferedImage
            var ink=0
            for(y in 0 until b.height) for(x in 0 until b.width) {
                val argb=b.getRGB(x,y)
                if(argb ushr 24>0){ink++;assertEquals(0,argb and 0xffffff)}
                if(x==0||y==0||x==b.width-1||y==b.height-1)assertEquals(0,argb ushr 24)
            }
            assertTrue(ink>b.width*b.height/5)
            assertTrue(ink<b.width*b.height*3/4)
        }
    }
}
