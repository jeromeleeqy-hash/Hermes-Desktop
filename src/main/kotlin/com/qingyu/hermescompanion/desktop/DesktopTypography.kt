package com.qingyu.hermescompanion.desktop

import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

// Bundle the licensed CJK font so Chinese labels do not depend on host font packages.
private val hermesFont = FontFamily(Font("fonts/NotoSansCJKsc-Regular.otf"))

fun desktopColors(dark:Boolean=false,accent:Color=Accent):ColorScheme = if(dark) {
    darkColorScheme(
        primary=lerp(accent,Color.White,.4f), onPrimary=Color(0xFF142043), background=Color(0xFF181C25),
        surface=Color(0xFF222630), onSurface=Color(0xFFE7EAF0), onBackground=Color(0xFFE7EAF0),
        secondaryContainer=accent.copy(alpha=.2f), onSecondaryContainer=Color(0xFFE7EAF0),
        outline=Color(0xFF3D4452), outlineVariant=Color(0xFF303744),
        surfaceVariant=Color(0xFF2B303D),onSurfaceVariant=Color(0xFFA6AEBD),
    )
} else {
    lightColorScheme(
        primary=accent, onPrimary=Color.White, background=Color(0xFFF1F4F9), surface=Color.White,
        onSurface=Ink, onBackground=Ink, secondary=Muted, outline=Hairline, outlineVariant=Color(0xFFEBEEF3),
        secondaryContainer=lerp(Color.White,accent,.14f), onSecondaryContainer=accent,
        surfaceVariant=Color(0xFFF3F5F8), onSurfaceVariant=Muted,
    )
}

fun desktopTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = hermesFont),
        displayMedium = base.displayMedium.copy(fontFamily = hermesFont),
        displaySmall = base.displaySmall.copy(fontFamily = hermesFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = hermesFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = hermesFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = hermesFont),
        titleLarge = base.titleLarge.copy(fontFamily = hermesFont),
        titleMedium = base.titleMedium.copy(fontFamily = hermesFont),
        titleSmall = base.titleSmall.copy(fontFamily = hermesFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = hermesFont, fontSize = 15.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = hermesFont, fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontFamily = hermesFont),
        labelLarge = base.labelLarge.copy(fontFamily = hermesFont, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = hermesFont),
        labelSmall = base.labelSmall.copy(fontFamily = hermesFont),
    )
}
