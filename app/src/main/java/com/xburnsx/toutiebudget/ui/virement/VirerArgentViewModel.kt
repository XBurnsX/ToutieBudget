// chemin/simule: /ui/virement/VirerArgentViewModel.kt
package com.xburnsx.toutiebudget.ui.virement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
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

class VirerArgentViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val argentService: ArgentService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VirerArgentUiState())
    val uiState: StateFlow<VirerArgentUiState> = _uiState.asStateFlow()

    init {
        chargerSourcesEtDestinations()
    }

    private fun chargerSourcesEtDestinations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val comptes = compteRepository.recupererTousLesComptes().getOrThrow()
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val allocations = enveloppeRepository.recupererAllocationsPourMois(Date()).getOrThrow()
                val categories = categorieRepository.recupererToutesLesCategories().getOrThrow()

                val itemsComptes = comptes
                    .filterIsInstance<CompteCheque>()
                    .map { ItemVirement.CompteItem(it) }

                val itemsEnveloppes = enveloppes.map { enveloppe ->
                    val alloc = allocations.find { it.enveloppeId == enveloppe.id }
                    val compteSource = alloc?.compteSourceId?.let { id -> comptes.find { it.id == id } }
                    ItemVirement.EnveloppeItem(enveloppe, alloc?.solde ?: 0.0, compteSource?.couleur)
                }

                // Créer un map pour accéder rapidement aux catégories par ID
                val categoriesMap = categories.associateBy { it.id }
                
                val sources = mapOf("Prêt à placer" to itemsComptes) + itemsEnveloppes.groupBy { item ->
                    val categorie = categoriesMap[item.enveloppe.categorieId]
                    categorie?.nom ?: "Autre"
                }
                val destinations = mapOf("Prêt à placer" to itemsComptes) + itemsEnveloppes.groupBy { item ->
                    val categorie = categoriesMap[item.enveloppe.categorieId]
                    categorie?.nom ?: "Autre"
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sourcesDisponibles = sources,
                        destinationsDisponibles = destinations
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, erreur = e.message) }
            }
        }
    }

    // ===== FONCTIONS PUBLIQUES POUR L'INTERFACE =====

    fun ouvrirSelecteur(type: SelecteurOuvert) { 
        _uiState.update { it.copy(selecteurOuvert = type) } 
    }
    
    fun fermerSelecteur() { 
        _uiState.update { it.copy(selecteurOuvert = SelecteurOuvert.AUCUN) } 
    }
    
    fun onItemSelected(item: ItemVirement) {
        when (_uiState.value.selecteurOuvert) {
            SelecteurOuvert.SOURCE -> _uiState.update { it.copy(sourceSelectionnee = item) }
            SelecteurOuvert.DESTINATION -> _uiState.update { it.copy(destinationSelectionnee = item) }
            SelecteurOuvert.AUCUN -> {}
        }
        fermerSelecteur()
    }

    /**
     * Met à jour le montant saisi par l'utilisateur
     */
    fun onMontantChange(nouveauMontant: String) {
        // Limiter à 9 chiffres maximum (99,999.99$)
        if (nouveauMontant.length <= 9) {
            _uiState.update { it.copy(montant = nouveauMontant) }
        }
    }

    /**
     * Exécute le virement d'argent entre source et destination
     */
    fun onVirementExecute() {
        val state = _uiState.value
        val source = state.sourceSelectionnee
        val destination = state.destinationSelectionnee
        val montant = (state.montant.toLongOrNull() ?: 0L) / 100.0

        if (source == null || destination == null || montant <= 0) {
            _uiState.update { it.copy(erreur = "Veuillez remplir tous les champs") }
            return
        }

        viewModelScope.launch {
            try {
                // TODO: Implémenter la logique de virement
                // Pour l'instant, on simule un succès
                _uiState.update { 
                    it.copy(
                        virementReussi = true,
                        montant = "",
                        sourceSelectionnee = null,
                        destinationSelectionnee = null
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = e.message) }
            }
        }
    }

    /**
     * Gère la saisie sur le clavier numérique.
     * Gère intelligemment le point décimal et les chiffres.
     */
    fun onClavierKeyPress(key: String) {
        _uiState.update { currentState ->
            var montantActuel = currentState.montant
            when (key) {
                "del" -> {
                    montantActuel = if (montantActuel.isNotEmpty()) {
                        montantActuel.dropLast(1)
                    } else {
                        ""
                    }
                }
                "." -> {
                    if (!montantActuel.contains('.') && montantActuel.isNotEmpty()) {
                        montantActuel += key
                    }
                }
                else -> {
                    if (montantActuel.length < 8) {
                        montantActuel += key
                    }
                }
            }
            currentState.copy(montant = montantActuel)
        }
    }
}