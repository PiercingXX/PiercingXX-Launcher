package com.launcher

import android.app.Application
import android.content.Context
import com.launcher.data.AppRepository
import com.launcher.data.SettingsRepository
import com.launcher.folder.FolderManager
import com.launcher.theme.ThemeManager

/** Single shared home for the repositories so activities don't re-enumerate apps. */
class LauncherApplication : Application() {

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val appRepo: AppRepository by lazy { AppRepository(this, settings) }
    val folders: FolderManager by lazy { FolderManager(this, settings) }
    val themeManager: ThemeManager by lazy { ThemeManager(this, settings) }

    override fun onCreate() {
        super.onCreate()
        themeManager.setAppearanceMode(settings.appearanceMode)
    }

    companion object {
        fun from(context: Context): LauncherApplication =
            context.applicationContext as LauncherApplication
    }
}
