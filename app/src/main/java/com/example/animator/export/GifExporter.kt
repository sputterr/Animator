package com.example.animator.export

import android.content.ContentResolver
import android.net.Uri
import com.example.animator.state.AnimState
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

/** GIF LZW compressor (one frame at a time). The dictionary is reset with a generation counter. */
private class LzwEncoder {
    private val stamp = IntArray(1 shl 20)
    private val codes = IntArray(1 shl 20)
    private var gen = 1
    private var cur = 0
    private var shift = 0
    private var codeSize = 9
    private var buf = ByteArrayOutputStream()

    private fun emit(code: Int) {
        cur = cur or (code shl shift)
        shift += codeSize
        while (shift >= 8) {
            buf.write(cur and 0xFF)
            cur = cur ushr 8
            shift -= 8
        }
    }

    fun encode(pixels: ByteArray): ByteArray {
        buf = ByteArrayOutputStream(pixels.size / 4 + 16)
        cur = 0; shift = 0; codeSize = 9
        gen++
        var nextCode = 258
        emit(256) // clear code
        var prefix = pixels[0].toInt() and 0xFF
        for (i in 1 until pixels.size) {
            val k = pixels[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            if (stamp[key] == gen) {
                prefix = codes[key]
            } else {
                emit(prefix)
                if (nextCode == 4096) {
                    emit(256)
                    gen++
                    nextCode = 258
                    codeSize = 9
                } else {
                    if (nextCode >= (1 shl codeSize)) codeSize++
                    stamp[key] = gen
                    codes[key] = nextCode++
                }
                prefix = k
            }
        }
        emit(prefix)
        emit(257) // end of information
        if (shift > 0) buf.write(cur and 0xFF)
        return buf.toByteArray()
    }
}

/**
 * Animated GIF writer. Builds one 256-colour palette (median cut) from all frames,
 * then writes each frame with that palette.
 */
object GifExporter {

    /** Median-cut palette (255 colours max). Returns palette bytes (RGB x 256) and a 15-bit-colour lookup. */
    private fun buildPalette(
        hist: IntArray, sr: LongArray, sg: LongArray, sb: LongArray
    ): Pair<ByteArray, IntArray> {
        val present = ArrayList<Int>()
        for (i in hist.indices) if (hist[i] > 0) present.add(i)
        val boxes = ArrayList<List<Int>>()
        boxes.add(present)

        while (boxes.size < 256) {
            var bi = -1
            var best = -1L
            for (i in boxes.indices) {
                val b = boxes[i]
                if (b.size < 2) continue
                var cnt = 0L
                for (bin in b) cnt += hist[bin]
                if (cnt > best) { best = cnt; bi = i }
            }
            if (bi < 0) break
            val b = boxes[bi]

            var rMin = 31; var rMax = 0
            var gMin = 31; var gMax = 0
            var bMin = 31; var bMax = 0
            for (bin in b) {
                val r = bin shr 10
                val g = (bin shr 5) and 31
                val bl = bin and 31
                if (r < rMin) rMin = r; if (r > rMax) rMax = r
                if (g < gMin) gMin = g; if (g > gMax) gMax = g
                if (bl < bMin) bMin = bl; if (bl > bMax) bMax = bl
            }
            val rr = rMax - rMin
            val gr = gMax - gMin
            val br = bMax - bMin
            val axis = if (rr >= gr && rr >= br) 0 else if (gr >= br) 1 else 2
            val sorted = b.sortedBy { bin ->
                when (axis) {
                    0 -> bin shr 10
                    1 -> (bin shr 5) and 31
                    else -> bin and 31
                }
            }
            var total = 0L
            for (bin in sorted) total += hist[bin]
            var acc = 0L
            var split = sorted.size / 2
            for (k in sorted.indices) {
                acc += hist[sorted[k]]
                if (acc * 2 >= total) {
                    split = (k + 1).coerceIn(1, sorted.size - 1)
                    break
                }
            }
            boxes[bi] = sorted.subList(0, split)
            boxes.add(sorted.subList(split, sorted.size))
        }

        val palette = ByteArray(768)
        val lut = IntArray(32768)
        for ((idx, b) in boxes.withIndex()) {
            var cnt = 0L
            var r = 0L
            var g = 0L
            var bl = 0L
            for (bin in b) {
                cnt += hist[bin]
                r += sr[bin]; g += sg[bin]; bl += sb[bin]
                lut[bin] = idx
            }
            if (cnt > 0) {
                palette[idx * 3] = (r / cnt).toInt().toByte()
                palette[idx * 3 + 1] = (g / cnt).toInt().toByte()
                palette[idx * 3 + 2] = (bl / cnt).toInt().toByte()
            }
        }
        return palette to lut
    }

