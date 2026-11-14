package com.example.animator.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animator.model.Blend
import com.example.animator.model.Layer

// Reusable building blocks: styled button, slider with label, collapsible section,
// colour swatches and one row of the layer list.

internal val Gray1 = Color(0xFF1A1A1A)
internal val Accent = Color(0xFF00ACC1)

@Composable
fun ToolButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    fontSize: TextUnit = 13.sp,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF00838F) else Gray1,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF0B0B0B),
            disabledContentColor = Color(0xFF555555)
        )
    ) { Text(text, maxLines = 1, fontSize = fontSize) }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Text(label, color = Color(0xFFBBBBBB), fontSize = 13.sp)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Accent,
            activeTrackColor = Accent,
            inactiveTrackColor = Color(0xFF2A2A2A),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
    )
}

@Composable
fun Section(title: String, startOpen: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            (if (open) "▼  " else "▶  ") + title,
            color = Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 8.dp)
        )
        if (open) content()
    }
}

@Composable
fun SwatchRow(colors: List<Color>, current: Color, onPick: (Color) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { c ->
            val selected = c == current
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) Color.Cyan else Color(0xFF555555),
                        shape = CircleShape
                    )
                    .clickable { onPick(c) }
            )
        }
    }
}

@Composable
fun LayerRow(layer: Layer, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val extra = if (layer.blend != Blend.NORMAL || layer.opacity < 0.99f)
            "  (${layer.blend.label}, ${(layer.opacity * 100).toInt()}%)" else ""
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Color(0xFF00838F) else Gray1)
                .clickable(onClick = onSelect)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(layer.name + extra, color = Color.White, maxLines = 1, fontSize = 13.sp)
        }
        ToolButton(
            if (layer.visible) "Visible" else "Hidden",
            Modifier.width(78.dp),
            selected = layer.visible
        ) { layer.visible = !layer.visible }
        ToolButton(
            if (layer.locked) "Locked" else "Lock",
            Modifier.width(78.dp),
            selected = layer.locked
        ) { layer.locked = !layer.locked }
    }
}