package com.xburnsx.toutiebudget.data.services

import android.content.Context
import java.time.LocalDate

/**
 * SA6 : valeurs nettes réellement constatées, une par jour. Rien n'est reconstitué après coup :
 * la courbe du patrimoine ne montre que des points mesurés (aucune tendance fabriquée).
 */
interface HistoriquePatrimoine {
    suspend fun enregistrer(jour: LocalDate, valeurNette: Double)
    suspend fun lire(): List<Pair<LocalDate, Double>>
}

/** Stockage local de l'appareil (un point par jour, le dernier calcul du jour gagne). */
class HistoriquePatrimoineLocal(context: Context) : HistoriquePatrimoine {
    private val prefs = context.applicationContext.getSharedPreferences("historique_patrimoine", Context.MODE_PRIVATE)

    override suspend fun enregistrer(jour: LocalDate, valeurNette: Double) {
        if (!valeurNette.isFinite()) return
        prefs.edit().putString(jour.toString(), valeurNette.toString()).apply()
    }

    override suspend fun lire(): List<Pair<LocalDate, Double>> =
        prefs.all.mapNotNull { (cle, valeur) ->
            val jour = runCatching { LocalDate.parse(cle) }.getOrNull() ?: return@mapNotNull null
            val montant = (valeur as? String)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            jour to montant
        }.sortedBy { it.first }
}
