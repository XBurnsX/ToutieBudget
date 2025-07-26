// chemin/simule: /utils/OrganisationEnveloppesUtils.kt
package com.xburnsx.toutiebudget.utils

import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

/**
 * Utilitaires pour organiser les catégories et enveloppes de manière cohérente
 * dans toute l'application.
 */
object OrganisationEnveloppesUtils {

    /**
     * Organise les enveloppes par catégorie selon l'ordre défini.
     * - Les catégories sont triées par leur champ 'ordre'
     * - Les enveloppes sont triées par leur champ 'ordre' dans chaque catégorie
     * - "Sans catégorie" apparaît en dernier si elle existe
     */
    fun organiserEnveloppesParCategorie(
        categories: List<Categorie>,
        enveloppes: List<Enveloppe>
    ): Map<String, List<Enveloppe>> {
        val categoriesMap = categories.associateBy { it.id }
        val groupes = mutableMapOf<String, MutableList<Enveloppe>>()

        // Trier les catégories par ordre
        val categoriesTriees = categories.sortedBy { it.ordre }

        // Initialiser toutes les catégories (même vides) dans le bon ordre
        categoriesTriees.forEach { categorie ->
            groupes[categorie.nom] = mutableListOf()
        }

        // Ajouter "Sans catégorie" pour les enveloppes orphelines
        groupes["Sans catégorie"] = mutableListOf()

        // Répartir les enveloppes dans leurs catégories
        enveloppes.forEach { enveloppe ->
            val categorie = categoriesMap[enveloppe.categorieId]
            val nomCategorie = categorie?.nom ?: "Sans catégorie"

            if (!groupes.containsKey(nomCategorie)) {
                groupes[nomCategorie] = mutableListOf()
            }

            groupes[nomCategorie]?.add(enveloppe)
        }

        // Trier les enveloppes dans chaque catégorie par leur ordre
        groupes.forEach { (_, enveloppesCategorie) ->
            enveloppesCategorie.sortBy { it.ordre }
        }

        // Supprimer "Sans catégorie" si elle est vide
        if (groupes["Sans catégorie"]?.isEmpty() == true) {
            groupes.remove("Sans catégorie")
        }

        // Retourner les groupes dans l'ordre des catégories triées
        return categoriesTriees.associate { categorie ->
            categorie.nom to (groupes[categorie.nom] ?: emptyList())
        }.let { ordonnees ->
            // Ajouter "Sans catégorie" à la fin si elle existe
            if (groupes.containsKey("Sans catégorie")) {
                ordonnees + ("Sans catégorie" to groupes["Sans catégorie"]!!)
            } else {
                ordonnees
            }
        }
    }

    /**
     * Retourne les catégories triées par ordre
     */
    fun trierCategories(categories: List<Categorie>): List<Categorie> {
        return categories.sortedBy { it.ordre }
    }

    /**
     * Retourne les enveloppes triées par ordre dans une catégorie donnée
     */
    fun trierEnveloppesParCategorie(
        enveloppes: List<Enveloppe>,
        categorieId: String
    ): List<Enveloppe> {
        return enveloppes
            .filter { it.categorieId == categorieId }
            .sortedBy { it.ordre }
    }

    /**
     * Retourne toutes les enveloppes organisées par catégorie sous forme de liste plate
     * en respectant l'ordre des catégories et l'ordre des enveloppes
     */
    fun obtenirEnveloppesOrdonnees(
        categories: List<Categorie>,
        enveloppes: List<Enveloppe>
    ): List<Enveloppe> {
        val enveloppesGroupees = organiserEnveloppesParCategorie(categories, enveloppes)
        val result = mutableListOf<Enveloppe>()

        enveloppesGroupees.forEach { (_, enveloppesCategorie) ->
            result.addAll(enveloppesCategorie)
        }

        return result
    }
}
