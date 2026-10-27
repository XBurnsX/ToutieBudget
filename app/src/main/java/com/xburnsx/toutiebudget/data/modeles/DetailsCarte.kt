package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.xburnsx.toutiebudget.utils.IdGenerator

/**
 * Configuration détaillée d'une carte de crédit (section 10 du TXT), stockée en JSON
 * dans `details_carte_json`. Tout est optionnel : la carte fonctionne avec seulement
 * solde signé, limite et taux (CC3) ; les règles viennent du profil d'émetteur (CC7).
 */
data class DetailsCarte(
    val emetteur: EmetteurCarte = EmetteurCarte.AUTRE,
    // CC10 : taux annuels distincts (en %). Achats = taux_interet du compte si absent.
    val tauxAvances: Double? = null,
    val tauxTransferts: Double? = null,
    val tauxPromo: Double? = null,
    val debutPromo: String? = null, // yyyy-MM-dd; début contractuel s'il est connu
    val finPromo: String? = null, // yyyy-MM-dd
    val tauxRetard: Double? = null,
    // CC4 : cycle du relevé
    val jourCloture: Int? = null, // jour du mois où le relevé ferme
    val joursAvantEcheance: Int? = null, // délai entre la clôture et l'échéance (21, 25…)
    // CC7 : règles du minimum ; null = valeur du profil d'émetteur
    val pourcentageMinimum: Double? = null,
    val plancherMinimum: Double? = null,
    // CC6 : relevés officiels saisis par l'utilisateur
    val releves: List<ReleveCarte> = emptyList()
) {
    val pourcentageMinimumEffectif: Double get() = pourcentageMinimum ?: emetteur.pourcentageMinimum
    val plancherMinimumEffectif: Double? get() = plancherMinimum

    /** Relevés du plus récent au plus ancien. */
    val relevesTries: List<ReleveCarte> get() = releves.sortedByDescending { it.dateEmission }

    fun versJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        /** Lit le champ tel que reçu (objet JSON, texte JSON ou absent). */
        fun depuis(brut: String?): DetailsCarte {
            if (brut.isNullOrBlank()) return DetailsCarte()
            return try {
                var element: JsonElement = JsonParser.parseString(brut)
                if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                    element = JsonParser.parseString(element.asString)
                }
                if (element.isJsonObject) gson.fromJson(element, DetailsCarte::class.java) ?: DetailsCarte()
                else DetailsCarte()
            } catch (_: Exception) {
                DetailsCarte()
            }
        }
    }
}

/**
 * Profils d'émetteur : valeurs de départ configurables, jamais imposées aux autres cartes.
 * Desjardins (Québec) : minimum de référence 5 % du solde du relevé (R-DESJARDINS-01).
 */
enum class EmetteurCarte(val libelle: String, val pourcentageMinimum: Double) {
    DESJARDINS("Desjardins", 5.0),
    AUTRE("Autre émetteur", 5.0)
}

/**
 * Relevé OFFICIEL (CC6). Les montants viennent du relevé de la banque et ne sont
 * jamais recalculés avec les achats faits après la clôture.
 * Les montants dus sont positifs (ex. 3 672,42 $ à payer).
 */
data class ReleveCarte(
    val id: String = IdGenerator.generateId(),
    val dateEmission: String, // yyyy-MM-dd (clôture)
    val dateEcheance: String? = null, // yyyy-MM-dd
    val soldeOfficiel: Double,
    val minimumOfficiel: Double? = null,
    val interetsFactures: Double = 0.0,
    val fraisFactures: Double = 0.0,
    val paiementsRecus: Double = 0.0,
    /** Vrai quand les intérêts facturés ont été ajoutés au solde de la carte dans l'app (une seule fois). */
    val interetsComptabilises: Boolean = false
)
