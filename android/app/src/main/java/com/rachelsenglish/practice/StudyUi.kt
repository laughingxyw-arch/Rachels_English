package com.rachelsenglish.practice

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Records are a snapshot: audio ticks never recompose this page or its calendar. */
@Composable internal fun UtilityPage(model: PracticeModel,page: String,systemContrast: Boolean,back: ()->Unit,preferences: ()->Unit,open: (Course)->Unit) {
    val palette=LocalPracticePalette.current
    val rows=remember(model,page){model.studySnapshot().filter {it.effectiveMs>0}}
    Column(Modifier.fillMaxSize().background(palette.background).testTag("utility-$page")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            GlyphButton(if(page=="records")"返回课程" else if(model.opened!=null)"返回练习" else "返回学习记录","back",back)
            Text(if(page=="records")"学习记录" else "设置",fontSize=19.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
            if(page=="records")GlyphButton("学习设置","more",preferences)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=28.dp).padding(bottom=28.dp)) {
            if(page=="preferences") {
                Setting("中文翻译",model.translation,model::updateTranslation)
                Setting("发音提示",model.cues,model::updateCues)
                AppearanceSetting(model,systemContrast)
                Spacer(Modifier.height(32.dp))
                Text("有效学习",fontSize=15.sp,fontWeight=FontWeight.Medium)
                Text("同一天、同一课程累计听音达到 60 秒后，才计入学习记录。达标后计入当天该课的实际听音和跟读留白；静音、暂停、加载、跳转和普通复读间隔不计时。",fontSize=13.sp,lineHeight=21.sp,color=palette.muted,modifier=Modifier.padding(top=12.dp))
                Text("记录仅保存在本机，从此版本开始累计。",fontSize=13.sp,lineHeight=21.sp,color=palette.muted,modifier=Modifier.padding(top=12.dp))
            } else StudyCalendar(rows,model.courses,open)
        }
    }
}
@Composable private fun StudyCalendar(rows: List<StudyDay>,courses: List<Course>,open: (Course)->Unit) {
    val palette=LocalPracticePalette.current
    val today=remember {LocalDate.now()}
    var monthText by rememberSaveable {mutableStateOf(YearMonth.from(today).toString())}
    val month=remember(monthText){YearMonth.parse(monthText)}
    val end=if(month==YearMonth.from(today))today else month.atEndOfMonth()
    val dates=remember(end){studyDates(end)}
    val first=remember(dates){dates.first().minusDays((dates.first().dayOfWeek.value-1).toLong())}
    val columns=remember(first,end){((java.time.temporal.ChronoUnit.DAYS.between(first,end))/7+1).toInt()}
    val totals=remember(rows){rows.groupBy {LocalDate.parse(it.day)}.mapValues {(_,r)->r.sumOf {it.effectiveMs}}}
    val total=remember(rows,month){rows.filter {YearMonth.from(LocalDate.parse(it.day))==month}.sumOf {it.effectiveMs}}
    var selectedText by rememberSaveable {mutableStateOf(today.toString())}
    val selected=LocalDate.parse(selectedText)
    val dayRows=remember(rows,selected){rows.filter {it.day==selected.toString()}}
    val courseMap=remember(courses){courses.associateBy {it.id}}
    Row(Modifier.fillMaxWidth().padding(top=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if(month==YearMonth.from(today))"本月练习" else "${month.monthValue} 月练习",fontSize=13.sp,color=palette.muted)
            Row(verticalAlignment=Alignment.Bottom) {
                Text("${total/60_000}",fontSize=46.sp,fontWeight=FontWeight.Medium,modifier=Modifier.testTag("study-month-minutes"))
                Text(" 分钟",fontSize=13.sp,color=palette.muted,modifier=Modifier.padding(bottom=10.dp))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
        GlyphButton("更早三个月","previous",{monthText=month.minusMonths(3).toString();selectedText=month.minusMonths(3).atEndOfMonth().toString()})
        Text("${dates.first().year}.${dates.first().monthValue}—${end.year}.${end.monthValue}",fontSize=13.sp,color=palette.muted,modifier=Modifier.weight(1f))
        GlyphButton("更近三个月","next",{val next=minOf(month.plusMonths(3),YearMonth.from(today));monthText=next.toString();selectedText=(if(next==YearMonth.from(today))today else next.atEndOfMonth()).toString()},enabled=month<YearMonth.from(today))
    }
    val colors=remember(palette){listOf(palette.track,palette.accent.copy(alpha=.24f),palette.accent.copy(alpha=.46f),palette.accent.copy(alpha=.72f),palette.accent)}
    Canvas(Modifier.fillMaxWidth().height(154.dp).testTag("study-heatmap")
        .semantics {contentDescription="三个月有效学习日历，蓝色越深练习越多";stateDescription="$selected，${(totals[selected]?:0)/60_000} 分钟"}
        .pointerInput(first,end,columns){detectTapGestures {point->
            val cell=size.width.toFloat()/columns
            val week=(point.x/cell).toInt().coerceIn(0,columns-1)
            val weekday=(point.y/(size.height.toFloat()/7)).toInt().coerceIn(0,6)
            val date=first.plusDays((week*7+weekday).toLong())
            if(date in dates)selectedText=date.toString()
        }}) {
        val width=size.width/columns;val height=size.height/7
        val side=minOf(width,height)-3.dp.toPx();val radius=CornerRadius(3.dp.toPx())
        dates.forEach {date->
            val offset=java.time.temporal.ChronoUnit.DAYS.between(first,date).toInt()
            val x=(offset/7)*width+(width-side)/2;val y=(offset%7)*height+(height-side)/2
            drawRoundRect(colors[studyHeatLevel(totals[date]?:0)],Offset(x,y),Size(side,side),radius)
            if(date==selected)drawRoundRect(palette.ink,Offset(x-1.dp.toPx(),y-1.dp.toPx()),Size(side+2.dp.toPx(),side+2.dp.toPx()),radius,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
        Text("少",fontSize=11.sp,color=palette.muted)
        colors.forEach {color->Box(Modifier.padding(start=4.dp).size(10.dp).background(color,RoundedCornerShape(2.dp)))}
        Text("  多",fontSize=11.sp,color=palette.muted)
    }
    Row(Modifier.fillMaxWidth().padding(top=28.dp),verticalAlignment=Alignment.CenterVertically) {
        GlyphButton("前一天","previous",{val date=selected.minusDays(1);selectedText=date.toString();if(date<dates.first())monthText=YearMonth.from(date).toString()})
        Column(Modifier.weight(1f)) {
            Text(selected.format(DateTimeFormatter.ofPattern("M 月 d 日")),fontSize=16.sp,fontWeight=FontWeight.Medium)
            Text(if(dayRows.isEmpty())"暂无有效学习" else "${dayRows.sumOf {it.effectiveMs}/60_000} 分钟",fontSize=13.sp,color=palette.muted,modifier=Modifier.padding(top=4.dp).testTag("study-day-summary"))
        }
        GlyphButton("后一天","next",{val date=selected.plusDays(1);selectedText=date.toString();if(date>end)monthText=YearMonth.from(date).toString()},enabled=selected<today)
    }
    dayRows.forEach {row->
        val course=courseMap[row.course]
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).then(if(course!=null)Modifier.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,onClick={open(course)}) else Modifier),verticalAlignment=Alignment.CenterVertically) {
            Text(course?.title?:"已移除的课程",fontSize=15.sp,modifier=Modifier.weight(1f))
            if(course!=null)Text("›",color=palette.muted)
        }
    }
    if(rows.isEmpty())Text("同一天、同一课程累计听音 60 秒后，这里会留下记录。",fontSize=13.sp,lineHeight=21.sp,color=palette.muted,modifier=Modifier.padding(top=24.dp))
}
