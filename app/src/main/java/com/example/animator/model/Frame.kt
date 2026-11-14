package com.example.animator.model

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** One animation frame: a lazily created cel per layer id. */
class Frame(val id: Long, val width: Int, val height: Int) {
    val cels = HashMap<Long, Cel>()

    /** Bumped by [touch] so Compose redraws anything that reads it. */
    var version by mutableIntStateOf(0)

    fun touch() { version++ }

    fun cel(layerId: Long): Cel = cels.getOrPut(layerId) {
        Cel(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }
}