package com.rachelsenglish.practice

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

class PasskeySetupInstrumentedTest {
 private val first="1".repeat(64)
 private val second="2".repeat(64)
 private fun fallback(ticket: String,path: String="passkey-setup")=Intent.parseUri("intent://rachels-english.pages.dev/$path/#Intent;scheme=https;package=com.rachelsenglish.practice;S.enrollment=$ticket;end",Intent.URI_INTENT_SCHEME)
 @Test fun exactBrowserFallbackAndVerifiedAppLinkResolveTheSameTicket(){
  val fallback=fallback(first)
  assertNull(fallback.data!!.fragment)
  assertEquals(first,passkeySetupTicket(fallback))
  assertEquals(first,passkeySetupTicket(Intent(Intent.ACTION_VIEW,Uri.parse("https://rachels-english.pages.dev/passkey-setup/#$first"))))
  assertEquals(first,passkeySetupTicket(fallback(first,"passkey-probe"),"/passkey-probe/"))
  assertNull(passkeySetupTicket(fallback(first,"passkey-probe")))
  assertNull(passkeySetupTicket(Intent(Intent.ACTION_VIEW,Uri.parse("https://invalid.example/passkey-setup/")).putExtra("enrollment",first)))
  assertNull(passkeySetupTicket(fallback("invalid")))
 }
 @Test fun coldLaunchAndWarmLinkBothStartBindingAndBusyWorkDoesNotDropTheSecondLink(){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  lateinit var model: PracticeModel
  lateinit var original: suspend (android.app.Activity,String?)->StudySession
  val tickets=CopyOnWriteArrayList<String>();val jobs=CopyOnWriteArrayList<Job>()
  val cold=CountDownLatch(1);val warm=CountDownLatch(1)
  instrumentation.runOnMainSync {
   model=(context.applicationContext as PracticeApplication).model;original=model.passkeyOperation
   model.passkeyOperation={_,ticket->
    jobs.add(currentCoroutineContext().job)
    if(ticket!=null){tickets.add(ticket);if(ticket==first)cold.countDown();if(ticket==second)warm.countDown()}
    awaitCancellation()
   }
  }
  var scenario: ActivityScenario<MainActivity>?=null
  try {
   val launched=ActivityScenario.launch<MainActivity>(fallback(first).setClass(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   scenario=launched
   assertTrue("Cold browser link must reach binding",cold.await(20,TimeUnit.SECONDS))
   launched.onActivity {activity->activity.startActivity(fallback(second).setClass(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))}
   assertTrue("Warm link must replace pending work instead of disappearing",warm.await(20,TimeUnit.SECONDS))
   assertEquals(listOf(first,second),tickets.toList())
  }finally {
   runBlocking {jobs.forEach {it.cancelAndJoin()}}
   instrumentation.runOnMainSync {model.passkeyOperation=original}
   scenario?.close()
  }
 }
}
