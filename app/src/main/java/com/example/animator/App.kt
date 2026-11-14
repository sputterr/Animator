package com.example.animator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.animator.state.AnimState

/** App-wide holder that survives screen rotation: the project currently open. */
object App {
    var state by mutableStateOf<AnimState?>(null)
}