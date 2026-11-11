package com.rachelsenglish.practice

import android.app.Activity
import androidx.credentials.*
import androidx.credentials.exceptions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

data class StudySession(val token: String,val account: String,val expires: Long)
class PasskeyUnavailable(val status: Int,val reason: String?=null): Exception("Passkey service unavailable")

/** Only the system credential provider sees biometrics. No fingerprints or private keys enter the app. */
class PasskeyClient(private val base: String) {
    private suspend fun post(path: String,body: JSONObject,token: String?=null): JSONObject=withContext(Dispatchers.IO) {
        val c=URI(base.trimEnd('/')+"/api/passkey/"+path).toURL().openConnection() as HttpURLConnection
        try {
            c.connectTimeout=15000;c.readTimeout=20000;c.instanceFollowRedirects=false
            c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
            token?.let {c.setRequestProperty("Authorization","Bearer $it")}
            c.outputStream.use {it.write(body.toString().toByteArray(Charsets.UTF_8))}
            if(c.responseCode!=200){
                val reason=runCatching {JSONObject(c.errorStream?.bufferedReader()?.use {it.readText()}?:"{}").optString("error")}.getOrNull()
                throw PasskeyUnavailable(c.responseCode,reason)
            }
            JSONObject(c.inputStream.bufferedReader().use {it.readText()})
        } finally {c.disconnect()}
    }
    private fun session(body: JSONObject): StudySession {
        val token=body.getString("token");val account=body.getString("account")
        check(token.matches(Regex("session\\.[a-f0-9]{64}"))&&account.matches(Regex("[a-f0-9]{64}")))
        return StudySession(token,account,body.getLong("expires"))
    }
    suspend fun register(activity: Activity,ticket: String?=null,token: String?=null,probe: Boolean=false): StudySession {
        val body=JSONObject().put("mode",if(probe)"probe" else "owner");ticket?.let {body.put("ticket",it)}
        val challenge=post("register/options",body,token)
        val response=CredentialManager.create(activity).createCredential(activity,CreatePublicKeyCredentialRequest(challenge.getJSONObject("options").toString())) as CreatePublicKeyCredentialResponse
        body.put("challengeId",challenge.getString("challengeId")).put("credential",JSONObject(response.registrationResponseJson))
        return session(post("register/finish",body,token))
    }
    suspend fun restore(activity: Activity,probe: Boolean=false): StudySession {
        val challenge=post("authenticate/options",JSONObject().put("mode",if(probe)"probe" else "owner"))
        val request=GetCredentialRequest(listOf(GetPublicKeyCredentialOption(challenge.getJSONObject("options").toString())))
        val credential=CredentialManager.create(activity).getCredential(activity,request).credential as PublicKeyCredential
        return session(post("authenticate/finish",JSONObject().put("challengeId",challenge.getString("challengeId")).put("credential",JSONObject(credential.authenticationResponseJson))))
    }
}

internal fun passkeyMessage(error: Exception): String=when(error) {
    is GetCredentialCancellationException,is CreateCredentialCancellationException->"已取消验证"
    is NoCredentialException->"未找到通行密钥，请确认系统已开启凭据服务"
    is GetCredentialProviderConfigurationException,is CreateCredentialProviderConfigurationException->"请先在系统设置中启用通行密钥服务"
    is PasskeyUnavailable->passkeyHttpMessage(error.status,error.reason)
    else->"暂未完成验证，可在云端同步中重试"
}

internal fun passkeyHttpMessage(status: Int,reason: String?): String=when(reason){
    "Setup link unavailable"->"绑定链接已使用或过期，请重新获取"
    "Setup required"->"尚未关联学习记录，请从首次绑定链接进入"
    "Wrong profile"->"请使用正式 App 对应的绑定链接"
    "Challenge expired"->"验证等待超时，请重新打开绑定链接"
    "Passkey verification failed"->"系统通行密钥校验未通过，请重试"
    "Unknown passkey"->"这枚通行密钥未关联学习记录"
    else->when(status){401->"身份验证未完成，请重试";404->"尚未绑定通行密钥";429->"请稍后重试";else->"暂时无法连接，请稍后重试"}
}
