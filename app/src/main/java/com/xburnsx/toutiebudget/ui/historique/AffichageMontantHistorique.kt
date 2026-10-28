package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.utils.MoneyFormatter

/**
 * HC10 : données anciennes / synchronisation corrompue.
 * Ne jamais remplacer NaN ou infini par 0,00 $ ni planter tout l'historique.
 * Pure fonction de présentation; aucune écriture financière.
 */
object AffichageMontantHistorique {
    fun formater(montant: Double): String =
        if (!montant.isFinite()) "Montant invalide — vérifier la transaction"
        else runCatching { MoneyFormatter.formatAmount(montant) }
            .getOrDefault("Montant impossible à afficher")
}
