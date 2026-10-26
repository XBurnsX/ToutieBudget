package com.xburnsx.toutiebudget.domain.services

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser

/**
 * AT9 : schéma UNIQUE des fractions d'une transaction (champ sousItems).
 * Écriture : clés « enveloppeId », « montant » (dollars), « allocation_mensuelle_id », « description », « note ».
 * Lecture : accepte aussi l'ancienne clé « enveloppe_id » (rétrocompatibilité).
 */
object SousItemsFractions {

    data class Fraction(
        val enveloppeId: String,
        val montant: Double,
        val allocationMensuelleId: String? = null,
        val description: String? = null,
        val note: String? = null
    )

    private val gson = Gson()

    fun ecrire(fractions: List<Fraction>): String = gson.toJson(
        fractions.mapIndexed { index, f ->
            mapOf(
                "id" to "fraction_${index + 1}",
                "description" to f.description,
                "enveloppeId" to f.enveloppeId,
                "montant" to f.montant,
                "allocation_mensuelle_id" to f.allocationMensuelleId,
                "note" to f.note
            )
        }
    )

    /** Liste vide si le texte n'est pas un tableau de fractions (ex. lien d'opération d'un paiement). */
    fun lire(json: String?): List<Fraction> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val element = JsonParser.parseString(json)
            if (!element.isJsonArray) return emptyList()
            (element as JsonArray).mapNotNull { e ->
                if (!e.isJsonObject) return@mapNotNull null
                val o = e.asJsonObject
                val enveloppeId = (o.get("enveloppeId") ?: o.get("enveloppe_id"))
                    ?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                Fraction(
                    enveloppeId = enveloppeId,
                    montant = o.get("montant")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
                    allocationMensuelleId = o.get("allocation_mensuelle_id")?.takeIf { !it.isJsonNull }?.asString,
                    description = o.get("description")?.takeIf { !it.isJsonNull }?.asString,
                    note = o.get("note")?.takeIf { !it.isJsonNull }?.asString
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
