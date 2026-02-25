# NIRBHAY — Complete Rebuild Guide
> Feed this entire file to an AI. It will rebuild everything step by step.

---

## Project Context

**App name:** NIRBHAY  
**Package:** `com.hacksrm.nirbhay`  
**Language:** Kotlin + Jetpack Compose  
**Min SDK:** 26 (Android 8)  
**Target SDK:** 34  
**Backend:** `https://nirbhay-467822196904.asia-south1.run.app`  
**Hardcoded victim_id:** `00000000-0000-0000-0000-000000000001`  
**Hardcoded emergency_token:** `tok_demo_123456`

This is a women's safety app. When danger is detected (manually or by AI), it sends an SOS via internet or Bridgefy Bluetooth mesh (offline fallback). Everything must work with zero internet.

---

## What Already Exists (DO NOT REBUILD)

The following is already in the repo and working:

- Bridgefy mesh integration (`mesh/` folder)
  - `BridgefyMesh.kt` — handles init, send, receive, relay
  - `SOSPacket.kt` — data model sent over mesh
- Basic project structure, AndroidManifest, permissions
- `TriggerSource` enum: `BUTTON, SHAKE, FALL, SCREAM, VOLUME, POWER, AUTO`

---

## Dependencies to Add in `app/build.gradle.kts`

Add ALL of these. Do not skip any:

```kotlin
// Retrofit + networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// TensorFlow Lite (YAMNet scream detection)
implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.4")

// Room (local cache)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// WorkManager (upload retry queue)
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Location
implementation("com.google.android.gms:play-services-location:21.0.1")
```

Also add in `android {}` block:
```kotlin
aaptOptions {
    noCompress += "tflite"
}
```

And apply plugin at top:
```kotlin
plugins {
    id("kotlin-kapt")
}
```

---

## AndroidManifest.xml Permissions

Make sure ALL of these are present:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.BODY_SENSORS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

Register these services in manifest:
```xml
<service android:name=".MainForegroundService"
    android:foregroundServiceType="microphone|location"
    android:exported="false" />

<service android:name=".sos.FallDetectionService"
    android:exported="false" />

<service android:name=".sos.ScreamDetectionService"
    android:foregroundServiceType="microphone"
    android:exported="false" />
```

---

## Folder Structure to Create

```
app/src/main/java/com/hacksrm/nirbhay/
├── mesh/
│   ├── BridgefyMesh.kt          ← EXISTS, do not touch
│   └── SOSPacket.kt             ← EXISTS, do not touch
├── sos/
│   ├── TriggerSource.kt         ← EXISTS, do not touch
│   ├── SOSEngine.kt             ← BUILD THIS (step 4)
│   ├── FallDetector.kt          ← BUILD THIS (step 2)
│   ├── FallDetectionService.kt  ← BUILD THIS (step 2)
│   ├── ScreamDetector.kt        ← BUILD THIS (step 3)
│   ├── ScreamDetectionService.kt← BUILD THIS (step 3)
│   ├── RiskScoreEngine.kt       ← BUILD THIS (step 5)
│   ├── AudioRecorder.kt         ← BUILD THIS (step 6)
│   └── SosApi.kt                ← BUILD THIS (step 7)
├── data/
│   ├── SosEventEntity.kt        ← BUILD THIS (step 8)
│   ├── SosEventDao.kt           ← BUILD THIS (step 8)
│   └── AppDatabase.kt           ← BUILD THIS (step 8)
├── worker/
│   └── UploadQueueWorker.kt     ← BUILD THIS (step 9)
└── MainForegroundService.kt     ← BUILD THIS (step 10)
```

---

## STEP 1 — ConnectivityHelper.kt

Create `utils/ConnectivityHelper.kt`:

```
Simple object with one function:
fun isOnline(context: Context): Boolean
Uses ConnectivityManager + NetworkCapabilities to check if internet is available.
Returns true if NET_CAPABILITY_INTERNET + NET_CAPABILITY_VALIDATED are both present.
```

---

## STEP 2 — Fall Detection

### `sos/FallDetector.kt`

