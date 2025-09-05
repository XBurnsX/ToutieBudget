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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.domain.usecases.EnregistrerTransactionUseCase
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
import com.xburnsx.toutiebudget.utils.DiagnosticConnexion
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionTransaction
import java.util.Calendar
import java.util.Date
import java.time.LocalDate
import java.time.ZoneId
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.IdGenerator

/**
 * ViewModel pour l'écran d'ajout de transactions.
 * Gère toute la logique de saisie, validation et sauvegarde des transactions.
 */
class AjoutTransactionViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val tiersRepository: TiersRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase,
    private val transactionRepository: TransactionRepository,
    private val pretPersonnelRepository: PretPersonnelRepository,
    private val argentService: com.xburnsx.toutiebudget.domain.services.ArgentService,
    private val realtimeSyncService: RealtimeSyncService,
    private val historiqueAllocationService: com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService
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
        
        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase pour les comptes
        viewModelScope.launch {
            realtimeSyncService.comptesUpdated.collectLatest {
                // DEBUG: AjoutTransactionViewModel reçoit l'événement comptesUpdated
                // Recharger les comptes quand ils sont modifiés
                chargerComptesSeulement()
            }
        }
        
        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase pour les enveloppes
        viewModelScope.launch {
            realtimeSyncService.budgetUpdated.collectLatest {
                // DEBUG: AjoutTransactionViewModel reçoit l'événement budgetUpdated
                // Recharger les enveloppes quand elles sont modifiées
                chargerEnveloppesSeulement()
            }
        }
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
                val messageErreur = when {
                    e.message?.contains("failed to connect", ignoreCase = true) == true -> 
                        "Impossible de se connecter au serveur. Vérifiez votre connexion réseau et que PocketBase est démarré."
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "Délai d'attente dépassé. Le serveur ne répond pas dans les délais."
                    e.message?.contains("unknown host", ignoreCase = true) == true -> 
                        "Serveur introuvable. Vérifiez l'adresse du serveur."
                    e.message?.contains("connection refused", ignoreCase = true) == true -> 
                        "Connexion refusée. Vérifiez que PocketBase est démarré sur le bon port."
                    else -> e.message ?: "Erreur lors du chargement des données"
                }
                
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        messageErreur = messageErreur
                    )
                }
                
                // Invalider le cache URL en cas d'erreur réseau
                if (e.message?.contains("connect", ignoreCase = true) == true || 
                    e.message?.contains("timeout", ignoreCase = true) == true) {
                    com.xburnsx.toutiebudget.di.UrlResolver.invaliderCache()
                }
            }
        }
    }

    /**
     * Charge seulement les comptes (pour les mises à jour temps réel).
     */
    private fun chargerComptesSeulement() {
        viewModelScope.launch {
            try {
                val resultComptes = compteRepository.recupererTousLesComptes()
                if (resultComptes.isSuccess) {
                    allComptes = resultComptes.getOrNull() ?: emptyList()
                    _uiState.update { state ->
                        state.copy(
                            comptesDisponibles = allComptes.filter { !it.estArchive }
                        )
                    }
                }
            } catch (e: Exception) {
                // Erreur lors du rechargement des comptes: ${e.message}
            }
        }
    }

    /**
     * Charge seulement les enveloppes (pour les mises à jour temps réel).
     */
    private fun chargerEnveloppesSeulement() {
        viewModelScope.launch {
            try {
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
                
                if (resultEnveloppes.isSuccess && resultCategories.isSuccess) {
                    allEnveloppes = resultEnveloppes.getOrNull() ?: emptyList()
                    allCategories = resultCategories.getOrNull() ?: emptyList()
                    allAllocations = resultAllocations.getOrNull() ?: emptyList()
                    
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
                    
                    _uiState.update { state ->
                        state.copy(
                            enveloppesDisponibles = enveloppesUi,
                            enveloppesFiltrees = enveloppesFiltrees
                        )
                    }
                }
            } catch (e: Exception) {
                // Erreur lors du rechargement des enveloppes: ${e.message}
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
            
            // Récupérer la couleur du compte source depuis l'allocation
            // 🎨 CORRECTION : Reset la couleur de provenance quand solde = 0
            val compteSource = if ((allocation?.solde ?: 0.0) > 0.001) {
                allocation?.compteSourceId?.let { compteId ->
                    allComptes.find { it.id == compteId }
                }
            } else {
                null // Reset la couleur quand solde = 0
            }

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = allocation?.solde ?: 0.0,
                depense = allocation?.depense ?: 0.0,
                alloue = allocation?.alloue ?: 0.0, // Alloué ce mois
                alloueCumulatif = allocation?.alloue ?: 0.0, // ← NOUVEAU : Pour simplifier, on utilise la même valeur
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
            val newState = state.copy(
                modeOperation = nouveauMode,
                enveloppeSelectionnee = null
            )
            // Si on entre en mode Paiement et qu'une dette est déjà sélectionnée, préremplir
            val dette = newState.comptePaiementSelectionne as? CompteDette
            if (nouveauMode == "Paiement" && dette?.paiementMinimum != null && dette.paiementMinimum > 0) {
                val cents = kotlin.math.round(dette.paiementMinimum * 100).toLong().toString()
                newState.copy(montant = cents).calculerValidite()
            } else {
                newState.calculerValidite()
            }
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
            state.copy(tiersUtiliser = nouveauTiers).calculerValidite()
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
     * Met à jour le compte de paiement sélectionné (pour le mode Paiement).
     */
    fun onComptePaiementChanged(nouveauCompte: Compte?) {
        _uiState.update { state ->
            val updated = state.copy(
                comptePaiementSelectionne = nouveauCompte,
                // Mettre automatiquement le nom du compte dans tiersUtiliser pour le mode Paiement
                tiersUtiliser = if (state.modeOperation == "Paiement" && nouveauCompte != null) {
                    nouveauCompte.nom
                } else {
                    state.tiersUtiliser
                }
            )
            val montantAuto = (nouveauCompte as? CompteDette)?.paiementMinimum
            if (state.modeOperation == "Paiement" && montantAuto != null && montantAuto > 0) {
                val cents = kotlin.math.round(montantAuto * 100).toLong().toString()
                updated.copy(montant = cents).calculerValidite()
            } else {
                updated.calculerValidite()
            }
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
            state.copy(tiersUtiliser = nouveauTexte).calculerValidite()
        }
    }

    /**
     * Sélectionne un tiers existant.
     */
    fun onTiersSelectionne(tiers: Tiers) {
        _uiState.update { state ->
            state.copy(
                tiersUtiliser = tiers.nom
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
                            tiersUtiliser = tiersCreated.nom
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
                val montant = state.montant.toDoubleOrNull()?.let { MoneyFormatter.roundAmount(it) }
                    ?: throw Exception(VirementErrorMessages.General.MONTANT_INVALIDE)

                val compte = state.compteSelectionne
                    ?: throw Exception(VirementErrorMessages.ClavierBudget.COMPTE_NON_SELECTIONNE)

                // Utiliser directement l'enum TypeTransaction
                val typeTransaction = state.typeTransaction
                
                // Gestion spéciale pour le mode Paiement
                if (state.modeOperation == "Paiement") {
                    val comptePaiement = state.comptePaiementSelectionne
                        ?: throw Exception("Aucune carte de crédit ou dette sélectionnée pour le paiement")
                    
                    // Effectuer le paiement de carte/dette
                    // DEBUG: Début paiement carte/dette - Compte: ${compte.nom}, Carte: ${comptePaiement.nom}, Montant: ${montant / 100.0}
                    
                    val result = argentService.effectuerPaiementCarteOuDette(
                        compteQuiPaieId = compte.id,
                        collectionCompteQuiPaie = when (compte) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissements"
                            else -> "comptes_cheques"
                        },
                        carteOuDetteId = comptePaiement.id,
                        collectionCarteOuDette = when (comptePaiement) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissements"
                            else -> "comptes_cheques"
                        },
                        montant = montant / 100.0, // Convertir centimes en dollars
                        note = comptePaiement.nom // Passer le nom de la dette/carte comme tiers
                    )
                    
                    // DEBUG: Résultat paiement: ${if (result.isSuccess) "SUCCÈS" else "ÉCHEC: ${result.exceptionOrNull()?.message}"}
                    
                    if (result.isSuccess) {
                        // Vérifier si la dette/carte est soldée après paiement
                        var message = "Votre paiement a été enregistré"
                        var detteTerminee = false
                        try {
                            val comptePaye = compteRepository.recupererCompteParId(comptePaiement.id, when (comptePaiement) {
                                is CompteCredit -> "comptes_credits"
                                is CompteDette -> "comptes_dettes"
                                is CompteCheque -> "comptes_cheques"
                                is CompteInvestissement -> "comptes_investissement"
                                else -> "comptes_cheques"
                            }).getOrNull()
                            when (comptePaiement) {
                                is CompteCredit -> {
                                    val soldeActuel = (comptePaye as? CompteCredit)?.solde ?: 0.0
                                    if (soldeActuel >= -0.01 && soldeActuel <= 0.01) {
                                        detteTerminee = true
                                    }
                                    message = "Votre paiement à la carte ${comptePaiement.nom} a été enregistré"
                                }
                                is CompteDette -> {
                                    val soldeActuel = (comptePaye as? CompteDette)?.solde ?: 0.0
                                    if (soldeActuel >= -0.01 && soldeActuel <= 0.01) {
                                        detteTerminee = true
                                    }
                                    message = "Votre paiement à la dette ${comptePaiement.nom} a été enregistré"
                                }
                                else -> { }
                            }
                        } catch (_: Exception) {}
                        _uiState.update { it.copy(
                            estEnTrainDeSauvegarder = false,
                            transactionReussie = true,
                            messageConfirmation = if (detteTerminee) message + "\nVotre dette est terminée, félicitations!" else message,
                            detteSoldee = detteTerminee
                        ) }

                        // Émettre l'événement global de rafraîchissement du budget
                        BudgetEvents.refreshBudget.tryEmit(Unit)

                        // Déclencher la mise à jour des comptes dans les autres écrans
                        realtimeSyncService.declencherMiseAJourBudget()

                        // Réinitialiser le formulaire après succès en PRÉSERVANT le message de confirmation
                        val lastMessage = _uiState.value.messageConfirmation
                        val lastDone = _uiState.value.detteSoldee
                        _uiState.update { 
                            AjoutTransactionUiState(
                                isLoading = false,
                                comptesDisponibles = state.comptesDisponibles,
                                enveloppesDisponibles = state.enveloppesDisponibles,
                                messageConfirmation = lastMessage,
                                detteSoldee = lastDone
                            ).calculerValidite()
                        }
                        
                        // Recharger les données pour mettre à jour les soldes
                        chargerDonneesInitiales()
                    } else {
                        _uiState.update { it.copy(estEnTrainDeSauvegarder = false, messageErreur = result.exceptionOrNull()?.message) }
                    }
                    return@launch
                }
                
                // Gestion spécifique PRÊT/EMPRUNT via collection pret_personnel
                if (state.modeOperation == "Prêt") {
                    val montantDollars = MoneyFormatter.roundAmount((state.montant.toDoubleOrNull() ?: 0.0) / 100.0)
                    val utilisateurId = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
                        ?: throw Exception("Utilisateur non connecté")
                    val nomTiers = state.tiersUtiliser
                    if (nomTiers.isBlank()) throw Exception("Nom du tiers requis")
                    val collectionCompte = when (compte) {
                        is CompteCheque -> "comptes_cheques"
                        is CompteCredit -> "comptes_credits"
                        is CompteDette -> "comptes_dettes"
                        is CompteInvestissement -> "comptes_investissements"
                        else -> "comptes_cheques"
                    }
                    val nowLocal = java.time.LocalDateTime.now()
                    val dateTx = Date.from(state.dateTransaction.atTime(nowLocal.hour, nowLocal.minute, nowLocal.second).atZone(ZoneId.systemDefault()).toInstant())
                    if (state.typePret == "Prêt accordé") {
                        val record = com.xburnsx.toutiebudget.data.modeles.PretPersonnel(
                            utilisateurId = utilisateurId,
                            nomTiers = nomTiers,
                            montantInitial = montantDollars,
                            solde = montantDollars,
                            type = com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET,
                            estArchive = false
                        )
                        val res = pretPersonnelRepository.creer(record)
                        val created = res.getOrElse { throw it }
                        // Débiter le compte et le prêt à placer
                        compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                            compteId = compte.id,
                            collectionCompte = collectionCompte,
                            variationSolde = -montantDollars,
                            mettreAJourPretAPlacer = true
                        )
                        // persister la transaction pour historique
                        val transactionPret = Transaction(
                            id = IdGenerator.generateId(),
                            utilisateurId = utilisateurId,
                            type = TypeTransaction.Pret,
                            montant = montantDollars,
                            date = dateTx,
                            compteId = compte.id,
                            collectionCompte = collectionCompte,
                            note = state.note.takeIf { it.isNotBlank() },
                            tiersUtiliser = nomTiers,
                            sousItems = "{\"pret_personnel_id\":\"${created.id}\"}"
                        )
                        // DEBUG: Tentative de création de transaction PRET: ${transactionPret}
                        val resultTransaction = transactionRepository.creerTransaction(transactionPret)
                        if (resultTransaction.isFailure) {
                            // ERREUR: Échec création transaction PRET: ${resultTransaction.exceptionOrNull()?.message}
                            throw resultTransaction.exceptionOrNull() ?: Exception("Échec création transaction PRET")
                        }
                        
                        // 📝 ENREGISTRER DANS L'HISTORIQUE
                        try {
                            val compte = compteRepository.getCompteById(state.compteSelectionne!!.id, state.compteSelectionne!!.collection)
                            if (compte is CompteCheque) {
                                android.util.Log.d("ToutieBudget", "🔄 AJOUT_TRANSACTION_VIEWMODEL : Tentative d'enregistrement dans l'historique pour transaction PRET")
                                // Récupérer le solde APRÈS la mise à jour
                                val soldeApres = compte.solde
                                val pretAPlacerApres = compte.pretAPlacer
                                // Calculer le solde AVANT (argent qui SORT : soldeAvant = soldeApres + montant)
                                val soldeAvant = soldeApres + montantDollars
                                val pretAPlacerAvant = pretAPlacerApres + montantDollars
                                
                                historiqueAllocationService.enregistrerTransactionDirecte(
                                    compte = compte,
                                    enveloppe = null,
                                    typeTransaction = "PRET",
                                    montant = -montantDollars, // Négatif car c'est un prêt sortant
                                    soldeAvant = soldeAvant,
                                    soldeApres = soldeApres,
                                    pretAPlacerAvant = pretAPlacerAvant,
                                    pretAPlacerApres = pretAPlacerApres,
                                    note = "Prêt à ${nomTiers}"
                                )
                                android.util.Log.d("ToutieBudget", "✅ AJOUT_TRANSACTION_VIEWMODEL : Enregistrement dans l'historique réussi (transaction PRET)")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ToutieBudget", "❌ AJOUT_TRANSACTION_VIEWMODEL : Erreur lors de l'enregistrement dans l'historique (transaction PRET): ${e.message}")
                        }
                        
                        // DEBUG: Transaction PRET créée avec succès: ${resultTransaction.getOrNull()}
                        _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true, messageConfirmation = "Prêt enregistré") }
                        BudgetEvents.refreshBudget.tryEmit(Unit)
                        realtimeSyncService.declencherMiseAJourBudget()
                        return@launch
                    } else if (state.typePret == "Remboursement reçu") {
                        // Remboursement reçu: distribuer sur TOUS les prêts actifs de ce tiers (FIFO)
                        val existants = pretPersonnelRepository.lister().getOrElse { emptyList() }
                            .filter { it.nomTiers.equals(nomTiers, true) && it.type == com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET && !it.estArchive && it.solde > 0 }
                            .sortedBy { it.created ?: "" } // plus ancien d'abord
                        var restant = montantDollars
                        if (existants.isEmpty()) throw Exception("Aucun prêt actif pour ce tiers")
                        // Créditer le compte et prêt à placer UNE fois pour le total
                        compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                            compteId = compte.id,
                            collectionCompte = collectionCompte,
                            variationSolde = montantDollars,
                            mettreAJourPretAPlacer = true
                        )
                        
                        // 📝 ENREGISTRER DANS L'HISTORIQUE APRÈS la mise à jour
                        try {
                            val compte = compteRepository.getCompteById(state.compteSelectionne!!.id, state.compteSelectionne!!.collection)
                            if (compte is CompteCheque) {
                                android.util.Log.d("ToutieBudget", "🔄 AJOUT_TRANSACTION_VIEWMODEL : Tentative d'enregistrement dans l'historique pour transaction REMBOURSEMENT_RECU")
                                // Récupérer le solde APRÈS la mise à jour
                                val soldeApres = compte.solde
                                val pretAPlacerApres = compte.pretAPlacer
                                // Calculer le solde AVANT (argent qui RENTRE : soldeAvant = soldeApres - montant)
                                val soldeAvant = soldeApres - montantDollars
                                val pretAPlacerAvant = pretAPlacerApres - montantDollars
                                
                                historiqueAllocationService.enregistrerTransactionDirecte(
                                    compte = compte,
                                    enveloppe = null,
                                    typeTransaction = "REMBOURSEMENT_RECU",
                                    montant = montantDollars, // Positif car c'est de l'argent qui arrive
                                    soldeAvant = soldeAvant,
                                    soldeApres = soldeApres,
                                    pretAPlacerAvant = pretAPlacerAvant,
                                    pretAPlacerApres = pretAPlacerApres,
                                    note = "Remboursement reçu de ${nomTiers}"
                                )
                                android.util.Log.d("ToutieBudget", "✅ AJOUT_TRANSACTION_VIEWMODEL : Enregistrement dans l'historique réussi (transaction REMBOURSEMENT_RECU)")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ToutieBudget", "❌ AJOUT_TRANSACTION_VIEWMODEL : Erreur lors de l'enregistrement dans l'historique (transaction REMBOURSEMENT_RECU): ${e.message}")
                        }
                        for (pret in existants) {
                            if (restant <= 0) break
                            val aPayer = kotlin.math.min(restant, pret.solde)
                            val nouveauSolde = kotlin.math.max(0.0, pret.solde - aPayer)
                            val archiver = kotlin.math.abs(nouveauSolde) < 0.005
                            val maj = pret.copy(solde = nouveauSolde, estArchive = archiver)
                            pretPersonnelRepository.mettreAJour(maj)
                            // Créer UNE transaction par portion appliquée à ce prêt
                            val sous = "{" + "\"pret_personnel_id\":\"${pret.id}\"}"
                            val transactionARendre = Transaction(
                                id = IdGenerator.generateId(),
                                utilisateurId = utilisateurId,
                                type = TypeTransaction.RemboursementRecu,
                                montant = aPayer,
                                date = dateTx,
                                compteId = compte.id,
                                collectionCompte = collectionCompte,
                                note = state.note.takeIf { it.isNotBlank() },
                                tiersUtiliser = nomTiers,
                                sousItems = sous
                            )
                            // DEBUG: Tentative de création de transaction: ${transactionARendre}
                            val resultTransaction = transactionRepository.creerTransaction(transactionARendre)
                            if (resultTransaction.isFailure) {
                                // ERREUR: Échec création transaction: ${resultTransaction.exceptionOrNull()?.message}
                                throw resultTransaction.exceptionOrNull() ?: Exception("Échec création transaction")
                            }
                            // DEBUG: Transaction créée avec succès: ${resultTransaction.getOrNull()}
                            restant -= aPayer
                        }
                        
                        // 📝 ENREGISTRER DANS L'HISTORIQUE
                        try {
                            val compte = compteRepository.getCompteById(state.compteSelectionne!!.id, state.compteSelectionne!!.collection)
                            if (compte is CompteCheque) {
                                android.util.Log.d("ToutieBudget", "🔄 AJOUT_TRANSACTION_VIEWMODEL : Tentative d'enregistrement dans l'historique pour transaction REMBOURSEMENT_RECU")
                                // Utiliser les vraies valeurs AVANT la mise à jour (solde actuel)
                                val soldeAvant = compte.solde
                                val soldeApres = compte.solde + montantDollars
                                val pretAPlacerAvant = compte.pretAPlacer
                                val pretAPlacerApres = compte.pretAPlacer + montantDollars
                                
                                historiqueAllocationService.enregistrerTransactionDirecte(
                                    compte = compte,
                                    enveloppe = null,
                                    typeTransaction = "REMBOURSEMENT_RECU",
                                    montant = montantDollars, // Positif car c'est de l'argent qui arrive
                                    soldeAvant = soldeAvant,
                                    soldeApres = soldeApres,
                                    pretAPlacerAvant = pretAPlacerAvant,
                                    pretAPlacerApres = pretAPlacerApres, // Le prêt à placer augmente aussi !
                                    note = "Remboursement reçu de ${nomTiers}"
                                )
                                android.util.Log.d("ToutieBudget", "✅ AJOUT_TRANSACTION_VIEWMODEL : Enregistrement dans l'historique réussi (transaction REMBOURSEMENT_RECU)")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ToutieBudget", "❌ AJOUT_TRANSACTION_VIEWMODEL : Erreur lors de l'enregistrement dans l'historique (transaction REMBOURSEMENT_RECU): ${e.message}")
                        }
                        
                        _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true, messageConfirmation = "Remboursement reçu enregistré") }
                        BudgetEvents.refreshBudget.tryEmit(Unit)
                        realtimeSyncService.declencherMiseAJourBudget()
                        return@launch
                    }
                }
                if (state.modeOperation == "Emprunt") {
                    val montantDollars = MoneyFormatter.roundAmount((state.montant.toDoubleOrNull() ?: 0.0) / 100.0)
                    val utilisateurId = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
                        ?: throw Exception("Utilisateur non connecté")
                    val nomTiers = state.tiersUtiliser
                    if (nomTiers.isBlank()) throw Exception("Nom du tiers requis")
                    val collectionCompte = when (compte) {
                        is CompteCheque -> "comptes_cheques"
                        is CompteCredit -> "comptes_credits"
                        is CompteDette -> "comptes_dettes"
                        is CompteInvestissement -> "comptes_investissements"
                        else -> "comptes_cheques"
                    }
                    val nowLocal2 = java.time.LocalDateTime.now()
                    val dateTx2 = Date.from(state.dateTransaction.atTime(nowLocal2.hour, nowLocal2.minute, nowLocal2.second).atZone(ZoneId.systemDefault()).toInstant())
                    if (state.typeDette == "Dette contractée") {
                        val record = com.xburnsx.toutiebudget.data.modeles.PretPersonnel(
                            utilisateurId = utilisateurId,
                            nomTiers = nomTiers,
                            montantInitial = montantDollars,
                            solde = -montantDollars,
                            type = com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.DETTE,
                            estArchive = false
                        )
                        val res = pretPersonnelRepository.creer(record)
                        val created = res.getOrElse { throw it }
                        // Créditer le compte et le prêt à placer
                        compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                            compteId = compte.id,
                            collectionCompte = collectionCompte,
                            variationSolde = montantDollars,
                            mettreAJourPretAPlacer = true
                        )
                        transactionRepository.creerTransaction(
                            Transaction(
                                id = IdGenerator.generateId(),
                                utilisateurId = utilisateurId,
                                type = TypeTransaction.Emprunt,
                                montant = montantDollars,
                                date = dateTx2,
                                compteId = compte.id,
                                collectionCompte = collectionCompte,
                                note = state.note.takeIf { it.isNotBlank() },
                                tiersUtiliser = nomTiers,
                                sousItems = "{\"pret_personnel_id\":\"${created.id}\"}"
                            )
                        )
                        
                        // Créditer le compte et le prêt à placer
                        compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                            compteId = compte.id,
                            collectionCompte = collectionCompte,
                            variationSolde = montantDollars,
                            mettreAJourPretAPlacer = true
                        )
                        
                        // 📝 ENREGISTRER DANS L'HISTORIQUE APRÈS la mise à jour
                        try {
                            val compte = compteRepository.getCompteById(state.compteSelectionne!!.id, state.compteSelectionne!!.collection)
                            if (compte is CompteCheque) {
                                android.util.Log.d("ToutieBudget", "🔄 AJOUT_TRANSACTION_VIEWMODEL : Tentative d'enregistrement dans l'historique pour transaction EMPRUNT")
                                // Récupérer le solde APRÈS la mise à jour
                                val soldeApres = compte.solde
                                val pretAPlacerApres = compte.pretAPlacer
                                // Calculer le solde AVANT (argent qui RENTRE : soldeAvant = soldeApres - montant)
                                val soldeAvant = soldeApres - montantDollars
                                val pretAPlacerAvant = pretAPlacerApres - montantDollars
                                
                                historiqueAllocationService.enregistrerTransactionDirecte(
                                    compte = compte,
                                    enveloppe = null,
                                    typeTransaction = "EMPRUNT",
                                    montant = montantDollars, // Positif car c'est de l'argent qui arrive
                                    soldeAvant = soldeAvant,
                                    soldeApres = soldeApres,
                                    pretAPlacerAvant = pretAPlacerAvant,
                                    pretAPlacerApres = pretAPlacerApres,
                                    note = "Emprunt de ${nomTiers}"
                                )
                                android.util.Log.d("ToutieBudget", "✅ AJOUT_TRANSACTION_VIEWMODEL : Enregistrement dans l'historique réussi (transaction EMPRUNT)")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ToutieBudget", "❌ AJOUT_TRANSACTION_VIEWMODEL : Erreur lors de l'enregistrement dans l'historique (transaction EMPRUNT): ${e.message}")
                        }
                        
                        _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true, messageConfirmation = "Emprunt enregistré") }
                        BudgetEvents.refreshBudget.tryEmit(Unit)
                        realtimeSyncService.declencherMiseAJourBudget()
                        return@launch
                    } else if (state.typeDette == "Remboursement donné") {
                        // Remboursement donné: distribuer sur TOUTES les dettes actives de ce tiers (FIFO)
                        val existants = pretPersonnelRepository.lister().getOrElse { emptyList() }
                            .filter { it.nomTiers.equals(nomTiers, true) && it.type == com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.DETTE && !it.estArchive && it.solde < 0 }
                            .sortedBy { it.created ?: "" }
                        if (existants.isEmpty()) throw Exception("Aucun emprunt actif pour ce tiers")
                        var restant = montantDollars
                        // Débiter le compte UNE fois
                        compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                            compteId = compte.id,
                            collectionCompte = collectionCompte,
                            variationSolde = -montantDollars,
                            mettreAJourPretAPlacer = true
                        )
                        for (det in existants) {
                            if (restant <= 0) break
                            val besoin = kotlin.math.abs(det.solde)
                            val aPayer = kotlin.math.min(restant, besoin)
                            val nouveauSolde = -(kotlin.math.max(0.0, besoin - aPayer))
                            val archiver = kotlin.math.abs(nouveauSolde) < 0.005
                            val maj = det.copy(solde = nouveauSolde, estArchive = archiver)
                            pretPersonnelRepository.mettreAJour(maj)
                            val sous = "{" + "\"pret_personnel_id\":\"${det.id}\"" + "}"
                            transactionRepository.creerTransaction(
                                Transaction(
                                    id = IdGenerator.generateId(),
                                    utilisateurId = utilisateurId,
                                    type = TypeTransaction.RemboursementDonne,
                                    montant = aPayer,
                                    date = dateTx2,
                                    compteId = compte.id,
                                    collectionCompte = collectionCompte,
                                    note = state.note.takeIf { it.isNotBlank() },
                                    tiersUtiliser = nomTiers,
                                    sousItems = sous
                                )
                            )
                            restant -= aPayer
                        }
                        
                        // Débiter le compte UNE fois
                        compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                            compteId = compte.id,
                            collectionCompte = collectionCompte,
                            variationSolde = -montantDollars,
                            mettreAJourPretAPlacer = true
                        )
                        
                        // 📝 ENREGISTRER DANS L'HISTORIQUE APRÈS la mise à jour
                        try {
                            val compte = compteRepository.getCompteById(state.compteSelectionne!!.id, state.compteSelectionne!!.collection)
                            if (compte is CompteCheque) {
                                android.util.Log.d("ToutieBudget", "🔄 AJOUT_TRANSACTION_VIEWMODEL : Tentative d'enregistrement dans l'historique pour transaction REMBOURSEMENT_DONNE")
                                // Récupérer le solde APRÈS la mise à jour
                                val soldeApres = compte.solde
                                val pretAPlacerApres = compte.pretAPlacer
                                // Calculer le solde AVANT (argent qui SORT : soldeAvant = soldeApres + montant)
                                val soldeAvant = soldeApres + montantDollars
                                val pretAPlacerAvant = pretAPlacerApres + montantDollars
                                
                                historiqueAllocationService.enregistrerTransactionDirecte(
                                    compte = compte,
                                    enveloppe = null,
                                    typeTransaction = "REMBOURSEMENT_DONNE",
                                    montant = -montantDollars, // Négatif car c'est de l'argent qui sort
                                    soldeAvant = soldeAvant,
                                    soldeApres = soldeApres,
                                    pretAPlacerAvant = pretAPlacerAvant,
                                    pretAPlacerApres = pretAPlacerApres,
                                    note = "Remboursement donné à ${nomTiers}"
                                )
                                android.util.Log.d("ToutieBudget", "✅ AJOUT_TRANSACTION_VIEWMODEL : Enregistrement dans l'historique réussi (transaction REMBOURSEMENT_DONNE)")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ToutieBudget", "❌ AJOUT_TRANSACTION_VIEWMODEL : Erreur lors de l'enregistrement dans l'historique (transaction REMBOURSEMENT_DONNE): ${e.message}")
                        }
                        
                        _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true, messageConfirmation = "Remboursement donné enregistré") }
                        BudgetEvents.refreshBudget.tryEmit(Unit)
                        realtimeSyncService.declencherMiseAJourBudget()
                        return@launch
                    }
                }

                // Convertir LocalDate en Date avec l'heure locale actuelle du téléphone
                // pour utiliser l'heure réelle de création de la transaction
                val maintenant = java.time.LocalDateTime.now()
                val dateTransaction = Date.from(state.dateTransaction.atTime(maintenant.hour, maintenant.minute, maintenant.second).atZone(ZoneId.systemDefault()).toInstant())
                
                // Vérifier si c'est une transaction fractionnée
                if (state.fractionnementEffectue && state.fractionsSauvegardees.isNotEmpty()) {
                    // Transaction fractionnée
                    val fractions = state.fractionsSauvegardees
                    
                    // Convertir les fractions en JSON pour sousItems
                        val sousItems = fractions.mapIndexed { index, fraction ->
                        // Récupérer ou créer l'allocation mensuelle pour cette enveloppe
                        val allocationActuelle = allocationMensuelleRepository.recupererOuCreerAllocation(
                            enveloppeId = fraction.enveloppeId,
                            mois = dateTransaction // Mois de la transaction
                        )
                        
                                                 // Le montant est en centimes, on le convertit en dollars pour le JSON
                         val montantEnDollars = MoneyFormatter.roundAmount(fraction.montant / 100.0)
                        
                        // Déterminer la collection du compte
                        val collectionCompte = when (compte) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissements"
                            else -> "comptes_cheques"
                        }
                        
                        // Mettre à jour l'allocation avec le compte source si pas déjà défini
                        if (allocationActuelle.compteSourceId == null) {
                            val allocationMiseAJour = allocationActuelle.copy(
                                compteSourceId = compte.id,
                                collectionCompteSource = collectionCompte
                            )
                            allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)
                        }
                        
                        // Récupérer le nom de l'enveloppe pour la description
                        val nomEnveloppe = allEnveloppes.find { it.id == fraction.enveloppeId }?.nom ?: "Enveloppe"
                        
                        mapOf(
                            "id" to "temp_${index + 1}",
                            "description" to nomEnveloppe, // TOUJOURS utiliser le nom de l'enveloppe
                            "enveloppeId" to fraction.enveloppeId,
                            "montant" to montantEnDollars, // Montant en dollars pour le JSON
                            "allocation_mensuelle_id" to allocationActuelle.id, // Lier à l'allocation
                            "transactionParenteId" to null
                        )
                    }
                    
                    val gson = com.google.gson.Gson()
                    val sousItemsJson = gson.toJson(sousItems)

                    // Créer la transaction principale avec estFractionnee = true
                    val transactionPrincipale = enregistrerTransactionUseCase.executer(
                        typeTransaction = typeTransaction,
                        montant = MoneyFormatter.roundAmount(montant / 100.0), // Convertir centimes en dollars et arrondir
                        compteId = compte.id,
                        collectionCompte = when (compte) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissements"
                            else -> "comptes_cheques"
                        },
                        enveloppeId = null, // Pas d'enveloppe pour la transaction principale
                        tiersUtiliser = state.tiersUtiliser.takeIf { it.isNotBlank() } ?: "Transaction fractionnée",
                        note = state.note.takeIf { it.isNotBlank() },
                        date = dateTransaction,
                        estFractionnee = true,
                        sousItems = sousItemsJson
                    )

                    if (transactionPrincipale.isSuccess) {
                        // Mettre à jour les allocations mensuelles en utilisant les données du JSON
                        for (sousItem in sousItems) {
                            val allocationId = sousItem["allocation_mensuelle_id"] as String
                                                         val montantEnDollars = sousItem["montant"] as Double
                            
                            // Récupérer l'allocation par son ID
                            val resultAllocation = enveloppeRepository.recupererAllocationParId(allocationId)
                            
                            if (resultAllocation.isSuccess) {
                                val allocationActuelle = resultAllocation.getOrThrow()
                                                                 // Mettre à jour l'allocation avec le montant en dollars (déjà en dollars dans le JSON)
                                 val nouvelleAllocation = allocationActuelle.copy(
                                     depense = allocationActuelle.depense + montantEnDollars,
                                     solde = allocationActuelle.solde - montantEnDollars
                                 )
                                
                                allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocation)
                            }
                        }
                        
                        val message = when (typeTransaction) {
                            TypeTransaction.Depense -> "Votre dépense a été enregistrée"
                            TypeTransaction.Revenu -> "Votre revenu a été enregistré"
                            else -> "Votre transaction a été enregistrée"
                        }
                        _uiState.update { it.copy(
                            estEnTrainDeSauvegarder = false,
                            transactionReussie = true,
                            messageConfirmation = message
                        ) }

                        // Émettre l'événement global de rafraîchissement du budget
                        BudgetEvents.refreshBudget.tryEmit(Unit)

                        // Déclencher la mise à jour des comptes dans les autres écrans
                        realtimeSyncService.declencherMiseAJourBudget()

                        // Réinitialiser le formulaire après succès en PRÉSERVANT le message de confirmation
                        val lastMessage = _uiState.value.messageConfirmation
                        _uiState.update { 
                            AjoutTransactionUiState(
                                isLoading = false,
                                comptesDisponibles = state.comptesDisponibles,
                                enveloppesDisponibles = state.enveloppesDisponibles,
                                messageConfirmation = lastMessage
                            ).calculerValidite()
                        }
                        
                        // Recharger les données pour mettre à jour les soldes
                        chargerDonneesInitiales()
                    } else {
                        _uiState.update { 
                            it.copy(
                                estEnTrainDeSauvegarder = false,
                                messageErreur = transactionPrincipale.exceptionOrNull()?.message
                            )
                        }
                    }
                } else {
                    // Transaction normale (non fractionnée)
                    // Gestion spécifique Remboursements (associer au prêt via sous_items et MAJ soldes prêts)
                    if (typeTransaction == TypeTransaction.RemboursementRecu || typeTransaction == TypeTransaction.RemboursementDonne) {
                        val montantDollars = MoneyFormatter.roundAmount((state.montant.toDoubleOrNull() ?: 0.0) / 100.0)
                        val nomTiers = state.tiersUtiliser
                        if (nomTiers.isBlank()) throw Exception("Nom du tiers requis pour le remboursement")
                        val collectionCompte = when (compte) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissements"
                            else -> "comptes_cheques"
                        }

                        if (typeTransaction == TypeTransaction.RemboursementRecu) {
                            val existants = pretPersonnelRepository.lister().getOrElse { emptyList() }
                                .filter { it.nomTiers.equals(nomTiers, true) && it.type == com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET && !it.estArchive && it.solde > 0 }
                                .sortedBy { it.created ?: "" }
                            if (existants.isEmpty()) throw Exception("Aucun prêt actif pour ce tiers")
                            // Créditer le compte une seule fois
                            compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                                compteId = compte.id,
                                collectionCompte = collectionCompte,
                                variationSolde = montantDollars,
                                mettreAJourPretAPlacer = true
                            )
                            var restant = montantDollars
                            for (pret in existants) {
                                if (restant <= 0) break
                                val aPayer = kotlin.math.min(restant, pret.solde)
                                val nouveauSolde = kotlin.math.max(0.0, pret.solde - aPayer)
                                val archiver = kotlin.math.abs(nouveauSolde) < 0.005
                                pretPersonnelRepository.mettreAJour(pret.copy(solde = nouveauSolde, estArchive = archiver))
                                val sous = "{" + "\"pret_personnel_id\":\"${pret.id}\"" + "}"
                                transactionRepository.creerTransaction(
                                    Transaction(
                                        type = TypeTransaction.RemboursementRecu,
                                        montant = aPayer,
                                        date = dateTransaction,
                                        compteId = compte.id,
                                        collectionCompte = collectionCompte,
                                        note = state.note.takeIf { it.isNotBlank() },
                                        tiersUtiliser = nomTiers,
                                        sousItems = sous
                                    )
                                )
                                restant -= aPayer
                            }
                            _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true, messageConfirmation = "Remboursement reçu enregistré") }
                            BudgetEvents.refreshBudget.tryEmit(Unit)
                            realtimeSyncService.declencherMiseAJourBudget()
                            val lastMessage = _uiState.value.messageConfirmation
                            _uiState.update {
                                AjoutTransactionUiState(
                                    isLoading = false,
                                    comptesDisponibles = state.comptesDisponibles,
                                    enveloppesDisponibles = state.enveloppesDisponibles,
                                    messageConfirmation = lastMessage
                                ).calculerValidite()
                            }
                            chargerDonneesInitiales()
                            return@launch
                        } else if (typeTransaction == TypeTransaction.RemboursementDonne) {
                            val existants = pretPersonnelRepository.lister().getOrElse { emptyList() }
                                .filter { it.nomTiers.equals(nomTiers, true) && it.type == com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.DETTE && !it.estArchive && it.solde < 0 }
                                .sortedBy { it.created ?: "" }
                            if (existants.isEmpty()) throw Exception("Aucun emprunt actif pour ce tiers")
                            // Débiter le compte une seule fois
                            compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                                compteId = compte.id,
                                collectionCompte = collectionCompte,
                                variationSolde = -montantDollars,
                                mettreAJourPretAPlacer = true
                            )
                            var restant = montantDollars
                            for (det in existants) {
                                if (restant <= 0) break
                                val besoin = kotlin.math.abs(det.solde)
                                val aPayer = kotlin.math.min(restant, besoin)
                                val nouveauSolde = -(kotlin.math.max(0.0, besoin - aPayer))
                                val archiver = kotlin.math.abs(nouveauSolde) < 0.005
                                pretPersonnelRepository.mettreAJour(det.copy(solde = nouveauSolde, estArchive = archiver))
                                val sous = "{" + "\"pret_personnel_id\":\"${det.id}\"" + "}"
                                transactionRepository.creerTransaction(
                                    Transaction(
                                        type = TypeTransaction.RemboursementDonne,
                                        montant = aPayer,
                                        date = dateTransaction,
                                        compteId = compte.id,
                                        collectionCompte = collectionCompte,
                                        note = state.note.takeIf { it.isNotBlank() },
                                        tiersUtiliser = nomTiers,
                                        sousItems = sous
                                    )
                                )
                                restant -= aPayer
                            }
                            _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true, messageConfirmation = "Remboursement donné enregistré") }
                            BudgetEvents.refreshBudget.tryEmit(Unit)
                            realtimeSyncService.declencherMiseAJourBudget()
                            val lastMessage = _uiState.value.messageConfirmation
                            _uiState.update {
                                AjoutTransactionUiState(
                                    isLoading = false,
                                    comptesDisponibles = state.comptesDisponibles,
                                    enveloppesDisponibles = state.enveloppesDisponibles,
                                    messageConfirmation = lastMessage
                                ).calculerValidite()
                            }
                            chargerDonneesInitiales()
                            return@launch
                        }
                    }

                    // AUTO-LIAGE REMBOURSEMENT si l'utilisateur a choisi Revenu/Depense au lieu de RemboursementRecu/Donne
                    run {
                        val nomTiers = state.tiersUtiliser
                        val montantDollars = MoneyFormatter.roundAmount((state.montant.toDoubleOrNull() ?: 0.0) / 100.0)
                        val collectionCompte = when (compte) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissements"
                            else -> "comptes_cheques"
                        }
                        // 🚨 DÉSACTIVÉ : Cette logique auto-liage transforme les REVENUS en REMBOURSEMENTRECU !
                        // if (typeTransaction == TypeTransaction.Revenu && nomTiers.isNotBlank()) {
                        //     val prets = pretPersonnelRepository.lister().getOrElse { emptyList() }
                        //         .filter { it.nomTiers.equals(nomTiers, true) && it.type == com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET && !it.estArchive && it.solde > 0 }
                        //         .sortedBy { it.created ?: "" }
                        //     if (prets.isNotEmpty()) {
                        //         // Créditer une fois
                        //         compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                        //             compteId = compte.id,
                        //             collectionCompte = collectionCompte,
                        //             variationSolde = montantDollars,
                        //             mettreAJourPretAPlacer = true
                        //         )
                        //         var restant = montantDollars
                        //         for (pret in prets) {
                        //             if (restant <= 0) break
                        //             val aPayer = kotlin.math.min(restant, pret.solde)
                        //             val nouveauSolde = kotlin.math.max(0.0, pret.solde - aPayer)
                        //             val archiver = kotlin.math.abs(nouveauSolde) < 0.005
                        //             pretPersonnelRepository.mettreAJour(pret.copy(solde = nouveauSolde, estArchive = archiver))
                        //         val sous = "{" + "\"pret_personnel_id\":\"${pret.id}\"" + "}"
                        //         transactionRepository.creerTransaction(
                        //             Transaction(
                        //                 type = TypeTransaction.RemboursementRecu,
                        //                 montant = aPayer,
                        //                 date = dateTransaction,
                        //                 compteId = compte.id,
                        //                 collectionCompte = collectionCompte,
                        //                 note = state.note.takeIf { it.isNotBlank() },
                        //                 tiersUtiliser = nomTiers,
                        //                 sousItems = sous
                        //             )
                        //         )
                        //         restant -= aPayer
                        //     }
                        //     _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true, messageConfirmation = "Remboursement reçu enregistré") }
                        //     BudgetEvents.refreshBudget.tryEmit(Unit)
                        //     realtimeSyncService.declencherMiseAJourBudget()
                        //     val lastMessage = _uiState.value.messageConfirmation
                        //     _uiState.update {
                        //         AjoutTransactionUiState(
                        //             isLoading = false,
                        //             comptesDisponibles = state.comptesDisponibles,
                        //             enveloppesDisponibles = state.enveloppesDisponibles,
                        //             messageConfirmation = lastMessage
                        //         ).calculerValidite()
                        //     }
                        //     chargerDonneesInitiales()
                        //     return@launch
                        // }
                        // }
                        // 🚨 DÉSACTIVÉ : Cette logique auto-liage transforme les DÉPENSES en REMBOURSEMENTDONNE !
                        // if (typeTransaction == TypeTransaction.Depense && nomTiers.isNotBlank()) {
                        //     val dettes = pretPersonnelRepository.lister().getOrElse { emptyList() }
                        //         .filter { it.nomTiers.equals(nomTiers, true) && it.type == com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.DETTE && !it.estArchive && it.solde < 0 }
                        //         .sortedBy { it.created ?: "" }
                            // 🚨 DÉSACTIVÉ : Cette logique auto-liage transforme les DÉPENSES en REMBOURSEMENTDONNE !
                            // if (dettes.isNotEmpty()) {
                            //     // Débiter une fois
                            //     compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                            //         compteId = compte.id,
                            //         collectionCompte = collectionCompte,
                            //         variationSolde = -montantDollars,
                            //         mettreAJourPretAPlacer = true
                            //     )
                            //     var restant = montantDollars
                            //     for (det in dettes) {
                            //         if (restant <= 0) break
                            //         val besoin = kotlin.math.abs(det.solde)
                            //         val aPayer = kotlin.math.min(restant, besoin)
                            //         val nouveauSolde = -(kotlin.math.max(0.0, besoin - aPayer))
                            //         val archiver = kotlin.math.abs(nouveauSolde) < 0.005
                            //         pretPersonnelRepository.mettreAJour(det.copy(solde = nouveauSolde, estArchive = archiver))
                            //         val sous = "{" + "\"pret_personnel_id\":\"${det.id}\"" + "}"
                            //         transactionRepository.creerTransaction(
                            //             Transaction(
                            //                 type = TypeTransaction.RemboursementDonne,
                            //                 montant = aPayer,
                            //                 date = dateTransaction,
                            //                 compteId = compte.id,
                            //                 collectionCompte = collectionCompte,
                            //                 note = state.note.takeIf { it.isNotBlank() },
                            //                 tiersUtiliser = nomTiers,
                            //                 sousItems = sous
                            //             )
                            //         )
                            //         restant -= aPayer
                            //     }
                            //     _uiState.update { it.copy(estEnTrainDeSauvegarder = false, transactionReussie = true, messageConfirmation = "Remboursement donné enregistré") }
                            //     BudgetEvents.refreshBudget.tryEmit(Unit)
                            //     realtimeSyncService.declencherMiseAJourBudget()
                            //     val lastMessage = _uiState.value.messageConfirmation
                            //     _uiState.update {
                            //         AjoutTransactionUiState(
                            //             isLoading = false,
                            //             comptesDisponibles = state.comptesDisponibles,
                            //             enveloppesDisponibles = state.enveloppesDisponibles,
                            //             messageConfirmation = lastMessage
                            //         ).calculerValidite()
                            //     }
                            //     chargerDonneesInitiales()
                            //     return@launch
                            // }
                        // }
                    }

                    // Pour les dépenses standard: vérifier enveloppe
                    val enveloppeId = if (state.typeTransaction == TypeTransaction.Depense) {
                        val enveloppeSelectionnee = state.enveloppeSelectionnee
                        // 🔍 LOG DEBUG : Tracer l'enveloppe sélectionnée
                                // 🔍 DEBUG - Type de transaction: ${state.typeTransaction}
        // 🔍 DEBUG - Enveloppe sélectionnée: ${enveloppeSelectionnee?.nom} (ID: ${enveloppeSelectionnee?.id})
        // 🔍 DEBUG - Solde de l'enveloppe: ${enveloppeSelectionnee?.solde}
                        
                        enveloppeSelectionnee?.id
                            ?: throw Exception("Aucune enveloppe sélectionnée pour la dépense")
                    } else {
                        null
                    }

                    // 🔍 LOG DEBUG : Tracer les paramètres de la transaction
                    // 🔍 DEBUG - Montant de la transaction: ${state.montant} centimes (${state.montant.toDoubleOrNull()?.div(100.0)} dollars)
                    // 🔍 DEBUG - Compte sélectionné: ${compte.nom} (ID: ${compte.id})
                    // 🔍 DEBUG - Collection du compte: ${when (compte) { is CompteCheque -> "comptes_cheques"; is CompteCredit -> "comptes_credits"; is CompteDette -> "comptes_dettes"; is CompteInvestissement -> "comptes_investissements"; else -> "comptes_cheques" }}

                    // Enregistrer la transaction standard
                    val result = enregistrerTransactionUseCase.executer(
                        typeTransaction = typeTransaction,
                        montant = montant / 100.0,
                        compteId = compte.id,
                        collectionCompte = when (compte) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissements"
                            else -> "comptes_cheques"
                        },
                        enveloppeId = enveloppeId,
                        tiersUtiliser = state.tiersUtiliser.takeIf { it.isNotBlank() } ?: "Transaction",
                        note = state.note.takeIf { it.isNotBlank() },
                        date = dateTransaction
                    )

                    // 🔍 LOG DEBUG : Tracer le résultat de la création
                    if (result.isSuccess) {
                        // 🔍 DEBUG - Transaction créée avec succès!
                    } else {
                        // 🔍 DEBUG - Erreur lors de la création de la transaction: ${result.exceptionOrNull()?.message}
                    }

                    if (result.isSuccess) {
                        val message = when (typeTransaction) {
                            TypeTransaction.Depense -> "Votre dépense a été enregistrée"
                            TypeTransaction.Revenu -> "Votre revenu a été enregistré"
                            else -> "Votre transaction a été enregistrée"
                        }
                        _uiState.update { it.copy(
                            estEnTrainDeSauvegarder = false,
                            transactionReussie = true,
                            messageConfirmation = message
                        ) }

                        BudgetEvents.refreshBudget.tryEmit(Unit)
                        realtimeSyncService.declencherMiseAJourBudget()
                        val lastMessage = _uiState.value.messageConfirmation
                        _uiState.update {
                            AjoutTransactionUiState(
                                isLoading = false,
                                comptesDisponibles = state.comptesDisponibles,
                                enveloppesDisponibles = state.enveloppesDisponibles,
                                messageConfirmation = lastMessage
                            ).calculerValidite()
                        }
                        chargerDonneesInitiales()
                    } else {
                        _uiState.update { it.copy(estEnTrainDeSauvegarder = false, messageErreur = result.exceptionOrNull()?.message) }
                    }
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
     * Ouvre l'interface de fractionnement de transaction.
     */
    fun ouvrirFractionnement() {
        // S'assurer que les enveloppes sont chargées
        if (_uiState.value.enveloppesDisponibles.isEmpty()) {
            // Recharger les données si les enveloppes ne sont pas disponibles
            chargerDonneesInitiales()
        }
        _uiState.update { it.copy(estEnModeFractionnement = true) }
    }

    /**
     * Ferme l'interface de fractionnement de transaction.
     */
    fun fermerFractionnement() {
        _uiState.update { it.copy(estEnModeFractionnement = false) }
    }

    /**
     * Confirme le fractionnement de la transaction.
     */
    fun confirmerFractionnement(fractions: List<FractionTransaction>) {
        // Les montants sont maintenant en dollars dans le dialog
        _uiState.update { 
            it.copy(
                estEnModeFractionnement = false,
                fractionnementEffectue = true, // Marquer que le fractionnement a été effectué
                fractionsSauvegardees = fractions // Sauvegarder les fractions pour les réutiliser
            ).calculerValidite() // Recalculer la validité pour activer le bouton Enregistrer
        }
    }

    /**
     * Efface le message d'erreur affiché.
     */
    fun effacerErreur() {
        _uiState.update { it.copy(messageErreur = null) }
    }

    /**
     * Efface le message de confirmation.
     */
    fun effacerConfirmation() {
        _uiState.update { it.copy(messageConfirmation = null, detteSoldee = false) }
    }

    /**
     * Recharge les données depuis les repositories.
     * À appeler quand l'écran redevient visible ou après des modifications.
     */
    fun rechargerDonnees() {
        chargerDonneesInitiales()
    }

    /**
     * Effectue un diagnostic de connexion pour déboguer les problèmes réseau
     */
    fun diagnostiquerConnexion() {
        viewModelScope.launch {
            try {
                val rapport = DiagnosticConnexion.diagnostiquerConnexion()
                
                _uiState.update { state ->
                    state.copy(
                        messageErreur = "Diagnostic terminé. Vérifiez les logs pour plus de détails."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        messageErreur = "Erreur lors du diagnostic: ${e.message}"
                    )
                }
            }
        }
    }
}
