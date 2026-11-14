package com.example.animator.export

import android.graphics.Bitmap
import com.example.animator.model.Frame
import com.example.animator.state.AnimState

/**
 * Renders one frame (all visible layers) at the export size.
 * The caller owns the returned bitmap and should recycle it.
 */
internal fun frameBitmap(state: AnimState, frame: Frame, w: Int, h: Int, paper: Boolean): Bitmap {
    val src = state.renderComposite(frame, paper)
    if (src.width == w && src.height == h) return src
    val scaled = Bitmap.createScaledBitmap(src, w, h, true)
    src.recycle()
    return scaled
}