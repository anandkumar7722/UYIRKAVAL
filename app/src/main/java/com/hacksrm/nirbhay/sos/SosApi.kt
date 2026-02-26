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


    /**
     * POST /api/sos/media — Upload audio + images evidence for an active SOS.
     * multipart/form-data with fields: sos_id (string), victim_id (string),
     * images (file, up to 10×), audio (file, optional)
     */
    @Multipart
    @POST("/api/sos/media")
    suspend fun uploadMedia(
        @Part("sos_id") sosId: RequestBody,
        @Part("victim_id") victimId: RequestBody,
        @Part images: List<MultipartBody.Part>,
        @Part audio: MultipartBody.Part? = null
    ): Response<MediaUploadResponse>
}
