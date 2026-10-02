package com.rachelsenglish.practice

import org.junit.Assert.*
import org.junit.Test

class SheetDepthTest {
    @Test fun backgroundFollowsSheetTravelInBothDirections(){
        assertEquals(1f,sheetBackgroundScale(1000f,1000f,400f,false),.00001f)
        assertEquals(.994f,sheetBackgroundScale(800f,1000f,400f,false),.00001f)
        assertEquals(.988f,sheetBackgroundScale(600f,1000f,400f,false),.00001f)
        val opening=(1000 downTo 600 step 10).map {sheetBackgroundScale(it.toFloat(),1000f,400f,false)}
        assertTrue(opening.zipWithNext().all {(before,after)->after<before})
        val closing=(600..1000 step 10).map {sheetBackgroundScale(it.toFloat(),1000f,400f,false)}
        assertEquals(opening.reversed(),closing)
    }
    @Test fun overscrollAndUnmeasuredGeometryAreSafe(){
        assertEquals(.988f,sheetBackgroundScale(-20f,1000f,400f,false),0f)
        assertEquals(1f,sheetBackgroundScale(1200f,1000f,400f,false),0f)
        assertEquals(1f,sheetBackgroundScale(Float.NaN,1000f,400f,false),0f)
        assertEquals(1f,sheetBackgroundScale(800f,0f,400f,false),0f)
        assertEquals(1f,sheetBackgroundScale(800f,1000f,0f,false),0f)
    }
    @Test fun reducedMotionKeepsTheReadingSurfaceStationary(){
        for(offset in 0..1000 step 100)assertEquals(1f,sheetBackgroundScale(offset.toFloat(),1000f,400f,true),0f)
    }
}
