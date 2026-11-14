package com.example.animator.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animator.model.Tool
import com.example.animator.state.AnimState
import kotlin.math.roundToInt

/** The scrolling control panel: tool row plus one collapsible section per feature area. */
@Composable
fun ControlPanel(state: AnimState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        ToolRow(state)
        FramesPanel(state)
        LayersPanel(state)
        ColorPanel(state)
        BrushPanel(state)
        FillPanel(state)
        OnionPanel(state)
        ProjectPanel(state)
        ExportPanel(state)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToolRow(state: AnimState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ToolButton("Brush", Modifier.weight(1f), selected = state.tool == Tool.BRUSH, fontSize = 12.sp) { state.tool = Tool.BRUSH }
        ToolButton("Eraser", Modifier.weight(1f), selected = state.tool == Tool.ERASER, fontSize = 12.sp) { state.tool = Tool.ERASER }
        ToolButton("Fill", Modifier.weight(1f), selected = state.tool == Tool.FILL, fontSize = 12.sp) { state.tool = Tool.FILL }
        ToolButton("Undo", Modifier.weight(1f), enabled = state.undoCount > 0, fontSize = 12.sp) { state.undo() }
        ToolButton("Redo", Modifier.weight(1f), enabled = state.redoCount > 0, fontSize = 12.sp) { state.redo() }
        ToolButton("Clear", Modifier.weight(1f), fontSize = 12.sp) { state.clearCurrentLayer() }
    }
}

@Composable
private fun FramesPanel(state: AnimState) {
    Section("Frames & playback") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton("+ New", Modifier.weight(1f), enabled = state.frames.size < AnimState.MAX_FRAMES) { state.addFrame() }
            ToolButton("Duplicate", Modifier.weight(1f), enabled = state.frames.size < AnimState.MAX_FRAMES) { state.duplicateFrame() }
            ToolButton("Delete", Modifier.weight(1f), enabled = state.frames.size > 1) { state.deleteFrame() }
        }
        LabeledSlider("FPS: ${state.fps}", state.fps.toFloat(), 1f..30f, steps = 28) {
            state.fps = it.roundToInt()
        }
    }
}

@Composable
private fun BrushPanel(state: AnimState) {
    Section("Brush") {
        LabeledSlider("Brush size: ${state.brushSize.toInt()}", state.brushSize, 1f..60f) {
            state.brushSize = it
        }
        LabeledSlider("Smoothing: ${(state.smoothing * 100).toInt()}%", state.smoothing, 0f..1f) {
            state.smoothing = it
        }
    }
}

@Composable
private fun FillPanel(state: AnimState) {
    Section("Bucket fill", startOpen = false) {
        ToolButton(
            if (state.fillMerged) "Fill reads: all visible layers" else "Fill reads: current layer only",
            Modifier.fillMaxWidth(),
            selected = state.fillMerged
        ) { state.fillMerged = !state.fillMerged }
        LabeledSlider("Tolerance: ${state.fillTolerance}", state.fillTolerance.toFloat(), 0f..100f) {
            state.fillTolerance = it.roundToInt()
        }
        LabeledSlider(
            "Gap closing: ${state.fillGap} px  (seals gaps up to ~${state.fillGap * 2} px)",
            state.fillGap.toFloat(), 0f..8f, steps = 7
        ) { state.fillGap = it.roundToInt() }
        Text(
            "Tip: draw on \"Line art\", select \"Color\", pick Fill, tap inside a shape.",
            color = Color(0xFF888888), fontSize = 12.sp
        )
    }
}

@Composable
private fun OnionPanel(state: AnimState) {
    Section("Onion skin", startOpen = false) {
        ToolButton(
            if (state.onionEnabled) "Onion skin: ON  (red = before, green = after)" else "Onion skin: OFF",
            Modifier.fillMaxWidth(),
            selected = state.onionEnabled
        ) { state.onionEnabled = !state.onionEnabled }
        LabeledSlider(
            "Onion opacity: ${(state.onionOpacity * 100).toInt()}%",
            state.onionOpacity, 0.1f..0.6f
        ) { state.onionOpacity = it }
        LabeledSlider(
            "Onion range: ${state.onionRange} frame(s) each side",
            state.onionRange.toFloat(), 1f..3f, steps = 1
        ) { state.onionRange = it.roundToInt() }
    }
}