package com.hacksrm.nirbhay.sos

data class SosCreateRequest(
    val victim_id: String,
    val emergency_token: String,
    val lat: Double,
    val lng: Double,
    val trigger_method: String,
    val risk_score: Int = 0,
    val battery_level: Int = 0,
    val timestamp: Long? = null,
    val audio_file_path: String? = null
)

// Response placeholder
data class SosCreateResponse(
    val success: Boolean = true,
    val message: String? = null,
    val sos_id: String? = null,
    val is_new: Boolean? = null
)

/**
 * Response from POST /api/sos/media
 * Backend returns public URLs for uploaded audio and images.
 */
data class MediaUploadResponse(
    val success: Boolean = true,
    val message: String? = null,
    val sos_id: String? = null,
    val audio_url: String? = null,
    val image_urls: List<String>? = null
)
