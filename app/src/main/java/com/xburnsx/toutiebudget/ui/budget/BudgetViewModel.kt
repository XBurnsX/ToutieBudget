// chemin/simule: /ui/budget/BudgetViewModel.kt
package com.xburnsx.toutiebudget.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
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
import kotlinx.coroutines.launch
import java.util.Date

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

    /**
     * Rafraîchit les données du budget pour le mois donné.
     * Si des données sont déjà présentes en cache, elles sont
     * affichées immédiatement puis un rafraîchissement silencieux est effectué.
     */
    fun chargerDonneesBudget(mois: Date) {
        viewModelScope.launch {
            // 1. Si on a déjà du cache, l'afficher immédiatement sans loader
            if (cacheComptes.isNotEmpty() && cacheEnveloppes.isNotEmpty() && cacheCategories.isNotEmpty()) {
                // Reconstruire la vue à partir du cache
                val bandeauxPretAPlacer = cacheComptes
                    .filterIsInstance<CompteCheque>()
                    .filter { it.solde > 0 }
                    .map {
                        PretAPlacerUi(it.id, it.nom, it.solde, it.couleur)
                    }
                val enveloppesUi = creerEnveloppesUi(cacheEnveloppes, cacheAllocations, cacheComptes)
                val groupesEnveloppes = enveloppesUi.groupBy { enveloppeUi ->
                    val enveloppe = cacheEnveloppes.find { it.id == enveloppeUi.id }
                    val cat = enveloppe?.categorieId?.let { id -> cacheCategories.find { it.id == id } }
                    cat?.nom ?: "Autre"
                }
                val categoriesEnveloppesUi = cacheCategories.map { cat ->
                    CategorieEnveloppesUi(cat.nom, groupesEnveloppes[cat.nom] ?: emptyList())
                }.sortedBy { it.nomCategorie }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bandeauxPretAPlacer = bandeauxPretAPlacer,
                        categoriesEnveloppes = categoriesEnveloppesUi,
                        messageChargement = null
                    )
                }
                // Et on rafraîchit en arrière-plan
                launch { rafraichirDepuisServeur(mois) }
                return@launch
            }
            // 2. Pas de cache? on affiche le loader puis on récupère
            _uiState.update { it.copy(isLoading = true, messageChargement = "Chargement des données...") }
            rafraichirDepuisServeur(mois)
        }
    }

    private suspend fun rafraichirDepuisServeur(mois: Date) {
        try {
            cacheComptes = compteRepository.recupererTousLesComptes().getOrThrow()
            cacheEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
            cacheAllocations = enveloppeRepository.recupererAllocationsPourMois(mois).getOrThrow()
            cacheCategories = categorieRepository.recupererToutesLesCategories().getOrThrow()

            val bandeauxPretAPlacer = cacheComptes
                .filterIsInstance<CompteCheque>()
                .filter { it.solde > 0 }
                .map { PretAPlacerUi(it.id, it.nom, it.solde, it.couleur) }

            val enveloppesUi = creerEnveloppesUi(cacheEnveloppes, cacheAllocations, cacheComptes)
            val groupesEnveloppes = enveloppesUi.groupBy { enveloppeUi ->
                val enveloppe = cacheEnveloppes.find { it.id == enveloppeUi.id }
                val cat = enveloppe?.categorieId?.let { id -> cacheCategories.find { it.id == id } }
                cat?.nom ?: "Autre"
            }
            val categoriesEnveloppesUi = cacheCategories.map { cat ->
                CategorieEnveloppesUi(cat.nom, groupesEnveloppes[cat.nom] ?: emptyList())
            }.sortedBy { it.nomCategorie }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    bandeauxPretAPlacer = bandeauxPretAPlacer,
                    categoriesEnveloppes = categoriesEnveloppesUi,
                    messageChargement = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, erreur = "Erreur lors du chargement des données: ${e.message}") }
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

    /**
     * Méthode publique pour rafraîchir les données depuis d'autres ViewModels
     */
    fun rafraichirDonnees() {
        chargerDonneesBudget(Date())
    }
}
