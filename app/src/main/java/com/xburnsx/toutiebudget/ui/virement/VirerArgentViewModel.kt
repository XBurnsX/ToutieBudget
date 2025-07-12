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

    // Cache pour éviter les rechargements visibles
    private var donneesCachees: VirerArgentUiState? = null

    init {
        // État initial sans chargement visible
        _uiState.update { it.copy(isLoading = false) }
        chargerSourcesEtDestinations()
    }

    private fun chargerSourcesEtDestinations() {
        // Si on a des données en cache, les afficher immédiatement
        donneesCachees?.let { cache ->
            _uiState.update { cache }
        }
        
        // Puis charger en arrière-plan
        chargerSourcesEtDestinationsSilencieusement()
    }

    private fun chargerSourcesEtDestinationsSilencieusement() {
        viewModelScope.launch {
            try {
                val comptes = compteRepository.recupererTousLesComptes().getOrThrow()
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val allocations = enveloppeRepository.recupererAllocationsPourMois(Date()).getOrThrow()
                val categories = categorieRepository.recupererToutesLesCategories().getOrThrow()

                // Créer les sources (comptes chèques avec solde > 0)
                val sourcesComptes = comptes
                    .filterIsInstance<CompteCheque>()
                    .filter { it.solde > 0 }
                    .map { ItemVirement.CompteItem(it) }

                // Créer les sources enveloppes avec solde > 0
                val sourcesEnveloppes = enveloppes
                    .filter { !it.estArchive }
                    .mapNotNull { enveloppe ->
                        val allocation = allocations.find { it.enveloppeId == enveloppe.id }
                        if (allocation?.solde ?: 0.0 > 0) {
                            val categorie = categories.find { it.id == enveloppe.categorieId }
                            ItemVirement.EnveloppeItem(
                                enveloppe = enveloppe,
                                solde = allocation?.solde ?: 0.0,
                                couleurProvenance = null
                            )
                        } else null
                    }

                // Créer les destinations (toutes les enveloppes)
                val destinationsEnveloppes = enveloppes
                    .filter { !it.estArchive }
                    .map { enveloppe ->
                        val allocation = allocations.find { it.enveloppeId == enveloppe.id }
                        val categorie = categories.find { it.id == enveloppe.categorieId }
                        ItemVirement.EnveloppeItem(
                            enveloppe = enveloppe,
                            solde = allocation?.solde ?: 0.0,
                            couleurProvenance = null
                        )
                    }

                // Grouper par catégorie
                val sourcesDisponibles = mapOf(
                    "Comptes" to sourcesComptes,
                    "Enveloppes" to sourcesEnveloppes
                ).filter { it.value.isNotEmpty() }

                val destinationsDisponibles = destinationsEnveloppes
                    .groupBy { enveloppeItem ->
                        val categorie = categories.find { it.id == enveloppeItem.enveloppe.categorieId }
                        categorie?.nom ?: "Sans catégorie"
                    }
                    .filter { it.value.isNotEmpty() }

                val nouvelEtat = VirerArgentUiState(
                    isLoading = false,
                    sourcesDisponibles = sourcesDisponibles,
                    destinationsDisponibles = destinationsDisponibles
                )

                // Mettre en cache et mettre à jour l'UI
                donneesCachees = nouvelEtat
                _uiState.update { nouvelEtat }

            } catch (e: Exception) {
                // Erreur silencieuse - on garde les données précédentes si disponibles
                if (donneesCachees == null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            erreur = "Erreur lors du chargement: ${e.message}"
                        ) 
                    }
                }
            }
        }
    }

    fun onMontantChange(montant: String) {
        _uiState.update { it.copy(montant = montant) }
    }

    fun onSourceSelectionnee(source: ItemVirement) {
        _uiState.update { it.copy(sourceSelectionnee = source) }
    }

    fun onDestinationSelectionnee(destination: ItemVirement) {
        _uiState.update { it.copy(destinationSelectionnee = destination) }
    }

    fun onSelecteurOuvert(selecteur: SelecteurOuvert) {
        _uiState.update { it.copy(selecteurOuvert = selecteur) }
    }

    fun onVirementExecute() {
        val source = _uiState.value.sourceSelectionnee
        val destination = _uiState.value.destinationSelectionnee
        val montant = _uiState.value.montant.toDoubleOrNull()

        if (source == null || destination == null || montant == null || montant <= 0) {
            _uiState.update { it.copy(erreur = "Veuillez sélectionner une source, une destination et un montant valide") }
            return
        }

        viewModelScope.launch {
            try {
                // Pour l'instant, on simule le virement
                // TODO: Implémenter la logique de virement avec ArgentService
                _uiState.update { 
                    it.copy(
                        virementReussi = true,
                        montant = "",
                        sourceSelectionnee = null,
                        destinationSelectionnee = null
                    ) 
                }
                // Recharger les données en arrière-plan
                chargerSourcesEtDestinationsSilencieusement()

            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur lors du virement: ${e.message}") }
            }
        }
    }

    fun onEffacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }

    fun onVirementReussi() {
        _uiState.update { it.copy(virementReussi = false) }
    }
}
