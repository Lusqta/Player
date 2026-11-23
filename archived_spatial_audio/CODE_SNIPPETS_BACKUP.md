# Backup de Trechos de Código Removidos — Áudio Espacial (JamesDSP & Steam Audio)

Este arquivo contém a cópia integral de todos os trechos de código que integravam o Áudio Espacial (Modo 1: JamesDSP / Dolby Padrão e Modo 2: Steam Audio Pro / `libphonon.so`) no Aurō Player antes da sua remoção completa.

---

## 1. `app/src/main/cpp/CMakeLists.txt`

### Headers incluídos:
```cmake
    ${CMAKE_CURRENT_SOURCE_DIR}/jamesdsp
    ${CMAKE_CURRENT_SOURCE_DIR}/steamaudio/include
```

### Import da biblioteca `phonon`:
```cmake
add_library(phonon SHARED IMPORTED)
set_target_properties(phonon PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libphonon.so"
)
```

### Arquivos C compilados na `audio_engine`:
```cmake
    jamesdsp/reverb.c
    jamesdsp/stereoEnhancement.c
    jamesdsp/polyphaseFilterbank.c
    jamesdsp/cos_fib_paraunitary.c
    jamesdsp/qr_fact.c
```

### Linkagem da biblioteca:
```cmake
target_link_libraries(audio_engine PRIVATE
    ...
    phonon
)
```

---

## 2. `app/src/main/cpp/jni_bridge.cpp`

```cpp
JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetSpatialMode(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint mode) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setSpatialMode(mode);
    } catch (...) {}
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetSpatialPreset(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint preset, jfloat intensity) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setSpatialPreset(preset, intensity);
    } catch (...) {}
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetSpatialProParams(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle,
        jfloat subCutoffHz, jfloat midGainDb, jfloat sideWidth, jfloat surroundAmbience, jfloat speakerAngle, jfloat elevation) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setSpatialProParams(subCutoffHz, midGainDb, sideWidth, surroundAmbience, speakerAngle, elevation);
    } catch (...) {}
}
```

---

## 3. `app/src/main/cpp/audio_engine.h` e `audio_engine.cpp`

### `audio_engine.h`:
```cpp
#include "spatial_processor.h"

// Métodos públicos:
void setSpatialMode(int mode);
void setSpatialPreset(int preset, float intensity);
void setSpatialProParams(float subCutoffHz, float midGainDb, float sideWidth, float surroundAmbience, float speakerAngle, float elevation = 0.0f);

// Membro privado:
SpatialProcessor mSpatialProcessor;
```

### `audio_engine.cpp`:
```cpp
// No construtor:
AudioEngine::AudioEngine()
    : ringBuffer_(131072),
      audioOutput_(std::make_unique<AudioOutput>(&ringBuffer_, &mSpatialProcessor)) {
...

// Em openFile:
const int32_t sampleRate = decoder_->getSampleRate();
mSpatialProcessor.setSampleRate(sampleRate);

// Implementações:
void AudioEngine::setSpatialMode(int mode) {
    mSpatialProcessor.setMode(mode);
}

void AudioEngine::setSpatialPreset(int preset, float intensity) {
    mSpatialProcessor.setStandardPreset(preset, intensity);
}

void AudioEngine::setSpatialProParams(float subCutoffHz, float midGainDb, float sideWidth, float surroundAmbience, float speakerAngle, float elevation) {
    mSpatialProcessor.setProParams(subCutoffHz, midGainDb, sideWidth, surroundAmbience, speakerAngle, elevation);
}
```

---

## 4. `app/src/main/cpp/audio_output.h` e `audio_output.cpp`

### `audio_output.h`:
```cpp
#include "spatial_processor.h"

// Construtor:
explicit AudioOutput(SpscRingBuffer* ringBuffer, SpatialProcessor* spatialProcessor = nullptr);

// Membro:
SpatialProcessor* spatialProcessor_ = nullptr;
```

### `audio_output.cpp`:
```cpp
AudioOutput::AudioOutput(SpscRingBuffer* ringBuffer, SpatialProcessor* spatialProcessor)
    : ringBuffer_(ringBuffer), spatialProcessor_(spatialProcessor) {}

// Em openStream:
if (spatialProcessor_) {
    spatialProcessor_->setSampleRate(sampleRate);
}

// Em onAudioReady:
if (spatialProcessor_) {
    spatialProcessor_->process(out, static_cast<int>(numFrames), 2);
}
```

