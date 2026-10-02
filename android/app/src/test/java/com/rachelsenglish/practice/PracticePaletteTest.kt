package com.rachelsenglish.practice
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
class PracticePaletteTest {
    private fun ratio(a: Color,b: Color): Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
    @Test fun bothThemesKeepBodyAndSelectedTextReadable(){
        for(dark in listOf(false,true))for(high in listOf(false,true)){
            val p=practicePalette(dark,high)
            for(bg in listOf(p.background,p.surface,p.selected))for(fg in listOf(p.ink,p.muted,p.accent)){
                assertTrue("$dark/$high text contrast ${ratio(fg,bg)}",ratio(fg,bg)>=4.5f)
            }
        }
    }
    @Test fun highContrastStrengthensTextWithoutChangingLayoutColors(){
        for(dark in listOf(false,true)){
            val normal=practicePalette(dark);val high=practicePalette(dark,true)
            assertEquals(normal.background,high.background);assertEquals(normal.surface,high.surface)
            assertTrue(ratio(high.ink,high.surface)>=ratio(normal.ink,normal.surface))
            assertTrue(ratio(high.muted,high.surface)>ratio(normal.muted,normal.surface))
        }
    }
}
