package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction

/**
 * HC16 : sens visuel d'un mouvement dans un historique de compte.
 * Une carte de crédit affiche un paiement REÇU comme un mouvement favorable
 * (réduction de la dette), alors que ce paiement reste une sortie du compte
 * chèque payeur. Cette classe ne change aucune écriture ou solde.
 */
object SensMontantHistorique {
    enum class Sens { SORTIE, ENTREE, INCONNU }

    fun determiner(type: TypeTransaction, collectionCompte: String?): Sens =
        when (type) {
            TypeTransaction.Revenu,
            TypeTransaction.Emprunt,
            TypeTransaction.RemboursementRecu,
            TypeTransaction.TransfertEntrant -> Sens.ENTREE

            // Le paiement crédité sur la carte réduit le montant à rembourser.
            // Pour les autres collections on conserve la présentation actuelle.
            TypeTransaction.Paiement ->
                if (collectionCompte == "comptes_credits") Sens.ENTREE else Sens.SORTIE

            TypeTransaction.Depense,
            TypeTransaction.Pret,
            TypeTransaction.RemboursementDonne,
            TypeTransaction.PaiementEffectue,
            TypeTransaction.TransfertSortant -> Sens.SORTIE
        }
}
