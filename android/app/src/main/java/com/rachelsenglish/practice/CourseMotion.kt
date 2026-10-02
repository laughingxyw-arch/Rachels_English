package com.rachelsenglish.practice

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Finish within a quarter physical pixel, rather than 1% of the whole page. */
fun courseProgressSpring(viewportExtent: Float): SpringSpec<Float> {
    val extent=if(viewportExtent.isFinite()&&viewportExtent>0f)viewportExtent else 4096f
    return spring(dampingRatio=1f,stiffness=500f,visibilityThreshold=.25f/extent)
}
