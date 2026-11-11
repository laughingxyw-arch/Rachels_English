package com.rachelsenglish.practice

import android.content.Intent

/** Browser fallback intents carry the ticket as an extra, with a valid URL but no fragment. */
internal fun passkeySetupTicket(intent: Intent,path: String="/passkey-setup/"): String? {
    val uri=intent.data
    if(uri!=null&&(uri.scheme!="https"||uri.host!="rachels-english.pages.dev"||uri.path!=path))return null
    return sequenceOf(uri?.fragment,intent.getStringExtra("enrollment"))
        .filterNotNull().firstOrNull {it.matches(Regex("[a-f0-9]{64}"))}
}
