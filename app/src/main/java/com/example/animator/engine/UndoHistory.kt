package com.example.animator.engine

import android.graphics.Bitmap
import android.graphics.PorterDuff
import com.example.animator.model.Frame

/** bitmap == null means "this cel was empty before the change". */
class UndoEntry(val frame: Frame, val layerId: Long, val bitmap: Bitmap?)

/**
 * Undo / redo for cel pixel changes.
 * Call [record] BEFORE changing a cel. Each entry is a snapshot of one cel (one frame, one layer).
 */
class UndoHistory(private val maxSteps: Int) {
    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()

    val undoSize: Int get() = undoStack.size
    val redoSize: Int get() = redoStack.size

    /** Stores the cel's current pixels as an undo step and clears the redo stack. */
    fun record(frame: Frame, layerId: Long) {
        undoStack.addLast(UndoEntry(frame, layerId, snapshot(frame, layerId)))
        while (undoStack.size > maxSteps) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Restores the previous pixels. Returns the entry that was restored, or null if empty. */
    fun undo(): UndoEntry? {
        val e = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(UndoEntry(e.frame, e.layerId, snapshot(e.frame, e.layerId)))
        restore(e)
        return e
    }

    /** Re-applies an undone change. Returns the entry that was restored, or null if empty. */
    fun redo(): UndoEntry? {
        val e = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(UndoEntry(e.frame, e.layerId, snapshot(e.frame, e.layerId)))
        restore(e)
        return e
    }

    /** Drop every step that refers to a deleted frame. */
    fun forgetFrame(frame: Frame) {
        undoStack.removeAll { it.frame === frame }
        redoStack.removeAll { it.frame === frame }
    }

    /** Drop every step that refers to a deleted layer. */
    fun forgetLayer(layerId: Long) {
        undoStack.removeAll { it.layerId == layerId }
        redoStack.removeAll { it.layerId == layerId }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun snapshot(f: Frame, layerId: Long): Bitmap? =
        f.cels[layerId]?.bitmap?.copy(Bitmap.Config.ARGB_8888, false)

    private fun restore(e: UndoEntry) {
        val cel = e.frame.cel(e.layerId)
        cel.canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        e.bitmap?.let { cel.canvas.drawBitmap(it, 0f, 0f, null) }
        cel.dirty = true
        e.frame.touch()
    }
}