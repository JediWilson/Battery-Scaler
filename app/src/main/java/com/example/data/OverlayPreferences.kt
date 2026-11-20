package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-configurable overlay alignment and size overrides applied on top of automatic
 * Pixel/rounded-corner status-bar metrics.
 */
data class OverlayLayoutConfig(
    /** Extra horizontal shift in dp from default right margin (positive moves left, negative moves right). */
    val offsetXDp: Float = DEFAULT_OFFSET_X_DP,
    /** Extra vertical shift in dp from status-bar icon vertical center (positive moves down, negative moves up). */
    val offsetYDp: Float = DEFAULT_OFFSET_Y_DP,
    /** Capsule body height in dp (default 13.5dp completely covers the native Pixel battery icon). */
    val capsuleHeightDp: Float = DEFAULT_CAPSULE_HEIGHT_DP,
) {
    val isDefault: Boolean
        get() = offsetXDp == DEFAULT_OFFSET_X_DP &&
            offsetYDp == DEFAULT_OFFSET_Y_DP &&
            capsuleHeightDp == DEFAULT_CAPSULE_HEIGHT_DP

    companion object {
        const val DEFAULT_OFFSET_X_DP = 0f
        const val DEFAULT_OFFSET_Y_DP = 0f
        const val DEFAULT_CAPSULE_HEIGHT_DP = 13.5f
    }
}

/**
 * Persists the user's overlay toggle preference and alignment configuration in SharedPreferences
 * so the UI and [com.example.overlay.BatteryAccessibilityService] stay synchronized (including
 * across device reboots).
 */
object OverlayPreferences {

    private const val PREFS_NAME = "rescaled_battery_prefs"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    private const val KEY_OFFSET_X_DP = "overlay_offset_x_dp"
    private const val KEY_OFFSET_Y_DP = "overlay_offset_y_dp"
    private const val KEY_CAPSULE_HEIGHT_DP = "overlay_capsule_height_dp"

    private val _isOverlayEnabled = MutableStateFlow(false)
    val isOverlayEnabled: StateFlow<Boolean> = _isOverlayEnabled.asStateFlow()

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _wasPermissionRevoked = MutableStateFlow(false)
    val wasPermissionRevoked: StateFlow<Boolean> = _wasPermissionRevoked.asStateFlow()

    private val _overlayLayoutConfig = MutableStateFlow(OverlayLayoutConfig())
    val overlayLayoutConfig: StateFlow<OverlayLayoutConfig> = _overlayLayoutConfig.asStateFlow()

    fun isOverlayEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
        _isOverlayEnabled.value = enabled
        return enabled
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
        _isOverlayEnabled.value = enabled
        if (enabled) {
            _wasPermissionRevoked.value = false
        }
    }

    fun getLayoutConfig(context: Context): OverlayLayoutConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val config = OverlayLayoutConfig(
            offsetXDp = prefs.getFloat(KEY_OFFSET_X_DP, OverlayLayoutConfig.DEFAULT_OFFSET_X_DP),
            offsetYDp = prefs.getFloat(KEY_OFFSET_Y_DP, OverlayLayoutConfig.DEFAULT_OFFSET_Y_DP),
            capsuleHeightDp = prefs.getFloat(
                KEY_CAPSULE_HEIGHT_DP,
                OverlayLayoutConfig.DEFAULT_CAPSULE_HEIGHT_DP,
            ),
        )
        _overlayLayoutConfig.value = config
        return config
    }

    fun setLayoutConfig(context: Context, config: OverlayLayoutConfig) {
        val clamped = OverlayLayoutConfig(
            offsetXDp = config.offsetXDp.coerceIn(-30f, 30f),
            offsetYDp = config.offsetYDp.coerceIn(-16f, 16f),
            capsuleHeightDp = config.capsuleHeightDp.coerceIn(10f, 18f),
        )
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_OFFSET_X_DP, clamped.offsetXDp)
            .putFloat(KEY_OFFSET_Y_DP, clamped.offsetYDp)
            .putFloat(KEY_CAPSULE_HEIGHT_DP, clamped.capsuleHeightDp)
            .apply()
        _overlayLayoutConfig.value = clamped
    }

    fun resetLayoutConfig(context: Context) {
        setLayoutConfig(context, OverlayLayoutConfig())
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    fun markPermissionRevoked(revoked: Boolean) {
        _wasPermissionRevoked.value = revoked
    }
}
