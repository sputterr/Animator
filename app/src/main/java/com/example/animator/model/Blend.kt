package com.example.animator.model

import android.graphics.PorterDuff
import androidx.compose.ui.graphics.BlendMode

/**
 * Layer blend modes. The constant NAMES are stored in saved projects: never rename them.
 */
enum class Blend(val label: String, val compose: BlendMode, val porterDuff: PorterDuff.Mode?) {
    NORMAL("Normal", BlendMode.SrcOver, null),
    MULTIPLY("Multiply", BlendMode.Multiply, PorterDuff.Mode.MULTIPLY),
    SCREEN("Screen", BlendMode.Screen, PorterDuff.Mode.SCREEN),
    OVERLAY("Overlay", BlendMode.Overlay, PorterDuff.Mode.OVERLAY),
    DARKEN("Darken", BlendMode.Darken, PorterDuff.Mode.DARKEN),
    LIGHTEN("Lighten", BlendMode.Lighten, PorterDuff.Mode.LIGHTEN),
    ADD("Add", BlendMode.Plus, PorterDuff.Mode.ADD)
}