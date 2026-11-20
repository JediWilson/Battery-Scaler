package com.example.ui.capsule

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.example.model.BatteryDisplayState
import com.example.model.BatteryRange

/**
 * Shared pixel-accurate Canvas renderer for the rescaled battery capsule.
 *
 * Proportions are tuned to match the native Pixel status-bar battery indicator as closely
 * as possible while preserving all required rescaling colors, contrast rules, and clean edges:
 * - Compact horizontal capsule (~1.95:1 body, ~2.11:1 total with right nub) with crisp rounded ends.
 * - Slim right-side rounded tab nub (~43% height) in the same opaque dark grey as the empty part.
 * - Solid fill from the left edge with a straight vertical edge meeting the empty dark grey part.
 * - Bold percentage number centered inside the fill (black on white Normal fill; white on red,
 *   blue, or green charging fill). When the fill is too narrow, centers in the whole capsule in
 *   white so it stays readable.
 * - Small lightning bolt next to the number while charging.
 * - No other decorations; everything outside the capsule and nub is fully transparent.
 */
object BatteryCapsuleRenderer {

    // Tuned to match the native Pixel status-bar battery indicator silhouette closely
    const val BODY_ASPECT_RATIO = 1.95f
    private const val CORNER_RADIUS_RATIO = 0.31f
    private const val NUB_GAP_RATIO = 0.055f
    private const val NUB_WIDTH_RATIO = 0.105f
    private const val NUB_HEIGHT_RATIO = 0.43f

    /** Total width-to-height ratio including body + gap + nub. */
    const val TOTAL_ASPECT_RATIO = BODY_ASPECT_RATIO + NUB_GAP_RATIO + NUB_WIDTH_RATIO

    // Fully opaque colors (0xFF...)
    const val COLOR_EMPTY_GREY = 0xFF6B6B70.toInt()
    const val COLOR_FILL_EMERGENCY_RED = 0xFFD93025.toInt()
    const val COLOR_FILL_NORMAL_WHITE = 0xFFFFFFFF.toInt()
    const val COLOR_FILL_EXCESS_BLUE = 0xFF1A73E8.toInt()
    const val COLOR_FILL_CHARGING_GREEN = 0xFF1E8E3E.toInt()
    const val COLOR_TEXT_ON_WHITE = 0xFF141416.toInt()
    const val COLOR_TEXT_LIGHT = 0xFFFFFFFF.toInt()

    fun resolveFillColor(state: BatteryDisplayState): Int = when {
        state.isCharging -> COLOR_FILL_CHARGING_GREEN
        state.range == BatteryRange.EMERGENCY -> COLOR_FILL_EMERGENCY_RED
        state.range == BatteryRange.NORMAL -> COLOR_FILL_NORMAL_WHITE
        state.range == BatteryRange.EXCESS -> COLOR_FILL_EXCESS_BLUE
        else -> COLOR_FILL_NORMAL_WHITE
    }

    /**
     * Draws the battery capsule centered inside [bounds] on [canvas].
     * No background or extra decorations are drawn outside the capsule and nub.
     */
    fun draw(
        canvas: Canvas,
        bounds: RectF,
        state: BatteryDisplayState,
    ) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return

        val bodyHeight = minOf(bounds.height(), bounds.width() / TOTAL_ASPECT_RATIO)
        val bodyWidth = bodyHeight * BODY_ASPECT_RATIO
        val nubGap = bodyHeight * NUB_GAP_RATIO
        val nubWidth = bodyHeight * NUB_WIDTH_RATIO
        val nubHeight = bodyHeight * NUB_HEIGHT_RATIO
        val totalWidth = bodyWidth + nubGap + nubWidth

        val left = bounds.left + (bounds.width() - totalWidth) / 2f
        val top = bounds.top + (bounds.height() - bodyHeight) / 2f

        val bodyRect = RectF(left, top, left + bodyWidth, top + bodyHeight)
        val cornerRadius = bodyHeight * CORNER_RADIUS_RATIO