---

## 5. `app/src/main/java/com/lucas/player/AudioEngine.kt`

```kotlin
// No companion object init:
System.loadLibrary("phonon")

// Métodos públicos:
fun setSpatialMode(mode: Int) {
    if (nativeHandle == 0L) return
    nativeSetSpatialMode(nativeHandle, mode)
}

fun setSpatialPreset(preset: Int, intensity: Float) {
    if (nativeHandle == 0L) return
    nativeSetSpatialPreset(nativeHandle, preset, intensity)
}

fun setSpatialProParams(
    subCutoffHz: Float,
    midGainDb: Float,
    sideWidth: Float,
    surroundAmbience: Float,
    speakerAngle: Float,
    elevation: Float = 0f
) {
    if (nativeHandle == 0L) return
    nativeSetSpatialProParams(nativeHandle, subCutoffHz, midGainDb, sideWidth, surroundAmbience, speakerAngle, elevation)
}

// Métodos externos nativos:
private external fun nativeSetSpatialMode(handle: Long, mode: Int)
private external fun nativeSetSpatialPreset(handle: Long, preset: Int, intensity: Float)
private external fun nativeSetSpatialProParams(
    handle: Long,
    subCutoffHz: Float,
    midGainDb: Float,
    sideWidth: Float,
    surroundAmbience: Float,
    speakerAngle: Float,
    elevation: Float
)
```

---

## 6. `app/src/main/java/com/lucas/player/service/PlaybackService.kt`

```kotlin
// Em onCreate():
// Restore Spatial Audio (HRTF + FDN Room 4x4 + LR4)
audioEngine.setSpatialMode(preferencesManager.spatialMode)
audioEngine.setSpatialPreset(preferencesManager.spatialPreset, preferencesManager.spatialIntensity)
audioEngine.setSpatialProParams(
    preferencesManager.spatialSubCutoff,
    preferencesManager.spatialMidGain,
    preferencesManager.spatialSideWidth,
    preferencesManager.spatialSurround,
    preferencesManager.spatialAngle,
    preferencesManager.spatialElevation
)

// Métodos públicos de serviço:
// --- Spatial Audio DSP ---
fun setSpatialMode(mode: Int) {
    audioEngine.setSpatialMode(mode)
    preferencesManager.spatialMode = mode
}

fun getSpatialMode(): Int = preferencesManager.spatialMode

fun setSpatialPreset(preset: Int, intensity: Float) {
    audioEngine.setSpatialPreset(preset, intensity)
    preferencesManager.spatialPreset = preset
    preferencesManager.spatialIntensity = intensity
}

fun getSpatialPreset(): Int = preferencesManager.spatialPreset
fun getSpatialIntensity(): Float = preferencesManager.spatialIntensity

fun setSpatialProParams(
    subCutoffHz: Float,
    midGainDb: Float,
    sideWidth: Float,
    surroundAmbience: Float,
    speakerAngle: Float,
    elevation: Float
) {
    audioEngine.setSpatialProParams(subCutoffHz, midGainDb, sideWidth, surroundAmbience, speakerAngle, elevation)
    preferencesManager.spatialSubCutoff = subCutoffHz
    preferencesManager.spatialMidGain = midGainDb
    preferencesManager.spatialSideWidth = sideWidth
    preferencesManager.spatialSurround = surroundAmbience
    preferencesManager.spatialAngle = speakerAngle
    preferencesManager.spatialElevation = elevation
}
```

---

## 7. `app/src/main/java/com/lucas/player/data/PreferencesManager.kt`

