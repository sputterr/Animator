# Animator – PROJECT_CONTEXT

Android 2D frame-by-frame animation app. Kotlin + Jetpack Compose, one `app` module,
package `com.example.animator`. No third-party libraries, no DI framework, no ViewModel.

This file describes the code **after the modular refactor** (the original single
~2,000-line `MainActivity.kt` was split into packages in 8 small stages, with no change
to features, UI or saved data). Keep it up to date when the structure changes.

---

## 1. Architecture

Layered, one-directional dependencies. Packages inside one Gradle module (separate
Gradle modules would add build complexity without benefit at this size).

```
            views ───────────────┐
              │                  │
              ▼                  ▼
            state         storage / export
              │                  │
              ▼                  ▼
            engine ◄─────────────┘
              │
              ▼
            model
```

Rules:

* `model` depends on nothing in the app.
* `engine` depends only on `model`. No Compose UI, no `Context`, no file I/O.
* `state` depends on `model` and `engine`. It is the single source of truth for what the user sees.
* `storage` and `export` depend on `state` and `model`. They never touch `views`.
* `views` (all Compose UI) depends on `state` and calls `storage` / `export` entry points.
  It also reads the `App` holder. Nothing depends on `views`.
* `MainActivity` wires theme, root composable and the "save on stop" hook.

Exception: `model` classes use Compose runtime state (`mutableStateOf`, `mutableIntStateOf`)
so the UI can observe them. That is the only Compose dependency allowed below `views`.

Note on naming: the Android Studio template already contains `ui/theme/` (Color.kt, Theme.kt,
Type.kt). Those template files are **not used** by the app (the dark theme is built in
`MainActivity`). They were left untouched. The app's own UI package is therefore called
`views`, not `ui`.

---

## 2. File map

```
com.example.animator
├── MainActivity.kt          Activity, dark theme, onStop() save hook
├── App.kt                   App.state: the project currently open (survives rotation)
│
├── model/                   plain data
│   ├── Tool.kt              enum Tool { BRUSH, ERASER, FILL }
│   ├── Blend.kt             enum Blend (Compose BlendMode + PorterDuff mapping)
│   ├── Layer.kt             Layer: name, visible, locked, opacity, blend
│   ├── Cel.kt               Cel: bitmap + canvas + file name + dirty flag
│   └── Frame.kt             Frame: layerId -> Cel map (lazy), redraw version counter
│
├── engine/                  drawing logic, no UI
│   ├── Compositor.kt        flattens a frame's visible layers into one bitmap
│   ├── BucketFill.kt        region finding: tolerance, gap closing, flood fill (pure functions)
│   ├── UndoHistory.kt       UndoEntry + undo/redo stacks + restore
│   └── BrushEngine.kt       stroke smoothing and painting onto a Cel
│
├── state/
│   └── AnimState.kt         frames, layers, playback, tool/colour/fill settings, frame and layer
│                            operations; orchestrates engine; public API used by views, storage, export
│
├── storage/
│   └── ProjectStore.kt      project folders, project.json, PNG cels, autosave, load/list/delete
│
├── export/
│   ├── ExportManager.kt     ExportKind, ExportSettings, ExportStatus, startExport()
│   ├── FrameBitmap.kt       renders one frame at export size (shared helper)
│   ├── PngZipExporter.kt    PNG sequence in a .zip
│   ├── GifExporter.kt       median-cut palette + LZW encoder + GIF89a writer
│   └── Mp4Exporter.kt       MediaCodec (YUV420 input) + MediaMuxer
│
├── views/                   all Compose UI
│   ├── AnimatorScreen.kt    AnimatorRoot (loading), AnimatorScreen (portrait/landscape,
│   │                        playback loop, autosave loop), ExportOverlay
│   ├── DrawingCanvas.kt     DrawingCanvas (touch -> AnimState) + drawComposite/drawOnion helpers
│   ├── Timeline.kt          PreviewArea (canvas + playback row + timeline), FrameThumb
│   ├── ControlPanel.kt      scrolling panel; tool row; frames, brush, fill, onion sections
│   ├── LayersPanel.kt       layers section
│   ├── ColorPanel.kt        colour section
│   ├── ProjectPanel.kt      project section (save as / new / load / delete)
│   ├── ExportPanel.kt       export section (file pickers, size, background)
│   └── Components.kt        ToolButton, LabeledSlider, Section, SwatchRow, LayerRow, colours
│
└── ui/theme/                Android Studio template files, unused
```

