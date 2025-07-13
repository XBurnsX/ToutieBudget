// chemin/simule: /ui/ajout_transaction/AjoutTransactionViewModel.kt
// Dépendances: ViewModel, Repositories, Use Cases, Modèles de données

package com.xburnsx.toutiebudget.ui.ajout_transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.domain.usecases.*
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ViewModel pour l'écran d'ajout de transactions.
 * Gère toute la logique de saisie et de validation des transactions.
 */
class AjoutTransactionViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val enregistrerDepenseUseCase: EnregistrerDepenseUseCase,
    private val enregistrerRevenuUseCase: EnregistrerRevenuUseCase,
    private val enregistrerPretAccordeUseCase: EnregistrerPretAccordeUseCase,
    private val enregistrerDetteContracteeUseCase: EnregistrerDetteContracteeUseCase,
    private val enregistrerPaiementDetteUseCase: EnregistrerPaiementDetteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AjoutTransactionUiState())
    val uiState: StateFlow<AjoutTransactionUiState> = _uiState.asStateFlow()

    // Données mise en cache pour éviter les rechargements
    private var allComptes: List<Compte> = emptyList()
    private var allEnveloppes: List<Enveloppe> = emptyList()
    private var allAllocations: List<AllocationMensuelle> = emptyList()
    private var allCategories: List<Categorie> = emptyList()

    init {
        chargerDonneesInitiales()
    }

    // ===== CHARGEMENT DES DONNÉES =====

    /**
     * Charge les comptes, enveloppes et allocations depuis les repositories.
     */
    fun chargerDonneesInitiales() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Charger toutes les données nécessaires
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

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        comptesDisponibles = allComptes
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

    // ===== GESTION DU MONTANT =====

    /**
     * Gère les changements de montant directs depuis le composant ChampArgent.
     * Le montant est déjà en centimes, pas besoin de conversion.
     */
    fun onMontantDirectChange(montantEnCentimes: String) {
        _uiState.update { currentState ->
            currentState.copy(montant = montantEnCentimes)
        }
    }

    /**
     * Gère la saisie sur le clavier numérique (conservé pour compatibilité).
     * Construit le montant en centimes pour éviter les erreurs de virgule flottante.
     */
    fun onClavierKeyPress(key: String) {
        _uiState.update { currentState ->
            var montantActuel = currentState.montant
            when (key) {
                "del" -> {
                    // Supprimer le dernier chiffre
                    montantActuel = if (montantActuel.isNotEmpty()) {
                        montantActuel.dropLast(1)
                    } else {
                        ""
                    }
                }
                "." -> {
                    // Ignorer le point décimal (on travaille en centimes)
                }
                else -> {
                    // Ajouter un chiffre (maximum 9 chiffres = 99,999.99$)
                    if (montantActuel.length < 9) {
                        montantActuel += key
                    }
                }
            }
            currentState.copy(montant = montantActuel)
        }
    }

    // ===== GESTION DES MODES ET TYPES =====

    /**
     * Change le mode d'opération (Standard, Prêt, Dette, Paiement).
     */
    fun onModeOperationChanged(nouveauMode: String) {
        _uiState.update { currentState ->
            currentState.copy(
                modeOperation = nouveauMode,
                // Réinitialiser certains champs selon le mode
                enveloppeSelectionnee = null,
                tiers = if (nouveauMode == "Standard") "" else currentState.tiers
            )
        }
    }

    /**
     * Change le type de transaction (Dépense/Revenu) pour le mode Standard.
     */
    fun onTypeTransactionChanged(nouveauType: String) {
        _uiState.update { currentState ->
            currentState.copy(
                typeTransaction = nouveauType,
                enveloppeSelectionnee = null  // Réinitialiser l'enveloppe
            )
        }
        
        // Recharger les enveloppes si un compte est sélectionné
        _uiState.value.compteSelectionne?.let { compte ->
            mettreAJourEnveloppesFiltrees(compte)
        }
    }

    /**
     * Change le type de prêt (Prêt accordé/Remboursement reçu).
     */
    fun onTypePretChanged(nouveauType: String) {
        _uiState.update { currentState ->
            currentState.copy(typePret = nouveauType)
        }
    }

    /**
     * Change le type de dette (Dette contractée/Remboursement donné).
     */
    fun onTypeDetteChanged(nouveauType: String) {
        _uiState.update { currentState ->
            currentState.copy(typeDette = nouveauType)
        }
    }

    // ===== GESTION DES SÉLECTIONS =====

    /**
     * Sélectionne un compte et met à jour les enveloppes disponibles.
     */
    fun onCompteSelected(compte: Compte) {
        _uiState.update { it.copy(compteSelectionne = compte) }
        mettreAJourEnveloppesFiltrees(compte)
    }

    /**
     * Sélectionne une enveloppe pour une dépense.
     */
    fun onEnveloppeSelected(enveloppe: EnveloppeUi) {
        _uiState.update { it.copy(enveloppeSelectionnee = enveloppe) }
    }

    /**
     * Modifie le champ tiers (payé à / reçu de).
     */
    fun onTiersChanged(nouveauTiers: String) {
        _uiState.update { currentState ->
            currentState.copy(tiers = nouveauTiers)
        }
    }

    /**
     * Modifie le champ note.
     */
    fun onNoteChanged(nouvelleNote: String) {
        _uiState.update { currentState ->
            currentState.copy(note = nouvelleNote)
        }
    }

    // ===== LOGIQUE MÉTIER =====

    /**
     * Met à jour la liste des enveloppes disponibles selon le compte sélectionné.
     * Filtre les enveloppes vides OU celles qui contiennent de l'argent du même compte.
     */
    private fun mettreAJourEnveloppesFiltrees(compteSelectionne: Compte) {
        val enveloppesUi = allEnveloppes.map { enveloppe ->
            val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
            val compteSource = allocation?.compteSourceId?.let { id ->
                allComptes.find { it.id == id }
            }
            val solde = allocation?.solde ?: 0.0
            val objectif = enveloppe.objectifMontant

            // Calculer le statut visuel de l'enveloppe
            val statut = when {
                objectif > 0 && solde >= objectif -> StatutObjectif.VERT
                solde > 0 -> StatutObjectif.JAUNE
                else -> StatutObjectif.GRIS
            }

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = solde,
                depense = allocation?.depense ?: 0.0,
                objectif = objectif,
                couleurProvenance = compteSource?.couleur,
                statutObjectif = statut
            )
        }

        // Filtrer: enveloppes vides OU avec argent du même compte
        val enveloppesFiltrees = enveloppesUi.filter { enveloppeUi ->
            enveloppeUi.solde == 0.0 || enveloppeUi.couleurProvenance == compteSelectionne.couleur
        }

        // Grouper par catégorie
        val enveloppesGroupees = enveloppesFiltrees.groupBy { enveloppeUi ->
            val enveloppe = allEnveloppes.find { it.id == enveloppeUi.id }
            val categorie = enveloppe?.categorieId?.let { categorieId ->
                allCategories.find { it.id == categorieId }
            }
            categorie?.nom ?: "Autre"
        }

        _uiState.update { it.copy(enveloppesFiltrees = enveloppesGroupees) }
    }

    // ===== SAUVEGARDE =====

    /**
     * Sauvegarde la transaction selon le mode et type sélectionnés.
     */
    fun sauvegarderTransaction() {
        viewModelScope.launch {
            val state = _uiState.value

            // Convertir le montant de centimes en dollars
            val montant = (state.montant.toLongOrNull() ?: 0L) / 100.0
            val compte = state.compteSelectionne

            // Validation de base
            if (montant <= 0) {
                _uiState.update {
                    it.copy(erreur = "Veuillez entrer un montant valide.")
                }
                return@launch
            }

            if (compte == null) {
                _uiState.update {
                    it.copy(erreur = "Veuillez sélectionner un compte.")
                }
                return@launch
            }

            // Choisir la bonne action selon le mode
            val resultat: Result<Unit> = when (state.modeOperation) {
                "Standard" -> {
                    if (state.typeTransaction == "Dépense") {
                        sauvegarderDepense(montant)
                    } else {
                        sauvegarderRevenu(montant)
                    }
                }
                "Prêt" -> {
                    enregistrerPretAccordeUseCase(montant, compte, compte.collection, state.tiers, state.note)
                }
                "Dette" -> {
                    enregistrerDetteContracteeUseCase(montant, compte, compte.collection, state.tiers, state.note)
                }
                "Paiement" -> {
                    enregistrerPaiementDetteUseCase(montant, compte, compte.collection, state.tiers, state.note)
                }
                else -> {
                    Result.failure(Exception("Type d'opération inconnu: ${state.modeOperation}"))
                }
            }

            // Traiter le résultat
            resultat.onSuccess {
                _uiState.update {
                    it.copy(
                        transactionReussie = true,
                        erreur = null
                    )
                }
            }.onFailure { erreur ->
                _uiState.update {
                    it.copy(erreur = erreur.message)
                }
            }
        }
    }

    /**
     * Sauvegarde une dépense vers une enveloppe.
     */
    private suspend fun sauvegarderDepense(montant: Double): Result<Unit> {
        val state = _uiState.value
        val enveloppe = state.enveloppeSelectionnee

        if (enveloppe == null) {
            return Result.failure(Exception("Veuillez sélectionner une enveloppe pour la dépense."))
        }

        // Récupérer ou créer l'allocation mensuelle
        val allocation = enveloppeRepository.recupererOuCreerAllocation(
            enveloppe.id,
            Date()
        ).getOrNull()

        if (allocation == null) {
            return Result.failure(Exception("Impossible de récupérer l'allocation de l'enveloppe."))
        }

        return enregistrerDepenseUseCase(
            montant = montant,
            allocationMensuelle = allocation,
            dateTransaction = Date(),
            note = state.note.ifBlank { null },
            tiers = state.tiers.ifBlank { null }
        )
    }

    /**
     * Sauvegarde un revenu vers un compte.
     */
    private suspend fun sauvegarderRevenu(montant: Double): Result<Unit> {
        val state = _uiState.value
        val compte = state.compteSelectionne!!

        return enregistrerRevenuUseCase(
            montant = montant,
            compteCible = compte,
            collectionCompteCible = compte.collection,
            dateTransaction = Date(),
            note = state.note.ifBlank { null },
            tiers = state.tiers.ifBlank { null }
        )
    }

    // ===== RÉINITIALISATION =====

    /**
     * Réinitialise le formulaire après une transaction réussie.
     */
    fun reinitialiserFormulaire() {
        _uiState.update {
            AjoutTransactionUiState(
                comptesDisponibles = allComptes
            )
        }
    }
}