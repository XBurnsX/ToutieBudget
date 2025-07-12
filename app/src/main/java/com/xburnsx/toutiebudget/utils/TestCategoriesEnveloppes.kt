// chemin/simule: /utils/TestCategoriesEnveloppes.kt
// Dépendances: Repositories, Modèles, Coroutines

package com.xburnsx.toutiebudget.utils

import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.coroutineScope

/**
 * Utilitaire de test pour valider le bon fonctionnement du système catégories/enveloppes.
 * À utiliser pour vérifier que tout est correctement configuré.
 */
object TestCategoriesEnveloppes {

    /**
     * Exécute une série de tests pour valider le système.
     * @param categorieRepo Le repository des catégories
     * @param enveloppeRepo Le repository des enveloppes
     * @return Un rapport détaillé des tests
     */
    suspend fun executerTestsComplets(
        categorieRepo: CategorieRepository,
        enveloppeRepo: EnveloppeRepository
    ): String = coroutineScope {
        val rapport = StringBuilder()
        rapport.appendLine("🧪 === TESTS DU SYSTÈME CATÉGORIES/ENVELOPPES ===")
        rapport.appendLine()

        try {
            // Test 1: Vérification de la connexion PocketBase
            rapport.appendLine("📡 Test 1: Connexion PocketBase")
            if (PocketBaseClient.estConnecte()) {
                val utilisateur = PocketBaseClient.obtenirUtilisateurConnecte()
                rapport.appendLine("   ✅ Connecté en tant que: ${utilisateur?.email}")
            } else {
                rapport.appendLine("   ❌ Non connecté à PocketBase")
                return@coroutineScope rapport.toString()
            }
            rapport.appendLine()

            // Test 2: Récupération des catégories existantes
            rapport.appendLine("📁 Test 2: Récupération des catégories")
            val categoriesResult = categorieRepo.recupererToutesLesCategories()
            val categories = categoriesResult.getOrElse { 
                rapport.appendLine("   ❌ Erreur: ${it.message}")
                emptyList()
            }
            rapport.appendLine("   ✅ ${categories.size} catégorie(s) trouvée(s)")
            categories.forEach { categorie ->
                rapport.appendLine("      • ${categorie.nom} (ID: ${categorie.id})")
            }
            rapport.appendLine()

            // Test 3: Récupération des enveloppes existantes
            rapport.appendLine("📄 Test 3: Récupération des enveloppes")
            val enveloppesResult = enveloppeRepo.recupererToutesLesEnveloppes()
            val enveloppes = enveloppesResult.getOrElse { 
                rapport.appendLine("   ❌ Erreur: ${it.message}")
                emptyList()
            }
            rapport.appendLine("   ✅ ${enveloppes.size} enveloppe(s) trouvée(s)")
            enveloppes.forEach { enveloppe ->
                val categorie = categories.find { it.id == enveloppe.categorieId }
                val nomCategorie = categorie?.nom ?: "❌ CATÉGORIE INTROUVABLE"
                rapport.appendLine("      • ${enveloppe.nom} → $nomCategorie")
                rapport.appendLine("        ID: ${enveloppe.id}, CategorieID: ${enveloppe.categorieId}")
            }
            rapport.appendLine()

            // Test 4: Vérification des liens
            rapport.appendLine("🔗 Test 4: Vérification des liens catégories/enveloppes")
            val enveloppesOrphelines = enveloppes.filter { enveloppe ->
                !categories.any { it.id == enveloppe.categorieId }
            }
            
            if (enveloppesOrphelines.isEmpty()) {
                rapport.appendLine("   ✅ Toutes les enveloppes sont correctement liées")
            } else {
                rapport.appendLine("   ❌ ${enveloppesOrphelines.size} enveloppe(s) orpheline(s) détectée(s):")
                enveloppesOrphelines.forEach { enveloppe ->
                    rapport.appendLine("      • ${enveloppe.nom} (CategorieID: ${enveloppe.categorieId})")
                }
            }
            rapport.appendLine()

            // Test 5: Test de création (si autorisé)
            rapport.appendLine("🚀 Test 5: Test de création d'une catégorie de test")
            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
            if (utilisateurId != null) {
                val categorieTest = Categorie(
                    id = "temp_test",
                    utilisateurId = utilisateurId,
                    nom = "Test Catégorie ${System.currentTimeMillis()}"
                )
                
                val creationResult = categorieRepo.creerCategorie(categorieTest)
                creationResult.onSuccess { categorieCreee ->
                    rapport.appendLine("   ✅ Catégorie de test créée: ${categorieCreee.nom}")
                    rapport.appendLine("      ID généré: ${categorieCreee.id}")
                    
                    // Test de création d'enveloppe liée
                    val enveloppeTest = Enveloppe(
                        id = "temp_test",
                        utilisateurId = utilisateurId,
                        nom = "Test Enveloppe ${System.currentTimeMillis()}",
                        categorieId = categorieCreee.id, // LIEN IMPORTANT
                        estArchive = false,
                        ordre = 0
                    )
                    
                    val enveloppeResult = enveloppeRepo.creerEnveloppe(enveloppeTest)
                    enveloppeResult.onSuccess { enveloppeCreee ->
                        rapport.appendLine("   ✅ Enveloppe de test créée: ${enveloppeCreee.nom}")
                        rapport.appendLine("      ID généré: ${enveloppeCreee.id}")
                        rapport.appendLine("      Liée à la catégorie: ${enveloppeCreee.categorieId}")
                        
                        // Nettoyage: supprimer les éléments de test
                        enveloppeRepo.supprimerEnveloppe(enveloppeCreee.id)
                        categorieRepo.supprimerCategorie(categorieCreee.id)
                        rapport.appendLine("   🧹 Éléments de test supprimés")
                    }.onFailure {
                        rapport.appendLine("   ❌ Erreur création enveloppe: ${it.message}")
                    }
                }.onFailure {
                    rapport.appendLine("   ❌ Erreur création catégorie: ${it.message}")
                }
            } else {
                rapport.appendLine("   ❌ ID utilisateur non disponible")
            }
            rapport.appendLine()

            // Test 6: Validation de la structure PocketBase
            rapport.appendLine("🔧 Test 6: Validation de la structure PocketBase")
            rapport.appendLine("   📋 Collections requises:")
            rapport.appendLine("      • categorie (champs: utilisateur_id, nom)")
            rapport.appendLine("      • enveloppes (champs: utilisateur_id, nom, categorieId, est_archive, ordre)")
            rapport.appendLine("      • allocations_mensuelles (champs: utilisateur_id, enveloppe_id, mois, solde, etc.)")
            rapport.appendLine()
            
            rapport.appendLine("   🔗 Relations importantes:")
            rapport.appendLine("      • enveloppes.categorieId → categorie.id")
            rapport.appendLine("      • allocations_mensuelles.enveloppe_id → enveloppes.id")
            rapport.appendLine()

        } catch (e: Exception) {
            rapport.appendLine("❌ ERREUR CRITIQUE: ${e.message}")
            rapport.appendLine("   Stack trace: ${e.stackTrace.joinToString("\n   ")}")
        }

        rapport.appendLine("=== FIN DES TESTS ===")
        return@coroutineScope rapport.toString()
    }

