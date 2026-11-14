package com.example.animator.state

import android.graphics.Bitmap
import android.graphics.PorterDuff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.animator.engine.BrushEngine
import com.example.animator.engine.BucketFill
import com.example.animator.engine.Compositor
import com.example.animator.engine.UndoHistory
import com.example.animator.model.Frame
import com.example.animator.model.Layer
import com.example.animator.model.Tool

class AnimState(val width: Int = 960, val height: Int = 540) {
    companion object {
        const val MAX_FRAMES = 60
        const val MAX_LAYERS = 4
        const val MAX_UNDO = 20
        const val MAX_HISTORY = 12
        const val MAX_SAVED = 20
    }

    var projectName by mutableStateOf("Untitled")
    var lastSavedMeta = ""

    var nextFrameId = 0L
    var nextLayerId = 0L
    var layerCounter = 2

    val layers = mutableStateListOf(
        Layer(nextLayerId++, "Color"),
        Layer(nextLayerId++, "Line art")
    )
    val frames = mutableStateListOf(Frame(nextFrameId++, width, height))

    var currentIndex by mutableIntStateOf(0)
    var currentLayerIndex by mutableIntStateOf(1)

    var isPlaying by mutableStateOf(false)
    var fps by mutableIntStateOf(12)

    var onionEnabled by mutableStateOf(true)
    var onionOpacity by mutableFloatStateOf(0.3f)
    var onionRange by mutableIntStateOf(1)

    var tool by mutableStateOf(Tool.BRUSH)
    var brushSize by mutableFloatStateOf(6f)
    var smoothing by mutableFloatStateOf(0.5f)

    var color by mutableStateOf(Color.Black)
    var hue by mutableFloatStateOf(0f)
    var sat by mutableFloatStateOf(0f)
    var bright by mutableFloatStateOf(0f)
    val colorHistory = mutableStateListOf<Color>()
    val savedColors = mutableStateListOf<Color>()

    var fillMerged by mutableStateOf(true)
    var fillTolerance by mutableIntStateOf(48)
    var fillGap by mutableIntStateOf(2)

    var undoCount by mutableIntStateOf(0)
    var redoCount by mutableIntStateOf(0)

    private val history = UndoHistory(MAX_UNDO)

    val currentFrame: Frame
        get() = frames[currentIndex.coerceIn(0, frames.size - 1)]
    val currentLayer: Layer
        get() = layers[currentLayerIndex.coerceIn(0, layers.size - 1)]

    // ---------------- loading / saving support ----------------
    val isStroking: Boolean get() = brush.isActive

    fun markAllDirty() {
        frames.forEach { f -> f.cels.values.forEach { it.dirty = true } }
    }

    fun installLoaded(
        newLayers: List<Layer>, newFrames: List<Frame>,
        frameIdx: Int, layerIdx: Int, newFps: Int, saved: List<Color>,
        nf: Long, nl: Long, counter: Int
    ) {
        layers.clear(); layers.addAll(newLayers)
        frames.clear(); frames.addAll(newFrames)
        currentIndex = frameIdx.coerceIn(0, frames.size - 1)
        currentLayerIndex = layerIdx.coerceIn(0, layers.size - 1)
        fps = newFps.coerceIn(1, 30)
        savedColors.clear(); savedColors.addAll(saved)
        nextFrameId = maxOf(nf, (newFrames.maxOfOrNull { it.id } ?: -1L) + 1)
        nextLayerId = maxOf(nl, (newLayers.maxOfOrNull { it.id } ?: -1L) + 1)
        layerCounter = counter
        history.clear()
        updateCounts()
    }

    // ---------------- color ----------------
    fun pickColor(c: Color) {
        color = c
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hue = hsv[0]; sat = hsv[1]; bright = hsv[2]
    }

    fun setHsv(h: Float, s: Float, v: Float) {
        hue = h; sat = s; bright = v
        color = Color.hsv(h, s, v)
    }

    private fun rememberColor() {
        if (colorHistory.firstOrNull() == color) return
        colorHistory.remove(color)
        colorHistory.add(0, color)
        while (colorHistory.size > MAX_HISTORY) colorHistory.removeAt(colorHistory.size - 1)
    }

    fun saveCurrentColor() {
        if (color in savedColors || savedColors.size >= MAX_SAVED) return
        savedColors.add(color)
    }

    // ---------------- painting internals ----------------
    private val brush = BrushEngine()
    private var activeFrame: Frame? = null

    private fun updateCounts() {
        undoCount = history.undoSize
        redoCount = history.redoSize
    }

    private fun pushUndo(f: Frame, layerId: Long) = history.record(f, layerId)

    private fun select(f: Frame, layerId: Long) {
        val fi = frames.indexOf(f)
        if (fi >= 0) currentIndex = fi
        val li = layers.indexOfFirst { it.id == layerId }
        if (li >= 0) currentLayerIndex = li
    }

