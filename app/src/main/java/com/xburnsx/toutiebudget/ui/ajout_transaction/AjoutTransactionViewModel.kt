// chemin/simule: /ui/ajout_transaction/AjoutTransactionViewModel.kt
// Dépendances: ViewModel, Repositories, Use Cases, Modèles de données, Coroutines

package com.xburnsx.toutiebudget.ui.ajout_transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.domain.usecases.EnregistrerTransactionUseCase
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
import java.util.Calendar
import java.util.Date
import java.time.LocalDate
import java.time.ZoneId

/**
 * ViewModel pour l'écran d'ajout de transactions.
 * Gère toute la logique de saisie, validation et sauvegarde des transactions.
 */
class AjoutTransactionViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val tiersRepository: TiersRepository,
    private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase,
    private val realtimeSyncService: RealtimeSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AjoutTransactionUiState())
    val uiState: StateFlow<AjoutTransactionUiState> = _uiState.asStateFlow()

    // Cache des données pour éviter les rechargements
    private var allComptes: List<Compte> = emptyList()
    private var allEnveloppes: List<Enveloppe> = emptyList()
    private var allAllocations: List<AllocationMensuelle> = emptyList()
    private var allCategories: List<Categorie> = emptyList()
    private var allTiers: List<Tiers> = emptyList()

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
                val resultTiers = tiersRepository.recupererTousLesTiers()

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
                allTiers = resultTiers.getOrNull() ?: emptyList()

                // Construire les enveloppes UI
                val enveloppesUi = construireEnveloppesUi()

                // Utiliser OrganisationEnveloppesUtils pour assurer un ordre cohérent
                val enveloppesGroupees = OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(allCategories, allEnveloppes)

                // Créer la map d'enveloppes filtrées en respectant l'ordre des catégories
                val enveloppesFiltrees = enveloppesGroupees.mapValues { (_, enveloppesCategorie) ->
                    enveloppesUi.filter { enveloppeUi ->
                        enveloppesCategorie.any { it.id == enveloppeUi.id }
                    }.sortedBy { enveloppeUi ->
                        // Trier selon l'ordre des enveloppes dans la catégorie
                        enveloppesCategorie.indexOfFirst { it.id == enveloppeUi.id }
                    }
                }
                
                // Mettre à jour l'état
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        comptesDisponibles = allComptes.filter { !it.estArchive },
                        enveloppesDisponibles = enveloppesUi,
                        enveloppesFiltrees = enveloppesFiltrees,
                        tiersDisponibles = allTiers
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
        println("[DEBUG] construireEnveloppesUi - Début avec ${allEnveloppes.size} enveloppes")

        return allEnveloppes.filter { !it.estArchive }.map { enveloppe ->
            val categorie = allCategories.find { it.id == enveloppe.categorieId }
            val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
            
            // Récupérer la couleur du compte source depuis l'allocation
            val compteSource = allocation?.compteSourceId?.let { compteId ->
                allComptes.find { it.id == compteId }
            }

            println("[DEBUG] construireEnveloppesUi - Enveloppe: ${enveloppe.nom} (ID: ${enveloppe.id})")
            println("[DEBUG] construireEnveloppesUi - Allocation trouvée: ${allocation?.id} pour enveloppeId: ${enveloppe.id}")
            println("[DEBUG] construireEnveloppesUi - Compte source: ${compteSource?.nom} - couleur: ${compteSource?.couleur}")

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = allocation?.solde ?: 0.0,
                depense = allocation?.depense ?: 0.0,
                objectif = enveloppe.objectifMontant,
                couleurProvenance = compteSource?.couleur, // ✅ VRAIE COULEUR DU COMPTE
                statutObjectif = StatutObjectif.GRIS
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
        println("[DEBUG] AjoutTransactionViewModel.onEnveloppeChanged - Nouvelle enveloppe sélectionnée: ${nouvelleEnveloppe?.nom} (ID: ${nouvelleEnveloppe?.id})")
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
     * Met à jour la date de la transaction.
     */
    fun onDateChanged(nouvelleDate: LocalDate) {
        _uiState.update { state ->
            state.copy(dateTransaction = nouvelleDate).calculerValidite()
        }
    }

    // === MÉTHODES POUR LA GESTION DES TIERS ===

    /**
     * Met à jour le texte saisi dans le champ Tiers.
     */
    fun onTexteTiersSaisiChange(nouveauTexte: String) {
        _uiState.update { state ->
            state.copy(texteTiersSaisi = nouveauTexte).calculerValidite()
        }
    }

    /**
     * Sélectionne un tiers existant.
     */
    fun onTiersSelectionne(tiers: Tiers) {
        _uiState.update { state ->
            state.copy(
                tiersSelectionne = tiers,
                texteTiersSaisi = tiers.nom
            ).calculerValidite()
        }
    }

    /**
     * Crée un nouveau tiers avec le nom fourni.
     */
    fun onCreerNouveauTiers(nomTiers: String) {
        if (nomTiers.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTiers = true) }

            try {
                val nouveauTiers = Tiers(nom = nomTiers.trim())
                val result = tiersRepository.creerTiers(nouveauTiers)

                if (result.isSuccess) {
                    val tiersCreated = result.getOrThrow()

                    // Mettre à jour le cache local
                    allTiers = allTiers + tiersCreated

                    // Mettre à jour l'état UI
                    _uiState.update { state ->
                        state.copy(
                            isLoadingTiers = false,
                            tiersDisponibles = allTiers,
                            tiersSelectionne = tiersCreated,
                            texteTiersSaisi = tiersCreated.nom
                        ).calculerValidite()
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isLoadingTiers = false,
                            messageErreur = "Erreur lors de la création du tiers: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isLoadingTiers = false,
                        messageErreur = "Erreur lors de la création du tiers: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Recherche des tiers selon le texte saisi.
     */
    fun rechercherTiers(texteRecherche: String) {
        if (texteRecherche.isBlank()) {
            _uiState.update { state ->
                state.copy(tiersDisponibles = allTiers)
            }
            return
        }

        viewModelScope.launch {
            try {
                val result = tiersRepository.rechercherTiersParNom(texteRecherche)

                if (result.isSuccess) {
                    val tiersTrouves = result.getOrThrow()
                    _uiState.update { state ->
                        state.copy(tiersDisponibles = tiersTrouves)
                    }
                }
            } catch (e: Exception) {
                // En cas d'erreur, utiliser le filtrage local
                val tiersFiltres = allTiers.filter { tiers ->
                    tiers.nom.contains(texteRecherche, ignoreCase = true)
                }
                _uiState.update { state ->
                    state.copy(tiersDisponibles = tiersFiltres)
                }
            }
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
                val montant = state.montant.toDoubleOrNull()?.div(100.0)
                    ?: throw Exception(VirementErrorMessages.General.MONTANT_INVALIDE)

                val compte = state.compteSelectionne
                    ?: throw Exception(VirementErrorMessages.ClavierBudget.COMPTE_NON_SELECTIONNE)

                // Pour les dépenses, vérifier qu'une enveloppe est sélectionnée
                val enveloppeId = if (state.typeTransaction == TypeTransaction.Depense) {
                    val enveloppeSelectionnee = state.enveloppeSelectionnee
                    println("[DEBUG] sauvegarderTransaction - Enveloppe sélectionnée: ${enveloppeSelectionnee?.nom} (ID: ${enveloppeSelectionnee?.id})")
                    enveloppeSelectionnee?.id
                        ?: throw Exception("Aucune enveloppe sélectionnée pour la dépense")
                } else {
                    null
                }
                
                println("[DEBUG] sauvegarderTransaction - Montant: $montant, Compte: ${compte.nom}, EnveloppeId: $enveloppeId")

                // Utiliser directement l'enum TypeTransaction
                val typeTransaction = state.typeTransaction
                
                // Convertir LocalDate en Date pour le UseCase
                val dateTransaction = state.dateTransaction.atStartOfDay(ZoneId.systemDefault()).toInstant().let { Date.from(it) }
                
                // Enregistrer la transaction
                println("[DEBUG] Avant appel executer - Texte tiers saisi: '${state.texteTiersSaisi}', Date: ${state.dateTransaction}")
                val result = enregistrerTransactionUseCase.executer(
                    typeTransaction = typeTransaction,
                    montant = montant,
                    compteId = compte.id,
                    collectionCompte = when (compte) {
                        is CompteCheque -> "comptes_cheques"
                        is CompteCredit -> "comptes_credits"
                        is CompteDette -> "comptes_dettes"
                        is CompteInvestissement -> "comptes_investissements"
                        else -> "comptes_cheques"
                    },
                    enveloppeId = enveloppeId,
                    tiersNom = state.texteTiersSaisi.takeIf { it.isNotBlank() } ?: "Transaction",
                    note = state.note.takeIf { it.isNotBlank() },
                    date = dateTransaction
                )
                
                if (result.isSuccess) {
                    _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true) }

                    // Émettre l'événement global de rafraîchissement du budget
                    BudgetEvents.refreshBudget.tryEmit(Unit)

                    // Déclencher la mise à jour des comptes dans les autres écrans
                    realtimeSyncService.declencherMiseAJourBudget()

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
                    _uiState.update { it.copy(estEnTrainDeSauvegarder = false, messageErreur = result.exceptionOrNull()?.message) }
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

    /**
     * Recharge les données depuis les repositories.
     * À appeler quand l'écran redevient visible ou après des modifications.
     */
    fun rechargerDonnees() {
        println("[DEBUG AJOUT] === Rechargement manuel des données ===")
        chargerDonneesInitiales()
    }
}