---

## 3. Important classes and relationships

* **AnimState** – owns `layers`, `frames`, `currentIndex`, `currentLayerIndex`, playback
  (`isPlaying`, `fps`), onion settings, tool settings, colour state, fill settings, `projectName`.
  Delegates to `BrushEngine` (strokes), `UndoHistory` (history), `BucketFill` and `Compositor`.
* **Layer** – exists across all frames; `id` (Long) identifies its cels.
* **Frame** – `cels: HashMap<layerId, Cel>`, created lazily (empty layer = no bitmap).
  `Frame.version` is bumped by `touch()` so Compose redraws.
* **Cel** – `bitmap` (ARGB_8888, project size), `canvas`, `fileName` (`c_<uuid>.png`), `dirty`.
* **UndoHistory / UndoEntry** – (frame, layerId, bitmap snapshot or null = cel was empty).
* **BrushEngine** – knows only a `Cel`; the caller decides what may be drawn on.
* **ProjectStore** – reads/writes an `AnimState` to disk; writes only dirty cels.
* **ExportManager / exporters** – read an `AnimState`, render via `renderComposite`, encode.
* **App** – holds the current `AnimState` in Compose state (rotation, project switching).
* **ExportStatus / ExportSettings** – global Compose state read by the UI.

```
App.state ──► AnimState ──► Layer*  ──(id)──┐
                  │  │                       ▼
                  │  └─► BrushEngine   Frame* ──► Cel* (one per layer, lazy)
                  ├────► UndoHistory
                  ├────► BucketFill, Compositor (engine, stateless)
ProjectStore ◄──► AnimState        GifExporter / Mp4Exporter / PngZipExporter ──► AnimState (read)
views ──► AnimState, ProjectStore, startExport, App
```

---

## 4. Data flow

**Drawing** – `DrawingCanvas` pointer input → `AnimState.startStroke/moveStroke/endStroke`
(or `bucketFill` on tap) → `UndoHistory.record` → `BrushEngine` paints on the layer's
`Cel.canvas` → `cel.dirty = true` → `Frame.touch()` → draw lambdas reading `frame.version`
(canvas, thumbnails) redraw.

**Undo / redo** – before each change a bitmap copy of the cel is stored (max 20). `undo()`
restores it, stores the current state on the redo stack, and selects that frame and layer.

**Playback** – `LaunchedEffect` in `AnimatorScreen` advances `currentIndex` every
`1000 / fps` ms. Drawing is blocked and onion skin hidden while playing.

**Autosave** – `LaunchedEffect` every 3 s (and `MainActivity.onStop`) calls
`ProjectStore.saveIfNeeded`: builds metadata JSON on the main thread, compares it with the
last saved JSON, writes PNGs only for dirty cels on `Dispatchers.IO`, writes `project.json`
atomically (tmp + rename), deletes unreferenced PNGs. Skipped while a stroke is active.

**Project switching** – `ProjectPanel` → `ProjectStore` → `App.state = newState` → root recomposes.

**Export** – `ExportPanel` launcher (`CreateDocument`) returns a `Uri` → `startExport` →
background scope on IO → frames rendered via `AnimState.renderComposite` (+ scaling) →
encoded → written to the `Uri`. `ExportStatus.progress` drives the blocking overlay.
MP4 is written to a cache file first, then copied.

---

## 5. Persistence format (compatibility contract – do not break)

Location: `<app files dir>/projects/<project name>/`

* `project.json` keys: `version`, `width`, `height`, `fps`, `currentIndex`, `currentLayerIndex`,
  `nextFrameId`, `nextLayerId`, `layerCounter`,
  `layers[] {id, name, visible, locked, opacity, blend}`,
  `frames[] {id, cels[] {layerId, file}}`, `saved[]` (ARGB ints).
* `c_<uuid>.png` – one PNG per cel.
* `SharedPreferences "animator"`, key `last` – name of the last open project.
* `blend` is stored as the enum constant name (`NORMAL`, `MULTIPLY`, `SCREEN`, `OVERLAY`,
  `DARKEN`, `LIGHTEN`, `ADD`). **Never rename these constants.**

Any change to these needs a migration that can still read old projects.

---

## 6. Existing features

