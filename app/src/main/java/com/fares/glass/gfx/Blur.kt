package com.fares.glass.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Software Gaussian blur. RenderEffect only exists from Android 12 and TV stick GPUs are weak,
 * so the image is blurred once, at reduced size, and the result is reused.
 * Three successive box blurs approximate a Gaussian very closely.
 */
object Blur {

    /** Scales [src] down to [targetWidth] px wide, boosts saturation ("vibrancy") and blurs. */
    fun frosted(src: Bitmap, targetWidth: Int, sigma: Float, saturation: Float, brightness: Float): Bitmap {
        val tw = targetWidth
        val th = max(1, (src.height * tw.toFloat() / src.width).roundToInt())
        val small = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        if (brightness != 1f) {
            matrix.postConcat(ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) })
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        Canvas(small).drawBitmap(src, null, Rect(0, 0, tw, th), paint)
        return gaussian(small, sigma)
    }

    fun gaussian(src: Bitmap, sigma: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val a = IntArray(px.size); val r = IntArray(px.size); val g = IntArray(px.size); val b = IntArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            a[i] = c ushr 24; r[i] = (c shr 16) and 0xFF; g[i] = (c shr 8) and 0xFF; b[i] = c and 0xFF
        }
        val tmp = IntArray(px.size)
        for (box in boxesForGauss(sigma, 3)) {
            val radius = (box - 1) / 2
            for (channel in arrayOf(a, r, g, b)) {
                boxH(channel, tmp, w, h, radius)
                boxV(tmp, channel, w, h, radius)
            }
        }
        for (i in px.indices) px[i] = (a[i] shl 24) or (r[i] shl 16) or (g[i] shl 8) or b[i]
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Sizes of the three boxes whose composition approximates a Gaussian with standard deviation [sigma]. */
    private fun boxesForGauss(sigma: Float, n: Int): IntArray {
        val wIdeal = sqrt(12.0 * sigma * sigma / n + 1)
        var wl = floor(wIdeal).toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val mIdeal = (12.0 * sigma * sigma - n * wl * wl - 4.0 * n * wl - 3.0 * n) / (-4.0 * wl - 4)
        val m = mIdeal.roundToInt()
        return IntArray(n) { if (it < m) wl else wu }
    }

    private fun boxH(s: IntArray, d: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (i in -r..r) sum += s[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                d[row + x] = (sum + div / 2) / div
                sum += s[row + min(x + r + 1, w - 1)] - s[row + max(x - r, 0)]
            }
        }
    }

    private fun boxV(s: IntArray, d: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (i in -r..r) sum += s[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                d[y * w + x] = (sum + div / 2) / div
                sum += s[min(y + r + 1, h - 1) * w + x] - s[max(y - r, 0) * w + x]
            }
        }
    }
}
