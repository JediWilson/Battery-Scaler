package com.example

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.data.OverlayPreferences
import com.example.model.BatteryRescaler
import com.example.overlay.BatteryAccessibilityService
import com.example.overlay.StopOverlayReceiver
import com.example.ui.MainViewModel
import com.example.ui.capsule.BatteryCapsuleRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        OverlayPreferences.setOverlayEnabled(app, false)
        OverlayPreferences.setOverlayRunning(false)
        OverlayPreferences.setAccessibilityConnected(false)
        OverlayPreferences.markPermissionRevoked(false)
        Settings.Secure.putString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "",
        )
    }

    @Test
    fun `app name matches Rescaled Battery`() {
        assertEquals("Rescaled Battery", app.getString(R.string.app_name))
    }

    @Test
    fun `toggling on without accessibility overlay permission shows explanation and does not crash`() {
        val viewModel = MainViewModel(app)

        val needsPermission = viewModel.onOverlayToggleChanged(true)

        assertTrue(needsPermission)
        assertFalse(viewModel.uiState.value.isOverlayEnabled)
        assertTrue(viewModel.uiState.value.showPermissionDeniedNotice)
    }

    @Test
    fun `toggling on with accessibility service enabled persists toggle on and toggling off disables it`() {
        val serviceComponent = ComponentName(app, BatteryAccessibilityService::class.java)
            .flattenToString()
        Settings.Secure.putString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            serviceComponent,
        )
        assertTrue(BatteryAccessibilityService.isEnabled(app))

        val viewModel = MainViewModel(app)

        val needsPermission = viewModel.onOverlayToggleChanged(true)
        assertFalse(needsPermission)
        assertTrue(viewModel.uiState.value.isOverlayEnabled)
        assertTrue(OverlayPreferences.isOverlayEnabled(app))

        viewModel.onOverlayToggleChanged(false)
        assertFalse(viewModel.uiState.value.isOverlayEnabled)
        assertFalse(OverlayPreferences.isOverlayEnabled(app))
    }

    @Test
    fun `stop overlay notification action turns saved overlay toggle off`() {
        OverlayPreferences.setOverlayEnabled(app, true)
        assertTrue(OverlayPreferences.isOverlayEnabled(app))

        StopOverlayReceiver().onReceive(app, Intent(StopOverlayReceiver.ACTION_STOP_OVERLAY))

        assertFalse(OverlayPreferences.isOverlayEnabled(app))
    }

    @Test
    fun `capsule renderer draws cleanly across all acceptance levels and charging states`() {
        val levels = listOf(0, 10, 19, 20, 50, 80, 81, 90, 100)
        val bitmap = Bitmap.createBitmap(240, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, 240f, 100f)

        for (level in levels) {
            for (charging in listOf(false, true)) {
                val state = BatteryRescaler.rescale(level, charging)
                BatteryCapsuleRenderer.draw(canvas, bounds, state)
            }
        }
        bitmap.recycle()
    }
}
