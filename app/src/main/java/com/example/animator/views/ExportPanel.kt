package com.example.animator.views

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animator.export.ExportKind
import com.example.animator.export.ExportSettings
import com.example.animator.export.ExportStatus
import com.example.animator.export.startExport
import com.example.animator.state.AnimState
import kotlin.math.roundToInt

/** Export size / background options and the three export buttons (system file picker). */
@Composable
fun ExportPanel(state: AnimState) {
    val ctx = LocalContext.current.applicationContext

    val gifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/gif")
    ) { uri: Uri? -> if (uri != null) startExport(ctx, state, uri, ExportKind.GIF) }
    val mp4Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri: Uri? -> if (uri != null) startExport(ctx, state, uri, ExportKind.MP4) }
    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? -> if (uri != null) startExport(ctx, state, uri, ExportKind.PNG_ZIP) }

    Section("Export", startOpen = false) {
        val ew = (state.width * ExportSettings.scale).roundToInt()
        val eh = (state.height * ExportSettings.scale).roundToInt()
        ToolButton(
            "Export size: $ew × $eh  (tap to switch)",
            Modifier.fillMaxWidth()
        ) { ExportSettings.scale = if (ExportSettings.scale >= 1f) 0.5f else 1f }
        Spacer(Modifier.height(6.dp))
        ToolButton(
            if (ExportSettings.transparentPng) "PNG background: transparent" else "PNG background: white paper",
            Modifier.fillMaxWidth(),
            selected = ExportSettings.transparentPng
        ) { ExportSettings.transparentPng = !ExportSettings.transparentPng }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton("GIF", Modifier.weight(1f)) { gifLauncher.launch("${state.projectName}.gif") }
            ToolButton("MP4", Modifier.weight(1f)) { mp4Launcher.launch("${state.projectName}.mp4") }
            ToolButton("PNGs (.zip)", Modifier.weight(1.3f)) { zipLauncher.launch("${state.projectName}_frames.zip") }
        }
        if (ExportStatus.message.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(ExportStatus.message, color = Color(0xFFBBBBBB), fontSize = 13.sp)
        }
    }
}