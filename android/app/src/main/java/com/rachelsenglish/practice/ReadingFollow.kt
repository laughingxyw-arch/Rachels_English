package com.rachelsenglish.practice

/** All coordinates are relative to the list viewport, excluding its floating controls. */
internal fun readingFollowDelta(top: Float,height: Float,viewport: Float,focusTop: Float=0f,focusBottom: Float=height): Float {
    if(viewport<=0f||height<=0f)return 0f
    val upper=viewport*.2f;val lower=viewport*.8f
    val start=if(height<=lower-upper)top else top+focusTop
    val end=if(height<=lower-upper)top+height else top+focusBottom
    return if(start<upper||end>lower)(start+end)/2f-viewport*.5f else 0f
}
internal data class ReadingFollowRequest(val selected: Int,val playing: Boolean,val task: Int,val dragging: Boolean,val focus: Pair<Int,Int>?)
