package com.qingyu.hermescompanion.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*

@Composable internal fun DesktopToggle(value:Boolean) {
    val colors=MaterialTheme.colorScheme
    val shift by animateDpAsState(if(value)14.dp else 0.dp,tween(motionMillis(160)),label="toggle-position")
    val fill by animateColorAsState(if(value)colors.primary else colors.onSurface.copy(alpha=.23f),tween(motionMillis(130)),label="toggle-color")
    Box(Modifier.size(34.dp,20.dp).background(fill,CircleShape).padding(3.dp)) {
        Box(Modifier.size(14.dp).offset(x=shift).background(Color.White,CircleShape))
    }
}

/** Code-native illustrations stay crisp at Windows scaling factors. */
@Composable internal fun EmptyIllustration(icon:String,modifier:Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    Box(modifier,contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w=size.width;val h=size.height
            drawOval(colors.primary.copy(alpha=.05f),Offset(w*.12f,h*.76f),Size(w*.78f,h*.17f))
            rotate(-12f,pivot=Offset(w*.43f,h*.46f)) {
                drawRoundRect(colors.primary.copy(alpha=.08f),Offset(w*.16f,h*.13f),Size(w*.49f,h*.60f),CornerRadius(7.dp.toPx()))
            }
            drawRoundRect(colors.surfaceVariant,Offset(w*.35f,h*.17f),Size(w*.44f,h*.60f),CornerRadius(6.dp.toPx()))
            drawRoundRect(colors.primary.copy(alpha=.12f),Offset(w*.36f,h*.18f),Size(w*.43f,h*.12f),CornerRadius(5.dp.toPx()))
            drawCircle(Color(0xFF28BFA7).copy(alpha=.65f),3.dp.toPx(),Offset(w*.8f,h*.64f))
        }
        Glyph(icon,Modifier.size(22.dp).offset(x=7.dp,y=0.dp),colors.primary.copy(alpha=.75f))
    }
}

@Composable internal fun FileEmblem(name:String,modifier:Modifier=Modifier) {
    val glyph=fileGlyph(name)
    val color=when(glyph){"image"->Color(0xFF9471CD);"table"->Color(0xFF2CAA86);"code"->Color(0xFF5D80D8);"pdf"->Color(0xFFE47862);else->Color(0xFF4A7BEB)}
    Box(modifier.background(color.copy(alpha=.10f),RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center){Glyph(glyph,Modifier.size(20.dp),color)}
}
