// chemin/simule: /data/repositories/impl/TransactionRepositoryImpl.kt
// Dépendances: PocketBaseClient, Gson, SafeDateAdapter, OkHttp3, Transaction, TypeTransaction

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.SafeDateAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implémentation du repository des transactions avec PocketBase.
 * Gère la création, récupération et suppression des transactions.
 */
class TransactionRepositoryImpl : TransactionRepository {
    
    private val client = PocketBaseClient
    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(Date::class.java, SafeDateAdapter())
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val httpClient = okhttp3.OkHttpClient()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // Noms des collections dans PocketBase
    private object Collections {
        const val TRANSACTIONS = "transactions"
    }

    override suspend fun creerTransaction(transaction: Transaction): Result<Transaction> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Préparer les données pour PocketBase
            val donneesTransaction = mapOf(
                "utilisateur_id" to utilisateurId,
                "type" to transaction.type.valeurPocketBase,
                "montant" to transaction.montant,
                "date" to dateFormatter.format(transaction.date),
                "note" to (transaction.note ?: ""),
                "compte_id" to transaction.compteId,
                "collection_compte" to transaction.collectionCompte,
                "allocation_mensuelle_id" to (transaction.allocationMensuelleId ?: ""),
                "tiers_id" to (transaction.tiersId ?: "")
            )

            val corpsRequete = gson.toJson(donneesTransaction)
            val url = "$urlBase/api/collections/${Collections.TRANSACTIONS}/records"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la création de la transaction: ${reponse.code} ${reponse.body?.string()}")
            }

            val corpsReponse = reponse.body!!.string()
            val transactionCreee = deserialiserTransaction(corpsReponse)
                ?: throw Exception("Erreur lors de la désérialisation de la transaction créée")

            Result.success(transactionCreee)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererToutesLesTransactions(): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken()
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour récupérer toutes les transactions de l'utilisateur connecté
            val filtreEncode = URLEncoder.encode(
                "utilisateur_id = '$utilisateurId'",
                "UTF-8"
            )
            val url = "$urlBase/api/collections/${Collections.TRANSACTIONS}/records?filter=$filtreEncode&perPage=500&sort=-date"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération de toutes les transactions: ${reponse.code} ${reponse.body?.string()}")
            }

            val corpsReponse = reponse.body!!.string()
            val transactions = deserialiserListeTransactions(corpsReponse)

            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val dateDebut = dateFormatter.format(debut)
            val dateFin = dateFormatter.format(fin)
            
            // Filtre pour ne récupérer que les transactions de l'utilisateur connecté dans la période
            val filtreEncode = URLEncoder.encode(
                "utilisateur_id = '$utilisateurId' && date >= '$dateDebut' && date <= '$dateFin'", 
                "UTF-8"
            )
            val url = "$urlBase/api/collections/${Collections.TRANSACTIONS}/records?filter=$filtreEncode&perPage=500&sort=-date"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération des transactions: ${reponse.code} ${reponse.body?.string()}")
            }

            val corpsReponse = reponse.body!!.string()
            val transactions = deserialiserListeTransactions(corpsReponse)

            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererTransactionsPourCompte(compteId: String, collectionCompte: String): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            println("DEBUG REPO: Recherche transactions pour compteId=$compteId, collectionCompte=$collectionCompte, utilisateurId=$utilisateurId")

            // Filtre pour récupérer les transactions d'un compte spécifique
            val filtreEncode = URLEncoder.encode(
                "utilisateur_id = '$utilisateurId' && compte_id = '$compteId' && collection_compte = '$collectionCompte'", 
                "UTF-8"
            )
            val url = "$urlBase/api/collections/${Collections.TRANSACTIONS}/records?filter=$filtreEncode&perPage=500&sort=-date"

            println("DEBUG REPO: URL de requête: $url")

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()

            println("DEBUG REPO: Code de réponse: ${reponse.code}")

            if (!reponse.isSuccessful) {
                val erreurCorps = reponse.body?.string() ?: "Erreur inconnue"
                println("DEBUG REPO: Erreur dans la réponse: $erreurCorps")
                throw Exception("Erreur lors de la récupération des transactions du compte: ${reponse.code} $erreurCorps")
            }

            val corpsReponse = reponse.body!!.string()
            println("DEBUG REPO: Réponse brute: $corpsReponse")

            val transactions = deserialiserListeTransactions(corpsReponse)
            println("DEBUG REPO: Nombre de transactions désérialisées: ${transactions.size}")

            Result.success(transactions)
        } catch (e: Exception) {
            println("DEBUG REPO: Exception: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun recupererTransactionsParAllocation(allocationId: String): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour récupérer les transactions d'une allocation spécifique
            val filtreEncode = URLEncoder.encode(
                "utilisateur_id = '$utilisateurId' && allocation_mensuelle_id = '$allocationId'", 
                "UTF-8"
            )
            val url = "$urlBase/api/collections/${Collections.TRANSACTIONS}/records?filter=$filtreEncode&perPage=500&sort=-date"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération des transactions de l'allocation: ${reponse.code} ${reponse.body?.string()}")
            }

            val corpsReponse = reponse.body!!.string()
            val transactions = deserialiserListeTransactions(corpsReponse)

            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerTransaction(transactionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }
        
        try {
            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val url = "$urlBase/api/collections/${Collections.TRANSACTIONS}/records/$transactionId"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la suppression de la transaction: ${reponse.code} ${reponse.body?.string()}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Désérialise une transaction simple depuis JSON PocketBase.
     */
    private fun deserialiserTransaction(json: String): Transaction? {
        return try {
            gson.fromJson(json, Transaction::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Désérialise une liste de transactions depuis JSON PocketBase.
     */
    private fun deserialiserListeTransactions(json: String): List<Transaction> {
        return try {
            val jsonObject = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val itemsArray = jsonObject.getAsJsonArray("items")
            
            itemsArray.map { item ->
                val transactionJson = item.toString()
                deserialiserTransaction(transactionJson)
            }.filterNotNull()
        } catch (e: Exception) {
            emptyList()
        }
    }
}