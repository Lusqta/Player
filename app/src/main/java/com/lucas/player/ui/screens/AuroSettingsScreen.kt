package com.lucas.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.lucas.player.data.BackupManager
import com.lucas.player.data.PreferencesManager
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.AuroRadius
import com.lucas.player.ui.theme.AuroSpace
import com.lucas.player.ui.theme.LiquidGlassSurface
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlin.math.roundToInt

enum class BackupOperationState {
    IDLE,
    EXPORTING,
    RESTORING
}

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
    selectedFolders: List<String> = emptyList(),
    onAddMusicFolder: () -> Unit = {},
    onRemoveMusicFolder: (String) -> Unit = {},
    onSelectMusicFolder: () -> Unit = {},
    onClearMusicFolder: () -> Unit = {},
    pauseGifs: Boolean = false,
    onPauseGifsChanged: (Boolean) -> Unit = {},
    backupState: BackupOperationState = BackupOperationState.IDLE,
    onExportBackup: (Uri) -> Unit = {},
    onRestoreBackup: (Uri) -> Unit = {},
    modifier: Modifier = Modifier,
    onSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    // Estados Reativos de Preferências
    var pauseOnDisconnect by remember { mutableStateOf(preferencesManager.isPauseOnHeadsetDisconnectEnabled()) }
    var gapless by remember { mutableStateOf(preferencesManager.isGaplessEnabled()) }
    var crossfadeSeconds by remember { mutableIntStateOf(preferencesManager.getCrossfadeDurationMs() / 1000) }
    var replayGainMode by remember { mutableIntStateOf(preferencesManager.getReplayGainMode()) }
    var rgPreampWith by remember { mutableFloatStateOf(preferencesManager.getReplayGainPreampWithRg()) }
    var rgPreampWithout by remember { mutableFloatStateOf(preferencesManager.getReplayGainPreampWithoutRg()) }
    var resumePlayback by remember { mutableStateOf(preferencesManager.isResumePlaybackEnabled()) }

    var reduceMotionMode by remember { mutableIntStateOf(preferencesManager.getReduceMotionMode()) }
    var showFileFormat by remember { mutableStateOf(preferencesManager.isShowFileFormatEnabled()) }

    var ignoreShorterThanSec by remember { mutableIntStateOf(preferencesManager.getIgnoreShorterThanSeconds()) }
    val currentFolders = if (selectedFolders.isNotEmpty()) selectedFolders else preferencesManager.getMusicFolderUris()

    // Diálogos de Seleção
    var showReplayGainPicker by remember { mutableStateOf(false) }
    var showReduceMotionPicker by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onExportBackup(uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        AuroTopBar(
            title = stringResource(R.string.settings_title),
            canGoBack = true,
            onBackClick = onBackClick
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp)
            ) {
            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 1: BIBLIOTECA
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_library))
                SettingsCard {
                    if (currentFolders.isEmpty()) {
                        SettingsClickableRow(
                            title = stringResource(R.string.settings_music_folders),
                            subtitle = stringResource(R.string.settings_music_folders_empty),
                            onClick = {
                                onAddMusicFolder()
                                onSelectMusicFolder()
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = AuroColor.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    } else {
                        currentFolders.forEach { uriStr ->
                            val folderDisplay = preferencesManager.formatFolderName(uriStr)
                            SettingsClickableRow(
                                title = folderDisplay,
                                subtitle = uriStr,
                                onClick = {},
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            onRemoveMusicFolder(uriStr)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.settings_music_folder_clear),
                                            tint = AuroColor.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                            HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                        }
                        SettingsClickableRow(
                            title = stringResource(R.string.settings_add_folder),
                            subtitle = stringResource(R.string.settings_add_folder_desc),
                            onClick = {
                                onAddMusicFolder()
                                onSelectMusicFolder()
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = AuroColor.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                                    color = Color.White,
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
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                    SettingsClickableRow(
                        title = stringResource(R.string.settings_rescan_library),
                        subtitle = stringResource(R.string.settings_rescan_library_desc),
                        onClick = { onRescanLibrary() },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )
                }
            }

            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 2: REPRODUÇÃO
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_playback))
                SettingsCard {
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
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                                    color = Color.White,
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
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                    if (replayGainMode != 0) {
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                                        color = Color.White,
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
                    }
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                                    color = Color.White,
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
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                }
            }

            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 3: APARÊNCIA
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
                SettingsCard {
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
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_pause_gifs),
                        subtitle = stringResource(R.string.settings_pause_gifs_desc),
                        checked = pauseGifs,
                        onCheckedChange = {
                            preferencesManager.setPauseGifsEnabled(it)
                            onPauseGifsChanged(it)
                            onSettingsChanged()
                        }
                    )
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                }
            }

            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 4: BACKUP E PERMANÊNCIA
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_backup))
                SettingsCard {
                    SettingsClickableRow(
                        title = stringResource(R.string.settings_backup_export),
                        subtitle = if (backupState == BackupOperationState.EXPORTING) {
                            stringResource(R.string.settings_backup_exporting)
                        } else {
                            stringResource(R.string.settings_backup_export_desc)
                        },
                        onClick = if (backupState == BackupOperationState.IDLE) {
                            { exportBackupLauncher.launch(BackupManager.defaultBackupFileName()) }
                        } else null,
                        trailingContent = {
                            if (backupState == BackupOperationState.EXPORTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Upload,
                                    contentDescription = null,
                                    tint = AuroColor.TextTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                    SettingsClickableRow(
                        title = stringResource(R.string.settings_backup_restore),
                        subtitle = if (backupState == BackupOperationState.RESTORING) {
                            stringResource(R.string.settings_backup_restoring)
                        } else {
                            stringResource(R.string.settings_backup_restore_desc)
                        },
                        onClick = if (backupState == BackupOperationState.IDLE) {
                            { restoreBackupLauncher.launch(arrayOf("application/zip")) }
                        } else null,
                        trailingContent = {
                            if (backupState == BackupOperationState.RESTORING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = AuroColor.TextTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                }
            }

            // ─────────────────────────────────────────────────────────────
            // SEÇÃO 5: SOBRE
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_about))
                SettingsCard {
                    SettingsClickableRow(
                        title = stringResource(R.string.settings_version),
                        subtitle = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
                    )
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                    SettingsClickableRow(
                        title = stringResource(R.string.settings_licenses),
                        subtitle = "Apache 2.0, MIT, Jetpack Compose, Oboe",
                        onClick = { showLicensesDialog = true }
                    )
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
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
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = AuroColor.TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        // Fade sutil de 16dp no topo
        AuroTopFade(modifier = Modifier.align(Alignment.TopCenter))
    }
}

    // Modal Sheet de Escolha do ReplayGain
    if (showReplayGainPicker) {
        AuroOverlay(
            visible = true,
            onDismiss = { showReplayGainPicker = false },
            type = AuroOverlayType.BottomSheet
        ) {
            AuroSheetContainer(
                title = stringResource(R.string.settings_replaygain),
                onDismiss = { showReplayGainPicker = false }
            ) {
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

    // Modal Sheet de Escolha de Animações
    if (showReduceMotionPicker) {
        AuroOverlay(
            visible = true,
            onDismiss = { showReduceMotionPicker = false },
            type = AuroOverlayType.BottomSheet
        ) {
            AuroSheetContainer(
                title = stringResource(R.string.settings_reduce_motion),
                onDismiss = { showReduceMotionPicker = false }
            ) {
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

    // Diálogo de Licenças
    if (showLicensesDialog) {
        AuroOverlay(
            visible = true,
            onDismiss = { showLicensesDialog = false },
            type = AuroOverlayType.Dialog
        ) {
            AuroDialogContainer(
                title = stringResource(R.string.settings_licenses),
                onDismiss = { showLicensesDialog = false },
                actions = {
                    AuroDialogTextButton(
                        text = stringResource(R.string.close),
                        onClick = { showLicensesDialog = false }
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.settings_licenses_text),
                    style = AuroType.Body.copy(color = AuroColor.TextSecondary)
                )
            }
        }
    }

    pendingRestoreUri?.let { restoreUri ->
        AuroOverlay(
            visible = true,
            onDismiss = { pendingRestoreUri = null },
            type = AuroOverlayType.Dialog
        ) {
            AuroDialogContainer(
                title = stringResource(R.string.settings_backup_restore_confirm_title),
                onDismiss = { pendingRestoreUri = null },
                actions = {
                    AuroDialogTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { pendingRestoreUri = null }
                    )
                    AuroDialogTextButton(
                        text = stringResource(R.string.settings_backup_restore_confirm_action),
                        onClick = {
                            pendingRestoreUri = null
                            onRestoreBackup(restoreUri)
                        }
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_restore_confirm_message),
                    style = AuroType.Body.copy(color = AuroColor.TextSecondary)
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuroSpace.ScreenMargin)
            .clip(RoundedCornerShape(24.dp))
            .background(AuroColor.SurfaceCard),
        content = content
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = AuroType.Subtitle.copy(
            color = AuroColor.TextTertiary,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier.padding(start = AuroSpace.ScreenMargin, top = 20.dp, bottom = 8.dp)
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
