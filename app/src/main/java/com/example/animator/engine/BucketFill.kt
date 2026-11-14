package com.example.animator.engine

import com.example.animator.engine.BucketFill.computeRegion
import kotlin.math.abs

/**
 * Bucket fill region finding. Pure functions on pixel arrays: no bitmaps, no UI, no state.
 *
 * Pixels that differ from the tapped pixel by more than [computeRegion]'s tolerance are
 * "barriers" (the lines). Barriers are thickened to seal small gaps, the region is flood
 * filled, then the area lost to the thickening is given back.
 */
object BucketFill {

    /**
     * @param px ARGB pixels (w * h) the region is detected on
     * @param seedIdx index of the tapped pixel (y * w + x)
     * @param tolerance 0..100, maximum channel difference still treated as "same colour"
     * @param gap gap closing radius in pixels (0 = off)
     * @param slipUnderLines grow the result by 1 px so it tucks under the line art
     * @return mask of pixels to paint
     */
    fun computeRegion(
        px: IntArray,
        w: Int,
        h: Int,
        seedIdx: Int,
        tolerance: Int,
        gap: Int,
        slipUnderLines: Boolean
    ): BooleanArray {
        val n = w * h
        val seed = px[seedIdx]
        val barrier = BooleanArray(n)
        for (i in 0 until n) barrier[i] = channelDiff(px[i], seed) > tolerance

        var blocked = barrier
        var usedGap = false
        if (gap > 0) {
            val thick = grow(barrier, w, h, gap, null)
            if (!thick[seedIdx]) {
                blocked = thick
                usedGap = true
            }
        }

        var filled = flood(blocked, w, h, seedIdx)
        if (usedGap) filled = grow(filled, w, h, gap, barrier)
        if (slipUnderLines) filled = grow(filled, w, h, 1, null)
        return filled
    }

    private fun channelDiff(a: Int, b: Int): Int {
        val da = abs((a ushr 24) - (b ushr 24))
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return maxOf(maxOf(da, dr), maxOf(dg, db))
    }

    /** Grows a mask by [steps] pixels (4-neighbour). Pixels in [forbidden] are never added. */
    private fun grow(
        mask: BooleanArray,
        w: Int,
        h: Int,
        steps: Int,
        forbidden: BooleanArray?
    ): BooleanArray {
        var cur = mask
        repeat(steps) {
            val next = cur.copyOf()
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val i = row + x
                    if (cur[i]) continue
                    if (forbidden != null && forbidden[i]) continue
                    if ((x > 0 && cur[i - 1]) || (x < w - 1 && cur[i + 1]) ||
                        (y > 0 && cur[i - w]) || (y < h - 1 && cur[i + w])
                    ) next[i] = true
                }
            }
            cur = next
        }
        return cur
    }

    private fun flood(blocked: BooleanArray, w: Int, h: Int, start: Int): BooleanArray {
        val n = w * h
        val filled = BooleanArray(n)
        val queue = IntArray(n)
        var head = 0
        var tail = 0
        queue[tail++] = start
        filled[start] = true
        while (head < tail) {
            val p = queue[head++]
            val x = p % w
            if (x > 0) {
                val q = p - 1
                if (!filled[q] && !blocked[q]) { filled[q] = true; queue[tail++] = q }
            }
            if (x < w - 1) {
                val q = p + 1
                if (!filled[q] && !blocked[q]) { filled[q] = true; queue[tail++] = q }
            }
            if (p >= w) {
                val q = p - w
                if (!filled[q] && !blocked[q]) { filled[q] = true; queue[tail++] = q }
            }
            if (p < n - w) {
                val q = p + w
                if (!filled[q] && !blocked[q]) { filled[q] = true; queue[tail++] = q }
            }
        }
        return filled
    }
}