package com.rachelsenglish.practice

fun phraseReadProgress(start: Double,end: Double,source: Double): Float =
    if(!source.isFinite()||!start.isFinite()||!end.isFinite()||end<=start)0f else ((source-start)/(end-start)).toFloat().coerceIn(0f,1f)
