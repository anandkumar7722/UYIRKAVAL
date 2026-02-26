package com.hacksrm.nirbhay.data

import android.content.Context
import android.util.Log
import com.hacksrm.nirbhay.auth.AuthRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Repository for Guardian CRUD operations.
 * Uses the same base URL and pattern as AuthRepository.
 */
object GuardianRepository {
    private const val TAG = "GuardianRepo"
    private const val BASE_URL = "https://nirbhay-5gcekoejfa-el.a.run.app"

    private val api: GuardianApi by lazy {
        val logging = HttpLoggingInterceptor { msg -> Log.i("OkHttp-Guardian", msg) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GuardianApi::class.java)
    }

    // ── ADD ──────────────────────────────────────────────────

    /**
     * Add a guardian for the currently logged-in user.
     * @return Pair(true, success_message) or Pair(false, error_message)
     */
    suspend fun addGuardian(
        context: Context,
        contactName: String,
        contactPhone: String?,
        contactEmail: String?,
        relation: String?
    ): Pair<Boolean, String> {
        val userId = AuthRepository.getUserId(context)
            ?: return Pair(false, "Not logged in")

        return try {
            val req = AddGuardianRequest(
                user_id = userId,
                contact_name = contactName,
                contact_phone = contactPhone?.takeIf { it.isNotBlank() },
                contact_email = contactEmail?.takeIf { it.isNotBlank() },
                relation = relation?.takeIf { it.isNotBlank() },
                notify_via_sms = false,
                notify_via_email = true
            )
            Log.i(TAG, "📤 POST /api/guardians name=$contactName")

            val resp = api.addGuardian(req)
            if (resp.isSuccessful && resp.body()?.success == true) {
                Log.i(TAG, "✅ Guardian added: ${resp.body()?.guardian?.id}")
                Pair(true, resp.body()?.message ?: "Guardian added")
            } else {
                val detail = parseError(resp.errorBody()?.string(), resp.code())
                Log.w(TAG, "❌ Add guardian failed: $detail")
                Pair(false, detail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Add guardian exception", e)
            Pair(false, e.message ?: "Network error")
        }
    }

    // ── LIST ─────────────────────────────────────────────────

    /**
     * Fetch all guardians for the current user.
     * @return Pair(true, list) or Pair(false, empty_list) + logs the error
     */
    suspend fun getGuardians(context: Context): Pair<Boolean, List<GuardianData>> {
        val userId = AuthRepository.getUserId(context)
            ?: return Pair(false, emptyList())

        return try {
            Log.i(TAG, "📤 GET /api/guardians/$userId")
            val resp = api.getGuardians(userId)
            if (resp.isSuccessful && resp.body()?.success == true) {
                val list = resp.body()?.guardians ?: emptyList()
                Log.i(TAG, "✅ Fetched ${list.size} guardians")
                Pair(true, list)
            } else {
                val detail = parseError(resp.errorBody()?.string(), resp.code())
                Log.w(TAG, "❌ Get guardians failed: $detail")
                Pair(false, emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Get guardians exception", e)
            Pair(false, emptyList())
        }
    }

    // ── UPDATE ───────────────────────────────────────────────

    suspend fun updateGuardian(
        context: Context,
        guardianId: String,
        contactName: String?,
        contactPhone: String?,
        contactEmail: String?,
        relation: String?
    ): Pair<Boolean, String> {
        val userId = AuthRepository.getUserId(context)
            ?: return Pair(false, "Not logged in")

        return try {
            val req = UpdateGuardianRequest(
                user_id = userId,
                contact_name = contactName,
                contact_phone = contactPhone,
                contact_email = contactEmail,
                relation = relation
            )
            Log.i(TAG, "📤 PUT /api/guardians/$guardianId")

            val resp = api.updateGuardian(guardianId, req)
            if (resp.isSuccessful && resp.body()?.success == true) {
                Log.i(TAG, "✅ Guardian updated: $guardianId")
                Pair(true, resp.body()?.message ?: "Guardian updated")
            } else {
                val detail = parseError(resp.errorBody()?.string(), resp.code())
                Log.w(TAG, "❌ Update guardian failed: $detail")
                Pair(false, detail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Update guardian exception", e)
            Pair(false, e.message ?: "Network error")
        }
    }

    // ── DELETE ────────────────────────────────────────────────

    suspend fun deleteGuardian(
        context: Context,
        guardianId: String
    ): Pair<Boolean, String> {
        val userId = AuthRepository.getUserId(context)
            ?: return Pair(false, "Not logged in")

        return try {
            val req = DeleteGuardianRequest(user_id = userId)
            Log.i(TAG, "📤 DELETE /api/guardians/$guardianId")

            val resp = api.deleteGuardian(guardianId, req)
            if (resp.isSuccessful && resp.body()?.success == true) {
                Log.i(TAG, "✅ Guardian deleted: $guardianId")
                Pair(true, resp.body()?.message ?: "Guardian deleted")
            } else {
                val detail = parseError(resp.errorBody()?.string(), resp.code())
                Log.w(TAG, "❌ Delete guardian failed: $detail")
                Pair(false, detail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Delete guardian exception", e)
            Pair(false, e.message ?: "Network error")
        }
    }

    // ── Helper ───────────────────────────────────────────────

    private fun parseError(body: String?, code: Int): String {
        return try {
            JSONObject(body ?: "").optString("detail", "Request failed ($code)")
        } catch (_: Exception) {
            "Request failed ($code)"
        }
    }
}

