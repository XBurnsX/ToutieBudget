// chemin/simule: /ui/ajout_transaction/AjoutTransactionViewModel.kt
// Dépendances: ViewModel, Repositories, Use Cases, Modèles de données, Coroutines

package com.xburnsx.toutiebudget.ui.ajout_transaction

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte

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
    private val historiqueAllocationService: com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService,
    private val syncJobDao: com.xburnsx.toutiebudget.data.room.daos.SyncJobDao? = null,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouSauvegarde = com.xburnsx.toutiebudget.utils.VerrouOperation()
    private val verrouCreationTiers = com.xburnsx.toutiebudget.utils.VerrouOperation()

    private val _uiState = MutableStateFlow(AjoutTransactionUiState())
    val uiState: StateFlow<AjoutTransactionUiState> = _uiState.asStateFlow()

    // Cache des données pour éviter les rechargements
    private var allComptes: List<Compte> = emptyList()
    private var allEnveloppes: List<Enveloppe> = emptyList()
    private var allAllocations: List<AllocationMensuelle> = emptyList()
    private var generationEnveloppes = 0L
    private var allCategories: List<Categorie> = emptyList()
    private var allTiers: List<Tiers> = emptyList()
    /** AT21 : allocations du mois impossibles à lire (différent de « 0 $ »). */
    private var allocationsIndisponibles = false
    /** Début de l'opération courante : permet à AA5 de ne suivre que ses propres écritures. */
    private var debutDerniereEcriture: Long? = null
    // AT4/AT14 : identifiant conservé pour les nouvelles tentatives du même brouillon.
    private var idOperationBrouillon: String = IdGenerator.generateId()
    /** AA5 : identifiants de la file avant la sauvegarde, puis écrits pendant CETTE opération. */
    private var idsJobsAvantEcriture: Set<String> = emptySet()
    private var idsJobsOperation: Set<String>? = null

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

        // AA5 : le bandeau évolue avec les tâches réellement créées par la dernière opération.
        syncJobDao?.let { dao ->
            viewModelScope.launch {
                dao.getAllSyncJobs().collectLatest { jobs ->
                    val depuis = debutDerniereEcriture ?: return@collectLatest
                    appliquerStatutSynchronisation(jobs, depuis)
                }
            }
        }
    }

    /**
     * Charge toutes les données nécessaires au démarrage de l'écran.
     */
    private fun chargerDonneesInitiales() {
        val generation = ++generationEnveloppes
        val dateDemandee = _uiState.value.dateTransaction
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageErreur = null) }
            
            try {
                // Charger toutes les données en parallèle
                val resultComptes = compteRepository.recupererTousLesComptes()
                val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                val resultCategories = categorieRepository.recupererToutesLesCategories()
                val resultTiers = tiersRepository.recupererTousLesTiers()

                // AT6 : les allocations du mois de la date choisie (pas toujours le mois courant)
                val resultAllocations = enveloppeRepository.recupererAllocationsParMois(premierJourDuMois(dateDemandee))
                if (generation != generationEnveloppes || _uiState.value.dateTransaction != dateDemandee) return@launch
                
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
                allComptes = resultComptes.getOrThrow()
                allEnveloppes = resultEnveloppes.getOrThrow()
                allCategories = resultCategories.getOrThrow()
                allAllocations = resultAllocations.getOrThrow()
                allocationsIndisponibles = false
                allTiers = resultTiers.getOrThrow()

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
                        tiersDisponibles = allTiers,
                        allocationsIndisponibles = allocationsIndisponibles,
                        // AT19/AT20 : relier le brouillon aux comptes réels relus et
                        // annuler une sélection si le compte a été archivé/supprimé.
                        compteSelectionne = state.compteSelectionne?.let { selection ->
                            allComptes.find { !it.estArchive && it.id == selection.id && it.collection == selection.collection }
                        },
                        comptePaiementSelectionne = state.comptePaiementSelectionne?.let { selection ->
                            allComptes.find { !it.estArchive && it.id == selection.id && it.collection == selection.collection }
                        },
                        enveloppeSelectionnee = state.enveloppeSelectionnee?.let { selection ->
                            enveloppesUi.find { it.id == selection.id }
                        }
                    ).calculerValidite()
                }
                
            } catch (e: Exception) {
                if (generation != generationEnveloppes) return@launch
                allocationsIndisponibles = true
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
                        messageErreur = messageErreur,
                        allocationsIndisponibles = true
                    ).calculerValidite()
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
                            comptesDisponibles = allComptes.filter { !it.estArchive },
                            compteSelectionne = state.compteSelectionne?.let { selection ->
                                allComptes.find { !it.estArchive && it.id == selection.id && it.collection == selection.collection }
                            },
                            comptePaiementSelectionne = state.comptePaiementSelectionne?.let { selection ->
                                allComptes.find { !it.estArchive && it.id == selection.id && it.collection == selection.collection }
                            }
                        ).calculerValidite()
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
        val generation = ++generationEnveloppes
        val dateDemandee = _uiState.value.dateTransaction
        viewModelScope.launch {
            try {
                val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                val resultCategories = categorieRepository.recupererToutesLesCategories()
                
                // AT6 : les allocations du mois de la date choisie (pas toujours le mois courant)
                val resultAllocations = enveloppeRepository.recupererAllocationsParMois(premierJourDuMois(dateDemandee))
                if (generation != generationEnveloppes || _uiState.value.dateTransaction != dateDemandee) return@launch

                if (resultEnveloppes.isFailure) resultEnveloppes.getOrThrow()
                if (resultCategories.isFailure) resultCategories.getOrThrow()
                if (resultEnveloppes.isSuccess && resultCategories.isSuccess) {
                    allEnveloppes = resultEnveloppes.getOrThrow()
                    allCategories = resultCategories.getOrThrow()
                    if (resultAllocations.isFailure) {
                        allocationsIndisponibles = true
                        _uiState.update { state -> state.copy(
                            allocationsIndisponibles = true,
                            messageErreur = "Allocations de ce mois indisponibles : ${resultAllocations.exceptionOrNull()?.message}"
                        ).calculerValidite() }
                        return@launch
                    }
                    allAllocations = resultAllocations.getOrThrow()
                    allocationsIndisponibles = false
                    
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
                            enveloppesFiltrees = enveloppesFiltrees,
                            allocationsIndisponibles = allocationsIndisponibles,
                            // L'enveloppe choisie garde sa sélection mais montre le solde du bon mois
                            enveloppeSelectionnee = state.enveloppeSelectionnee?.let { choisie -> enveloppesUi.find { it.id == choisie.id } }
                        ).calculerValidite()
                    }
                }
            } catch (e: Exception) {
                if (generation != generationEnveloppes) return@launch
                allocationsIndisponibles = true
                _uiState.update { etat ->
                    etat.copy(allocationsIndisponibles = true,
                        messageErreur = "Impossible de charger les enveloppes du mois : ${e.message}")
                        .calculerValidite()
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
            // GN4 : toutes les allocations du mois de cette enveloppe, additionnées
            val allocations = allAllocations.filter { it.enveloppeId == enveloppe.id }
            val solde = allocations.sumOf { it.solde }
            
            // Récupérer la couleur du compte source depuis l'allocation
            // 🎨 CORRECTION : Reset la couleur de provenance quand solde = 0
            val compteSource = if (solde > 0.001) {
                allocations.filter { it.solde > 0 }.maxByOrNull { it.solde }?.compteSourceId?.let { compteId ->
                    allComptes.find { it.id == compteId }
                }
            } else {
                null // Reset la couleur quand solde = 0
            }

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = solde,
                depense = allocations.sumOf { it.depense },
                alloue = allocations.sumOf { it.alloue }, // Alloué ce mois
                alloueCumulatif = allocations.sumOf { it.alloue }, // ← NOUVEAU : Pour simplifier, on utilise la même valeur
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
                enveloppeSelectionnee = null,
                fractionnementEffectue = false // AT7 : le fractionnement est à revoir dans le nouveau mode
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
        rafraichirDossiersEtSuggestions()
    }

    /**
     * Met à jour le type de transaction sélectionné (pour mode Standard).
     */
    fun onTypeTransactionChanged(nouveauType: TypeTransaction) {
        _uiState.update { state ->
            state.copy(
                typeTransaction = nouveauType,
                enveloppeSelectionnee = null, // Reset de l'enveloppe si on change de type
                fractionnementEffectue = false // AT7
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
        rafraichirDossiersEtSuggestions()
    }

    /**
     * Met à jour le type de dette sélectionné.
     */
    fun onTypeDetteChanged(nouveauType: String) {
        _uiState.update { state ->
            state.copy(typeDette = nouveauType).calculerValidite()
        }
        rafraichirDossiersEtSuggestions()
    }

    /**
     * Met à jour le montant saisi directement.
     */
    fun onMontantDirectChange(nouveauMontant: String) {
        _uiState.update { state ->
            state.copy(montant = nouveauMontant,
                // AT7 : ne pas laisser une ancienne confirmation de fractionnement
                // valable lorsque la somme a changé.
                fractionnementEffectue = state.fractionnementEffectue &&
                    state.fractionsSauvegardees.sumOf { it.montant } == nouveauMontant.toLongOrNull()?.toDouble()
            ).calculerValidite()
        }
    }

    /**
     * Met à jour le compte sélectionné.
     */
    fun onCompteSelected(nouveauCompte: Compte?) {
        _uiState.update { state ->
            val aChange = state.compteSelectionne?.id != nouveauCompte?.id
            state.copy(compteSelectionne = nouveauCompte,
                fractionnementEffectue = state.fractionnementEffectue && !aChange,
                // AT7 : une ancienne fraction ne doit pas être réutilisée en silence
                // avec un autre compte. La sélection doit être reconfirmée.
                fractionsSauvegardees = if (aChange) emptyList() else state.fractionsSauvegardees
            ).calculerValidite()
        }
        _uiState.update { it.avecAvertissementProvenance() }
    }

    /**
     * Met à jour l'enveloppe sélectionnée.
     */
    fun onEnveloppeSelected(nouvelleEnveloppe: EnveloppeUi?) {
        _uiState.update { state ->
            state.copy(enveloppeSelectionnee = nouvelleEnveloppe).calculerValidite()
        }
        _uiState.update { it.avecAvertissementProvenance() }
    }

    /**
     * Met à jour le champ tiers.
     */
    fun onTiersChanged(nouveauTiers: String) {
        _uiState.update { state ->
            state.copy(tiersUtiliser = nouveauTiers).calculerValidite()
        }
        rafraichirDossiersEtSuggestions()
    }

    /**
     * Réinitialise pour une nouvelle transaction.
     */
    fun nouvelleTransaction() {
        idOperationBrouillon = IdGenerator.generateId()
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
            state.copy(montant = nouveauMontant,
                // AT7 : ne pas laisser une ancienne confirmation de fractionnement
                // valable lorsque la somme a changé.
                fractionnementEffectue = state.fractionnementEffectue &&
                    state.fractionsSauvegardees.sumOf { it.montant } == nouveauMontant.toLongOrNull()?.toDouble()
            ).calculerValidite()
        }
    }

    /**
     * Met à jour le compte sélectionné.
     */
    fun onCompteChanged(nouveauCompte: Compte?) {
        _uiState.update { state ->
            val aChange = state.compteSelectionne?.id != nouveauCompte?.id
            state.copy(compteSelectionne = nouveauCompte,
                fractionnementEffectue = state.fractionnementEffectue && !aChange,
                fractionsSauvegardees = if (aChange) emptyList() else state.fractionsSauvegardees
            ).calculerValidite()
        }
        _uiState.update { it.avecAvertissementProvenance() }
    }

    /**
     * Met à jour l'enveloppe sélectionnée.
     */
    fun onEnveloppeChanged(nouvelleEnveloppe: EnveloppeUi?) {
        _uiState.update { state ->
            state.copy(enveloppeSelectionnee = nouvelleEnveloppe).calculerValidite()
        }
        _uiState.update { it.avecAvertissementProvenance() }
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
        val ancienne = _uiState.value.dateTransaction
        _uiState.update { state ->
            state.copy(
                dateTransaction = nouvelleDate,
                fractionnementEffectue = state.fractionnementEffectue &&
                    ancienne.year == nouvelleDate.year && ancienne.monthValue == nouvelleDate.monthValue
            ).calculerValidite()
        }
        // AT6 : autre mois = autres soldes d'enveloppes
        if (ancienne.year != nouvelleDate.year || ancienne.monthValue != nouvelleDate.monthValue) {
            chargerEnveloppesSeulement()
        }
    }

    /**
     * AT17 / AT20 : fin commune de toute opération réussie. Le formulaire est vidé une seule fois
     * (pas de 2e enregistrement possible du même brouillon), le message reste affiché et « réussie »
     * ne reste pas vrai : l'écran ne relance donc pas la navigation une 2e fois.
     */
    private fun terminerAvecSucces(message: String, detteSoldee: Boolean = false) {
        // Une nouvelle saisie = nouvelle opération; un échec gardera l'ancien ID.
        idOperationBrouillon = IdGenerator.generateId()
        val avant = _uiState.value
        _uiState.update {
            AjoutTransactionUiState(
                isLoading = false,
                comptesDisponibles = avant.comptesDisponibles,
                enveloppesDisponibles = avant.enveloppesDisponibles,
                enveloppesFiltrees = avant.enveloppesFiltrees,
                tiersDisponibles = avant.tiersDisponibles,
                messageConfirmation = message,
                statutSynchronisation = StatutSynchronisationEcriture.ENREGISTREE_LOCALEMENT,
                detteSoldee = detteSoldee
            ).calculerValidite()
        }
        val depuis = debutDerniereEcriture
        if (syncJobDao != null && depuis != null) {
            viewModelScope.launch {
                val jobs = syncJobDao.getAllSyncJobs().first()
                // Seules les tâches ajoutées durant l'opération sont suivies,
                // même si le worker met à jour ensuite leurs statuts.
                idsJobsOperation = jobs.filter { it.id !in idsJobsAvantEcriture }
                    .map { it.id }.toSet()
                appliquerStatutSynchronisation(jobs, depuis)
            }
        }
        BudgetEvents.refreshBudget.tryEmit(Unit)
        realtimeSyncService.declencherMiseAJourBudget()
        // Recharger les soldes affichés
        chargerDonneesInitiales()
    }

    private var travailTiers: kotlinx.coroutines.Job? = null

    /**
     * Selon le tiers saisi :
     * - AA3 : ses dossiers de prêt/emprunt actifs, pour choisir lequel rembourser ;
     * - AA2 : les enveloppes déjà utilisées avec lui, proposées (jamais choisies d'office, CAT-01).
     */
    private fun rafraichirDossiersEtSuggestions() {
        travailTiers?.cancel()
        travailTiers = viewModelScope.launch {
            kotlinx.coroutines.delay(250) // pas à chaque lettre
            val s = _uiState.value
            val cle = cleTiers(s.tiersUtiliser)

            val typeDossier = when {
                s.modeOperation == "Prêt" && s.typePret == "Remboursement reçu" -> com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET
                s.modeOperation == "Emprunt" && s.typeDette == "Remboursement donné" -> com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.DETTE
                else -> null
            }
            val dossiers = if (typeDossier != null && cle.isNotBlank()) {
                val resultatDossiers = pretPersonnelRepository.lister()
                if (resultatDossiers.isFailure) {
                    _uiState.update { it.copy(messageErreur =
                        "Impossible de charger les prêts de ce tiers : ${resultatDossiers.exceptionOrNull()?.message}") }
                    return@launch
                }
                resultatDossiers.getOrThrow()
                    .filter { cleTiers(it.nomTiers ?: "") == cle && it.type == typeDossier && !it.estArchive && kotlin.math.abs(it.solde) > 0.005 }
                    .sortedBy { it.created ?: "" }
            } else emptyList()

            val suggestions = if (s.modeOperation == "Standard" && s.typeTransaction == TypeTransaction.Depense && cle.isNotBlank()) {
                enveloppesUtiliseesAvec(cle).mapNotNull { id -> s.enveloppesDisponibles.find { it.id == id } }
            } else emptyList()

            _uiState.update { etat ->
                etat.copy(
                    dossiersDisponibles = dossiers,
                    dossierChoisiId = etat.dossierChoisiId?.takeIf { id -> dossiers.any { it.id == id } },
                    suggestionsEnveloppes = suggestions
                )
            }
        }
    }

    /** Les 3 enveloppes les plus récemment utilisées avec ce tiers (achats simples et fractions). */
    private suspend fun enveloppesUtiliseesAvec(cle: String): List<String> {
        val recentes = transactionRepository.recupererToutesLesTransactions().getOrElse { erreur ->
            _uiState.update { it.copy(messageErreur = "Suggestions de tiers indisponibles : ${erreur.message}") }
            return emptyList()
        }
            .filter { it.type == TypeTransaction.Depense && cleTiers(it.tiersUtiliser ?: "") == cle }
            .sortedByDescending { it.date }
            .take(20)
        val ids = LinkedHashSet<String>()
        for (tx in recentes) {
            if (tx.estFractionnee && !tx.sousItems.isNullOrBlank()) {
                runCatching {
                    com.google.gson.JsonParser.parseString(tx.sousItems).asJsonArray.forEach { element ->
                        val champs = element.asJsonObject
                        (champs.get("enveloppe_id") ?: champs.get("enveloppeId"))
                            ?.takeIf { !it.isJsonNull }?.asString?.let { ids += it }
                    }
                }
            } else {
                tx.allocationMensuelleId?.let { allocationMensuelleRepository.getAllocationById(it)?.enveloppeId }?.let { ids += it }
            }
            if (ids.size >= 3) break
        }
        return ids.take(3)
    }

    /** AA3 : rembourser un dossier précis ; null = du plus ancien au plus récent. */
    fun onDossierChoisi(dossierId: String?) {
        _uiState.update { it.copy(dossierChoisiId = dossierId) }
    }

    /**
     * AA4 : remplit le formulaire avec une ancienne dépense ou un ancien revenu, SANS l'enregistrer.
     * La date devient aujourd'hui ; un compte archivé ou une enveloppe archivée ne sont pas repris
     * (l'utilisateur les choisit de nouveau). Paiements, virements et prêts ne se reprennent pas ici.
     */
    fun reprendreTransaction(transactionId: String) {
        viewModelScope.launch {
            val tx = transactionRepository.recupererTransactionParId(transactionId).getOrNull()
            if (tx == null) {
                _uiState.update { it.copy(messageErreur = "Transaction introuvable.") }
                return@launch
            }
            val lie = tx.sousItems?.let { it.contains("pret_personnel_id") || it.contains(com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl.CLE_OPERATION_PAIEMENT) } ?: false
            if ((tx.type != TypeTransaction.Depense && tx.type != TypeTransaction.Revenu) || lie) {
                _uiState.update { it.copy(messageErreur = "Seules une dépense ou un revenu peuvent être repris.") }
                return@launch
            }
            if (allComptes.isEmpty()) chargerDonneesInitiales()

            val fractions = if (tx.estFractionnee && !tx.sousItems.isNullOrBlank()) {
                runCatching {
                    com.google.gson.JsonParser.parseString(tx.sousItems).asJsonArray.mapIndexedNotNull { index, element ->
                        val o = element.asJsonObject
                        val enveloppeId = (o.get("enveloppe_id") ?: o.get("enveloppeId"))
                            ?.takeIf { !it.isJsonNull }?.asString ?: return@mapIndexedNotNull null
                        if (allEnveloppes.find { it.id == enveloppeId }?.estArchive != false) return@mapIndexedNotNull null
                        FractionTransaction(
                            id = "f${index + 1}",
                            montant = Math.round((o.get("montant")?.asDouble ?: 0.0) * 100).toDouble(),
                            enveloppeId = enveloppeId,
                            note = o.get("note")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        )
                    }
                }.getOrElse { emptyList() }
            } else emptyList()
            val enveloppeId = if (!tx.estFractionnee) {
                tx.allocationMensuelleId?.let { allocationMensuelleRepository.getAllocationById(it)?.enveloppeId }
            } else null

            _uiState.update { state ->
                state.copy(
                    modeOperation = "Standard",
                    typeTransaction = tx.type,
                    montant = Math.round(tx.montant * 100).toString(),
                    tiersUtiliser = tx.tiersUtiliser ?: "",
                    note = tx.note ?: "",
                    dateTransaction = LocalDate.now(),
                    compteSelectionne = state.comptesDisponibles.find { it.id == tx.compteId },
                    enveloppeSelectionnee = enveloppeId?.let { id -> state.enveloppesDisponibles.find { it.id == id } },
                    // Fractions reprises seulement si elles font encore le total exact (AT11)
                    fractionsSauvegardees = fractions,
                    fractionnementEffectue = fractions.isNotEmpty() && Math.round(fractions.sumOf { it.montant }) == Math.round(tx.montant * 100),
                    messageErreur = null,
                    messageConfirmation = null
                ).calculerValidite()
            }
            _uiState.update { it.avecAvertissementProvenance() }
        }
    }

    /** AT18 : « Irving  Port-Cartier » et « irving port-cartier » sont le même tiers. */
    private fun cleTiers(nom: String): String = nom.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun premierJourDuMois(date: LocalDate): Date =
        Date.from(date.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant())

    /** AT12 : prévient (sans bloquer) quand l'argent de l'enveloppe vient d'un autre compte chèque que celui qui paie. */
    private fun AjoutTransactionUiState.avecAvertissementProvenance(): AjoutTransactionUiState {
        val compte = compteSelectionne as? CompteCheque
        val enveloppe = enveloppeSelectionnee
        val message = if (compte != null && enveloppe != null && enveloppe.solde > 0.001 &&
            modeOperation == "Standard" && typeTransaction == TypeTransaction.Depense
        ) {
            val provenance = allAllocations.filter { it.enveloppeId == enveloppe.id && it.solde > 0 }
                .maxByOrNull { it.solde }?.compteSourceId
            if (provenance != null && provenance != compte.id) {
                val nomProvenance = allComptes.find { it.id == provenance }?.nom ?: "un autre compte"
                "L'argent de « ${enveloppe.nom} » vient de $nomProvenance, mais cette dépense sort de ${compte.nom}."
            } else null
        } else null
        return copy(avertissementProvenance = message)
    }

    // === MÉTHODES POUR LA GESTION DES TIERS ===

    /**
     * Met à jour le texte saisi dans le champ Tiers.
     */
    fun onTexteTiersSaisiChange(nouveauTexte: String) {
        _uiState.update { state ->
            state.copy(tiersUtiliser = nouveauTexte).calculerValidite()
        }
        rafraichirDossiersEtSuggestions()
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
        rafraichirDossiersEtSuggestions()
    }

    /**
     * Crée un nouveau tiers avec le nom fourni.
     */
    fun onCreerNouveauTiers(nomTiers: String) {
        if (nomTiers.isBlank()) return

        // AT18 : un tiers qui existe déjà (casse, espaces) est réutilisé, pas recréé
        val cle = cleTiers(nomTiers)
        allTiers.firstOrNull { cleTiers(it.nom) == cle }?.let { existant ->
            onTiersSelectionne(existant)
            return
        }

        viewModelScope.lancerUneFois(verrouCreationTiers) {
            _uiState.update { it.copy(isLoadingTiers = true) }

            try {
                // AT18 : le cache du dropdown peut être périmé ou un autre
                // clic vient tout juste de créer ce tiers; relire Room.
                val tiersActuels = tiersRepository.recupererTousLesTiers().getOrThrow()
                val dejaExistant = tiersActuels.firstOrNull { cleTiers(it.nom) == cle }
                if (dejaExistant != null) {
                    allTiers = tiersActuels
                    _uiState.update { state ->
                        state.copy(isLoadingTiers = false,
                            tiersDisponibles = tiersActuels,
                            tiersUtiliser = dejaExistant.nom).calculerValidite()
                    }
                    return@lancerUneFois
                }
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
        
        viewModelScope.lancerUneFois(verrouSauvegarde) {
            // Les tâches plus anciennes (ou créées pour une autre action) ne sont
            // pas une preuve de synchronisation de la transaction courante.
            idsJobsOperation = null
            idsJobsAvantEcriture = syncJobDao?.getAllSyncJobs()?.first()?.map { it.id }?.toSet()
                ?: emptySet()
            debutDerniereEcriture = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    estEnTrainDeSauvegarder = true,
                    messageErreur = null,
                    statutSynchronisation = null
                )
            }

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
                    // AT19 : pas de paiement vers soi-même, vers un compte archivé ou vers un type non admissible
                    if (comptePaiement.id == compte.id) throw Exception("Un compte ne peut pas se payer lui-même.")
                    if (comptePaiement !is CompteCredit && comptePaiement !is CompteDette) throw Exception("Seules une carte de crédit ou une dette peuvent recevoir un paiement.")
                    if (comptePaiement.estArchive || compte.estArchive) throw Exception("Un des comptes est archivé.")
                    
                    // Effectuer le paiement de carte/dette
                    // DEBUG: Début paiement carte/dette - Compte: ${compte.nom}, Carte: ${comptePaiement.nom}, Montant: ${montant / 100.0}
                    
                    val result = argentService.effectuerPaiementCarteOuDette(
                        compteQuiPaieId = compte.id,
                        collectionCompteQuiPaie = when (compte) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissement"
                            else -> "comptes_cheques"
                        },
                        carteOuDetteId = comptePaiement.id,
                        collectionCarteOuDette = when (comptePaiement) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissement"
                            else -> "comptes_cheques"
                        },
                        montant = montant / 100.0, // Convertir centimes en dollars
                        note = comptePaiement.nom, // Passer le nom de la dette/carte comme tiers
                        // DF14 : la date choisie (avec l'heure actuelle) et donc son mois
                        date = java.time.LocalDateTime.now().let { maintenant ->
                            Date.from(state.dateTransaction.atTime(maintenant.hour, maintenant.minute, maintenant.second)
                                .atZone(ZoneId.systemDefault()).toInstant())
                        }
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
                            // AT15 : échec de rafraîchissement ≠ carte/dette soldée.
                            when (comptePaiement) {
                                is CompteCredit -> {
                                    val soldeActuel = (comptePaye as? CompteCredit)?.solde
                                    if (soldeActuel != null && soldeActuel.isFinite() &&
                                        soldeActuel >= -0.01 && soldeActuel <= 0.01) {
                                        detteTerminee = true
                                    }
                                    message = "Votre paiement à la carte ${comptePaiement.nom} a été enregistré"
                                }
                                is CompteDette -> {
                                    val soldeActuel = (comptePaye as? CompteDette)?.solde
                                    if (soldeActuel != null && soldeActuel.isFinite() &&
                                        soldeActuel >= -0.01 && soldeActuel <= 0.01) {
                                        detteTerminee = true
                                    }
                                    message = "Votre paiement à la dette ${comptePaiement.nom} a été enregistré"
                                }
                                else -> { }
                            }
                        } catch (_: Exception) {}
                        terminerAvecSucces(
                            if (detteTerminee) message + "\nVotre dette est terminée, félicitations!" else message,
                            detteSoldee = detteTerminee
                        )
                    } else {
                        _uiState.update { it.copy(estEnTrainDeSauvegarder = false, messageErreur = result.exceptionOrNull()?.message) }
                    }
                    return@lancerUneFois
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
                        is CompteInvestissement -> "comptes_investissement"
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
                        // AT4 : dossier, compte et transaction ensemble ou pas du tout
                        operationAtomique.executer {
                            val created = pretPersonnelRepository.creer(record).getOrThrow()
                            // Débiter le compte et le prêt à placer
                            compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                                compteId = compte.id,
                                collectionCompte = collectionCompte,
                                variationSolde = -montantDollars,
                                mettreAJourPretAPlacer = true
                            ).getOrThrow()
                            // persister la transaction pour historique
                            transactionRepository.creerTransaction(
                                Transaction(
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
                            ).getOrThrow()
                            // AT15/AT16 : journal dans la MÊME transaction Room.
                            if (compte is CompteCheque) {
                                val compteApres = compteRepository.getCompteById(compte.id, collectionCompte) as? CompteCheque
                                    ?: throw IllegalStateException("Compte chèque introuvable après écriture.")
                                historiqueAllocationService.enregistrerTransactionDirecte(
                                    compte = compteApres,
                                    enveloppe = null,
                                    typeTransaction = "PRET",
                                    montant = -montantDollars,
                                    soldeAvant = compteApres.solde + montantDollars,
                                    soldeApres = compteApres.solde,
                                    pretAPlacerAvant = compteApres.pretAPlacer + montantDollars,
                                    pretAPlacerApres = compteApres.pretAPlacer,
                                    note = "Prêt à ${nomTiers}"
                                )
                            }
                        }
                        
                        // Le journal fait partie de l'écriture Room; aucune exception n'est ignorée.
                        terminerAvecSucces("Prêt enregistré")
                        return@lancerUneFois
                    } else if (state.typePret == "Remboursement reçu") {
                        enregistrerRemboursementPersonnel(nomTiers, recu = true, montantDollars = montantDollars, compte = compte, collectionCompte = collectionCompte, date = dateTx, note = state.note.takeIf { it.isNotBlank() }, dossierId = state.dossierChoisiId)
                        terminerAvecSucces("Remboursement reçu enregistré")
                        return@lancerUneFois
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
                        is CompteInvestissement -> "comptes_investissement"
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
                        // AT4 : dossier, compte et transaction ensemble ou pas du tout
                        operationAtomique.executer {
                            val created = pretPersonnelRepository.creer(record).getOrThrow()
                            // Créditer le compte et le prêt à placer
                            compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                                compteId = compte.id,
                                collectionCompte = collectionCompte,
                                variationSolde = montantDollars,
                                mettreAJourPretAPlacer = true
                            ).getOrThrow()
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
                            ).getOrThrow()
                            // AT15/AT16 : journal dans la MÊME transaction Room.
                            if (compte is CompteCheque) {
                                val compteApres = compteRepository.getCompteById(compte.id, collectionCompte) as? CompteCheque
                                    ?: throw IllegalStateException("Compte chèque introuvable après écriture.")
                                historiqueAllocationService.enregistrerTransactionDirecte(
                                    compte = compteApres,
                                    enveloppe = null,
                                    typeTransaction = "EMPRUNT",
                                    montant = montantDollars,
                                    soldeAvant = compteApres.solde - montantDollars,
                                    soldeApres = compteApres.solde,
                                    pretAPlacerAvant = compteApres.pretAPlacer - montantDollars,
                                    pretAPlacerApres = compteApres.pretAPlacer,
                                    note = "Emprunt de ${nomTiers}"
                                )
                            }
                        }
                        // P5 : le compte est crédité une seule fois (un second crédit doublait le montant)

                        // Le journal fait partie de l'écriture Room; aucune exception n'est ignorée.
                        terminerAvecSucces("Emprunt enregistré")
                        return@lancerUneFois
                    } else if (state.typeDette == "Remboursement donné") {
                        enregistrerRemboursementPersonnel(nomTiers, recu = false, montantDollars = montantDollars, compte = compte, collectionCompte = collectionCompte, date = dateTx2, note = state.note.takeIf { it.isNotBlank() }, dossierId = state.dossierChoisiId)
                        terminerAvecSucces("Remboursement donné enregistré")
                        return@lancerUneFois
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
                    // AT11 : dernière vérification juste avant d'écrire (somme au cent, enveloppes actives)
                    if (!state.fractionsCorrespondentAuMontant()) throw Exception("Les fractions ne font plus le total : revoyez le fractionnement.")
                    fractions.forEach { f ->
                        val env = allEnveloppes.find { it.id == f.enveloppeId }
                        if (env == null || env.estArchive) throw Exception("Une enveloppe du fractionnement n'est plus disponible.")
                    }
                    
                    val collectionPrincipale = when (compte) {
                        is CompteCheque -> "comptes_cheques"
                        is CompteCredit -> "comptes_credits"
                        is CompteDette -> "comptes_dettes"
                        is CompteInvestissement -> "comptes_investissement"
                        else -> "comptes_cheques"
                    }
                    val montantDollars = MoneyFormatter.roundAmount(montant / 100.0)
                    val idTransaction = idOperationBrouillon

                    // AT4 / AT8 / AT15 : transaction principale et toutes ses fractions, ensemble ou pas du tout
                    val transactionPrincipale = runCatching {
                        operationAtomique.executer {
                            // Rejeu après réponse perdue : jamais retoucher les fractions.
                            val dejaEnregistree = transactionRepository.recupererTransactionParId(idTransaction).getOrNull()
                            if (dejaEnregistree != null) {
                                if (dejaEnregistree.type != typeTransaction ||
                                    dejaEnregistree.compteId != compte.id ||
                                    !dejaEnregistree.estFractionnee ||
                                    kotlin.math.abs(dejaEnregistree.montant - montantDollars) > 0.000001
                                ) throw IllegalStateException("L'identifiant de cette opération est déjà utilisé.")
                                return@executer
                            }
                            // AT4 : création d'allocation, JSON des fractions et
                            // écritures de comptes dans la MÊME transaction Room.
                        val compteConfirme = compteRepository.recupererCompteParIdToutesCollections(compte.id).getOrThrow()
                        if (compteConfirme.estArchive || compteConfirme.collection != collectionPrincipale ||
                            compteConfirme.utilisateurId != compte.utilisateurId
                        ) throw IllegalStateException("Compte de fractionnement archivé ou modifié.")
                        val enveloppesConfirmees = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                            .filter { !it.estArchive && it.utilisateurId == compteConfirme.utilisateurId }
                            .associateBy { it.id }
                        val sousItems = fractions.mapIndexed { index, fraction ->
                            val env = enveloppesConfirmees[fraction.enveloppeId]
                                ?: throw IllegalStateException("Enveloppe indisponible au moment d'enregistrer.")
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
                            is CompteInvestissement -> "comptes_investissement"
                            else -> "comptes_cheques"
                        }
                        
                        // AT11/AT12 : provenance et soldes relus SOUS la
                        // transaction. Un financement positif d'un autre compte
                        // ne peut pas servir une dépense sur celui sélectionné.
                        if (!allocationActuelle.solde.isFinite() ||
                            !allocationActuelle.alloue.isFinite() ||
                            !allocationActuelle.depense.isFinite()
                        ) throw IllegalStateException("Montants d'allocation invalides.")
                        if (kotlin.math.round(allocationActuelle.solde * 100.0).toLong() > 0L &&
                            allocationActuelle.compteSourceId != null &&
                            allocationActuelle.compteSourceId != compte.id
                        ) throw IllegalStateException("Cette enveloppe est financée par un autre compte.")
                        // Ne pas réattribuer un ancien historique inconnu à ce compte.
                        if (allocationActuelle.compteSourceId == null &&
                            (kotlin.math.abs(allocationActuelle.alloue) > 0.005 ||
                             kotlin.math.abs(allocationActuelle.depense) > 0.005)) {
                            throw IllegalStateException("Allocation historique sans provenance : correction requise.")
                        }
                        if (allocationActuelle.compteSourceId == null) {
                            val allocationMiseAJour = allocationActuelle.copy(
                                compteSourceId = compte.id,
                                collectionCompteSource = collectionCompte
                            )
                            allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)
                        }
                        
                        // Récupérer le nom de l'enveloppe pour la description
                        val nomEnveloppe = env.nom
                        
                        mapOf(
                            "id" to "temp_${index + 1}",
                            "description" to nomEnveloppe, // TOUJOURS utiliser le nom de l'enveloppe
                            "enveloppeId" to fraction.enveloppeId,
                            "enveloppe_id" to fraction.enveloppeId,
                            "montant" to montantEnDollars, // Montant en dollars pour le JSON
                            "allocation_mensuelle_id" to allocationActuelle.id, // Lier à l'allocation
                            "transactionParenteId" to null,
                            "note" to fraction.note.trim() // AT10
                        )
                    }
                    
                    val gson = com.google.gson.Gson()
                    val sousItemsJson = gson.toJson(sousItems)


                            enregistrerTransactionUseCase.executer(
                                typeTransaction = typeTransaction,
                                montant = montantDollars,
                                compteId = compte.id,
                                collectionCompte = collectionPrincipale,
                                enveloppeId = null, // Pas d'enveloppe pour la transaction principale
                                tiersUtiliser = state.tiersUtiliser.takeIf { it.isNotBlank() } ?: "Transaction fractionnée",
                                note = state.note.takeIf { it.isNotBlank() },
                                date = dateTransaction,
                                estFractionnee = true,
                                sousItems = sousItemsJson,
                                id = idTransaction,
                                verifierIdempotence = true
                            ).getOrThrow()

                            // Chaque fraction touche son allocation ; une allocation introuvable arrête tout
                            for (sousItem in sousItems) {
                                val allocationId = sousItem["allocation_mensuelle_id"] as String
                                val montantEnDollars = sousItem["montant"] as Double
                                val avant = enveloppeRepository.recupererAllocationParId(allocationId).getOrThrow()
                                // AT8 : même règle qu'un achat simple ; sur carte de crédit, le solde de l'enveloppe ne bouge pas
                                val apres = if (collectionPrincipale == "comptes_credits") {
                                    avant.copy(
                                        depense = MoneyFormatter.roundAmount(avant.depense + montantEnDollars),
                                        alloue = MoneyFormatter.roundAmount(avant.alloue + montantEnDollars)
                                    )
                                } else {
                                    avant.copy(
                                        depense = MoneyFormatter.roundAmount(avant.depense + montantEnDollars),
                                        solde = MoneyFormatter.roundAmount(avant.solde - montantEnDollars)
                                    )
                                }
                                allocationMensuelleRepository.mettreAJourAllocation(apres)
                            }
                        }
                    }

                    if (transactionPrincipale.isSuccess) {

                        val message = when (typeTransaction) {
                            TypeTransaction.Depense -> "Votre dépense a été enregistrée"
                            TypeTransaction.Revenu -> "Votre revenu a été enregistré"
                            else -> "Votre transaction a été enregistrée"
                        }
                        terminerAvecSucces(message)
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
                            is CompteInvestissement -> "comptes_investissement"
                            else -> "comptes_cheques"
                        }

                        if (typeTransaction == TypeTransaction.RemboursementRecu) {
                            enregistrerRemboursementPersonnel(nomTiers, recu = true, montantDollars = montantDollars, compte = compte, collectionCompte = collectionCompte, date = dateTransaction, note = state.note.takeIf { it.isNotBlank() })
                            terminerAvecSucces("Remboursement reçu enregistré")
                            return@lancerUneFois
                        } else if (typeTransaction == TypeTransaction.RemboursementDonne) {
                            enregistrerRemboursementPersonnel(nomTiers, recu = false, montantDollars = montantDollars, compte = compte, collectionCompte = collectionCompte, date = dateTransaction, note = state.note.takeIf { it.isNotBlank() })
                            terminerAvecSucces("Remboursement donné enregistré")
                            return@lancerUneFois
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
                            is CompteInvestissement -> "comptes_investissement"
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
                        //     return@lancerUneFois
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
                            //     return@lancerUneFois
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
                    // 🔍 DEBUG - Collection du compte: ${when (compte) { is CompteCheque -> "comptes_cheques"; is CompteCredit -> "comptes_credits"; is CompteDette -> "comptes_dettes"; is CompteInvestissement -> "comptes_investissement"; else -> "comptes_cheques" }}

                    // Enregistrer la transaction standard
                    val result = enregistrerTransactionUseCase.executer(
                        typeTransaction = typeTransaction,
                        montant = montant / 100.0,
                        compteId = compte.id,
                        collectionCompte = when (compte) {
                            is CompteCheque -> "comptes_cheques"
                            is CompteCredit -> "comptes_credits"
                            is CompteDette -> "comptes_dettes"
                            is CompteInvestissement -> "comptes_investissement"
                            else -> "comptes_cheques"
                        },
                        enveloppeId = enveloppeId,
                        tiersUtiliser = state.tiersUtiliser.takeIf { it.isNotBlank() } ?: "Transaction",
                        note = state.note.takeIf { it.isNotBlank() },
                        date = dateTransaction,
                        id = idOperationBrouillon,
                        verifierIdempotence = true
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
                        terminerAvecSucces(message)
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

    /** AA5 : classe seulement les tâches apparues depuis le début de cette écriture. */
    private fun appliquerStatutSynchronisation(
        jobs: List<com.xburnsx.toutiebudget.data.room.entities.SyncJob>,
        depuis: Long
    ) {
        if (_uiState.value.messageConfirmation == null) return
        val ids = idsJobsOperation ?: return // capture initiale non terminée
        val jobsSuivis = jobs.filter { it.id in ids }
        val statut = if (ids.isEmpty()) StatutSynchronisationEcriture.ENREGISTREE_LOCALEMENT
            else determinerStatutSynchronisation(jobsSuivis, 0L)
        _uiState.update { courant ->
            // Un COMPLETED observé reste une preuve si PocketBase nettoie
            // ensuite ses tâches. Les autres jobs ne changent jamais ce statut.
            val confirmeAvant = courant.statutSynchronisation == StatutSynchronisationEcriture.SYNCHRONISEE
            courant.copy(
                statutSynchronisation = if (confirmeAvant && jobsSuivis.isEmpty())
                    StatutSynchronisationEcriture.SYNCHRONISEE else statut
            )
        }
    }

    /**
     * Remboursement d'un prêt (reçu) ou d'un emprunt (donné) à un tiers, réparti du plus ancien au plus récent.
     *
     * P6 : tout est validé AVANT de toucher au compte ; un montant supérieur au total restant
     * des dossiers actifs de ce tiers est refusé.
     * P5 : le compte est modifié une seule fois, chaque portion crée une seule transaction,
     * et toute erreur d'écriture interrompt l'opération avec un message au lieu d'être ignorée.
     */
    private suspend fun enregistrerRemboursementPersonnel(
        nomTiers: String,
        recu: Boolean,
        montantDollars: Double,
        compte: Compte,
        collectionCompte: String,
        date: Date,
        note: String?,
        dossierId: String? = null // AA3 : un dossier précis ; null = du plus ancien au plus récent
    ) {
        val typePret = if (recu) com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET
        else com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.DETTE
        val dossiers = pretPersonnelRepository.lister().getOrThrow()
            .filter {
                it.nomTiers.equals(nomTiers, true) && it.type == typePret && !it.estArchive &&
                    kotlin.math.abs(it.solde) > 0.005
            }
            .sortedBy { it.created ?: "" } // plus ancien d'abord
            .filter { dossierId == null || it.id == dossierId }
        if (dossiers.isEmpty()) {
            throw Exception(if (recu) "Aucun prêt actif pour ce tiers" else "Aucun emprunt actif pour ce tiers")
        }
        if (montantDollars <= 0.0) throw Exception(VirementErrorMessages.General.MONTANT_INVALIDE)

        val totalRestant = MoneyFormatter.roundAmount(dossiers.sumOf { kotlin.math.abs(it.solde) })
        if (montantDollars > totalRestant + 0.005) {
            val quoi = if (recu) "à recevoir de" else "dû à"
            throw Exception(
                "Montant trop élevé : il reste ${MoneyFormatter.formatAmount(totalRestant)} $quoi $nomTiers."
            )
        }

        val utilisateurId = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
            ?: throw Exception("Utilisateur non connecté")

        // Répartition calculée entièrement avant la première écriture
        var restant = montantDollars
        val portions = dossiers.mapNotNull { dossier ->
            if (restant <= 0.005) return@mapNotNull null
            val aPayer = MoneyFormatter.roundAmount(kotlin.math.min(restant, kotlin.math.abs(dossier.solde)))
            restant = MoneyFormatter.roundAmount(restant - aPayer)
            dossier to aPayer
        }

        // AT4 / P6 : relire les dossiers dans la transaction Room. Si un autre
        // changement a modifié un solde depuis la préparation, tout échoue AVANT
        // le débit bancaire, sans remboursement excédentaire.
        operationAtomique.executer {
        val soldesActuels = pretPersonnelRepository.lister().getOrThrow().associateBy { it.id }
        portions.forEach { (dossier, _) ->
            val actuel = soldesActuels[dossier.id]
                ?: throw IllegalStateException("Prêt introuvable : actualiser le formulaire.")
            if (actuel.estArchive || actuel.solde != dossier.solde || actuel.type != dossier.type) {
                throw IllegalStateException("Le solde du dossier a changé : recommence le remboursement.")
            }
        }
        // 1) Le compte bouge une seule fois pour le total
        val variationCompte = if (recu) montantDollars else -montantDollars
        compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
            compteId = compte.id,
            collectionCompte = collectionCompte,
            variationSolde = variationCompte,
            mettreAJourPretAPlacer = true
        ).getOrThrow()

        // 2) Chaque dossier et sa transaction
        portions.forEach { (dossier, aPayer) ->
            val reste = MoneyFormatter.roundAmount(kotlin.math.abs(dossier.solde) - aPayer).coerceAtLeast(0.0)
            val nouveauSolde = if (recu) reste else -reste
            pretPersonnelRepository.mettreAJour(
                dossier.copy(solde = nouveauSolde, estArchive = reste < 0.005)
            ).getOrThrow()
            val idTransaction = IdGenerator.generateId()
            transactionRepository.creerTransaction(
                Transaction(
                    id = idTransaction,
                    utilisateurId = utilisateurId,
                    type = if (recu) TypeTransaction.RemboursementRecu else TypeTransaction.RemboursementDonne,
                    montant = aPayer,
                    date = date,
                    compteId = compte.id,
                    collectionCompte = collectionCompte,
                    note = note,
                    tiersUtiliser = nomTiers,
                    sousItems = "{\"pret_personnel_id\":\"${dossier.id}\"}"
                )
            ).getOrThrow()
        }
        // P5 : la trace du compte fait partie du même commit Room
        // que la transaction et la mise à jour des dossiers. Un échec
        // d'historique annule également le remboursement.
        val compteApres = compteRepository.getCompteById(compte.id, collectionCompte)
        if (compteApres is CompteCheque) {
            val variation = if (recu) montantDollars else -montantDollars
            historiqueAllocationService.enregistrerTransactionDirecte(
                compte = compteApres,
                enveloppe = null,
                typeTransaction = if (recu) "REMBOURSEMENT_RECU" else "REMBOURSEMENT_DONNE",
                montant = variation,
                soldeAvant = compteApres.solde - variation,
                soldeApres = compteApres.solde,
                pretAPlacerAvant = compteApres.pretAPlacer - variation,
                pretAPlacerApres = compteApres.pretAPlacer,
                note = if (recu) "Remboursement reçu de $nomTiers" else "Remboursement donné à $nomTiers"
            )
        }
        }
    }

    /**
     * Ouvre l'interface de fractionnement de transaction.
     */
    fun ouvrirFractionnement() {
        val state = _uiState.value
        if (state.allocationsIndisponibles || state.isLoading) {
            _uiState.update { it.copy(messageErreur =
                "Impossible de fractionner sans les soldes du mois. Appuie sur Réessayer.") }
            if (!state.isLoading) chargerDonneesInitiales()
            return
        }
        if (state.enveloppesDisponibles.isEmpty()) {
            _uiState.update { it.copy(messageErreur = "Aucune enveloppe active disponible.") }
            return
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
        val etat = _uiState.value
        val aValider = etat.copy(fractionsSauvegardees = fractions, fractionnementEffectue = true)
        if (etat.allocationsIndisponibles || !aValider.fractionsCorrespondentAuMontant() ||
            fractions.any { f -> etat.enveloppesDisponibles.none { it.id == f.enveloppeId } }
        ) {
            _uiState.update { it.copy(messageErreur =
                "Fractions non valides pour ce montant ou ce mois. Corrige les montants avant de confirmer.") }
            return
        }
        _uiState.update { it.copy(
            estEnModeFractionnement = false,
            fractionnementEffectue = true,
            fractionsSauvegardees = fractions,
            messageErreur = null
        ).calculerValidite() }
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
        debutDerniereEcriture = null
        _uiState.update {
            it.copy(
                messageConfirmation = null,
                statutSynchronisation = null,
                detteSoldee = false
            )
        }
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

/** AA5 : seule une preuve positive (au moins un SyncJob terminé) permet « Synchronisée ».
 * Aucune tâche visible ne prouve rien : le worker peut ne pas l'avoir encore créée,
 * la file peut avoir été nettoyée, ou la lecture peut être incomplète.
 */
internal fun determinerStatutSynchronisation(
    jobs: List<com.xburnsx.toutiebudget.data.room.entities.SyncJob>,
    depuis: Long
): StatutSynchronisationEcriture {
    val jobsOperation = jobs.filter { it.createdAt >= depuis }
    return when {
        jobsOperation.any { it.status == "FAILED" || it.bloque } -> StatutSynchronisationEcriture.ECHEC
        jobsOperation.isEmpty() || jobsOperation.any { it.status != "COMPLETED" } ->
            StatutSynchronisationEcriture.ENREGISTREE_LOCALEMENT
        else -> StatutSynchronisationEcriture.SYNCHRONISEE
    }
}
