@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.rachelsenglish.practice

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun CloudStudySetting(model: PracticeModel) {
    var expanded by remember {mutableStateOf(false)}
    val palette=LocalPracticePalette.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button){expanded=!expanded},verticalAlignment=Alignment.CenterVertically){
            Text("云端同步",fontSize=15.sp,modifier=Modifier.weight(1f));Text(model.studySyncStatus+" ›",fontSize=13.sp,color=palette.muted)
        }
        AnimatedVisibility(expanded,enter=if(LocalReduced.current)EnterTransition.None else expandVertically(spring(1f,600f))+fadeIn(tween(120)),exit=if(LocalReduced.current)ExitTransition.None else shrinkVertically(tween(160))+fadeOut(tween(100))) {
            Column(Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                Text("自动同步学习记录",fontSize=12.sp,color=palette.muted)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    TextButton(onClick={model.syncStudy(true)},enabled=!model.studySyncing){Text("立即同步")}
                }
            }
        }
    }
}
