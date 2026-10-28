package com.xburnsx.toutiebudget.ui.historique

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * HC15 / HE16 : clé de jour indépendante des libellés formatés, ordre stable,
 * et aucun doublon d'ID rendu lorsque le dépôt retourne deux fois la même
 * transaction pendant une synchronisation.
 *
 * Lecture seule, sans toucher aux montants ni aux écritures.
 */
object RegroupementHistorique {
    fun grouper(
        transactions: List<TransactionUi>,
        fuseau: ZoneId = ZoneId.systemDefault()
    ): Map<String, List<TransactionUi>> {
        val formateur = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
        // Une révision plus récente d'un même ID est prioritaire; en son absence,
        // conserver la première version rencontrée pour un résultat déterministe.
        val uniques = transactions
            .sortedWith(compareByDescending<TransactionUi> { it.revisionTransaction?.time ?: Long.MIN_VALUE })
            .distinctBy { it.id }
            .sortedWith(compareByDescending<TransactionUi> { it.date.time }.thenBy { it.id })

        val jours = uniques.groupBy { tx ->
            Instant.ofEpochMilli(tx.date.time).atZone(fuseau).toLocalDate()
        }
        val groupes = linkedMapOf<String, List<TransactionUi>>()
        jours.entries.sortedByDescending { it.key }.forEach { (jour, lignes) ->
            groupes[jour.format(formateur)] = lignes
        }
        return groupes
    }

    fun nombreDoublons(transactions: List<TransactionUi>): Int =
        transactions.size - transactions.distinctBy { it.id }.size
}
