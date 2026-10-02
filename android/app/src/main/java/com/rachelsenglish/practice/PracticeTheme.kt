package com.rachelsenglish.practice

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*

@Immutable
data class PracticePalette(val dark: Boolean,val background: Color,val surface: Color,val ink: Color,
    val muted: Color,val accent: Color,val selected: Color,val error: Color,val track: Color)
fun practicePalette(dark: Boolean,contrast: Boolean=false): PracticePalette = if(dark)
    PracticePalette(true,Color(0xff10141b),Color(0xff191f29),
        if(contrast)Color.White else Color(0xffe6eaf2),
        if(contrast)Color(0xffc1cad8) else Color(0xffa6afbe),
        if(contrast)Color(0xffb1d3ff) else Color(0xff88baff),
        Color(0xff20344a),Color(0xffffb4ac),Color(0xff343f50))
    else PracticePalette(false,Color(0xfff7f8fb),Color.White,
        if(contrast)Color(0xff101721) else Color(0xff25283b),
        if(contrast)Color(0xff414957) else Color(0xff686e82),
        Color(0xff1765c1),Color(0xffedf4fc),Color(0xffa94b44),Color(0xffe3e9f1))
val LocalPracticePalette=staticCompositionLocalOf {practicePalette(false)}
fun practiceColorScheme(p: PracticePalette): ColorScheme {
    val base=if(p.dark)darkColorScheme() else lightColorScheme()
    return base.copy(primary=p.accent,onPrimary=if(p.dark)Color(0xff102e50) else Color.White,
        primaryContainer=p.selected,onPrimaryContainer=p.ink,secondary=p.accent,onSecondary=if(p.dark)Color(0xff102e50) else Color.White,
        background=p.background,onBackground=p.ink,surface=p.surface,onSurface=p.ink,
        surfaceVariant=p.track,onSurfaceVariant=p.muted,surfaceContainerLow=p.surface,
        surfaceContainer=p.surface,surfaceContainerHigh=p.surface,surfaceTint=p.accent,
        outline=p.muted,error=p.error,onError=if(p.dark)Color(0xff481a17) else Color.White)
}
