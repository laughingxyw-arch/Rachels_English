package com.rachelsenglish.practice
import org.junit.Assert.*
import org.junit.Test
class MaterialStyleTest {
 @Test fun readableSurfacesOverrideDecorativeMaterial(){
  val ordinary=VisualEnvironment(api=35)
  assertTrue(materialStyle(ordinary,false,false).frosted)
  assertFalse(materialStyle(ordinary,true,false).frosted)
  assertFalse(materialStyle(ordinary,false,true).frosted)
  assertTrue(materialStyle(ordinary.copy(highContrast=true),false,false).highContrast)
  assertFalse(materialStyle(ordinary.copy(highContrast=true),false,false).frosted)
 }
 @Test fun unsupportedAndPowerSavingDevicesUseSolidSurfaces(){
  listOf(VisualEnvironment(api=26),VisualEnvironment(api=30),VisualEnvironment(api=35,powerSaver=true),VisualEnvironment(api=35,lowRam=true)).forEach {assertFalse(materialStyle(it,false,false).frosted)}
  assertTrue(materialStyle(VisualEnvironment(api=31,reducedMotion=true),false,false).reducedMotion)
 }
 @Test fun gestureVelocityFollowsDirectionAndDropsStaleSamples(){
  val gesture=GestureVelocity();gesture.add(.1f,100);gesture.add(.2f,120)
  assertEquals(5f,gesture.velocity,.001f)
  gesture.add(.15f,140);assertEquals(-2.5f,gesture.velocity,.001f)
  gesture.add(.4f,500);assertEquals(0f,gesture.velocity,0f)
 }
}
