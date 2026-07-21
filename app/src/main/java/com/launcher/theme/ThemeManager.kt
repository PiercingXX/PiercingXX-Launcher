package com.launcher.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.appcompat.app.AppCompatDelegate
import com.launcher.data.SettingsRepository
import android.graphics.Color
import android.os.Build

data class ThemeColors(
    val backgroundColor: Int,
    val textColor: Int
)

class ThemeManager(private val context: Context, private val settingsRepo: SettingsRepository) {

    // Built-in theme presets, in display order for the preview strip
    val presets = linkedMapOf(
        "amoled" to ThemeColors(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
        "graphite" to ThemeColors(0xFF333333.toInt(), 0xFFFFFFFF.toInt()),
        "forest" to ThemeColors(0xFF1B4D2E.toInt(), 0xFFFFFFFF.toInt()),
        "ocean" to ThemeColors(0xFF0F4C75.toInt(), 0xFFFFFFFF.toInt()),
        "paper" to ThemeColors(0xFFFFFAF0.toInt(), 0xFF1A1A1A.toInt()),
        "mist" to ThemeColors(0xFFE0E0E0.toInt(), 0xFF1A1A1A.toInt())
    )
    
    fun getCurrentColors(): ThemeColors {
        val preset = settingsRepo.themePreset
        val customColor = settingsRepo.customBgColor
        
        return when (preset) {
            "custom" -> ThemeColors(customColor, getContrastTextColor(customColor))
            else -> presets[preset] ?: presets["amoled"]!!
        }
    }
    
    private fun getContrastTextColor(bgColor: Int): Int {
        val r = (bgColor shr 16) and 0xFF
        val g = (bgColor shr 8) and 0xFF
        val b = bgColor and 0xFF
        
        // Calculate luminance
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        
        // Return white for dark backgrounds, black for light backgrounds
        return if (luminance > 182) {
            0xFF000000.toInt() // Black text on light background
        } else {
            0xFFFFFFFF.toInt() // White text on dark background
        }
    }
    
    /**
     * Mirrors the theme background onto the system wallpaper so app-switch
     * animations blend seamlessly with the launcher.
     */
    fun applyWallpaper() {
        val colors = getCurrentColors()
        try {
            val wm = android.app.WallpaperManager.getInstance(context)
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap.setPixel(0, 0, colors.backgroundColor)
            wm.setBitmap(bitmap)
        } catch (e: Exception) {
            // Wallpaper mirroring is best-effort.
        }
    }
    
    fun setAppearanceMode(mode: String) {
        when (mode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
