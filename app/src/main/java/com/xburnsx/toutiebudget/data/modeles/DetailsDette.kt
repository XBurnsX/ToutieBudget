package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.xburnsx.toutiebudget.utils.IdGenerator

/**
 * Configuration d'une dette (section 11 du TXT), stockée en JSON dans `details_dette_json`.
 * Rien ici ne remplace le solde réel (`solde_dette`) : ce sont le contrat, le lien
 * d'enveloppe et l'historique des corrections confirmées.
 */
data class DetailsDette(
    val mode: ModeDette = ModeDette.SIMPLE, // DA1
    // DF5 : vraies dates du contrat (yyyy-MM-dd)
    val dateDebut: String? = null,
    val dateFin: String? = null,
    // Date du 1er versement réel (yyyy-MM-dd) : la banque le prend parfois avant une période complète
    // (ex. 7 jours après le contrat pour un prêt aux deux semaines). Null = une période après le début.
    val datePremierPaiement: String? = null,
    val jourVersement: Int? = null,
    // Null uniquement pour les anciennes dettes créées avant l'ajout du choix;
    // elles restent mensuelles pour compatibilité.
    val frequencePaiement: FrequencePaiementDette? = null,
    // DF11 : lien permanent vers l'enveloppe de la dette
    val enveloppeId: String? = null,
    // DA7 : taux variable, changements datés (le passé n'est jamais recalculé)
    val tauxVariable: Boolean = false,
    val changementsTaux: List<ChangementTaux> = emptyList(),
    // DA5 / DF7 : soldes officiels confirmés et intérêts/frais réellement ajoutés
    val ajustements: List<AjustementDette> = emptyList()
) {
    fun versJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun depuis(brut: String?): DetailsDette {
            if (brut.isNullOrBlank()) return DetailsDette()
            return try {
                var element: JsonElement = JsonParser.parseString(brut)
                if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                    element = JsonParser.parseString(element.asString)
                }
                if (element.isJsonObject) gson.fromJson(element, DetailsDette::class.java) ?: DetailsDette()
                else DetailsDette()
            } catch (_: Exception) {
                DetailsDette()
            }
        }
    }
}

enum class FrequencePaiementDette(val libelle: String, val paiementsParAn: Int) {
    HEBDOMADAIRE("Chaque semaine", 52),
    AUX_DEUX_SEMAINES("Aux deux semaines", 26),
    MENSUELLE("Chaque mois", 12)
}

enum class ModeDette(val libelle: String) {
    /** Montant réellement dû, sans taux ni amortissement inventés. */
    SIMPLE("Dette simple"),
    /** Principal, taux, dates et durée : échéancier d'amortissement possible. */
    FINANCE("Prêt financé")
}

data class ChangementTaux(
    val id: String = IdGenerator.generateId(),
    val date: String, // yyyy-MM-dd : taux en vigueur à partir de cette date
    val taux: Double
)

enum class TypeAjustementDette(val libelle: String) {
    SOLDE_OFFICIEL("Solde officiel confirmé"),
    INTERETS("Intérêts ajoutés"),
    FRAIS("Frais ajoutés")
}

/** Correction confirmée : jamais un paiement bancaire ni un revenu. */
data class AjustementDette(
    val id: String = IdGenerator.generateId(),
    val date: String,
    val type: TypeAjustementDette,
    val soldeAvant: Double,
    val soldeApres: Double,
    val note: String? = null
)
