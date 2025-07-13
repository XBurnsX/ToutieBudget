// chemin/simule: /ui/ajout_transaction/AjoutTransactionViewModel.kt
// Dépendances: ViewModel, Repositories, Use Cases, Modèles de données, Coroutines

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
import com.xburnsx.toutiebudget.domain.usecases.EnregistrerTransactionUseCase
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import java.util.Calendar
import java.util.Date

/**
 * ViewModel pour l'écran d'ajout de transactions.
 * Gère toute la logique de saisie, validation et sauvegarde des transactions.
 */
class AjoutTransactionViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase
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
    private fun chargerDonneesInitiales() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageErreur = null) }
            
            try {
                // Charger toutes les données en parallèle
                val resultComptes = compteRepository.recupererTousLesComptes()
                val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                val resultCategories = categorieRepository.recupererToutesLesCategories()
                
                // Calculer le mois actuel pour les allocations
                val maintenant = Date()
                val calendrier = Calendar.getInstance().apply {
                    time = maintenant
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val premierJourMoisActuel = calendrier.time
                
                val resultAllocations = enveloppeRepository.recupererAllocationsParMois(premierJourMoisActuel)
                
                // Vérifier les erreurs
                if (resultComptes.isFailure) {
                    throw Exception("Erreur lors du chargement des comptes: ${resultComptes.exceptionOrNull()?.message}")
                }
                if (resultEnveloppes.isFailure) {
                    throw Exception("Erreur lors du chargement des enveloppes: ${resultEnveloppes.exceptionOrNull()?.message}")
                }
                if (resultCategories.isFailure) {
                    throw Exception("Erreur lors du chargement des catégories: ${resultCategories.exceptionOrNull()?.message}")
                }
                
                // Stocker les données dans le cache
                allComptes = resultComptes.getOrNull() ?: emptyList()
                allEnveloppes = resultEnveloppes.getOrNull() ?: emptyList()
                allCategories = resultCategories.getOrNull() ?: emptyList()
                allAllocations = resultAllocations.getOrNull() ?: emptyList()
                
                // Construire les enveloppes UI
                val enveloppesUi = construireEnveloppesUi()
                val enveloppesFiltrees = enveloppesUi.groupBy { enveloppe ->
                    val categorieNom = allCategories.find { cat -> 
                        allEnveloppes.find { it.id == enveloppe.id }?.categorieId == cat.id 
                    }?.nom ?: "Sans catégorie"
                    categorieNom
                }
                
                // Mettre à jour l'état
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        comptesDisponibles = allComptes.filter { !it.estArchive },
                        enveloppesDisponibles = enveloppesUi,
                        enveloppesFiltrees = enveloppesFiltrees
                    ).calculerValidite()
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        messageErreur = e.message ?: "Erreur lors du chargement des données"
                    )
                }
            }
        }
    }

    /**
     * Construit la liste des enveloppes UI avec leurs allocations.
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
                statutObjectif = StatutObjectif.GRIS  // Simplifié pour l'ajout de transaction
            )
        }.sortedBy { enveloppe ->
            val categorie = allCategories.find { cat -> 
                allEnveloppes.find { it.id == enveloppe.id }?.categorieId == cat.id 
            }
            categorie?.nom ?: "Sans catégorie"
        }
    }

    /**
     * Met à jour le mode d'opération sélectionné.
     */
    fun onModeOperationChanged(nouveauMode: String) {
        _uiState.update { state ->
            state.copy(
                modeOperation = nouveauMode,
                enveloppeSelectionnee = null // Reset de l'enveloppe si on change de mode
            ).calculerValidite()
        }
    }

    /**
     * Met à jour le type de transaction sélectionné (pour mode Standard).
     */
    fun onTypeTransactionChanged(nouveauType: TypeTransaction) {
        _uiState.update { state ->
            state.copy(
                typeTransaction = nouveauType,
                enveloppeSelectionnee = null // Reset de l'enveloppe si on change de type
            ).calculerValidite()
        }
    }

    /**
     * Met à jour le type de prêt sélectionné.
     */
    fun onTypePretChanged(nouveauType: String) {
        _uiState.update { state ->
            state.copy(typePret = nouveauType).calculerValidite()
        }
    }

    /**
     * Met à jour le type de dette sélectionné.
     */
    fun onTypeDetteChanged(nouveauType: String) {
        _uiState.update { state ->
            state.copy(typeDette = nouveauType).calculerValidite()
        }
    }

    /**
     * Met à jour le montant saisi directement.
     */
    fun onMontantDirectChange(nouveauMontant: String) {
        _uiState.update { state ->
            state.copy(montant = nouveauMontant).calculerValidite()
        }
    }

    /**
     * Met à jour le compte sélectionné.
     */
    fun onCompteSelected(nouveauCompte: Compte?) {
        _uiState.update { state ->
            state.copy(compteSelectionne = nouveauCompte).calculerValidite()
        }
    }

    /**
     * Met à jour l'enveloppe sélectionnée.
     */
    fun onEnveloppeSelected(nouvelleEnveloppe: EnveloppeUi?) {
        _uiState.update { state ->
            state.copy(enveloppeSelectionnee = nouvelleEnveloppe).calculerValidite()
        }
    }

    /**
     * Met à jour le champ tiers.
     */
    fun onTiersChanged(nouveauTiers: String) {
        _uiState.update { state ->
            state.copy(tiers = nouveauTiers).calculerValidite()
        }
    }

    /**
     * Réinitialise pour une nouvelle transaction.
     */
    fun nouvelleTransaction() {
        _uiState.update { 
            AjoutTransactionUiState(
                isLoading = false,
                comptesDisponibles = _uiState.value.comptesDisponibles,
                enveloppesDisponibles = _uiState.value.enveloppesDisponibles,
                enveloppesFiltrees = _uiState.value.enveloppesFiltrees
            ).calculerValidite()
        }
    }

    /**
     * Met à jour le montant saisi.
     */
    fun onMontantChanged(nouveauMontant: String) {
        _uiState.update { state ->
            state.copy(montant = nouveauMontant).calculerValidite()
        }
    }

    /**
     * Met à jour le compte sélectionné.
     */
    fun onCompteChanged(nouveauCompte: Compte?) {
        _uiState.update { state ->
            state.copy(compteSelectionne = nouveauCompte).calculerValidite()
        }
    }

    /**
     * Met à jour l'enveloppe sélectionnée.
     */
    fun onEnveloppeChanged(nouvelleEnveloppe: EnveloppeUi?) {
        _uiState.update { state ->
            state.copy(enveloppeSelectionnee = nouvelleEnveloppe).calculerValidite()
        }
    }

    /**
     * Met à jour la note saisie.
     */
    fun onNoteChanged(nouvelleNote: String) {
        _uiState.update { state ->
            state.copy(note = nouvelleNote).calculerValidite()
        }
    }

    /**
     * Sauvegarde la transaction avec toutes les validations.
     */
    fun sauvegarderTransaction() {
        val state = _uiState.value
        
        if (!state.peutSauvegarder) {
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(estEnTrainDeSauvegarder = true, messageErreur = null) }
            
            try {
                val montant = state.montant.toDoubleOrNull() 
                    ?: throw Exception("Montant invalide")
                
                val compte = state.compteSelectionne 
                    ?: throw Exception("Aucun compte sélectionné")
                
                // Pour les dépenses, vérifier qu'une enveloppe est sélectionnée
                val enveloppeId = if (state.typeTransaction == TypeTransaction.Depense) {
                    state.enveloppeSelectionnee?.id 
                        ?: throw Exception("Aucune enveloppe sélectionnée pour la dépense")
                } else {
                    null
                }
                
                // Utiliser directement l'enum TypeTransaction
                val typeTransaction = state.typeTransaction
                
                // Enregistrer la transaction
                val result = enregistrerTransactionUseCase.executer(
                    typeTransaction = typeTransaction,
                    montant = montant,
                    compteId = compte.id,
                    collectionCompte = when (compte) {
                        is CompteCheque -> "comptes_cheque"
                        is CompteCredit -> "comptes_credit"
                        is CompteDette -> "comptes_dette"
                        is CompteInvestissement -> "comptes_investissement"
                        else -> "comptes_cheque"
                    },
                    enveloppeId = enveloppeId,
                    note = state.note.takeIf { it.isNotBlank() }
                )
                
                if (result.isSuccess) {
                    // Réinitialiser le formulaire après succès
                    _uiState.update { 
                        AjoutTransactionUiState(
                            isLoading = false,
                            comptesDisponibles = state.comptesDisponibles,
                            enveloppesDisponibles = state.enveloppesDisponibles
                        ).calculerValidite()
                    }
                    
                    // Recharger les données pour mettre à jour les soldes
                    chargerDonneesInitiales()
                } else {
                    throw result.exceptionOrNull() ?: Exception("Erreur lors de la sauvegarde")
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        estEnTrainDeSauvegarder = false,
                        messageErreur = e.message ?: "Erreur lors de la sauvegarde"
                    )
                }
            }
        }
    }

    /**
     * Efface le message d'erreur affiché.
     */
    fun effacerErreur() {
        _uiState.update { it.copy(messageErreur = null) }
    }
}