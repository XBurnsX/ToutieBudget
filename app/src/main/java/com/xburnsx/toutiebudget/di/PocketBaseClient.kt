// chemin/simule: /di/PocketBaseClient.kt
// Client PocketBase personnalisé avec OkHttp

package com.xburnsx.toutiebudget.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException

object PocketBaseClient {

    private val client = OkHttpClient()
    private val gson = Gson()
    private var baseUrl: String? = null
    private var authToken: String? = null

    suspend fun initialize() {
        baseUrl = UrlResolver.getActiveUrl()
    }

    suspend fun loginWithGoogle(authCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (baseUrl == null) initialize()
            val requestBody = JsonObject().apply {
                addProperty("code", authCode)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/collections/users/auth-with-oauth2?provider=google")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)
                authToken = authResponse.token
                Result.success(Unit)
            } else {
                Result.failure(Exception("Auth failed: ${response.code} - ${response.body?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        authToken = null
    }

    fun isAuthenticated(): Boolean {
        return authToken != null
    }

    // ... autres fonctions get/post ...

    data class AuthResponse(
        val token: String,
        val record: UserRecord
    )

    data class UserRecord(
        val id: String,
        val email: String,
        val name: String?
    )
}