    private fun writeShort(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    fun export(
        cr: ContentResolver, uri: Uri, state: AnimState, scale: Float,
        onProgress: (Float) -> Unit
    ) {
        val w = (state.width * scale).roundToInt().coerceAtLeast(2)
        val h = (state.height * scale).roundToInt().coerceAtLeast(2)
        val frameList = state.frames.toList()
        val n = frameList.size
        val px = IntArray(w * h)

        // Pass 1: collect colours from every frame.
        val hist = IntArray(32768)
        val sr = LongArray(32768)
        val sg = LongArray(32768)
        val sb = LongArray(32768)
        for ((i, f) in frameList.withIndex()) {
            val bmp = frameBitmap(state, f, w, h, true)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            for (p in px) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val bin = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                hist[bin]++
                sr[bin] += r.toLong(); sg[bin] += g.toLong(); sb[bin] += b.toLong()
            }
            onProgress(0.4f * (i + 1) / n)
        }
        val (palette, lut) = buildPalette(hist, sr, sg, sb)

        // Pass 2: write the file.
        val os = cr.openOutputStream(uri) ?: throw IOException("Could not open the file")
        BufferedOutputStream(os, 1 shl 16).use { out ->
            out.write("GIF89a".toByteArray(Charsets.US_ASCII))
            writeShort(out, w)
            writeShort(out, h)
            out.write(0xF7) // global colour table, 256 entries
            out.write(0)    // background colour index
            out.write(0)    // pixel aspect ratio
            out.write(palette)

            // Loop forever
            out.write(0x21); out.write(0xFF); out.write(0x0B)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(0x03); out.write(0x01); writeShort(out, 0); out.write(0x00)

            val enc = LzwEncoder()
            val indices = ByteArray(w * h)
            val fps = state.fps
            var prevT = 0
            for ((i, f) in frameList.withIndex()) {
                val bmp = frameBitmap(state, f, w, h, true)
                bmp.getPixels(px, 0, w, 0, 0, w, h)
                bmp.recycle()
                for (k in px.indices) {
                    val p = px[k]
                    val bin = ((((p shr 16) and 0xFF) shr 3) shl 10) or
                            ((((p shr 8) and 0xFF) shr 3) shl 5) or
                            ((p and 0xFF) shr 3)
                    indices[k] = lut[bin].toByte()
                }

                // Frame delay in 1/100 s, spread so the average matches the FPS.
                val t = ((i + 1) * 100.0 / fps).roundToInt()
                val delay = maxOf(2, t - prevT)
                prevT = t

                out.write(0x21); out.write(0xF9); out.write(0x04)
                out.write(0x04) // disposal: leave in place, no transparency
                writeShort(out, delay)
                out.write(0x00); out.write(0x00)

                out.write(0x2C)
                writeShort(out, 0); writeShort(out, 0)
                writeShort(out, w); writeShort(out, h)
                out.write(0x00)

                out.write(8) // LZW minimum code size
                val data = enc.encode(indices)
                var pos = 0
                while (pos < data.size) {
                    val len = minOf(255, data.size - pos)
                    out.write(len)
                    out.write(data, pos, len)
                    pos += len
                }
                out.write(0)
                onProgress(0.4f + 0.6f * (i + 1) / n)
            }
            out.write(0x3B)
        }
    }
}