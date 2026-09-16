package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.platform.DesktopHost
import com.sun.jna.NativeLibrary

internal fun requireScreenAccess(granted:()->Boolean,request:()->Boolean) {
    check(granted()||request()) {
        "请在系统设置的“隐私与安全性”中允许 Hermes 录制屏幕，然后重试截图；如仍无法截图，请退出并重新打开 Hermes。"
    }
}

/** Load CoreGraphics only when the user starts a capture on macOS. */
internal object MacScreenAccess {
    private val graphics by lazy {NativeLibrary.getInstance("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics")}
    fun ensureGranted() {
        if(!DesktopHost.isMac)return
        requireScreenAccess(
            {graphics.getFunction("CGPreflightScreenCaptureAccess").invokeInt(emptyArray())!=0},
            {graphics.getFunction("CGRequestScreenCaptureAccess").invokeInt(emptyArray())!=0},
        )
    }
}
