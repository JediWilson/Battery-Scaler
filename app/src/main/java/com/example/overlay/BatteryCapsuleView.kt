package com.example.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import com.example.model.BatteryDisplayState
import com.example.model.BatteryRescaler
import com.example.ui.capsule.BatteryCapsuleRenderer

/**
 * Lightweight, non-interactive View drawn in the status bar overlay window.
 * Renders only the battery capsule via [BatteryCapsuleRenderer]; the rest of the view
 * is completely transparent.
 */
class BatteryCapsuleView(context: Context) : View(context) {

    private var state: BatteryDisplayState = BatteryRescaler.rescale(50, isCharging = false)
    private val drawBounds = RectF()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun updateState(newState: BatteryDisplayState) {
        if (state != newState) {
            state = newState
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        drawBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        BatteryCapsuleRenderer.draw(canvas, drawBounds, state)
    }
}
