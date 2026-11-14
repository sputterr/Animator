package com.example.animator.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animator.state.AnimState

private val presets = listOf(
    Color.Black, Color(0xFF555555), Color(0xFFAAAAAA), Color.White,
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFEC407A),
    Color(0xFF6D4C41), Color(0xFFF1C27D), Color(0xFFC68642)
)

/** Current colour preview, HSV sliders, preset / saved palette and recent colours. */
@Composable
fun ColorPanel(state: AnimState) {
    Section("Color") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(state.color)
                    .border(2.dp, Color.White, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            ToolButton("Save to palette", Modifier.weight(1f)) { state.saveCurrentColor() }
        }
        LabeledSlider("Hue: ${state.hue.toInt()}", state.hue, 0f..360f) {
            state.setHsv(it, state.sat, state.bright)
        }
        LabeledSlider("Saturation: ${(state.sat * 100).toInt()}%", state.sat, 0f..1f) {
            state.setHsv(state.hue, it, state.bright)
        }
        LabeledSlider("Brightness: ${(state.bright * 100).toInt()}%", state.bright, 0f..1f) {
            state.setHsv(state.hue, state.sat, it)
        }
        Text("Palette", color = Color(0xFFBBBBBB), fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        SwatchRow(presets, state.color) { state.pickColor(it) }
        if (state.savedColors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Saved", color = Color(0xFFBBBBBB), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            SwatchRow(state.savedColors, state.color) { state.pickColor(it) }
        }
        if (state.colorHistory.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Recent", color = Color(0xFFBBBBBB), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            SwatchRow(state.colorHistory, state.color) { state.pickColor(it) }
        }
    }
}