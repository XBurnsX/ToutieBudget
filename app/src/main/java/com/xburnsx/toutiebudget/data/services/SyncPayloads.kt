package com.xburnsx.toutiebudget.data.services

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.room.daos.SyncJobDao
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.utils.IdGenerator
import com.xburnsx.toutiebudget.data.room.entities.AllocationMensuelle as AllocationMensuelleEntity
import com.xburnsx.toutiebudget.data.room.entities.Categorie as CategorieEntity
import com.xburnsx.toutiebudget.data.room.entities.CompteCheque as CompteChequeEntity
import com.xburnsx.toutiebudget.data.room.entities.CompteCredit as CompteCreditEntity
import com.xburnsx.toutiebudget.data.room.entities.CompteDette as CompteDetteEntity
import com.xburnsx.toutiebudget.data.room.entities.CompteInvestissement as CompteInvestissementEntity
import com.xburnsx.toutiebudget.data.room.entities.Enveloppe as EnveloppeEntity
import com.xburnsx.toutiebudget.data.room.entities.PretPersonnel as PretPersonnelEntity
import com.xburnsx.toutiebudget.data.room.entities.Tiers as TiersEntity
import com.xburnsx.toutiebudget.data.room.entities.Transaction as TransactionEntity

/**
 * Source unique du format JSON envoyé à PocketBase pour chaque entité Room,
 * et de la création des SyncJob correspondants.
 *
 * Les repositories ET l'écran de gestion de la base passent par ici, pour qu'une
 * modification faite n'importe où dans l'app finisse toujours sur le serveur.
 */
object SyncPayloads {

    // Comptes : nulls envoyés explicitement (comme avant dans CompteRepositoryRoomImpl)
    private val gsonAvecNulls: Gson = GsonBuilder().serializeNulls().create()
    private val gsonSimple: Gson = GsonBuilder().create()
    private val gsonSnakeCase: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    /** Type de SyncJob (voir SyncCollections) pour une entité Room. */
    fun typePour(entite: Any): String = when (entite) {
        is TransactionEntity -> "TRANSACTION"
        is CompteChequeEntity -> "COMPTE_CHEQUE"
        is CompteCreditEntity -> "COMPTE_CREDIT"
        is CompteDetteEntity -> "COMPTE_DETTE"
        is CompteInvestissementEntity -> "COMPTE_INVESTISSEMENT"
        is AllocationMensuelleEntity -> "ALLOCATION_MENSUELLE"
        is EnveloppeEntity -> "ENVELOPPE"
        is CategorieEntity -> "CATEGORIE"
        is TiersEntity -> "TIERS"
        is PretPersonnelEntity -> "PRET_PERSONNEL"
        else -> throw IllegalArgumentException("Entité non synchronisable: ${entite::class.simpleName}")
    }