* Drawing: brush, eraser, size, stabilizer/smoothing (finger input; no pressure).
* Frames: new, duplicate, delete, timeline with thumbnails, prev/next, playback, FPS 1–30.
* Onion skin: red = before, green = after; opacity; range 1–3 frames.
* Layers (max 4): visibility, lock, opacity, blend modes, reorder, add/delete.
  Default layers: "Color" (bottom) and "Line art" (top).
* Colour: HSV sliders, preset palette, saved palette, recent colours.
* Bucket fill: reads all visible layers or the current layer; tolerance; gap closing.
* Undo/redo (20 steps, per frame + layer), clear current layer.
* Projects: autosave, save as, new, load, delete; survives rotation and app restart.
* Portrait and landscape layouts.
* Export: GIF (256 colours), MP4 (H.264), PNG sequence (.zip); 100% or 50% size;
  optional transparent PNG background.

---

## 7. Known limitations

* Limits: 60 frames, 4 layers, 20 undo steps; canvas 960×540.
* Memory: each drawn cel is a full bitmap (~2 MB) kept in memory.
* Undo does not cover adding/deleting frames or layers, or layer property changes.
* No group layers, clipping masks, layer renaming, frame reordering, frame holds (exposure).
* No keyframing/tweening, graph editor, symbols, bone rigging, vector drawing, stylus pressure.
* Blend modes blend against the white paper as well.
* GIF: 256 colours, delay rounded to 1/100 s, no transparency.
* MP4: depends on the device's H.264 encoder (YUV420 flexible input); no audio.
* Export reads state off the main thread; the UI is blocked by an overlay during export.
* "Save as" over an existing name overwrites it without confirmation.
* Fill is a synchronous pixel operation (proportional to canvas size).
* Template files in `ui/theme/` are unused.

---

## 8. Development conventions

* One main class per file; file name = class name. Small related helpers may share a file.
* Follow the dependency rules in section 1. Nothing below `views` imports from `views`.
* `engine` code must stay free of Compose UI and `Context`.
* **Whenever a cel's pixels change:** set `cel.dirty = true` and call `frame.touch()`.
* Before destructive pixel changes call `UndoHistory.record` (via `pushUndo` in `AnimState`).
* All bitmaps are `ARGB_8888` and exactly the project size.
* Disk and encoding work runs on `Dispatchers.IO`; Compose state is mutated on the main thread
  (export progress is the one deliberate exception).
* Keep the public API of `AnimState` stable when refactoring internals.
* Each Compose section is one composable that wraps its own `Section(...)`, so its
  `remember`ed state lives outside the collapsible content.
* Do not add libraries or architecture frameworks without a demonstrated need.
* Keep a backup or commit before each larger change. Do not uninstall the app while testing:
  saved projects live in its internal storage.

---

## 9. Where to put new work

| Task | Touch | Leave alone |
|------|-------|-------------|
| New blend mode | `model/Blend.kt` (add a constant at the END) | storage, export |
| New drawing tool | `model/Tool.kt`, `engine/` (new engine class), `AnimState` (routing), `views/ControlPanel.kt` tool row | storage, export |
| New fill behaviour | `engine/BucketFill.kt`, `AnimState.bucketFill`, `views/ControlPanel.kt` (FillPanel) | everything else |
| New export format | new file in `export/`, add a value to `ExportKind`, branch in `startExport`, button in `views/ExportPanel.kt` | engine, state, storage |
| New panel / section | new file in `views/`, one line in `ControlPanel` | engine, state, storage, export |
| New saved setting | `AnimState` property, `ProjectStore.buildMeta` + `load` (optional key with default) | views, export |
| Change layer/frame structure | `model/`, `AnimState`, `ProjectStore` (migration!), `UndoHistory` if undo is affected | exporters (they use `renderComposite`) |
| New feature that animates layers (tweening, etc.) | new `engine/` code, extend `Compositor`, `AnimState`, `ProjectStore` | BucketFill, BrushEngine |

---

## 10. Regression checklist (run after every larger change)

1. App launches; previous project is restored.
2. Brush, eraser, size, smoothing; undo / redo / clear.
3. Bucket fill inside a closed shape on the "Color" layer; gap closing.
4. New / duplicate / delete frame; onion skin; play / stop; FPS slider.
5. Layers: add, delete, move, hide, lock, opacity, blend mode.
6. Rotate the phone: drawing and tool state are kept; landscape layout works.
7. Close the app from recents and reopen: work is restored.
8. Project panel: save as, new, load, delete.
9. Export GIF, MP4 and PNG zip; open the results.