```
Class FallDetector(context, onFallDetected: () -> Unit) : SensorEventListener

Uses: Sensor.TYPE_ACCELEROMETER + Sensor.TYPE_GYROSCOPE

Detection logic (3 phases):
  Phase 1 — Free fall:
    gForce = sqrt(x²+y²+z²) / SensorManager.GRAVITY_EARTH
    If gForce < 0.3 for at least 100ms → record freeFallTime

  Phase 2 — Impact:
    If gForce > 2.5 AND freeFallTime was recorded within last 500ms → mark impactDetected = true

  Phase 3 — Stillness:
    After impact, wait 2000ms using Handler(Looper.getMainLooper()).postDelayed
    If impactDetected is still true → call onFallDetected()
    Then reset: freeFallTime = 0, impactDetected = false

Functions:
  start() → register listener with SENSOR_DELAY_GAME
  stop() → unregister listener

On fall confirmed → calls onFallDetected callback
```

### `sos/FallDetectionService.kt`

```
Foreground service that owns FallDetector.

onCreate:
  Create FallDetector with callback:
    → RiskScoreEngine.addScore(40, "fall_detected", context)
    → Also sendBroadcast ACTION_SOS_TRIGGERED (for UI)
  fallDetector.start()

onDestroy:
  fallDetector.stop()

Show foreground notification: "Fall detection active"
```

---

## STEP 3 — Scream Detection

### `sos/ScreamDetector.kt`

```
Class ScreamDetector(context, onScreamDetected: () -> Unit)

Uses: TensorFlow Lite AudioClassifier with yamnet.tflite model
Model file location: app/src/main/assets/yamnet.tflite
(User must download yamnet.tflite from TensorFlow Hub and place in assets/)

Logic:
  AudioClassifier.createFromFile(context, "yamnet.tflite")
  AudioRecord from classifier.createAudioRecord()

  Run inference loop in background thread (coroutine/thread):
    - Record 1-second audio windows continuously
    - classifier.classify(audioTensor)
    - Get top classifications
    - Log top 3 labels every frame (for diagnostics):
        "Top labels: Scream:0.82, Shout:0.12, Speech:0.05"
    
    Smoothing (sliding average):
      - Keep a window of last 3 frames
      - For each frame, find best distress score from labels:
          "Screaming", "Scream", "Shout", "Yell", "Crying", "Cry"
          (check label.contains() case-insensitive)
      - Average the last 3 distress scores
      - If average >= 0.75 → log "DISTRESS DETECTED (avg=X)" → call onScreamDetected()

  Cooldown: after triggering, wait 10 seconds before triggering again

Functions:
  start() → initialize classifier, start recording loop
  stop() → stop recording, release classifier

On detection → calls onScreamDetected callback
```

### `sos/ScreamDetectionService.kt`

```
Foreground service (foregroundServiceType = microphone) that owns ScreamDetector.

onCreate:
  Create ScreamDetector with callback:
    → RiskScoreEngine.addScore(50, "scream_detected", context)
    → sendBroadcast ACTION_SCREAM_DETECTED (for UI)
  screamDetector.start()

onDestroy:
  screamDetector.stop()

Show foreground notification: "Listening for distress signals..."
Notification must use FOREGROUND_SERVICE_TYPE_MICROPHONE
```

---

## STEP 4 — SOSEngine.kt

```
Object SOSEngine (singleton)

Data needed per trigger:
  - triggerSource: TriggerSource
  - context: Context
  - lat: Double (from FusedLocationProviderClient, timeout 5s, fallback 0.0)
  - lng: Double
  - batteryLevel: Int (from BatteryManager)
  - riskScore: Int (from RiskScoreEngine.currentScore)
  - timestamp: Long (System.currentTimeMillis())

Main function: triggerSOS(source: TriggerSource, context: Context)

Flow:
  1. Show 10-second countdown overlay (send broadcast to UI)
     If cancelled within 10s → abort, log false alarm, return

  2. Collect snapshot:
     - Get GPS via FusedLocationProviderClient (timeout 5s)
     - Get battery level via BatteryManager.EXTRA_LEVEL
     - Get current risk score from RiskScoreEngine.currentScore

  3. Save to Room immediately (before any network call):
     val entity = SosEventEntity(
         victimId = HARDCODED_USER_ID,
         lat = lat, lng = lng,
         triggerMethod = source.name,
         riskScore = riskScore,
         batteryLevel = batteryLevel,
         timestamp = timestamp,
         uploaded = false,
         audioFilePath = null
     )
     val rowId = AppDatabase.getInstance(context).sosEventDao().insert(entity)

  4. Start audio recording:
     AudioRecorder.start(context, timestamp)
     After 60 seconds → AudioRecorder.stop() → get file path
     → AppDatabase.getInstance(context).sosEventDao().updateAudioPath(rowId, filePath)

  5. Check connectivity:
     If online:
       → POST to /api/sos/trigger via Retrofit (SosApi)
       → On success: mark rowId as uploaded = true in Room
       → On failure: leave uploaded = false, mesh fallback below

     If offline OR online POST failed:
       → Build SOSPacket and call BridgefyMesh.sendSos(packet)
       → Log "SOS sent via mesh"

  6. Reset risk score: RiskScoreEngine.resetScore()

Incoming mesh packet handler: handleIncomingMeshPacket(packet: SOSPacket, context: Context)
  - If online → POST to /api/sos/relay via Retrofit
  - If offline → re-broadcast on mesh (BridgefyMesh.sendSos)
  - Cap relay hops at 10 (check packet.hopCount)

Constants:
  HARDCODED_USER_ID = "00000000-0000-0000-0000-000000000001"
  EMERGENCY_TOKEN = "tok_demo_123456"
```

