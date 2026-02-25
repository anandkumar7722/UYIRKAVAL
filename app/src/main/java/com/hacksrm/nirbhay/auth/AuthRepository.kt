package com.hacksrm.nirbhay.auth

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.random.Random

/**
 * Handles registration & login via the backend, persists user_id in SharedPreferences.
 *
 * After a successful login or register the app should read userId everywhere from
 * [getUserId] instead of a hardcoded constant.
 */
object AuthRepository {
    private const val TAG = "AuthRepository"
    private const val BASE_URL = "https://nirbhay-5gcekoejfa-el.a.run.app"
    private const val PREFS_NAME = "nirbhay_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_FULL_NAME = "user_full_name"

    private val api: AuthApi by lazy {
        val logging = HttpLoggingInterceptor { msg -> Log.i("OkHttp-Auth", msg) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    // ── Public helpers to read persisted identity ────────────

    /** Returns the saved user_id, or null if not logged in yet. */
    fun getUserId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, null)
    }

    fun getUserEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)
    }

    fun getFullName(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FULL_NAME, null)
    }

    /** Clear all saved auth state (logout). */
    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_FULL_NAME)
            .apply()
    }

    // ── Private persistence ──────────────────────────────────

    private fun saveSession(context: Context, userId: String, email: String, fullName: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putString(KEY_FULL_NAME, fullName ?: "")
            .apply()
        Log.i(TAG, "✅ Session saved — user_id=$userId email=$email")
    }

    // ── API calls ────────────────────────────────────────────

    /**
     * Register a new user.
     * full_name = part of email before '@'
     * phone_number = random 10-digit number prefixed with +91
     * secure_pin = "0000"
     *
     * @return Pair(success=true, userId) or Pair(false, errorMessage)
     */
    suspend fun register(context: Context, email: String, password: String): Pair<Boolean, String> {
        return try {
            val fullName = email.substringBefore("@")
            val phone = "+91" + (1_000_000_000L + Random.nextLong(9_000_000_000L)).toString()

            val req = RegisterRequest(
                email = email.lowercase().trim(),
                password = password,
                full_name = fullName,
                phone_number = phone,
                secure_pin = "0000"
            )
            Log.i(TAG, "📤 POST /api/auth/register email=${req.email} full_name=${req.full_name}")

            val resp = api.register(req)
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null && body.success && body.user_id != null) {
                    saveSession(context, body.user_id, email.lowercase().trim(), body.full_name)
                    Pair(true, body.user_id)
                } else {
                    Pair(false, body?.message ?: "Registration failed")
                }
            } else {
                val errorBody = resp.errorBody()?.string()
                val detail = try { JSONObject(errorBody ?: "").optString("detail", "Registration failed") } catch (_: Exception) { "Registration failed (${resp.code()})" }
                Log.w(TAG, "❌ Register failed: $detail")
                Pair(false, detail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Register exception", e)
            Pair(false, e.message ?: "Network error")
        }
    }

    /**
     * Login an existing user.
     * @return Pair(success=true, userId) or Pair(false, errorMessage)
     */
    suspend fun login(context: Context, email: String, password: String): Pair<Boolean, String> {
        return try {
            val req = LoginRequest(
                email = email.lowercase().trim(),
                password = password
            )
            Log.i(TAG, "📤 POST /api/auth/login email=${req.email}")

            val resp = api.login(req)
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null && body.success && body.user_id != null) {
                    saveSession(context, body.user_id, email.lowercase().trim(), body.full_name)
                    Pair(true, body.user_id)
                } else {
                    Pair(false, body?.message ?: "Login failed")
                }
            } else {
                val errorBody = resp.errorBody()?.string()
                val detail = try { JSONObject(errorBody ?: "").optString("detail", "Login failed") } catch (_: Exception) { "Login failed (${resp.code()})" }
                Log.w(TAG, "❌ Login failed: $detail")
                Pair(false, detail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Login exception", e)
            Pair(false, e.message ?: "Network error")
        }
    }
}