    // ---------------- strokes ----------------
    fun startStroke(x: Float, y: Float) {
        if (isPlaying || tool == Tool.FILL) return
        val layer = currentLayer
        if (!layer.visible || layer.locked) return
        val f = currentFrame
        if (tool == Tool.ERASER && f.cels[layer.id] == null) return
        pushUndo(f, layer.id)
        val cel = f.cel(layer.id)
        cel.dirty = true
        activeFrame = f
        if (tool == Tool.BRUSH) rememberColor()
        brush.begin(cel, x, y, color.toArgb(), brushSize, erase = tool == Tool.ERASER)
        f.touch()
        updateCounts()
    }

    fun moveStroke(x: Float, y: Float) {
        val f = activeFrame ?: return
        if (!brush.isActive) return
        brush.move(x, y, smoothing)
        f.touch()
    }

    fun endStroke() {
        val f = activeFrame ?: return
        val cel = brush.end() ?: return
        cel.dirty = true
        f.touch()
        activeFrame = null
        updateCounts()
    }

    // ---------------- undo / redo / clear ----------------
    fun undo() {
        val e = history.undo() ?: return
        select(e.frame, e.layerId)
        updateCounts()
    }

    fun redo() {
        val e = history.redo() ?: return
        select(e.frame, e.layerId)
        updateCounts()
    }

    fun clearCurrentLayer() {
        val layer = currentLayer
        if (layer.locked) return
        val f = currentFrame
        if (f.cels[layer.id] == null) return
        pushUndo(f, layer.id)
        val cel = f.cel(layer.id)
        cel.canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        cel.dirty = true
        f.touch()
        updateCounts()
    }

    // ---------------- compositing (preview fill + export) ----------------
    fun renderComposite(frame: Frame, withPaper: Boolean = true): Bitmap =
        Compositor.render(frame, layers, width, height, withPaper)

    // ---------------- bucket fill ----------------
    fun bucketFill(x: Int, y: Int) {
        if (isPlaying) return
        if (x < 0 || y < 0 || x >= width || y >= height) return
        val layer = currentLayer
        if (!layer.visible || layer.locked) return
        val frame = currentFrame
        val w = width
        val h = height
        val n = w * h

        // 1. Read the pixels used to find the fill region.
        val px = IntArray(n)
        if (fillMerged) {
            val comp = renderComposite(frame)
            comp.getPixels(px, 0, w, 0, 0, w, h)
            comp.recycle()
        } else {
            frame.cels[layer.id]?.bitmap?.getPixels(px, 0, w, 0, 0, w, h)
        }

        // 2. Find the region (pure algorithm in engine/BucketFill.kt).
        val filled = BucketFill.computeRegion(
            px, w, h, y * w + x,
            tolerance = fillTolerance,
            gap = fillGap,
            slipUnderLines = fillMerged
        )

        // 3. Paint the region onto the current layer.
        pushUndo(frame, layer.id)
        val cel = frame.cel(layer.id)
        val target = IntArray(n)
        cel.bitmap.getPixels(target, 0, w, 0, 0, w, h)
        val c = color.toArgb()
        for (i in 0 until n) if (filled[i]) target[i] = c
        cel.bitmap.setPixels(target, 0, w, 0, 0, w, h)
        cel.dirty = true
        rememberColor()
        frame.touch()
        updateCounts()
    }

    // ---------------- frame operations ----------------
    fun addFrame() {
        if (frames.size >= MAX_FRAMES) return
        frames.add(currentIndex + 1, Frame(nextFrameId++, width, height))
        currentIndex += 1
    }

    fun duplicateFrame() {
        if (frames.size >= MAX_FRAMES) return
        val copy = Frame(nextFrameId++, width, height)
        for ((layerId, cel) in currentFrame.cels) {
            copy.cel(layerId).canvas.drawBitmap(cel.bitmap, 0f, 0f, null)
        }
        frames.add(currentIndex + 1, copy)
        currentIndex += 1
    }

    fun deleteFrame() {
        if (frames.size <= 1) return
        val f = currentFrame
        history.forgetFrame(f)
        frames.removeAt(currentIndex)
        currentIndex = currentIndex.coerceAtMost(frames.size - 1)
        updateCounts()
    }

    fun nextFrame() { currentIndex = (currentIndex + 1) % frames.size }
    fun prevFrame() { currentIndex = (currentIndex - 1 + frames.size) % frames.size }

    // ---------------- layer operations ----------------
    fun addLayer() {
        if (layers.size >= MAX_LAYERS) return
        layerCounter++
        layers.add(currentLayerIndex + 1, Layer(nextLayerId++, "Layer $layerCounter"))
        currentLayerIndex += 1
    }

    fun deleteLayer() {
        if (layers.size <= 1) return
        val l = currentLayer
        frames.forEach { it.cels.remove(l.id); it.touch() }
        history.forgetLayer(l.id)
        layers.removeAt(currentLayerIndex)
        currentLayerIndex = currentLayerIndex.coerceAtMost(layers.size - 1)
        updateCounts()
    }

    fun moveLayerUp() {
        val i = currentLayerIndex
        if (i >= layers.size - 1) return
        val a = layers[i]
        layers[i] = layers[i + 1]
        layers[i + 1] = a
        currentLayerIndex = i + 1
    }

    fun moveLayerDown() {
        val i = currentLayerIndex
        if (i <= 0) return
        val a = layers[i]
        layers[i] = layers[i - 1]
        layers[i - 1] = a
        currentLayerIndex = i - 1
    }
}