---

## STEP 5 — RiskScoreEngine.kt

```
Object RiskScoreEngine (singleton)

State:
  private var score: Int = 0
  val currentScore: Int get() = score
  
  Expose as StateFlow for UI:
  private val _scoreFlow = MutableStateFlow(0)
  val scoreFlow: StateFlow<Int> = _scoreFlow.asStateFlow()

  private var lastAutoTriggerTime: Long = 0
  private val AUTO_TRIGGER_COOLDOWN = 60_000L (1 minute)

Function: addScore(points: Int, reason: String, context: Context)
  score += points
  _scoreFlow.value = score
  Log.d("RiskScoreEngine", "Score: $score (+$points from $reason)")

  Apply passive bonuses:
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    if (hour >= 22 || hour <= 5) score += 10  // night time
    
    val battery = getBatteryLevel(context)
    if (battery < 15) score += 5

  Evaluate thresholds:
    if score >= 70:
      val now = System.currentTimeMillis()
      if (now - lastAutoTriggerTime > AUTO_TRIGGER_COOLDOWN):
        lastAutoTriggerTime = now
        Log.d("RiskScoreEngine", "AUTO SOS TRIGGER — score=$score")
        SOSEngine.triggerSOS(TriggerSource.AUTO, context)
    else if score >= 50:
      Log.d("RiskScoreEngine", "Guardian silent alert — score=$score")
    else if score >= 30:
      Log.d("RiskScoreEngine", "Increase sensor polling — score=$score")

Function: resetScore()
  score = 0
  _scoreFlow.value = 0

Private helper: getBatteryLevel(context): Int
  Uses BatteryManager or Intent registerReceiver trick
```

---

## STEP 6 — AudioRecorder.kt

```
Object AudioRecorder (singleton)

State:
  private var mediaRecorder: MediaRecorder? = null
  private var currentFilePath: String? = null
  private var recordingJob: Job? = null

Function: start(context: Context, timestamp: Long): String
  Create directory: context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/sos_recordings/"
  Create if not exists.

  Filename: "sos_$timestamp.m4a"
  Full path: dir + filename

  MediaRecorder setup:
    setAudioSource(MediaRecorder.AudioSource.MIC)
    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    setAudioSamplingRate(44100)
    setAudioEncodingBitRate(128000)
    setOutputFile(filePath)
    prepare()
    start()

  currentFilePath = filePath
  Log: "Audio recording started: $filePath"
  Return filePath

Function: stop(): String?
  mediaRecorder?.stop()
  mediaRecorder?.release()
  mediaRecorder = null
  Log: "Audio recording stopped: $currentFilePath"
  Return currentFilePath (so caller can store in Room)

Auto-stop after 60 seconds:
  In SOSEngine, use coroutine:
    launch {
      delay(60_000)
      val path = AudioRecorder.stop()
      if (path != null) dao.updateAudioPath(rowId, path)
    }
```

---

## STEP 7 — SosApi.kt (Retrofit)

