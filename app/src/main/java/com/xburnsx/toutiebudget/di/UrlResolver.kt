// chemin/simule: /di/UrlResolver.kt
package com.xburnsx.toutiebudget.di

import com.xburnsx.toutiebudget.BuildConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import kotlinx.coroutines.withTimeoutOrNull

object UrlResolver {
    private var activeUrl: String? = null

    suspend fun getActiveUrl(): String {
        activeUrl?.let { return it }
        val isLocalAvailable = withTimeoutOrNull(500L) {
            try {
                HttpClient(OkHttp).head(BuildConfig.POCKETBASE_URL_LOCAL + "api/health")
                true
            } catch (e: Exception) {
                false
            }
        } ?: false

        return if (isLocalAvailable) {
            println("Serveur local détecté. Utilisation de l'URL locale.")
            BuildConfig.POCKETBASE_URL_LOCAL.also { activeUrl = it }
        } else {
            println("Serveur local non joignable. Utilisation de l'URL publique.")
            BuildConfig.POCKETBASE_URL_PUBLIC.also { activeUrl = it }
        }
    }
}
