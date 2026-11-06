package com.lucas.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.MusicDatabase
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.LocalAuroAccent
import com.lucas.player.ui.theme.LocalAuroBottomInset
import java.util.Locale
import kotlin.math.roundToInt

/**
 * AURŌ DESIGN SYSTEM v2 - TELA DE EQUALIZADOR (AURŌ EQUALIZER)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroEqualizerScreen(
    eqEnabled: Boolean,
    onToggleEq: (Boolean) -> Unit,
    toneEnabled: Boolean,
    onToggleTone: (Boolean) -> Unit,
    preampDb: Float,
    onPreampChange: (Float) -> Unit,
    bassDb: Float,
    onBassChange: (Float) -> Unit,
    trebleDb: Float,
    onTrebleChange: (Float) -> Unit,
    balance: Float,
    onBalanceChange: (Float) -> Unit,
    isMono: Boolean,
    onToggleMono: (Boolean) -> Unit,
    bandGains: List<Float>,
    onBandGainChange: (Int, Float) -> Unit,
    activePresetName: String,
    presets: List<MusicDatabase.EqPreset>,
    onSelectPreset: (MusicDatabase.EqPreset) -> Unit,
    onResetEq: () -> Unit = {},
    onSavePreset: () -> Unit = {},
    onDeletePreset: (MusicDatabase.EqPreset) -> Unit = {},
    onClose: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val accent = LocalAuroAccent.current.a.value
    val view = LocalView.current
    val bandFreqs = remember {
        listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(
                AuroBreadcrumbItem(stringResource(R.string.library_breadcrumb)),
                AuroBreadcrumbItem(stringResource(R.string.equalizer_breadcrumb))
            ),
            onItemClick = { onClose() },
            canGoBack = true,
            onBackClick = onClose,
            rightActions = {
                AuroSwitch(
                    checked = eqEnabled,
                    onCheckedChange = onToggleEq
                )
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = LocalAuroBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Presets
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.eq_presets_header),
                        style = AuroType.Caption.copy(
                            color = AuroColor.TextTertiary,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.eq_reset),
                            style = AuroType.Label.copy(color = AuroColor.TextSecondary),
                            modifier = Modifier
                                .clickable { onResetEq() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.eq_save),
                            style = AuroType.Label.copy(
                                color = accent,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clickable { onSavePreset() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets, key = { it.id }) { p ->
                        val isSelected = p.name.equals(activePresetName, ignoreCase = true)
                        AuroGlass(
                            kind = AuroGlassKind.Card,
                            onClick = { onSelectPreset(p) },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 14.dp)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = p.name,
                                    style = AuroType.Body.copy(
                                        color = if (isSelected) accent else AuroColor.TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                                if (!p.isBuiltin) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable { onDeletePreset(p) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_delete_preset),
                                            tint = AuroColor.TextTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tom & Pré-amp
            item {
                AuroGlass(
                    kind = AuroGlassKind.Card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.eq_tone_preamp_header),
                                    style = AuroType.Caption.copy(
                                        color = AuroColor.TextTertiary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                AuroSwitch(
                                    checked = toneEnabled,
                                    onCheckedChange = onToggleTone,
                                    enabled = eqEnabled
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.eq_mono),
                                    style = AuroType.Label.copy(color = AuroColor.TextSecondary)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Checkbox(
                                    checked = isMono,
                                    onCheckedChange = onToggleMono,
                                    colors = CheckboxDefaults.colors(checkedColor = accent)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        AuroToneRow(label = stringResource(R.string.eq_preamp_label), value = preampDb, range = -12f..12f, enabled = eqEnabled, onValueChange = onPreampChange)
                        AuroToneRow(label = stringResource(R.string.eq_bass_label), value = bassDb, range = -12f..12f, enabled = eqEnabled && toneEnabled, onValueChange = onBassChange)
                        AuroToneRow(label = stringResource(R.string.eq_treble_label), value = trebleDb, range = -12f..12f, enabled = eqEnabled && toneEnabled, onValueChange = onTrebleChange)
                        AuroToneRow(label = stringResource(R.string.eq_balance_label), value = balance, range = -1f..1f, isBalance = true, enabled = true, onValueChange = onBalanceChange)
                    }
                }
            }

            // 10 Bandas
            item {
                AuroGlass(
                    kind = AuroGlassKind.Card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.eq_bands_header),
                            style = AuroType.Caption.copy(
                                color = AuroColor.TextTertiary,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        bandFreqs.forEachIndexed { bandIndex, freqLabel ->
                            val gain = bandGains.getOrElse(bandIndex) { 0f }
                            val formattedGain = String.format(Locale.getDefault(), "%+.1f dB", gain)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = freqLabel,
                                    style = AuroType.Caption.copy(
                                        color = AuroColor.TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.width(50.dp)
                                )

                                AuroSlider(
                                    value = gain,
                                    onValueChange = { onBandGainChange(bandIndex, it) },
                                    valueRange = -12f..12f,
                                    contentDescription = stringResource(R.string.cd_eq_band, freqLabel),
                                    stateDescription = formattedGain,
                                    enabled = eqEnabled,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                )

                                Text(
                                    text = formattedGain,
                                    style = AuroType.Caption.copy(
                                        color = AuroColor.TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(54.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroToneRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isBalance: Boolean = false,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AuroType.Label.copy(
                color = if (enabled) AuroColor.TextSecondary else AuroColor.TextTertiary
            ),
            modifier = Modifier.width(86.dp)
        )
        val textVal = if (isBalance) {
            when {
                value < -0.05f -> "L ${(value * -100).roundToInt()}%"
                value > 0.05f -> "R ${(value * 100).roundToInt()}%"
                else -> stringResource(R.string.eq_balance_center)
            }
        } else {
            String.format(Locale.getDefault(), "%+.1f dB", value)
        }
        AuroSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            contentDescription = label,
            stateDescription = textVal,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
        )
        Text(
            text = textVal,
            style = AuroType.Caption.copy(
                color = if (enabled) AuroColor.TextPrimary else AuroColor.TextTertiary,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            ),
            textAlign = TextAlign.End,
            modifier = Modifier.width(54.dp)
        )
    }
}
