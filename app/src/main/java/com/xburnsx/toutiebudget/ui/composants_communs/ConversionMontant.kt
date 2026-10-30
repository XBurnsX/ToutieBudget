package com.xburnsx.toutiebudget.ui.composants_communs

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pont entre les formulaires qui gardent un montant en texte (« 12.34 », « -400,00 ») et
 * ChampUniversel / ClavierNumerique, qui travaillent en centimes.
 */
object ConversionMontant {
    /** Texte saisi → centimes; 0 si vide ou illisible. */
    fun versCentimes(texte: String): Long =
        runCatching {
            BigDecimal(texte.trim().replace(Regex("[\\s\\p{Z}$]+"), "").replace(',', '.'))
                .setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrDefault(0L)

    /** Centimes → texte lu par les formulaires (« 12.34 »). [videSiZero] : champ facultatif laissé vide. */
    fun versTexte(centimes: Long, videSiZero: Boolean = false): String =
        if (videSiZero && centimes == 0L) ""
        else BigDecimal.valueOf(centimes).movePointLeft(2).setScale(2).toPlainString()
}