```
Retrofit interface + DTOs + client builder

Data classes:
  SosCreateRequest:
    victim_id: String
    emergency_token: String
    lat: Double
    lng: Double
    trigger_method: String
    risk_score: Int
    battery_level: Int
    timestamp: Long
    audio_file_path: String? (nullable)

  SosCreateResponse:
    success: Boolean
    message: String?
    sos_id: String?

Interface SosApi:
  @POST("api/sos/trigger")
  suspend fun trigger(@Body request: SosCreateRequest): Response<SosCreateResponse>

  @POST("api/sos/relay")
  suspend fun relay(@Body request: SosCreateRequest): Response<SosCreateResponse>

Object RetrofitClient:
  Base URL: "https://nirbhay-467822196904.asia-south1.run.app/"
  
  Build with:
    OkHttpClient with HttpLoggingInterceptor (BODY level in debug)
    MoshiConverterFactory
    Timeout: connect 10s, read 15s, write 15s

  val sosApi: SosApi (lazy singleton)
```

---

## STEP 8 — Room Database (Local Cache)

### `data/SosEventEntity.kt`

```kotlin
@Entity(tableName = "sos_events")
data class SosEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val victimId: String,
    val lat: Double,
    val lng: Double,
    val triggerMethod: String,
    val riskScore: Int,
    val batteryLevel: Int,
    val timestamp: Long,
    val uploaded: Boolean = false,
    val audioFilePath: String? = null
)
```

### `data/SosEventDao.kt`

```kotlin
@Dao
interface SosEventDao {
    @Insert
    suspend fun insert(event: SosEventEntity): Long

    @Query("SELECT * FROM sos_events WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<SosEventEntity>

    @Query("UPDATE sos_events SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)

    @Query("UPDATE sos_events SET audioFilePath = :path WHERE id = :id")
    suspend fun updateAudioPath(id: Long, path: String)

    @Query("SELECT * FROM sos_events ORDER BY timestamp DESC")
    suspend fun getAll(): List<SosEventEntity>
}
```

### `data/AppDatabase.kt`

```
Room database singleton.
Entities: [SosEventEntity::class]
Version: 1
Singleton pattern using companion object + volatile instance.
getInstance(context) → synchronized block → build if null.
```

---

## STEP 9 — UploadQueueWorker.kt

```
Class UploadQueueWorker(context, workerParams) : CoroutineWorker

doWork():
  1. Get all unuploaded events from Room:
     val events = AppDatabase.getInstance(context).sosEventDao().getUnuploaded()

  2. For each event:
     Build SosCreateRequest from entity fields
     Try POST to https://httpbin.org/post (DUMMY endpoint for now)
       Use RetrofitClient OR simple OkHttp POST
       On HTTP 200 → dao.markUploaded(event.id)
       On failure → log, continue to next event

  3. Return Result.success() always (don't retry the worker itself,
     individual events stay in queue)

Schedule in Application class or MainForegroundService:
  PeriodicWorkRequest every 15 minutes:
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    
    val workRequest = PeriodicWorkRequestBuilder<UploadQueueWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()
    
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "sos_upload_queue",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
```

---

## STEP 10 — MainForegroundService.kt

```
Foreground service that is the coordinator. Starts everything.

onCreate():
  1. Start foreground with persistent notification:
     Channel: "nirbhay_main" 
     Title: "NIRBHAY Active"
     Text: "Monitoring for your safety..."
     Priority: PRIORITY_LOW (so it's not annoying)

  2. Start FallDetector:
     fallDetector = FallDetector(this) {
         RiskScoreEngine.addScore(40, "fall_detected", this)
     }
     fallDetector.start()

  3. Start ScreamDetector:
     screamDetector = ScreamDetector(this) {
         RiskScoreEngine.addScore(50, "scream_detected", this)
     }
     screamDetector.start()

  4. Initialize Bridgefy mesh:
     BridgefyMesh.initialize(this)
     BridgefyMesh.start()
     Set mesh receive callback → SOSEngine.handleIncomingMeshPacket(packet, this)

  5. Register NetworkCallback for upload queue:
     val networkCallback = object : ConnectivityManager.NetworkCallback() {
         override fun onAvailable(network: Network) {
             // Internet came back → trigger immediate upload of queued events
             val oneTimeWork = OneTimeWorkRequestBuilder<UploadQueueWorker>().build()
             WorkManager.getInstance(applicationContext).enqueue(oneTimeWork)
             Log.d("MainService", "Network available — triggering upload queue")
         }
     }
     connectivityManager.registerDefaultNetworkCallback(networkCallback)

  6. Schedule periodic UploadQueueWorker (every 15 min)

onDestroy():
  fallDetector.stop()
  screamDetector.stop()
  BridgefyMesh.stop()
  connectivityManager.unregisterNetworkCallback(networkCallback)
```

