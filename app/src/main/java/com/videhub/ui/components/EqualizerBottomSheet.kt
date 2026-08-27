package com.videhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videhub.audio.EqualizerManager
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    onDismiss: () -> Unit,
    mediaPlayer: androidx.media3.common.Player? = null
) {
    val context = LocalContext.current
    val isEnabled by EqualizerManager.isEnabled.collectAsState()
    val currentPreset by EqualizerManager.currentPreset.collectAsState()
    val bassStrength by EqualizerManager.bassStrength.collectAsState()
    val virtualizerStrength by EqualizerManager.virtualizerStrength.collectAsState()
    val bandLevels by EqualizerManager.bandLevels.collectAsState()
    val bandFrequencies by EqualizerManager.bandFrequencies.collectAsState()
    val minBandLevel by EqualizerManager.minBandLevel.collectAsState()
    val maxBandLevel by EqualizerManager.maxBandLevel.collectAsState()

    val currentSpeed by com.videhub.audio.AudioSpeedPitchManager.speed.collectAsState()
    val currentPitch by com.videhub.audio.AudioSpeedPitchManager.pitch.collectAsState()
    val activeAudioPreset by com.videhub.audio.AudioSpeedPitchManager.currentPreset.collectAsState()
    val crossfadeSecs by com.videhub.audio.CrossfadeManager.crossfadeDurationSec.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with Master Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = if (isEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Studio Equalizer",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (isEnabled) "Active ($currentPreset)" else "Master EQ Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { EqualizerManager.setEnabled(it) },
                    thumbContent = if (isEnabled) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }

            // Presets Horizontal Carousel
            Text(
                text = "Sound Profiles",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerManager.presets.forEach { preset ->
                    val isSelected = currentPreset == preset && isEnabled
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (!isEnabled) EqualizerManager.setEnabled(true)
                            EqualizerManager.selectPreset(preset)
                        },
                        leadingIcon = {
                            val icon = when (preset) {
                                "Bass Boost" -> Icons.Default.Speaker
                                "Vocal Boost" -> Icons.Default.Mic
                                "Rock" -> Icons.Default.ElectricBolt
                                "Pop" -> Icons.Default.MusicNote
                                "EDM" -> Icons.Default.Headphones
                                "Hip-Hop" -> Icons.Default.Radio
                                "Acoustic" -> Icons.Default.Spa
                                "Custom" -> Icons.Default.Tune
                                else -> Icons.Default.LinearScale
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = preset,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dual Enhancers: Bass Boost & 3D Surround Virtualizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bass Boost Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SurroundSound,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Bass Boost",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${(bassStrength / 10f).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Slider(
                            value = bassStrength.toFloat(),
                            onValueChange = {
                                if (!isEnabled) EqualizerManager.setEnabled(true)
                                EqualizerManager.setBassStrength(it.toInt().toShort())
                            },
                            valueRange = 0f..1000f,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // 3D Spatial Virtualizer Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SpatialAudio,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "3D Surround",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${(virtualizerStrength / 10f).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Slider(
                            value = virtualizerStrength.toFloat(),
                            onValueChange = {
                                if (!isEnabled) EqualizerManager.setEnabled(true)
                                EqualizerManager.setVirtualizerStrength(it.toInt().toShort())
                            },
                            valueRange = 0f..1000f,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Graphic Equalizer Studio Board
            if (bandFrequencies.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Graphic Equalizer (${bandFrequencies.size} Bands)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Range: ±${(maxBandLevel / 100f).roundToInt()} dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        bandFrequencies.forEach { (band, freqHz) ->
                            val currentLevel = bandLevels[band] ?: 0
                            val freqLabel = formatFreq(freqHz)
                            val dbGain = (currentLevel / 100f)
                            val dbFormatted = if (dbGain > 0) "+${String.format(java.util.Locale.US, "%.1f", dbGain)} dB"
                            else "${String.format(java.util.Locale.US, "%.1f", dbGain)} dB"

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = freqLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = dbFormatted,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (currentLevel.toInt() != 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = currentLevel.toFloat(),
                                    onValueChange = {
                                        if (!isEnabled) EqualizerManager.setEnabled(true)
                                        EqualizerManager.setBandLevel(band, it.toInt().toShort())
                                    },
                                    valueRange = minBandLevel.toFloat()..maxBandLevel.toFloat(),
                                    enabled = isEnabled,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audio FX & Sound Modifiers Section
            Text(
                text = "Audio FX & Pitch / Speed",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Quick Modifiers Chips (Nightcore, Slowed+Reverb, etc.)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.videhub.audio.AudioPreset.values().forEach { preset ->
                            val isSel = activeAudioPreset == preset
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    com.videhub.audio.AudioSpeedPitchManager.applyPreset(preset, context, mediaPlayer)
                                },
                                label = { Text(preset.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Playback Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playback Tempo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2fx", currentSpeed),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = currentSpeed,
                        onValueChange = { newSpeed ->
                            com.videhub.audio.AudioSpeedPitchManager.setSpeed(context, mediaPlayer, newSpeed)
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pitch Shifter Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Key & Pitch Shift",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2fx", currentPitch),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = currentPitch,
                        onValueChange = { newPitch ->
                            com.videhub.audio.AudioSpeedPitchManager.setPitch(context, mediaPlayer, newPitch)
                        },
                        valueRange = 0.5f..1.5f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Crossfade & Gapless Transition Section
            Text(
                text = "Playback Transition",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Crossfade Duration",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (crossfadeSecs == 0) "Off (Gapless)" else "${crossfadeSecs}s fade",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (crossfadeSecs > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val crossfadeOptions = listOf(0, 2, 4, 6, 8, 12)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        crossfadeOptions.forEach { sec ->
                            FilterChip(
                                selected = crossfadeSecs == sec,
                                onClick = { com.videhub.audio.CrossfadeManager.setCrossfadeDuration(context, sec) },
                                label = { Text(if (sec == 0) "Gapless" else "${sec}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reset All Button
            OutlinedButton(
                onClick = {
                    EqualizerManager.selectPreset("Flat")
                    EqualizerManager.setBassStrength(0)
                    EqualizerManager.setVirtualizerStrength(0)
                    com.videhub.audio.CrossfadeManager.setCrossfadeDuration(context, 0)
                    com.videhub.audio.AudioSpeedPitchManager.reset(context, mediaPlayer)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset All Audio Effects & EQ",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatFreq(freqMillihertz: Int): String {
    val freqHz = if (freqMillihertz > 100000) freqMillihertz / 1000 else freqMillihertz
    return if (freqHz >= 1000) {
        val khz = freqHz / 1000f
        if (khz % 1.0f == 0f) "${khz.toInt()} kHz" else String.format(java.util.Locale.US, "%.1f kHz", khz)
    } else {
        "$freqHz Hz"
    }
}
