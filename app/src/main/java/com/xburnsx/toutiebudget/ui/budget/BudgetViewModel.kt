// chemin/simule: /ui/budget/BudgetViewModel.kt
package com.xburnsx.toutiebudget.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.domain.usecases.VerifierEtExecuterRolloverUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

class BudgetViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase
) : ViewModel() {

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
    }

    fun chargerDonneesBudget(mois: Date) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageChargement = "Chargement des données...") }
            try {
                val comptes = compteRepository.recupererTousLesComptes().getOrThrow()
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val allocations = enveloppeRepository.recupererAllocationsPourMois(mois).getOrThrow()
                val categories = categorieRepository.recupererToutesLesCategories().getOrThrow()

                // Créer des bandeaux pour chaque compte chèque avec solde > 0
                val bandeauxPretAPlacer = comptes
                    .filterIsInstance<CompteCheque>()
                    .filter { it.solde > 0 }
                    .map { compte ->
                        PretAPlacerUi(
                            compteId = compte.id,
                            nomCompte = compte.nom,
                            montant = compte.solde,
                            couleurCompte = compte.couleur
                        )
                    }

                // Créer les enveloppes UI et les grouper par catégorie
                val enveloppesUi = creerEnveloppesUi(enveloppes, allocations, comptes)
                
                // Grouper les enveloppes par catégorie
                val groupesEnveloppes = enveloppesUi.groupBy { enveloppeUi ->
                    val enveloppe = enveloppes.find { it.id == enveloppeUi.id }
                    val categorie = enveloppe?.categorieId?.let { categorieId ->
                        categories.find { it.id == categorieId }
                    }
                    categorie?.nom ?: "Autre"
                }
                
                // Créer un map complet avec toutes les catégories (même vides)
                val categoriesEnveloppes = categories.map { categorie ->
                    CategorieEnveloppesUi(
                        nomCategorie = categorie.nom,
                        enveloppes = groupesEnveloppes[categorie.nom] ?: emptyList()
                    )
                }.sortedBy { it.nomCategorie }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bandeauxPretAPlacer = bandeauxPretAPlacer,
                        categoriesEnveloppes = categoriesEnveloppes
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, erreur = "Erreur lors du chargement des données: ${e.message}") }
            }
        }
    }

    private fun creerEnveloppesUi(
        enveloppes: List<Enveloppe>,
        allocations: List<AllocationMensuelle>,
        comptes: List<Compte>
    ): List<EnveloppeUi> {
        val mapAllocations = allocations.associateBy { it.enveloppeId }
        val mapComptes = comptes.associateBy { it.id }

        return enveloppes.map { enveloppe ->
            val allocation = mapAllocations[enveloppe.id]
            val compteSource = allocation?.compteSourceId?.let { mapComptes[it] }
            val solde = allocation?.solde ?: 0.0
            val depense = allocation?.depense ?: 0.0
            val objectif = enveloppe.objectifMontant
            val statut = when {
                objectif > 0 && solde >= objectif -> StatutObjectif.VERT
                solde > 0 -> StatutObjectif.JAUNE
                else -> StatutObjectif.GRIS
            }
            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = solde,
                depense = depense,
                objectif = objectif,
                couleurProvenance = compteSource?.couleur,
                statutObjectif = statut
            )
        }
    }
}
