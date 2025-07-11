// chemin/simule: /data/repositories/impl/TransactionRepositoryImpl.kt
package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Date
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType

class TransactionRepositoryImpl : TransactionRepository {
    
    private val client = PocketBaseClient
    private val gson = Gson()
    private val httpClient = okhttp3.OkHttpClient()

    // Noms des collections dans PocketBase
    private object Collections {
        const val TRANSACTIONS = "transactions"
    }

    override suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour ne récupérer que les transactions de l'utilisateur connecté dans la période
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId' && date >= '$debut' && date <= '$fin'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.TRANSACTIONS}/records?filter=$filtreEncode&perPage=100&sort=-date"

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
            val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, Transaction::class.java).type
            val resultatPagine: ListeResultats<Transaction> = gson.fromJson(corpsReponse, typeReponse)

            Result.success(resultatPagine.items)
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
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour ne récupérer que les transactions du compte spécifié
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId' && compte_id = '$compteId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.TRANSACTIONS}/records?filter=$filtreEncode&perPage=100&sort=-date"

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
            val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, Transaction::class.java).type
            val resultatPagine: ListeResultats<Transaction> = gson.fromJson(corpsReponse, typeReponse)

            Result.success(resultatPagine.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerTransaction(transaction: Transaction): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé pour la création."))

            // Injecte l'ID de l'utilisateur dans l'objet transaction
            val transactionAvecUtilisateur = transaction.copy(utilisateurId = utilisateurId)
            val corpsJson = gson.toJson(transactionAvecUtilisateur)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/${Collections.TRANSACTIONS}/records")
                .addHeader("Authorization", "Bearer $token")
                .post(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la création: ${reponse.body?.string()}"))
                }
            }
            
            println("Transaction créée: $transaction")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