```kotlin
var spatialMode: Int
    get() = prefs.getInt(KEY_SPATIAL_MODE, 0)
    set(value) = prefs.edit().putInt(KEY_SPATIAL_MODE, value).apply()

var spatialPreset: Int
    get() = prefs.getInt(KEY_SPATIAL_PRESET, 0)
    set(value) = prefs.edit().putInt(KEY_SPATIAL_PRESET, value).apply()

var spatialIntensity: Float
    get() = prefs.getFloat(KEY_SPATIAL_INTENSITY, 0.85f)
    set(value) = prefs.edit().putFloat(KEY_SPATIAL_INTENSITY, value).apply()

var spatialSubCutoff: Float
    get() = prefs.getFloat(KEY_SPATIAL_SUB_CUTOFF, 110f)
    set(value) = prefs.edit().putFloat(KEY_SPATIAL_SUB_CUTOFF, value).apply()

var spatialMidGain: Float
    get() = prefs.getFloat(KEY_SPATIAL_MID_GAIN, 0.5f)
    set(value) = prefs.edit().putFloat(KEY_SPATIAL_MID_GAIN, value).apply()

var spatialSideWidth: Float
    get() = prefs.getFloat(KEY_SPATIAL_SIDE_WIDTH, 1.25f)
    set(value) = prefs.edit().putFloat(KEY_SPATIAL_SIDE_WIDTH, value).apply()

var spatialSurround: Float
    get() = prefs.getFloat(KEY_SPATIAL_SURROUND, 0.28f)
    set(value) = prefs.edit().putFloat(KEY_SPATIAL_SURROUND, value).apply()

var spatialAngle: Float
    get() = prefs.getFloat(KEY_SPATIAL_ANGLE, 0.30f)
    set(value) = prefs.edit().putFloat(KEY_SPATIAL_ANGLE, value).apply()

var spatialElevation: Float
    get() = prefs.getFloat(KEY_SPATIAL_ELEVATION, 0f)
    set(value) = prefs.edit().putFloat(KEY_SPATIAL_ELEVATION, value).apply()

var spatialProPreset: Int
    get() = prefs.getInt(KEY_SPATIAL_PRO_PRESET, 0)
    set(value) = prefs.edit().putInt(KEY_SPATIAL_PRO_PRESET, value).apply()

// Constantes em companion object:
private const val KEY_SPATIAL_MODE = "spatial_mode"
private const val KEY_SPATIAL_PRESET = "spatial_preset"
private const val KEY_SPATIAL_INTENSITY = "spatial_intensity"
private const val KEY_SPATIAL_SUB_CUTOFF = "spatial_sub_cutoff"
private const val KEY_SPATIAL_MID_GAIN = "spatial_mid_gain"
private const val KEY_SPATIAL_SIDE_WIDTH = "spatial_side_width"
private const val KEY_SPATIAL_SURROUND = "spatial_surround"
private const val KEY_SPATIAL_ANGLE = "spatial_angle"
private const val KEY_SPATIAL_ELEVATION = "spatial_elevation"
private const val KEY_SPATIAL_PRO_PRESET = "spatial_pro_preset"
```

---

## 8. `app/src/main/java/com/lucas/player/ui/screens/AuroEqualizerScreen.kt`

### Parâmetros do Composable:
```kotlin
    spatialMode: Int = 0,
    onSpatialModeChange: (Int) -> Unit = {},
    spatialPreset: Int = 0,
    onSpatialPresetChange: (Int) -> Unit = {},
    spatialIntensity: Float = 0.75f,
    onSpatialIntensityChange: (Float) -> Unit = {},
    spatialSubCutoff: Float = 120f,
    onSpatialSubCutoffChange: (Float) -> Unit = {},
    spatialMidGain: Float = 0f,
    onSpatialMidGainChange: (Float) -> Unit = {},
    spatialSideWidth: Float = 1.3f,
    onSpatialSideWidthChange: (Float) -> Unit = {},
    spatialSurround: Float = 0.35f,
    onSpatialSurroundChange: (Float) -> Unit = {},
    spatialAngle: Float = 0.4f,
    onSpatialAngleChange: (Float) -> Unit = {},
    spatialElevation: Float = 0f,
    onSpatialElevationChange: (Float) -> Unit = {},
    spatialProPreset: Int = 0,
    onSpatialProPresetChange: (Int) -> Unit = {},
    onResetSpatialPro: () -> Unit = {},
```

