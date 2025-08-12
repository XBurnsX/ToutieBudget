package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.repositories.PretPersonnelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class PretPersonnelRepositoryImpl : PretPersonnelRepository {
    private val client = PocketBaseClient
    private val http = OkHttpClient()
    private val gson = Gson()

    private fun deserializeList(json: String): List<PretPersonnel> {
        // PocketBase list format: {items:[...]} or raw; handle both
        return try {
            val wrapperType = object: TypeToken<PBListWrapper<PretPersonnel>>(){}.type
            val w: PBListWrapper<PretPersonnel> = gson.fromJson(json, wrapperType)
            w.items ?: emptyList()
        } catch (_: Exception) {
            val listType = object: TypeToken<List<PretPersonnel>>(){}.type
            gson.fromJson(json, listType) ?: emptyList()
        }
    }

    override suspend fun lister(): Result<List<PretPersonnel>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) return@withContext Result.success(emptyList())
        try {
            val userId = client.obtenirUtilisateurConnecte()?.id ?: return@withContext Result.failure(Exception("ID utilisateur manquant"))
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val base = client.obtenirUrlBaseActive()
            val filter = URLEncoder.encode("utilisateur_id = '$userId'", "UTF-8")
            val url = "$base/api/collections/pret_personnel/records?filter=$filter&perPage=500&sort=-created"
            val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").get().build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("Erreur liste: ${resp.code} ${resp.body?.string()}")
            val body = resp.body!!.string()
            val items = deserializeList(body)
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creer(pret: PretPersonnel): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) return@withContext Result.failure(Exception("Non connecté"))
        try {
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val base = client.obtenirUrlBaseActive()
            val url = "$base/api/collections/pret_personnel/records"
            val json = gson.toJson(pret)
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("Erreur création: ${resp.code} ${resp.body?.string()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimer(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) return@withContext Result.failure(Exception("Non connecté"))
        try {
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val base = client.obtenirUrlBaseActive()
            val url = "$base/api/collections/pret_personnel/records/$id"
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("Erreur suppression: ${resp.code} ${resp.body?.string()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private data class PBListWrapper<T>(val items: List<T>?)


