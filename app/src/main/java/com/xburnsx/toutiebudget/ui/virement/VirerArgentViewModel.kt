// chemin/simule: /ui/virement/VirerArgentViewModel.kt
// Dépendances: ViewModel, Repositories, Services, Modèles de données

package com.xburnsx.toutiebudget.ui.virement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.domain.services.ArgentService
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ViewModel pour l'écran de virement d'argent.
 * Gère les virements entre comptes et enveloppes.
 */
class VirerArgentViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val argentService: ArgentService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VirerArgentUiState())
    val uiState: StateFlow<VirerArgentUiState> = _uiState.asStateFlow()

    // Données mises en cache
    private var allComptes: List<Compte> = emptyList()
    private var allEnveloppes: List<Enveloppe> = emptyList()
    private var allAllocations: List<AllocationMensuelle> = emptyList()
    private var allCategories: List<Categorie> = emptyList()

    init {
        chargerSourcesEtDestinations()
    }

    // ===== CHARGEMENT DES DONNÉES =====

    /**
     * Charge toutes les sources et destinations possibles pour les virements.
     */
    private fun chargerSourcesEtDestinations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Charger toutes les données
                allComptes = compteRepository.recupererTousLesComptes()
                    .getOrThrow()
                    .filter { !it.estArchive }
                
                allEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                    .getOrThrow()
                    .filter { !it.estArchive }
                
                allAllocations = enveloppeRepository.recupererAllocationsPourMois(Date())
                    .getOrThrow()
                
                allCategories = categorieRepository.recupererToutesLesCategories()
                    .getOrThrow()

                // Créer les items de comptes (seulement les comptes chèque pour l'instant)
                val itemsComptes = allComptes
                    .filterIsInstance<CompteCheque>()
                    .map { ItemVirement.CompteItem(it) }

                // Créer les enveloppes UI avec le même système que AjoutTransactionViewModel
                val enveloppesUi = construireEnveloppesUi()

                // Créer un map pour accéder rapidement aux catégories par ID
                val categoriesMap = allCategories.associateBy { it.id }
                
                // Grouper les sources (comptes + enveloppes avec argent)
                val sourcesEnveloppes = enveloppesUi
                    .filter { it.solde > 0 }  // Seulement les enveloppes avec de l'argent
                    .map { ItemVirement.EnveloppeItem(it) }
                    .groupBy { enveloppeItem ->
                        val categorie = categoriesMap[allEnveloppes.find { it.id == enveloppeItem.enveloppe.id }?.categorieId]
                        categorie?.nom ?: "Autre"
                    }
                
                val sources = mapOf("Prêt à placer" to itemsComptes) + sourcesEnveloppes
                
                // Grouper les destinations (comptes + toutes les enveloppes)
                val destinationsEnveloppes = enveloppesUi
                    .map { ItemVirement.EnveloppeItem(it) }
                    .groupBy { enveloppeItem ->
                        val categorie = categoriesMap[allEnveloppes.find { it.id == enveloppeItem.enveloppe.id }?.categorieId]
                        categorie?.nom ?: "Autre"
                    }
                
                val destinations = mapOf("Prêt à placer" to itemsComptes) + destinationsEnveloppes

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sourcesDisponibles = sources,
                        destinationsDisponibles = destinations
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        erreur = "Erreur de chargement: ${e.message}"
                    ) 
                }
            }
        }
    }

    /**
     * Construit la liste des enveloppes UI avec leurs allocations.
     * Même logique que dans AjoutTransactionViewModel.
     */
    private fun construireEnveloppesUi(): List<EnveloppeUi> {
        return allEnveloppes.filter { !it.estArchive }.map { enveloppe ->
            val categorie = allCategories.find { it.id == enveloppe.categorieId }
            val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
            
            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = allocation?.solde ?: 0.0,
                depense = allocation?.depense ?: 0.0,
                objectif = enveloppe.objectifMontant,
                couleurProvenance = "#6366F1",  // Couleur par défaut
                statutObjectif = StatutObjectif.GRIS  // Simplifié pour le virement
            )
        }.sortedBy { enveloppe ->
            val categorie = allCategories.find { cat -> 
                allEnveloppes.find { it.id == enveloppe.id }?.categorieId == cat.id 
            }
            categorie?.nom ?: "Sans catégorie"
        }
    }

    // ===== GESTION DES SÉLECTEURS =====

    /**
     * Ouvre le sélecteur de source ou destination.
     */
    fun ouvrirSelecteur(type: SelecteurOuvert) { 
        _uiState.update { it.copy(selecteurOuvert = type) } 
    }
    
    /**
     * Ferme le sélecteur ouvert.
     */
    fun fermerSelecteur() { 
        _uiState.update { it.copy(selecteurOuvert = SelecteurOuvert.AUCUN) } 
    }
    
    /**
     * Sélectionne un item (source ou destination) selon le sélecteur ouvert.
     */
    fun onItemSelected(item: ItemVirement) {
        when (_uiState.value.selecteurOuvert) {
            SelecteurOuvert.SOURCE -> {
                _uiState.update { 
                    it.copy(
                        sourceSelectionnee = item,
                        erreur = null  // Effacer les erreurs précédentes
                    ) 
                }
            }
            SelecteurOuvert.DESTINATION -> {
                // Vérifier qu'on ne vire pas vers la même source
                val source = _uiState.value.sourceSelectionnee
                if (source != null && memeItem(source, item)) {
                    _uiState.update { 
                        it.copy(erreur = "La source et la destination ne peuvent pas être identiques.") 
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            destinationSelectionnee = item,
                            erreur = null
                        ) 
                    }
                }
            }
            SelecteurOuvert.AUCUN -> {}
        }
        fermerSelecteur()
    }

    /**
     * Sélectionne une enveloppe pour la source ou la destination.
     */
    fun onEnveloppeSelected(enveloppeUi: EnveloppeUi, isSource: Boolean) {
        val item = ItemVirement.EnveloppeItem(enveloppeUi)
        
        if (isSource) {
            _uiState.update { 
                it.copy(
                    sourceSelectionnee = item,
                    erreur = null
                ) 
            }
        } else {
            // Vérifier qu'on ne vire pas vers la même source
            val source = _uiState.value.sourceSelectionnee
            if (source != null && memeItem(source, item)) {
                _uiState.update { 
                    it.copy(erreur = "La source et la destination ne peuvent pas être identiques.") 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        destinationSelectionnee = item,
                        erreur = null
                    ) 
                }
            }
        }
    }

    // ===== GESTION DU MONTANT =====

    /**
     * Met à jour le montant saisi par l'utilisateur.
     * Le montant est reçu en format centimes depuis ChampArgent.
     */
    fun onMontantChange(nouveauMontantEnCentimes: String) {
        // Limiter à 8 chiffres maximum pour éviter les débordements
        if (nouveauMontantEnCentimes.length <= 8) {
            _uiState.update { 
                it.copy(
                    montant = nouveauMontantEnCentimes,
                    erreur = null  // Effacer les erreurs lors de la saisie
                ) 
            }
        }
    }

    // ===== EXÉCUTION DU VIREMENT =====

    /**
     * Exécute le virement d'argent entre source et destination.
     */
    fun onVirementExecute() {
        val state = _uiState.value
        val source = state.sourceSelectionnee
        val destination = state.destinationSelectionnee
        val montantEnCentimes = state.montant.toLongOrNull() ?: 0L
        val montantEnDollars = montantEnCentimes / 100.0

        // Validations
        if (source == null) {
            _uiState.update { it.copy(erreur = "Veuillez sélectionner une source.") }
            return
        }
        
        if (destination == null) {
            _uiState.update { it.copy(erreur = "Veuillez sélectionner une destination.") }
            return
        }
        
        if (montantEnCentimes <= 0) {
            _uiState.update { it.copy(erreur = "Veuillez entrer un montant valide.") }
            return
        }

        // Vérifier que la source a assez d'argent
        val soldeSource = obtenirSoldeItem(source)
        if (soldeSource < montantEnDollars) {
            _uiState.update { 
                it.copy(erreur = "Solde insuffisant dans la source sélectionnée.") 
            }
            return
        }

        viewModelScope.launch {
            try {
                // Effectuer le virement selon les types source/destination
                when {
                    // Compte vers Compte
                    source is ItemVirement.CompteItem && destination is ItemVirement.CompteItem -> {
                        argentService.effectuerVirementCompteVersCompte(
                            compteSource = source.compte,
                            compteDestination = destination.compte,
                            montant = montantEnDollars
                        )
                    }
                    // Compte vers Enveloppe
                    source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                        argentService.allouerArgentEnveloppe(
                            enveloppeId = destination.enveloppe.id,
                            compteSourceId = source.compte.id,
                            collectionCompteSource = source.compte.collection,
                            montant = montantEnDollars,
                            mois = Date()
                        )
                    }
                    // Enveloppe vers Compte
                    source is ItemVirement.EnveloppeItem && destination is ItemVirement.CompteItem -> {
                        // Logique pour retirer de l'enveloppe vers le compte
                        // À implémenter selon vos besoins
                    }
                    // Enveloppe vers Enveloppe
                    source is ItemVirement.EnveloppeItem && destination is ItemVirement.EnveloppeItem -> {
                        // Logique pour virement entre enveloppes
                        // À implémenter selon vos besoins
                    }
                }

                _uiState.update {
                    it.copy(
                        virementReussi = true,
                        erreur = null
                    )
                }

                // Recharger les données après le virement
                chargerSourcesEtDestinations()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(erreur = "Erreur lors du virement: ${e.message}")
                }
            }
        }
    }

    // ===== UTILITAIRES =====

    /**
     * Obtient le solde d'un item (compte ou enveloppe).
     */
    private fun obtenirSoldeItem(item: ItemVirement): Double {
        return when (item) {
            is ItemVirement.CompteItem -> item.compte.solde
            is ItemVirement.EnveloppeItem -> item.enveloppe.solde
        }
    }

    /**
     * Vérifie si deux items sont identiques.
     */
    private fun memeItem(item1: ItemVirement, item2: ItemVirement): Boolean {
        return when {
            item1 is ItemVirement.CompteItem && item2 is ItemVirement.CompteItem -> 
                item1.compte.id == item2.compte.id
            item1 is ItemVirement.EnveloppeItem && item2 is ItemVirement.EnveloppeItem -> 
                item1.enveloppe.id == item2.enveloppe.id
            else -> false
        }
    }
}