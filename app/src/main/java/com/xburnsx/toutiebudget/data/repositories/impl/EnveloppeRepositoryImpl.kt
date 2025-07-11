// chemin/simule: /data/repositories/impl/EnveloppeRepositoryImpl.kt
package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Date
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType

class EnveloppeRepositoryImpl : EnveloppeRepository {
    
    private val client = PocketBaseClient
    private val gson = Gson().newBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val httpClient = okhttp3.OkHttpClient()

    // Noms des collections dans PocketBase
    private object Collections {
        const val ENVELOPPES = "enveloppes"
        const val ALLOCATIONS = "allocations_mensuelles"
    }

    override suspend fun recupererToutesLesEnveloppes(): Result<List<Enveloppe>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour ne récupérer que les enregistrements de l'utilisateur connecté
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records?filter=$filtreEncode&perPage=100"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération des enveloppes: ${reponse.code} ${reponse.body?.string()}")
            }

            val corpsReponse = reponse.body!!.string()
            val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, Enveloppe::class.java).type
            val resultatPagine: ListeResultats<Enveloppe> = gson.fromJson(corpsReponse, typeReponse)

            val enveloppes = resultatPagine.items.filter { !it.estArchive }.sortedBy { it.ordre }
            Result.success(enveloppes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererAllocationsPourMois(mois: Date): Result<List<AllocationMensuelle>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour ne récupérer que les allocations de l'utilisateur connecté
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtreEncode&perPage=100"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération des allocations: ${reponse.code} ${reponse.body?.string()}")
            }

            val corpsReponse = reponse.body!!.string()
            val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, AllocationMensuelle::class.java).type
            val resultatPagine: ListeResultats<AllocationMensuelle> = gson.fromJson(corpsReponse, typeReponse)

            Result.success(resultatPagine.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val corpsJson = gson.toJson(allocation)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/${Collections.ALLOCATIONS}/records/${allocation.id}")
                .addHeader("Authorization", "Bearer $token")
                .patch(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la mise à jour: ${reponse.body?.string()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé pour la création."))

            // Injecte l'ID de l'utilisateur dans l'objet enveloppe
            val enveloppeAvecUtilisateur = enveloppe.copy(utilisateurId = utilisateurId)
            val corpsJson = gson.toJson(enveloppeAvecUtilisateur)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/${Collections.ENVELOPPES}/records")
                .addHeader("Authorization", "Bearer $token")
                .post(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la création: ${reponse.body?.string()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): Result<AllocationMensuelle> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            // D'abord, essayer de récupérer l'allocation existante
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour trouver l'allocation existante
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId' && enveloppe_id = '$enveloppeId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtreEncode&perPage=1"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (reponse.isSuccessful) {
                val corpsReponse = reponse.body!!.string()
                val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, AllocationMensuelle::class.java).type
                val resultatPagine: ListeResultats<AllocationMensuelle> = gson.fromJson(corpsReponse, typeReponse)

                if (resultatPagine.items.isNotEmpty()) {
                    return@withContext Result.success(resultatPagine.items.first())
                }
            }

            // Si aucune allocation n'existe, en créer une nouvelle
            val nouvelleAllocation = AllocationMensuelle(
                id = UUID.randomUUID().toString(),
                utilisateurId = utilisateurId,
                enveloppeId = enveloppeId,
                mois = mois,
                solde = 0.0,
                alloue = 0.0,
                depense = 0.0,
                compteSourceId = null,
                collectionCompteSource = null
            )

            val corpsJson = gson.toJson(nouvelleAllocation)
            val requeteCreation = Request.Builder()
                .url("$urlBase/api/collections/${Collections.ALLOCATIONS}/records")
                .addHeader("Authorization", "Bearer $token")
                .post(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requeteCreation).execute().use { reponseCreation ->
                if (!reponseCreation.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la création de l'allocation: ${reponseCreation.body?.string()}"))
                }
            }

            Result.success(nouvelleAllocation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/enveloppe/records/${enveloppe.id}"
            val json = gson.toJson(enveloppe)
            val body = json.toRequestBody("application/json".toMediaType())
            val requete = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .patch(body)
                .build()
            val response = httpClient.newCall(requete).execute()
            if (!response.isSuccessful) throw Exception("Erreur PocketBase: ${response.code}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
