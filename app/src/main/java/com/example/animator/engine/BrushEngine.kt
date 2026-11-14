package com.example.animator.engine

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.example.animator.model.Cel

/**
 * Paints one smoothed stroke at a time onto a [Cel].
 * It knows nothing about frames, layers, undo or the UI: the caller decides what may be drawn on.
 *
 * Smoothing: the brush point follows the finger with a lag (the "stabilizer"), and the line is
 * drawn as quadratic curves between midpoints.
 */
class BrushEngine {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    private var cel: Cel? = null
    private var smX = 0f
    private var smY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var midX = 0f
    private var midY = 0f

    /** True between [begin] and [end]. */
    val isActive: Boolean get() = cel != null

    fun begin(target: Cel, x: Float, y: Float, argb: Int, size: Float, erase: Boolean) {
        strokePaint.strokeWidth = size
        strokePaint.color = argb
        dotPaint.color = argb
        val mode = if (erase) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
        strokePaint.xfermode = mode
        dotPaint.xfermode = mode

        cel = target
        smX = x; smY = y
        lastX = x; lastY = y
        midX = x; midY = y
        target.canvas.drawCircle(x, y, size / 2f, dotPaint)
    }

    /** @param smoothing 0..1, higher = smoother but laggier */
    fun move(x: Float, y: Float, smoothing: Float) {
        val target = cel ?: return
        val follow = 1f - smoothing * 0.9f
        smX += (x - smX) * follow
        smY += (y - smY) * follow
        val newMidX = (lastX + smX) / 2f
        val newMidY = (lastY + smY) / 2f
        path.reset()
        path.moveTo(midX, midY)
        path.quadTo(lastX, lastY, newMidX, newMidY)
        target.canvas.drawPath(path, strokePaint)
        lastX = smX; lastY = smY
        midX = newMidX; midY = newMidY
    }

    /** Finishes the line and returns the cel that was painted on (null if no stroke was active). */
    fun end(): Cel? {
        val target = cel ?: return null
        path.reset()
        path.moveTo(midX, midY)
        path.lineTo(lastX, lastY)
        target.canvas.drawPath(path, strokePaint)
        cel = null
        return target
    }
}