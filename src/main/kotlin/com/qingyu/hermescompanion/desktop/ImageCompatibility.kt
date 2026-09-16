package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.qingyu.hermescompanion.platform.ModernImageSupport
import org.jetbrains.skia.*

private fun validateImage(bytes:ByteArray) {
    Data.makeFromBytes(bytes).use {data->Codec.makeFromData(data).use {codec->
        ModernImageSupport.checkDimensions(codec.imageInfo.width.toLong(),codec.imageInfo.height.toLong())
    }}
}

internal fun compatiblePng(bytes:ByteArray,name:String):ByteArray {
    // Prefer bundled Skia. Native fallback uses the original bytes only in a private temp folder.
    return try {
        validateImage(bytes)
        Image.makeFromEncoded(bytes).use {image->
            requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) {"无法生成 PNG 副本。"}.use {it.bytes}
        }
    }catch(e:Exception){
        if(!ModernImageSupport.isModern(name))throw e
        ModernImageSupport.png(bytes,name)
    }
}

internal fun decodeCompatibleImage(bytes:ByteArray,name:String):ImageBitmap {
    val encoded=try{validateImage(bytes);bytes}catch(e:Exception){
        if(!ModernImageSupport.isModern(name))throw e
        ModernImageSupport.png(bytes,name).also(::validateImage)
    }
    // Compose owns the Skia-backed image for the lifetime of the bitmap.
    return Image.makeFromEncoded(encoded).toComposeImageBitmap()
}
