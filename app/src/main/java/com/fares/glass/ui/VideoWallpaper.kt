package com.fares.glass.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A silent, looping video behind the home screen (a live wallpaper). A TextureView is used so the
 * Compose layers (clock, dock) draw normally on top. The player only exists while Glass is on screen,
 * so it never holds the video decoder while you watch something else.
 */
@Composable
fun VideoWallpaper(path: String, modifier: Modifier = Modifier) {
    key(path) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        var player: MediaPlayer? = null
                        var surface: Surface? = null

                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            try {
                                val s = Surface(st).also { surface = it }
                                player = MediaPlayer().apply {
                                    setDataSource(path)
                                    setSurface(s)
                                    isLooping = true
                                    setVolume(0f, 0f)
                                    setOnPreparedListener { it.start() }
                                    setOnErrorListener { _, what, extra -> Log.w("Glass", "video error $what/$extra"); true }
                                    prepareAsync()
                                }
                            } catch (e: Exception) {
                                Log.w("Glass", "Can't play $path", e)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            runCatching { player?.release() }
                            player = null
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
        )
    }
}
