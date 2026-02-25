package com.hacksrm.nirbhay.sos

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface SosApi {
    @POST("/api/sos/trigger")
    suspend fun trigger(@Body body: SosCreateRequest): Response<SosCreateResponse>

    @POST("/api/sos/relay")
    suspend fun relay(@Body body: SosCreateRequest): Response<SosCreateResponse>

    @Multipart
    @POST("/api/sos/trigger")
    suspend fun triggerMultipart(
        @Part("victim_id") victimId: RequestBody,
        @Part("emergency_token") emergencyToken: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("trigger_method") triggerMethod: RequestBody,
        @Part("risk_score") riskScore: RequestBody,
        @Part("battery_level") batteryLevel: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part audio: MultipartBody.Part?
    ): Response<SosCreateResponse>

    /**
     * POST /api/sos/media — Upload audio/video evidence for an active SOS.
     * multipart/form-data with fields: sos_id (string), victim_id (string), audio (file), video (file)
     */
    @Multipart
    @POST("/api/sos/media")
    suspend fun uploadMedia(
        @Part("sos_id") sosId: RequestBody,
        @Part("victim_id") victimId: RequestBody,
        @Part audio: MultipartBody.Part? = null,
        @Part video: MultipartBody.Part? = null
    ): Response<MediaUploadResponse>
}
