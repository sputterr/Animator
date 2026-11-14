package com.example.animator.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.animator.state.AnimState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class ExportKind { GIF, MP4, PNG_ZIP }

/** User choices shown in the Export section. */
object ExportSettings {
    var scale by mutableFloatStateOf(1f)
    var transparentPng by mutableStateOf(false)
}

/** What the UI shows while exporting (progress overlay) and afterwards (message). */
object ExportStatus {
    var running by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var message by mutableStateOf("")
}

/** Process-wide scope so an export survives screen rotation. */
private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Starts an export in the background. Progress and result are reported through [ExportStatus].
 * Only one export runs at a time.
 */
fun startExport(ctx: Context, state: AnimState, uri: Uri, kind: ExportKind) {
    if (ExportStatus.running) return
    state.isPlaying = false
    ExportStatus.running = true
    ExportStatus.progress = 0f
    ExportStatus.message = ""
    val scale = ExportSettings.scale
    val transparent = ExportSettings.transparentPng
    val cr = ctx.contentResolver
    exportScope.launch(Dispatchers.IO) {
        try {
            when (kind) {
                ExportKind.GIF -> GifExporter.export(cr, uri, state, scale) { ExportStatus.progress = it }
                ExportKind.MP4 -> Mp4Exporter.export(ctx, uri, state, scale) { ExportStatus.progress = it }
                ExportKind.PNG_ZIP -> PngZipExporter.export(cr, uri, state, scale, transparent) { ExportStatus.progress = it }
            }
            ExportStatus.message = when (kind) {
                ExportKind.GIF -> "GIF saved."
                ExportKind.MP4 -> "MP4 saved."
                ExportKind.PNG_ZIP -> "PNG sequence (.zip) saved."
            }
        } catch (e: Throwable) {
            ExportStatus.message = "Export failed: ${e.message ?: e.javaClass.simpleName}"
            try { DocumentsContract.deleteDocument(cr, uri) } catch (_: Throwable) {}
        } finally {
            ExportStatus.running = false
        }
    }
}