package com.fares.glass.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * Font for the whole UI. Inter by default (OFL license, a free counterpart to SF Pro).
 * If the user puts their own files in /sdcard/Glass/fonts/ (Regular, Medium, Semibold, Bold
 * as .ttf or .otf), Glass uses them instead.
 */
object Typo {
    var family: FontFamily = FontFamily.Default
        private set
    /** Rounded face for the big clock (Nunito, OFL). */
    var clock: FontFamily = FontFamily.Default
        private set

    fun load(context: Context) {
        clock = runCatching { FontFamily(Font("fonts/Clock-Bold.ttf", context.assets, FontWeight.Bold)) }.getOrDefault(FontFamily.Default)
        family = custom(context) ?: FontFamily(
            Font("fonts/Inter-400.ttf", context.assets, FontWeight.Normal),
            Font("fonts/Inter-500.ttf", context.assets, FontWeight.Medium),
            Font("fonts/Inter-600.ttf", context.assets, FontWeight.SemiBold),
            Font("fonts/Inter-700.ttf", context.assets, FontWeight.Bold),
        )
    }

    private fun custom(context: Context): FontFamily? {
        if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return null
        val dir = File(Environment.getExternalStorageDirectory(), "Glass/fonts")
        val files = dir.listFiles { f -> f.extension.lowercase() in setOf("ttf", "otf") }.orEmpty()
        fun pick(vararg names: String) = files.firstOrNull { f -> names.any { f.nameWithoutExtension.contains(it, ignoreCase = true) } }
        val regular = pick("Regular") ?: return null
        val fonts = buildList {
            add(Font(regular, FontWeight.Normal))
            pick("Medium")?.let { add(Font(it, FontWeight.Medium)) }
            pick("Semibold", "SemiBold")?.let { add(Font(it, FontWeight.SemiBold)) }
            files.firstOrNull {
                it.nameWithoutExtension.contains("Bold", ignoreCase = true) && !it.nameWithoutExtension.contains("Semi", ignoreCase = true)
            }?.let { add(Font(it, FontWeight.Bold)) }
        }
        return runCatching { FontFamily(fonts) }.getOrNull()
    }
}
