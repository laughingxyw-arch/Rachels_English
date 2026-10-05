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
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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

private val Ink: Color @Composable get()=LocalPracticePalette.current.ink
private val Muted: Color @Composable get()=LocalPracticePalette.current.muted
private val Accent: Color @Composable get()=LocalPracticePalette.current.accent
private val Backdrop: Color @Composable get()=LocalPracticePalette.current.background
private val Tint: Color @Composable get()=LocalPracticePalette.current.selected
private val Surface: Color @Composable get()=LocalPracticePalette.current.surface
val LibraryPullKey=SemanticsPropertyKey<Float>("LibraryPullFraction")
val RecordsProgressKey=SemanticsPropertyKey<Float>("RecordsProgress")
val DarkThemeKey=SemanticsPropertyKey<Boolean>("DarkTheme")
internal val LocalReduced=staticCompositionLocalOf {false}
private val LocalSeeking=staticCompositionLocalOf {false}
val CoverBadgeOpacityKey=SemanticsPropertyKey<Float>("CoverBadgeOpacity")
val ModeRollPositionKey=SemanticsPropertyKey<Float>("ModeRollPosition")
val ReadingFirstItemKey=SemanticsPropertyKey<Int>("ReadingFirstItem")
val ReadingFirstOffsetKey=SemanticsPropertyKey<Int>("ReadingFirstOffset")
val CourseTitleLineCountKey=SemanticsPropertyKey<Int>("CourseTitleLineCount")
val ReadingSceneActiveKey=SemanticsPropertyKey<Boolean>("ReadingSceneActive")
@Composable fun PracticeApp(model: PracticeModel) {
    val environment=rememberVisualEnvironment()
    val style=materialStyle(environment,model.reduceTransparency,model.enhanceContrast)
    val dark=androidx.compose.foundation.isSystemInDarkTheme()
    val palette=remember(dark,style.highContrast){practicePalette(dark,style.highContrast)}
    val scheme=remember(palette){practiceColorScheme(palette)}
    val reduced=style.reducedMotion
    val backdrop=rememberBackdropSource()
    val libraryState=rememberLazyGridState()
    var utilityPage by rememberSaveable {mutableStateOf<String?>(null)}
    val recordsProgress=remember {Animatable(if(utilityPage!=null)1f else 0f)}
    var recordsSeeking by remember {mutableStateOf(false)}
    var recordsToken by remember {mutableIntStateOf(0)}
    var recordsJob by remember {mutableStateOf<kotlinx.coroutines.Job?>(null)}
    var query by rememberSaveable {mutableStateOf("")}
    var settings by rememberSaveable(model.opened?.course?.id) {mutableStateOf(false)}
    val settingsSheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    var sceneSize by remember {mutableStateOf(IntSize.Zero)}
    var sheetHeight by remember {mutableFloatStateOf(0f)}
    val progressSpring=remember(sceneSize){courseProgressSpring(maxOf(sceneSize.width,sceneSize.height).toFloat())}
    val gestureScope=rememberCoroutineScope()
    val navigation=remember {SeekableTransitionState<OpenLesson?>(model.opened)}
    val transition=rememberTransition(navigation,label="course-space")
    // Retained exit content must never sit above the destination's touch targets.
    // Keep separate transforms: AnimatedContent remembers each scene's enter spec.
    val homeTransform=remember(reduced){if(reduced)EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(120,delayMillis=80)) togetherWith fadeOut(tween(80))}
    val lessonTransform=remember(reduced){if(reduced)EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(120,delayMillis=80)) togetherWith fadeOut(tween(80))}
    homeTransform.targetContentZIndex=if(transition.targetState==null)1f else 0f
    lessonTransform.targetContentZIndex=if(transition.targetState!=null)1f else 0f
    val gesture=remember {Animatable(0f)}
    var seeking by remember {mutableStateOf(false)}
    var gestureToken by remember {mutableIntStateOf(0)}
    // Keep all shared geometry on one linear timeline. Apply physics to its progress,
    // rather than mixing bounds springs of different durations with a seekable gesture.
    LaunchedEffect(model.opened,reduced,seeking){if(!seeking){if(model.opened!=null)gesture.snapTo(0f);if(reduced)navigation.snapTo(model.opened) else navigation.animateTo(model.opened,animationSpec=progressSpring)}}
    LaunchedEffect(utilityPage,reduced,recordsSeeking) {
        if(utilityPage!=null&&!recordsSeeking){if(reduced)recordsProgress.snapTo(1f) else recordsProgress.animateTo(1f,progressSpring)}
    }
    val closeRecords: ()->Unit={
        val token=++recordsToken;recordsJob?.cancel();recordsSeeking=true
        recordsJob=gestureScope.launch {
            if(reduced)recordsProgress.snapTo(0f) else recordsProgress.animateTo(0f,progressSpring)
            if(token==recordsToken){utilityPage=null;recordsSeeking=false}
        }
    }
    PredictiveBackHandler(enabled=utilityPage!=null&&!settings) {events->
        val token=++recordsToken;recordsJob?.cancel();recordsSeeking=true
        val initial=recordsProgress.value;val velocity=GestureVelocity()
        try {
            events.collect {velocity.add(it.progress,android.os.SystemClock.uptimeMillis());if(!reduced)recordsProgress.snapTo(initial*(1f-it.progress))}
            if(reduced)recordsProgress.snapTo(0f) else recordsProgress.animateTo(0f,progressSpring,initialVelocity=-initial*velocity.velocity)
            if(token==recordsToken)utilityPage=null
        } catch(_: CancellationException){withContext(NonCancellable){if(token==recordsToken){if(reduced)recordsProgress.snapTo(1f) else recordsProgress.animateTo(1f,progressSpring,initialVelocity=-initial*velocity.velocity)}}}
        finally {if(token==recordsToken)recordsSeeking=false}
    }
    PredictiveBackHandler(enabled=model.opened!=null&&!settings&&utilityPage==null) {events ->
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
            if(token==gestureToken&&model.opened===origin){if(reduced)navigation.snapTo(null);model.finishBack(origin)}
        } catch(_: CancellationException){withContext(NonCancellable){
            tracking?.cancelAndJoin()
            if(token==gestureToken&&model.opened===origin){
                if(!reduced)coroutineScope {
                    val fading=launch {gesture.animateTo(0f,progressSpring,initialVelocity=velocity.velocity)}
                    navigation.animateTo(origin,animationSpec=progressSpring)
                    fading.cancelAndJoin();gesture.snapTo(0f)
                }
                if(reduced&&token==gestureToken&&model.opened===origin)navigation.snapTo(origin)
            }
        }} finally {tracking?.cancel();if(token==gestureToken)seeking=false}
    }
    CompositionLocalProvider(LocalContentColor provides palette.ink,LocalPracticePalette provides palette,LocalReduced provides reduced,LocalSeeking provides seeking,LocalMaterialStyle provides style,LocalBackdropSource provides backdrop) {
        MaterialTheme(colorScheme=scheme) {
            Box(Modifier.fillMaxSize().testTag("app-root").semantics {this[DarkThemeKey]=dark}.onSizeChanged {sceneSize=it}.background(Backdrop).safeDrawingPadding()) {
                SharedTransitionLayout(Modifier.fillMaxSize().graphicsLayer {
                    // Read the sheet's live offset in the layer phase, not in composition.
                    // This also follows drag reversal and restores the background while hiding.
                    val offset=if(settings&&sheetHeight>0f&&settingsSheet.hasExpandedState)settingsSheet.requireOffset() else Float.NaN
                    val scale=sheetBackgroundScale(offset,sceneSize.height.toFloat(),sheetHeight,reduced)
                    scaleX=scale;scaleY=scale
                    translationX=-size.width*.22f*recordsProgress.value
                    alpha=(1f-recordsProgress.value).coerceIn(0f,1f)
                }.testTag("reading-space").semantics {this[ReadingSceneActiveKey]=transition.currentState!=null||transition.targetState!=null}.then(if(utilityPage!=null)Modifier.clearAndSetSemantics {} else Modifier).captureBackdrop(backdrop)) {
                    val shared=this
                    transition.AnimatedContent(contentKey={it?.course?.id?:"home"},
                        transitionSpec={(if(targetState==null)homeTransform else lessonTransform).using(null)}) {open ->
                        if(open==null)Library(model,shared,this,libraryState,query,{query=it},{utilityPage="records"})
                        else LessonScreen(model,open,shared,this)
                    }
                }
                AnimatedVisibility(model.opened!=null,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp).then(if(utilityPage!=null)Modifier.clearAndSetSemantics {} else Modifier).graphicsLayer {alpha=if(seeking||model.opened==null)(1f-gesture.value).coerceIn(0f,1f) else 1f},
                    enter=if(reduced)EnterTransition.None else fadeIn(tween(140))+slideInVertically(spring(1f,600f)){it/3},
                    exit=if(reduced)ExitTransition.None else fadeOut(tween(100))) {
                    Transport(model,{settings=true},Modifier.padding(horizontal=28.dp))
                }
                if(utilityPage!=null) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        translationX=size.width*(1f-recordsProgress.value)
                        val offset=if(settings&&sheetHeight>0f&&settingsSheet.hasExpandedState)settingsSheet.requireOffset() else Float.NaN
                        val scale=sheetBackgroundScale(offset,sceneSize.height.toFloat(),sheetHeight,reduced)
                        scaleX=scale;scaleY=scale
                    }.testTag("records-space").semantics {this[RecordsProgressKey]=recordsProgress.value}.captureBackdrop(backdrop)) {
                        UtilityPage(model,closeRecords,{settings=true},{course->closeRecords();model.open(course)})
                    }
                }
                NoticePill(model,Modifier.align(Alignment.BottomCenter).padding(horizontal=24.dp).padding(bottom=if(model.opened!=null)88.dp else 24.dp))
            }
            if(settings&&(model.opened!=null||utilityPage!=null))SettingsSheet(model,environment.highContrast,settingsSheet,{sheetHeight=it},{settings=false},generalOnly=utilityPage!=null)
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
    val reduced=LocalReduced.current
    return Modifier.sharedElement(rememberSharedContentState("title-${c.id}"),visibility,
        boundsTransform={_,_->if(reduced)snap() else tween(360,easing=LinearEasing)}).skipToLookaheadSize()
}
@Composable private fun CourseTitle(c: Course,modifier: Modifier) {
    var lines by remember(c.id){mutableIntStateOf(0)}
    Text(c.title,fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.Medium,
        modifier=modifier.testTag("course-title-${c.id}").semantics {this[CourseTitleLineCountKey]=lines},
        onTextLayout={if(lines!=it.lineCount)lines=it.lineCount})
}
@Composable private fun coverBadge(visibility: AnimatedVisibilityScope,library: Boolean): State<Float> {
    val reduced=LocalReduced.current
    return visibility.transition.animateFloat(transitionSpec={if(reduced)snap() else tween(360,easing=LinearEasing)},label="cover-duration") {
        if((it==EnterExitState.Visible)==library)1f else 0f
    }
}
@Composable private fun Cover(c: Course,repo: CourseRepository,modifier: Modifier,badge: State<Float>) {
    val bitmap by produceState<android.graphics.Bitmap?>(repo.cachedCover(c),c.id,c.version){
        // Decoding remains on IO; snapshot notifications return through the UI dispatcher.
        value=withContext(Dispatchers.Main.immediate){repo.cover(c)}
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Tint)) {
        bitmap?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize().testTag("cover-ready-${c.id}"),contentScale=ContentScale.Crop)}
        Text("${c.seconds}s",fontSize=10.sp,color=Color.White,
            modifier=Modifier.align(Alignment.BottomEnd).padding(5.dp).graphicsLayer {alpha=badge.value}
                .testTag("cover-duration-${c.id}").clearAndSetSemantics {this[CoverBadgeOpacityKey]=badge.value}
                .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha=.55f)).padding(5.dp,2.dp))
    }
}
@Composable private fun Library(model: PracticeModel,shared: SharedTransitionScope,visibility: AnimatedVisibilityScope,list: LazyGridState,query: String,changeQuery: (String)->Unit,records: ()->Unit) {
    val courses=model.courses.filter { "${it.title} ${it.label}".contains(query,true) }
    val refreshState=rememberPullToRefreshState()
    var refreshRequested by remember {mutableStateOf(false)}
    val refreshing=refreshRequested&&model.syncing
    val haptic=LocalHapticFeedback.current
    LaunchedEffect(model.syncing){if(!model.syncing)refreshRequested=false}
    LaunchedEffect(refreshState){snapshotFlow {refreshState.distanceFraction>=1f}.collect {crossed->if(crossed&&!model.syncing)haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}}
    Box(Modifier.fillMaxSize().pullToRefresh(isRefreshing=refreshing,state=refreshState,enabled=!model.syncing,threshold=72.dp,onRefresh={refreshRequested=true;model.sync(false)})
        .testTag("library-refresh").semantics {this[LibraryPullKey]=refreshState.distanceFraction;customActions=listOf(CustomAccessibilityAction("更新课程"){if(!model.syncing){refreshRequested=true;model.sync(false)};true})}) {
    LazyVerticalGrid(GridCells.Adaptive(360.dp),state=list,contentPadding=PaddingValues(16.dp,20.dp,16.dp,24.dp),
        horizontalArrangement=Arrangement.spacedBy(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.graphicsLayer {translationY=72.dp.toPx()*refreshState.distanceFraction.coerceAtMost(1.5f)}.testTag("library")) {
        item(span={GridItemSpan(maxLineSpan)}) {
            Column {
                Text("RACHEL’S ENGLISH",fontSize=10.sp,letterSpacing=2.sp,color=Muted)
                Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("原声练习",fontSize=28.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
                    Text("${model.courses.size} 门课程",fontSize=12.sp,color=Muted)
                    GlyphButton("学习记录","history",records)
                }
                if(model.courses.size>=6) OutlinedTextField(query,changeQuery,singleLine=true,placeholder={Text("查找课程")},modifier=Modifier.fillMaxWidth().padding(top=20.dp),shape=RoundedCornerShape(16.dp))
            }
        }
        items(courses,key={it.id}) {course ->
            val interaction=remember {MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if(pressed).978f else 1f,if(LocalReduced.current)snap() else spring(1f,900f),label="card-pressure")
            Row(with(shared){containerModifier(course,visibility)}.graphicsLayer {scaleX=scale;scaleY=scale}.clip(RoundedCornerShape(18.dp)).background(Surface)
                .then(with(shared){Modifier.skipToLookaheadSize()})
                .clickable(interactionSource=interaction,indication=null,role=Role.Button,onClick={model.open(course)})
                .testTag("course-${course.id}").semantics {stateDescription="${course.seconds} 秒 · ${course.count} 句"}.fillMaxWidth().heightIn(min=88.dp).padding(vertical=8.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Box {
                    Cover(course,model.repository,with(shared){coverModifier(course,visibility)}.size(112.dp,70.dp),coverBadge(visibility,true))
                    if(model.loadingId==course.id)CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp),color=Color.White,strokeWidth=2.dp)
                }
                Column(Modifier.weight(1f)) {
                    CourseTitle(course,with(shared){titleModifier(course,visibility)})
                    Text("${course.label} · ${course.count} 句",fontSize=12.sp,lineHeight=18.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                }
            }
        }
        if(courses.isEmpty())item(span={GridItemSpan(maxLineSpan)}){Text("没有找到这门课程。",color=Muted)}
    }
    PullToRefreshDefaults.Indicator(state=refreshState,isRefreshing=refreshing,modifier=Modifier.align(Alignment.TopCenter),containerColor=Surface,color=Accent)
    }

}
@Composable private fun LessonScreen(model: PracticeModel,open: OpenLesson,shared: SharedTransitionScope,visibility: AnimatedVisibilityScope) {
    val selected by remember(model){derivedStateOf {model.playback.selected}}
    // Restore before the first measure; entry must not reveal the header then scroll away.
    val entrySelected=remember(open.course.id){selected}
    val list=androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex=if(entrySelected==0)0 else entrySelected+1)
    var followedSelected by remember(open.course.id){mutableIntStateOf(entrySelected)}
    val coverEnd=with(LocalDensity.current){110.dp.roundToPx()}
    val headerVisible by remember(list,coverEnd){derivedStateOf {list.firstVisibleItemIndex==0&&list.firstVisibleItemScrollOffset<coverEnd}}
    val reduced=LocalReduced.current
    LaunchedEffect(selected) {
        // Auto-follow only a subsequent sentence change, never the restored entry position.
        if(selected==followedSelected)return@LaunchedEffect
        followedSelected=selected
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
        LazyColumn(state=list,modifier=Modifier.testTag("dialogue-list").semantics {this[ReadingFirstItemKey]=list.firstVisibleItemIndex;this[ReadingFirstOffsetKey]=list.firstVisibleItemScrollOffset},contentPadding=PaddingValues(16.dp,12.dp,16.dp,128.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            item(key="header") {
                Column(Modifier.padding(bottom=18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {GlyphButton("返回课程","back",model::back);Text("课程",fontSize=12.sp,color=Muted)}
                    Row(Modifier.padding(top=10.dp,bottom=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Cover(open.course,model.repository,(if(headerVisible)with(shared){coverModifier(open.course,visibility)} else Modifier).size(76.dp,50.dp),coverBadge(visibility,false))
                        Column(Modifier.weight(1f)) {
                            CourseTitle(open.course,if(headerVisible)with(shared){titleModifier(open.course,visibility)} else Modifier)
                            Text(open.course.label,fontSize=12.sp,color=Muted)
                        }
                        val context=LocalContext.current
                        GlyphButton("打开原视频","external",{runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v=${open.course.id}")))}})
                    }
                }
            }
            items(open.lesson.groups,key={it.id}) {sentence ->
                val active=sentence.id==selected
                val background=key(LocalPracticePalette.current){animateColorAsState(if(active)Tint else Surface,if(reduced)snap() else tween(160),label="sentence-focus").value}
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background)
                    .clickable(role=Role.Button,onClick={model.start(sentence.id)}).semantics {contentDescription="第 ${sentence.id+1} 句";stateDescription=if(active&&model.playback.running&&!model.playback.paused)"正在播放" else if(active)"当前句" else ""}
                    .testTag("sentence-${sentence.id}").padding(16.dp,18.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text("%02d".format(sentence.id+1),fontSize=11.sp,color=if(active)Accent else Muted,modifier=Modifier.padding(top=6.dp))
                        Column(Modifier.weight(1f)) {
                            ReadingText(sentence,active,if(active)model.playback.source else -1.0,reduced)
                            if(model.translation&&sentence.translation.isNotEmpty())Text(sentence.translation,fontSize=13.sp,lineHeight=21.sp,color=Muted,modifier=Modifier.padding(top=10.dp))
                            if(active&&model.cues&&sentence.cues.isNotEmpty())Text(sentence.cues.joinToString(" · "),fontSize=11.sp,lineHeight=18.sp,color=Accent,modifier=Modifier.padding(top=10.dp))
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun ModeRoller(drill: Boolean,onChange: (Boolean)->Unit) {
    val reduced=LocalReduced.current
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    var step by remember {mutableIntStateOf(if(drill)1 else 0)}
    val roll=remember {Animatable(step.toFloat())}
    LaunchedEffect(drill){if((step%2==1)!=drill)step++}
    LaunchedEffect(step,reduced){if(reduced)roll.snapTo(step.toFloat()) else roll.animateTo(step.toFloat(),spring(1f,650f,visibilityThreshold=.002f))}
    val pressure=animateFloatAsState(if(pressed).96f else 1f,if(reduced)snap() else spring(1f,900f),label="mode-pressure")
    val measurer=rememberTextMeasurer()
    val textStyle=TextStyle(fontSize=13.sp,fontWeight=FontWeight.Medium,color=Accent)
    val original=remember(measurer,textStyle){measurer.measure(AnnotatedString("原句"),style=textStyle)}
    val practice=remember(measurer,textStyle){measurer.measure(AnnotatedString("Drill"),style=textStyle)}
    val windowHeight=with(LocalDensity.current){maxOf(original.size.height,practice.size.height).toDp().coerceAtLeast(20.dp)}
    Box(Modifier.width(52.dp).heightIn(min=48.dp)
        .clickable(interactionSource=interaction,indication=null,role=Role.Button,onClick={step++;onChange(!drill)})
        .testTag("mode-toggle").semantics {contentDescription="切换播放模式";stateDescription=if(drill)"Drill" else "原句";this[ModeRollPositionKey]=roll.value},contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(windowHeight).clipToBounds().graphicsLayer {scaleX=pressure.value;scaleY=pressure.value}) {
            val position=roll.value
            val base=kotlin.math.floor(position).toInt()
            val fraction=position-base
            fun label(index: Int,offset: Float,opacity: Float){
                val layout=if(index%2==0)original else practice
                drawText(layout,topLeft=Offset((size.width-layout.size.width)/2,(size.height-layout.size.height)/2+offset),alpha=opacity)
            }
            label(base,fraction*size.height,1f-fraction*.6f)
            label(base+1,(fraction-1f)*size.height,.4f+fraction*.6f)
        }
    }
}
@Composable private fun Transport(model: PracticeModel,settings: ()->Unit,modifier: Modifier) {
    val p=model.playback
    val style=LocalMaterialStyle.current
    val accent=Accent
    val progress=animateFloatAsState(p.taskProgress?:0f,if(LocalReduced.current||p.taskProgress==0f)snap() else tween(45,easing=LinearEasing),label="transport-light")
    Column(modifier.animateContentSize(if(LocalReduced.current)snap() else spring(1f,600f)),horizontalAlignment=Alignment.CenterHorizontally) {
        FrostedSurface(Modifier.testTag("player-surface").semantics {p.taskProgress?.let {progressBarRangeInfo=ProgressBarRangeInfo(it,0f..1f)}},radius=32.dp) {
            if(p.taskProgress!=null)Canvas(Modifier.matchParentSize()){
                val colors=if(style.highContrast)listOf(accent.copy(alpha=.12f),accent.copy(alpha=.12f)) else listOf(accent.copy(alpha=.025f),Color(0xff69b5ff).copy(alpha=.18f))
                drawRect(Brush.horizontalGradient(colors),size=androidx.compose.ui.geometry.Size(size.width*progress.value,size.height))
            }
            Row(Modifier.padding(6.dp,4.dp),verticalAlignment=Alignment.CenterVertically) {
                GlyphButton("上一句","previous",model::previous,enabled=p.selected>0)
                GlyphButton(if(p.running&&!p.paused)"暂停" else "播放",if(p.running&&!p.paused)"pause" else "play",model::toggle)
                GlyphButton("下一句","next",model::next,enabled=p.selected<(model.opened?.lesson?.groups?.lastIndex?:0))
                ModeRoller(model.drill,model::updateDrill)
                GlyphButton("练习设置","more",settings)
            }
        }
        if(p.running&&p.waiting&&model.shadow)Text("跟读 · %.1f 秒".format(p.waitSeconds),fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=5.dp))
    }
}
@Composable private fun SettingsSheet(model: PracticeModel,systemContrast: Boolean,sheet: SheetState,measure: (Float)->Unit,dismiss: ()->Unit,generalOnly: Boolean=false) {
    val scope=rememberCoroutineScope()
    val close: ()->Unit={scope.launch {sheet.hide();dismiss()};Unit}
    var moreExpanded by rememberSaveable {mutableStateOf(false)}
    val moreRotation=animateFloatAsState(if(moreExpanded)90f else 0f,if(LocalReduced.current)snap() else spring(1f,600f),label="more-settings-chevron")
    ModalBottomSheet(onDismissRequest=dismiss,modifier=Modifier.onSizeChanged {measure(it.height.toFloat())}.testTag("settings-sheet"),containerColor=Color.Transparent,scrimColor=if(LocalPracticePalette.current.dark)Color.Black.copy(alpha=.4f) else Color(0xff172539).copy(alpha=.16f),dragHandle=null,sheetState=sheet) {
        FrostedSurface(Modifier.fillMaxWidth(),radius=28.dp,weight=SurfaceWeight.Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=28.dp).padding(top=12.dp,bottom=24.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom=14.dp).size(28.dp,3.dp).clip(RoundedCornerShape(2.dp)).background(Muted.copy(alpha=.3f)))
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically){Text(if(generalOnly)"设置" else "练习设置",fontSize=17.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f));GlyphButton("关闭设置","close",close)}
            if(!generalOnly) {
            if(model.drill) RepeatSetting(model)
            LoopSetting(model)
            Setting("留白跟读",model.shadow,model::updateShadow)
            if(model.shadow){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(1f,1.5f,2f).forEach {factor->Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(model.gap==factor)Tint else Color.Transparent)
                    .selectable(model.gap==factor,interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.RadioButton,onClick={model.updateGap(factor)}).heightIn(min=48.dp).padding(12.dp),contentAlignment=Alignment.Center){Text("${factor}×",fontSize=13.sp,color=if(model.gap==factor)Accent else Muted)}
            }}}
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button,onClick={moreExpanded=!moreExpanded}).testTag("more-settings").semantics {stateDescription=if(moreExpanded)"已展开" else "已收起"},verticalAlignment=Alignment.CenterVertically) {
                Text("更多设置",fontSize=15.sp,modifier=Modifier.weight(1f));Text("›",color=Muted,modifier=Modifier.graphicsLayer {rotationZ=moreRotation.value})
            }
            }
            AnimatedVisibility(generalOnly||moreExpanded,enter=if(LocalReduced.current)EnterTransition.None else expandVertically(spring(1f,600f))+fadeIn(tween(120)),exit=if(LocalReduced.current)ExitTransition.None else shrinkVertically(tween(160))+fadeOut(tween(100))) {
                Column(Modifier.testTag("inline-preferences")) {
                    Setting("中文翻译",model.translation,model::updateTranslation)
                    Setting("发音提示",model.cues,model::updateCues)
                    AppearanceSetting(model,systemContrast)
                }
            }
        }
        }
    }
}
@Composable internal fun Setting(label: String,checked: Boolean,change: (Boolean)->Unit,enabled: Boolean=true) {
    val interaction=remember {MutableInteractionSource()}
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("setting-$label").toggleable(value=checked,interactionSource=interaction,indication=null,enabled=enabled,role=Role.Switch,onValueChange=change),verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=15.sp,modifier=Modifier.weight(1f));Switch(checked,onCheckedChange=null,enabled=enabled,colors=SwitchDefaults.colors(uncheckedTrackColor=LocalPracticePalette.current.track,uncheckedBorderColor=Color.Transparent,uncheckedThumbColor=if(LocalPracticePalette.current.dark)LocalPracticePalette.current.muted else Color.White))
    }
}
@Composable private fun RepeatSetting(model: PracticeModel) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    val reduced=LocalReduced.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button,onClick={expanded=!expanded}).testTag("repeat-setting")
            .semantics {stateDescription="${model.repeatCount} 次"},verticalAlignment=Alignment.CenterVertically) {
            Text("复读次数",fontSize=15.sp,modifier=Modifier.weight(1f))
            Text("${model.repeatCount}×",fontSize=14.sp,color=Accent)
        }
        AnimatedVisibility(expanded,enter=if(reduced)EnterTransition.None else expandVertically(spring(1f,600f))+fadeIn(tween(120)),exit=if(reduced)ExitTransition.None else shrinkVertically(tween(160))+fadeOut(tween(100))) {
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                (2..5).forEach {count ->
                    Box(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if(model.repeatCount==count)Tint else Color.Transparent)
                        .selectable(model.repeatCount==count,interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.RadioButton,onClick={model.updateRepeatCount(count);expanded=false})
                        .testTag("repeat-$count"),contentAlignment=Alignment.Center) {Text("${count}×",fontSize=14.sp,color=if(model.repeatCount==count)Accent else Muted)}
                }
            }
        }
    }
}
@Composable internal fun AppearanceSetting(model: PracticeModel,systemContrast: Boolean) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button,onClick={expanded=!expanded}).testTag("appearance-setting"),verticalAlignment=Alignment.CenterVertically) {
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
    val noticeColor=if(model.messageIsError)LocalPracticePalette.current.error else Accent
    AnimatedVisibility(text!=null,modifier=modifier,enter=if(reduced)EnterTransition.None else fadeIn(tween(140))+slideInVertically(spring(1f,650f)){it/4},exit=if(reduced)ExitTransition.None else fadeOut(tween(160))+slideOutVertically(tween(160)){it/6}) {
        FrostedSurface(Modifier.widthIn(max=300.dp).semantics {liveRegion=if(model.messageIsError)LiveRegionMode.Assertive else LiveRegionMode.Polite}.testTag("notice"),radius=24.dp,weight=SurfaceWeight.Chip) {
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Canvas(Modifier.size(15.dp)) {
                val color=noticeColor
                if(model.messageIsError){drawCircle(color.copy(alpha=.15f));drawLine(color,Offset(size.width/2,size.height*.25f),Offset(size.width/2,size.height*.58f),1.4.dp.toPx(),StrokeCap.Round);drawCircle(color,.8.dp.toPx(),Offset(size.width/2,size.height*.76f))}
                else {listOf(.38f,.72f,.52f).forEachIndexed {i,h ->val x=size.width*(.22f+.28f*i);drawLine(color,Offset(x,size.height*(1-h)/2),Offset(x,size.height*(1+h)/2),1.6.dp.toPx(),StrokeCap.Round)}}
            }
            Text(text?:last,fontSize=12.sp,lineHeight=18.sp,color=Ink,maxLines=3,modifier=Modifier.weight(1f,fill=false))
        }
        }
    }
}
@Composable internal fun GlyphButton(label: String,glyph: String,click: ()->Unit,enabled: Boolean=true) {
    val interaction=remember {MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
    val ink=Ink;val muted=Muted;val accent=Accent
    val highlight=if(LocalPracticePalette.current.dark)Color.White.copy(alpha=.07f) else Color.White.copy(alpha=.75f)
    val scale by animateFloatAsState(if(pressed).97f else 1f,if(LocalReduced.current)snap() else spring(1f,900f),label="control-pressure")
    IconButton(onClick=click,enabled=enabled,interactionSource=interaction,modifier=Modifier.size(48.dp).drawBehind {
        if(glyph=="play"||glyph=="pause")drawCircle(Brush.verticalGradient(listOf(highlight,accent.copy(alpha=if(pressed).10f else .025f))),radius=20.dp.toPx()*scale)
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
                "history"->{drawRoundRect(color,Offset(4*s,4*s),androidx.compose.ui.geometry.Size(16*s,16*s),androidx.compose.ui.geometry.CornerRadius(3*s),style=Stroke(stroke));listOf(8f,12f,16f).forEach {x->listOf(8f,12f,16f).forEach {y->drawCircle(color,1f*s,Offset(x*s,y*s))}}}
                "refresh"->{drawArc(color,35f,280f,false,Offset(4*s,4*s),androidx.compose.ui.geometry.Size(16*s,16*s),style=Stroke(stroke,cap=StrokeCap.Round));path(16f,3f,20f,7f,15f,8f)}
                "external"->{path(9f,5f,5f,5f,5f,19f,19f,19f,19f,15f);path(14f,5f,19f,5f,19f,10f);line(19f,5f,11f,13f)}
                else->listOf(5f,12f,19f).forEach {drawCircle(color,1.4f*s,Offset(it*s,12f*s))}
            }
        }
        }
    }
}

@Composable private fun LoopSetting(model: PracticeModel) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("loop-setting")
            .clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button,onClick={expanded=!expanded})
            .semantics {stateDescription=model.loopMode.label},verticalAlignment=Alignment.CenterVertically) {
            Text("循环",fontSize=15.sp,modifier=Modifier.weight(1f))
            Text(model.loopMode.label,fontSize=14.sp,color=Accent);Text("  ›",color=Muted)
        }
        AnimatedVisibility(expanded,enter=if(LocalReduced.current)EnterTransition.None else expandVertically(spring(1f,600f))+fadeIn(tween(120)),exit=if(LocalReduced.current)ExitTransition.None else shrinkVertically(tween(160))+fadeOut(tween(100))) {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(bottom=8.dp)) {
                PracticeLoop.entries.forEach {mode->
                    Box(Modifier.weight(1f).heightIn(min=48.dp).testTag("loop-${mode.name}")
                        .selectable(model.loopMode==mode,interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.RadioButton,onClick={model.updateLoopMode(mode);expanded=false}),contentAlignment=Alignment.Center) {
                        Text(mode.label,fontSize=14.sp,color=if(model.loopMode==mode)Accent else Muted)
                    }
                }
            }
        }
    }
}
