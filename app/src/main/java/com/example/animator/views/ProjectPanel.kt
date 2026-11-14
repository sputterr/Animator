package com.example.animator.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animator.App
import com.example.animator.state.AnimState
import com.example.animator.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Save as / new / load / delete projects. All disk work goes through [ProjectStore]. */
@Composable
fun ProjectPanel(state: AnimState) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var nameInput by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf(emptyList<String>()) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var projectMsg by remember { mutableStateOf("") }

    fun refreshProjects() {
        scope.launch { projects = withContext(Dispatchers.IO) { ProjectStore.list(ctx) } }
    }

    LaunchedEffect(state.projectName) {
        delay(200)
        projects = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
        delay(3500)
        projects = withContext(Dispatchers.IO) { ProjectStore.list(ctx) }
    }

    Section("Project (auto-saved)", startOpen = false) {
        Text("Current: ${state.projectName}", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Name for \"Save as\"") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton("Save as", Modifier.weight(1f), enabled = nameInput.isNotBlank()) {
                scope.launch {
                    val clean = ProjectStore.cleanName(nameInput)
                    state.projectName = clean
                    state.markAllDirty()
                    state.lastSavedMeta = ""
                    ProjectStore.saveIfNeeded(ctx, state)
                    projectMsg = "Saved as \"$clean\""
                    nameInput = ""
                    refreshProjects()
                }
            }
            ToolButton("New project", Modifier.weight(1f)) {
                scope.launch {
                    ProjectStore.saveIfNeeded(ctx, state)
                    val s = AnimState()
                    s.projectName = ProjectStore.uniqueName(ctx, "Untitled")
                    state.isPlaying = false
                    App.state = s
                    ProjectStore.setLast(ctx, s.projectName)
                    projectMsg = ""
                }
            }
        }
        if (projectMsg.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(projectMsg, color = Color(0xFF888888), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        projects.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    p + if (p == state.projectName) "  (open)" else "",
                    color = Color.White, fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                ToolButton("Load", Modifier.width(70.dp), enabled = p != state.projectName) {
                    scope.launch {
                        ProjectStore.saveIfNeeded(ctx, state)
                        val s = withContext(Dispatchers.IO) { ProjectStore.load(ctx, p) }
                        if (s != null) {
                            state.isPlaying = false
                            App.state = s
                            ProjectStore.setLast(ctx, p)
                            projectMsg = ""
                        } else {
                            projectMsg = "Could not open \"$p\""
                        }
                    }
                }
                ToolButton(
                    if (pendingDelete == p) "Sure?" else "Delete",
                    Modifier.width(76.dp),
                    enabled = p != state.projectName,
                    selected = pendingDelete == p
                ) {
                    if (pendingDelete == p) {
                        pendingDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) { ProjectStore.delete(ctx, p) }
                            refreshProjects()
                        }
                    } else {
                        pendingDelete = p
                    }
                }
            }
        }
    }
}