    /** JSON envoyé à PocketBase pour une entité Room. */
    fun jsonPour(entite: Any): String = when (entite) {
        is CompteChequeEntity -> gsonAvecNulls.toJson(mapOf(
            "id" to entite.id,
            "utilisateur_id" to entite.utilisateurId,
            "nom" to entite.nom,
            "solde" to entite.solde,
            "pret_a_placer" to entite.pretAPlacerRaw,
            "couleur" to entite.couleur,
            "archive" to entite.estArchive,
            "ordre" to entite.ordre,
            "collection" to entite.collection
        ))
        is CompteCreditEntity -> gsonAvecNulls.toJson(mapOf(
            "id" to entite.id,
            "utilisateur_id" to entite.utilisateurId,
            "nom" to entite.nom,
            "solde_utilise" to entite.soldeUtilise,
            "couleur" to entite.couleur,
            "archive" to entite.estArchive,
            "ordre" to entite.ordre,
            "limite_credit" to entite.limiteCredit,
            "taux_interet" to entite.tauxInteret,
            "paiement_minimum" to entite.paiementMinimum,
            "frais_mensuels_json" to entite.fraisMensuelsJson,
            "collection" to entite.collection,
            // Objet JSON (et non texte) pour le champ JSON PocketBase ; ignoré tant que le champ n'existe pas
            "details_carte_json" to entite.detailsCarteJson?.let {
                try { com.google.gson.JsonParser.parseString(it) } catch (_: Exception) { null }
            }
        ))
        is CompteDetteEntity -> gsonAvecNulls.toJson(mapOf(
            "id" to entite.id,
            "utilisateur_id" to entite.utilisateurId,
            "nom" to entite.nom,
            "solde_dette" to entite.soldeDette,
            "archive" to entite.estArchive,
            "ordre" to entite.ordre,
            "montant_initial" to entite.montantInitial,
            "taux_interet" to entite.tauxInteret,
            "paiement_minimum" to entite.paiementMinimum,
            "duree_mois_pret" to entite.dureeMoisPret,
            "paiement_effectue" to entite.paiementEffectue,
            "prix_total" to entite.prixTotal,
            "collection" to entite.collection
        ))
        is CompteInvestissementEntity -> gsonAvecNulls.toJson(mapOf(
            "id" to entite.id,
            "utilisateur_id" to entite.utilisateurId,
            "nom" to entite.nom,
            "solde" to entite.solde,
            "couleur" to entite.couleur,
            "archive" to entite.estArchive,
            "ordre" to entite.ordre,
            "collection" to entite.collection
        ))
        is EnveloppeEntity -> gsonSimple.toJson(mapOf(
            "id" to entite.id,
            "utilisateur_id" to entite.utilisateurId,
            "nom" to entite.nom,
            "categorie_id" to entite.categorieId,
            "est_archive" to entite.estArchive,
            "ordre" to entite.ordre,
            "frequence_objectif" to entite.typeObjectif.toString(),
            "montant_objectif" to entite.objectifMontant,
            "date_objectif" to entite.dateObjectif,
            "date_debut_objectif" to entite.dateDebutObjectif,
            "objectif_jour" to entite.objectifJour,
            "reset_apres_echeance" to entite.resetApresEcheance
        ))
        is PretPersonnelEntity -> gsonSnakeCase.toJson(mapOf(
            "id" to entite.id,
            "utilisateur_id" to entite.utilisateurId,
            "nom_tiers" to entite.nomTiers,
            "montant_initial" to entite.montantInitial,
            "solde" to entite.solde,
            "type" to entite.type,
            "archive" to entite.estArchive,
            "date_creation" to entite.dateCreation,
            "created" to entite.created,
            "updated" to entite.updated
        ))
        is TransactionEntity, is AllocationMensuelleEntity, is CategorieEntity, is TiersEntity ->
            gsonSnakeCase.toJson(entite)
        else -> throw IllegalArgumentException("Entité non synchronisable: ${entite::class.simpleName}")
    }

    /**
     * Ajoute à la file un SyncJob CREATE ou UPDATE pour cette entité, puis déclenche la synchro.
     */
    suspend fun enregistrer(syncJobDao: SyncJobDao, action: String, entite: Any, recordId: String) {
        syncJobDao.insertSyncJob(
            SyncJob(
                id = IdGenerator.generateId(),
                type = typePour(entite),
                action = action,
                dataJson = jsonPour(entite),
                recordId = recordId,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
        )
        SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
    }

    /**
     * Ajoute à la file un SyncJob DELETE, puis déclenche la synchro.
     */
    suspend fun enregistrerSuppression(syncJobDao: SyncJobDao, type: String, recordId: String) {
        syncJobDao.insertSyncJob(
            SyncJob(
                id = IdGenerator.generateId(),
                type = type,
                action = "DELETE",
                dataJson = gsonSimple.toJson(mapOf("id" to recordId)),
                recordId = recordId,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
        )
        SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
    }
}
