package com.example.animator.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.example.animator.state.AnimState
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/** Writes every frame as frame_001.png, frame_002.png, ... inside one .zip file. */
object PngZipExporter {
    fun export(
        cr: ContentResolver, uri: Uri, state: AnimState, scale: Float,
        transparent: Boolean, onProgress: (Float) -> Unit
    ) {
        val w = (state.width * scale).roundToInt().coerceAtLeast(2)
        val h = (state.height * scale).roundToInt().coerceAtLeast(2)
        val frameList = state.frames.toList()
        val n = frameList.size
        val os = cr.openOutputStream(uri) ?: throw IOException("Could not open the file")
        ZipOutputStream(BufferedOutputStream(os)).use { zip ->
            for ((i, f) in frameList.withIndex()) {
                val bmp = frameBitmap(state, f, w, h, !transparent)
                zip.putNextEntry(ZipEntry("frame_%03d.png".format(i + 1)))
                bmp.compress(Bitmap.CompressFormat.PNG, 100, zip)
                zip.closeEntry()
                bmp.recycle()
                onProgress((i + 1f) / n)
            }
        }
    }
}