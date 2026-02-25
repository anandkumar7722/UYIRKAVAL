# EDGE-AI INTEGRATION GUIDE — Nirbhay / SHE-SHIELD

> **Audience**: Android developers working on the Nirbhay frontend.
> **Last updated**: Based on the current codebase (SDK 34, minSdk 26, YAMNet TFLite).

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Voice / Scream Detection (YAMNet)](#2-voice--scream-detection-yamnet)
3. [Fall Detection (Accelerometer)](#3-fall-detection-accelerometer)
4. [Broadcast → UI Pipeline](#4-broadcast--ui-pipeline)
5. [Permissions Checklist](#5-permissions-checklist)
6. [AndroidManifest Requirements](#6-androidmanifest-requirements)
7. [Debugging & Logcat Filters](#7-debugging--logcat-filters)
8. [Tuning Thresholds](#8-tuning-thresholds)
9. [Common Failure Modes & Fixes](#9-common-failure-modes--fixes)
10. [Database Schema Compatibility](#10-database-schema-compatibility)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                     MainActivity                     │
│                                                     │
│  ┌──────────────┐     ┌─────────────────────┐       │
│  │ Permission   │     │ LocalBroadcastMgr   │       │
│  │ Launcher     │     │ (sosReceiver)       │       │
│  └──────┬───────┘     └──────────┬──────────┘       │
│         │ grants                 │ receives          │
│         ▼                        │ ACTION_SOS_       │
│  startForegroundService()        │ TRIGGERED         │
│         │                        │                   │
│    ┌────┴────────────┐           │                   │
│    │                 │           │                   │
│    ▼                 ▼           ▼                   │
│ ┌────────────┐ ┌───────────┐  sosTriggerReason      │
│ │ Scream     │ │ Fall      │  (MutableState)        │
│ │ Detection  │ │ Detection │       │                │
│ │ Service    │ │ Service   │       ▼                │
│ │ (YAMNet)   │ │ (Accel)   │  NirbhayNav           │
│ └─────┬──────┘ └────┬──────┘  LaunchedEffect        │
│       │              │         → navigate("sos")    │
│       │              │                              │
│       └──── LocalBroadcastManager.sendBroadcast ────┘
│              ACTION_SOS_TRIGGERED
│              REASON = "SCREAM_DETECTED" | "FALL_DETECTED"
└─────────────────────────────────────────────────────┘
```

**Key flow:**
1. `MainActivity` requests permissions → starts both foreground services.
2. Each service runs independently, monitoring its sensor (mic / accelerometer).
3. When a threat is detected, the service fires a `LocalBroadcast` with action `ACTION_SOS_TRIGGERED`.
4. `MainActivity.sosReceiver` catches the broadcast and sets `sosTriggerReason.value`.
5. `NirbhayNav` observes that state via `LaunchedEffect` and auto-navigates to the SOS countdown screen.

---

## 2. Voice / Scream Detection (YAMNet)

### File: `ScreamDetectionService.kt`

### How it works

| Step | What happens |
|------|-------------|
| **1. Model load** | `AudioClassifier.createFromFile(context, "yamnet.tflite")` loads the on-device YAMNet model from `assets/`. |
| **2. AudioRecord** | `classifier.createAudioRecord()` creates a mic recorder pre-configured with the sample rate / buffer size the model expects (16 kHz, mono, 0.975 s frames). |
| **3. Mic validation** | Before inference, raw PCM is read and **RMS is computed**. If RMS < 10.0, a warning is logged — the mic may be muted by Android. |
| **4. Inference** | `tensorAudio.load(record)` → `classifier.classify(tensorAudio)` runs ~1 inference per second on a background daemon thread. |
| **5. Label matching** | Output categories are checked against `DISTRESS_LABELS`. If any label matches with confidence > `CONFIDENCE_THRESHOLD` (0.30), it's a "hit". |
| **6. Consecutive frames** | A single hit is not enough. `CONSECUTIVE_FRAMES_REQUIRED` (2) consecutive inference frames must exceed the threshold. This prevents a single noisy frame from false-triggering. |
| **7. SOS broadcast** | After enough consecutive hits, a `LocalBroadcast` is sent with `REASON = "SCREAM_DETECTED"`, plus `LABEL` and `SCORE` extras. |
| **8. Cooldown** | 5-second cooldown prevents duplicate triggers. |

### Distress labels detected

```
Scream, Screaming, Yell, Shout,
Crying sobbing, Wail moan, Whimper,
Groan, Battle cry, Children shouting
```

### Key constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `CONFIDENCE_THRESHOLD` | `0.30f` | Minimum YAMNet confidence (30%) |
| `CONSECUTIVE_FRAMES_REQUIRED` | `2` | Frames that must consecutively exceed threshold |
| `COOLDOWN_MS` | `5_000L` | 5 s between SOS broadcasts |
| `SILENT_RMS_THRESHOLD` | `10f` | Below this = mic is effectively muted |
| `MODEL_FILE` | `"yamnet.tflite"` | Asset file name |

### How to use from your Activity / Fragment

You do **NOT** call the service directly. Just start it:

```kotlin
// Start the service (requires RECORD_AUDIO permission)
val intent = Intent(context, ScreamDetectionService::class.java)
context.startForegroundService(intent)
```

Then register a receiver to listen for detections:

```kotlin
val receiver = object : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, intent: Intent?) {
        val reason = intent?.getStringExtra("REASON") ?: return
        if (reason == "SCREAM_DETECTED") {
            val label = intent.getStringExtra("LABEL")
            val score = intent.getFloatExtra("SCORE", 0f)
            // Handle SOS trigger
        }
    }
}

LocalBroadcastManager.getInstance(this).registerReceiver(
    receiver,
    IntentFilter("ACTION_SOS_TRIGGERED")
)
```

### Stopping the service

```kotlin
val intent = Intent(context, ScreamDetectionService::class.java)
context.stopService(intent)
```

---

## 3. Fall Detection (Accelerometer)

### File: `FallDetectionService.kt`

### How it works — Two-Phase State Machine

```
┌──────────┐   gForce < 0.3 G    ┌───────────────┐
│   IDLE   │ ──────────────────► │  FREE-FALL    │
└──────────┘                     └───────┬───────┘
      ▲                                  │
      │  window expired (>1 s)           │ gForce > 2.5 G
      │  or cooldown active              │ within 1 000 ms
      │                                  ▼
      │                          ┌───────────────┐
      └────────── cooldown ◄──── │     FALL      │
                  10 000 ms      │   CONFIRMED   │
                                 └───────────────┘
```

| Phase | Condition | Meaning |
|-------|-----------|---------|
| **Free-fall** | `gForce < 0.3 G` | Phone is weightless (falling). Opens a 1 s window. |
| **Impact** | `gForce > 2.5 G` within 1 000 ms of free-fall | Sudden deceleration = hitting floor/wall. |
| **Cooldown** | 10 000 ms after confirmed fall | Prevents duplicate triggers from bouncing. |

### Physics

```
gForce = √(ax² + ay² + az²) / 9.80665

• gForce ≈ 1.0  →  phone at rest (gravity only)
• gForce ≈ 0.0  →  free-fall
• gForce > 2.0  →  sudden impact / violent jerk
```

Sampling rate: `SENSOR_DELAY_GAME` (~50 Hz / 20 ms between samples).

### Key constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `FREE_FALL_THRESHOLD_G` | `0.3f` | Below this = in free-fall |
| `IMPACT_THRESHOLD_G` | `2.5f` | Above this = impact detected |
| `IMPACT_WINDOW_MS` | `1_000L` | Impact must happen within 1 s of free-fall |
| `COOLDOWN_MS` | `10_000L` | 10 s between SOS broadcasts |
| `STANDARD_GRAVITY` | `9.80665f` | m/s², used for normalization |

### How to use from your Activity

```kotlin
// Start the service (no special permission needed for accelerometer)
val intent = Intent(context, FallDetectionService::class.java)
context.startForegroundService(intent)
```

Register the same receiver — it fires with `REASON = "FALL_DETECTED"`:

```kotlin
// Same receiver as scream detection — both use ACTION_SOS_TRIGGERED
if (reason == "FALL_DETECTED") {
    val gForce = intent.getFloatExtra("G_FORCE", 0f)
    val elapsed = intent.getLongExtra("ELAPSED_MS", 0L)
    // Handle fall-triggered SOS
}
```

---

## 4. Broadcast → UI Pipeline

### The full chain

```
Service detects threat
    │
    ▼
LocalBroadcastManager.sendBroadcast(
    Intent("ACTION_SOS_TRIGGERED")
        .putExtra("REASON", "SCREAM_DETECTED" or "FALL_DETECTED")
)
    │
    ▼
MainActivity.sosReceiver.onReceive()
    │
    ▼
sosTriggerReason.value = reason   // MutableState<String?>
    │
    ▼
NirbhayNav  →  LaunchedEffect(triggerReason) {
    if (triggerReason != null) {
        navController.navigate("sos")
        sosTriggerReason.value = null   // reset for next trigger
    }
}
    │
    ▼
SosCountdownScreen() displayed with 9-second countdown
```

### Key design decisions

- **`LocalBroadcastManager`** (not system broadcast): Stays in-process, no IPC overhead, no security risk.
- **`MutableState<String?>`**: Observable by Compose. When it flips to non-null, `LaunchedEffect` fires.
- **Reset after navigation**: `sosTriggerReason.value = null` ensures the next detection can trigger again.
- **Single action string**: Both services use the same `ACTION_SOS_TRIGGERED` action so only ONE receiver is needed.

---

## 5. Permissions Checklist

All requested in a single batch from `MainActivity`:

| Permission | Why | Required by |
|-----------|-----|-------------|
| `RECORD_AUDIO` | Microphone access for YAMNet inference | ScreamDetectionService |
| `ACCESS_FINE_LOCATION` | GPS for SOS location + Bluetooth mesh | LocationHelper, Bridgefy |
| `BLUETOOTH_SCAN` | Discover nearby mesh devices | BridgefyMesh |
| `BLUETOOTH_CONNECT` | Connect to mesh peers | BridgefyMesh |
| `BLUETOOTH_ADVERTISE` | Advertise SOS packets on mesh | BridgefyMesh |
| `POST_NOTIFICATIONS` | Show foreground service notification (Android 13+) | Both services |

### Runtime flow

```kotlin
// 1. Check if already granted
if (hasAllPermissions()) {
    startMesh()
    startEdgeAiServices()
} else {
    // 2. Request all at once — user sees ONE dialog
    requestPermissionsLauncher.launch(allPermissions)
}

// 3. In the callback:
//    - If RECORD_AUDIO granted → startEdgeAiServices()
//    - If location + BT granted → startMesh()
//    - If denied → log a warning (degraded mode)
```

### Android 14+ (API 34) special considerations

- **Foreground service type**: Services must declare `android:foregroundServiceType` in the manifest.
  - ScreamDetectionService: `"microphone"`
  - FallDetectionService: (none needed — accelerometer doesn't require a service type)
- **`POST_NOTIFICATIONS`**: Required on Android 13+ (TIRAMISU) to show the persistent notification.

---

## 6. AndroidManifest Requirements

These entries are already present in `AndroidManifest.xml` but listed here for reference:

### Permissions

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.INTERNET" />
```

### Service declarations

```xml
<service
    android:name=".ScreamDetectionService"
    android:exported="false"
    android:foregroundServiceType="microphone" />

<service
    android:name=".FallDetectionService"
    android:exported="false" />
```

**Critical**: `ScreamDetectionService` MUST have `foregroundServiceType="microphone"` or Android 14+ will deny mic access to the foreground service.

---

## 7. Debugging & Logcat Filters

### Recommended Logcat filter

```
tag:ScreamDetection | tag:FallDetection | tag:MainActivity
```

### What to look for

| Log pattern | Meaning |
|-------------|---------|
| `✅ Model loaded` | YAMNet TFLite loaded successfully |
| `✅ AudioRecord initialized` | Mic recorder created and ready |
| `✅ AudioRecord is RECORDING` | Mic is actively recording |
| `🎤 Mic RMS: X.X` | Current mic volume (logged every ~10 cycles) |
| `⚠️ VERY LOW MIC RMS` | Mic may be muted — check permissions |
| `❌❌❌ AudioRecord FAILED` | Mic permission denied or mic busy |
| `📊 Top-5: label=X%` | Top 5 YAMNet labels (every ~15 cycles) |
| `🔶 Distress candidate` | A label matched — building toward SOS |
| `🚨🚨🚨 DISTRESS CONFIRMED` | SOS broadcast fired (scream) |
| `⬇ Free-fall detected` | Phase 1 of fall detection triggered |
| `🚨🚨🚨 FALL DETECTED` | SOS broadcast fired (fall) |
| `🚨🚨🚨 SOS BROADCAST RECEIVED` | MainActivity caught the broadcast |

### Testing scream detection manually

1. Install the app on a physical device (emulator mic is unreliable).
2. Grant all permissions.
3. Open Logcat with filter `tag:ScreamDetection`.
4. Play a loud scream audio file near the phone's mic.
5. Watch for:
   - `Mic RMS` values > 100 (confirms mic is working).
   - `Top-5` labels — look for "Scream", "Yell", etc. appearing.
   - `Distress candidate` → `DISTRESS CONFIRMED` (the full chain).

### Testing fall detection manually

1. Hold the phone and let it free-fall ~30 cm into your other hand.
2. Watch Logcat for `Free-fall detected` → `FALL DETECTED`.
3. Or shake the phone violently (may trigger depending on G-force).

---

## 8. Tuning Thresholds

### Scream Detection

| Parameter | Current | To make MORE sensitive | To make LESS sensitive |
|-----------|---------|----------------------|----------------------|
| `CONFIDENCE_THRESHOLD` | `0.30f` | Lower to `0.20f` | Raise to `0.45f` |
| `CONSECUTIVE_FRAMES_REQUIRED` | `2` | Set to `1` (instant fire) | Set to `3` or `4` |
| `DISTRESS_LABELS` | 10 labels | Add more YAMNet labels | Remove edge-case labels |
| `COOLDOWN_MS` | `5000L` | Lower to `3000L` | Raise to `10000L` |

**Warning**: Setting `CONSECUTIVE_FRAMES_REQUIRED = 1` with `CONFIDENCE_THRESHOLD < 0.25f` will cause frequent false positives in noisy environments (crowds, TV, traffic).

### Fall Detection

| Parameter | Current | To make MORE sensitive | To make LESS sensitive |
|-----------|---------|----------------------|----------------------|
| `FREE_FALL_THRESHOLD_G` | `0.3f` | Raise to `0.5f` (easier to trigger) | Lower to `0.2f` |
| `IMPACT_THRESHOLD_G` | `2.5f` | Lower to `2.0f` | Raise to `3.5f` |
| `IMPACT_WINDOW_MS` | `1000L` | Extend to `1500L` | Shrink to `500L` |
| `COOLDOWN_MS` | `10000L` | Lower to `5000L` | Raise to `15000L` |

---

## 9. Common Failure Modes & Fixes

### Scream detection not triggering

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Mic RMS: 0.0` every cycle | `RECORD_AUDIO` not granted at runtime | Check `Settings > Apps > Nirbhay > Permissions > Microphone` |
| `AudioRecord FAILED TO INITIALIZE` | Another app is holding the mic | Close camera / voice recorder apps |
| Top-5 always shows `Speech` / `Music` | Threshold too high for the actual scores | Lower `CONFIDENCE_THRESHOLD` to `0.20f` |
| Candidates appear but never confirm | `CONSECUTIVE_FRAMES_REQUIRED` too high | Lower to `1` for testing |
| Service killed after ~5 min | Missing `foregroundServiceType="microphone"` | Add to `AndroidManifest.xml` |
| No logs at all from `ScreamDetection` | Service never started | Check `RECORD_AUDIO` was granted and `startEdgeAiServices()` was called |

### Fall detection not triggering

| Symptom | Cause | Fix |
|---------|-------|-----|
| No `Free-fall detected` log | Drop height too small or phone is in a case | Drop from > 50 cm or lower `FREE_FALL_THRESHOLD_G` to `0.5f` |
| Free-fall detected but no impact | Impact was onto a soft surface | Lower `IMPACT_THRESHOLD_G` to `2.0f` |
| No accelerometer sensor | Emulator without sensor emulation | Use a physical device |

### SOS screen never appears

| Symptom | Cause | Fix |
|---------|-------|-----|
| Broadcast fires but UI doesn't navigate | `sosReceiver` not registered | Ensure `LocalBroadcastManager.registerReceiver()` is in `onCreate()` |
| `sosTriggerReason` changes but no navigation | `NirbhayNav` doesn't accept the parameter | Pass `sosTriggerReason` to `NirbhayNav()` composable |
| Navigation fires but SOS screen is blank | Route mismatch | Verify `NavHost` has `composable("sos") { SosCountdownScreen() }` |

---

## 10. Database Schema Compatibility

Both Edge-AI services trigger the SOS flow which eventually hits the backend's `/api/sos/trigger` endpoint. The detection reason maps to the `trigger_type` column in the `sos_events` table:

| Service | `REASON` extra | Maps to `trigger_type` |
|---------|---------------|----------------------|
| ScreamDetectionService | `"SCREAM_DETECTED"` | `"voice_detected"` or `"auto_scream"` |
| FallDetectionService | `"FALL_DETECTED"` | `"fall_detected"` or `"auto_fall"` |

The backend's `SOSTriggerRequest` model accepts a `trigger_type` string field. The frontend should map the broadcast reason to one of the accepted trigger types before sending to the API.

### Required database columns for Edge-AI SOS events

```sql
-- Already in sos_events table:
trigger_type    TEXT NOT NULL,      -- "manual", "voice_detected", "fall_detected", etc.
latitude        DOUBLE PRECISION,   -- from LocationHelper
longitude       DOUBLE PRECISION,   -- from LocationHelper
created_at      TIMESTAMPTZ,        -- auto-set
status          TEXT DEFAULT 'active'
```

---

## Quick Reference Card

```
┌──────────────────────────────────────────────────────┐
│                EDGE-AI QUICK REFERENCE                │
├──────────────────────────────────────────────────────┤
│                                                      │
│  SCREAM DETECTION                                    │
│    Model:      yamnet.tflite (assets/)               │
│    Service:    ScreamDetectionService                │
│    Threshold:  30% confidence, 2 consecutive frames  │
│    Cooldown:   5 seconds                             │
│    Broadcast:  REASON = "SCREAM_DETECTED"            │
│    Permission: RECORD_AUDIO + foregroundServiceType  │
│                                                      │
│  FALL DETECTION                                      │
│    Sensor:     TYPE_ACCELEROMETER @ 50 Hz            │
│    Service:    FallDetectionService                  │
│    Algorithm:  Free-fall (<0.3G) → Impact (>2.5G)   │
│    Window:     1 second between phases               │
│    Cooldown:   10 seconds                            │
│    Broadcast:  REASON = "FALL_DETECTED"              │
│    Permission: None (accelerometer is unrestricted)  │
│                                                      │
│  SHARED                                              │
│    Action:     "ACTION_SOS_TRIGGERED"                │
│    Channel:    "edge_ai_channel" (low importance)    │
│    UI bridge:  sosTriggerReason: MutableState<String?>│
│    Nav route:  "sos" → SosCountdownScreen()          │
│                                                      │
└──────────────────────────────────────────────────────┘
```
