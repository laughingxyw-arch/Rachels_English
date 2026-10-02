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
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

private val Ink: Color @Composable get()=if(LocalMaterialStyle.current.highContrast)Color(0xff101721) else Color(0xff25283b)
private val Muted: Color @Composable get()=if(LocalMaterialStyle.current.highContrast)Color(0xff414957) else Color(0xff6d7288)
private val Accent=Color(0xff1765c1)
private val Backdrop=Color(0xfff7f8fb)
private val Tint=Color(0xffedf4fc)
private val LocalReduced=staticCompositionLocalOf {false}
private val LocalSeeking=staticCompositionLocalOf {false}
val ReadingSceneActiveKey=SemanticsPropertyKey<Boolean>("ReadingSceneActive")
@Composable fun PracticeApp(model: PracticeModel) {
    val environment=rememberVisualEnvironment()
    val style=materialStyle(environment,model.reduceTransparency,model.enhanceContrast)
    val reduced=style.reducedMotion
    val backdrop=rememberBackdropSource()
    val libraryState=rememberLazyGridState()
    var query by rememberSaveable {mutableStateOf("")}
    var settings by rememberSaveable(model.opened?.course?.id) {mutableStateOf(false)}
    val settingsSheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    var sceneSize by remember {mutableStateOf(IntSize.Zero)}
    var sheetHeight by remember {mutableFloatStateOf(0f)}
    val progressSpring=remember(sceneSize){courseProgressSpring(maxOf(sceneSize.width,sceneSize.height).toFloat())}
    val gestureScope=rememberCoroutineScope()
    val navigation=remember {SeekableTransitionState<OpenLesson?>(model.opened)}
    val transition=rememberTransition(navigation,label="course-space")
    val gesture=remember {Animatable(0f)}
    var seeking by remember {mutableStateOf(false)}
    var gestureToken by remember {mutableIntStateOf(0)}
    // Keep all shared geometry on one linear timeline. Apply physics to its progress,
    // rather than mixing bounds springs of different durations with a seekable gesture.
    LaunchedEffect(model.opened,reduced,seeking){if(!seeking){if(model.opened!=null)gesture.snapTo(0f);if(reduced)navigation.snapTo(model.opened) else navigation.animateTo(model.opened,animationSpec=progressSpring)}}
    PredictiveBackHandler(enabled=model.opened!=null&&!settings) {events ->
        val origin=model.opened
        val token=++gestureToken
        val initial=if(seeking)gesture.value.coerceIn(0f,1f) else 0f
        val velocity=GestureVelocity()
        gesture.snapTo(initial);seeking=true
        val tracking=if(!reduced)gestureScope.launch {
            snapshotFlow {gesture.value to transition.totalDurationNanos}.collect {(progress,duration)->
                val geometryShare=if(duration>0L)(360_000_000f/duration).coerceAtMost(1f) else 1f
                navigation.seekTo((progress*geometryShare).coerceIn(0f,1f),null)
            }
        } else null
        try {
            events.collect {val progress=initial+(1f-initial)*it.progress;velocity.add(progress,android.os.SystemClock.uptimeMillis());if(!reduced)gesture.snapTo(progress)}
            // Stop the asynchronous finger follower before settling. The transition itself
            // owns the remaining geometry and must finish before the static card takes over.
            tracking?.cancelAndJoin()
            if(!reduced)coroutineScope {
                val fading=launch {gesture.animateTo(1f,progressSpring,initialVelocity=velocity.velocity)}
                navigation.animateTo(null,animationSpec=progressSpring)
                fading.cancelAndJoin();gesture.snapTo(1f)
            }
            if(token==gestureToken&&model.opened==origin){if(reduced)navigation.snapTo(null);model.back()}
        } catch(_: CancellationException){withContext(NonCancellable){
            tracking?.cancelAndJoin()
            if(token==gestureToken&&model.opened==origin){
                if(!reduced)coroutineScope {
                    val fading=launch {gesture.animateTo(0f,progressSpring,initialVelocity=velocity.velocity)}
                    navigation.animateTo(origin,animationSpec=progressSpring)
                    fading.cancelAndJoin();gesture.snapTo(0f)
                }
                if(reduced&&token==gestureToken&&model.opened==origin)navigation.snapTo(origin)
            }
        }} finally {tracking?.cancel();if(token==gestureToken)seeking=false}
    }
    CompositionLocalProvider(LocalReduced provides reduced,LocalSeeking provides seeking,LocalMaterialStyle provides style,LocalBackdropSource provides backdrop) {
        MaterialTheme(colorScheme=lightColorScheme(primary=Accent,background=Backdrop,surface=Color.White,onSurface=Ink,onBackground=Ink)) {
            Box(Modifier.fillMaxSize().onSizeChanged {sceneSize=it}.background(Backdrop).safeDrawingPadding()) {
                SharedTransitionLayout(Modifier.fillMaxSize().graphicsLayer {
                    // Read the sheet's live offset in the layer phase, not in composition.
                    // This also follows drag reversal and restores the background while hiding.
                    val offset=if(settings&&sheetHeight>0f&&settingsSheet.hasExpandedState)settingsSheet.requireOffset() else Float.NaN
                    val scale=sheetBackgroundScale(offset,sceneSize.height.toFloat(),sheetHeight,reduced)
                    scaleX=scale;scaleY=scale
                }.testTag("reading-space").semantics {this[ReadingSceneActiveKey]=transition.currentState!=null||transition.targetState!=null}.captureBackdrop(backdrop)) {
                    val shared=this
                    transition.AnimatedContent(contentKey={it?.course?.id?:"home"},
                        transitionSpec={(if(reduced)EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(120,delayMillis=80)) togetherWith fadeOut(tween(80))).using(null)}) {open ->
                        if(open==null)Library(model,shared,this,libraryState,query,{query=it})
                        else LessonScreen(model,open,shared,this)
                    }
                }
                AnimatedVisibility(model.opened!=null,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp).graphicsLayer {alpha=if(seeking||model.opened==null)(1f-gesture.value).coerceIn(0f,1f) else 1f},
                    enter=if(reduced)EnterTransition.None else fadeIn(tween(140))+slideInVertically(spring(1f,600f)){it/3},
                    exit=if(reduced)ExitTransition.None else fadeOut(tween(100))) {
                    Transport(model,{settings=true},Modifier.padding(horizontal=28.dp))
                }
                NoticePill(model,Modifier.align(Alignment.BottomCenter).padding(horizontal=24.dp).padding(bottom=if(model.opened!=null)88.dp else 24.dp))
            }
            if(settings&&model.opened!=null)SettingsSheet(model,environment.highContrast,settingsSheet,{sheetHeight=it}){settings=false}
        }
    }
}
@Composable private fun SharedTransitionScope.containerModifier(c: Course,visibility: AnimatedVisibilityScope): Modifier {
    val reduced=LocalReduced.current;val seeking=LocalSeeking.current
    return Modifier.sharedBounds(rememberSharedContentState("container-${c.id}"),visibility,
        boundsTransform={_,_->if(reduced)snap() else tween(360,easing=LinearEasing)},
        enter=if(reduced)EnterTransition.None else if(seeking)fadeIn(tween(72,delayMillis=288)) else fadeIn(tween(180,delayMillis=80)),
        exit=if(reduced)ExitTransition.None else if(seeking)fadeOut(tween(72,delayMillis=288)) else fadeOut(tween(100)),
        resizeMode=SharedTransitionScope.ResizeMode.RemeasureToBounds,placeHolderSize=SharedTransitionScope.PlaceHolderSize.animatedSize)
}
@Composable private fun SharedTransitionScope.coverModifier(c: Course,visibility: AnimatedVisibilityScope): Modifier {
    val reduced=LocalReduced.current;val seeking=LocalSeeking.current
    return Modifier.sharedElement(rememberSharedContentState("cover-${c.id}"),visibility,
        boundsTransform={_,_->if(reduced)snap() else tween(360,easing=LinearEasing)})
}
@Composable private fun SharedTransitionScope.titleModifier(c: Course,visibility: AnimatedVisibilityScope): Modifier {
    val reduced=LocalReduced.current;val seeking=LocalSeeking.current
    return Modifier.sharedElement(rememberSharedContentState("title-${c.id}"),visibility,
        boundsTransform={_,_->if(reduced)snap() else tween(360,easing=LinearEasing)})
}
@Composable private fun Cover(c: Course,repo: CourseRepository,modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(repo.cachedCover(c),c.id,c.version){
        // Return decoding results through the UI dispatcher before notifying Compose snapshots.
        // Decoding itself remains on IO inside the repository.
        value=withContext(Dispatchers.Main.immediate){repo.cover(c)}
    }
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
            val scale by animateFloatAsState(if(pressed).978f else 1f,if(LocalReduced.current)snap() else spring(1f,900f),label="card-pressure")
            Row(with(shared){containerModifier(course,visibility)}.graphicsLayer {scaleX=scale;scaleY=scale}.clip(RoundedCornerShape(18.dp)).background(Color.White)
                .then(with(shared){Modifier.skipToLookaheadSize()})
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
    val list=androidx.compose.foundation.lazy.rememberLazyListState()
    val coverEnd=with(LocalDensity.current){110.dp.roundToPx()}
    val headerVisible by remember(list,coverEnd){derivedStateOf {list.firstVisibleItemIndex==0&&list.firstVisibleItemScrollOffset<coverEnd}}
    val selected by remember(model){derivedStateOf {model.playback.selected}}
    val reduced=LocalReduced.current
    LaunchedEffect(selected) {
        snapshotFlow {list.layoutInfo.totalItemsCount}.first {it>0}
        val visible=list.layoutInfo.visibleItemsInfo
        val item=visible.firstOrNull {it.index==selected+1}
        if(item==null||item.offset<list.layoutInfo.viewportStartOffset||item.offset+item.size>list.layoutInfo.viewportEndOffset-96) {
            if(reduced)list.scrollToItem(selected+1) else list.animateScrollToItem(selected+1)
        }
    }
    val seeking=LocalSeeking.current
    val corner by visibility.transition.animateDp(transitionSpec={if(reduced)snap() else tween(360,easing=LinearEasing)},label="course-corner") {if(it==EnterExitState.Visible)0.dp else 18.dp}
    Box(with(shared){containerModifier(open.course,visibility)}.testTag("lesson").clip(RoundedCornerShape(corner)).background(Backdrop)
        .then(with(shared){Modifier.skipToLookaheadSize()}).fillMaxSize()) {
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
                    ModeSelector(model.drill,model.repeatCount,model::updateDrill)
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
                            ReadingText(sentence,active,if(active)model.playback.source else -1.0,reduced)
                            if(model.translation&&sentence.translation.isNotEmpty())Text(sentence.translation,fontSize=13.sp,lineHeight=21.sp,color=Muted,modifier=Modifier.padding(top=10.dp))
                            if(active&&model.cues&&sentence.cues.isNotEmpty())Text(sentence.cues.joinToString(" · "),fontSize=11.sp,lineHeight=18.sp,color=Accent.copy(alpha=if(LocalMaterialStyle.current.highContrast)1f else .75f),modifier=Modifier.padding(top=10.dp))
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun ModeSelector(drill: Boolean,repeats: Int,onChange: (Boolean)->Unit) {
    BoxWithConstraints(Modifier.width(204.dp).height(56.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xffe8eef6)).padding(4.dp)) {
        val half=maxWidth/2
        val offset by animateDpAsState(if(drill)half else 0.dp,if(LocalReduced.current)snap() else spring(1f,700f),label="mode-selection")
        Box(Modifier.offset(x=offset).width(half).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Color.White))
        Row {listOf(false,true).forEach {value -> Box(Modifier.width(half).fillMaxHeight().clip(RoundedCornerShape(10.dp))
            .selectable(selected=drill==value,role=Role.Tab,onClick={onChange(value)}).testTag(if(value)"mode-drill" else "mode-original"),contentAlignment=Alignment.Center) {
            Text(if(value)"Drill · ${repeats}×" else "原句",fontSize=13.sp,color=if(drill==value)Ink else Muted,fontWeight=if(drill==value)FontWeight.Medium else FontWeight.Normal)
        }}}
    }
}
@Composable private fun Transport(model: PracticeModel,settings: ()->Unit,modifier: Modifier) {
    val p=model.playback
    val style=LocalMaterialStyle.current
    val progress by animateFloatAsState(p.progress,if(LocalReduced.current||p.progress==0f)snap() else tween(45,easing=LinearEasing),label="transport-light")
    Column(modifier.animateContentSize(if(LocalReduced.current)snap() else spring(1f,600f)),horizontalAlignment=Alignment.CenterHorizontally) {
        FrostedSurface(Modifier.testTag("player-surface").semantics {progressBarRangeInfo=ProgressBarRangeInfo(p.progress,0f..1f)},radius=32.dp) {
            Canvas(Modifier.matchParentSize()){
                val colors=if(style.highContrast)listOf(Accent.copy(alpha=.12f),Accent.copy(alpha=.12f)) else listOf(Accent.copy(alpha=.025f),Color(0xff69b5ff).copy(alpha=.18f))
                drawRect(Brush.horizontalGradient(colors),size=androidx.compose.ui.geometry.Size(size.width*progress,size.height))
            }
            Row(Modifier.padding(6.dp,4.dp),verticalAlignment=Alignment.CenterVertically) {
                GlyphButton("上一句","previous",model::previous,enabled=p.selected>0)
                GlyphButton(if(p.running&&!p.paused)"暂停" else "播放",if(p.running&&!p.paused)"pause" else "play",model::toggle)
                GlyphButton("下一句","next",model::next,enabled=p.selected<(model.opened?.lesson?.groups?.lastIndex?:0))
                Box(Modifier.width(44.dp),contentAlignment=Alignment.Center){Text(if(p.repeat>0)"${p.repeat}/${p.total}" else "${p.selected+1}/${model.opened?.lesson?.groups?.size?:0}",fontSize=11.sp,color=Accent,maxLines=1)}
                GlyphButton("练习设置","more",settings)
            }
        }
        if(p.running&&p.waiting&&model.shadow)Text("跟读 · %.1f 秒".format(p.waitSeconds),fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=5.dp))
    }
}
@Composable private fun SettingsSheet(model: PracticeModel,systemContrast: Boolean,sheet: SheetState,measure: (Float)->Unit,dismiss: ()->Unit) {
    val scope=rememberCoroutineScope()
    val close: ()->Unit={scope.launch {sheet.hide();dismiss()};Unit}
    ModalBottomSheet(onDismissRequest=dismiss,modifier=Modifier.onSizeChanged {measure(it.height.toFloat())}.testTag("settings-sheet"),containerColor=Color.Transparent,scrimColor=Color(0xff172539).copy(alpha=.16f),dragHandle=null,sheetState=sheet) {
        FrostedSurface(Modifier.fillMaxWidth(),radius=28.dp,weight=SurfaceWeight.Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=28.dp).padding(top=12.dp,bottom=24.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom=14.dp).size(28.dp,3.dp).clip(RoundedCornerShape(2.dp)).background(Muted.copy(alpha=.3f)))
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically){Text("练习设置",fontSize=17.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f));GlyphButton("关闭设置","close",close)}
            if(model.drill) RepeatSetting(model)
            Setting("循环当前组",model.loop,model::updateLoop)
            Setting("留白跟读",model.shadow,model::updateShadow)
            if(model.shadow){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(1f,1.5f,2f).forEach {factor->Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(model.gap==factor)Tint else Color.Transparent)
                    .selectable(model.gap==factor,role=Role.RadioButton,onClick={model.updateGap(factor)}).heightIn(min=48.dp).padding(12.dp),contentAlignment=Alignment.Center){Text("${factor}×",fontSize=13.sp,color=if(model.gap==factor)Accent else Muted)}
            }}}
            Setting("中文翻译",model.translation,model::updateTranslation)
            Setting("发音提示",model.cues,model::updateCues)
            AppearanceSetting(model,systemContrast)
        }
        }
    }
}
@Composable private fun Setting(label: String,checked: Boolean,change: (Boolean)->Unit,enabled: Boolean=true) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=change),verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=15.sp,modifier=Modifier.weight(1f));Switch(checked,onCheckedChange=null,enabled=enabled,colors=SwitchDefaults.colors(uncheckedTrackColor=Color(0xffe3e9f1),uncheckedBorderColor=Color.Transparent,uncheckedThumbColor=Color.White))
    }
}
@Composable private fun RepeatSetting(model: PracticeModel) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    val reduced=LocalReduced.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role=Role.Button,onClick={expanded=!expanded}).testTag("repeat-setting")
            .semantics {stateDescription="${model.repeatCount} 次"},verticalAlignment=Alignment.CenterVertically) {
            Text("复读次数",fontSize=15.sp,modifier=Modifier.weight(1f))
            Text("${model.repeatCount}×",fontSize=14.sp,color=Accent)
        }
        AnimatedVisibility(expanded,enter=if(reduced)EnterTransition.None else expandVertically(spring(1f,600f))+fadeIn(tween(120)),exit=if(reduced)ExitTransition.None else shrinkVertically(tween(160))+fadeOut(tween(100))) {
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                (2..5).forEach {count ->
                    Box(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if(model.repeatCount==count)Tint else Color.Transparent)
                        .selectable(model.repeatCount==count,role=Role.RadioButton,onClick={model.updateRepeatCount(count);expanded=false})
                        .testTag("repeat-$count"),contentAlignment=Alignment.Center) {Text("${count}×",fontSize=14.sp,color=if(model.repeatCount==count)Accent else Muted)}
                }
            }
        }
    }
}
@Composable private fun AppearanceSetting(model: PracticeModel,systemContrast: Boolean) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=Role.Button,onClick={expanded=!expanded}).testTag("appearance-setting"),verticalAlignment=Alignment.CenterVertically) {
            Text("显示辅助",fontSize=15.sp,modifier=Modifier.weight(1f))
            Text(if(model.enhanceContrast||systemContrast)"高对比度" else if(model.reduceTransparency)"实色" else "自动",fontSize=12.sp,color=Muted)
        }
        AnimatedVisibility(expanded,enter=if(LocalReduced.current)EnterTransition.None else expandVertically(spring(1f,600f))+fadeIn(tween(120)),exit=if(LocalReduced.current)ExitTransition.None else shrinkVertically(tween(140))+fadeOut(tween(100))) {
            Column {
                Setting("减少透明效果",model.reduceTransparency,model::updateReduceTransparency)
                Setting(if(systemContrast)"增强对比度 · 系统已启用" else "增强对比度",model.enhanceContrast||systemContrast,model::updateEnhanceContrast,enabled=!systemContrast)
            }
        }
    }
}
@Composable private fun NoticePill(model: PracticeModel,modifier: Modifier) {
    val text=model.message
    var last by remember {mutableStateOf("")}
    SideEffect {if(text!=null)last=text}
    val accessibility=LocalAccessibilityManager.current
    LaunchedEffect(text,model.messageIsError){if(text!=null){
        val duration=accessibility?.calculateRecommendedTimeoutMillis(if(model.messageIsError)5000L else 3000L,containsIcons=true,containsText=true)?:3000L
        delay(duration);if(model.message==text)model.consumeMessage()
    }}
    val reduced=LocalReduced.current
    AnimatedVisibility(text!=null,modifier=modifier,enter=if(reduced)EnterTransition.None else fadeIn(tween(140))+slideInVertically(spring(1f,650f)){it/4},exit=if(reduced)ExitTransition.None else fadeOut(tween(160))+slideOutVertically(tween(160)){it/6}) {
        FrostedSurface(Modifier.widthIn(max=300.dp).semantics {liveRegion=if(model.messageIsError)LiveRegionMode.Assertive else LiveRegionMode.Polite}.testTag("notice"),radius=24.dp,weight=SurfaceWeight.Chip) {
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Canvas(Modifier.size(15.dp)) {
                val color=if(model.messageIsError)Color(0xffa94b44) else Accent
                if(model.messageIsError){drawCircle(color.copy(alpha=.15f));drawLine(color,Offset(size.width/2,size.height*.25f),Offset(size.width/2,size.height*.58f),1.4.dp.toPx(),StrokeCap.Round);drawCircle(color,.8.dp.toPx(),Offset(size.width/2,size.height*.76f))}
                else {listOf(.38f,.72f,.52f).forEachIndexed {i,h ->val x=size.width*(.22f+.28f*i);drawLine(color,Offset(x,size.height*(1-h)/2),Offset(x,size.height*(1+h)/2),1.6.dp.toPx(),StrokeCap.Round)}}
            }
            Text(text?:last,fontSize=12.sp,lineHeight=18.sp,color=Ink,maxLines=3,modifier=Modifier.weight(1f,fill=false))
        }
        }
    }
}
@Composable private fun GlyphButton(label: String,glyph: String,click: ()->Unit,enabled: Boolean=true) {
    val interaction=remember {MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
    val ink=Ink;val muted=Muted
    val scale by animateFloatAsState(if(pressed).97f else 1f,if(LocalReduced.current)snap() else spring(1f,900f),label="control-pressure")
    IconButton(onClick=click,enabled=enabled,interactionSource=interaction,modifier=Modifier.size(48.dp).drawBehind {
        if(glyph=="play"||glyph=="pause")drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha=.75f),Accent.copy(alpha=if(pressed).10f else .025f))),radius=20.dp.toPx()*scale)
    }.semantics {contentDescription=label}) {
        Crossfade(glyph,animationSpec=if(LocalReduced.current)snap() else tween(100),label="control-symbol") {symbol ->
        Canvas(Modifier.size(21.dp).graphicsLayer {scaleX=scale;scaleY=scale}) {
            val color=if(enabled)ink else muted.copy(alpha=.5f);val stroke=1.7.dp.toPx();val s=size.width/24f
            fun line(x1: Float,y1: Float,x2: Float,y2: Float)=drawLine(color,Offset(x1*s,y1*s),Offset(x2*s,y2*s),stroke,StrokeCap.Round)
            fun path(vararg pts: Float,fill: Boolean=false){val p=Path();p.moveTo(pts[0]*s,pts[1]*s);var i=2;while(i<pts.size){p.lineTo(pts[i]*s,pts[i+1]*s);i+=2};if(fill)p.close();drawPath(p,color,style=if(fill)androidx.compose.ui.graphics.drawscope.Fill else Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))}
            when(symbol){
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
}
