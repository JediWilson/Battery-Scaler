package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OverlayLayoutConfig
import com.example.data.OverlayPreferences
import com.example.data.PreciseBatteryEstimator
import com.example.model.BatteryDisplayState
import com.example.model.BatteryRescaler
import com.example.overlay.BatteryAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MainUiState(
    val realBattery: BatteryDisplayState = BatteryRescaler.rescale(50.0, isCharging = false),
    val isOverlayEnabled: Boolean = false,
    val isOverlayRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val showPermissionDeniedNotice: Boolean = false,
    val wasPermissionRevoked: Boolean = false,
    val layoutConfig: OverlayLayoutConfig = OverlayLayoutConfig(),
    val previewRealPercent: Double = 53.6,
    val previewIsCharging: Boolean = false,
) {
    val previewBattery: BatteryDisplayState
        get() = BatteryRescaler.rescale(previewRealPercent, previewIsCharging, isPrecise = true)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private var awaitingOverlayPermissionResult = false

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                OverlayPreferences.isOverlayEnabled,
                OverlayPreferences.isOverlayRunning,
                OverlayPreferences.isAccessibilityConnected,
                OverlayPreferences.wasPermissionRevoked,
                OverlayPreferences.overlayLayoutConfig,
            ) { enabled, running, connected, revoked, config ->
                OverlaySyncSnapshot(enabled, running, connected, revoked, config)
            }.collect { snapshot ->
                val hasPermission = snapshot.connected || BatteryAccessibilityService.isEnabled(appContext)
                _uiState.update { current ->
                    current.copy(
                        hasOverlayPermission = hasPermission,
                        isOverlayEnabled = snapshot.enabled && hasPermission,
                        isOverlayRunning = snapshot.running && hasPermission,
                        wasPermissionRevoked = snapshot.revoked,
                        layoutConfig = snapshot.layoutConfig,
                    )
                }
            }
        }
    }

    private data class OverlaySyncSnapshot(
        val enabled: Boolean,
        val running: Boolean,
        val connected: Boolean,
        val revoked: Boolean,
        val layoutConfig: OverlayLayoutConfig,
    )

    private fun createInitialState(): MainUiState {
        val realBattery = PreciseBatteryEstimator.sample(appContext)
        val hasPermission = BatteryAccessibilityService.isEnabled(appContext)
        val savedEnabled = OverlayPreferences.isOverlayEnabled(appContext)
        val savedConfig = OverlayPreferences.getLayoutConfig(appContext)

        return MainUiState(
            realBattery = realBattery,
            isOverlayEnabled = savedEnabled && hasPermission,
            isOverlayRunning = OverlayPreferences.isOverlayRunning.value && hasPermission,
            hasOverlayPermission = hasPermission,
            wasPermissionRevoked = OverlayPreferences.wasPermissionRevoked.value,
            layoutConfig = savedConfig,
            previewRealPercent = 53.6,
            previewIsCharging = false,
        )
    }

    fun onBatteryIntentReceived(intent: Intent? = null) {
        val updated = PreciseBatteryEstimator.sample(appContext, intent)
        _uiState.update { it.copy(realBattery = updated) }
    }

    fun refreshPermissionAndServiceState() {
        val hasPermission = BatteryAccessibilityService.isEnabled(appContext)
        val savedEnabled = OverlayPreferences.isOverlayEnabled(appContext)

        val deniedAfterSettings = awaitingOverlayPermissionResult && !hasPermission
        awaitingOverlayPermissionResult = false

        if (!hasPermission) {
            if (savedEnabled || OverlayPreferences.isOverlayRunning.value) {
                OverlayPreferences.markPermissionRevoked(true)
                OverlayPreferences.setOverlayEnabled(appContext, false)
            }
        } else {
            OverlayPreferences.markPermissionRevoked(false)
        }

        val freshBattery = PreciseBatteryEstimator.sample(appContext)
        _uiState.update { current ->
            current.copy(
                realBattery = freshBattery,
                hasOverlayPermission = hasPermission,
                isOverlayEnabled = if (hasPermission) OverlayPreferences.isOverlayEnabled(appContext) else false,
                showPermissionDeniedNotice = if (hasPermission) false else (deniedAfterSettings || current.showPermissionDeniedNotice),
                wasPermissionRevoked = OverlayPreferences.wasPermissionRevoked.value,
            )
        }
    }

    fun markOpeningOverlaySettings() {
        awaitingOverlayPermissionResult = true
        _uiState.update { it.copy(showPermissionDeniedNotice = false) }
    }

    /**
     * Toggles the status-bar overlay on or off.
     * Returns true if the caller should direct the user to enable the Accessibility overlay service.
     */
    fun onOverlayToggleChanged(enabled: Boolean): Boolean {
        if (!enabled) {
            OverlayPreferences.setOverlayEnabled(appContext, false)
            OverlayPreferences.markPermissionRevoked(false)
            _uiState.update {
                it.copy(
                    isOverlayEnabled = false,
                    isOverlayRunning = false,
                    showPermissionDeniedNotice = false,
                )
            }
            return false
        }

        val hasPermission = BatteryAccessibilityService.isEnabled(appContext)
        if (!hasPermission) {
            OverlayPreferences.setOverlayEnabled(appContext, false)
            _uiState.update {
                it.copy(
                    hasOverlayPermission = false,
                    isOverlayEnabled = false,
                    showPermissionDeniedNotice = true,
                )
            }
            return true
        }

        OverlayPreferences.markPermissionRevoked(false)
        OverlayPreferences.setOverlayEnabled(appContext, true)

        _uiState.update {
            it.copy(
                hasOverlayPermission = true,
                isOverlayEnabled = true,
                showPermissionDeniedNotice = false,
                wasPermissionRevoked = false,
            )
        }
        return false
    }

    fun onOverlayOffsetXChanged(offsetXDp: Float) {
        val snapped = (offsetXDp * 2f).roundToInt() / 2f
        val updated = _uiState.value.layoutConfig.copy(offsetXDp = snapped)
        OverlayPreferences.setLayoutConfig(appContext, updated)
    }

    fun onOverlayOffsetYChanged(offsetYDp: Float) {
        val snapped = (offsetYDp * 2f).roundToInt() / 2f
        val updated = _uiState.value.layoutConfig.copy(offsetYDp = snapped)
        OverlayPreferences.setLayoutConfig(appContext, updated)
    }

    fun onOverlayHeightChanged(heightDp: Float) {
        val snapped = (heightDp * 2f).roundToInt() / 2f
        val updated = _uiState.value.layoutConfig.copy(capsuleHeightDp = snapped)
        OverlayPreferences.setLayoutConfig(appContext, updated)
    }

    fun onResetOverlayLayout() {
        OverlayPreferences.resetLayoutConfig(appContext)
    }

    fun onPreviewPercentChanged(realPercent: Double) {
        val snapped = (realPercent.coerceIn(0.0, 100.0) * 10.0).roundToInt() / 10.0
        _uiState.update { it.copy(previewRealPercent = snapped) }
    }

    fun onPreviewChargingChanged(isCharging: Boolean) {
        _uiState.update { it.copy(previewIsCharging = isCharging) }
    }
}
