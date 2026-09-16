package com.qingyu.hermescompanion.platform

object Base64 {
    const val NO_WRAP = 0
    const val DEFAULT = 0
    fun encodeToString(bytes: ByteArray, flags: Int): String = java.util.Base64.getEncoder().encodeToString(bytes)
    fun decode(value: String, flags: Int): ByteArray = java.util.Base64.getMimeDecoder().decode(value)
}
