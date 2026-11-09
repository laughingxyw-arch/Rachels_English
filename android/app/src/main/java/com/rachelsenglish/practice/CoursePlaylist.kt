package com.rachelsenglish.practice

/** A bounded traversal: each course is visited at most once, even after wrapping. */
fun courseNavigationOrder(ids: List<String>,current: String,direction: Int,wrap: Boolean): List<String> {
    require(direction==1||direction==-1)
    val at=ids.indexOf(current)
    if(at<0||ids.isEmpty())return emptyList()
    val count=if(wrap)ids.size else if(direction>0)ids.lastIndex-at else at
    return (1..count).map {step->ids[Math.floorMod(at+direction*step,ids.size)]}
}
