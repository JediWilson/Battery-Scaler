package com.example.model

import android.content.Intent
import android.os.BatteryManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The three exact rescaled battery ranges.
 */
enum class BatteryRange {
    /** Real 0–19% (< 20.0%) -> displayed real / 20 * 100 (Red) */
    EMERGENCY,
    /** Real 20–80% (20.0%..80.0%) -> displayed (real - 20) / 60 * 100 (White) */
    NORMAL,
    /** Real 81–100% (> 80.0%) -> displayed (real - 80) / 20 * 100 (Blue) */
    EXCESS,
}

/**
 * Immutable snapshot of real (optionally high-precision fractional) and rescaled battery state.
 */
data class BatteryDisplayState(
    val realPercent: Int,
    val realPercentExact: Double,
    val displayedPercent: Int,
    val range: BatteryRange,
    val isCharging: Boolean,
    val isPrecise: Boolean = false,
) {
    /** Formats real percentage as "53.4%" when fractional/precise, or "53%" when integer. */
    fun formattedRealPercent(): String {
        val isWhole = abs(realPercentExact - realPercentExact.roundToInt()) < 0.05
        return if (isPrecise || !isWhole) {
            String.format(Locale.US, "%.1f%%", realPercentExact)
        } else {
            "${realPercent}%"
        }
    }
}

/**
 * Implements the exact rescaling rules on continuous real battery percentage [0.0, 100.0]:
 * - [0.0, 20.0):  real / 20 * 100        (Emergency power range, Red)
 * - [20.0, 80.0]: (real - 20) / 60 * 100 (Normal, White)
 * - (80.0, 100.0]: (real - 80) / 20 * 100 (Excess power range, Blue)
 *
 * Preserves every exact integer boundary (0->0, 19->95, 20->0, 80->100, 81->5, 100->100)
 * while allowing sub-1% real battery data to advance displayed percentages cleanly 1% at a time
 * without skipping numbers.
 */
object BatteryRescaler {

    fun rescale(
        realPercent: Double,
        isCharging: Boolean,
        isPrecise: Boolean = false,
    ): BatteryDisplayState {
        val clampedReal = realPercent.coerceIn(0.0, 100.0)
        val range = when {
            clampedReal < 20.0 -> BatteryRange.EMERGENCY
            clampedReal <= 80.0 -> BatteryRange.NORMAL
            else -> BatteryRange.EXCESS
        }
        val displayed = when (range) {
            BatteryRange.EMERGENCY -> (clampedReal / 20.0 * 100.0).roundToInt()
            BatteryRange.NORMAL -> ((clampedReal - 20.0) / 60.0 * 100.0).roundToInt()
            BatteryRange.EXCESS -> ((clampedReal - 80.0) / 20.0 * 100.0).roundToInt()
        }.coerceIn(0, 100)

        return BatteryDisplayState(
            realPercent = clampedReal.roundToInt(),
            realPercentExact = clampedReal,
            displayedPercent = displayed,
            range = range,
            isCharging = isCharging,
            isPrecise = isPrecise,
        )
    }

    fun rescale(realPercent: Int, isCharging: Boolean): BatteryDisplayState =
        rescale(realPercent = realPercent.toDouble(), isCharging = isCharging, isPrecise = false)

    /**
     * Extracts integer level and charging flag from a system ACTION_BATTERY_CHANGED intent.
     */
    fun extractIntentTelemetry(intent: Intent?): Pair<Int, Boolean> {
        if (intent == null) return 50 to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val realPercent = if (level >= 0 && scale > 0) {
            (level * 100.0 / scale).roundToInt().coerceIn(0, 100)
        } else {
            50
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)
        return realPercent to isCharging
    }

    /**
     * Parses a system ACTION_BATTERY_CHANGED intent into a [BatteryDisplayState] (integer fallback).
     */
    fun fromBatteryIntent(intent: Intent?): BatteryDisplayState {
        val (level, isCharging) = extractIntentTelemetry(intent)
        return rescale(realPercent = level, isCharging = isCharging)
    }
}
