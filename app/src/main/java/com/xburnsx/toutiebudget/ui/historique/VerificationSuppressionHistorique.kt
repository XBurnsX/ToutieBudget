package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.Transaction
import kotlin.math.roundToLong

/**
 * HC1 : vérifier au moment de la confirmation que la transaction Room
 * est toujours celle décrite dans la fenêtre. Ne modifie aucun solde.
 *
 * Important : cette vérification UI précède l'UseCase; la protection
 * transactionnelle contre une écriture concurrente appartient à celui-ci.
 */
object VerificationSuppressionHistorique {
    fun estEncoreLaMeme(affichee: TransactionUi, actuelle: Transaction): Boolean {
        if (affichee.id != actuelle.id || affichee.type != actuelle.type) return false
        if (affichee.date.time != actuelle.date.time) return false
        if (affichee.estFractionnee != actuelle.estFractionnee) return false
        if (affichee.sousItems != actuelle.sousItems) return false
        if (affichee.note != actuelle.note) return false
        if (affichee.tiersBrut != actuelle.tiersUtiliser) return false
        if (affichee.allocationMensuelleId != actuelle.allocationMensuelleId) return false
        if (affichee.compteId != actuelle.compteId) return false
        if (affichee.collectionCompte != actuelle.collectionCompte) return false

        // Dans l'historique d'enveloppe, affichee.montant est la portion.
        val montantBancaireAffiche = affichee.montantTotalTransaction ?: affichee.montant
        if (!montantBancaireAffiche.isFinite() || !actuelle.montant.isFinite()) return false
        if ((montantBancaireAffiche * 100).roundToLong() !=
            (actuelle.montant * 100).roundToLong()) return false

        // Les anciens enregistrements ne possèdent pas toujours updated.
        if (affichee.revisionTransaction != null &&
            affichee.revisionTransaction.time != actuelle.updated?.time) return false
        return true
    }
}
