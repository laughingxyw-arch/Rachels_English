package com.rachelsenglish.practice

import androidx.credentials.exceptions.NoCredentialException

/** Restore first; a one-use private setup capability may enroll only an unlinked installation. */
internal suspend fun recoverOrEnrollPasskey(
    linked: Boolean,
    bootstrapTicket: String,
    restore: suspend () -> StudySession,
    register: suspend (String?) -> StudySession
): StudySession = try {
    restore()
} catch(error: Exception) {
    if(linked || !(error is NoCredentialException || error is PasskeyUnavailable && error.status==404))throw error
    register(bootstrapTicket.takeIf {it.matches(Regex("[a-f0-9]{64}"))})
}
