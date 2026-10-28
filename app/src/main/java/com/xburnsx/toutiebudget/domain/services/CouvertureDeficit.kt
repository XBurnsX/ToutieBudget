package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation

/**
 * GA5 : quelle part des virements d'un mois a réellement couvert un déficit d'enveloppe.
 * Chaque modification d'allocation note le solde de l'enveloppe avant/après dans « details ».
 * Couvert = remontée du solde entre le déficit d'avant et 0 (le surplus au-dessus de 0 ne compte pas).
 */
object CouvertureDeficit {
    private const val PREFIXE = "Allocation mensuelle modifiée"

    fun details(soldeAvant: Double, soldeApres: Double): String =
        "$PREFIXE;avant=${centimes(soldeAvant)};apres=${centimes(soldeApres)}"

    /** Centimes couverts pour une entrée, ou 0 si elle n'a pas réduit un déficit (ou date d'avant cette version). */
    fun centimesCouverts(details: String?): Long {
        if (details == null || !details.startsWith(PREFIXE)) return 0L
        val valeurs = details.split(';').drop(1).mapNotNull { morceau ->
            val (cle, valeur) = morceau.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            valeur.toLongOrNull()?.let { cle to it }
        }.toMap()
        val avant = valeurs["avant"] ?: return 0L
        val apres = valeurs["apres"] ?: return 0L
        if (avant >= 0L || apres <= avant) return 0L
        return minOf(apres, 0L) - avant
    }

    fun montantCouvert(historique: List<HistoriqueAllocation>): Double =
        historique.sumOf { centimesCouverts(it.details) } / 100.0

    private fun centimes(montant: Double): Long =
        if (montant.isFinite()) Math.round(montant * 100.0) else 0L
}
