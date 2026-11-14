package com.example.animator.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animator.model.Frame
import com.example.animator.model.Layer
import com.example.animator.model.Tool
import com.example.animator.state.AnimState

// The drawing surface: draws the current frame (plus onion skin) and turns touches into
// AnimState calls. Also holds the DrawScope helpers used by the thumbnails.

fun DrawScope.drawComposite(frame: Frame, layers: List<Layer>, dst: IntSize) {
    @Suppress("UNUSED_VARIABLE")
    val redrawTrigger = frame.version
    for (layer in layers) {
        if (!layer.visible) continue
        val cel = frame.cels[layer.id] ?: continue
        drawImage(
            image = cel.bitmap.asImageBitmap(),
            dstSize = dst,
            alpha = layer.opacity,
            blendMode = layer.blend.compose
        )
    }
}

fun DrawScope.drawOnion(frame: Frame, layers: List<Layer>, dst: IntSize, tint: Color, alpha: Float) {
    @Suppress("UNUSED_VARIABLE")
    val redrawTrigger = frame.version
    for (layer in layers) {
        if (!layer.visible) continue
        val cel = frame.cels[layer.id] ?: continue
        drawImage(
            image = cel.bitmap.asImageBitmap(),
            dstSize = dst,
            alpha = alpha * layer.opacity,
            colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
        )
    }
}

@Composable
fun DrawingCanvas(state: AnimState, modifier: Modifier = Modifier.fillMaxWidth()) {
    val cl = state.currentLayer
    val blockMsg = when {
        state.isPlaying -> null
        !cl.visible -> "\"${cl.name}\" is hidden"
        cl.locked -> "\"${cl.name}\" is locked"
        else -> null
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(state.width.toFloat() / state.height.toFloat())
                .border(1.dp, Color(0xFF333333))
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (state.isPlaying) return@awaitEachGesture
                        down.consume()
                        val scale = state.width.toFloat() / size.width.toFloat()

                        if (state.tool == Tool.FILL) {
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                state.bucketFill(
                                    (down.position.x * scale).toInt(),
                                    (down.position.y * scale).toInt()
                                )
                            }
                            return@awaitEachGesture
                        }

                        state.startStroke(down.position.x * scale, down.position.y * scale)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            state.moveStroke(change.position.x * scale, change.position.y * scale)
                            change.consume()
                        }
                        state.endStroke()
                    }
                }
        ) {
            val dst = IntSize(size.width.toInt(), size.height.toInt())
            drawRect(Color.White)

            val cur = state.currentIndex.coerceIn(0, state.frames.size - 1)

            if (state.onionEnabled && !state.isPlaying) {
                for (d in state.onionRange downTo 1) {
                    val a = state.onionOpacity / d
                    val prev = cur - d
                    if (prev >= 0) {
                        drawOnion(state.frames[prev], state.layers, dst, Color(0xFFFF5252), a)
                    }
                    val next = cur + d
                    if (next < state.frames.size) {
                        drawOnion(state.frames[next], state.layers, dst, Color(0xFF69F0AE), a)
                    }
                }
            }

            drawComposite(state.frames[cur], state.layers, dst)
        }

        if (blockMsg != null) {
            Text(
                blockMsg,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(6.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}