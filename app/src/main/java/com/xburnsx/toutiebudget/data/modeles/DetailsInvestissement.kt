package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.xburnsx.toutiebudget.utils.IdGenerator

/**
 * Suivi d'un compte de placement (section 12 du TXT), stocké en JSON dans `details_investissement_json`.
 * Le mode détaillé est FACULTATIF (IA1) : un compte simple fonctionne sans aucune de ces données.
 * Rien ici n'entre dans les enveloppes ni dans le « Prêt à placer ».
 */
data class DetailsInvestissement(
    val mode: ModeSuiviPlacement = ModeSuiviPlacement.SIMPLE,
    val typeCompte: TypeComptePlacement = TypeComptePlacement.NON_PRECISE, // IA7 : métadonnée seulement
    val institution: String? = null,
    // IA2 : valeur connue au départ du suivi (null = inconnue, jamais inventée)
    val valeurDepart: Double? = null,
    val dateDepart: String? = null,
    // IF7 / IA4 : historique daté des valeurs (marché ou correction)
    val valorisations: List<Valorisation> = emptyList(),
    // IF6 : liquidités (mode détaillé)
    val encaisse: Double? = null,
    val retirableConnu: Double? = null,
    // IA5 / IA6
    val positions: List<PositionTitre> = emptyList(),
    val mouvements: List<MouvementPlacement> = emptyList()
) {
    fun versJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun depuis(brut: String?): DetailsInvestissement {
            if (brut.isNullOrBlank()) return DetailsInvestissement()
            return try {
                var element: JsonElement = JsonParser.parseString(brut)
                if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                    element = JsonParser.parseString(element.asString)
                }
                if (element.isJsonObject) gson.fromJson(element, DetailsInvestissement::class.java) ?: DetailsInvestissement()
                else DetailsInvestissement()
            } catch (_: Exception) {
                DetailsInvestissement()
            }
        }
    }
}

enum class ModeSuiviPlacement(val libelle: String) { SIMPLE("Suivi simple"), DETAILLE("Suivi détaillé") }

enum class TypeComptePlacement(val libelle: String) {
    NON_PRECISE("Non précisé"),
    CELI("CELI"),
    REER("REER"),
    CELIAPP("CELIAPP"),
    NON_ENREGISTRE("Non enregistré"),
    EPARGNE("Épargne / espèces"),
    AUTRE("Autre")
}

enum class TypeValorisation(val libelle: String) {
    MARCHE("Nouvelle valeur de marché"),
    CORRECTION("Correction d'erreur de solde")
}

/** IF7 : ajustement daté et typé ; ce n'est jamais un dépôt, un revenu ni du Prêt à placer. */
data class Valorisation(
    val id: String = IdGenerator.generateId(),
    val date: String, // yyyy-MM-dd
    val valeur: Double,
    val ancienneValeur: Double? = null,
    val type: TypeValorisation = TypeValorisation.MARCHE,
    val note: String? = null
)

enum class CategorieActif(val libelle: String) {
    ACTION("Actions"), FNB("FNB"), FONDS("Fonds"), OBLIGATION("Obligations"), CRYPTO("Cryptos"), AUTRE("Autres")
}

/** IA5 : quantité fractionnaire permise ; le coût inclut les frais d'achat. */
data class PositionTitre(
    val id: String = IdGenerator.generateId(),
    val symbole: String,
    val categorie: CategorieActif = CategorieActif.AUTRE,
    val quantite: Double,
    val coutTotal: Double,
    val cours: Double? = null,
    val dateCours: String? = null,
    val devise: String = "CAD"
) {
    val valeur: Double? get() = cours?.let { it * quantite }
}

enum class TypeMouvementPlacement(val libelle: String) {
    ACHAT("Achat"),
    VENTE("Vente"),
    DIVIDENDE("Dividende"),
    DISTRIBUTION("Distribution"),
    INTERETS("Intérêts"),
    FRAIS("Frais"),
    REINVESTISSEMENT("Réinvestissement")
}

/**
 * IA6 : mouvement à l'intérieur du placement. Un achat ou un réinvestissement n'est pas une
 * dépense budgétaire : l'argent reste dans le portefeuille.
 */
data class MouvementPlacement(
    val id: String = IdGenerator.generateId(),
    val date: String,
    val type: TypeMouvementPlacement,
    val montant: Double,
    val symbole: String? = null,
    val quantite: Double? = null,
    /** Vente : gain ou perte réalisé (prix de vente - coût), si connu. */
    val gainRealise: Double? = null,
    val note: String? = null
)