### Card de Áudio Espacial:
```kotlin
            // Áudio Espacial (3 Modos: Desativado, Dolby Padrão e Espacial Pro com Valve Steam Audio)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AuroColor.SurfaceCard)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.spatial_header),
                            style = AuroType.Caption.copy(
                                color = AuroColor.TextTertiary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val view = LocalView.current
                    val modeLabels = listOf(
                        stringResource(R.string.spatial_mode_off),
                        stringResource(R.string.spatial_mode_standard),
                        stringResource(R.string.spatial_mode_pro)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(19.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        modeLabels.forEachIndexed { index, label ->
                            val isSelected = spatialMode == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.60f) else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        AuroHaptics.tick(view)
                                        onSpatialModeChange(index)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = AuroType.Caption.copy(
                                        color = if (isSelected) Color.White else AuroColor.TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Modo 0: Desativado
                    if (spatialMode == 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.spatial_off_desc),
                            style = AuroType.Caption.copy(color = AuroColor.TextTertiary)
                        )
                    }

                    // Modo 1: Dolby Padrão
                    if (spatialMode == 1) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val presetLabels = listOf(
                            stringResource(R.string.spatial_preset_music),
                            stringResource(R.string.spatial_preset_cinema),
                            stringResource(R.string.spatial_preset_stage)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetLabels.forEachIndexed { index, label ->
                                val isSelected = spatialPreset == index
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(17.dp)
                                        )
                                        .clickable {
                                            AuroHaptics.tick(view)
                                            onSpatialPresetChange(index)
                                        }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = AuroType.Body.copy(
                                            color = if (isSelected) Color.White else AuroColor.TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val formattedIntensity = "${(spatialIntensity * 100).roundToInt()}%"
                        AuroSpatialSliderRow(
                            label = stringResource(R.string.spatial_intensity_label),
                            value = spatialIntensity,
                            range = 0f..1f,
                            formattedValue = formattedIntensity,
                            onValueChange = onSpatialIntensityChange
                        )
                    }

                    // Modo 2: Espacial Pro
                    if (spatialMode == 2) {
                        Spacer(modifier = Modifier.height(14.dp))

                        val proPresetLabels = listOf(
                            "Estúdio Nx",
                            "Abbey Road",
                            "Cinema 360°",
                            "Arena 3D",
                            "Personalizado"
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(proPresetLabels.size) { index ->
                                val label = proPresetLabels[index]
                                val isSelected = spatialProPreset == index
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(17.dp)
                                        )
                                        .clickable {
                                            AuroHaptics.tick(view)
                                            onSpatialProPresetChange(index)
                                        }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = AuroType.Body.copy(
                                            color = if (isSelected) Color.White else AuroColor.TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Parâmetros acústicos 3D",
                                style = AuroType.Caption.copy(
                                    color = AuroColor.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            AuroGlass(
                                kind = AuroGlassKind.Card,
                                cornerRadius = 16.dp,
                                onClick = onResetSpatialPro,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp)
                                        .fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.RestartAlt,
                                        contentDescription = null,
                                        tint = AuroColor.TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.spatial_reset_pro),
                                        style = AuroType.Label.copy(
                                            color = AuroColor.TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AuroSpatialSliderRow(
                            label = stringResource(R.string.spatial_sub_cutoff_label),
                            value = spatialSubCutoff,
                            range = 60f..250f,
                            formattedValue = "${spatialSubCutoff.roundToInt()} Hz",
                            onValueChange = onSpatialSubCutoffChange
                        )

                        AuroSpatialSliderRow(
                            label = stringResource(R.string.spatial_mid_gain_label),
                            value = spatialMidGain,
                            range = -6f..6f,
                            formattedValue = String.format(Locale.getDefault(), "%+.1f dB", spatialMidGain),
                            onValueChange = onSpatialMidGainChange
                        )

                        AuroSpatialSliderRow(
                            label = stringResource(R.string.spatial_side_width_label),
                            value = spatialSideWidth,
                            range = 0.5f..2.2f,
                            formattedValue = "${(spatialSideWidth * 100).roundToInt()}%",
                            onValueChange = onSpatialSideWidthChange
                        )

                        AuroSpatialSliderRow(
                            label = stringResource(R.string.spatial_surround_label),
                            value = spatialSurround,
                            range = 0f..1f,
                            formattedValue = "${(spatialSurround * 100).roundToInt()}%",
                            onValueChange = onSpatialSurroundChange
                        )

                        val degrees = (15f + spatialAngle * 50f).roundToInt()
                        AuroSpatialSliderRow(
                            label = stringResource(R.string.spatial_angle_label),
                            value = spatialAngle,
                            range = 0f..1f,
                            formattedValue = "$degrees°",
                            onValueChange = onSpatialAngleChange
                        )

                        val elevDegrees = (spatialElevation * 35).toInt()
                        AuroSpatialSliderRow(
                            label = stringResource(R.string.spatial_elevation_label),
                            value = spatialElevation,
                            range = -1f..1f,
                            formattedValue = "$elevDegrees°",
                            onValueChange = onSpatialElevationChange
                        )
                    }
                }
            }
```

