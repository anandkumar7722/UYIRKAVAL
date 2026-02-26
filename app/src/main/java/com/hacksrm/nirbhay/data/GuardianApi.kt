package com.hacksrm.nirbhay.data

import retrofit2.Response
import retrofit2.http.*

// ─── Request DTOs ────────────────────────────────────────────

data class AddGuardianRequest(
    val user_id: String,
    val contact_name: String,
    val contact_phone: String? = null,
    val contact_email: String? = null,
    val relation: String? = null,
    val notify_via_sms: Boolean = false,
    val notify_via_email: Boolean = true
)

data class UpdateGuardianRequest(
    val user_id: String,
    val contact_name: String? = null,
    val contact_phone: String? = null,
    val contact_email: String? = null,
    val relation: String? = null,
    val notify_via_sms: Boolean? = null,
    val notify_via_email: Boolean? = null
)

data class DeleteGuardianRequest(
    val user_id: String
)

// ─── Response DTOs ───────────────────────────────────────────

data class GuardianData(
    val id: String,
    val user_id: String,
    val contact_name: String,
    val contact_phone: String? = null,
    val contact_email: String? = null,
    val relation: String? = null,
    val notify_via_sms: Boolean = false,
    val notify_via_email: Boolean = true,
    val created_at: String? = null
)

data class AddGuardianResponse(
    val success: Boolean,
    val message: String? = null,
    val guardian: GuardianData? = null
)

data class GuardiansListResponse(
    val success: Boolean,
    val message: String? = null,
    val guardians: List<GuardianData> = emptyList(),
    val count: Int = 0
)

data class UpdateGuardianResponse(
    val success: Boolean,
    val message: String? = null,
    val guardian: GuardianData? = null
)

data class DeleteGuardianResponse(
    val success: Boolean,
    val message: String? = null,
    val guardian: GuardianData? = null
)

// ─── Retrofit Interface ─────────────────────────────────────

interface GuardianApi {

    /** Add a new guardian */
    @POST("/api/guardians")
    suspend fun addGuardian(@Body body: AddGuardianRequest): Response<AddGuardianResponse>

    /** Get all guardians for a user */
    @GET("/api/guardians/{user_id}")
    suspend fun getGuardians(@Path("user_id") userId: String): Response<GuardiansListResponse>

    /** Update an existing guardian (ownership checked via user_id in body) */
    @PUT("/api/guardians/{guardian_id}")
    suspend fun updateGuardian(
        @Path("guardian_id") guardianId: String,
        @Body body: UpdateGuardianRequest
    ): Response<UpdateGuardianResponse>

    /** Delete a guardian (needs user_id in body for ownership verification) */
    @HTTP(method = "DELETE", path = "/api/guardians/{guardian_id}", hasBody = true)
    suspend fun deleteGuardian(
        @Path("guardian_id") guardianId: String,
        @Body body: DeleteGuardianRequest
    ): Response<DeleteGuardianResponse>
}

