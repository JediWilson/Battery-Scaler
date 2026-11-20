package com.example.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.model.BatteryDisplayState
import com.example.model.BatteryRescaler
import kotlin.math.abs

/**
 * Estimates sub-1% real battery percentage using Android's fuel-gauge coulomb counter
 * ([BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER], in microampere-hours) calibrated against
 * system integer capacity transitions.
 *
 * Because the rescaled Normal range expands 1% real into 1.67% displayed and Emergency/Excess
 * expand 1% real into 5% displayed, sub-1% charge-counter resolution lets the overlay step
 * smoothly through every displayed percentage without skipping numbers.
 */
class PreciseBatteryEstimator(
    private val initialFullCapacityUah: Double = 0.0,
    private val onFullCapacityCalibrated: (Double) -> Unit = {},
) {
    private var estimatedFullCapacityUah: Double = initialFullCapacityUah
    private var lastLevelInt: Int = -1
    private var lastChargeUah: Long = -1L

    /**
     * Pure estimation & calibration step, testable without Android framework dependencies.
     */
    @Synchronized
    fun estimate(
        levelInt: Int,
        chargeCounterUah: Long,
        isCharging: Boolean,
    ): BatteryDisplayState {
        val clampedLevel = levelInt.coerceIn(0, 100)

        // Fall back cleanly if the device/emulator does not expose a valid charge counter
        if (chargeCounterUah <= 0L || chargeCounterUah == Long.MIN_VALUE) {
            lastLevelInt = clampedLevel
            return BatteryRescaler.rescale(clampedLevel.toDouble(), isCharging, isPrecise = false)
        }

        // Bootstrap full capacity if not yet known
        if (estimatedFullCapacityUah !in MIN_SANE_FULL_UAH..MAX_SANE_FULL_UAH) {
            estimatedFullCapacityUah = if (clampedLevel > 0) {
                (chargeCounterUah / (clampedLevel / 100.0)).coerceIn(MIN_SANE_FULL_UAH, MAX_SANE_FULL_UAH)
            } else {
                DEFAULT_PIXEL_FULL_UAH
            }
            onFullCapacityCalibrated(estimatedFullCapacityUah)
        }

        // Calibrate on single-step integer level boundary crossings (e.g., 53 -> 52 crosses 52.5%)
        if (lastLevelInt >= 0 && clampedLevel != lastLevelInt) {
            val deltaLevel = clampedLevel - lastLevelInt
            if (abs(deltaLevel) == 1 && lastChargeUah > 0L) {
                val boundaryPercent = if (deltaLevel < 0) {
                    // Discharging from L+1 to L crosses L + 0.5%
                    (clampedLevel + 0.5).coerceIn(0.5, 99.5)
                } else {
                    // Charging from L-1 to L crosses L - 0.5%
                    (clampedLevel - 0.5).coerceIn(0.5, 99.5)
                }
                val observedFull = chargeCounterUah / (boundaryPercent / 100.0)
                if (observedFull in MIN_SANE_FULL_UAH..MAX_SANE_FULL_UAH) {
                    estimatedFullCapacityUah = estimatedFullCapacityUah * 0.65 + observedFull * 0.35
                    onFullCapacityCalibrated(estimatedFullCapacityUah)
                }
            }
        }

        var rawPercent = (chargeCounterUah / estimatedFullCapacityUah) * 100.0

        // Guard against OS fuel-gauge recalibration drift (> 1.25% discrepancy from OS integer)
        if (abs(rawPercent - clampedLevel) > 1.25 && clampedLevel > 0) {
            val reanchored = (chargeCounterUah / (clampedLevel / 100.0))
                .coerceIn(MIN_SANE_FULL_UAH, MAX_SANE_FULL_UAH)
            estimatedFullCapacityUah = reanchored
            onFullCapacityCalibrated(estimatedFullCapacityUah)
            rawPercent = (chargeCounterUah / estimatedFullCapacityUah) * 100.0
        }

        // Keep fractional estimate consistent with the OS integer bucket's natural half-percent window
        val minBound = when (clampedLevel) {
            0 -> 0.0
            100 -> 99.5
            else -> (clampedLevel - 0.5).coerceAtLeast(0.0)
        }
        val maxBound = when (clampedLevel) {
            0 -> 0.49
            100 -> 100.0
            else -> (clampedLevel + 0.49).coerceAtMost(100.0)
        }
        val precisePercent = rawPercent.coerceIn(minBound, maxBound)

        lastLevelInt = clampedLevel
        lastChargeUah = chargeCounterUah

        return BatteryRescaler.rescale(
            realPercent = precisePercent,
            isCharging = isCharging,
            isPrecise = true,
        )
    }

    companion object {
        private const val PREFS_NAME = "rescaled_battery_calibration"
        private const val KEY_FULL_CAPACITY_UAH = "full_capacity_uah"
        private const val MIN_SANE_FULL_UAH = 500_000.0 // 500 mAh
        private const val MAX_SANE_FULL_UAH = 20_000_000.0 // 20,000 mAh
        private const val DEFAULT_PIXEL_FULL_UAH = 4_700_000.0 // 4,700 mAh

        @Volatile
        private var sharedInstance: PreciseBatteryEstimator? = null

        fun getInstance(context: Context): PreciseBatteryEstimator {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: create(context.applicationContext).also { sharedInstance = it }
            }
        }

        private fun create(appContext: Context): PreciseBatteryEstimator {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedFull = java.lang.Double.longBitsToDouble(
                prefs.getLong(KEY_FULL_CAPACITY_UAH, java.lang.Double.doubleToRawLongBits(0.0)),
            )
            return PreciseBatteryEstimator(
                initialFullCapacityUah = if (savedFull.isFinite() && savedFull > 0.0) savedFull else 0.0,
                onFullCapacityCalibrated = { fullUah ->
                    prefs.edit()
                        .putLong(KEY_FULL_CAPACITY_UAH, java.lang.Double.doubleToRawLongBits(fullUah))
                        .apply()
                },
            )
        }

        /**
         * Samples both the system battery intent (or sticky intent) and the real-time
         * [BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER] to produce a smooth fractional reading.
         */
        fun sample(context: Context, batteryIntent: Intent? = null): BatteryDisplayState {
            val appContext = context.applicationContext
            val intent = batteryIntent
                ?: appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val (levelInt, isCharging) = BatteryRescaler.extractIntentTelemetry(intent)

            val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val chargeCounterUah = try {
                val longVal = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                    ?: Long.MIN_VALUE
                if (longVal != Long.MIN_VALUE && longVal > 0L) {
                    longVal
                } else {
                    val intVal = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                        ?: Int.MIN_VALUE
                    if (intVal != Int.MIN_VALUE && intVal > 0) intVal.toLong() else Long.MIN_VALUE
                }
            } catch (_: Exception) {
                Long.MIN_VALUE
            }

            return getInstance(appContext).estimate(
                levelInt = levelInt,
                chargeCounterUah = chargeCounterUah,
                isCharging = isCharging,
            )
        }
    }
}
