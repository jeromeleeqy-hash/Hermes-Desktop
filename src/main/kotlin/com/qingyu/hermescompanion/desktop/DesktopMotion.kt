package com.qingyu.hermescompanion.desktop

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

val LocalReduceMotion=staticCompositionLocalOf {false}
@Composable internal fun motionMillis(value:Int)=if(LocalReduceMotion.current)0 else value

/** A single content tree: transitions never mount duplicate editors or network effects. */
internal fun Modifier.arrive(key:Any?=Unit):Modifier=composed {
    val reduced=LocalReduceMotion.current
    val progress=remember {Animatable(1f)}
    val distance=with(LocalDensity.current){3.dp.toPx()}
    LaunchedEffect(key,reduced) {
        if(reduced)progress.snapTo(1f) else {
            progress.snapTo(.72f)
            progress.animateTo(1f,tween(210,easing=CubicBezierEasing(.16f,1f,.3f,1f)))
        }
    }
    graphicsLayer {alpha=progress.value;translationY=(1f-progress.value)*distance}
}
