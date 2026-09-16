package com.qingyu.hermescompanion.desktop

import org.junit.Test
import org.junit.Assert.*
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

class DesktopMenuBoundsTest {
    @Test fun trayMenuStaysAboveTaskbarAndInsideRightEdge() {
        val work=Rectangle(0,0,1920,1040)
        val menu=desktopMenuBounds(Point(1890,1060),Dimension(280,278),listOf(work))
        assertTrue(work.contains(menu));assertEquals(1040,menu.y+menu.height)
        assertTrue(menu.x<1890)
    }
    @Test fun floatingMenuUsesAdjacentSpaceAndFlipsAtEdges() {
        val work=Rectangle(0,0,1600,900)
        val below=desktopMenuBounds(Point(100,100),Dimension(280,318),listOf(work))
        assertEquals(Point(106,106),below.location)
        val above=desktopMenuBounds(Point(1540,850),Dimension(280,318),listOf(work))
        assertTrue(above.x<1540);assertTrue(above.y+above.height<850);assertTrue(work.contains(above))
    }
    @Test fun leftMonitorAndDisconnectedSavedPositionChooseNearestWorkArea() {
        val left=Rectangle(-1920,40,1920,1040);val primary=Rectangle(0,0,2560,1400)
        assertTrue(left.contains(desktopMenuBounds(Point(-8,1030),Dimension(280,318),listOf(left,primary))))
        assertTrue(left.contains(desktopMenuBounds(Point(-4000,100),Dimension(280,318),listOf(primary,left))))
    }
    @Test fun smallWorkAreaClampsOversizedMenu() {
        val work=Rectangle(50,70,240,220)
        assertEquals(work,desktopMenuBounds(Point(270,275),Dimension(280,400),listOf(work)))
    }
}
