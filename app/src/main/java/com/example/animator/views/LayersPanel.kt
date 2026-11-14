package com.example.animator.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.animator.model.Blend
import com.example.animator.state.AnimState

/** Layer list (top layer first), layer buttons, opacity and blend mode of the selected layer. */
@Composable
fun LayersPanel(state: AnimState) {
    Section("Layers") {
        state.layers.indices.reversed().forEach { i ->
            LayerRow(state.layers[i], i == state.currentLayerIndex) { state.currentLayerIndex = i }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton("+ Layer", Modifier.weight(1f), enabled = state.layers.size < AnimState.MAX_LAYERS) { state.addLayer() }
            ToolButton("Delete", Modifier.weight(1f), enabled = state.layers.size > 1) { state.deleteLayer() }
            ToolButton("Up", Modifier.weight(1f)) { state.moveLayerUp() }
            ToolButton("Down", Modifier.weight(1f)) { state.moveLayerDown() }
        }
        val cl = state.currentLayer
        LabeledSlider("Layer opacity: ${(cl.opacity * 100).toInt()}%", cl.opacity, 0f..1f) {
            cl.opacity = it
        }
        ToolButton(
            "Blend mode: ${cl.blend.label}  (tap to change)",
            Modifier.fillMaxWidth()
        ) {
            val all = Blend.values()
            cl.blend = all[(cl.blend.ordinal + 1) % all.size]
        }
    }
}