### Componente `AuroSpatialSliderRow`:
```kotlin
@Composable
private fun AuroSpatialSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formattedValue: String,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
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
            modifier = Modifier.width(136.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AuroSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            contentDescription = label,
            stateDescription = formattedValue,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
        )
        Text(
            text = formattedValue,
            style = AuroType.Caption.copy(
                color = if (enabled) AuroColor.TextPrimary else AuroColor.TextTertiary,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            ),
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp)
        )
    }
}
```

---

## 9. `app/src/main/java/com/lucas/player/MainActivity.kt`

### Estados:
```kotlin
    private val spatialModeState = mutableIntStateOf(0)
    private val spatialPresetState = mutableIntStateOf(0)
    private val spatialIntensityState = mutableFloatStateOf(0.85f)
    private val spatialProPresetState = mutableIntStateOf(0)
    private val spatialSubCutoffState = mutableFloatStateOf(110f)
    private val spatialMidGainState = mutableFloatStateOf(0.5f)
    private val spatialSideWidthState = mutableFloatStateOf(1.25f)
    private val spatialSurroundState = mutableFloatStateOf(0.28f)
    private val spatialAngleState = mutableFloatStateOf(0.30f)
    private val spatialElevationState = mutableFloatStateOf(0.0f)
```

### Parâmetros passados para `AuroEqualizerScreen`:
```kotlin
spatialMode = spatialModeState.intValue,
onSpatialModeChange = { mode ->
    spatialModeState.intValue = mode
    playbackService?.setSpatialMode(mode)
    preferencesManager.spatialMode = mode
},
spatialPreset = spatialPresetState.intValue,
onSpatialPresetChange = { preset ->
    spatialPresetState.intValue = preset
    playbackService?.setSpatialPreset(preset, spatialIntensityState.floatValue)
    preferencesManager.spatialPreset = preset
},
spatialIntensity = spatialIntensityState.floatValue,
onSpatialIntensityChange = { intensity ->
    spatialIntensityState.floatValue = intensity
    playbackService?.setSpatialPreset(spatialPresetState.intValue, intensity)
    preferencesManager.spatialIntensity = intensity
},
spatialSubCutoff = spatialSubCutoffState.floatValue,
onSpatialSubCutoffChange = { cutoff ->
    spatialSubCutoffState.floatValue = cutoff
    spatialProPresetState.intValue = 4
    preferencesManager.spatialProPreset = 4
    preferencesManager.spatialSubCutoff = cutoff
    playbackService?.setSpatialProParams(
        cutoff,
        spatialMidGainState.floatValue,
        spatialSideWidthState.floatValue,
        spatialSurroundState.floatValue,
        spatialAngleState.floatValue,
        spatialElevationState.floatValue
    )
},
spatialMidGain = spatialMidGainState.floatValue,
onSpatialMidGainChange = { gain ->
    spatialMidGainState.floatValue = gain
    spatialProPresetState.intValue = 4
    preferencesManager.spatialProPreset = 4
    preferencesManager.spatialMidGain = gain
    playbackService?.setSpatialProParams(
        spatialSubCutoffState.floatValue,
        gain,
        spatialSideWidthState.floatValue,
        spatialSurroundState.floatValue,
        spatialAngleState.floatValue,
        spatialElevationState.floatValue
    )
},
spatialSideWidth = spatialSideWidthState.floatValue,
onSpatialSideWidthChange = { width ->
    spatialSideWidthState.floatValue = width
    spatialProPresetState.intValue = 4
    preferencesManager.spatialProPreset = 4
    preferencesManager.spatialSideWidth = width
    playbackService?.setSpatialProParams(
        spatialSubCutoffState.floatValue,
        spatialMidGainState.floatValue,
        width,
        spatialSurroundState.floatValue,
        spatialAngleState.floatValue,
        spatialElevationState.floatValue
    )
},
spatialSurround = spatialSurroundState.floatValue,
onSpatialSurroundChange = { surround ->
    spatialSurroundState.floatValue = surround
    spatialProPresetState.intValue = 4
    preferencesManager.spatialProPreset = 4
    preferencesManager.spatialSurround = surround
    playbackService?.setSpatialProParams(
        spatialSubCutoffState.floatValue,
        spatialMidGainState.floatValue,
        spatialSideWidthState.floatValue,
        surround,
        spatialAngleState.floatValue,
        spatialElevationState.floatValue
    )
},
spatialAngle = spatialAngleState.floatValue,
onSpatialAngleChange = { angle ->
    spatialAngleState.floatValue = angle
    spatialProPresetState.intValue = 4
    preferencesManager.spatialProPreset = 4
    preferencesManager.spatialAngle = angle
    playbackService?.setSpatialProParams(
        spatialSubCutoffState.floatValue,
        spatialMidGainState.floatValue,
        spatialSideWidthState.floatValue,
        spatialSurroundState.floatValue,
        angle,
        spatialElevationState.floatValue
    )
},
spatialElevation = spatialElevationState.floatValue,
onSpatialElevationChange = { elevation ->
    spatialElevationState.floatValue = elevation
    spatialProPresetState.intValue = 4
    preferencesManager.spatialProPreset = 4
    preferencesManager.spatialElevation = elevation
    playbackService?.setSpatialProParams(
        spatialSubCutoffState.floatValue,
        spatialMidGainState.floatValue,
        spatialSideWidthState.floatValue,
        spatialSurroundState.floatValue,
        spatialAngleState.floatValue,
        elevation
    )
},
spatialProPreset = spatialProPresetState.intValue,
onSpatialProPresetChange = { presetIndex ->
    spatialProPresetState.intValue = presetIndex
    preferencesManager.spatialProPreset = presetIndex
    when (presetIndex) {
        0 -> { // Estúdio Nx (Waves Nx Nearfield)
            val sub = 110f; val mid = 0.5f; val width = 1.25f; val room = 0.28f; val angle = 0.30f; val elev = 0.0f
            spatialSubCutoffState.floatValue = sub
            spatialMidGainState.floatValue = mid
            spatialSideWidthState.floatValue = width
            spatialSurroundState.floatValue = room
            spatialAngleState.floatValue = angle
            spatialElevationState.floatValue = elev
            preferencesManager.spatialSubCutoff = sub
            preferencesManager.spatialMidGain = mid
            preferencesManager.spatialSideWidth = width
            preferencesManager.spatialSurround = room
            preferencesManager.spatialAngle = angle
            preferencesManager.spatialElevation = elev
            playbackService?.setSpatialProParams(sub, mid, width, room, angle, elev)
        }
        1 -> { // Abbey Road (Sala Grande de Estúdio)
            val sub = 105f; val mid = 0.2f; val width = 1.50f; val room = 0.55f; val angle = 0.55f; val elev = 0.22f
            spatialSubCutoffState.floatValue = sub
            spatialMidGainState.floatValue = mid
            spatialSideWidthState.floatValue = width
            spatialSurroundState.floatValue = room
            spatialAngleState.floatValue = angle
            spatialElevationState.floatValue = elev
            preferencesManager.spatialSubCutoff = sub
            preferencesManager.spatialMidGain = mid
            preferencesManager.spatialSideWidth = width
            preferencesManager.spatialSurround = room
            preferencesManager.spatialAngle = angle
            preferencesManager.spatialElevation = elev
            playbackService?.setSpatialProParams(sub, mid, width, room, angle, elev)
        }
        2 -> { // Cinema 360° (Cúpula Atmos 360°)
            val sub = 130f; val mid = 2.0f; val width = 1.70f; val room = 0.68f; val angle = 0.72f; val elev = 0.48f
            spatialSubCutoffState.floatValue = sub
            spatialMidGainState.floatValue = mid
            spatialSideWidthState.floatValue = width
            spatialSurroundState.floatValue = room
            spatialAngleState.floatValue = angle
            spatialElevationState.floatValue = elev
            preferencesManager.spatialSubCutoff = sub
            preferencesManager.spatialMidGain = mid
            preferencesManager.spatialSideWidth = width
            preferencesManager.spatialSurround = room
            preferencesManager.spatialAngle = angle
            preferencesManager.spatialElevation = elev
            playbackService?.setSpatialProParams(sub, mid, width, room, angle, elev)
        }
        3 -> { // Arena 3D (Palco Máximo)
            val sub = 95f; val mid = -0.5f; val width = 1.95f; val room = 0.85f; val angle = 0.90f; val elev = 0.35f
            spatialSubCutoffState.floatValue = sub
            spatialMidGainState.floatValue = mid
            spatialSideWidthState.floatValue = width
            spatialSurroundState.floatValue = room
            spatialAngleState.floatValue = angle
            spatialElevationState.floatValue = elev
            preferencesManager.spatialSubCutoff = sub
            preferencesManager.spatialMidGain = mid
            preferencesManager.spatialSideWidth = width
            preferencesManager.spatialSurround = room
            preferencesManager.spatialAngle = angle
            preferencesManager.spatialElevation = elev
            playbackService?.setSpatialProParams(sub, mid, width, room, angle, elev)
        }
        else -> {}
    }
},
onResetSpatialPro = {
    spatialProPresetState.intValue = 0
    preferencesManager.spatialProPreset = 0
    val sub = 110f; val mid = 0.5f; val width = 1.25f; val room = 0.28f; val angle = 0.30f; val elev = 0.0f
    spatialSubCutoffState.floatValue = sub
    spatialMidGainState.floatValue = mid
    spatialSideWidthState.floatValue = width
    spatialSurroundState.floatValue = room
    spatialAngleState.floatValue = angle
    spatialElevationState.floatValue = elev
    playbackService?.setSpatialProParams(sub, mid, width, room, angle, elev)
    preferencesManager.spatialSubCutoff = sub
    preferencesManager.spatialMidGain = mid
    preferencesManager.spatialSideWidth = width
    preferencesManager.spatialSurround = room
    preferencesManager.spatialAngle = angle
    preferencesManager.spatialElevation = elev
},
```

