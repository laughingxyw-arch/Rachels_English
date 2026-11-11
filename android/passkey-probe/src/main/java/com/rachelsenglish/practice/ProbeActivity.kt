package com.rachelsenglish.practice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Separate package, fake record and passkey scope. Never loads or changes the real study profile. */
class ProbeActivity: ComponentActivity() {
    private val client=PasskeyClient("https://rachels-english.pages.dev/")
    private var status by mutableStateOf("打开专用绑定链接，或恢复已保存的验证密钥。")
    private var busy by mutableStateOf(false)
    private var ticket: String?=null
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge();setup(intent)
        setContent {
            MaterialTheme(colorScheme=if(isSystemInDarkTheme())darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        Spacer(Modifier.height(32.dp));Text("通行密钥验证",fontSize=26.sp)
                        Text(status)
                        Text("仅使用一条虚拟学习记录。绑定成功后，可以卸载这个验证应用再重装，点击恢复，检查系统密钥是否仍可使用。正式应用和真实记录不受影响。",style=MaterialTheme.typography.bodyMedium)
                        TextButton(onClick={verify()},enabled=!busy){Text(if(busy)"等待系统验证…" else if(ticket!=null)"绑定验证密钥" else "用通行密钥恢复")}
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent){super.onNewIntent(intent);setIntent(intent);setup(intent)}
    private fun setup(intent: Intent){
        val uri=intent.data
        val value=if(uri?.scheme=="https"&&uri.host=="rachels-english.pages.dev"&&uri.path=="/passkey-probe/")uri.fragment else intent.getStringExtra("enrollment")
        if(value?.matches(Regex("[a-f0-9]{64}"))==true){ticket=value;intent.data=null;intent.removeExtra("enrollment");window.decorView.post {verify()}}
    }
    private fun verify(){
        if(busy)return;busy=true
        lifecycleScope.launch {
            try {
                val session=ticket?.let {client.register(this@ProbeActivity,it)}?:client.restore(this@ProbeActivity,probe=true)
                ticket=null
                val seconds=withContext(Dispatchers.IO){
                    val c=URI("https://rachels-english.pages.dev/api/study").toURL().openConnection() as HttpURLConnection
                    try {
                        c.connectTimeout=15000;c.readTimeout=20000;c.instanceFollowRedirects=false
                        c.setRequestProperty("Authorization","Bearer ${session.token}");check(c.responseCode==200)
                        val rows=JSONObject(c.inputStream.bufferedReader().use {it.readText()}).getJSONArray("rows")
                        (0 until rows.length()).sumOf {rows.getJSONObject(it).getLong("audioMs")}/1000
                    }finally {c.disconnect()}
                }
                check(seconds==60L)
                status="验证成功 · 已找回 60 秒虚拟记录。\n\n若这是卸载重装后完成的恢复，说明本机凭据服务已通过这一步验证。"
            } catch(e: kotlinx.coroutines.CancellationException){throw e}
            catch(e: Exception){status=passkeyMessage(e)}
            finally {busy=false}
        }
    }
}