        val capsulePath = Path().apply {
            addRoundRect(bodyRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // 1. Opaque dark grey empty capsule body
        fillPaint.color = COLOR_EMPTY_GREY
        canvas.drawPath(capsulePath, fillPaint)

        // 2. Solid fill from left edge up to displayedPercent with straight vertical right edge
        val fraction = state.displayedPercent.coerceIn(0, 100) / 100f
        val fillWidth = bodyWidth * fraction
        if (fillWidth > 0f) {
            val saveCount = canvas.save()
            canvas.clipPath(capsulePath)
            fillPaint.color = resolveFillColor(state)
            canvas.drawRect(
                bodyRect.left,
                bodyRect.top,
                bodyRect.left + fillWidth,
                bodyRect.bottom,
                fillPaint,
            )
            canvas.restoreToCount(saveCount)
        }

        // 3. Slim right-side nub (flat left edge facing capsule, rounded right corners, opaque dark grey)
        val nubLeft = bodyRect.right + nubGap
        val nubTop = bodyRect.top + (bodyHeight - nubHeight) / 2f
        val nubRect = RectF(nubLeft, nubTop, nubLeft + nubWidth, nubTop + nubHeight)
        val nubRightCorner = nubWidth * 0.9f
        val nubRadii = floatArrayOf(
            0f, 0f,
            nubRightCorner, nubRightCorner,
            nubRightCorner, nubRightCorner,
            0f, 0f,
        )
        val nubPath = Path().apply {
            addRoundRect(nubRect, nubRadii, Path.Direction.CW)
        }
        fillPaint.color = COLOR_EMPTY_GREY
        canvas.drawPath(nubPath, fillPaint)

        // 4. Bold percentage digits (+ optional lightning bolt while charging)
        val label = state.displayedPercent.coerceIn(0, 100).toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            isSubpixelText = true
        }

        var textSize = bodyHeight * 0.74f
        textPaint.textSize = textSize

        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        var boltHeight = if (state.isCharging) bodyHeight * 0.54f else 0f
        var boltWidth = if (state.isCharging) bodyHeight * 0.33f else 0f
        var boltGap = if (state.isCharging) bodyHeight * 0.055f else 0f

        var measuredTextWidth = textBounds.width().toFloat()
            .coerceAtLeast(textPaint.measureText(label) * 0.92f)
        var contentWidth = measuredTextWidth + if (state.isCharging) boltGap + boltWidth else 0f

        // Ensure 3-digit numbers ("100") and charging bolt always fit comfortably inside the capsule
        val maxContentWidth = bodyWidth * 0.84f
        if (contentWidth > maxContentWidth && contentWidth > 0f) {
            val scale = maxContentWidth / contentWidth
            textSize *= scale
            textPaint.textSize = textSize
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            measuredTextWidth = textBounds.width().toFloat()
                .coerceAtLeast(textPaint.measureText(label) * 0.92f)
            if (state.isCharging) {
                boltHeight *= scale
                boltWidth *= scale
                boltGap *= scale
            }
            contentWidth = measuredTextWidth + if (state.isCharging) boltGap + boltWidth else 0f
        }

        val innerPadding = bodyHeight * 0.12f
        val fitsInsideFill = fillWidth >= (contentWidth + innerPadding * 2f)

        val contentLeft = if (fitsInsideFill) {
            val centeredInFill = bodyRect.left + (fillWidth - contentWidth) / 2f
            centeredInFill.coerceIn(
                bodyRect.left + innerPadding,
                bodyRect.left + fillWidth - contentWidth - innerPadding * 0.5f,
            )
        } else {
            bodyRect.left + (bodyWidth - contentWidth) / 2f
        }

        val contentColor = if (fitsInsideFill) {
            if (!state.isCharging && state.range == BatteryRange.NORMAL) {
                COLOR_TEXT_ON_WHITE
            } else {
                COLOR_TEXT_LIGHT
            }
        } else {
            COLOR_TEXT_LIGHT
        }

        textPaint.color = contentColor

        val centerY = bodyRect.centerY()
        val textBaseline = centerY - textBounds.exactCenterY()
        val textDrawX = contentLeft - textBounds.left

        val saveText = canvas.save()
        canvas.clipPath(capsulePath)
        canvas.drawText(label, textDrawX, textBaseline, textPaint)

        if (state.isCharging && boltWidth > 0f && boltHeight > 0f) {
            val boltLeft = contentLeft + measuredTextWidth + boltGap
            val boltTop = centerY - boltHeight / 2f
            drawLightningBolt(
                canvas = canvas,
                left = boltLeft,
                top = boltTop,
                width = boltWidth,
                height = boltHeight,
                color = contentColor,
            )
        }
        canvas.restoreToCount(saveText)
    }

    private fun drawLightningBolt(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        color: Int,
    ) {
        val boltPath = Path().apply {
            moveTo(left + width * 0.62f, top)
            lineTo(left + width * 0.05f, top + height * 0.56f)
            lineTo(left + width * 0.46f, top + height * 0.56f)
            lineTo(left + width * 0.36f, top + height)
            lineTo(left + width * 0.95f, top + height * 0.42f)
            lineTo(left + width * 0.54f, top + height * 0.42f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawPath(boltPath, paint)
    }
}
