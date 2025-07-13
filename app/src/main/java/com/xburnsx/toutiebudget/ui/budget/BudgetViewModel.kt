// chemin/simule: /ui/budget/BudgetViewModel.kt
// Dépendances: Remplacez temporairement votre BudgetViewModel par cette version pour diagnostic

package com.xburnsx.toutiebudget.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.domain.usecases.VerifierEtExecuterRolloverUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BudgetViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase
) : ViewModel() {

    // --- Cache en mémoire pour éviter les écrans de chargement ---
    private var cacheComptes: List<Compte> = emptyList()
    private var cacheEnveloppes: List<Enveloppe> = emptyList()
    private var cacheAllocations: List<AllocationMensuelle> = emptyList()
    private var cacheCategories: List<Categorie> = emptyList()
    
    // Garder en mémoire le mois sélectionné pour les rafraîchissements automatiques
    private var moisSelectionne: Date = Date()

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageChargement = "Vérification du budget...") }
            verifierEtExecuterRolloverUseCase().onSuccess {
                chargerDonneesBudget(Date())
            }.onFailure { e ->
                _uiState.update { it.copy(erreur = "Erreur de rollover: ${e.message}") }
                chargerDonneesBudget(Date())
            }
        }
        // Abonnement à l'event bus pour rafraîchir le budget (si présent)
        viewModelScope.launch {
            try {
                BudgetEvents.refreshBudget.collectLatest {
                    println("[DEBUG] Rafraîchissement automatique avec mois sélectionné: $moisSelectionne")
                    chargerDonneesBudget(moisSelectionne)
                }
            } catch (e: Exception) {
                // BudgetEvents peut ne pas exister, on ignore cette erreur
                println("[DEBUG] BudgetEvents non disponible, rafraîchissement manuel seulement")
            }
        }
    }

    /**
     * Rafraîchit les données du budget pour le mois donné.
     * Version avec diagnostic intégré pour identifier le problème des enveloppes à 0$.
     */
    fun chargerDonneesBudget(moisCible: Date = Date()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageChargement = "Chargement des données...") }
            
            try {
                println("[DEBUG] ========================================")
                println("[DEBUG] DÉBUT DIAGNOSTIC COMPLET")
                println("[DEBUG] Mois cible: ${formatDatePourDebug(moisCible)}")
                println("[DEBUG] ========================================")
                
                // 1. Charger les comptes
                _uiState.update { it.copy(messageChargement = "Chargement des comptes...") }
                val resultComptes = compteRepository.recupererTousLesComptes()
                val comptes = resultComptes.getOrElse { 
                    println("[ERROR] Erreur récupération comptes: ${it.message}")
                    emptyList() 
                }
                println("[DEBUG] ✅ Comptes récupérés: ${comptes.size}")
                cacheComptes = comptes

                // 2. Charger les enveloppes
                _uiState.update { it.copy(messageChargement = "Chargement des enveloppes...") }
                val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                val enveloppes = resultEnveloppes.getOrElse { 
                    println("[ERROR] Erreur récupération enveloppes: ${it.message}")
                    emptyList() 
                }
                println("[DEBUG] ✅ Enveloppes récupérées: ${enveloppes.size}")
                enveloppes.forEachIndexed { index, env ->
                    println("[DEBUG]   $index. Enveloppe: id='${env.id}' nom='${env.nom}' categorieId='${env.categorieId}'")
                }
                cacheEnveloppes = enveloppes

                // 3. Charger les catégories
                _uiState.update { it.copy(messageChargement = "Chargement des catégories...") }
                val resultCategories = categorieRepository.recupererToutesLesCategories()
                val categories = resultCategories.getOrElse { 
                    println("[ERROR] Erreur récupération catégories: ${it.message}")
                    emptyList() 
                }
                println("[DEBUG] ✅ Catégories récupérées: ${categories.size}")
                categories.forEachIndexed { index, cat ->
                    println("[DEBUG]   $index. Catégorie: id='${cat.id}' nom='${cat.nom}'")
                }
                cacheCategories = categories

                // 4. Charger les allocations pour le mois en cours
                _uiState.update { it.copy(messageChargement = "Chargement des allocations mensuelles...") }
                val premierJourDuMois = obtenirPremierJourDuMois(moisCible)
                println("[DEBUG] 📅 Premier jour du mois calculé: ${formatDatePourDebug(premierJourDuMois)}")
                
                val resultAllocations = enveloppeRepository.recupererAllocationsPourMois(premierJourDuMois)
                val allocations = resultAllocations.getOrElse { 
                    println("[ERROR] ❌ Erreur récupération allocations: ${it.message}")
                    emptyList() 
                }
                println("[DEBUG] ✅ Allocations récupérées: ${allocations.size}")
                
                if (allocations.isEmpty()) {
                    println("[DEBUG] ⚠️  AUCUNE allocation trouvée pour le mois ${formatDatePourDebug(premierJourDuMois)}")
                    println("[DEBUG] ⚠️  C'est probablement pourquoi les enveloppes affichent 0$")
                } else {
                    println("[DEBUG] 📋 Détail des allocations:")
                    allocations.forEachIndexed { index, allocation ->
                        println("[DEBUG]   $index. Allocation:")
                        println("[DEBUG]      - ID: '${allocation.id}'")
                        println("[DEBUG]      - EnveloppeID: '${allocation.enveloppeId}'")
                        println("[DEBUG]      - Mois: ${formatDatePourDebug(allocation.mois)}")
                        println("[DEBUG]      - Solde: ${allocation.solde}")
                        println("[DEBUG]      - Dépense: ${allocation.depense}")
                        println("[DEBUG]      - Alloué: ${allocation.alloue}")
                        println("[DEBUG]      - CompteSourceId: '${allocation.compteSourceId ?: "null"}'")
                    }
                }
                cacheAllocations = allocations

                // 5. Créer les bandeaux "Prêt à placer"
                val bandeauxPretAPlacer = creerBandeauxPretAPlacer(comptes)
                println("[DEBUG] ✅ Bandeaux prêt à placer créés: ${bandeauxPretAPlacer.size}")

                // 6. Créer les enveloppes UI avec les allocations
                println("[DEBUG] 🔗 DÉBUT CRÉATION ENVELOPPES UI")
                val enveloppesUi = creerEnveloppesUi(enveloppes, allocations, comptes)
                println("[DEBUG] ✅ EnveloppesUi créées: ${enveloppesUi.size}")
                
                // Debug des enveloppes UI créées
                println("[DEBUG] 📋 Résultat final des enveloppes UI:")
                enveloppesUi.forEachIndexed { index, env ->
                    println("[DEBUG]   $index. EnveloppeUi:")
                    println("[DEBUG]      - Nom: '${env.nom}'")
                    println("[DEBUG]      - Solde: ${env.solde}")
                    println("[DEBUG]      - Dépense: ${env.depense}")
                    println("[DEBUG]      - Objectif: ${env.objectif}")
                    println("[DEBUG]      - Statut: ${env.statutObjectif}")
                }

                // 7. Grouper par catégories
                val groupesEnveloppes = enveloppesUi.groupBy { enveloppe ->
                    val enveloppeComplete = cacheEnveloppes.find { env -> env.id == enveloppe.id }
                    val categorie = categories.find { it.id == enveloppeComplete?.categorieId }
                    categorie?.nom ?: "Autre"
                }
                val categoriesEnveloppesUi = cacheCategories.map { cat ->
                    CategorieEnveloppesUi(cat.nom, groupesEnveloppes[cat.nom] ?: emptyList())
                }.sortedBy { it.nomCategorie }

                // 8. Mettre à jour l'état final
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bandeauxPretAPlacer = bandeauxPretAPlacer,
                        categoriesEnveloppes = categoriesEnveloppesUi,
                        messageChargement = null,
                        erreur = null
                    )
                }
                
                println("[DEBUG] ========================================")
                println("[DEBUG] ✅ CHARGEMENT TERMINÉ AVEC SUCCÈS")
                println("[DEBUG] ========================================")
                moisSelectionne = moisCible

            } catch (e: Exception) {
                println("[ERROR] ========================================")
                println("[ERROR] ❌ ERREUR LORS DU CHARGEMENT")
                println("[ERROR] Message: ${e.message}")
                println("[ERROR] ========================================")
                e.printStackTrace()
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        erreur = "Erreur lors du chargement des données: ${e.message}",
                        messageChargement = null
                    ) 
                }
            }
        }
    }

    /**
     * Crée les enveloppes UI en combinant les données des enveloppes et allocations.
     * Version avec diagnostic détaillé pour identifier le problème de correspondance.
     */
    private fun creerEnveloppesUi(
        enveloppes: List<Enveloppe>,
        allocations: List<AllocationMensuelle>,
        comptes: List<Compte>
    ): List<EnveloppeUi> {
        println("[DEBUG] ==========================================")
        println("[DEBUG] 🔗 DÉBUT CRÉATION MAPPING ENVELOPPES UI")
        println("[DEBUG] ==========================================")
        println("[DEBUG] Input: ${enveloppes.size} enveloppes, ${allocations.size} allocations")
        
        // Créer une map des allocations par ID d'enveloppe pour un accès rapide
        val mapAllocations = allocations.associateBy { it.enveloppeId }
        println("[DEBUG] 📋 Map allocations créée avec ${mapAllocations.size} entrées:")
        mapAllocations.forEach { (enveloppeId, allocation) ->
            println("[DEBUG]   '${enveloppeId}' -> allocation.id='${allocation.id}' solde=${allocation.solde}")
        }
        
        // Créer une map des comptes par ID pour récupérer les couleurs
        val mapComptes = comptes.associateBy { it.id }
        println("[DEBUG] 📋 Map comptes créée avec ${mapComptes.size} entrées")

        val resultat = enveloppes.mapIndexed { index, enveloppe ->
            println("[DEBUG] ----------------------------------------")
            println("[DEBUG] 🏷️  TRAITEMENT ENVELOPPE $index")
            println("[DEBUG] ----------------------------------------")
            println("[DEBUG] Enveloppe:")
            println("[DEBUG]   - ID: '${enveloppe.id}' (${enveloppe.id.length} caractères)")
            println("[DEBUG]   - Nom: '${enveloppe.nom}'")
            println("[DEBUG]   - CategorieID: '${enveloppe.categorieId}'")
            
            // Afficher les caractères de l'ID pour debug
            println("[DEBUG] Caractères de l'ID enveloppe: ${enveloppe.id.toCharArray().joinToString(" ") { "'$it'(${it.code})" }}")
            
            // Récupérer l'allocation mensuelle correspondante
            val allocation = mapAllocations[enveloppe.id]
            if (allocation != null) {
                println("[DEBUG] ✅ ALLOCATION TROUVÉE:")
                println("[DEBUG]   - Allocation ID: '${allocation.id}'")
                println("[DEBUG]   - Solde: ${allocation.solde}")
                println("[DEBUG]   - Dépense: ${allocation.depense}")
                println("[DEBUG]   - Alloué: ${allocation.alloue}")
                println("[DEBUG]   - Mois: ${formatDatePourDebug(allocation.mois)}")
            } else {
                println("[DEBUG] ❌ AUCUNE ALLOCATION TROUVÉE")
                println("[DEBUG] IDs disponibles dans mapAllocations:")
                mapAllocations.keys.forEachIndexed { idx, key ->
                    println("[DEBUG]   $idx. '$key' (${key.length} caractères)")
                    println("[DEBUG]      Caractères: ${key.toCharArray().joinToString(" ") { "'$it'(${it.code})" }}")
                    println("[DEBUG]      Égal à enveloppe.id? ${enveloppe.id == key}")
                    
                    // Comparaison caractère par caractère si les longueurs sont différentes
                    if (enveloppe.id.length != key.length) {
                        println("[DEBUG]      ⚠️  Longueurs différentes: enveloppe=${enveloppe.id.length}, allocation=${key.length}")
                    } else {
                        // Comparaison caractère par caractère
                        val differences = mutableListOf<Int>()
                        enveloppe.id.forEachIndexed { charIndex, char ->
                            if (charIndex < key.length && char != key[charIndex]) {
                                differences.add(charIndex)
                            }
                        }
                        if (differences.isNotEmpty()) {
                            println("[DEBUG]      ⚠️  Différences aux positions: $differences")
                            differences.forEach { pos ->
                                println("[DEBUG]         Position $pos: env='${enveloppe.id[pos]}'(${enveloppe.id[pos].code}) vs alloc='${key[pos]}'(${key[pos].code})")
                            }
                        }
                    }
                }
            }
            
            // Récupérer le compte source pour la couleur
            val compteSource = allocation?.compteSourceId?.let { mapComptes[it] }
            if (allocation?.compteSourceId != null) {
                println("[DEBUG] 🏦 Compte source recherché: id='${allocation.compteSourceId}', trouvé: ${compteSource != null}")
            }
            
            // Utiliser les valeurs de l'allocation ou 0.0 par défaut
            val solde = allocation?.solde ?: 0.0
            val depense = allocation?.depense ?: 0.0
            val objectif = enveloppe.objectifMontant
            
            println("[DEBUG] 💰 Valeurs calculées:")
            println("[DEBUG]   - Solde: $solde (depuis allocation: ${allocation?.solde})")
            println("[DEBUG]   - Dépense: $depense (depuis allocation: ${allocation?.depense})")
            println("[DEBUG]   - Objectif: $objectif (depuis enveloppe)")
            
            // Calculer le statut de l'objectif
            val statut = when {
                objectif > 0 && solde >= objectif -> StatutObjectif.VERT
                solde > 0 -> StatutObjectif.JAUNE
                else -> StatutObjectif.GRIS
            }
            
            val enveloppeUi = EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = solde,
                depense = depense,
                objectif = objectif,
                couleurProvenance = compteSource?.couleur,
                statutObjectif = statut
            )
            
            println("[DEBUG] ✅ EnveloppeUi créée:")
            println("[DEBUG]   - Nom: '${enveloppeUi.nom}'")
            println("[DEBUG]   - Solde final: ${enveloppeUi.solde}")
            println("[DEBUG]   - Dépense finale: ${enveloppeUi.depense}")
            println("[DEBUG]   - Statut: ${enveloppeUi.statutObjectif}")
            
            enveloppeUi
        }
        
        println("[DEBUG] ==========================================")
        println("[DEBUG] 🏁 FIN CRÉATION MAPPING ENVELOPPES UI")
        println("[DEBUG] Résultat: ${resultat.size} enveloppes UI créées")
        println("[DEBUG] ==========================================")
        
        return resultat
    }

    /**
     * Crée les bandeaux "Prêt à placer" à partir des comptes ayant un solde positif.
     */
    private fun creerBandeauxPretAPlacer(comptes: List<Compte>): List<PretAPlacerUi> {
        return comptes
            .filter { it.solde > 0 }
            .map { compte ->
                PretAPlacerUi(
                    compteId = compte.id,
                    nomCompte = compte.nom,
                    montant = compte.solde,
                    couleurCompte = compte.couleur
                )
            }
    }

    /**
     * Obtient le premier jour du mois pour une date donnée.
     * Important pour la requête des allocations mensuelles.
     */
    private fun obtenirPremierJourDuMois(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Formate une date pour le debug.
     */
    private fun formatDatePourDebug(date: Date): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return format.format(date)
    }

    /**
     * Méthode publique pour rafraîchir les données depuis d'autres ViewModels.
     * Utilisée quand une transaction est créée pour mettre à jour l'affichage.
     */
    fun rafraichirDonnees() {
        println("[DEBUG] 🔄 rafraichirDonnees appelée")
        chargerDonneesBudget(Date())
    }

    /**
     * Efface l'erreur actuelle.
     */
    fun effacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }
}