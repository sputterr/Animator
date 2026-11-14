package com.example.animator.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A layer exists across all frames. Each frame stores its own image (cel) per layer id. */
class Layer(val id: Long, initialName: String) {
    var name by mutableStateOf(initialName)
    var visible by mutableStateOf(true)
    var locked by mutableStateOf(false)
    var opacity by mutableFloatStateOf(1f)
    var blend by mutableStateOf(Blend.NORMAL)
}