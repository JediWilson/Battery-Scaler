package com.example.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.OverlayPreferences

/**
 * Handles the "Stop overlay" action from the ongoing status notification.
 */
class StopOverlayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP_OVERLAY) return
        OverlayPreferences.setOverlayEnabled(context, false)
    }

    companion object {
        const val ACTION_STOP_OVERLAY = "com.example.overlay.ACTION_STOP_OVERLAY"
    }
}
