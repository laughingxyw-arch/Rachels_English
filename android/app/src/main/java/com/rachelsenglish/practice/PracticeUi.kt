@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.rachelsenglish.practice

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

private val Ink=Color(0xff25283b)
private val Muted=Color(0xff6d7288)
private val Accent=Color(0xff1765c1)
private val Backdrop=Color(0xfff7f8fb)
private val Tint=Color(0xffedf4fc)
private val LocalReduced=staticCompositionLocalOf { false }
@Composable fun PracticeApp(model: PracticeModel) {
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(lifecycle){val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME)reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f };lifecycle.lifecycle.addObserver(observer);onDispose {lifecycle.lifecycle.removeObserver(observer)}}
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(Unit){snapshotFlow {model.message}.filterNotNull().collect {text->snackbar.showSnackbar(text);if(model.message==text)model.consumeMessage()}}
    val libraryState=rememberLazyGridState()
    var query by rememberSaveable {mutableStateOf("")}
    val backProgress=remember {Animatable(0f)}
    var backDirection by remember {mutableFloatStateOf(1f)}
    PredictiveBackHandler(enabled=model.opened!=null) { events ->
        try {events.collect {backDirection=if(it.swipeEdge==androidx.activity.BackEventCompat.EDGE_LEFT)1f else -1f;backProgress.snapTo(it.progress)};model.back();backProgress.snapTo(0f)}
        catch(_: CancellationException) {withContext(NonCancellable){if(reduced)backProgress.snapTo(0f) else backProgress.animateTo(0f,spring(.9f,600f))}}
    }
    CompositionLocalProvider(LocalReduced provides reduced) {
        MaterialTheme(colorScheme=lightColorScheme(primary=Accent,background=Backdrop,surface=Color.White,onSurface=Ink,onBackground=Ink)) {
            Box(Modifier.fillMaxSize().background(Backdrop).safeDrawingPadding()) {
                SharedTransitionLayout {
                    val shared=this
                    AnimatedContent(targetState=model.opened,contentKey={it?.course?.id?:"home"},
                        transitionSpec={
                            if(reduced)EnterTransition.None togetherWith ExitTransition.None
                            else if(targetState!=null) (fadeIn(tween(160))+slideInHorizontally(spring(.94f,500f)){it/10}) togetherWith (fadeOut(tween(140))+slideOutHorizontally(tween(220)){ -it/18 })
                            else (fadeIn(tween(160))+slideInHorizontally(spring(.94f,500f)){ -it/18 }) togetherWith (fadeOut(tween(140))+slideOutHorizontally(tween(220)){it/10})
                        },label="course-space") { open ->
                        val visibility=this
                        if(open==null) Library(model,shared,visibility,libraryState,query,{query=it})
                        else Box(Modifier.fillMaxSize().graphicsLayer {
                            val progress=if(reduced)0f else backProgress.value
                            translationX=size.width*.12f*progress*backDirection;scaleX=1f-.04f*progress;scaleY=scaleX
                            shape=RoundedCornerShape((progress*24).dp);clip=progress>0
                        }) { LessonScreen(model,open,shared,visibility) }
                    }
                }
                SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=88.dp))
            }
        }
    }
}
@Composable private fun SharedTransitionScope.coverModifier(c: Course,visibility: AnimatedVisibilityScope): Modifier {
    val reduced=LocalReduced.current
    return Modifier.sharedElement(rememberSharedContentState("cover-${c.id}"),visibility,
        boundsTransform={_,_->if(reduced)snap() else spring(dampingRatio=.92f,stiffness=420f)})
}
@Composable private fun SharedTransitionScope.titleModifier(c: Course,visibility: AnimatedVisibilityScope): Modifier {
    val reduced=LocalReduced.current
    return Modifier.sharedBounds(rememberSharedContentState("title-${c.id}"),visibility,
        boundsTransform={_,_->if(reduced)snap() else spring(dampingRatio=.92f,stiffness=420f)})
}
@Composable private fun Cover(c: Course,repo: CourseRepository,modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,c.id,c.version){value=repo.cover(c)}
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Tint)) {
        bitmap?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize().testTag("cover-ready-${c.id}"),contentScale=ContentScale.Crop)}
    }
}
@Composable private fun Library(model: PracticeModel,shared: SharedTransitionScope,visibility: AnimatedVisibilityScope,list: LazyGridState,query: String,changeQuery: (String)->Unit) {
    val courses=model.courses.filter { "${it.title} ${it.label}".contains(query,true) }
    LazyVerticalGrid(GridCells.Adaptive(360.dp),state=list,contentPadding=PaddingValues(16.dp,20.dp,16.dp,24.dp),
        horizontalArrangement=Arrangement.spacedBy(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.testTag("library")) {
        item(span={GridItemSpan(maxLineSpan)}) {
            Column {
                Text("RACHEL’S ENGLISH",fontSize=10.sp,letterSpacing=2.sp,color=Muted)
                Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("原声练习",fontSize=28.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
                    Text("${model.courses.size} 门课程",fontSize=12.sp,color=Muted)
                    GlyphButton("更新课程","refresh",{model.sync()},enabled=!model.syncing)
                }
                if(model.courses.size>=6) OutlinedTextField(query,changeQuery,singleLine=true,placeholder={Text("查找课程")},modifier=Modifier.fillMaxWidth().padding(top=20.dp),shape=RoundedCornerShape(16.dp))
            }
        }
        items(courses,key={it.id}) {course ->
            val interaction=remember {MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if(pressed).978f else 1f,if(LocalReduced.current)snap() else spring(.85f,650f),label="card-pressure")
            Row(Modifier.graphicsLayer {scaleX=scale;scaleY=scale}.clip(RoundedCornerShape(18.dp))
                .clickable(interactionSource=interaction,indication=null,role=Role.Button,onClick={model.open(course)})
                .testTag("course-${course.id}").fillMaxWidth().heightIn(min=88.dp).padding(vertical=8.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Box {
                    Cover(course,model.repository,with(shared){coverModifier(course,visibility)}.size(112.dp,70.dp))
                    Text("${course.seconds}s",fontSize=10.sp,color=Color.White,modifier=Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha=.55f)).padding(5.dp,2.dp))
                    if(model.loadingId==course.id)CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp),color=Color.White,strokeWidth=2.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(course.title,fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.Medium,modifier=with(shared){titleModifier(course,visibility)})
                    Text("${course.label} · ${course.count} 句",fontSize=12.sp,lineHeight=18.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                }
            }
        }
        if(courses.isEmpty())item(span={GridItemSpan(maxLineSpan)}){Text("没有找到这门课程。",color=Muted)}
    }
}
@Composable private fun LessonScreen(model: PracticeModel,open: OpenLesson,shared: SharedTransitionScope,visibility: AnimatedVisibilityScope) {
    var settings by rememberSaveable(open.course.id) {mutableStateOf(false)}
    val list=androidx.compose.foundation.lazy.rememberLazyListState()
    val coverEnd=with(LocalDensity.current){110.dp.roundToPx()}
    val headerVisible by remember(list,coverEnd){derivedStateOf {list.firstVisibleItemIndex==0&&list.firstVisibleItemScrollOffset<coverEnd}}
    val selected=model.playback.selected
    val reduced=LocalReduced.current
    LaunchedEffect(selected) {
        snapshotFlow {list.layoutInfo.totalItemsCount}.first {it>0}
        val visible=list.layoutInfo.visibleItemsInfo
        val item=visible.firstOrNull {it.index==selected+1}
        if(item==null||item.offset<list.layoutInfo.viewportStartOffset||item.offset+item.size>list.layoutInfo.viewportEndOffset-96) {
            if(reduced)list.scrollToItem(selected+1) else list.animateScrollToItem(selected+1)
        }
    }
    Box(Modifier.fillMaxSize().testTag("lesson")) {
        LazyColumn(state=list,contentPadding=PaddingValues(16.dp,12.dp,16.dp,128.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            item(key="header") {
                Column(Modifier.padding(bottom=18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {GlyphButton("返回课程","back",model::back);Text("课程",fontSize=12.sp,color=Muted)}
                    Row(Modifier.padding(top=10.dp,bottom=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Cover(open.course,model.repository,(if(headerVisible)with(shared){coverModifier(open.course,visibility)} else Modifier).size(76.dp,50.dp))
                        Column(Modifier.weight(1f)) {
                            Text(open.course.title,fontSize=21.sp,fontWeight=FontWeight.SemiBold,modifier=if(headerVisible)with(shared){titleModifier(open.course,visibility)} else Modifier)
                            Text(open.course.label,fontSize=12.sp,color=Muted)
                        }
                        val context=LocalContext.current
                        GlyphButton("打开原视频","external",{runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v=${open.course.id}")))}})
                    }
                    ModeSelector(model.drill,model::updateDrill)
                }
            }
            items(open.lesson.groups,key={it.id}) {sentence ->
                val active=sentence.id==selected
                val background by animateColorAsState(if(active)Tint else Color.White,if(reduced)snap() else tween(160),label="sentence-focus")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background)
                    .clickable(role=Role.Button,onClick={model.start(sentence.id)}).semantics {contentDescription="第 ${sentence.id+1} 句";stateDescription=if(active&&model.playback.running&&!model.playback.paused)"正在播放" else if(active)"当前句" else ""}
                    .testTag("sentence-${sentence.id}").padding(16.dp,18.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text("%02d".format(sentence.id+1),fontSize=11.sp,color=if(active)Accent else Muted,modifier=Modifier.padding(top=6.dp))
                        Column(Modifier.weight(1f)) {
                            val text=buildAnnotatedString {sentence.phrases.forEachIndexed {i,p ->
                                if(i>0)append(" ")
                                val speaking=active&&model.playback.source>=p.start&&model.playback.source<p.end
                                withStyle(SpanStyle(color=if(speaking)Accent else if(active)Ink else Muted,textDecoration=if(speaking)TextDecoration.Underline else null)){append(p.text)}
                            }}
                            Text(text,fontSize=21.sp,lineHeight=34.sp)
                            if(model.translation&&sentence.translation.isNotEmpty())Text(sentence.translation,fontSize=13.sp,lineHeight=21.sp,color=Muted,modifier=Modifier.padding(top=10.dp))
                            if(active&&model.cues&&sentence.cues.isNotEmpty())Text(sentence.cues.joinToString(" · "),fontSize=11.sp,lineHeight=18.sp,color=Accent.copy(alpha=.75f),modifier=Modifier.padding(top=10.dp))
                        }
                    }
                }
            }
        }
        var controlsVisible by remember(open.course.id) {mutableStateOf(false)}
        LaunchedEffect(open.course.id){controlsVisible=true}
        AnimatedVisibility(controlsVisible,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp),
            enter=if(reduced)EnterTransition.None else fadeIn(tween(120))+slideInVertically(spring(.9f,520f)){it/2},
            exit=if(reduced)ExitTransition.None else fadeOut(tween(100))) {
            Transport(model,{settings=true},Modifier.padding(horizontal=12.dp))
        }
    }
    if(settings) SettingsSheet(model){settings=false}
}
@Composable private fun ModeSelector(drill: Boolean,onChange: (Boolean)->Unit) {
    BoxWithConstraints(Modifier.width(204.dp).height(56.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xffe8eef6)).padding(4.dp)) {
        val half=maxWidth/2
        val offset by animateDpAsState(if(drill)half else 0.dp,if(LocalReduced.current)snap() else spring(.86f,650f),label="mode-selection")
        Box(Modifier.offset(x=offset).width(half).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Color.White))
        Row {listOf(false,true).forEach {value -> Box(Modifier.width(half).fillMaxHeight().clip(RoundedCornerShape(10.dp))
            .selectable(selected=drill==value,role=Role.Tab,onClick={onChange(value)}).testTag(if(value)"mode-drill" else "mode-original"),contentAlignment=Alignment.Center) {
            Text(if(value)"Drill · 三遍" else "原句",fontSize=13.sp,color=if(drill==value)Ink else Muted,fontWeight=if(drill==value)FontWeight.Medium else FontWeight.Normal)
        }}}
    }
}
@Composable private fun Transport(model: PracticeModel,settings: ()->Unit,modifier: Modifier) {
    val p=model.playback
    Column(modifier.animateContentSize(if(LocalReduced.current)snap() else spring(.95f,600f)), horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(Color.White,Color(0xffedf4fc))))
            .border(1.dp,Color.White,RoundedCornerShape(50)).semantics {progressBarRangeInfo=ProgressBarRangeInfo(p.progress,0f..1f)}) {
            Canvas(Modifier.matchParentSize()){drawRect(Accent.copy(alpha=.09f),size=androidx.compose.ui.geometry.Size(size.width*p.progress,size.height))}
            Row(Modifier.padding(6.dp,4.dp),verticalAlignment=Alignment.CenterVertically) {
                GlyphButton("上一句","previous",model::previous,enabled=p.selected>0)
                GlyphButton(if(p.running&&!p.paused)"暂停" else "播放",if(p.running&&!p.paused)"pause" else "play",model::toggle)
                GlyphButton("下一句","next",model::next,enabled=p.selected<(model.opened?.lesson?.groups?.lastIndex?:0))
                Box(Modifier.width(44.dp),contentAlignment=Alignment.Center) {
                    Text(if(p.repeat>0)"${p.repeat}/${p.total}" else "${p.selected+1}/${model.opened?.lesson?.groups?.size?:0}",fontSize=11.sp,color=Accent,maxLines=1)
                }
                GlyphButton("练习设置","more",settings)
            }
        }
        if(p.running&&p.waiting&&model.shadow)Text("跟读 · %.1f 秒".format(p.waitSeconds),fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=5.dp))
    }
}
@Composable private fun SettingsSheet(model: PracticeModel,dismiss: ()->Unit) {
    val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val scope=rememberCoroutineScope()
    val close: ()->Unit={scope.launch {sheet.hide();dismiss()};Unit}
    ModalBottomSheet(onDismissRequest=dismiss,containerColor=Backdrop,sheetState=sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=28.dp).padding(bottom=24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically){Text("练习设置",fontSize=17.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f));GlyphButton("关闭设置","close",close)}
            Setting("循环当前组",model.loop,model::updateLoop)
            Setting("留白跟读",model.shadow,model::updateShadow)
            if(model.shadow){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(1f,1.5f,2f).forEach {factor->Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(model.gap==factor)Tint else Color.Transparent)
                    .selectable(model.gap==factor,role=Role.RadioButton,onClick={model.updateGap(factor)}).heightIn(min=48.dp).padding(12.dp),contentAlignment=Alignment.Center){Text("${factor}×",fontSize=13.sp,color=if(model.gap==factor)Accent else Muted)}
            }}}
            Setting("中文翻译",model.translation,model::updateTranslation)
            Setting("发音提示",model.cues,model::updateCues)
        }
    }
}
@Composable private fun Setting(label: String,checked: Boolean,change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(checked,role=Role.Switch,onValueChange=change),verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=15.sp,modifier=Modifier.weight(1f));Switch(checked,onCheckedChange=null)
    }
}
@Composable private fun GlyphButton(label: String,glyph: String,click: ()->Unit,enabled: Boolean=true) {
    val interaction=remember {MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).9f else 1f,if(LocalReduced.current)snap() else spring(.8f,700f),label="control-pressure")
    IconButton(onClick=click,enabled=enabled,interactionSource=interaction,modifier=Modifier.size(48.dp).semantics {contentDescription=label}) {
        Canvas(Modifier.size(21.dp).graphicsLayer {scaleX=scale;scaleY=scale}) {
            val color=if(enabled)Ink else Muted.copy(alpha=.5f);val stroke=1.7.dp.toPx();val s=size.width/24f
            fun line(x1: Float,y1: Float,x2: Float,y2: Float)=drawLine(color,Offset(x1*s,y1*s),Offset(x2*s,y2*s),stroke,StrokeCap.Round)
            fun path(vararg pts: Float,fill: Boolean=false){val p=Path();p.moveTo(pts[0]*s,pts[1]*s);var i=2;while(i<pts.size){p.lineTo(pts[i]*s,pts[i+1]*s);i+=2};if(fill)p.close();drawPath(p,color,style=if(fill)androidx.compose.ui.graphics.drawscope.Fill else Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))}
            when(glyph){
                "play"->path(9f,5f,20f,12f,9f,19f,fill=true)
                "pause"->{line(9f,5f,9f,19f);line(16f,5f,16f,19f)}
                "previous"->{line(6f,5f,6f,19f);path(19f,5f,9f,12f,19f,19f,19f,5f)}
                "next"->{line(18f,5f,18f,19f);path(5f,5f,15f,12f,5f,19f,5f,5f)}
                "back"->path(14f,6f,8f,12f,14f,18f)
                "close"->{line(7f,7f,17f,17f);line(17f,7f,7f,17f)}
                "all"->{line(4f,6f,20f,6f);line(4f,11f,14f,11f);line(4f,16f,11f,16f);path(16f,14f,21f,17f,16f,20f,fill=true)}
                "refresh"->{drawArc(color,35f,280f,false,Offset(4*s,4*s),androidx.compose.ui.geometry.Size(16*s,16*s),style=Stroke(stroke,cap=StrokeCap.Round));path(16f,3f,20f,7f,15f,8f)}
                "external"->{path(9f,5f,5f,5f,5f,19f,19f,19f,19f,15f);path(14f,5f,19f,5f,19f,10f);line(19f,5f,11f,13f)}
                else->listOf(5f,12f,19f).forEach {drawCircle(color,1.4f*s,Offset(it*s,12f*s))}
            }
        }
    }
}
