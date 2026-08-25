package com.videhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val isEnabled by EqualizerManager.isEnabled.collectAsState()
    val currentPreset by EqualizerManager.currentPreset.collectAsState()
    val bassStrength by EqualizerManager.bassStrength.collectAsState()
    val bandLevels by EqualizerManager.bandLevels.collectAsState()
    val bandFrequencies by EqualizerManager.bandFrequencies.collectAsState()
    val minBandLevel by EqualizerManager.minBandLevel.collectAsState()
    val maxBandLevel by EqualizerManager.maxBandLevel.collectAsState()

    val currentSpeed by com.videhub.audio.AudioSpeedPitchManager.speed.collectAsState()
    val currentPitch by com.videhub.audio.AudioSpeedPitchManager.pitch.collectAsState()
    val activeAudioPreset by com.videhub.audio.AudioSpeedPitchManager.currentPreset.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Audio Equalizer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pro sound customization & bass boost",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { EqualizerManager.setEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Horizontal Row
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerManager.presets.forEach { preset ->
                    val isSelected = currentPreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isEnabled) {
                                EqualizerManager.selectPreset(preset)
                            } else {
                                EqualizerManager.setEnabled(true)
                                EqualizerManager.selectPreset(preset)
                            }
                        },
                        label = {
                            Text(
                                text = preset,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bass Boost Slider
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bass Boost",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(bassStrength / 10f).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = bassStrength.toFloat(),
                        onValueChange = {
                            if (!isEnabled) EqualizerManager.setEnabled(true)
                            EqualizerManager.setBassStrength(it.toInt().toShort())
                        },
                        valueRange = 0f..1000f,
                        enabled = isEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Frequency Bands
            if (bandFrequencies.isNotEmpty()) {
                Text(
                    text = "Graphic Equalizer (${bandFrequencies.size} Bands)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        bandFrequencies.forEach { (band, freqHz) ->
                            val currentLevel = bandLevels[band] ?: 0
                            val freqLabel = if (freqHz >= 1000000) {
                                "${freqHz / 1000000} kHz"
                            } else if (freqHz >= 1000) {
                                val khz = freqHz / 1000f
                                if (khz % 1 == 0f) "${khz.toInt()} Hz" else "${String.format("%.1f", khz / 1000f)} kHz"
                            } else {
                                "$freqHz Hz"
                            }

                            val dbGain = (currentLevel / 100f)
                            val dbFormatted = if (dbGain > 0) "+${String.format("%.1f", dbGain)} dB" else "${String.format("%.1f", dbGain)} dB"

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = freqLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = dbFormatted,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (currentLevel != 0.toShort()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    enabled = isEnabled
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Crossfade & Gapless Section
            val context = androidx.compose.ui.platform.LocalContext.current
            val crossfadeSecs by com.videhub.audio.CrossfadeManager.crossfadeDurationSec.collectAsState()

            Text(
                text = "Seamless Crossfade & Gapless Playback",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transition Fade",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (crossfadeSecs == 0) "Off (Gapless)" else "${crossfadeSecs}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (crossfadeSecs > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val crossfadeOptions = listOf(0, 2, 4, 6, 8, 12)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        crossfadeOptions.forEach { sec ->
                            FilterChip(
                                selected = crossfadeSecs == sec,
                                onClick = { com.videhub.audio.CrossfadeManager.setCrossfadeDuration(context, sec) },
                                label = { Text(if (sec == 0) "Off" else "${sec}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audio FX & Sound Shifter Section
            Text(
                text = "Audio FX & Mood Modifiers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Quick Modifiers Chips
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.videhub.audio.AudioPreset.values().forEach { preset ->
                            FilterChip(
                                selected = activeAudioPreset == preset,
                                onClick = {
                                    com.videhub.audio.AudioSpeedPitchManager.applyPreset(preset, context, mediaPlayer)
                                },
                                label = { Text(preset.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Playback Speed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            String.format(java.util.Locale.US, "%.2fx", currentSpeed),
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audio Pitch", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            String.format(java.util.Locale.US, "%.2fx", currentPitch),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
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
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Reset Button
            OutlinedButton(
                onClick = {
                    EqualizerManager.selectPreset("Flat")
                    EqualizerManager.setBassStrength(0)
                    com.videhub.audio.CrossfadeManager.setCrossfadeDuration(context, 0)
                    com.videhub.audio.AudioSpeedPitchManager.reset(context, mediaPlayer)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Effects & EQ")
            }
        }
    }
}
