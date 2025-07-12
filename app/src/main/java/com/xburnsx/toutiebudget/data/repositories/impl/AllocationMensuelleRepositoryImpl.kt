package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

/**
 * Implémentation minimale du repository d'allocations mensuelles.
 * Elle effectue directement des appels REST à PocketBase.
 * Collection PocketBase : "allocations_mensuelles" (à ajuster si nécessaire).
 */
class AllocationMensuelleRepositoryImpl : AllocationMensuelleRepository {

    private val client = PocketBaseClient
    private val gson = Gson()
    private val httpClient = okhttp3.OkHttpClient()

    private companion object {
        const val COLLECTION = "allocations_mensuelles"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }

    override suspend fun getAllocationById(id: String): AllocationMensuelle? = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext null
            val urlBase = UrlResolver.obtenirUrlActive()
            val request = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records/$id")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                return@withContext gson.fromJson(resp.body!!.string(), AllocationMensuelle::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun mettreAJourAllocation(
        id: String,
        nouveauSolde: Double,
        nouvelleDepense: Double
    ) = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext
            val urlBase = UrlResolver.obtenirUrlActive()
            val bodyJson = "{\"solde\":$nouveauSolde,\"depense\":$nouvelleDepense}"
            val request = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records/$id")
                .addHeader("Authorization", "Bearer $token")
                .patch(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {
        }
    }

    override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle) = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext
            val urlBase = UrlResolver.obtenirUrlActive()
            val bodyJson = gson.toJson(allocation)
            val request = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records/${allocation.id}")
                .addHeader("Authorization", "Bearer $token")
                .put(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {
        }
    }

    override suspend fun mettreAJourCompteSource(id: String, compteSourceId: String, collectionCompteSource: String) = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext
            val urlBase = UrlResolver.obtenirUrlActive()
            val bodyJson = "{\"compte_source_id\":\"$compteSourceId\",\"collection_compte_source\":\"$collectionCompteSource\"}"
            val request = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records/$id")
                .addHeader("Authorization", "Bearer $token")
                .patch(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {
        }
    }

    override suspend fun getOrCreateAllocationMensuelle(enveloppeId: String, mois: Date): AllocationMensuelle = withContext(Dispatchers.IO) {
        // 1. Chercher s'il existe déjà une allocation pour enveloppe+mois
        val token = client.obtenirToken() ?: throw Exception("Token manquant")
        val urlBase = UrlResolver.obtenirUrlActive()
        val moisIso = DATE_FORMAT.format(mois)
        val filtre = java.net.URLEncoder.encode("enveloppe_id='$enveloppeId' && mois='$moisIso'", "UTF-8")
        val url = "$urlBase/api/collections/$COLLECTION/records?filter=$filtre&perPage=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Erreur de récupération allocation: ${resp.code}")
            val data = resp.body!!.string()
            val listType = com.google.gson.reflect.TypeToken.getParameterized(java.util.List::class.java, AllocationMensuelle::class.java).type
            val items: List<AllocationMensuelle> = gson.fromJson(data, listType)
            if (items.isNotEmpty()) return@withContext items.first()
        }

        // 2. Sinon, créer
        val bodyJson = gson.toJson(
            AllocationMensuelle(
                id = "", // PocketBase en généra un
                utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: throw Exception("Utilisateur manquant"),
                enveloppeId = enveloppeId,
                mois = mois,
                solde = 0.0,
                alloue = 0.0,
                depense = 0.0,
                compteSourceId = null,
                collectionCompteSource = null
            )
        )
        val createReq = Request.Builder()
            .url("$urlBase/api/collections/$COLLECTION/records")
            .addHeader("Authorization", "Bearer $token")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(createReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Erreur création allocation: ${resp.code} ${resp.body?.string()}")
            return@withContext gson.fromJson(resp.body!!.string(), AllocationMensuelle::class.java)
        }
    }
}
