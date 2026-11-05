package com.fares.glass.gfx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Environment
import androidx.annotation.StringRes
import com.fares.glass.R
import java.io.File
import kotlin.random.Random

/**
 * Abstract wallpapers generated on the fly (no Apple images bundled): pools of color and
 * sharp-edged light ribbons, so the glass has something to blur.
 */
object Wallpapers {

    private class Preset(@StringRes val name: Int, val base: Int, val blobs: IntArray, val ribbons: IntArray)

    private val presets = listOf(
        Preset(R.string.wallpaper_liquid, 0xFF070B1E.toInt(),
            intArrayOf(0xFF3D2BE0.toInt(), 0xFFB0309A.toInt(), 0xFF0E9FB8.toInt(), 0xFF1B3FA8.toInt(), 0xFFFF7A45.toInt()),
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF9AD8FF.toInt(), 0xFFFFB3E6.toInt())),
        Preset(R.string.wallpaper_aurora, 0xFF03140F.toInt(),
            intArrayOf(0xFF12B886.toInt(), 0xFF0B7285.toInt(), 0xFF6741D9.toInt(), 0xFF0B3D2E.toInt(), 0xFF63E6BE.toInt()),
            intArrayOf(0xFFB2F2BB.toInt(), 0xFFFFFFFF.toInt(), 0xFFD0BFFF.toInt())),
        Preset(R.string.wallpaper_dusk, 0xFF1A0B12.toInt(),
            intArrayOf(0xFFFF6B35.toInt(), 0xFFD6336C.toInt(), 0xFF5F3DC4.toInt(), 0xFFFFC078.toInt(), 0xFF862E9C.toInt()),
            intArrayOf(0xFFFFE8CC.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC9C9.toInt())),
        Preset(R.string.wallpaper_graphite, 0xFF0B0C0F.toInt(),
            intArrayOf(0xFF2B2F38.toInt(), 0xFF3B4252.toInt(), 0xFF1F2A44.toInt(), 0xFF4C566A.toInt(), 0xFF2E3440.toInt()),
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFAFC4E8.toInt(), 0xFFFFFFFF.toInt())),
    )

    /** Calm, dark landscapes bundled as assets (original renders, no third-party photos). */
    private val photos = listOf(
        "Lake" to "wallpapers/lake.jpg",
        "Ridge" to "wallpapers/ridge.jpg",
        "Dusk" to "wallpapers/dusk.jpg",
        "Mist" to "wallpapers/mist.jpg",
    )

    private val customFile get() = File(Environment.getExternalStorageDirectory(), "Glass/wallpaper.jpg")

    private fun hasCustom(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            customFile.canRead()

    fun count(context: Context) = photos.size + presets.size + if (hasCustom(context)) 1 else 0

    fun name(context: Context, index: Int): String = when {
        index < photos.size -> photos[index].first
        index >= photos.size + presets.size && hasCustom(context) -> context.getString(R.string.wallpaper_photo)
        else -> context.getString(presets[(index - photos.size) % presets.size].name)
    }

    fun load(context: Context, index: Int): Bitmap {
        if (index < photos.size) {
            runCatching { context.assets.open(photos[index].second).use { BitmapFactory.decodeStream(it) } }
                .getOrNull()?.let { return it }
        }
        if (index >= photos.size + presets.size && hasCustom(context)) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(customFile.path, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= 1920) sample *= 2
            BitmapFactory.decodeFile(customFile.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.let { return it }
        }
        val p = ((index - photos.size) % presets.size + presets.size) % presets.size
        return render(presets[p], index)
    }

    private fun render(p: Preset, seed: Int, w: Int = 1280, h: Int = 720): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(p.base)
        val rnd = Random(seed * 7919 + 17)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Color pools.
        val spots = arrayOf(0.18f to 0.22f, 0.86f to 0.16f, 0.66f to 0.88f, 0.08f to 0.92f, 0.94f to 0.70f)
        p.blobs.forEachIndexed { i, color ->
            val (fx, fy) = spots[i]
            val cx = w * (fx + rnd.nextFloat() * 0.08f - 0.04f)
            val cy = h * (fy + rnd.nextFloat() * 0.08f - 0.04f)
            val radius = w * (0.42f + rnd.nextFloat() * 0.18f) * if (i == 4) 0.55f else 1f
            paint.shader = RadialGradient(cx, cy, radius, intArrayOf(color, withAlpha(color, 0x66), withAlpha(color, 0)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, radius, paint)
        }

        // Light ribbons: sharp-edged curves that show off the glass blur.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        p.ribbons.forEachIndexed { i, color ->
            val y0 = h * (0.25f + 0.22f * i + rnd.nextFloat() * 0.1f)
            val y1 = h * (0.15f + rnd.nextFloat() * 0.7f)
            val path = Path().apply {
                moveTo(-0.1f * w, y0)
                cubicTo(0.3f * w, y0 - h * (0.35f + rnd.nextFloat() * 0.3f),
                    0.65f * w, y1 + h * (0.3f + rnd.nextFloat() * 0.3f), 1.1f * w, y1)
            }
            paint.strokeWidth = h * (0.05f + rnd.nextFloat() * 0.09f)
            paint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
                intArrayOf(withAlpha(color, 0), withAlpha(color, 0x70), withAlpha(color, 0x28), withAlpha(color, 0)),
                floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
            paint.maskFilter = BlurMaskFilter(6f + i * 10f, BlurMaskFilter.Blur.NORMAL)
            c.drawPath(path, paint)
            // Thin bright line along the ribbon's core.
            paint.strokeWidth = 2.5f
            paint.maskFilter = null
            paint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
                intArrayOf(withAlpha(color, 0), withAlpha(Color.WHITE, 0x90), withAlpha(color, 0)),
                null, Shader.TileMode.CLAMP)
            c.drawPath(path, paint)
        }

        // Light grain to break up flat areas (avoids 8-bit gradient banding).
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val noise = Random(seed)
        for (i in px.indices) {
            val n = noise.nextInt(-3, 4)
            val v = px[i]
            val r = ((v shr 16) and 0xFF) + n
            val g = ((v shr 8) and 0xFF) + n
            val b = (v and 0xFF) + n
            px[i] = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
