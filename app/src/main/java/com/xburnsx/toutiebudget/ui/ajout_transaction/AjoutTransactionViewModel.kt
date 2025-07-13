// chemin/simule: /ui/ajout_transaction/AjoutTransactionViewModel.kt
// Dépendances: ViewModel, Repositories, Use Cases, Modèles de données

package com.xburnsx.toutiebudget.ui.ajout_transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.domain.usecases.*
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
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

    // Cache des données pour éviter les rechargements
    private var allComptes: List<Compte> = emptyList()
    private var allEnveloppes: List<Enveloppe> = emptyList()
    private var allAllocations: List<AllocationMensuelle> = emptyList()
    private var allCategories: List<Categorie> = emptyList()

    init {
        chargerDonneesInitiales()
    }

    /**
     * Charge toutes les données nécessaires au démarrage de l'écran.
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
     * *** CORRECTION : Ne plus vider le champ tiers pour le mode Standard ***
     */
    fun onModeOperationChanged(nouveauMode: String) {
        _uiState.update { currentState ->
            currentState.copy(
                modeOperation = nouveauMode,
                // Réinitialiser certains champs selon le mode
                enveloppeSelectionnee = null
                // SUPPRIMÉ : tiers = if (nouveauMode == "Standard") "" else currentState.tiers
                // Le champ tiers est maintenant gardé pour Standard/Dépense
            )
        }
    }

    /**
     * Change le type de transaction (Dépense/Revenu) pour le mode Standard.
     * *** CORRECTION : Vider le tiers seulement pour Standard/Revenu ***
     */
    fun onTypeTransactionChanged(nouveauType: String) {
        _uiState.update { currentState ->
            currentState.copy(
                typeTransaction = nouveauType,
                enveloppeSelectionnee = null,  // Réinitialiser l'enveloppe
                // NOUVEAU : Vider le tiers seulement si on passe en "Revenu"
                tiers = if (nouveauType == "Revenu") "" else currentState.tiers
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

            try {
                when (state.modeOperation) {
                    "Standard" -> {
                        if (state.typeTransaction == "Dépense") {
                            sauvegarderDepense(montant)
                        } else {
                            sauvegarderRevenu(montant)
                        }
                    }
                    "Prêt" -> {
                        if (state.typePret == "Prêt accordé") {
                            sauvegarderPretAccorde(montant)
                        } else {
                            sauvegarderRemboursementRecu(montant)
                        }
                    }
                    "Dette" -> {
                        if (state.typeDette == "Dette contractée") {
                            sauvegarderDetteContractee(montant)
                        } else {
                            sauvegarderRemboursementDonne(montant)
                        }
                    }
                    "Paiement" -> {
                        sauvegarderPaiement(montant)
                    }
                }

                _uiState.update { it.copy(transactionReussie = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(erreur = "Erreur lors de la sauvegarde: ${e.message}")
                }
            }
        }
    }

    /**
     * Sauvegarde une dépense standard avec soustraction de l'enveloppe.
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

    /**
     * Sauvegarde un prêt accordé.
     */
    private suspend fun sauvegarderPretAccorde(montant: Double): Result<Unit> {
        val state = _uiState.value
        val compte = state.compteSelectionne!!

        return enregistrerPretAccordeUseCase(
            montant = montant,
            compteSource = compte,
            collectionCompteSource = compte.collection,
            tiers = state.tiers,
            note = state.note.ifBlank { null }
        )
    }

    /**
     * Sauvegarde un remboursement reçu (comme un revenu).
     */
    private suspend fun sauvegarderRemboursementRecu(montant: Double): Result<Unit> {
        val state = _uiState.value
        val compte = state.compteSelectionne!!

        return enregistrerRevenuUseCase(
            montant = montant,
            compteCible = compte,
            collectionCompteCible = compte.collection,
            dateTransaction = Date(),
            note = "Remboursement reçu de ${state.tiers}. ${state.note}".trim(),
            tiers = state.tiers
        )
    }

    /**
     * Sauvegarde une dette contractée.
     */
    private suspend fun sauvegarderDetteContractee(montant: Double): Result<Unit> {
        val state = _uiState.value
        val compte = state.compteSelectionne!!

        return enregistrerDetteContracteeUseCase(
            montant = montant,
            compteCible = compte,
            collectionCompteCible = compte.collection,
            tiers = state.tiers,
            note = state.note.ifBlank { null }
        )
    }

    /**
     * Sauvegarde un remboursement donné.
     */
    private suspend fun sauvegarderRemboursementDonne(montant: Double): Result<Unit> {
        val state = _uiState.value
        val compte = state.compteSelectionne!!

        return enregistrerPaiementDetteUseCase(
            montant = montant,
            compteSource = compte,
            collectionCompteSource = compte.collection,
            tiers = state.tiers,
            note = state.note.ifBlank { null }
        )
    }

    /**
     * Sauvegarde un paiement.
     */
    private suspend fun sauvegarderPaiement(montant: Double): Result<Unit> {
        val state = _uiState.value
        val compte = state.compteSelectionne!!

        return enregistrerPaiementDetteUseCase(
            montant = montant,
            compteSource = compte,
            collectionCompteSource = compte.collection,
            tiers = state.tiers,
            note = state.note.ifBlank { null }
        )
    }

    // ===== UTILITAIRES =====

    /**
     * Réinitialise le formulaire après une transaction réussie.
     */
    fun reinitialiserFormulaire() {
        _uiState.update {
            AjoutTransactionUiState(
                comptesDisponibles = allComptes,
                transactionReussie = false
            )
        }
    }

    /**
     * Efface le message d'erreur.
     */
    fun effacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }
}