### Em `syncEqualizerState`:
```kotlin
spatialModeState.intValue = preferencesManager.spatialMode
spatialPresetState.intValue = preferencesManager.spatialPreset
spatialIntensityState.floatValue = preferencesManager.spatialIntensity
spatialProPresetState.intValue = preferencesManager.spatialProPreset
spatialSubCutoffState.floatValue = preferencesManager.spatialSubCutoff
spatialMidGainState.floatValue = preferencesManager.spatialMidGain
spatialSideWidthState.floatValue = preferencesManager.spatialSideWidth
spatialSurroundState.floatValue = preferencesManager.spatialSurround
spatialAngleState.floatValue = preferencesManager.spatialAngle
spatialElevationState.floatValue = preferencesManager.spatialElevation
```

---

## 10. `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml`

### `values/strings.xml`:
```xml
    <string name="spatial_header">Áudio espacial</string>
    <string name="spatial_mode_off">Desativado</string>
    <string name="spatial_mode_standard">Dolby Padrão</string>
    <string name="spatial_mode_pro">Espacial Pro</string>
    <string name="spatial_preset_music">Música</string>
    <string name="spatial_preset_cinema">Cinema</string>
    <string name="spatial_preset_stage">Palco</string>
    <string name="spatial_intensity_label">Intensidade</string>
    <string name="spatial_sub_cutoff_label">Corte Sub-Grave</string>
    <string name="spatial_mid_gain_label">Foco Vocal (Mid)</string>
    <string name="spatial_side_width_label">Largura de Palco</string>
    <string name="spatial_surround_label">Acústica da Sala</string>
    <string name="spatial_angle_label">Ângulo Binaural</string>
    <string name="spatial_elevation_label">Altura 3D (Elevação)</string>
    <string name="spatial_reset_pro">Resetar Padrão</string>
    <string name="spatial_off_desc">Processamento binaural desativado (saída estéreo pura)</string>
    <string name="menu_equalizer_spatial">Equalizador &amp; Áudio Espacial</string>
```

### `values-en/strings.xml`:
```xml
    <!-- Spatial Audio DSP -->
    <string name="spatial_header">Spatial audio</string>
    <string name="spatial_mode_off">Off</string>
    <string name="spatial_mode_standard">Dolby Standard</string>
    <string name="spatial_mode_pro">Spatial Pro</string>
    <string name="spatial_preset_music">Music</string>
    <string name="spatial_preset_cinema">Cinema</string>
    <string name="spatial_preset_stage">Stage</string>
    <string name="spatial_intensity_label">Intensity</string>
    <string name="spatial_sub_cutoff_label">Sub-Bass Cutoff</string>
    <string name="spatial_mid_gain_label">Vocal Focus (Mid)</string>
    <string name="spatial_side_width_label">Stage Width</string>
    <string name="spatial_surround_label">Room Acoustics</string>
    <string name="spatial_angle_label">Binaural Angle</string>
    <string name="spatial_elevation_label">3D Height (Elevation)</string>
    <string name="spatial_reset_pro">Reset to Default</string>
    <string name="spatial_off_desc">Binaural processing disabled (pure stereo output)</string>
    <string name="menu_equalizer_spatial">Equalizer &amp; Spatial Audio</string>
```
