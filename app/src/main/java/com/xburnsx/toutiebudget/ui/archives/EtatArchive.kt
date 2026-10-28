package com.xburnsx.toutiebudget.ui.archives

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlin.math.abs

/**
 * ARC5/ARC20 : distinguer une archive soldée d'une archive qui contient encore de l'argent
 * ou un passif. Archivé ne veut pas dire remboursé : on ne cache jamais ce qui reste dû.
 */
object EtatArchive {
    fun libelleType(compte: Compte): String = when (compte) {
        is CompteCheque -> "Compte chèque"
        is CompteCredit -> "Carte de crédit"
        is CompteDette -> "Dette"
        is CompteInvestissement -> "Placement"
    }

    fun statut(compte: Compte): String = when {
        compte is CompteCredit && compte.soldeUtilise < -0.005 ->
            "archivée, non soldée (${MoneyFormatter.formatAmount(abs(compte.soldeUtilise))} dû)"
        compte is CompteDette && compte.soldeDette < -0.005 ->
            "archivée, encore due (${MoneyFormatter.formatAmount(abs(compte.soldeDette))})"
        compte !is CompteCredit && compte !is CompteDette && abs(compte.solde) > 0.005 ->
            "archivé avec ${MoneyFormatter.formatAmount(compte.solde)}"
        else -> "archivé, soldé"
    }
}
