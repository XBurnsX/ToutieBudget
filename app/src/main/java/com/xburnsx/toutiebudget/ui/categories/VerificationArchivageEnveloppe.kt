package com.xburnsx.toutiebudget.ui.categories

import java.math.BigDecimal

/**
 * CE2/CE5 : aucun solde historique non nul, non fini ou non réconciliable
 * au centime ne permet l'archivage. N'altère jamais les données comptables.
 */
object VerificationArchivageEnveloppe {
    fun centimesExact(montant: Double): Long {
        require(montant.isFinite()) { "Montant d'allocation invalide." }
        return runCatching {
            BigDecimal.valueOf(montant).movePointRight(2).longValueExact()
        }.getOrElse {
            throw IllegalArgumentException("Montant d'allocation non réconciliable au centime.", it)
        }
    }

    fun verifierSoldes(soldes: Iterable<Double>): Long {
        var total = 0L
        for (solde in soldes) {
            val cents = centimesExact(solde)
            require(cents == 0L) {
                "Une allocation contient encore de l'argent à déplacer avant le retrait."
            }
            total = Math.addExact(total, cents)
        }
        return total
    }
}
