package com.xburnsx.toutiebudget.data.banque

import androidx.room.withTransaction
import com.google.gson.Gson
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService
import com.xburnsx.toutiebudget.utils.IdGenerator
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.daos.OperationBancaireEnAttenteDao
import com.xburnsx.toutiebudget.data.room.entities.OperationBancaireEnAttente
import com.xburnsx.toutiebudget.di.PocketBaseClient
import java.security.MessageDigest
import java.time.LocalDate

/**
 * BANQ-03 : lecture/import idempotents, SANS aucune mutation financière.
 * Ne jamais déduire un débit bancaire de la création de cette fiche.
 */
class BoiteOperationsBancairesRepository(
    private val database: ToutieBudgetDatabase,
    private val dao: OperationBancaireEnAttenteDao
) {
    data class LigneBanque(
        val compteId: String,
        val collectionCompte: String,
        val source: String,
        val idExterne: String,
        val dateOperation: String,
        /** signé : dépense négative, crédit positif */
        val montantCentimes: Long,
        val libelleBrut: String,
        val statutBancaire: String
    )

    private fun userId(): String =
        PocketBaseClient.obtenirUtilisateurConnecte()?.id?.takeIf(String::isNotBlank)
            ?: error("Utilisateur connecté requis pour importer des opérations.")

    fun operationsAConfirmer() = dao.observerAConfirmer(userId())
    fun toutesLesOperations() = dao.observerToutes(userId())

    suspend fun importer(ligne: LigneBanque): OperationBancaireEnAttente {
        val uid = userId()
        val origine = ligne.source.trim().uppercase()
        val idExterne = ligne.idExterne.trim()
        require(origine in setOf("CSV", "PLAID", "NOTIFICATION")) { "Source bancaire invalide." }
        require(idExterne.isNotBlank() && idExterne.length <= 256) { "Identifiant externe requis." }
        require(ligne.compteId.isNotBlank()) { "Compte requis." }
        require(ligne.collectionCompte in setOf("comptes_cheques", "comptes_credits")) {
            "Seuls les comptes bancaires et cartes sont admissibles."
        }
        require(ligne.montantCentimes != 0L) { "Montant nul refusé." }
        require(ligne.libelleBrut.isNotBlank() && ligne.libelleBrut.length <= 512) {
            "Libellé bancaire invalide."
        }
        LocalDate.parse(ligne.dateOperation) // valide ISO yyyy-MM-dd
        require(ligne.statutBancaire in setOf("EN_ATTENTE", "FINALISEE")) {
            "Statut bancaire invalide."
        }
        val resultat = database.withTransaction {
            val appartient = if (ligne.collectionCompte == "comptes_cheques")
                database.compteChequeDao().getCompteById(ligne.compteId)?.utilisateurId == uid
            else database.compteCreditDao().getCompteById(ligne.compteId)?.utilisateurId == uid
            require(appartient) { "Compte introuvable pour cet utilisateur." }

            val ancienne = dao.parCleExterne(uid, ligne.compteId, origine, idExterne)
            if (ancienne == null) {
                val identifiant = idStable(uid, ligne.compteId, origine, idExterne)
                require(dao.parId(identifiant, uid) == null) { "Collision d'identifiant externe." }
                val operation = OperationBancaireEnAttente(
                    id = identifiant, utilisateurId = uid, compteId = ligne.compteId,
                    collectionCompte = ligne.collectionCompte, source = origine,
                    idExterne = idExterne, dateOperation = ligne.dateOperation,
                    montantCentimes = ligne.montantCentimes,
                    libelleBrut = ligne.libelleBrut, statutBancaire = ligne.statutBancaire
                )
                dao.ajouter(operation)
                mettreEnFile(operation, "CREATE")
                operation
            } else if (ancienne.statutBancaire == ligne.statutBancaire &&
                ancienne.dateOperation == ligne.dateOperation &&
                ancienne.montantCentimes == ligne.montantCentimes &&
                ancienne.libelleBrut == ligne.libelleBrut) {
                ancienne // réimport exact = aucune écriture
            } else {
                // Aucun débit n'a encore été produit dans le registre comptable par cet import.
                // Si l'utilisateur a ignoré l'opération, ne pas la réintroduire silencieusement.
                val conflit = ancienne.montantCentimes != ligne.montantCentimes
                dao.actualiserImport(
                    id = ancienne.id, utilisateurId = uid,
                    statutBancaire = ligne.statutBancaire,
                    dateOperation = ligne.dateOperation,
                    montantCentimes = ligne.montantCentimes,
                    libelleBrut = ligne.libelleBrut,
                    verificationRequise = ancienne.verificationRequise || conflit,
                    montantFinalCentimes = if (conflit) ligne.montantCentimes else ancienne.montantFinalCentimes
                )
                val actualisee = dao.parId(ancienne.id, uid) ?: error("Import mis à jour introuvable.")
                mettreEnFile(actualisee, "UPDATE")
                actualisee
            }
        }
        SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
        return resultat
    }


    suspend fun importerCsv(
        compteId: String,
        collectionCompte: String,
        contenuCsv: String
    ): List<OperationBancaireEnAttente> {
        val lignes = ImportCsvBancaire.analyser(contenuCsv)
        return database.withTransaction {
            lignes.map { ligne ->
                importer(LigneBanque(
                    compteId = compteId,
                    collectionCompte = collectionCompte,
                    source = "CSV",
                    idExterne = ligne.idExterne,
                    dateOperation = ligne.date,
                    montantCentimes = ligne.montantCentimes,
                    libelleBrut = ligne.libelleBrut,
                    statutBancaire = ligne.statut
                ))
            }
        }
    }

    /** Ignorer est réversible et synchronisé ; l'original bancaire reste conservé. */
    suspend fun ignorer(id: String): Boolean = changerDecision(id, "IGNOREE")

    suspend fun restaurer(id: String): Boolean = changerDecision(id, "A_CONFIRMER")

    private suspend fun changerDecision(id: String, decision: String): Boolean {
        val uid = userId()
        val modifie = database.withTransaction {
            val change = dao.changerDecision(id, uid, decision) > 0
            if (change) {
                val operation = dao.parId(id, uid) ?: error("Opération absente.")
                mettreEnFile(operation, "UPDATE")
            }
            change
        }
        if (modifie) SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
        return modifie
    }

    /** Affectation du tiers existant à UN import, sans changer un alias général. */
    suspend fun selectionnerTiers(id: String, tiersId: String): Boolean {
        val uid = userId()
        require(database.tiersDao().getTiersById(tiersId)?.utilisateurId == uid) {
            "Le tiers doit exister et appartenir à l'utilisateur."
        }
        val modifie = database.withTransaction {
            if (dao.selectionnerTiers(id, uid, tiersId) == 0) return@withTransaction false
            val operation = dao.parId(id, uid) ?: error("Opération introuvable.")
            mettreEnFile(operation, "UPDATE")
            true
        }
        if (modifie) SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
        return modifie
    }

    suspend fun lire(id: String): OperationBancaireEnAttente? = dao.parId(id, userId())

    /** JSON utilisé par la collection PocketBase. Centimes conservés en texte entier. */
    private suspend fun mettreEnFile(op: OperationBancaireEnAttente, action: String) {
        val payload = mapOf(
            "id" to op.id,
            "utilisateur_id" to op.utilisateurId,
            "compte_id" to op.compteId,
            "collection_compte" to op.collectionCompte,
            "source" to op.source,
            "id_externe" to op.idExterne,
            "date_operation" to op.dateOperation,
            "montant_centimes" to op.montantCentimes.toString(),
            "libelle_brut" to op.libelleBrut,
            "statut_bancaire" to op.statutBancaire,
            "decision" to op.decision,
            "tiers_id" to (op.tiersId ?: ""),
            "verification_requise" to op.verificationRequise,
            "montant_final_centimes" to (op.montantFinalCentimes?.toString() ?: "")
        )
        database.syncJobDao().insertSyncJob(SyncJob(
            id = IdGenerator.generateId(),
            type = "OPERATION_BANCAIRE",
            action = action,
            dataJson = Gson().toJson(payload),
            recordId = op.id,
            utilisateurId = op.utilisateurId,
            createdAt = System.currentTimeMillis(),
            status = "PENDING"
        ))
    }

    private fun idStable(uid: String, compteId: String, source: String, idExterne: String): String {
        val cle = listOf(uid, compteId, source, idExterne).joinToString("\u001f")
        val bytes = MessageDigest.getInstance("SHA-256").digest(cle.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }.take(15)
    }
}
