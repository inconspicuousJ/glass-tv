package com.fares.glass.gfx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live (video) wallpapers: free Mixkit clips listed in assets/videos.json (checked at build time).
 * Each clip is downloaded the first time it's chosen and kept on the device, so the app stays small;
 * a still frame is saved next to it for the glass blur. Only the few most recent clips are kept.
 */
object LiveWallpapers {
    private const val KEEP = 6

    @Volatile private var list: List<Pair<String, String>>? = null

    fun all(context: Context): List<Pair<String, String>> = list ?: runCatching {
        val arr = JSONArray(context.assets.open("videos.json").bufferedReader().use { it.readText() })
        List(arr.length()) { i -> arr.getJSONObject(i).let { it.getString("name") to it.getString("url") } }
    }.getOrDefault(emptyList()).also { list = it }

    private fun dir(context: Context) = File(context.filesDir, "live").apply { mkdirs() }
    private fun key(url: String) = Integer.toHexString(url.hashCode())

    /** Downloads (if needed) and returns the clip and its still frame; null when offline. Call off the main thread. */
    fun prepare(context: Context, index: Int): Pair<File, Bitmap>? {
        val url = all(context).getOrNull(index)?.second ?: return null
        val video = File(dir(context), key(url) + ".mp4")
        val still = File(dir(context), key(url) + ".jpg")
        if (!video.exists()) {
            val tmp = File(dir(context), key(url) + ".part")
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) GlassTV")
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                if (!tmp.renameTo(video)) return null
            } catch (e: Exception) {
                Log.w("Glass", "Can't download $url", e)
                tmp.delete()
                return null
            }
        }
        video.setLastModified(System.currentTimeMillis())
        var frame: Bitmap? = if (still.exists()) BitmapFactory.decodeFile(still.path) else null
        if (frame == null) {
            frame = runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(video.path)
                    r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    r.release()
                }
            }.getOrNull()
            frame?.let { b -> runCatching { still.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) } } }
        }
        trim(context)
        return frame?.let { video to it }
    }

    private fun trim(context: Context) {
        val clips = dir(context).listFiles { f -> f.extension == "mp4" }.orEmpty().sortedByDescending { it.lastModified() }
        clips.drop(KEEP).forEach { f ->
            f.delete()
            File(f.parentFile, f.nameWithoutExtension + ".jpg").delete()
        }
    }
}
