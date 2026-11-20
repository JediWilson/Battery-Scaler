package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.OverlayLayoutConfig
import com.example.data.OverlayPreferences
import com.example.data.PreciseBatteryEstimator
import com.example.model.BatteryDisplayState
import com.example.model.BatteryRange
import com.example.model.BatteryRescaler
import com.example.ui.capsule.BatteryCapsuleRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Minimal AccessibilityService used exclusively to attach a non-touchable
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] window above the system status bar
 * so the rescaled battery capsule completely covers the native battery indicator.
 *
 * Does not inspect screen content or intercept input. Automatically reconnected by Android
 * after device reboot if enabled, restoring the overlay whenever the in-app toggle is on.
 */
class BatteryAccessibilityService : AccessibilityService() {

    private var serviceScope: CoroutineScope? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: BatteryCapsuleView? = null
    private var receiversRegistered = false
    private var isScreenInteractive = true
    private var currentState: BatteryDisplayState = BatteryRescaler.rescale(50.0, isCharging = false)
    private var layoutConfig: OverlayLayoutConfig = OverlayLayoutConfig()

    private val precisionPollRunnable = object : Runnable {
        override fun run() {
            if (!isScreenInteractive || overlayView == null) return
            val sampled = PreciseBatteryEstimator.sample(this@BatteryAccessibilityService)
            if (sampled != currentState) {
                updateBatteryDisplay(sampled)
            }
            mainHandler.postDelayed(this, PRECISION_POLL_INTERVAL_MS)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    if (overlayView == null) return
                    val updated = PreciseBatteryEstimator.sample(this@BatteryAccessibilityService, intent)
                    updateBatteryDisplay(updated)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    mainHandler.removeCallbacks(precisionPollRunnable)
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    if (overlayView != null) {
                        val updated = PreciseBatteryEstimator.sample(this@BatteryAccessibilityService)
                        updateBatteryDisplay(updated)
                        schedulePrecisionPoll()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        layoutConfig = OverlayPreferences.getLayoutConfig(this)
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        isScreenInteractive = powerManager?.isInteractive ?: true
        createNotificationChannel()

        OverlayPreferences.setAccessibilityConnected(true)
        OverlayPreferences.markPermissionRevoked(false)

        serviceScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        serviceScope = scope

        // Sync initial state (handles automatic OS reconnect after reboot!)
        val initialEnabled = OverlayPreferences.isOverlayEnabled(this)
        syncOverlayActiveState(initialEnabled)

        scope.launch {
            OverlayPreferences.isOverlayEnabled.collect { enabled ->
                syncOverlayActiveState(enabled)
            }
        }
        scope.launch {
            OverlayPreferences.overlayLayoutConfig.collect { updatedConfig ->
                if (updatedConfig != layoutConfig) {
                    layoutConfig = updatedConfig
                    applyUpdatedLayoutParams()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally unused: this service never reads or processes UI events.
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyUpdatedLayoutParams()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handleServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handleServiceDisconnected()
        super.onDestroy()
    }

    private fun handleServiceDisconnected() {
        val wasRunning = overlayView != null || OverlayPreferences.isOverlayRunning.value
        deactivateOverlay()
        OverlayPreferences.setAccessibilityConnected(false)
        if (wasRunning && OverlayPreferences.isOverlayEnabled(this)) {
            OverlayPreferences.markPermissionRevoked(true)
            OverlayPreferences.setOverlayEnabled(this, false)
        }
        serviceScope?.cancel()
        serviceScope = null
    }

    private fun syncOverlayActiveState(enabled: Boolean) {
        if (enabled) {
            activateOverlay()
        } else {
            deactivateOverlay()
        }
    }

    private fun activateOverlay() {
        layoutConfig = OverlayPreferences.getLayoutConfig(this)
        currentState = PreciseBatteryEstimator.sample(this)

        if (!attachOrUpdateOverlay(currentState)) {
            OverlayPreferences.setOverlayRunning(false)
            return
        }

        registerReceiversIfNeeded()
        schedulePrecisionPoll()
        postOngoingNotification(currentState)
        OverlayPreferences.setOverlayRunning(true)
    }

    private fun deactivateOverlay() {
        mainHandler.removeCallbacks(precisionPollRunnable)
        unregisterReceiversSafely()
        removeOverlayViewSafely()
        cancelOngoingNotification()
        OverlayPreferences.setOverlayRunning(false)
    }

    private fun schedulePrecisionPoll() {
        mainHandler.removeCallbacks(precisionPollRunnable)
        if (isScreenInteractive && overlayView != null) {
            mainHandler.postDelayed(precisionPollRunnable, PRECISION_POLL_INTERVAL_MS)
        }
    }

    private fun updateBatteryDisplay(state: BatteryDisplayState) {
        currentState = state
        overlayView?.updateState(state)
        postOngoingNotification(state)
    }

    private fun attachOrUpdateOverlay(state: BatteryDisplayState): Boolean {
        val wm = windowManager ?: return false
        val existing = overlayView
        if (existing != null) {
            existing.updateState(state)
            return true
        }

        val view = BatteryCapsuleView(this).apply {
            alpha = 1.0f
            updateState(state)
        }
        val params = createOverlayLayoutParams()

        return try {
            wm.addView(view, params)
            overlayView = view
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun applyUpdatedLayoutParams() {
        val view = overlayView ?: return
        val params = createOverlayLayoutParams()
        try {
            windowManager?.updateViewLayout(view, params)
            view.invalidate()
        } catch (_: Exception) {
            removeOverlayViewSafely()
            if (OverlayPreferences.isOverlayEnabled(this)) {
                attachOrUpdateOverlay(currentState)
            }
        }
    }

    private fun createOverlayLayoutParams(): WindowManager.LayoutParams {
        val capsuleHeightPx = dpToPx(layoutConfig.capsuleHeightDp)
        val capsuleWidthPx = (capsuleHeightPx * BatteryCapsuleRenderer.TOTAL_ASPECT_RATIO).roundToInt()

        val defaultRightMarginPx = resolveDefaultRightMarginPx()
        val rightOffsetPx = (defaultRightMarginPx + dpToPx(layoutConfig.offsetXDp)).coerceAtLeast(0)

        val targetCenterYPx = resolveStatusBarIconCenterYPx()
        val topOffsetPx = (targetCenterYPx - capsuleHeightPx / 2 + dpToPx(layoutConfig.offsetYDp))
            .coerceAtLeast(0)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(
            capsuleWidthPx,
            capsuleHeightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            alpha = 1.0f
            gravity = Gravity.TOP or Gravity.END
            x = rightOffsetPx
            y = topOffsetPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun resolveDefaultRightMarginPx(): Int {
        val wm = windowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val windowInsets = wm.currentWindowMetrics.windowInsets
            val cutoutRight = windowInsets.displayCutout?.safeInsetRight ?: 0
            val roundedCornerRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
            } else {
                0
            }
            if (cutoutRight > 0) {
                return cutoutRight + dpToPx(8f)
            }
            if (roundedCornerRadius > 0) {
                return (roundedCornerRadius * 0.72f).roundToInt()
            }
        }
        return dpToPx(DEFAULT_SQUARE_CORNER_END_MARGIN_DP)
    }

    private fun resolveStatusBarIconCenterYPx(): Int {
        val wm = windowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val windowInsets = wm.currentWindowMetrics.windowInsets
            val cutoutTop = windowInsets.displayCutout?.boundingRectTop
            if (cutoutTop != null && !cutoutTop.isEmpty && cutoutTop.bottom > 0) {
                return cutoutTop.centerY()
            }
            val statusTop = windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            if (statusTop > 0) {
                return statusTop / 2
            }
        }
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) {
            val dimenPx = resources.getDimensionPixelSize(resId)
            if (dimenPx > 0) return dimenPx / 2
        }
        return dpToPx(12f)
    }

    private fun dpToPx(dp: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics,
    ).roundToInt()

    private fun registerReceiversIfNeeded() {
        if (receiversRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this,
            systemReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiversRegistered = true
    }

    private fun unregisterReceiversSafely() {
        if (!receiversRegistered) return
        try {
            unregisterReceiver(systemReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
        receiversRegistered = false
    }

    private fun removeOverlayViewSafely() {
        val view = overlayView ?: return
        overlayView = null
        try {
            windowManager?.removeViewImmediate(view)
        } catch (_: Exception) {
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // Already detached
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun postOngoingNotification(state: BatteryDisplayState) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(state))
        } catch (_: SecurityException) {
            // Ignore gracefully if POST_NOTIFICATIONS is not granted
        }
    }

    private fun cancelOngoingNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            manager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
            // Ignore
        }
    }

    private fun buildNotification(state: BatteryDisplayState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, StopOverlayReceiver::class.java).apply {
            action = StopOverlayReceiver.ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val rangeLabel = when (state.range) {
            BatteryRange.EMERGENCY -> getString(R.string.range_emergency)
            BatteryRange.NORMAL -> getString(R.string.range_normal)
            BatteryRange.EXCESS -> getString(R.string.range_excess)
        }
        val realText = state.formattedRealPercent()
        val contentText = if (state.isCharging) {
            getString(R.string.notification_content_charging, realText, state.displayedPercent, rangeLabel)
        } else {
            getString(R.string.notification_content, realText, state.displayedPercent, rangeLabel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_battery)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .addAction(
                0,
                getString(R.string.notification_action_stop),
                stopPendingIntent,
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "rescaled_battery_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val PRECISION_POLL_INTERVAL_MS = 15_000L
        private const val DEFAULT_SQUARE_CORNER_END_MARGIN_DP = 14f

        /**
         * Returns true if [BatteryAccessibilityService] is currently connected or enabled
         * in system Accessibility settings.
         */
        fun isEnabled(context: Context): Boolean {
            if (OverlayPreferences.isAccessibilityConnected.value) return true

            val appContext = context.applicationContext
            val expectedComponent = ComponentName(appContext, BatteryAccessibilityService::class.java)
            val flattenedFull = expectedComponent.flattenToString()
            val flattenedShort = expectedComponent.flattenToShortString()

            val enabledSetting = try {
                Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            } catch (_: Exception) {
                null
            }
            if (!enabledSetting.isNullOrEmpty()) {
                val matched = enabledSetting.split(':').any { entry ->
                    entry.equals(flattenedFull, ignoreCase = true) ||
                        entry.equals(flattenedShort, ignoreCase = true)
                }
                if (matched) return true
            }

            val accessibilityManager =
                appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledList = try {
                accessibilityManager?.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
                ).orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            return enabledList.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo != null &&
                    serviceInfo.packageName == appContext.packageName &&
                    serviceInfo.name == BatteryAccessibilityService::class.java.name
            }
        }
    }
}
