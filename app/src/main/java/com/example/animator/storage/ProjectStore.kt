package com.example.animator.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.animator.model.Blend
import com.example.animator.model.Cel
import com.example.animator.model.Frame
import com.example.animator.model.Layer
import com.example.animator.state.AnimState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Saves and loads projects in the app's private storage.
 *
 * ON-DISK FORMAT (compatibility contract, do not change without a migration):
 *   <filesDir>/projects/<name>/project.json   metadata (layers, frames, fps, ids, saved colours)
 *   <filesDir>/projects/<name>/c_<uuid>.png   one PNG per cel
 *   SharedPreferences "animator", key "last"  name of the last open project
 * Blend modes are stored by enum constant name.
 *
 * Only cels marked dirty are written, so auto-save stays cheap.
 */

object ProjectStore {
    private val lock = Any()

    private fun projectsDir(ctx: Context): File =
        File(ctx.filesDir, "projects").apply { mkdirs() }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("animator", Context.MODE_PRIVATE)

    fun setLast(ctx: Context, name: String) {
        prefs(ctx).edit().putString("last", name).apply()
    }

    fun cleanName(s: String): String {
        val t = s.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "_").trimStart('.').take(40).trim()
        return if (t.isEmpty()) "Untitled" else t
    }

    fun uniqueName(ctx: Context, base: String): String {
        val dir = projectsDir(ctx)
        if (!File(dir, base).exists()) return base
        var i = 2
        while (File(dir, "$base $i").exists()) i++
        return "$base $i"
    }

    fun list(ctx: Context): List<String> {
        val dirs = projectsDir(ctx).listFiles { f -> f.isDirectory && File(f, "project.json").exists() }
            ?: return emptyList()
        return dirs.sortedByDescending { File(it, "project.json").lastModified() }.map { it.name }
    }

    fun delete(ctx: Context, name: String) {
        synchronized(lock) { File(projectsDir(ctx), name).deleteRecursively() }
    }

    private fun buildMeta(state: AnimState, frames: List<Frame>): Pair<String, Set<String>> {
        val j = JSONObject()
        j.put("version", 1)
        j.put("width", state.width)
        j.put("height", state.height)
        j.put("fps", state.fps)
        j.put("currentIndex", state.currentIndex)
        j.put("currentLayerIndex", state.currentLayerIndex)
        j.put("nextFrameId", state.nextFrameId)
        j.put("nextLayerId", state.nextLayerId)
        j.put("layerCounter", state.layerCounter)

        val layersJ = JSONArray()
        for (l in state.layers) {
            layersJ.put(
                JSONObject()
                    .put("id", l.id)
                    .put("name", l.name)
                    .put("visible", l.visible)
                    .put("locked", l.locked)
                    .put("opacity", l.opacity.toDouble())
                    .put("blend", l.blend.name)
            )
        }
        j.put("layers", layersJ)

        val refs = HashSet<String>()
        val framesJ = JSONArray()
        for (f in frames) {
            val celsJ = JSONArray()
            for (entry in f.cels.entries.sortedBy { it.key }) {
                celsJ.put(
                    JSONObject()
                        .put("layerId", entry.key)
                        .put("file", entry.value.fileName)
                )
                refs.add(entry.value.fileName)
            }
            framesJ.put(JSONObject().put("id", f.id).put("cels", celsJ))
        }
        j.put("frames", framesJ)

        val saved = JSONArray()
        state.savedColors.forEach { saved.put(it.toArgb()) }
        j.put("saved", saved)
        return j.toString() to refs
    }

    /** Saves only what changed. Cheap to call often. */
    suspend fun saveIfNeeded(ctx: Context, state: AnimState) {
        if (state.isStroking) return
        val frames = state.frames.toList()
        val (meta, referenced) = buildMeta(state, frames)
        val dirty = ArrayList<Cel>()
        for (f in frames) for (c in f.cels.values) if (c.dirty) dirty.add(c)
        if (meta == state.lastSavedMeta && dirty.isEmpty()) return

        dirty.forEach { it.dirty = false }
        state.lastSavedMeta = meta
        val name = state.projectName
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                synchronized(lock) {
                    val dir = File(projectsDir(ctx), name).apply { mkdirs() }
                    for (c in dirty) {
                        FileOutputStream(File(dir, c.fileName)).use { os ->
                            c.bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                        }
                    }
                    val tmp = File(dir, "project.json.tmp")
                    tmp.writeText(meta)
                    val dst = File(dir, "project.json")
                    if (!tmp.renameTo(dst)) {
                        dst.delete()
                        tmp.renameTo(dst)
                    }
                    dir.listFiles()?.forEach { f ->
                        if (f.name.endsWith(".png") && f.name !in referenced) f.delete()
                    }
                }
            }
            setLast(ctx, name)
        } catch (e: Throwable) {
            dirty.forEach { it.dirty = true }
            state.lastSavedMeta = ""
            if (e is CancellationException) throw e
        }
    }

    private fun decode(f: File): Bitmap? {
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val b = BitmapFactory.decodeFile(f.path, opts) ?: return null
        return if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true)
    }

    fun load(ctx: Context, name: String): AnimState? {
        val dir = File(projectsDir(ctx), name)
        val mf = File(dir, "project.json")
        if (!mf.exists()) return null
        return try {
            val j = JSONObject(mf.readText())
            val st = AnimState(j.getInt("width"), j.getInt("height"))

            val newLayers = ArrayList<Layer>()
            val la = j.getJSONArray("layers")
            for (i in 0 until la.length()) {
                val o = la.getJSONObject(i)
                val l = Layer(o.getLong("id"), o.getString("name"))
                l.visible = o.getBoolean("visible")
                l.locked = o.getBoolean("locked")
                l.opacity = o.getDouble("opacity").toFloat()
                l.blend = try { Blend.valueOf(o.getString("blend")) } catch (e: Exception) { Blend.NORMAL }
                newLayers.add(l)
            }

            val newFrames = ArrayList<Frame>()
            val fa = j.getJSONArray("frames")
            for (i in 0 until fa.length()) {
                val o = fa.getJSONObject(i)
                val fr = Frame(o.getLong("id"), st.width, st.height)
                val ca = o.getJSONArray("cels")
                for (k in 0 until ca.length()) {
                    val c = ca.getJSONObject(k)
                    val file = c.getString("file")
                    val bmp = decode(File(dir, file)) ?: continue
                    val cel = Cel(bmp, file)
                    cel.dirty = false
                    fr.cels[c.getLong("layerId")] = cel
                }
                newFrames.add(fr)
            }
            if (newFrames.isEmpty() || newLayers.isEmpty()) return null

            val saved = ArrayList<Color>()
            val sa = j.optJSONArray("saved")
            if (sa != null) for (i in 0 until sa.length()) saved.add(Color(sa.getInt(i)))

            st.projectName = name
            st.installLoaded(
                newLayers, newFrames,
                j.optInt("currentIndex", 0), j.optInt("currentLayerIndex", 0),
                j.optInt("fps", 12), saved,
                j.optLong("nextFrameId", 0L), j.optLong("nextLayerId", 0L),
                j.optInt("layerCounter", newLayers.size)
            )
            st
        } catch (e: Exception) {
            null
        }
    }

    fun loadLast(ctx: Context): AnimState? {
        val last = prefs(ctx).getString("last", null)
        if (last != null) {
            val s = load(ctx, last)
            if (s != null) return s
        }
        val first = list(ctx).firstOrNull() ?: return null
        return load(ctx, first)
    }
}