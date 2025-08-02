// chemin/simule: /data/modeles/Enveloppe.kt
package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.annotations.SerializedName
import java.util.Date
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif // Importer l'énumération centralisée

// Supprimé l'énumération locale en doublon, utilisez l'énumération centralisée dans TypeObjectif.kt
// enum class TypeObjectif {
//     Aucun,
//     Mensuel,
//     Bihebdomadaire,
//     Echeance,
//     Annuel
// }

data class Enveloppe(
    val id: String,
    @SerializedName("utilisateur_id")
    val utilisateurId: String,
    val nom: String,
    @SerializedName("categorie_Id")  // Correspondre exactement au nom dans PocketBase
    val categorieId: String, // <-- référence à la catégorie
    @SerializedName("est_archive")
    val estArchive: Boolean,
    val ordre: Int,
    @SerializedName("frequence_objectif")  // Nouveau nom dans PocketBase
    val typeObjectif: TypeObjectif = TypeObjectif.Aucun,
    @SerializedName("montant_objectif")  // Nouveau nom dans PocketBase
    val objectifMontant: Double = 0.0,
    @SerializedName("date_objectif")  // La date que l'objectif doit atteindre (ex: 23 pour le 23 du mois)
    val dateObjectif: String? = null,
    @SerializedName("date_debut_objectif")  // La date où l'objectif commence
    val dateDebutObjectif: Date? = null,
    @SerializedName("date_fin_objectif")  // 🆕 NOUVEAU : La date de fin pour les échéances
    val dateFinObjectif: Date? = null,
    @SerializedName("objectif_jour")  // Garder ce champ si nécessaire
    val objectifJour: Int? = null,
    @SerializedName("reset_apres_echeance")  // Nouveau champ pour les objectifs d'échéance
    val resetApresEcheance: Boolean = false
)
