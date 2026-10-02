package com.rachelsenglish.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.*
import kotlin.math.floor

private data class ReadingStroke(val start: Int,val end: Int,val progress: State<Float>,val opacity: State<Float>)

@Composable fun ReadingText(sentence: Sentence,active: Boolean,source: Double,reduced: Boolean) {
    val contrast=LocalMaterialStyle.current.highContrast
    var layout by remember(sentence.id) {mutableStateOf<TextLayoutResult?>(null)}
    val strokes=mutableListOf<ReadingStroke>()
    val text=buildAnnotatedString {
        sentence.phrases.forEachIndexed {i,p ->
            if(i>0)append(" ")
            val start=length
            val speaking=active&&source>=p.start&&source<p.end
            val color by animateColorAsState(if(speaking)Color(0xff1765c1) else if(contrast)Color(0xff101721) else if(active)Color(0xff25283b) else Color(0xff6d7288),if(reduced)snap() else tween(140),label="voice-color-$i")
            val retained=remember(sentence.id,i){floatArrayOf(0f)}
            if(active&&source>=0)retained[0]=phraseReadProgress(p.start,p.end,source)
            val progress=animateFloatAsState(retained[0],if(reduced)snap() else tween(45,easing=LinearEasing),label="voice-trace-$i")
            val opacity=animateFloatAsState(if(speaking)1f else 0f,if(reduced)snap() else tween(180),label="voice-release-$i")
            withStyle(SpanStyle(color=color)){append(p.text)}
            strokes.add(ReadingStroke(start,length,progress,opacity))
        }
    }
    Text(text,fontSize=21.sp,lineHeight=34.sp,onTextLayout={layout=it},modifier=Modifier.drawWithContent {
        drawContent()
        val measured=layout?:return@drawWithContent
        strokes.forEach {trace ->
            if(trace.opacity.value<.01f||trace.progress.value<=0f||trace.end<=trace.start)return@forEach
            val read=trace.start+(trace.end-trace.start)*trace.progress.value
            val finalCharacter=floor(read).toInt().coerceIn(trace.start,trace.end-1)
            val firstLine=measured.getLineForOffset(trace.start)
            val lastLine=measured.getLineForOffset(finalCharacter)
            for(line in firstLine..lastLine){
                val from=maxOf(trace.start,measured.getLineStart(line))
                val to=minOf(trace.end,measured.getLineEnd(line,visibleEnd=true))
                if(to<=from)continue
                val left=measured.getBoundingBox(from).left
                val right=if(read>=to)measured.getBoundingBox(to-1).right else {
                    val box=measured.getBoundingBox(finalCharacter)
                    box.left+box.width*(read-finalCharacter).coerceIn(0f,1f)
                }
                val y=measured.getLineBaseline(line)+4.dp.toPx()
                if(right>left)drawLine(Color(0xff3483d4).copy(alpha=.72f*trace.opacity.value),Offset(left,y),Offset(right,y),(if(contrast)1.8.dp else 1.35.dp).toPx(),StrokeCap.Round)
            }
        }
    })
}
