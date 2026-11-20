package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.overlay.BatteryAccessibilityService
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.RescaledBatteryTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var batteryReceiverRegistered = false

    private val foregroundPollRunnable = object : Runnable {
        override fun run() {
            viewModel.onBatteryIntentReceived()
            mainHandler.postDelayed(this, FOREGROUND_POLL_INTERVAL_MS)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Ongoing notification posts whenever permission is granted
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                viewModel.onBatteryIntentReceived(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RescaledBatteryTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    uiState = uiState,
                    onToggleOverlay = { enabled ->
                        val needsPermission = viewModel.onOverlayToggleChanged(enabled)
                        if (enabled && !needsPermission) {
                            requestNotificationPermissionIfNeeded()
                        }
                    },
                    onGrantPermissionClick = {
                        openAccessibilityOverlaySettings()
                    },
                    onOverlayOffsetXChange = viewModel::onOverlayOffsetXChanged,
                    onOverlayOffsetYChange = viewModel::onOverlayOffsetYChanged,
                    onOverlayHeightChange = viewModel::onOverlayHeightChanged,
                    onResetOverlayLayout = viewModel::onResetOverlayLayout,
                    onPreviewPercentChange = viewModel::onPreviewPercentChanged,
                    onPreviewChargingChange = viewModel::onPreviewChargingChanged,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerBatteryReceiverIfNeeded()
        mainHandler.postDelayed(foregroundPollRunnable, FOREGROUND_POLL_INTERVAL_MS)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionAndServiceState()
    }

    override fun onStop() {
        mainHandler.removeCallbacks(foregroundPollRunnable)
        unregisterBatteryReceiverSafely()
        super.onStop()
    }

    private fun openAccessibilityOverlaySettings() {
        viewModel.markOpeningOverlaySettings()
        val component = ComponentName(packageName, BatteryAccessibilityService::class.java.name)
            .flattenToString()
        val highlightBundle = Bundle().apply {
            putString(EXTRA_FRAGMENT_ARG_KEY, component)
        }
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
            putExtra(EXTRA_SHOW_FRAGMENT_ARGS, highlightBundle)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                viewModel.refreshPermissionAndServiceState()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun registerBatteryReceiverIfNeeded() {
        if (batteryReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) {
            viewModel.onBatteryIntentReceived(sticky)
        }
        batteryReceiverRegistered = true
    }

    private fun unregisterBatteryReceiverSafely() {
        if (!batteryReceiverRegistered) return
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
        batteryReceiverRegistered = false
    }

    companion object {
        private const val FOREGROUND_POLL_INTERVAL_MS = 10_000L
        private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    }
}
