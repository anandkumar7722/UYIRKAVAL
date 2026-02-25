package com.hacksrm.nirbhay.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// ─── Request DTOs ────────────────────────────────────────────

data class RegisterRequest(
    val email: String,
    val password: String,
    val full_name: String,
    val phone_number: String,
    val secure_pin: String = "0000"
)

data class LoginRequest(
    val email: String,
    val password: String
)

// ─── Response DTOs ───────────────────────────────────────────

data class AuthResponse(
    val success: Boolean,
    val message: String? = null,
    val user_id: String? = null,
    val email: String? = null,
    val full_name: String? = null,
    val phone_number: String? = null
)

// ─── Retrofit interface ──────────────────────────────────────

interface AuthApi {
    @POST("/api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>
}

