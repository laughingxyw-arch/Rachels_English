package com.rachelsenglish.practice
import org.junit.Assert.assertEquals
import org.junit.Test
class CourseSourceTest {
    @Test fun oldCatalogKeepsYouTubeSource() {
        assertEquals("https://www.youtube.com/watch?v=4dXbgvm4_7g",courseSourceUrl("4dXbgvm4_7g",null))
    }
    @Test fun nonYouTubeCourseKeepsItsActualSource() {
        val url="https://www.bilibili.com/video/BV1PeWczpEUC/?t=429"
        assertEquals(url,courseSourceUrl("HIMYM010102",url))
    }
    @Test fun unsafeOrMissingSourcesUseFallback() {
        for(url in listOf("","javascript:alert(1)","file:///tmp/media","https://user:secret@example.com/")) {
            assertEquals("https://www.youtube.com/watch?v=HIMYM010102",courseSourceUrl("HIMYM010102",url))
        }
    }
}
