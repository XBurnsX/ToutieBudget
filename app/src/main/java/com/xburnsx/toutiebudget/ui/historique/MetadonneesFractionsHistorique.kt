package com.xburnsx.toutiebudget.ui.historique

import com.google.gson.JsonParser

/**
 * HC9/HC19 : les champs optionnels d'une fraction (note et description)
 * ne doivent jamais rendre son montant invalide.
 *
 * Les montants restent exclusivement validés par PortionsHistorique.
 * Un champ de note mal typé est ignoré, sans inventer de texte ni perdre
 * les autres notes de la transaction.
 */
object MetadonneesFractionsHistorique {
    data class Informations(val description: String = "", val note: String = "")

    fun lire(json: String?, nombreFractions: Int): List<Informations> {
        require(nombreFractions in 0..1000) { "Nombre de fractions invalide." }
        val tableau = runCatching {
            JsonParser.parseString(json).asJsonArray
        }.getOrNull()
        return List(nombreFractions) { index ->
            val objet = runCatching { tableau?.get(index)?.asJsonObject }.getOrNull()
            fun champ(nom: String): String =
                runCatching {
                    objet?.get(nom)
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString
                }.getOrNull().orEmpty()
            Informations(
                description = champ("description"),
                note = champ("note")
            )
        }
    }
}
