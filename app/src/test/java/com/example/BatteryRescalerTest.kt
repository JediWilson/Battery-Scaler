package com.example

import com.example.data.PreciseBatteryEstimator
import com.example.model.BatteryRange
import com.example.model.BatteryRescaler
import com.example.ui.capsule.BatteryCapsuleRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryRescalerTest {

    @Test
    fun `emergency power range exact edge cases and midpoint`() {
        val zero = BatteryRescaler.rescale(0, isCharging = false)
        assertEquals(0, zero.displayedPercent)
        assertEquals(BatteryRange.EMERGENCY, zero.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_EMERGENCY_RED, BatteryCapsuleRenderer.resolveFillColor(zero))

        val ten = BatteryRescaler.rescale(10, isCharging = false)
        assertEquals(50, ten.displayedPercent)
        assertEquals(BatteryRange.EMERGENCY, ten.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_EMERGENCY_RED, BatteryCapsuleRenderer.resolveFillColor(ten))

        val nineteen = BatteryRescaler.rescale(19, isCharging = false)
        assertEquals(95, nineteen.displayedPercent)
        assertEquals(BatteryRange.EMERGENCY, nineteen.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_EMERGENCY_RED, BatteryCapsuleRenderer.resolveFillColor(nineteen))
    }

    @Test
    fun `normal range exact edge cases and midpoint`() {
        val twenty = BatteryRescaler.rescale(20, isCharging = false)
        assertEquals(0, twenty.displayedPercent)
        assertEquals(BatteryRange.NORMAL, twenty.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_NORMAL_WHITE, BatteryCapsuleRenderer.resolveFillColor(twenty))

        val fifty = BatteryRescaler.rescale(50, isCharging = false)
        assertEquals(50, fifty.displayedPercent)
        assertEquals(BatteryRange.NORMAL, fifty.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_NORMAL_WHITE, BatteryCapsuleRenderer.resolveFillColor(fifty))

        val eighty = BatteryRescaler.rescale(80, isCharging = false)
        assertEquals(100, eighty.displayedPercent)
        assertEquals(BatteryRange.NORMAL, eighty.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_NORMAL_WHITE, BatteryCapsuleRenderer.resolveFillColor(eighty))
    }

    @Test
    fun `excess power range exact edge cases and midpoint`() {
        val eightyOne = BatteryRescaler.rescale(81, isCharging = false)
        assertEquals(5, eightyOne.displayedPercent)
        assertEquals(BatteryRange.EXCESS, eightyOne.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_EXCESS_BLUE, BatteryCapsuleRenderer.resolveFillColor(eightyOne))

        val ninety = BatteryRescaler.rescale(90, isCharging = false)
        assertEquals(50, ninety.displayedPercent)
        assertEquals(BatteryRange.EXCESS, ninety.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_EXCESS_BLUE, BatteryCapsuleRenderer.resolveFillColor(ninety))

        val hundred = BatteryRescaler.rescale(100, isCharging = false)
        assertEquals(100, hundred.displayedPercent)
        assertEquals(BatteryRange.EXCESS, hundred.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_EXCESS_BLUE, BatteryCapsuleRenderer.resolveFillColor(hundred))
    }

    @Test
    fun `fractional real percentages advance displayed numbers cleanly without skipping`() {
        // Normal range: 53.0 -> 55%, 53.6 -> 56%, 54.2 -> 57%
        assertEquals(55, BatteryRescaler.rescale(53.0, isCharging = false).displayedPercent)
        assertEquals(56, BatteryRescaler.rescale(53.6, isCharging = false).displayedPercent)
        assertEquals(57, BatteryRescaler.rescale(54.2, isCharging = false).displayedPercent)

        // Excess range: 80.2 -> 1%, 80.4 -> 2%, 80.6 -> 3%, 80.8 -> 4%, 81.0 -> 5%
        assertEquals(1, BatteryRescaler.rescale(80.2, isCharging = false).displayedPercent)
        assertEquals(2, BatteryRescaler.rescale(80.4, isCharging = false).displayedPercent)
        assertEquals(3, BatteryRescaler.rescale(80.6, isCharging = false).displayedPercent)
        assertEquals(4, BatteryRescaler.rescale(80.8, isCharging = false).displayedPercent)
        assertEquals(5, BatteryRescaler.rescale(81.0, isCharging = false).displayedPercent)

        // Emergency range: 19.0 -> 95%, 19.2 -> 96%, 19.4 -> 97%, 19.6 -> 98%, 19.8 -> 99%
        assertEquals(95, BatteryRescaler.rescale(19.0, isCharging = false).displayedPercent)
        assertEquals(96, BatteryRescaler.rescale(19.2, isCharging = false).displayedPercent)
        assertEquals(97, BatteryRescaler.rescale(19.4, isCharging = false).displayedPercent)
        assertEquals(98, BatteryRescaler.rescale(19.6, isCharging = false).displayedPercent)
        assertEquals(99, BatteryRescaler.rescale(19.8, isCharging = false).displayedPercent)
    }

    @Test
    fun `precise battery estimator smoothly steps through displayed percentages using charge counter`() {
        val fullCapacityUah = 4_700_000.0
        val estimator = PreciseBatteryEstimator(initialFullCapacityUah = fullCapacityUah)

        // Simulate smooth drain from 54.2% down to 52.4% (every 0.2%)
        val seenDisplayed = linkedSetOf<Int>()
        var pct = 54.2
        while (pct >= 52.4) {
            val osLevelInt = kotlin.math.round(pct).toInt()
            val chargeUah = (fullCapacityUah * (pct / 100.0)).toLong()
            val state = estimator.estimate(
                levelInt = osLevelInt,
                chargeCounterUah = chargeUah,
                isCharging = false,
            )
            assertTrue(state.isPrecise)
            seenDisplayed.add(state.displayedPercent)
            pct -= 0.2
        }

        // Must hit 57, 56, 55, 54 cleanly without skipping 56
        assertTrue(seenDisplayed.containsAll(listOf(57, 56, 55, 54)))
    }

    @Test
    fun `precise battery estimator falls back cleanly when charge counter is unavailable`() {
        val estimator = PreciseBatteryEstimator()
        val state = estimator.estimate(
            levelInt = 53,
            chargeCounterUah = Long.MIN_VALUE,
            isCharging = false,
        )
        assertFalse(state.isPrecise)
        assertEquals(53, state.realPercent)
        assertEquals(55, state.displayedPercent)
    }

    @Test
    fun `charging overrides fill color to green across every range while preserving rescaled percent`() {
        val emergencyCharging = BatteryRescaler.rescale(10, isCharging = true)
        assertEquals(50, emergencyCharging.displayedPercent)
        assertEquals(BatteryRange.EMERGENCY, emergencyCharging.range)
        assertTrue(emergencyCharging.isCharging)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_CHARGING_GREEN, BatteryCapsuleRenderer.resolveFillColor(emergencyCharging))

        val normalCharging = BatteryRescaler.rescale(50, isCharging = true)
        assertEquals(50, normalCharging.displayedPercent)
        assertEquals(BatteryRange.NORMAL, normalCharging.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_CHARGING_GREEN, BatteryCapsuleRenderer.resolveFillColor(normalCharging))

        val excessCharging = BatteryRescaler.rescale(90, isCharging = true)
        assertEquals(50, excessCharging.displayedPercent)
        assertEquals(BatteryRange.EXCESS, excessCharging.range)
        assertEquals(BatteryCapsuleRenderer.COLOR_FILL_CHARGING_GREEN, BatteryCapsuleRenderer.resolveFillColor(excessCharging))
    }

    @Test
    fun `clamps out-of-range real battery levels safely`() {
        val below = BatteryRescaler.rescale(-5, isCharging = false)
        assertEquals(0, below.realPercent)
        assertEquals(0, below.displayedPercent)
        assertFalse(below.isCharging)

        val above = BatteryRescaler.rescale(120, isCharging = false)
        assertEquals(100, above.realPercent)
        assertEquals(100, above.displayedPercent)
        assertEquals(BatteryRange.EXCESS, above.range)
    }
}
