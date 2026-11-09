package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class CoursePlaylistTest {
 @Test fun directionsStopAtEdgesUnlessAllCoursesWasChosen(){
  val ids=listOf("a","b","c")
  assertEquals(listOf("c"),courseNavigationOrder(ids,"b",1,false))
  assertEquals(listOf("a"),courseNavigationOrder(ids,"b",-1,false))
  assertTrue(courseNavigationOrder(ids,"a",-1,false).isEmpty())
  assertTrue(courseNavigationOrder(ids,"c",1,false).isEmpty())
  assertEquals(listOf("a","b","c"),courseNavigationOrder(ids,"c",1,true))
  assertEquals(listOf("c","b","a"),courseNavigationOrder(ids,"a",-1,true))
 }
 @Test fun failuresHaveABoundedTraversalIncludingTheOnlyCourse(){
  assertEquals(listOf("a"),courseNavigationOrder(listOf("a"),"a",1,true))
  val order=courseNavigationOrder(listOf("a","b","c"),"b",1,true)
  assertEquals(order.size,order.distinct().size)
  assertEquals(listOf("b"),order.filter {it !in setOf("a","c")})
  assertTrue(courseNavigationOrder(emptyList(),"missing",1,true).isEmpty())
  assertTrue(courseNavigationOrder(listOf("a"),"removed",1,true).isEmpty())
 }
 @Test fun copiedOrderIsStableWhenTheCatalogChanges(){
  val catalog=mutableListOf("a","b","c");val snapshot=catalog.toList()
  catalog.add(0,"new");catalog.reverse()
  assertEquals(listOf("b","c","a"),courseNavigationOrder(snapshot,"a",1,true))
 }
}
