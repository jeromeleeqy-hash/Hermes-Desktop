package com.qingyu.hermescompanion.desktop

import org.junit.Assert.*
import org.junit.Test
import javax.swing.SwingUtilities

class FloatingBallSurfaceTest {
    @Test fun freshBallInitializesAccessibilityWithoutInstallingAHoverTooltip() {
        SwingUtilities.invokeAndWait {
            val panel = FloatingBallSurface()
            assertEquals("单击提问 · 双击截图 · 按住说话，松开发送 · 拖动移动",
                panel.getAccessibleContext().accessibleDescription)
            assertNull(panel.toolTipText)
            assertSame(panel.getAccessibleContext(), panel.getAccessibleContext())
        }
    }
}