---

## Complete Data Flow (reference)

```
TRIGGER (button / fall / scream / shake / power / volume / auto)
    ↓
SOSEngine.triggerSOS(source, context)
    ↓
10s countdown → user can cancel
    ↓ (if not cancelled)
Collect: GPS + battery + riskScore + timestamp
    ↓
Save SosEventEntity to Room (uploaded=false)
    ↓
Start AudioRecorder → saves sos_[timestamp].m4a to phone storage
    ↓
Check connectivity
    ↓                           ↓
  ONLINE                     OFFLINE
    ↓                           ↓
POST /api/sos/trigger      BridgefyMesh.sendSos(packet)
    ↓                           ↓
 success?               nearby device receives
    ↓ yes                       ↓
mark uploaded=true       has internet?
    ↓ no                   ↓ yes → POST /api/sos/relay
mesh fallback            ↓ no  → re-broadcast mesh
    ↓
After 60s → AudioRecorder.stop() → update Room with audio path
    ↓
UploadQueueWorker runs every 15min (or when network returns)
→ finds uploaded=false rows → POSTs to backend → marks uploaded=true
```

---

## Risk Score Reference

| Event | Score Added |
|---|---|
| Fall detected | +40 |
| Scream detected | +50 |
| Violent grab (gyroscope) | +30 |
| Night time (10pm–5am) | +10 |
| Battery < 15% | +5 |

| Threshold | Action |
|---|---|
| >= 70 | Auto-trigger SOS (with 1 min cooldown) |
| >= 50 | Silent guardian notification (log for now) |
| >= 30 | Increase sensor polling rate (log for now) |

---

## Testing Commands

```bash
# Build and install
.\gradlew assembleDebug
.\gradlew installDebug

# Watch all relevant logs
adb logcat -s ScreamDetector RiskScoreEngine SOSEngine FallDetection BridgefyMesh UploadQueueWorker AudioRecorder

# Test scream detection — push audio file and play it
adb push scream.mp3 /sdcard/Download/scream.mp3
adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/scream.mp3 -t audio/mpeg

# Test backend endpoint manually
curl -X POST https://nirbhay-467822196904.asia-south1.run.app/api/sos/trigger \
-H "Content-Type: application/json" \
-d '{
  "victim_id":"00000000-0000-0000-0000-000000000001",
  "emergency_token":"tok_demo_123456",
  "lat":28.6139,
  "lng":77.2090,
  "trigger_method":"manual_button",
  "risk_score":87,
  "battery_level":42,
  "timestamp":1234567890000
}'

# Test relay endpoint
curl -X POST https://nirbhay-467822196904.asia-south1.run.app/api/sos/relay \
-H "Content-Type: application/json" \
-d '{
  "victim_id":"00000000-0000-0000-0000-000000000001",
  "emergency_token":"tok_demo_123456",
  "lat":28.6139,
  "lng":77.2090,
  "trigger_method":"mesh_relay",
  "risk_score":87,
  "battery_level":42,
  "timestamp":1234567890000
}'
```

---

## What to Tell the AI Building This

```
Build each STEP in order (1 through 10).
After each step, confirm it compiles before moving to the next.
Do NOT modify anything in the mesh/ folder.
Do NOT change TriggerSource.kt or SOSPacket.kt.
All new files go in the folders specified in the folder structure.
Use coroutines (not callbacks) wherever async work is needed.
Every class that uses mic must handle SecurityException for RECORD_AUDIO.
Every Room operation must be called from a coroutine (suspend functions).
Add Log.d() statements with meaningful tags on all key events.
```

---

## Placeholders to Replace After Hackathon

- `victim_id` → real user UUID from onboarding / SharedPreferences
- `emergency_token` → per-user auth token from backend login
- `risk_score` in relay → real score from `RiskScoreEngine.currentScore`
- httpbin.org dummy endpoint in UploadQueueWorker → real backend `/api/sos/trigger`
- Silent guardian notification (score >= 50) → real push notification via FCM
- Increase sensor polling (score >= 30) → actual `SensorManager.SENSOR_DELAY_FASTEST`
