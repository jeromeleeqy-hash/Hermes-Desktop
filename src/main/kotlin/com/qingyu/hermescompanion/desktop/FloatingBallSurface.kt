package com.qingyu.hermescompanion.desktop

import javax.swing.JPanel

/** Shared with the native floating ball so initialization is testable without a display. */
internal open class FloatingBallSurface : JPanel() {
    init {
        // Call Swing's lazy initializer explicitly: the inherited protected field starts null.
        // Keep this out of toolTipText: native tooltips can trigger hover flicker.
        getAccessibleContext().accessibleDescription =
            "单击提问 · 双击截图 · 按住说话，松开发送 · 拖动移动"
    }
}
