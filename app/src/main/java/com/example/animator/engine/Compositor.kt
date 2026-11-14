package com.example.animator.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuffXfermode
import com.example.animator.model.Frame
import com.example.animator.model.Layer
import kotlin.math.roundToInt

/**
 * Flattens the visible layers of one frame into a single bitmap.
 * Used by the bucket fill (to read what is on screen) and by every exporter.
 */
object Compositor {
    fun render(
        frame: Frame,
        layers: List<Layer>,
        width: Int,
        height: Int,
        withPaper: Boolean = true
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (withPaper) c.drawColor(Color.WHITE)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        for (layer in layers) {
            if (!layer.visible) continue
            val cel = frame.cels[layer.id] ?: continue
            p.alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            p.xfermode = layer.blend.porterDuff?.let { PorterDuffXfermode(it) }
            c.drawBitmap(cel.bitmap, 0f, 0f, p)
        }
        return out
    }
}