package com.rachelsenglish.practice

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*

class StudyStoreInstrumentedTest {
 @Test fun writesAreMonotonicAndCacheBelongsToItsIdentity(){
  val target=InstrumentationRegistry.getInstrumentation().targetContext
  val isolated=object: android.content.ContextWrapper(target){
   override fun getDatabasePath(name: String)=super.getDatabasePath("sync-test-"+name)
   override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,errorHandler: android.database.DatabaseErrorHandler?)=super.openOrCreateDatabase("sync-test-"+name,mode,factory,errorHandler)
  }
  target.deleteDatabase("sync-test-study-v1.db")
  val store=StudyStore(isolated)
  try {
   val row=StudyDay("2026-10-05","hLgMIwFeE88",65000,1000)
   store.write(listOf(row));store.write(listOf(row.copy(audioMs=60000,shadowMs=0)))
   assertEquals(row,store.read().single())
   val cached=listOf(StudyContribution("old-device",row))
   store.cacheRemote("first",cached);assertEquals(cached,store.readRemote("first"))
   assertTrue(store.readRemote("other").isEmpty())
   store.cacheRemote("other",emptyList());assertTrue(store.readRemote("first").isEmpty())
  }finally{store.close();target.deleteDatabase("sync-test-study-v1.db")}
 }
}
