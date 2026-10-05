@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.rachelsenglish.practice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun CloudStudySetting(model: PracticeModel) {
    var expanded by remember {mutableStateOf(false)}
    var restoreExpanded by remember {mutableStateOf(false)}
    var code by remember {mutableStateOf("")}
    val palette=LocalPracticePalette.current
    val context=LocalContext.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button){expanded=!expanded},verticalAlignment=Alignment.CenterVertically){
            Text("云端同步",fontSize=15.sp,modifier=Modifier.weight(1f));Text(model.studySyncStatus+" ›",fontSize=13.sp,color=palette.muted)
        }
        AnimatedVisibility(expanded,enter=if(LocalReduced.current)EnterTransition.None else expandVertically(spring(1f,600f))+fadeIn(tween(120)),exit=if(LocalReduced.current)ExitTransition.None else shrinkVertically(tween(160))+fadeOut(tween(100))) {
            Column(Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                Text("恢复码请保存在手机以外，重装后可找回已同步记录。",fontSize=12.sp,color=palette.muted)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    TextButton(onClick={
                        val clip=ClipData.newPlainText("原声练习恢复码",model.recoveryCode)
                        if(android.os.Build.VERSION.SDK_INT>=33)clip.description.extras=android.os.PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip);model.notify("恢复码已复制，请妥善保存")
                    }){Text("复制恢复码")}
                    TextButton(onClick={model.syncStudy(true)},enabled=!model.studySyncing){Text("立即同步")}
                    TextButton(onClick={restoreExpanded=!restoreExpanded},enabled=!model.studySyncing){Text("恢复")}
                }
                AnimatedVisibility(restoreExpanded,enter=if(LocalReduced.current)EnterTransition.None else expandVertically(spring(1f,600f)),exit=if(LocalReduced.current)ExitTransition.None else shrinkVertically(tween(160))){
                    Column {
                        OutlinedTextField(code,{code=it},label={Text("恢复码")},singleLine=true,visualTransformation=PasswordVisualTransformation(),enabled=!model.studySyncing,modifier=Modifier.fillMaxWidth())
                        TextButton(onClick={model.syncStudy(true,code);code=""},enabled=code.isNotBlank()&&!model.studySyncing,modifier=Modifier.align(Alignment.End)){Text("恢复记录")}
                    }
                }
            }
        }
    }
}
