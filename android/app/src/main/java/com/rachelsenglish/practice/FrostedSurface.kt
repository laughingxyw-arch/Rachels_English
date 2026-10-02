package com.rachelsenglish.practice

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*

class BackdropSource(val layer: GraphicsLayer) {var origin by mutableStateOf(Offset.Zero)}
val LocalBackdropSource=staticCompositionLocalOf<BackdropSource?> {null}
val FrostedMaterialKey=SemanticsPropertyKey<Boolean>("FrostedMaterial")
@Composable fun rememberBackdropSource(): BackdropSource {
    val layer=rememberGraphicsLayer();return remember(layer){BackdropSource(layer)}
}
@Composable fun Modifier.captureBackdrop(source: BackdropSource): Modifier {
    val view=LocalView.current
    return onGloballyPositioned {val screen=IntArray(2);view.getLocationOnScreen(screen);source.origin=it.positionInWindow()+Offset(screen[0].toFloat(),screen[1].toFloat())}
        .drawWithContent {source.layer.record {this@drawWithContent.drawContent()};drawLayer(source.layer)}
}
enum class SurfaceWeight {Chip,Player,Panel}
@Composable fun FrostedSurface(modifier: Modifier=Modifier,radius: Dp=28.dp,weight: SurfaceWeight=SurfaceWeight.Player,content: @Composable BoxScope.()->Unit) {
    val palette=LocalPracticePalette.current;val dark=palette.dark
    val style=LocalMaterialStyle.current;val source=LocalBackdropSource.current
    val frosted=style.frosted&&source!=null
    val shape=RoundedCornerShape(radius)
    val tint=if(style.highContrast)palette.surface else if(dark)when(weight){SurfaceWeight.Chip->Color(0xff222a36);SurfaceWeight.Player->Color(0xff1d2939);SurfaceWeight.Panel->Color(0xff202833)} else when(weight){SurfaceWeight.Chip->Color(0xfffafcff);SurfaceWeight.Player->Color(0xfff5f9ff);SurfaceWeight.Panel->Color(0xfff8faff)}
    val opacity=if(frosted)when(weight){SurfaceWeight.Chip->.82f;SurfaceWeight.Player->.76f;SurfaceWeight.Panel->.9f} else 1f
    // Only this small surface has a filtered buffer. Never blur the buttons or text.
    val material=if(frosted)Modifier.sampleBackdrop(source!!,if(weight==SurfaceWeight.Panel)22.dp else 16.dp) else Modifier
    Box(modifier.shadow(if(weight==SurfaceWeight.Chip)8.dp else if(weight==SurfaceWeight.Panel)24.dp else 16.dp,shape,
        ambientColor=if(dark)Color.Black.copy(alpha=.22f) else Color(0xff253852).copy(alpha=.07f),spotColor=if(dark)Color.Black.copy(alpha=.32f) else Color(0xff253852).copy(alpha=.13f))
        .clip(shape).then(material).background(tint.copy(alpha=opacity))
        .drawBehind {
            if(!style.highContrast){
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(dark).035f else .38f),Color.Transparent,Color(0xff42698f).copy(alpha=.025f))))
            }
        }.border(if(style.highContrast)1.25.dp else .75.dp,if(style.highContrast)palette.muted else Color.White.copy(alpha=if(dark).12f else .86f),shape)
        .semantics {this[FrostedMaterialKey]=frosted},content=content)
}
@Composable private fun Modifier.sampleBackdrop(source: BackdropSource,blur: Dp): Modifier {
    val view=LocalView.current
    var origin by remember {mutableStateOf(Offset.Zero)}
    val filtered=rememberGraphicsLayer()
    val radius=with(androidx.compose.ui.platform.LocalDensity.current){blur.toPx()}
    val effect=remember(radius){if(Build.VERSION.SDK_INT>=31)RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP).asComposeRenderEffect() else null}
    return onGloballyPositioned {val screen=IntArray(2);view.getLocationOnScreen(screen);origin=it.positionInWindow()+Offset(screen[0].toFloat(),screen[1].toFloat())}
        .drawWithContent {
            if(source.layer.size.width>0&&source.layer.size.height>0){
                filtered.renderEffect=effect
                filtered.record {
                    val offset=source.origin-origin
                    translate(offset.x,offset.y){drawLayer(source.layer)}
                }
                drawLayer(filtered)
            }
            drawContent()
        }
}