    /**
     * Test rapide pour vérifier que les liens fonctionnent.
     */
    suspend fun testerLiensRapide(
        categorieRepo: CategorieRepository,
        enveloppeRepo: EnveloppeRepository
    ): Boolean {
        return try {
            val categories = categorieRepo.recupererToutesLesCategories().getOrNull() ?: return false
            val enveloppes = enveloppeRepo.recupererToutesLesEnveloppes().getOrNull() ?: return false
            
            // Vérifier qu'il n'y a pas d'enveloppes orphelines
            val enveloppesOrphelines = enveloppes.count { enveloppe ->
                !categories.any { it.id == enveloppe.categorieId }
            }
            
            enveloppesOrphelines == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Génère un rapport d'état pour l'interface utilisateur.
     */
    suspend fun genererRapportEtat(
        categorieRepo: CategorieRepository,
        enveloppeRepo: EnveloppeRepository
    ): String {
        return try {
            val categories = categorieRepo.recupererToutesLesCategories().getOrNull() ?: emptyList()
            val enveloppes = enveloppeRepo.recupererToutesLesEnveloppes().getOrNull() ?: emptyList()
            
            val enveloppesParCategorie = enveloppes.groupBy { enveloppe ->
                categories.find { it.id == enveloppe.categorieId }?.nom ?: "Sans catégorie"
            }
            
            val rapport = StringBuilder()
            rapport.appendLine("📊 État du système:")
            rapport.appendLine("• ${categories.size} catégorie(s)")
            rapport.appendLine("• ${enveloppes.size} enveloppe(s)")
            
            enveloppesParCategorie.forEach { (categorie, enveloppesListe) ->
                rapport.appendLine("• $categorie: ${enveloppesListe.size} enveloppe(s)")
            }
            
            rapport.toString()
        } catch (e: Exception) {
            "❌ Erreur lors de la génération du rapport: ${e.message}"
        }
    }
}