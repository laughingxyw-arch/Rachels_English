package com.rachelsenglish.practice

/** Match the visible sheet's travel; no second animation or scale completion threshold. */
fun sheetBackgroundScale(offset: Float,viewportHeight: Float,sheetHeight: Float,reducedMotion: Boolean): Float {
    if(reducedMotion||!offset.isFinite()||!viewportHeight.isFinite()||!sheetHeight.isFinite()||viewportHeight<=0f||sheetHeight<=0f)return 1f
    val reveal=((viewportHeight-offset)/sheetHeight).coerceIn(0f,1f)
    return 1f-.012f*reveal
}
