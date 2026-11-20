package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.OverlayLayoutConfig
import com.example.model.BatteryDisplayState
import com.example.model.BatteryRange
import com.example.ui.capsule.BatteryCapsuleCanvas
import com.example.ui.theme.RangeChargingGreen
import com.example.ui.theme.RangeEmergencyRed
import com.example.ui.theme.RangeExcessBlue
import com.example.ui.theme.RangeNormalStroke
import com.example.ui.theme.RangeNormalWhite
import com.example.ui.theme.StatusBarStageBackground
import com.example.ui.theme.StatusBarStageBorder
import com.example.ui.theme.StatusBarStageMutedText
import java.util.Locale

@Composable
fun MainScreen(
    uiState: MainUiState,
    onToggleOverlay: (Boolean) -> Unit,
    onGrantPermissionClick: () -> Unit,
    onOverlayOffsetXChange: (Float) -> Unit,
    onOverlayOffsetYChange: (Float) -> Unit,
    onOverlayHeightChange: (Float) -> Unit,
    onResetOverlayLayout: () -> Unit,
    onPreviewPercentChange: (Double) -> Unit,
    onPreviewChargingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            val isExpanded = maxWidth >= 840.dp

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HeaderSection()
                        OverlayControlsCard(
                            uiState = uiState,
                            onToggleOverlay = onToggleOverlay,
                            onGrantPermissionClick = onGrantPermissionClick,
                            onOverlayOffsetXChange = onOverlayOffsetXChange,
                            onOverlayOffsetYChange = onOverlayOffsetYChange,
                            onOverlayHeightChange = onOverlayHeightChange,
                            onResetOverlayLayout = onResetOverlayLayout,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        PreviewSectionCard(
                            previewState = uiState.previewBattery,
                            onPreviewPercentChange = onPreviewPercentChange,
                            onPreviewChargingChange = onPreviewChargingChange,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HeaderSection()
                        OverlayControlsCard(
                            uiState = uiState,
                            onToggleOverlay = onToggleOverlay,
                            onGrantPermissionClick = onGrantPermissionClick,
                            onOverlayOffsetXChange = onOverlayOffsetXChange,
                            onOverlayOffsetYChange = onOverlayOffsetYChange,
                            onOverlayHeightChange = onOverlayHeightChange,
                            onResetOverlayLayout = onResetOverlayLayout,
                        )
                        PreviewSectionCard(
                            previewState = uiState.previewBattery,
                            onPreviewPercentChange = onPreviewPercentChange,
                            onPreviewChargingChange = onPreviewChargingChange,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("app_title"),
        )
        Text(
            text = stringResource(R.string.app_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("app_explanation"),
        )
    }
}

@Composable
private fun OverlayControlsCard(
    uiState: MainUiState,
    onToggleOverlay: (Boolean) -> Unit,
    onGrantPermissionClick: () -> Unit,
    onOverlayOffsetXChange: (Float) -> Unit,
    onOverlayOffsetYChange: (Float) -> Unit,
    onOverlayHeightChange: (Float) -> Unit,
    onResetOverlayLayout: () -> Unit,
) {
    val isActive = uiState.isOverlayEnabled && uiState.hasOverlayPermission
    val realState = uiState.realBattery
    val realText = realState.formattedRealPercent()
    val realRangeLabel = rangeLabelString(realState.range)
    var alignmentExpanded by rememberSaveable { mutableStateOf(false) }

    val statusText = when {
        uiState.wasPermissionRevoked && !uiState.hasOverlayPermission -> {
            stringResource(R.string.status_permission_revoked)
        }
        !uiState.hasOverlayPermission -> {
            stringResource(
                R.string.status_permission_required,
                realText,
                realState.displayedPercent,
                realRangeLabel,
            )
        }
        isActive && realState.isCharging -> {
            stringResource(
                R.string.status_active_charging,
                realText,
                realState.displayedPercent,
                realRangeLabel,
            )
        }
        isActive -> {
            stringResource(
                R.string.status_active,
                realText,
                realState.displayedPercent,
                realRangeLabel,
            )
        }
        realState.isCharging -> {
            stringResource(
                R.string.status_inactive_charging,
                realText,
                realState.displayedPercent,
                realRangeLabel,
            )
        }
        else -> {
            stringResource(
                R.string.status_inactive,
                realText,
                realState.displayedPercent,
                realRangeLabel,
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Enable / Disable Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        role = Role.Switch,
                        onClick = { onToggleOverlay(!uiState.isOverlayEnabled) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = stringResource(R.string.cd_overlay_icon),
                            tint = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.overlay_toggle_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isActive) {
                            stringResource(R.string.overlay_toggle_subtitle_on)
                        } else {
                            stringResource(R.string.overlay_toggle_subtitle_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = uiState.isOverlayEnabled && uiState.hasOverlayPermission,
                    onCheckedChange = onToggleOverlay,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("overlay_toggle_switch"),
                )
            }

            // Permission card + "Grant overlay permission" button (shown ONLY until granted)
            AnimatedVisibility(
                visible = !uiState.hasOverlayPermission,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (uiState.showPermissionDeniedNotice || uiState.wasPermissionRevoked) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = stringResource(R.string.cd_permission_warning),
                                tint = if (uiState.showPermissionDeniedNotice || uiState.wasPermissionRevoked) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.permission_needed_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Text(
                            text = if (uiState.showPermissionDeniedNotice) {
                                stringResource(R.string.permission_denied_message)
                            } else {
                                stringResource(R.string.permission_needed_message)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("permission_explanation_text"),
                        )

                        Button(
                            onClick = onGrantPermissionClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("grant_overlay_permission_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(text = stringResource(R.string.grant_overlay_permission_button))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Status line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overlay_status_line"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isActive) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = RangeChargingGreen,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (!uiState.hasOverlayPermission) {
                                    RangeEmergencyRed
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ),
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Collapsible fine-tune position & size controls (shown once permission is granted)
            if (uiState.hasOverlayPermission) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                OverlayAlignmentSection(
                    config = uiState.layoutConfig,
                    expanded = alignmentExpanded,
                    onToggleExpanded = { alignmentExpanded = !alignmentExpanded },
                    onOffsetXChange = onOverlayOffsetXChange,
                    onOffsetYChange = onOverlayOffsetYChange,
                    onHeightChange = onOverlayHeightChange,
                    onReset = onResetOverlayLayout,
                )
            }
        }
    }
}

@Composable
private fun OverlayAlignmentSection(
    config: OverlayLayoutConfig,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleExpanded)
                .minimumInteractiveComponentSize()
                .padding(horizontal = 4.dp)
                .testTag("alignment_toggle_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.alignment_section_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(R.string.cd_expand_alignment),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AlignmentSliderRow(
                    label = stringResource(R.string.alignment_horizontal_label),
                    valueText = String.format(Locale.US, "%+.1f dp", config.offsetXDp),
                    value = config.offsetXDp,
                    valueRange = -30f..30f,
                    onValueChange = onOffsetXChange,
                    sliderTag = "alignment_x_slider",
                )
                AlignmentSliderRow(
                    label = stringResource(R.string.alignment_vertical_label),
                    valueText = String.format(Locale.US, "%+.1f dp", config.offsetYDp),
                    value = config.offsetYDp,
                    valueRange = -16f..16f,
                    onValueChange = onOffsetYChange,
                    sliderTag = "alignment_y_slider",
                )
                AlignmentSliderRow(
                    label = stringResource(R.string.alignment_size_label),
                    valueText = String.format(Locale.US, "%.1f dp", config.capsuleHeightDp),
                    value = config.capsuleHeightDp,
                    valueRange = 10f..18f,
                    onValueChange = onHeightChange,
                    sliderTag = "alignment_size_slider",
                )
                if (!config.isDefault) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onReset,
                            modifier = Modifier.testTag("alignment_reset_button"),
                        ) {
                            Text(text = stringResource(R.string.alignment_reset_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlignmentSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    sliderTag: String,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(sliderTag),
        )
    }
}

@Composable
private fun PreviewSectionCard(
    previewState: BatteryDisplayState,
    onPreviewPercentChange: (Double) -> Unit,
    onPreviewChargingChange: (Boolean) -> Unit,
) {
    val activeRangeLabel = rangeLabelString(previewState.range)
    val accentColor = rangeAccentColor(previewState)
    val realFormatted = previewState.formattedRealPercent()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.preview_section_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.preview_section_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Dark status-bar-inspired stage so the capsule looks crisp in both light & dark themes
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = StatusBarStageBackground,
                border = BorderStroke(1.dp, StatusBarStageBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    BatteryCapsuleCanvas(
                        state = previewState,
                        modifier = Modifier
                            .height(84.dp)
                            .testTag("preview_battery_capsule"),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RangeStatusPill(
                            label = activeRangeLabel,
                            dotColor = rangeDotColor(previewState.range),
                            isWhiteDot = previewState.range == BatteryRange.NORMAL,
                            modifier = Modifier.testTag("preview_range_label"),
                        )
                        if (previewState.isCharging) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = RangeChargingGreen.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, RangeChargingGreen.copy(alpha = 0.6f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = Color(0xFF6EE787),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.preview_charging_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFDCFCE7),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TelemetryReadout(
                            label = stringResource(R.string.preview_real_label),
                            value = realFormatted,
                            valueColor = Color.White,
                            valueTag = "preview_real_percent",
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(StatusBarStageBorder),
                        )
                        TelemetryReadout(
                            label = stringResource(R.string.preview_displayed_label),
                            value = "${previewState.displayedPercent}%",
                            valueColor = if (previewState.isCharging) {
                                Color(0xFF6EE787)
                            } else if (previewState.range == BatteryRange.NORMAL) {
                                Color.White
                            } else {
                                accentColor
                            },
                            valueTag = "preview_displayed_percent",
                        )
                    }
                }
            }

            // Simulated real battery slider (0.0–100.0% at 0.1% precision)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.preview_slider_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = realFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Slider(
                    value = previewState.realPercentExact.toFloat(),
                    onValueChange = { onPreviewPercentChange(it.toDouble()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (!previewState.isCharging && previewState.range == BatteryRange.NORMAL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            accentColor
                        },
                        activeTrackColor = if (!previewState.isCharging && previewState.range == BatteryRange.NORMAL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            accentColor
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preview_battery_slider"),
                )

                ZoneReferenceBar(modifier = Modifier.fillMaxWidth())
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Checkbox,
                        onClick = { onPreviewChargingChange(!previewState.isCharging) },
                    )
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(
                    checked = previewState.isCharging,
                    onCheckedChange = onPreviewChargingChange,
                    modifier = Modifier.testTag("preview_charging_checkbox"),
                )
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (previewState.isCharging) {
                        RangeChargingGreen
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.preview_charging_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RangeStatusPill(
    label: String,
    dotColor: Color,
    isWhiteDot: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF22252E),
        border = BorderStroke(1.dp, StatusBarStageBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(
                        if (isWhiteDot) {
                            Modifier.border(1.dp, RangeNormalStroke, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TelemetryReadout(
    label: String,
    value: String,
    valueColor: Color,
    valueTag: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = StatusBarStageMutedText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = valueColor,
            modifier = Modifier.testTag(valueTag),
        )
    }
}

@Composable
private fun ZoneReferenceBar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(0.20f)
                    .fillMaxHeight()
                    .background(RangeEmergencyRed),
            )
            Box(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Box(
                modifier = Modifier
                    .weight(0.20f)
                    .fillMaxHeight()
                    .background(RangeExcessBlue),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.zone_emergency_short),
                style = MaterialTheme.typography.labelSmall,
                color = RangeEmergencyRed,
            )
            Text(
                text = stringResource(R.string.zone_normal_short),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.zone_excess_short),
                style = MaterialTheme.typography.labelSmall,
                color = RangeExcessBlue,
            )
        }
    }
}

@Composable
private fun rangeLabelString(range: BatteryRange): String = when (range) {
    BatteryRange.EMERGENCY -> stringResource(R.string.range_emergency)
    BatteryRange.NORMAL -> stringResource(R.string.range_normal)
    BatteryRange.EXCESS -> stringResource(R.string.range_excess)
}

private fun rangeDotColor(range: BatteryRange): Color = when (range) {
    BatteryRange.EMERGENCY -> RangeEmergencyRed
    BatteryRange.NORMAL -> RangeNormalWhite
    BatteryRange.EXCESS -> RangeExcessBlue
}

private fun rangeAccentColor(state: BatteryDisplayState): Color = when {
    state.isCharging -> RangeChargingGreen
    state.range == BatteryRange.EMERGENCY -> RangeEmergencyRed
    state.range == BatteryRange.NORMAL -> RangeNormalWhite
    state.range == BatteryRange.EXCESS -> RangeExcessBlue
    else -> RangeNormalWhite
}
