package com.example.animator.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.animator.App
import com.example.animator.export.ExportStatus
import com.example.animator.state.AnimState
import com.example.animator.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Entry composable: loads the last project (or starts a new one), then shows the main screen. */
@Composable
fun AnimatorRoot() {
    val ctx = LocalContext.current.applicationContext
    val st = App.state

    LaunchedEffect(Unit) {
        if (App.state == null) {
            val loaded = withContext(Dispatchers.IO) { ProjectStore.loadLast(ctx) ?: AnimState() }
            if (App.state == null) App.state = loaded
        }
    }

    if (st == null) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) { Text("Loading…", color = Color.White) }
    } else {
        AnimatorScreen(st)
    }
}

/** Main screen: portrait or landscape layout, playback loop and auto-save loop. */
@Composable
fun AnimatorScreen(state: AnimState) {
    val ctx = LocalContext.current.applicationContext
    val listState = rememberLazyListState()

    // Playback loop
    LaunchedEffect(state, state.isPlaying, state.fps) {
        while (state.isPlaying) {
            delay(1000L / state.fps)
            if (state.isPlaying) state.nextFrame()
        }
    }

    // Keep the selected frame visible in the timeline
    LaunchedEffect(state, state.currentIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == state.currentIndex }
        if (!visible && state.currentIndex < state.frames.size) {
            listState.animateScrollToItem(state.currentIndex)
        }
    }

    // Auto-save every few seconds (only writes what changed)
    LaunchedEffect(state) {
        while (true) {
            delay(3000)
            ProjectStore.saveIfNeeded(ctx, state)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(8.dp)
        ) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoxWithConstraints(Modifier.weight(1.35f).fillMaxHeight()) {
                        val availH = (maxHeight - 150.dp).coerceAtLeast(120.dp)
                        val canvasW = minOf(maxWidth, availH * (state.width.toFloat() / state.height.toFloat()))
                        Column(Modifier.fillMaxSize()) {
                            PreviewArea(state, listState, Modifier.width(canvasW))
                        }
                    }
                    ControlPanel(state, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PreviewArea(state, listState, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    ControlPanel(state, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        if (ExportStatus.running) ExportOverlay()
    }
}

/** Blocks touches and shows progress while an export runs. */
@Composable
fun ExportOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Exporting…  ${(ExportStatus.progress * 100).toInt()}%", color = Color.White)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { ExportStatus.progress },
                modifier = Modifier.width(240.dp)
            )
        }
    }
}