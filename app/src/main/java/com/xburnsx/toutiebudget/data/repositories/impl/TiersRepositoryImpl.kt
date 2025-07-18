// chemin/simule: /data/repositories/impl/TiersRepositoryImpl.kt

package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class TiersRepositoryImpl : TiersRepository {

    private val client = PocketBaseClient
    private val httpClient = okhttp3.OkHttpClient()

    override suspend fun recupererTousLesTiers(): Result<List<Tiers>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))
            val urlBase = UrlResolver.obtenirUrlActive()

            val filtreEncode = URLEncoder.encode("utilisateur_id='$utilisateurId'", "UTF-8")

                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()


                Result.success(resultat.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerTiers(tiers: Tiers): Result<Tiers> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = UrlResolver.obtenirUrlActive()

            )


                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()


                val errorBody = response.body?.string()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

            return@withContext Result.success(emptyList())
        }
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))
            val urlBase = UrlResolver.obtenirUrlActive()

            val filtreEncode = URLEncoder.encode("utilisateur_id='$utilisateurId' && nom~'$recherche'", "UTF-8")

                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()


                Result.success(resultat.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    }