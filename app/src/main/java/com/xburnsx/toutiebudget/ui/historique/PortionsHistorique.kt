package com.xburnsx.toutiebudget.ui.historique

import com.google.gson.JsonParser
import java.math.BigDecimal

/**
 * HE2 : lecture STRICTE des portions enregistrées (en dollars dans le JSON).
 * Ne jamais convertir une valeur absente, mal formée ou avec une fraction
 * de centime en 0 $ : l'historique serait financièrement trompeur.
 */
object PortionsHistorique {
    data class Portion(val enveloppeId: String, val montantCentimes: Long)

    fun lire(json: String?): List<Portion> {
        require(!json.isNullOrBlank()) { "Aucune fraction conservée." }
        val racine = JsonParser.parseString(json)
        require(racine.isJsonArray) { "Le fractionnement n'est pas un tableau." }
        val tableau = racine.asJsonArray
        require(tableau.size() in 1..1000) { "Liste des fractions vide ou disproportionnée." }
        return tableau.map { element ->
            require(element.isJsonObject) { "Fraction invalide." }
            val objet = element.asJsonObject
            val enveloppeId = (objet.get("enveloppeId") ?: objet.get("enveloppe_id"))
                ?.takeIf { !it.isJsonNull }?.asString
            require(!enveloppeId.isNullOrBlank()) { "Fraction sans enveloppe." }
            val montant = objet.get("montant")?.takeIf { !it.isJsonNull }
                ?.asString ?: error("Montant de fraction absent.")
            val centimes = BigDecimal(montant).movePointRight(2).longValueExact()
            require(centimes > 0L) { "Fraction à zéro ou négative." }
            Portion(enveloppeId, centimes)
        }
    }

    /**
     * HC9 : la somme des fractions doit réconcilier le montant bancaire
     * au centime. La vérification n'altère ni l'historique ni les allocations.
     */
    fun verifierTotal(portions: List<Portion>, montantTransaction: Double): Boolean {
        if (portions.isEmpty() || !montantTransaction.isFinite()) return false
        return runCatching {
            val total = portions.fold(0L) { cumul, portion ->
                Math.addExact(cumul, portion.montantCentimes)
            }
            val bancaire = BigDecimal.valueOf(montantTransaction)
                .movePointRight(2).longValueExact()
            total == bancaire
        }.getOrDefault(false)
    }

    fun totalPourEnveloppe(portions: List<Portion>, enveloppeId: String): Double {
        val correspondantes = portions.filter { it.enveloppeId == enveloppeId }
        require(correspondantes.isNotEmpty()) { "Aucune portion de cette enveloppe." }
        val centimes = correspondantes.fold(0L) { somme, portion ->
            Math.addExact(somme, portion.montantCentimes)
        }
        return centimes / 100.0
    }
}
