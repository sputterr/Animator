package com.example.animator.export

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.animator.state.AnimState
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * H.264 MP4 writer using the phone's hardware encoder (MediaCodec) and MediaMuxer.
 * Frames are fed as YUV420 with exact timestamps; the video is written to a cache file
 * first and then copied to the chosen location.
 */
object Mp4Exporter {
    private fun evenSize(v: Float): Int = ((v.roundToInt() / 2) * 2).coerceAtLeast(2)

    private fun fillImage(image: Image, px: IntArray, w: Int, h: Int) {
        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        val yBuf = yP.buffer
        val uBuf = uP.buffer
        val vBuf = vP.buffer
        val yRow = yP.rowStride
        val yPix = yP.pixelStride
        val uRow = uP.rowStride
        val uPix = uP.pixelStride
        val vRow = vP.rowStride
        val vPix = vP.pixelStride

        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val p = px[rowOff + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val yy = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(y * yRow + x * yPix, yy.toByte())
            }
        }
        for (y in 0 until h / 2) {
            for (x in 0 until w / 2) {
                val i0 = (2 * y) * w + 2 * x
                val p0 = px[i0]
                val p1 = px[i0 + 1]
                val p2 = px[i0 + w]
                val p3 = px[i0 + w + 1]
                val r = (((p0 shr 16) and 0xFF) + ((p1 shr 16) and 0xFF) + ((p2 shr 16) and 0xFF) + ((p3 shr 16) and 0xFF)) shr 2
                val g = (((p0 shr 8) and 0xFF) + ((p1 shr 8) and 0xFF) + ((p2 shr 8) and 0xFF) + ((p3 shr 8) and 0xFF)) shr 2
                val b = ((p0 and 0xFF) + (p1 and 0xFF) + (p2 and 0xFF) + (p3 and 0xFF)) shr 2
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                uBuf.put(y * uRow + x * uPix, u.coerceIn(0, 255).toByte())
                vBuf.put(y * vRow + x * vPix, v.coerceIn(0, 255).toByte())
            }
        }
    }

    fun export(
        ctx: Context, uri: Uri, state: AnimState, scale: Float,
        onProgress: (Float) -> Unit
    ) {
        val w = evenSize(state.width * scale)
        val h = evenSize(state.height * scale)
        val fps = state.fps
        val frameList = state.frames.toList()
        val n = frameList.size
        val tmp = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, (w * h * 8).coerceIn(1_000_000, 16_000_000))
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = enc
            enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            val mux = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            val px = IntArray(w * h)
            var loaded = -1
            val total = n + 1 // the last drawing is shown once more so it keeps its full duration
            var inIndex = 0
            var inputDone = false
            var outputDone = false
            var track = -1
            val info = MediaCodec.BufferInfo()
            var lastActivity = System.currentTimeMillis()

            while (!outputDone) {
                if (System.currentTimeMillis() - lastActivity > 20_000) {
                    throw IOException("The video encoder stopped responding")
                }
                if (!inputDone) {
                    val ib = enc.dequeueInputBuffer(10_000)
                    if (ib >= 0) {
                        val pts = inIndex * 1_000_000L / fps
                        if (inIndex >= total) {
                            enc.queueInputBuffer(ib, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val src = minOf(inIndex, n - 1)
                            if (src != loaded) {
                                val bmp = frameBitmap(state, frameList[src], w, h, true)
                                bmp.getPixels(px, 0, w, 0, 0, w, h)
                                bmp.recycle()
                                loaded = src
                                onProgress(0.95f * (src + 1) / n)
                            }
                            val image = enc.getInputImage(ib)
                                ?: throw IOException("This phone's video encoder is not supported")
                            fillImage(image, px, w, h)
                            enc.queueInputBuffer(ib, 0, w * h * 3 / 2, pts, 0)
                            inIndex++
                        }
                        lastActivity = System.currentTimeMillis()
                    }
                }

                val ob = enc.dequeueOutputBuffer(info, 10_000)
                if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = mux.addTrack(enc.outputFormat)
                    mux.start()
                    muxerStarted = true
                    lastActivity = System.currentTimeMillis()
                } else if (ob >= 0) {
                    val data = enc.getOutputBuffer(ob)
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (data != null && info.size > 0 && !isConfig && muxerStarted) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        mux.writeSampleData(track, data, info)
                    }
                    enc.releaseOutputBuffer(ob, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    lastActivity = System.currentTimeMillis()
                }
            }

            if (!muxerStarted) throw IOException("The encoder produced no video")
            mux.stop()
            muxerStarted = false

            val os = ctx.contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open the file")
            os.use { out -> tmp.inputStream().use { it.copyTo(out) } }
            onProgress(1f)
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            tmp.delete()
        }
    }
}