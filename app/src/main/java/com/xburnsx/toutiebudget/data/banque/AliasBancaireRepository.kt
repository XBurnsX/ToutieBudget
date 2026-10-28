package com.xburnsx.toutiebudget.data.banque

import androidx.room.withTransaction
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.daos.AliasBancaireDao
import com.xburnsx.toutiebudget.data.room.daos.SyncJobDao
import com.xburnsx.toutiebudget.data.room.daos.TiersDao
import com.xburnsx.toutiebudget.data.room.entities.AliasBancaire
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.IdGenerator
import kotlinx.coroutines.flow.first

/**
 * BANQ-02 : les alias sont des règles volontaires rattachées à un Tiers EXISTANT.
 * Une écriture locale et son SyncJob sont engagés dans la même transaction Room.
 * Aucune transaction financière n'est créée ou modifiée par ce composant.
 */
class AliasBancaireRepository(
    private val database: ToutieBudgetDatabase,
    private val aliasDao: AliasBancaireDao,
    private val tiersDao: TiersDao,
    private val syncJobDao: SyncJobDao
) {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    private fun utilisateurConnecte(): String =
        PocketBaseClient.obtenirUtilisateurConnecte()?.id?.takeIf { it.isNotBlank() }
            ?: error("Utilisateur non connecté.")

    suspend fun toutes(): List<RegleAliasBancaire> =
        aliasDao.toutesParUtilisateur(utilisateurConnecte()).map { it.versRegle() }

    suspend fun reconnaitre(compteId: String, libelleOriginal: String): Tiers? {
        val userId = utilisateurConnecte()
        val regles = aliasDao.toutesParUtilisateur(userId).map { it.versRegle() }
        val tiers = tiersDao.getTiersByUtilisateur(userId).first().map {
            Tiers(id = it.id, nom = it.nom, utilisateur_id = it.utilisateurId,
                created = it.created, updated = it.updated,
                collectionId = it.collectionId, collectionName = it.collectionName)
        }
        return ReconnaissanceTiersBancaire.reconnaitre(userId, compteId, libelleOriginal, regles, tiers)
    }

    /** Pour une règle globale, l'appelant DOIT transmettre explicitement compteId = null. */
    suspend fun enregistrer(regle: RegleAliasBancaire): RegleAliasBancaire {
        val userId = utilisateurConnecte()
        require(regle.utilisateurId == userId) { "Règle d'un autre utilisateur." }
        require(regle.libelleBrut.isNotBlank()) { "Libellé bancaire requis." }
        require(regle.compteId == null || regle.compteId.isNotBlank()) { "Compte invalide." }
        require(tiersDao.getTiersById(regle.tiersId)?.utilisateurId == userId) {
            "Le tiers existant doit appartenir à l'utilisateur."
        }
        val resultat = database.withTransaction {
            val regles = aliasDao.toutesParUtilisateur(userId)
            val ancienne = if (regle.id.isBlank()) null else aliasDao.trouverParId(regle.id)
            require(ancienne == null || ancienne.utilisateurId == userId) {
                "Modification d'une règle d'un autre utilisateur interdite."
            }
            val memesLibelles = regles.filter {
                it.id != regle.id && it.compteId == regle.compteId &&
                    ReconnaissanceTiersBancaire.normaliser(it.libelleBrut) ==
                    ReconnaissanceTiersBancaire.normaliser(regle.libelleBrut)
            }
            require(memesLibelles.all { it.tiersId == regle.tiersId }) {
                "Deux tiers différents pour le même libellé : corriger la règle existante."
            }
            val identique = memesLibelles.firstOrNull()
            val id = ancienne?.id ?: identique?.id ?: regle.id.ifBlank { IdGenerator.generateId() }
            val courante = aliasDao.trouverParId(id)
            require(courante == null || courante.utilisateurId == userId) { "ID de règle déjà utilisé." }
            val entite = AliasBancaire(id, userId, regle.compteId,
                regle.libelleBrut.trim(), regle.tiersId, regle.active)
            aliasDao.enregistrer(entite)
            syncJobDao.insertSyncJob(SyncJob(
                id = IdGenerator.generateId(), type = "ALIAS_BANCAIRE",
                action = if (courante == null) "CREATE" else "UPDATE",
                dataJson = gson.toJson(mapOf(
                    "id" to entite.id,
                    "utilisateur_id" to entite.utilisateurId,
                    "compte_id" to (entite.compteId ?: ""),
                    "libelle_brut" to entite.libelleBrut,
                    "tiers_id" to entite.tiersId,
                    "active" to entite.active
                )), recordId = id,
                utilisateurId = userId, createdAt = System.currentTimeMillis(), status = "PENDING"
            ))
            entite.versRegle()
        }
        SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
        return resultat
    }

    suspend fun desactiver(id: String): RegleAliasBancaire {
        val existante = aliasDao.trouverParId(id) ?: error("Règle introuvable.")
        require(existante.utilisateurId == utilisateurConnecte()) { "Utilisateur incorrect." }
        return enregistrer(existante.versRegle().copy(active = false))
    }

    suspend fun supprimer(id: String) {
        val userId = utilisateurConnecte()
        database.withTransaction {
            val ancienne = aliasDao.trouverParId(id) ?: return@withTransaction
            require(ancienne.utilisateurId == userId) { "Utilisateur incorrect." }
            aliasDao.supprimer(id, userId)
            syncJobDao.insertSyncJob(SyncJob(
                id = IdGenerator.generateId(), type = "ALIAS_BANCAIRE",
                action = "DELETE", dataJson = gson.toJson(mapOf("id" to id)),
                recordId = id, utilisateurId = userId,
                createdAt = System.currentTimeMillis(), status = "PENDING"
            ))
        }
        SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
    }

    private fun AliasBancaire.versRegle() =
        RegleAliasBancaire(id, utilisateurId, compteId, libelleBrut, tiersId, active)
}
