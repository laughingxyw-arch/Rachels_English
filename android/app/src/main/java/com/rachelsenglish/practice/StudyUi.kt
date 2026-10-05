package com.rachelsenglish.practice

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val StudyWindowKey=SemanticsPropertyKey<String>("StudyWindow")
val StudySelectedDayKey=SemanticsPropertyKey<String>("StudySelectedDay")

/** A snapshot isolates the calendar from live audio ticks. Pager offsets use lazy placement. */
@Composable internal fun UtilityPage(model: PracticeModel,back: ()->Unit,preferences: ()->Unit,open: (Course)->Unit) {
    val palette=LocalPracticePalette.current
    val rows=remember(model){model.studySnapshot().filter {it.effectiveMs>0}}
    Column(Modifier.fillMaxSize().background(palette.background).pointerInput(Unit){detectTapGestures(onTap={})}.testTag("utility-records")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            GlyphButton("返回课程","back",back)
            Text("学习记录",fontSize=19.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
            GlyphButton("学习设置","more",preferences)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=28.dp).padding(bottom=28.dp)) {
            StudyCalendar(rows,model.courses,open)
        }
    }
}
@Composable private fun StudyCalendar(rows: List<StudyDay>,courses: List<Course>,open: (Course)->Unit) {
    val palette=LocalPracticePalette.current
    val reduced=LocalReduced.current
    val today=remember {LocalDate.now()}
    val currentMonth=remember(today){YearMonth.from(today)}
    val currentWindow=remember(today){(ChronoUnit.MONTHS.between(YearMonth.of(1970,1),currentMonth)/3).toInt()}
    var selectedText by rememberSaveable {mutableStateOf(today.toString())}
    val selected=LocalDate.parse(selectedText)
    val months=rememberPagerState(initialPage=currentWindow){currentWindow+1}
    val days=rememberPagerState(initialPage=selected.toEpochDay().toInt()){today.toEpochDay().toInt()+1}
    val scope=rememberCoroutineScope()
    var dayGesturePending by remember {mutableStateOf(false)}
    val totals=remember(rows){rows.groupBy {LocalDate.parse(it.day)}.mapValues {(_,r)->r.sumOf {it.effectiveMs}}}
    val courseMap=remember(courses){courses.associateBy {it.id}}
    suspend fun selectDay(date: LocalDate,showWindow: Boolean=true){
        selectedText=date.toString()
        if(showWindow){
            val distance=ChronoUnit.MONTHS.between(YearMonth.from(date),currentMonth).toInt().coerceAtLeast(0)
            val target=(currentWindow-distance/3).coerceAtLeast(0)
            if(target!=months.currentPage)scope.launch {if(reduced)months.scrollToPage(target) else months.animateScrollToPage(target)}
        }
        if(reduced)days.scrollToPage(date.toEpochDay().toInt()) else days.animateScrollToPage(date.toEpochDay().toInt())
    }
    LaunchedEffect(days){days.interactionSource.interactions.collect {if(it is DragInteraction.Start)dayGesturePending=true}}
    LaunchedEffect(months){snapshotFlow {months.settledPage to months.isScrollInProgress}.collect {(page,moving)->
        if(!moving){
            val month=currentMonth.minusMonths((currentWindow-page)*3L)
            val end=if(page==currentWindow)today else month.atEndOfMonth()
            val first=month.minusMonths(2).atDay(1)
            val date=LocalDate.parse(selectedText)
            if(date<first||date>end)scope.launch {selectDay(end,showWindow=false)}
        }
    }}
    LaunchedEffect(days){snapshotFlow {days.settledPage to days.isScrollInProgress}.collect {(page,moving)->
        if(!moving){
            val date=LocalDate.ofEpochDay(page.toLong());selectedText=date.toString()
            if(dayGesturePending){
                dayGesturePending=false
                val distance=ChronoUnit.MONTHS.between(YearMonth.from(date),currentMonth).toInt().coerceAtLeast(0)
                val target=(currentWindow-distance/3).coerceAtLeast(0)
                if(target!=months.currentPage)scope.launch {if(reduced)months.scrollToPage(target) else months.animateScrollToPage(target)}
            }
        }
    }}
    val activeMonth=currentMonth.minusMonths((currentWindow-months.settledPage)*3L)
    val total=remember(rows,activeMonth){rows.filter {YearMonth.from(LocalDate.parse(it.day))==activeMonth}.sumOf {it.effectiveMs}}
    Column(Modifier.fillMaxWidth().padding(top=16.dp)) {
        Text(if(activeMonth==currentMonth)"本月练习" else "${activeMonth.year} 年 ${activeMonth.monthValue} 月练习",fontSize=13.sp,color=palette.muted)
        Row(verticalAlignment=Alignment.Bottom) {
            Text("${total/60_000}",fontSize=46.sp,fontWeight=FontWeight.Medium,modifier=Modifier.testTag("study-month-minutes"))
            Text(" 分钟",fontSize=13.sp,color=palette.muted,modifier=Modifier.padding(bottom=10.dp))
        }
    }
    val browseWindow: (Int)->Boolean={delta->
        val target=(months.settledPage+delta).coerceIn(0,currentWindow)
        scope.launch {if(reduced)months.scrollToPage(target) else months.animateScrollToPage(target)};true
    }
    HorizontalPager(months,flingBehavior=PagerDefaults.flingBehavior(months,snapAnimationSpec=if(reduced)snap() else spring(1f,600f)),beyondViewportPageCount=1,modifier=Modifier.fillMaxWidth().padding(top=24.dp).testTag("study-window")
        .semantics {this[StudyWindowKey]=activeMonth.toString();customActions=listOf(CustomAccessibilityAction("更早三个月"){browseWindow(-1)},CustomAccessibilityAction("更近三个月"){browseWindow(1)})}) {page->
        val month=remember(page){currentMonth.minusMonths((currentWindow-page)*3L)}
        val end=if(page==currentWindow)today else month.atEndOfMonth()
        val dates=remember(end){studyDates(end).filter {it.toEpochDay()>=0}}
        val first=remember(dates){dates.first().minusDays((dates.first().dayOfWeek.value-1).toLong())}
        val columns=remember(first,end){(ChronoUnit.DAYS.between(first,end)/7+1).toInt()}
        val colors=remember(palette){listOf(palette.track,palette.accent.copy(alpha=.24f),palette.accent.copy(alpha=.46f),palette.accent.copy(alpha=.72f),palette.accent)}
        Column {
            Text("${dates.first().year}.${dates.first().monthValue}—${end.year}.${end.monthValue}",fontSize=13.sp,color=palette.muted,modifier=Modifier.padding(bottom=18.dp))
            Canvas(Modifier.fillMaxWidth().height(154.dp).testTag(if(page==months.currentPage)"study-heatmap" else "study-adjacent-heatmap")
                .semantics {contentDescription="学习日历";stateDescription="$selected，${(totals[selected]?:0)/60_000} 分钟"}
                .pointerInput(first,end,columns){detectTapGestures {point->
                    val week=(point.x/(size.width.toFloat()/columns)).toInt().coerceIn(0,columns-1)
                    val weekday=(point.y/(size.height.toFloat()/7)).toInt().coerceIn(0,6)
                    val date=first.plusDays((week*7+weekday).toLong())
                    if(date in dates)scope.launch {selectDay(date)}
                }}) {
                val width=size.width/columns;val height=size.height/7
                val side=minOf(width,height)-3.dp.toPx();val radius=CornerRadius(3.dp.toPx())
                dates.forEach {date->
                    val offset=ChronoUnit.DAYS.between(first,date).toInt()
                    val x=(offset/7)*width+(width-side)/2;val y=(offset%7)*height+(height-side)/2
                    drawRoundRect(colors[studyHeatLevel(totals[date]?:0)],Offset(x,y),Size(side,side),radius)
                    if(date==selected)drawRoundRect(palette.ink,Offset(x-1.dp.toPx(),y-1.dp.toPx()),Size(side+2.dp.toPx(),side+2.dp.toPx()),radius,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
        }
    }
    val browseDay: (Int)->Boolean={delta->scope.launch {selectDay(selected.plusDays(delta.toLong()).coerceIn(LocalDate.of(1970,1,1),today))};true}
    val density=LocalDensity.current
    val rowHeight=with(density){maxOf(56.dp,44.sp.toDp()+12.dp)}
    val headerHeight=with(density){maxOf(72.dp,42.sp.toDp()+30.dp)}
    val detailHeight=remember(rows,rowHeight,headerHeight){maxOf(176.dp,headerHeight+rowHeight*(rows.groupBy {it.day}.values.maxOfOrNull {it.size}?:0))}
    HorizontalPager(days,flingBehavior=PagerDefaults.flingBehavior(days,snapAnimationSpec=if(reduced)snap() else spring(1f,600f)),beyondViewportPageCount=1,verticalAlignment=Alignment.Top,modifier=Modifier.fillMaxWidth().height(detailHeight+32.dp).padding(top=32.dp).testTag("study-days")
        .semantics {this[StudySelectedDayKey]=selectedText;customActions=listOf(CustomAccessibilityAction("前一天"){browseDay(-1)},CustomAccessibilityAction("后一天"){browseDay(1)})}) {page->
        val date=remember(page){LocalDate.ofEpochDay(page.toLong())}
        val dayRows=remember(rows,date){rows.filter {it.day==date.toString()}}
        Column(Modifier.fillMaxWidth()) {
            Text(date.format(DateTimeFormatter.ofPattern("M 月 d 日")),fontSize=16.sp,fontWeight=FontWeight.Medium)
            Text(if(dayRows.isEmpty())"暂无练习" else "${dayRows.sumOf {it.effectiveMs}/60_000} 分钟",fontSize=13.sp,color=palette.muted,modifier=Modifier.padding(top=6.dp,bottom=12.dp).testTag(if(page==days.currentPage)"study-day-summary" else "study-adjacent-day-summary"))
            dayRows.forEach {row->
                val course=courseMap[row.course]
                Row(Modifier.fillMaxWidth().heightIn(min=rowHeight).then(if(course!=null)Modifier.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,onClick={open(course)}) else Modifier),verticalAlignment=Alignment.CenterVertically) {
                    Text(course?.title?:"已移除的课程",fontSize=15.sp,lineHeight=22.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                    if(course!=null)Text("›",color=palette.muted)
                }
            }
        }
    }
}
