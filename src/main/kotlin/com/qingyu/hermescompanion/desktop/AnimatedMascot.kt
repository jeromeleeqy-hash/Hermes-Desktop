package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.*
import org.jetbrains.skia.*

/** Decode only the current frame, and suspend animation when the window is inactive. */
@Composable fun AnimatedMascot(c:DesktopController,modifier:Modifier=Modifier) {
    val asset=when {
        c.recording->"listening"
        c.runs.isNotEmpty()->"working"
        c.completions.firstOrNull()?.let { System.currentTimeMillis()-it.completedAtMillis<12_000 }==true->"done"
        c.sessions.isEmpty()->"welcome"
        else->"idle"
    }
    var frame by remember(asset) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(asset,c.appFocused,c.reduceMotion) {
        if(c.reduceMotion || !c.appFocused)return@LaunchedEffect
        try {
            val bytes=withContext(Dispatchers.IO) { object {}.javaClass.getResourceAsStream("/mascot/$asset.webp")!!.use { it.readBytes() } }
            Data.makeFromBytes(bytes).use { data->Codec.makeFromData(data).use { codec->
                Bitmap().use { bitmap->
                    bitmap.allocPixels(codec.imageInfo)
                    while(isActive)for(index in 0 until codec.frameCount.coerceAtLeast(1)) {
                        ensureActive()
                        val next=withContext(Dispatchers.Default) {
                            if(index==0)codec.readPixels(bitmap,0)else codec.readPixels(bitmap,index,index-1)
                            Image.makeFromBitmap(bitmap).toComposeImageBitmap()
                        }
                        frame=next
                        delay(codec.getFrameInfo(index).duration.coerceAtLeast(40).toLong())
                    }
                }
            } }
        } catch(e:CancellationException){throw e}catch(_:Exception){frame=null}
    }
    if(frame==null)Image(painterResource("mascot.png"),c.hermesName,modifier)
    else Image(frame!!,c.hermesName,modifier)
}
