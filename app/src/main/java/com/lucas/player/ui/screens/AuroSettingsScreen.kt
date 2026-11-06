package com.lucas.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.BuildConfig
import com.lucas.player.R
import com.lucas.player.data.PreferencesManager
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.AuroRadius
import com.lucas.player.ui.theme.LocalAuroAccent
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlin.math.roundToInt

/**
 * AURŌ DESIGN SYSTEM v3 - TELA DE CONFIGURAÇÕES (AURŌ SETTINGS SCREEN)
 * Fase 4.2: Configurações persistidas no PreferencesManager, alinhadas às diretrizes do plano:
 * - Cabeçalhos em Label maiúsculo com Accent A
 * - Linhas mínimas de 56dp com divisor Hairline
 * - AuroSwitch (48x28dp) e AuroSlider com semantics
 * - Seções: Biblioteca, Reprodução, Aparência (com botão de vidro de exemplo ao vivo) e Sobre
 */
@Composable
fun AuroSettingsScreen(
    preferencesManager: PreferencesManager,
    onBackClick: () -> Unit,
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val accent = LocalAuroAccent.current.a.value

    // Estados Reativos de Preferências
    var pauseOnDisconnect by remember { mutableStateOf(preferencesManager.isPauseOnHeadsetDisconnectEnabled()) }
    var gapless by remember { mutableStateOf(preferencesManager.isGaplessEnabled()) }
    var crossfadeSeconds by remember { mutableIntStateOf(preferencesManager.getCrossfadeDurationMs() / 1000) }
    var replayGainMode by remember { mutableIntStateOf(preferencesManager.getReplayGainMode()) }
    var rgPreampWith by remember { mutableFloatStateOf(preferencesManager.getReplayGainPreampWithRg()) }
    var rgPreampWithout by remember { mutableFloatStateOf(preferencesManager.getReplayGainPreampWithoutRg()) }
    var resumePlayback by remember { mutableStateOf(preferencesManager.isResumePlaybackEnabled()) }

    var tiltEnabled by remember { mutableStateOf(preferencesManager.isLightReactionTiltEnabled()) }
    var reflectionSoftness by remember { mutableFloatStateOf(preferencesManager.getReflectionSoftness()) }
    var reduceMotionMode by remember { mutableIntStateOf(preferencesManager.getReduceMotionMode()) }
    var showFileFormat by remember { mutableStateOf(preferencesManager.isShowFileFormatEnabled()) }

    var ignoreShorterThanSec by remember { mutableIntStateOf(preferencesManager.getIgnoreShorterThanSeconds()) }

    // Diálogos de Seleção
    var showReplayGainPicker by remember { mutableStateOf(false) }
    var showReduceMotionPicker by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(
                AuroBreadcrumbItem(stringResource(R.string.library_breadcrumb)),
                AuroBreadcrumbItem(stringResource(R.string.settings_title))
            ),
            onItemClick = { onBackClick() },
            canGoBack = true,
            onBackClick = onBackClick
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 1: BIBLIOTECA
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_library))
            }

            item {
                SettingsClickableRow(
                    title = stringResource(R.string.settings_music_folders),
                    subtitle = stringResource(R.string.settings_music_folders_desc),
                    onClick = { onRescanLibrary() },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = AuroColor.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ignore_shorter_than),
                            style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (ignoreShorterThanSec == 0) {
                                stringResource(R.string.settings_crossfade_off)
                            } else {
                                stringResource(R.string.settings_ignore_shorter_than_sec, ignoreShorterThanSec)
                            },
                            style = AuroType.Label.copy(
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AuroSlider(
                        value = ignoreShorterThanSec.toFloat(),
                        onValueChange = {
                            val rounded = it.roundToInt()
                            ignoreShorterThanSec = rounded
                            preferencesManager.setIgnoreShorterThanSeconds(rounded)
                            onSettingsChanged()
                        },
                        valueRange = 0f..60f,
                        steps = 11
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                SettingsClickableRow(
                    title = stringResource(R.string.settings_rescan_library),
                    subtitle = stringResource(R.string.settings_rescan_library_desc),
                    onClick = {
                        onRescanLibrary()
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 2: REPRODUÇÃO
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_playback))
            }

            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_pause_headset),
                    subtitle = stringResource(R.string.settings_pause_headset_desc),
                    checked = pauseOnDisconnect,
                    onCheckedChange = {
                        pauseOnDisconnect = it
                        preferencesManager.setPauseOnHeadsetDisconnectEnabled(it)
                        onSettingsChanged()
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_gapless),
                    subtitle = stringResource(R.string.settings_gapless_desc),
                    checked = gapless,
                    onCheckedChange = {
                        gapless = it
                        preferencesManager.setGaplessEnabled(it)
                        onSettingsChanged()
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_crossfade),
                                style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (crossfadeSeconds == 0) {
                                    stringResource(R.string.settings_crossfade_off)
                                } else {
                                    stringResource(R.string.settings_crossfade_desc, crossfadeSeconds)
                                },
                                style = AuroType.Label.copy(color = AuroColor.TextSecondary)
                            )
                        }
                        Text(
                            text = "${crossfadeSeconds}s",
                            style = AuroType.Label.copy(
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AuroSlider(
                        value = crossfadeSeconds.toFloat(),
                        onValueChange = {
                            val rounded = it.roundToInt()
                            crossfadeSeconds = rounded
                            preferencesManager.saveCrossfadeDurationMs(rounded * 1000)
                            onSettingsChanged()
                        },
                        valueRange = 0f..12f,
                        steps = 11
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                val rgLabel = when (replayGainMode) {
                    1 -> stringResource(R.string.settings_replaygain_track)
                    2 -> stringResource(R.string.settings_replaygain_album)
                    else -> stringResource(R.string.settings_replaygain_off)
                }
                SettingsClickableRow(
                    title = stringResource(R.string.settings_replaygain),
                    subtitle = rgLabel,
                    onClick = { showReplayGainPicker = true },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = AuroColor.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            if (replayGainMode != 0) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_rg_preamp_with),
                                style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = String.format("%.1f dB", rgPreampWith),
                                style = AuroType.Label.copy(
                                    color = accent,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        AuroSlider(
                            value = rgPreampWith,
                            onValueChange = {
                                rgPreampWith = it
                                preferencesManager.saveReplayGainPreampWithRg(it)
                                onSettingsChanged()
                            },
                            valueRange = -6f..6f,
                            steps = 23
                        )
                    }
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_rg_preamp_without),
                            style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = String.format("%.1f dB", rgPreampWithout),
                            style = AuroType.Label.copy(
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AuroSlider(
                        value = rgPreampWithout,
                        onValueChange = {
                            rgPreampWithout = it
                            preferencesManager.saveReplayGainPreampWithoutRg(it)
                            onSettingsChanged()
                        },
                        valueRange = -6f..6f,
                        steps = 23
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_resume_playback),
                    subtitle = stringResource(R.string.settings_resume_playback_desc),
                    checked = resumePlayback,
                    onCheckedChange = {
                        resumePlayback = it
                        preferencesManager.setResumePlaybackEnabled(it)
                        onSettingsChanged()
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 3: APARÊNCIA
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            }

            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_tilt_light),
                    subtitle = stringResource(R.string.settings_tilt_light_desc),
                    checked = tiltEnabled,
                    onCheckedChange = {
                        tiltEnabled = it
                        preferencesManager.setLightReactionTiltEnabled(it)
                        onSettingsChanged()
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_reflection_softness),
                                style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_reflection_softness_desc),
                                style = AuroType.Label.copy(color = AuroColor.TextSecondary)
                            )
                        }
                        Text(
                            text = String.format("%.2f", reflectionSoftness),
                            style = AuroType.Label.copy(
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    AuroSlider(
                        value = reflectionSoftness,
                        onValueChange = {
                            reflectionSoftness = it
                            preferencesManager.setReflectionSoftness(it)
                            onSettingsChanged()
                        },
                        valueRange = 0f..1f,
                        steps = 20
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Exemplo interativo de vidro ao vivo com o valor do slider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AuroRadius.card))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_reflection_preview),
                            style = AuroType.Label.copy(color = AuroColor.TextSecondary)
                        )

                        AuroGlass(
                            kind = AuroGlassKind.Button,
                            soft = reflectionSoftness,
                            onClick = { },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                val motionLabel = when (reduceMotionMode) {
                    1 -> stringResource(R.string.settings_reduce_motion_always)
                    2 -> stringResource(R.string.settings_reduce_motion_never)
                    else -> stringResource(R.string.settings_reduce_motion_system)
                }
                SettingsClickableRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    subtitle = motionLabel,
                    onClick = { showReduceMotionPicker = true },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = AuroColor.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_show_file_format),
                    subtitle = stringResource(R.string.settings_show_file_format_desc),
                    checked = showFileFormat,
                    onCheckedChange = {
                        showFileFormat = it
                        preferencesManager.setShowFileFormatEnabled(it)
                        onSettingsChanged()
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 4: SOBRE
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_about))
            }

            item {
                SettingsClickableRow(
                    title = stringResource(R.string.settings_version),
                    subtitle = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                SettingsClickableRow(
                    title = stringResource(R.string.settings_licenses),
                    subtitle = "Apache 2.0, MIT, Jetpack Compose, Oboe",
                    onClick = { showLicensesDialog = true }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            item {
                SettingsClickableRow(
                    title = stringResource(R.string.settings_repository),
                    subtitle = "GitHub • Aurō Audio Engine v2.0",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Lusqta/Player"))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = AuroColor.TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }
        }
    }

    // Modal Sheet de Escolha do ReplayGain
    if (showReplayGainPicker) {
        AuroOverlay(
            visible = true,
            onDismiss = { showReplayGainPicker = false },
            type = AuroOverlayType.BottomSheet
        ) {
            Surface(
                color = AuroColor.Black,
                shape = RoundedCornerShape(topStart = AuroRadius.card, topEnd = AuroRadius.card),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AuroColor.Hairline, RoundedCornerShape(topStart = AuroRadius.card, topEnd = AuroRadius.card))
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.settings_replaygain),
                        style = AuroType.Subtitle.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AuroDialogOption(
                        icon = if (replayGainMode == 0) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = stringResource(R.string.settings_replaygain_off),
                        onClick = {
                            replayGainMode = 0
                            preferencesManager.saveReplayGainMode(0)
                            showReplayGainPicker = false
                            onSettingsChanged()
                        }
                    )
                    AuroDialogOption(
                        icon = if (replayGainMode == 1) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = stringResource(R.string.settings_replaygain_track),
                        onClick = {
                            replayGainMode = 1
                            preferencesManager.saveReplayGainMode(1)
                            showReplayGainPicker = false
                            onSettingsChanged()
                        }
                    )
                    AuroDialogOption(
                        icon = if (replayGainMode == 2) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = stringResource(R.string.settings_replaygain_album),
                        onClick = {
                            replayGainMode = 2
                            preferencesManager.saveReplayGainMode(2)
                            showReplayGainPicker = false
                            onSettingsChanged()
                        }
                    )
                }
            }
        }
    }

    // Modal Sheet de Escolha de Animações
    if (showReduceMotionPicker) {
        AuroOverlay(
            visible = true,
            onDismiss = { showReduceMotionPicker = false },
            type = AuroOverlayType.BottomSheet
        ) {
            Surface(
                color = AuroColor.Black,
                shape = RoundedCornerShape(topStart = AuroRadius.card, topEnd = AuroRadius.card),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AuroColor.Hairline, RoundedCornerShape(topStart = AuroRadius.card, topEnd = AuroRadius.card))
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.settings_reduce_motion),
                        style = AuroType.Subtitle.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AuroDialogOption(
                        icon = if (reduceMotionMode == 0) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = stringResource(R.string.settings_reduce_motion_system),
                        onClick = {
                            reduceMotionMode = 0
                            preferencesManager.setReduceMotionMode(0)
                            showReduceMotionPicker = false
                            onSettingsChanged()
                        }
                    )
                    AuroDialogOption(
                        icon = if (reduceMotionMode == 1) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = stringResource(R.string.settings_reduce_motion_always),
                        onClick = {
                            reduceMotionMode = 1
                            preferencesManager.setReduceMotionMode(1)
                            showReduceMotionPicker = false
                            onSettingsChanged()
                        }
                    )
                    AuroDialogOption(
                        icon = if (reduceMotionMode == 2) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = stringResource(R.string.settings_reduce_motion_never),
                        onClick = {
                            reduceMotionMode = 2
                            preferencesManager.setReduceMotionMode(2)
                            showReduceMotionPicker = false
                            onSettingsChanged()
                        }
                    )
                }
            }
        }
    }

    // Diálogo de Licenças
    if (showLicensesDialog) {
        AuroOverlay(
            visible = true,
            onDismiss = { showLicensesDialog = false },
            type = AuroOverlayType.Dialog
        ) {
            Surface(
                color = AuroColor.Black,
                shape = RoundedCornerShape(AuroRadius.card),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.settings_licenses),
                        style = AuroType.Subtitle.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_licenses_text),
                        style = AuroType.Body.copy(color = AuroColor.TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = stringResource(R.string.close),
                            style = AuroType.Label.copy(color = accent, fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showLicensesDialog = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = AuroType.SectionHeader.copy(
            color = LocalAuroAccent.current.a.value,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        ),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = AuroType.Label.copy(color = AuroColor.TextSecondary)
            )
        }
        AuroSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(clickMod)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = AuroType.Label.copy(color = AuroColor.TextSecondary)
            )
        }
        trailingContent?.invoke()
    }
}
