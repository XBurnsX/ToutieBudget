package com.xburnsx.toutiebudget.data.services

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.workers.EtatFichesSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * SJA5 : comparaison Room ↔ PocketBase en LECTURE SEULE, collection par collection.
 * Ne corrige rien : signale les fiches présentes d'un seul côté (en tenant compte des envois en
 * attente et des suppressions récentes) avant toute correction contrôlée.
 */
object ComparaisonServeur {
    data class Ligne(
        val collection: String,
        val ici: Int,
        val serveur: Int,
        val absentesDuServeur: Int,
        val absentesIci: Int,
        val enAttente: Int
    ) {
        val coherente: Boolean get() = absentesDuServeur == 0 && absentesIci == 0
    }

    suspend fun comparer(context: Context): Result<List<Ligne>> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: error("Connexion requise.")
            val db = AppModule.provideBaseDeDonnees()
            val jobs = db.syncJobDao().getAllSyncJobs().first()
                .filter { it.status in setOf("PENDING", "FAILED", "IN_PROGRESS") }
            val enAttente = jobs.map { it.recordId }.filter { it.isNotBlank() }.toSet()
            val locales: Map<String, Set<String>> = mapOf(
                "comptes_cheques" to db.compteChequeDao().getComptesByUtilisateur(uid).first().map { it.id }.toSet(),
                "comptes_credits" to db.compteCreditDao().getComptesByUtilisateur(uid).first().map { it.id }.toSet(),
                "comptes_dettes" to db.compteDetteDao().getComptesByUtilisateur(uid).first().map { it.id }.toSet(),
                "comptes_investissement" to db.compteInvestissementDao().getComptesByUtilisateur(uid).first().map { it.id }.toSet(),
                "categories" to db.categorieDao().getCategoriesByUtilisateur(uid).first().map { it.id }.toSet(),
                "enveloppes" to db.enveloppeDao().getEnveloppesByUtilisateur(uid).first().map { it.id }.toSet(),
                "allocations_mensuelles" to db.allocationMensuelleDao().getAllocationsByUtilisateur(uid).first().map { it.id }.toSet(),
                "transactions" to db.transactionDao().getTransactionsByUtilisateur(uid).first().map { it.id }.toSet(),
                "tiers" to db.tiersDao().getTiersByUtilisateur(uid).first().map { it.id }.toSet(),
                "pret_personnel" to db.pretPersonnelDao().getPretsByUtilisateur(uid).first().map { it.id }.toSet()
            )
            locales.map { (collection, ici) ->
                val serveur = idsServeur(collection, uid)
                Ligne(
                    collection = collection,
                    ici = ici.size,
                    serveur = serveur.size,
                    absentesDuServeur = ici.count { it !in serveur && it !in enAttente },
                    absentesIci = serveur.count { it !in ici && it !in enAttente &&
                        !EtatFichesSync.estSupprimee(context, collection, it) },
                    enAttente = ici.count { it in enAttente }
                )
            }
        }
    }

    /** Tous les IDs de l'utilisateur sur le serveur (toutes les pages, seulement le champ id). */
    private suspend fun idsServeur(collection: String, uid: String): Set<String> {
        val client = PocketBaseClient
        val ids = mutableSetOf<String>()
        var page = 1
        while (true) {
            val json = client.effectuerRequeteGet(
                "/api/collections/$collection/records",
                mapOf("page" to page.toString(), "perPage" to "500", "fields" to "id", "filter" to "utilisateur_id = '$uid'")
            )
            val obj = JsonParser.parseString(json).asJsonObject
            val items = obj.getAsJsonArray("items")
            items.forEach { ids += (it as JsonObject).get("id").asString }
            val total = obj.get("totalPages")?.asInt ?: 1
            if (page >= total || items.size() == 0) break
            page++
        }
        return ids
    }
}
