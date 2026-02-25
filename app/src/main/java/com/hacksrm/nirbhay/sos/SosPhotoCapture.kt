package com.hacksrm.nirbhay.sos

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Silently captures 10 photos when SOS is triggered:
 *   • 5 from the BACK camera
 *   • 5 from the FRONT camera
 *   • ~2 s between each shot
 *
 * Uses Camera2 API directly — no preview surface or LifecycleOwner needed,
 * so this works perfectly from a background Service or coroutine.
 *
 * Photos are saved to: <app-external>/sos_photos/sos_photo_<timestamp>_<index>_<front|back>.jpg
 */
class SosPhotoCapture(private val context: Context) {

    companion object {
        private const val TAG = "SosPhotoCapture"
        private const val PHOTOS_PER_CAMERA = 5
        private const val DELAY_BETWEEN_SHOTS_MS = 2000L
    }

    private val capturedPhotos = mutableListOf<File>()
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    /** Directory where SOS photos are saved on the device */
    private fun getPhotoDir(): File {
        val dir = File(context.getExternalFilesDir(null), "sos_photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Capture 10 photos (5 back + 5 front) asynchronously.
     * Returns the list of saved File objects when done.
     * Call from a coroutine scope — it suspends while capturing.
     */
    suspend fun captureAll(): List<File> = withContext(Dispatchers.IO) {
        capturedPhotos.clear()

        // Start a background HandlerThread for Camera2 callbacks
        bgThread = HandlerThread("SosCamera").also { it.start() }
        bgHandler = Handler(bgThread!!.looper)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Find camera IDs
        val backCameraId  = findCamera(cameraManager, CameraCharacteristics.LENS_FACING_BACK)
        val frontCameraId = findCamera(cameraManager, CameraCharacteristics.LENS_FACING_FRONT)

        Log.i(TAG, "📸 ─── PHOTO CAPTURE START ───")
        Log.i(TAG, "📸 Back camera:  ${backCameraId ?: "NOT FOUND"}")
        Log.i(TAG, "📸 Front camera: ${frontCameraId ?: "NOT FOUND"}")

        // Capture 5 from BACK camera
        if (backCameraId != null) {
            Log.i(TAG, "📸 Capturing $PHOTOS_PER_CAMERA photos from BACK camera…")
            captureFromCamera(cameraManager, backCameraId, "back")
        } else {
            Log.w(TAG, "📸 No back camera found — skipping")
        }

        // Capture 5 from FRONT camera
        if (frontCameraId != null) {
            Log.i(TAG, "📸 Capturing $PHOTOS_PER_CAMERA photos from FRONT camera…")
            captureFromCamera(cameraManager, frontCameraId, "front")
        } else {
            Log.w(TAG, "📸 No front camera found — skipping")
        }

        // Clean up
        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null

        Log.i(TAG, "📸 ─── PHOTO CAPTURE COMPLETE: ${capturedPhotos.size} photos ───")
        capturedPhotos.forEach { f ->
            Log.i(TAG, "📸   • ${f.name}  (${f.length() / 1024} KB)")
        }

        capturedPhotos.toList()
    }

    /** Find the camera ID for the given lens facing direction */
    private fun findCamera(manager: CameraManager, lensFacing: Int): String? {
        return try {
            manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == lensFacing
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding camera: ${e.message}")
            null
        }
    }

    /**
     * Open one camera, capture [PHOTOS_PER_CAMERA] still images, close it.
     * Each photo is saved as a JPEG file.
     */
    @SuppressLint("MissingPermission")
    private suspend fun captureFromCamera(
        manager: CameraManager,
        cameraId: String,
        label: String   // "front" or "back"
    ) {
        val handler = bgHandler ?: return

        // Determine output size (use a reasonable JPEG size)
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = streamConfigMap?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        // Pick a medium resolution (around 1280x960) if available, otherwise largest
        val targetSize = jpegSizes
            .sortedBy { it.width * it.height }
            .firstOrNull { it.width >= 1280 } ?: jpegSizes.lastOrNull()

        val width  = targetSize?.width  ?: 1280
        val height = targetSize?.height ?: 960
        Log.d(TAG, "📸 [$label] Using resolution: ${width}x${height}")

        val imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2)

        // Open camera (suspend until opened)
        val device = suspendCancellableCoroutine<CameraDevice?> { cont ->
            try {
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (cont.isActive) cont.resume(camera) {}
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        Log.w(TAG, "📸 [$label] Camera disconnected")
                        camera.close()
                        if (cont.isActive) cont.resume(null) {}
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(TAG, "📸 [$label] Camera error: $error")
                        camera.close()
                        if (cont.isActive) cont.resume(null) {}
                    }
                }, handler)
            } catch (e: Exception) {
                Log.e(TAG, "📸 [$label] Failed to open camera: ${e.message}")
                if (cont.isActive) cont.resume(null) {}
            }
        }

        if (device == null) {
            Log.e(TAG, "📸 [$label] Camera device is null — skipping")
            imageReader.close()
            return
        }

        // Create capture session (suspend until configured)
        val session = suspendCancellableCoroutine<CameraCaptureSession?> { cont ->
            try {
                @Suppress("DEPRECATION")
                device.createCaptureSession(
                    listOf(imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            if (cont.isActive) cont.resume(s) {}
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            Log.e(TAG, "📸 [$label] Session configure failed")
                            if (cont.isActive) cont.resume(null) {}
                        }
                    },
                    handler
                )
            } catch (e: Exception) {
                Log.e(TAG, "📸 [$label] Failed to create session: ${e.message}")
                if (cont.isActive) cont.resume(null) {}
            }
        }

        if (session == null) {
            Log.e(TAG, "📸 [$label] Capture session is null — closing camera")
            device.close()
            imageReader.close()
            return
        }

        // Give auto-exposure a moment to settle on the first frame
        delay(500)

        // Capture N photos
        for (i in 1..PHOTOS_PER_CAMERA) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                val photoFile = File(getPhotoDir(), "sos_photo_${timestamp}_${i}_${label}.jpg")

                // Set up ImageReader listener for this capture
                val savedFile = suspendCancellableCoroutine<File?> { cont ->
                    imageReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            try {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)

                                FileOutputStream(photoFile).use { it.write(bytes) }
                                Log.d(TAG, "📸 [$label] Photo $i saved: ${photoFile.name} (${bytes.size / 1024} KB)")
                                if (cont.isActive) cont.resume(photoFile) {}
                            } catch (e: Exception) {
                                Log.e(TAG, "📸 [$label] Error saving photo $i: ${e.message}")
                                if (cont.isActive) cont.resume(null) {}
                            } finally {
                                image.close()
                            }
                        } else {
                            if (cont.isActive) cont.resume(null) {}
                        }
                    }, handler)

                    // Build & send still capture request
                    try {
                        val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                        }.build()

                        session.capture(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureFailed(
                                s: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure
                            ) {
                                Log.e(TAG, "📸 [$label] Capture $i failed: reason=${failure.reason}")
                                if (cont.isActive) cont.resume(null) {}
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "📸 [$label] Capture request $i failed: ${e.message}")
                        if (cont.isActive) cont.resume(null) {}
                    }
                }

                if (savedFile != null) {
                    capturedPhotos.add(savedFile)
                }

                // Delay between shots
                if (i < PHOTOS_PER_CAMERA) {
                    delay(DELAY_BETWEEN_SHOTS_MS)
                }

            } catch (e: Exception) {
                Log.e(TAG, "📸 [$label] Photo $i overall error: ${e.message}")
            }
        }

        // Close camera resources
        try { session.close() } catch (_: Exception) {}
        try { device.close() } catch (_: Exception) {}
        try { imageReader.close() } catch (_: Exception) {}
        Log.d(TAG, "📸 [$label] Camera closed after $PHOTOS_PER_CAMERA captures")
    }

    /** Get the list of all captured photo files */
    fun getCapturedPhotos(): List<File> = capturedPhotos.toList()
}

