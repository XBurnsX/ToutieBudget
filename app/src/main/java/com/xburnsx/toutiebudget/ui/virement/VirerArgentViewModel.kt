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

                // Créer les items d'enveloppes avec leurs soldes
                val itemsEnveloppes = allEnveloppes.map { enveloppe ->
                    val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
                    val compteSource = allocation?.compteSourceId?.let { id -> 
                        allComptes.find { it.id == id } 
                    }
                    
                    ItemVirement.EnveloppeItem(
                        enveloppe = enveloppe,
                        solde = allocation?.solde ?: 0.0,
                        couleurProvenance = compteSource?.couleur
                    )
                }

                // Créer un map pour accéder rapidement aux catégories par ID
                val categoriesMap = allCategories.associateBy { it.id }
                
                // Grouper les sources (comptes + enveloppes avec argent)
                val sourcesEnveloppes = itemsEnveloppes
                    .filter { it.solde > 0 }  // Seulement les enveloppes avec de l'argent
                    .groupBy { item ->
                        val categorie = categoriesMap[item.enveloppe.categorieId]
                        categorie?.nom ?: "Autre"
                    }
                
                val sources = mapOf("Prêt à placer" to itemsComptes) + sourcesEnveloppes
                
                // Grouper les destinations (comptes + toutes les enveloppes)
                val destinationsEnveloppes = itemsEnveloppes.groupBy { item ->
                    val categorie = categoriesMap[item.enveloppe.categorieId]
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
                        argentService.effectuerVirementCompteVersEnveloppe(
                            compte = source.compte,
                            enveloppe = destination.enveloppe,
                            montant = montantEnDollars
                        )
                    }
                    
                    // Enveloppe vers Compte
                    source is ItemVirement.EnveloppeItem && destination is ItemVirement.CompteItem -> {
                        argentService.effectuerVirementEnveloppeVersCompte(
                            enveloppe = source.enveloppe,
                            compte = destination.compte,
                            montant = montantEnDollars
                        )
                    }
                    
                    // Enveloppe vers Enveloppe
                    source is ItemVirement.EnveloppeItem && destination is ItemVirement.EnveloppeItem -> {
                        argentService.effectuerVirementEnveloppeVersEnveloppe(
                            enveloppeSource = source.enveloppe,
                            enveloppeDestination = destination.enveloppe,
                            montant = montantEnDollars
                        )
                    }
                }
                
                // Succès - réinitialiser le formulaire
                _uiState.update { 
                    it.copy(
                        virementReussi = true,
                        montant = "",
                        sourceSelectionnee = null,
                        destinationSelectionnee = null,
                        erreur = null
                    ) 
                }
                
                // Recharger les données pour refléter les changements
                chargerSourcesEtDestinations()
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(erreur = "Erreur lors du virement: ${e.message}") 
                }
            }
        }
    }

    // ===== FONCTIONS UTILITAIRES =====

    /**
     * Détermine si deux items sont identiques.
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

    /**
     * Obtient le solde disponible d'un item.
     */
    private fun obtenirSoldeItem(item: ItemVirement): Double {
        return when (item) {
            is ItemVirement.CompteItem -> item.compte.solde
            is ItemVirement.EnveloppeItem -> item.solde
        }
    }

    /**
     * Réinitialise l'état de succès du virement.
     */
    fun reinitialiserSucces() {
        _uiState.update { it.copy(virementReussi = false) }
    }

    /**
     * Efface l'erreur actuelle.
     */
    fun effacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }
}