package com.xburnsx.toutiebudget.data.banque

import androidx.room.withTransaction
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
        return database.withTransaction {
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
                dao.parId(ancienne.id, uid) ?: error("Import mis à jour introuvable.")
            }
        }
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

    /** Ignorer est réversible ; on ne détruit jamais l'original bancaire. */
    suspend fun ignorer(id: String): Boolean =
        dao.changerDecision(id, userId(), "IGNOREE") > 0

    suspend fun restaurer(id: String): Boolean =
        dao.changerDecision(id, userId(), "A_CONFIRMER") > 0

    suspend fun lire(id: String): OperationBancaireEnAttente? = dao.parId(id, userId())

    private fun idStable(uid: String, compteId: String, source: String, idExterne: String): String {
        val cle = listOf(uid, compteId, source, idExterne).joinToString("\u001f")
        val bytes = MessageDigest.getInstance("SHA-256").digest(cle.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }.take(15)
    }
}
