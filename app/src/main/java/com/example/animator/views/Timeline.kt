package com.example.animator.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animator.model.Frame
import com.example.animator.model.Layer
import com.example.animator.state.AnimState

// The preview area under the canvas: playback buttons and the frame timeline.

@Composable
fun FrameThumb(frame: Frame, layers: List<Layer>, index: Int, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(16f / 9f)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.Cyan else Color(0xFF444444)
                )
                .clickable(onClick = onClick)
        ) {
            val dst = IntSize(size.width.toInt(), size.height.toInt())
            drawRect(Color.White)
            drawComposite(frame, layers, dst)
        }
        Text(
            "${index + 1}",
            color = if (selected) Color.Cyan else Color(0xFF999999),
            fontSize = 12.sp
        )
    }
}

@Composable
fun PreviewArea(state: AnimState, listState: LazyListState, canvasModifier: Modifier) {
    Column(Modifier.fillMaxWidth()) {
        DrawingCanvas(state, canvasModifier)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton("Prev", Modifier.weight(1f)) { state.isPlaying = false; state.prevFrame() }
            ToolButton(
                if (state.isPlaying) "Stop" else "Play",
                Modifier.weight(1.4f),
                selected = state.isPlaying
            ) { state.isPlaying = !state.isPlaying }
            ToolButton("Next", Modifier.weight(1f)) { state.isPlaying = false; state.nextFrame() }
            Text(
                "${state.currentIndex + 1} / ${state.frames.size}",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(state.frames, key = { _, f -> f.id }) { i, f ->
                FrameThumb(
                    frame = f,
                    layers = state.layers,
                    index = i,
                    selected = i == state.currentIndex,
                    onClick = { state.isPlaying = false; state.currentIndex = i }
                )
            }
        }
    }
}