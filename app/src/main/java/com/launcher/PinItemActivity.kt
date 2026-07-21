package com.launcher

import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.launcher.util.showToast

/** Accepts Android pin-shortcut requests (the standard confirmation activity). */
class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }

        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = launcherApps.getPinItemRequest(intent)
        if (request != null) {
            handleRequest(request)
        } else {
            showToast(getString(R.string.pin_request_invalid))
        }
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRequest(request: LauncherApps.PinItemRequest) {
        when (request.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                if (request.shortcutInfo != null && request.accept()) {
                    showToast(getString(R.string.pin_shortcut_success))
                    LauncherApplication.from(this).appRepo.refresh()
                } else {
                    showToast(getString(R.string.pin_shortcut_failed))
                }
            }

            else -> showToast(getString(R.string.pin_widgets_unsupported))
        }
    }
}
