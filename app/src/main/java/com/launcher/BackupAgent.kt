package com.launcher

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

class BackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        // Add backup helper for preferences
        val helper = SharedPreferencesBackupHelper(this, "launcher_prefs")
        addHelper("launcher_prefs", helper)
    }
}
