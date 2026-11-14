package com.example.animator.model

import android.graphics.Bitmap
import java.util.UUID
import android.graphics.Canvas as AndroidCanvas

/**
 * One image of one layer in one frame.
 * [fileName] is the PNG name used on disk; [dirty] means "changed since the last save".
 */
class Cel(val bitmap: Bitmap, val fileName: String = "c_${UUID.randomUUID()}.png") {
    val canvas = AndroidCanvas(bitmap)
    var dirty = true
}