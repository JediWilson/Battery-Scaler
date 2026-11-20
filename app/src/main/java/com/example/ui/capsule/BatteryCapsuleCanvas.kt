package com.example.ui.capsule

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.R
import com.example.model.BatteryDisplayState
import com.example.model.BatteryRange

/**
 * Jetpack Compose wrapper around [BatteryCapsuleRenderer], guaranteeing the large in-app preview
 * renders the exact same capsule geometry, colors, and contrast rules as the status-bar overlay.
 */
@Composable
fun BatteryCapsuleCanvas(
    state: BatteryDisplayState,
    modifier: Modifier = Modifier,
) {
    val rangeLabel = when (state.range) {
        BatteryRange.EMERGENCY -> stringResource(R.string.range_emergency)
        BatteryRange.NORMAL -> stringResource(R.string.range_normal)
        BatteryRange.EXCESS -> stringResource(R.string.range_excess)
    }
    val accessibilityDescription = if (state.isCharging) {
        stringResource(R.string.cd_battery_capsule_charging, state.displayedPercent, rangeLabel)
    } else {
        stringResource(R.string.cd_battery_capsule, state.displayedPercent, rangeLabel)
    }

    val bounds = remember { RectF() }

    Canvas(
        modifier = modifier
            .aspectRatio(BatteryCapsuleRenderer.TOTAL_ASPECT_RATIO)
            .semantics { contentDescription = accessibilityDescription },
    ) {
        bounds.set(0f, 0f, size.width, size.height)
        drawIntoCanvas { canvas ->
            BatteryCapsuleRenderer.draw(
                canvas = canvas.nativeCanvas,
                bounds = bounds,
                state = state,
            )
        }
    }
}
