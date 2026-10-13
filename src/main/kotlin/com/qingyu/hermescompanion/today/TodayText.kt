package com.qingyu.hermescompanion.today

import com.qingyu.hermescompanion.i18n.desktopLanguage
import com.qingyu.hermescompanion.i18n.traditionalLabel

fun todayText(zh:String,en:String):String = when(desktopLanguage){"en"->en;"zh-TW"->traditionalLabel(zh);else->zh}
