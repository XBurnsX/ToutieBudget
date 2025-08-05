// chemin/simule: /data/modeles/Compte.kt
package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.annotations.SerializedName

sealed interface Compte {
    val id: String
    @get:SerializedName("utilisateur_id")
    val utilisateurId: String
    val nom: String
    val solde: Double
    val couleur: String
    @get:SerializedName("archive")  // ← CORRECTION: "archive" au lieu de "est_archive"
    val estArchive: Boolean
    val ordre: Int
    val collection: String
}

data class CompteCheque(
    override val id: String = "",
    @SerializedName("utilisateur_id")
    override var utilisateurId: String = "",
    override val nom: String,
    override val solde: Double,
    @SerializedName("pret_a_placer")
    val pretAPlacerRaw: Double? = null,
    override val couleur: String,
    @SerializedName("archive")  // ← CORRECTION: "archive" au lieu de "est_archive"
    override val estArchive: Boolean,
    override val ordre: Int,
    override val collection: String = "comptes_cheques"
) : Compte {
    // Propriété calculée pour gérer la valeur par défaut
    val pretAPlacer: Double get() = pretAPlacerRaw ?: 0.0
}

// Structure pour représenter un frais mensuel individuel
data class FraisMensuel(
    val nom: String, // Nom du frais (ex: "Assurance", "AccordD")
    val montant: Double, // Montant du frais
    val description: String? = null // Description optionnelle
)

data class CompteCredit(
    override val id: String = "",
    @SerializedName("utilisateur_id")
    override var utilisateurId: String = "",
    override val nom: String,
    @SerializedName("solde_utilise")
    val soldeUtilise: Double, // Dette actuelle utilisée sur la carte
    override val couleur: String,
    @SerializedName("archive")
    override val estArchive: Boolean,
    override val ordre: Int,
    @SerializedName("limite_credit")
    val limiteCredit: Double,
    @SerializedName("taux_interet")
    val tauxInteret: Double? = null,
    @SerializedName("frais_mensuels_json")
    val fraisMensuelsJson: com.google.gson.JsonElement? = null, // Peut être String ou Array
    override val collection: String = "comptes_credits"
) : Compte {
    // Pour compatibilité avec l'interface Compte, on map soldeUtilise vers solde
    override val solde: Double get() = soldeUtilise
    
    // Propriété calculée pour parser les frais mensuels depuis JSON
    val fraisMensuels: List<FraisMensuel> get() {
        return try {
            if (fraisMensuelsJson == null) {
                emptyList()
            } else {
                val gson = com.google.gson.Gson()
                
                when {
                    fraisMensuelsJson.isJsonArray -> {
                        // PocketBase retourne un tableau JSON directement
                        val jsonArray = fraisMensuelsJson.asJsonArray
                        jsonArray.map { element ->
                            gson.fromJson(element, FraisMensuel::class.java)
                        }
                    }
                    fraisMensuelsJson.isJsonPrimitive && fraisMensuelsJson.asJsonPrimitive.isString -> {
                        // C'est une chaîne JSON, la parser normalement
                        gson.fromJson(fraisMensuelsJson.asString, Array<FraisMensuel>::class.java).toList()
                    }
                    else -> emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Propriété calculée pour obtenir le total des frais mensuels
    val totalFraisMensuels: Double get() = fraisMensuels.sumOf { it.montant }
}

data class CompteDette(
    override val id: String = "",
    @SerializedName("utilisateur_id")
    override var utilisateurId: String = "",
    override val nom: String,
    override val solde: Double,
    @SerializedName("archive")  // ← CORRECTION: "archive" au lieu de "est_archive"
    override val estArchive: Boolean,
    override val ordre: Int,
    @SerializedName("montant_initial")
    val montantInitial: Double,
    val interet: Double? = null,
    override val collection: String = "comptes_dettes"
) : Compte {
    // La couleur est gérée dans l'UI, toujours rouge pour les dettes.
    override val couleur: String = "#FF0000"
}

data class CompteInvestissement(
    override val id: String = "",
    @SerializedName("utilisateur_id")
    override var utilisateurId: String = "",
    override val nom: String,
    override val solde: Double,
    override val couleur: String,
    @SerializedName("archive")  // ← CORRECTION: "archive" au lieu de "est_archive"
    override val estArchive: Boolean,
    override val ordre: Int,
    override val collection: String = "comptes_investissement"
) : Compte
