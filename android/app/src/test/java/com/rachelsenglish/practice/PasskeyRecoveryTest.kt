package com.rachelsenglish.practice

import androidx.credentials.exceptions.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PasskeyRecoveryTest {
 private val ticket="a".repeat(64)
 private val session=StudySession("session."+"b".repeat(64),"c".repeat(64),1L)
 @Test fun existingPasskeyRestoresWithoutSpendingPrivateSetup()=runBlocking {
  assertEquals(session,recoverOrEnrollPasskey(false,ticket,{session},{kotlin.error("Must not enroll")}))
 }
 @Test fun firstInstallationEnrollsNativelyWithPrivateCapability()=runBlocking {
  for(error in listOf(NoCredentialException(),PasskeyUnavailable(404))){
   var supplied: String?=null
   assertEquals(session,recoverOrEnrollPasskey(false,ticket,{throw error},{supplied=it;session}))
   assertEquals(ticket,supplied)
  }
 }
 @Test fun ordinaryBuildUsesAuthorizedLegacyEnrollmentWithoutPrivateCapability()=runBlocking {
  assertEquals(session,recoverOrEnrollPasskey(false,"",{throw PasskeyUnavailable(404)},{assertNull(it);session}))
 }
 @Test fun linkedCancellationNetworkAndVerificationFailuresNeverCreateNewKeys()=runBlocking {
  for((linked,error) in listOf(true to PasskeyUnavailable(404),false to GetCredentialCancellationException(),false to PasskeyUnavailable(503),false to PasskeyUnavailable(401))){
   try{recoverOrEnrollPasskey(linked,ticket,{throw error},{kotlin.error("Must not enroll")});fail("Expected original failure")}
   catch(actual: Exception){assertSame(error,actual)}
  }
 }
}
