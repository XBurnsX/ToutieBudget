// chemin/simule: /ui/comptes/ComptesViewModel.kt
package com.xburnsx.toutiebudget.ui.comptes

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
// import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.xburnsx.toutiebudget.utils.IdGenerator
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte

class ComptesViewModel(
    private val compteRepository: CompteRepository,
    private val realtimeSyncService: RealtimeSyncService,
    private val categorieRepository: CategorieRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val transactionRepository: TransactionRepository,
    private val historiqueAllocationService: com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouCompte = com.xburnsx.toutiebudget.utils.VerrouOperation()

    // Callback pour notifier les autres ViewModels des changements
    var onCompteChange: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(ComptesUiState())
    val uiState: StateFlow<ComptesUiState> = _uiState.asStateFlow()

    init {
        chargerComptes()
        // CO8 : plus de sondage toutes les 2 secondes ; les événements ci-dessous
        // (temps réel PocketBase, modifications locales) et l'ouverture de l'écran suffisent.

        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase
        viewModelScope.launch {
            realtimeSyncService.comptesUpdated.collectLatest {
                chargerComptes()
            }
        }
        
        // 🔄 RAFRAÎCHISSEMENT MANUEL : Écoute des événements de suppression/modification
        viewModelScope.launch {
            BudgetEvents.refreshBudget.collectLatest {
                // 🔄 Événement de rafraîchissement reçu - Mise à jour des comptes
                chargerComptes()
            }
        }
    }

    fun chargerComptes() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            compteRepository.recupererTousLesComptes().onSuccess { comptes ->
                val comptesGroupes = comptes.groupBy {
                    when (it) {
                        is CompteCheque -> "Comptes chèques"
                        is CompteCredit -> "Cartes de crédit"
                        is CompteDette -> "Dettes"
                        is CompteInvestissement -> "Investissements"
                    }
                }.mapValues { (_, comptesType) ->
                    comptesType.filter { !it.estArchive }.sortedBy { it.ordre }
                }.filter { (typeDeCompte, comptesType) ->
                    // Ne pas afficher la catégorie "Dettes" s'il n'y a plus de dettes actives
                    typeDeCompte != "Dettes" || comptesType.isNotEmpty()
                }
                _uiState.update {
                    it.copy(isLoading = false, comptesGroupes = comptesGroupes)
                }
            }.onFailure { erreur ->
                _uiState.update {
                    it.copy(isLoading = false, erreur = "Impossible de charger les comptes: ${erreur.message}")
                }
            }
        }
    }

    /** CO3 : liste séparée, qu'un rafraîchissement des comptes actifs ne peut pas écraser. */
    fun chargerComptesArchives() {
        viewModelScope.launch(Dispatchers.IO) {
            compteRepository.recupererTousLesComptes().onSuccess { comptes ->
                _uiState.update { it.copy(comptesArchives = comptes.filter { c -> c.estArchive }.sortedBy { c -> c.nom.lowercase() }) }
            }.onFailure { erreur ->
                _uiState.update { it.copy(erreur = "Impossible de charger les comptes archivés: ${erreur.message}") }
            }
        }
    }

    // 🔄 NOUVELLE MÉTHODE : Rafraîchir les comptes
    fun rafraichirComptes() {
        chargerComptes()
    }

    fun effacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }

    fun onCompteLongPress(compte: Compte) {
        _uiState.update { it.copy(compteSelectionne = compte, isMenuContextuelVisible = true) }
    }

    fun onDismissMenu() {
        _uiState.update { it.copy(isMenuContextuelVisible = false) }
    }

    fun onOuvrirReconciliationDialog() {
        _uiState.update { 
            it.copy(
                isReconciliationDialogVisible = true,
                isMenuContextuelVisible = false
            ) 
        }
    }

    /**
     * CO5 : écart = solde réel - solde actuel, appliqué au compte choisi seulement.
     * Pour un compte chèque, le « Prêt à placer » bouge du MÊME écart.
     * Une seule trace « Réconciliation » est écrite dans l'historique (pas de faux revenu
     * ni de fausse dépense) ; si elle échoue, l'ajustement du compte est annulé.
     */
    fun onReconcilierCompte(
        nouveauSolde: Double,
        // IF2 / IF7 : pour un placement, nouvelle valeur de marché ou correction d'erreur (jamais un revenu ni une dépense)
        typeValorisation: com.xburnsx.toutiebudget.data.modeles.TypeValorisation = com.xburnsx.toutiebudget.data.modeles.TypeValorisation.MARCHE
    ) {
        val compteSelectionne = _uiState.value.compteSelectionne ?: return
        viewModelScope.lancerUneFois(verrouCompte) {
            try {
                if (!nouveauSolde.isFinite()) throw IllegalArgumentException("Solde réel invalide")
                val soldeReel = com.xburnsx.toutiebudget.utils.MoneyFormatter.roundAmount(nouveauSolde)
                // CO5 : ne pas calculer l'écart sur un solde d'écran périmé.
                val compte = compteRepository.recupererCompteParIdToutesCollections(compteSelectionne.id).getOrThrow()
                val ecart = com.xburnsx.toutiebudget.utils.MoneyFormatter.roundAmount(soldeReel - compte.solde)
                if (kotlin.math.abs(ecart) < 0.005) {
                    _uiState.update { it.copy(isReconciliationDialogVisible = false, compteSelectionne = null) }
                    return@lancerUneFois
                }

                val (compteReconcilie, pretAvant, pretApres) = when (compte) {
                    is CompteCheque -> {
                        val nouveauPret = com.xburnsx.toutiebudget.utils.MoneyFormatter.roundAmount(compte.pretAPlacer + ecart)
                        Triple(compte.copy(solde = soldeReel, pretAPlacerRaw = nouveauPret), compte.pretAPlacer, nouveauPret)
                    }
                    is CompteInvestissement -> {
                        val valorisation = com.xburnsx.toutiebudget.data.modeles.Valorisation(
                            date = com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit.ecrireDate(java.util.Date()),
                            valeur = soldeReel,
                            ancienneValeur = compte.solde,
                            type = typeValorisation
                        )
                        val details = compte.details.copy(valorisations = compte.details.valorisations + valorisation)
                        Triple(compte.copy(solde = soldeReel, detailsInvestissementJson = details.versJson()), 0.0, 0.0)
                    }
                    else -> {
                        _uiState.update { it.copy(erreur = "La réconciliation n'est pas offerte pour ce type de compte.") }
                        return@lancerUneFois
                    }
                }

                operationAtomique.executer {
                    compteRepository.mettreAJourCompte(compteReconcilie).getOrThrow()
                    historiqueAllocationService.enregistrerAjustementCompte(
                        compte = compteReconcilie,
                        typeAction = when {
                            compte !is CompteInvestissement -> "RECONCILIATION"
                            typeValorisation == com.xburnsx.toutiebudget.data.modeles.TypeValorisation.MARCHE -> "VALORISATION_MARCHE"
                            else -> "CORRECTION_SOLDE"
                        },
                        description = "Réconciliation ${compte.nom} : ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(compte.solde)} → " +
                            "${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(soldeReel)} (écart ${if (ecart > 0) "+" else ""}${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(ecart)})",
                        montant = ecart,
                        soldeAvant = compte.solde,
                        soldeApres = soldeReel,
                        pretAPlacerAvant = pretAvant,
                        pretAPlacerApres = pretApres
                    )
                }

                _uiState.update { it.copy(isReconciliationDialogVisible = false, compteSelectionne = null) }
                chargerComptes()
                realtimeSyncService.declencherMiseAJourBudget()
                onCompteChange?.invoke()
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur réconciliation: ${e.message}") }
            }
        }
    }

    /**
     * CO2 : mise à jour ciblée (copy) — minimums, taux, frais, durée, valeurs initiales restent intacts.
     * CO1 : une dette archivée garde tout son historique ; son enveloppe n'est jamais supprimée :
     *       elle est archivée seulement si la dette est terminée ET qu'il n'y reste pas de fonds.
     */
    fun onArchiverCompte() {
        val compte = _uiState.value.compteSelectionne ?: return
        viewModelScope.lancerUneFois(verrouCompte) {
            try {
                val compteArchive = when (compte) {
                    is CompteCheque -> compte.copy(estArchive = true)
                    is CompteCredit -> compte.copy(estArchive = true)
                    is CompteDette -> compte.copy(estArchive = true)
                    is CompteInvestissement -> compte.copy(estArchive = true)
                }

                compteRepository.mettreAJourCompte(compteArchive).getOrThrow()

                var message: String? = null
                if (compte is CompteDette) {
                    message = archiverEnveloppeDetteSiTerminee(compte)
                }

                _uiState.update {
                    it.copy(
                        isMenuContextuelVisible = false,
                        isConfirmationArchivageVisible = false,
                        compteSelectionne = null,
                        erreur = message
                    )
                }
                chargerComptes()
                realtimeSyncService.declencherMiseAJourBudget()
                realtimeSyncService.declencherMiseAJourComptes()
                onCompteChange?.invoke()
            } catch (e: Exception) {
                _uiState.update { it.copy(isConfirmationArchivageVisible = false, erreur = "Erreur archivage: ${e.message}") }
            }
        }
    }

    /** Retourne un message à afficher si l'enveloppe de la dette a été laissée active. */
    private suspend fun archiverEnveloppeDetteSiTerminee(dette: CompteDette): String? {
        val categories = categorieRepository.recupererToutesLesCategories().getOrElse { emptyList() }
        val catDettes = categories.firstOrNull { it.nom.equals("Dettes", ignoreCase = true) }
        val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrElse { emptyList() }
        // DF11 : le lien par ID d'abord ; sinon un seul homonyme dans la catégorie Dettes, jamais ailleurs
        val envDette = dette.details.enveloppeId?.let { id -> enveloppes.firstOrNull { it.id == id && !it.estArchive } }
            ?: enveloppes.singleOrNull { it.nom.equals(dette.nom, ignoreCase = true) && catDettes != null && it.categorieId == catDettes.id && !it.estArchive }
            ?: return null

        val terminee = kotlin.math.abs(dette.soldeDette) < 0.01
        if (!terminee) {
            return "Dette archivée. Son enveloppe « ${envDette.nom} » reste active : la dette n'est pas terminée."
        }

        val soldeEnveloppe = enveloppeRepository.recupererAllocationsEnveloppe(envDette.id).getOrElse { emptyList() }
            .groupBy { it.mois.time }
            .maxByOrNull { it.key }?.value?.sumOf { it.solde } ?: 0.0
        if (kotlin.math.abs(soldeEnveloppe) > 0.009) {
            return "Dette archivée. Son enveloppe « ${envDette.nom} » contient encore " +
                "${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(soldeEnveloppe)} : elle reste active."
        }

        // Archiver (jamais supprimer) : l'historique d'allocation et les références restent intacts
        enveloppeRepository.mettreAJourEnveloppe(envDette.copy(estArchive = true))
        return null
    }

    /** CO7 : demander confirmation avant d'archiver. */
    fun onDemanderArchivage() {
        _uiState.update { it.copy(isConfirmationArchivageVisible = true, isMenuContextuelVisible = false) }
    }

    fun onAnnulerArchivage() {
        _uiState.update { it.copy(isConfirmationArchivageVisible = false) }
    }

    fun onOuvrirAjoutDialog() {
        _uiState.update { it.copy(isAjoutDialogVisible = true, formState = CompteFormState()) }
    }

    fun onOuvrirModificationDialog() {
        val compte = _uiState.value.compteSelectionne ?: return
        _uiState.update {
            it.copy(
                isModificationDialogVisible = true,
                isMenuContextuelVisible = false,
                formState = CompteFormState(
                    id = compte.id,
                    nom = compte.nom,
                    solde = compte.solde.toString(),
                    pretAPlacer = if (compte is CompteCheque) compte.pretAPlacer.toString() else "",
                    couleur = compte.couleur,
                    type = when (compte) {
                        is CompteCheque -> "Compte chèque"
                        is CompteCredit -> "Carte de crédit"
                        is CompteDette -> "Dette"
                        is CompteInvestissement -> "Investissement"
                    }
                )
            )
        }
    }

    fun onFermerTousLesDialogues() {
        _uiState.update {
            it.copy(
                isAjoutDialogVisible = false,
                isModificationDialogVisible = false,
                isReconciliationDialogVisible = false,
                isMenuContextuelVisible = false,
                isClavierNumeriqueVisible = false,
                isConfirmationArchivageVisible = false,
                enregistrementEnCours = false
            )
        }
    }

    fun onFormValueChange(nom: String? = null, type: String? = null, solde: String? = null, pretAPlacer: String? = null, couleur: String? = null) {
        _uiState.update { currentState ->
            currentState.copy(
                formState = currentState.formState.copy(
                    nom = nom ?: currentState.formState.nom,
                    type = type ?: currentState.formState.type,
                    solde = solde ?: currentState.formState.solde,
                    pretAPlacer = pretAPlacer ?: currentState.formState.pretAPlacer,
                    couleur = couleur ?: currentState.formState.couleur
                )
            )
        }
    }

    fun onFormCarteChange(solde: String?, taux: String?, emetteur: com.xburnsx.toutiebudget.data.modeles.EmetteurCarte?) {
        _uiState.update { s ->
            s.copy(
                formState = s.formState.copy(
                    soldeCarte = solde ?: s.formState.soldeCarte,
                    tauxCarte = taux ?: s.formState.tauxCarte,
                    emetteurCarte = emetteur ?: s.formState.emetteurCarte
                )
            )
        }
    }

    fun onTypePlacementChange(type: com.xburnsx.toutiebudget.data.modeles.TypeComptePlacement) {
        _uiState.update { s -> s.copy(formState = s.formState.copy(typePlacement = type)) }
    }

    /** IF15 : réactiver le même compte (même ID), avec tout son historique. */
    fun reactiverCompte(compte: Compte) {
        viewModelScope.lancerUneFois(verrouCompte) {
            val actif = when (compte) {
                is CompteCheque -> compte.copy(estArchive = false)
                is CompteCredit -> compte.copy(estArchive = false)
                is CompteDette -> compte.copy(estArchive = false)
                is CompteInvestissement -> compte.copy(estArchive = false)
            }
            compteRepository.mettreAJourCompte(actif)
                .onSuccess {
                    chargerComptes()
                    chargerComptesArchives()
                    realtimeSyncService.declencherMiseAJourComptes()
                    onCompteChange?.invoke()
                }
                .onFailure { e -> _uiState.update { it.copy(erreur = "Erreur de réactivation: ${e.message}") } }
        }
    }

    fun onSauvegarderCompte() {
        if (_uiState.value.enregistrementEnCours) return
        // CO6 : chaque sauvegarde ferme son dialogue elle-même, seulement après réussite
        if (_uiState.value.formState.id != null) {
            sauvegarderModification()
        } else {
            _uiState.update { it.copy(enregistrementEnCours = true) }
            creerNouveauCompte()
        }
    }

    private fun creerNouveauCompte() {
        viewModelScope.lancerUneFois(verrouCompte) {
            val formState = _uiState.value.formState
            val soldeInitial = formState.solde.toDoubleOrNull() ?: 0.0
            val nouveauCompte = when(formState.type) {
                "Compte chèque" -> CompteCheque(
                    nom = formState.nom,
                    solde = soldeInitial,
                    pretAPlacerRaw = soldeInitial, // Pour l'ajout, prêt à placer = solde initial
                    couleur = formState.couleur,
                    estArchive = false,
                    ordre = 0
                )
                "Carte de crédit" -> CompteCredit(
                    nom = formState.nom,
                    // CC3 : solde SIGNÉ tel que saisi (dette négative), sans inversion
                    soldeUtilise = formState.soldeCarte.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0,
                    couleur = formState.couleur,
                    estArchive = false,
                    ordre = 0,
                    limiteCredit = soldeInitial, // Le montant saisi devient la limite de crédit
                    tauxInteret = formState.tauxCarte.replace(',', '.').toDoubleOrNull(),
                    detailsCarteJson = com.xburnsx.toutiebudget.data.modeles.DetailsCarte(emetteur = formState.emetteurCarte).versJson()
                )
                "Dette" -> CompteDette(
                    id = IdGenerator.generateId(), // connu d'avance pour relier son enveloppe (DF11)
                    nom = formState.nom,
                    soldeDette = -soldeInitial, // dette négative à la création
                    estArchive = false,
                    ordre = 0,
                    montantInitial = soldeInitial,
                    prixTotal = soldeInitial
                )
                "Investissement" -> CompteInvestissement(
                    nom = formState.nom, solde = soldeInitial, couleur = formState.couleur, estArchive = false, ordre = 0,
                    // IA2 : la valeur à la création est la valeur de départ connue du suivi
                    detailsInvestissementJson = com.xburnsx.toutiebudget.data.modeles.DetailsInvestissement(
                        typeCompte = formState.typePlacement,
                        valeurDepart = soldeInitial,
                        dateDepart = com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit.ecrireDate(java.util.Date())
                    ).versJson()
                )
                else -> throw IllegalArgumentException("Type de compte inconnu")
            }
            // OPTIMISATION : Créer le compte immédiatement pour UX fluide
            compteRepository.creerCompte(nouveauCompte).onSuccess {
                // ✅ COMPTE CRÉÉ IMMÉDIATEMENT - L'utilisateur le voit tout de suite !
                
                // 🔄 Mettre à jour l'UI immédiatement
                chargerComptes()
                onFermerTousLesDialogues()
                realtimeSyncService.declencherMiseAJourBudget()
                realtimeSyncService.declencherMiseAJourComptes()
                onCompteChange?.invoke()

                // 🚀 CRÉATION EN ARRIÈRE-PLAN des catégories/enveloppes (transparent pour l'utilisateur)
                viewModelScope.launch {
                    try {
                        val utilisateurId = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
                            ?: return@launch
                        
                        // Création via repositories directs
                        // Utiliser les repositories Room injectés dans le ViewModel

                        when (nouveauCompte) {
                            is CompteDette -> {
                                // 1) Assurer la catégorie "Dettes" et la forcer en première position
                                val categories = categorieRepository.recupererToutesLesCategories().getOrElse { emptyList() }
                                val catDettes = categories.firstOrNull { it.nom.equals("Dettes", ignoreCase = true) } ?: run {
                                    val nouvelleCat = com.xburnsx.toutiebudget.data.modeles.Categorie(
                                        id = "",
                                        utilisateurId = utilisateurId,
                                        nom = "Dettes",
                                        ordre = 0
                                    )
                                    categorieRepository.creerCategorie(nouvelleCat).getOrNull() ?: nouvelleCat
                                }
                                
                                try {
                                    if (catDettes.ordre != 0) {
                                        categorieRepository.mettreAJourCategorie(catDettes.copy(ordre = 0))
                                    }
                                    val autres = categories.filter { it.id != catDettes.id }.sortedBy { it.ordre }
                                    autres.forEachIndexed { index, c ->
                                        val targetOrdre = index + 1
                                        if (c.ordre != targetOrdre) {
                                            categorieRepository.mettreAJourCategorie(c.copy(ordre = targetOrdre))
                                        }
                                    }
                                } catch (_: Exception) {}

                                // 2) Créer l'enveloppe associée à la dette
                                val env = com.xburnsx.toutiebudget.data.modeles.Enveloppe(
                                    id = IdGenerator.generateId(),
                                    utilisateurId = utilisateurId,
                                    nom = nouveauCompte.nom,
                                    categorieId = catDettes.id,
                                    estArchive = false,
                                    ordre = 0,
                                    typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun,
                                    objectifMontant = 0.0,
                                    dateObjectif = null,
                                    dateDebutObjectif = null,
                                    objectifJour = null,
                                    resetApresEcheance = false
                                )
                                enveloppeRepository.creerEnveloppe(env)
                                // DF11 : lien permanent dette -> enveloppe (par ID, plus par nom)
                                compteRepository.mettreAJourCompte(
                                    nouveauCompte.copy(
                                        utilisateurId = utilisateurId,
                                        detailsDetteJson = nouveauCompte.details.copy(enveloppeId = env.id).versJson()
                                    )
                                )
                            }
                            
                            is CompteCredit -> {
                                // Assurer catégories et ordre: Cartes de crédit (0), Dettes (1), autres (2+)
                                val categories = categorieRepository.recupererToutesLesCategories().getOrElse { emptyList() }
                                val catCartes = categories.firstOrNull { it.nom.equals("Cartes de crédit", ignoreCase = true) } ?: run {
                                    val nouvelleCat = com.xburnsx.toutiebudget.data.modeles.Categorie(
                                        id = "",
                                        utilisateurId = utilisateurId,
                                        nom = "Cartes de crédit",
                                        ordre = 0
                                    )
                                    categorieRepository.creerCategorie(nouvelleCat).getOrNull() ?: nouvelleCat
                                }
                                val catDettes = categories.firstOrNull { it.nom.equals("Dettes", ignoreCase = true) }

                                // Forcer ordres
                                try {
                                    if (catCartes.ordre != 0) categorieRepository.mettreAJourCategorie(catCartes.copy(ordre = 0))
                                    if (catDettes != null && catDettes.ordre != 1) categorieRepository.mettreAJourCategorie(catDettes.copy(ordre = 1))
                                    // Renuméroter autres à partir de 2
                                    val bloqueIds = setOf(catCartes.id) + (catDettes?.id?.let { setOf(it) } ?: emptySet())
                                    val autres = categories.filter { it.id !in bloqueIds }.sortedBy { it.ordre }
                                    autres.forEachIndexed { index, c ->
                                        val target = index + 2
                                        if (c.ordre != target) categorieRepository.mettreAJourCategorie(c.copy(ordre = target))
                                    }
                                } catch (_: Exception) {}

                                // Créer l'enveloppe associée à la carte
                                val env = com.xburnsx.toutiebudget.data.modeles.Enveloppe(
                                    id = IdGenerator.generateId(),
                                    utilisateurId = utilisateurId,
                                    nom = nouveauCompte.nom,
                                    categorieId = catCartes.id,
                                    estArchive = false,
                                    ordre = 0,
                                    typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun,
                                    objectifMontant = 0.0,
                                    dateObjectif = null,
                                    dateDebutObjectif = null,
                                    objectifJour = null,
                                    resetApresEcheance = false
                                )
                                enveloppeRepository.creerEnveloppe(env)
                            }
                            
                            is CompteCheque -> {
                                // Pour les comptes chèques, pas de catégorie spéciale nécessaire
                                // L'enveloppe sera créée automatiquement par le système d'enveloppes
                            }
                            
                            is CompteInvestissement -> {
                                // Pour les comptes d'investissement, pas de catégorie spéciale nécessaire
                                // L'enveloppe sera créée automatiquement par le système d'enveloppes
                            }
                        }
                        
                        // 🔄 Rafraîchir l'UI après création des catégories/enveloppes
                        realtimeSyncService.declencherMiseAJourBudget()
                        
                        // Attendre un peu pour que les SyncJob soient créés (comptes + enveloppes + allocations)
                        kotlinx.coroutines.delay(500L)
                        
                        // Forcer une deuxième mise à jour pour s'assurer que les SyncJob sont visibles
                        realtimeSyncService.declencherMiseAJourBudget()
                        
                        // Attendre encore un peu et forcer une troisième mise à jour
                        kotlinx.coroutines.delay(300L)
                        realtimeSyncService.declencherMiseAJourBudget()
                        
                    } catch (e: Exception) {
                        // 🚨 Gestion d'erreur silencieuse - ne pas bloquer l'utilisateur
                        // Les catégories/enveloppes seront créées plus tard si nécessaire
                    }
                }
            }.onFailure { e ->
                // CO6 : erreur visible, le formulaire reste ouvert avec les valeurs saisies
                _uiState.update { it.copy(enregistrementEnCours = false, erreur = "Erreur création: ${e.message}") }
            }
        }
    }

    /**
     * CO2 : copie ciblée, tous les autres champs du compte sont conservés.
     * CO9 : solde et « Prêt à placer » restent deux champs indépendants ; un changement est
     *       tracé comme « Modification du compte », jamais comme une dépense ou un revenu.
     * CO6 : le dialogue reste ouvert avec les valeurs saisies si la sauvegarde échoue.
     */
    private fun sauvegarderModification() {
        viewModelScope.lancerUneFois(verrouCompte) {
            try {
                val form = _uiState.value.formState
                val compteOriginal = _uiState.value.compteSelectionne ?: return@lancerUneFois
                val soldeDouble = form.solde.replace(',', '.').toDoubleOrNull()
                    ?: throw Exception("Solde invalide")
                val pretAPlacerDouble = if (compteOriginal is CompteCheque) {
                    form.pretAPlacer.replace(',', '.').toDoubleOrNull() ?: throw Exception("Prêt à placer invalide")
                } else 0.0

                val compteModifie = when (compteOriginal) {
                    is CompteCheque -> compteOriginal.copy(
                        nom = form.nom,
                        solde = soldeDouble,
                        pretAPlacerRaw = pretAPlacerDouble,
                        couleur = form.couleur
                    )
                    is CompteCredit -> compteOriginal.copy(nom = form.nom, soldeUtilise = soldeDouble, couleur = form.couleur)
                    is CompteDette -> compteOriginal.copy(nom = form.nom, soldeDette = soldeDouble)
                    is CompteInvestissement -> {
                        // IF7 : un changement de solde à la main est noté comme correction datée
                        val details = if (kotlin.math.abs(soldeDouble - compteOriginal.solde) > 0.005) {
                            compteOriginal.details.copy(
                                valorisations = compteOriginal.details.valorisations + com.xburnsx.toutiebudget.data.modeles.Valorisation(
                                    date = com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit.ecrireDate(java.util.Date()),
                                    valeur = soldeDouble,
                                    ancienneValeur = compteOriginal.solde,
                                    type = com.xburnsx.toutiebudget.data.modeles.TypeValorisation.CORRECTION
                                )
                            )
                        } else compteOriginal.details
                        compteOriginal.copy(nom = form.nom, solde = soldeDouble, couleur = form.couleur, detailsInvestissementJson = details.versJson())
                    }
                }

                _uiState.update { it.copy(enregistrementEnCours = true) }
                compteRepository.mettreAJourCompte(compteModifie).getOrThrow()

                val pretAvant = (compteOriginal as? CompteCheque)?.pretAPlacer ?: 0.0
                val pretApres = (compteModifie as? CompteCheque)?.pretAPlacer ?: 0.0
                val soldeChange = kotlin.math.abs(compteModifie.solde - compteOriginal.solde) > 0.005
                val pretChange = kotlin.math.abs(pretApres - pretAvant) > 0.005
                if (soldeChange || pretChange) {
                    try {
                        historiqueAllocationService.enregistrerAjustementCompte(
                            compte = compteModifie,
                            typeAction = "MODIFICATION_COMPTE",
                            description = "Modification manuelle de ${compteModifie.nom}" +
                                (if (soldeChange) " — solde ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(compteOriginal.solde)} → ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(compteModifie.solde)}" else "") +
                                (if (pretChange) " — prêt à placer ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(pretAvant)} → ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(pretApres)}" else ""),
                            montant = compteModifie.solde - compteOriginal.solde,
                            soldeAvant = compteOriginal.solde,
                            soldeApres = compteModifie.solde,
                            pretAPlacerAvant = pretAvant,
                            pretAPlacerApres = pretApres
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("ToutieBudget", "Trace de modification du compte non enregistrée: ${e.message}")
                    }
                }

                _uiState.update {
                    it.copy(isModificationDialogVisible = false, compteSelectionne = null, enregistrementEnCours = false)
                }
                chargerComptes()
                realtimeSyncService.declencherMiseAJourBudget()
                realtimeSyncService.declencherMiseAJourComptes()
                onCompteChange?.invoke()
            } catch (e: Exception) {
                _uiState.update { it.copy(enregistrementEnCours = false, erreur = "Erreur modification: ${e.message}") }
            }
        }
    }

    // ===== GESTION DE LA RÉORGANISATION DES COMPTES =====

    /**
     * Active ou désactive le mode de réorganisation des comptes.
     */
    fun onToggleModeReorganisation() {
        _uiState.update {
            it.copy(
                isModeReorganisation = !it.isModeReorganisation,
                compteEnDeplacement = null
            )
        }
    }

    /**
     * Déplace un compte vers une nouvelle position.
     */
    fun onDeplacerCompte(compteId: String, nouvellePosition: Int) {
        viewModelScope.launch {
            try {
                // Obtenir tous les comptes non archivés
                val tousComptes = compteRepository.recupererTousLesComptes().getOrElse { emptyList() }
                val comptesActifs = tousComptes.filter { !it.estArchive }.sortedBy { it.ordre }

                // Trouver le compte à déplacer
                val compteADeplacer = comptesActifs.find { it.id == compteId }
                    ?: return@launch

                // Trouver le type de compte pour ne déplacer que dans le même groupe
                val typeCompteADeplacer = when (compteADeplacer) {
                    is CompteCheque -> "Comptes chèques"
                    is CompteCredit -> "Cartes de crédit"
                    is CompteDette -> "Dettes"
                    is CompteInvestissement -> "Investissements"
                }

                // Filtrer seulement les comptes du même type
                val comptesMemeType = comptesActifs.filter { compte ->
                    when (compte) {
                        is CompteCheque -> typeCompteADeplacer == "Comptes chèques"
                        is CompteCredit -> typeCompteADeplacer == "Cartes de crédit"
                        is CompteDette -> typeCompteADeplacer == "Dettes"
                        is CompteInvestissement -> typeCompteADeplacer == "Investissements"
                    }
                }.sortedBy { it.ordre }

                // Calculer les nouveaux ordres dans le groupe
                val nouveauxComptes = calculerNouveauxOrdresComptes(
                    comptesMemeType,
                    compteADeplacer,
                    nouvellePosition
                )

                // Mettre à jour tous les comptes modifiés
                val misesAJour = nouveauxComptes.filter { nouveau ->
                    val ancien = comptesMemeType.find { it.id == nouveau.id }
                    ancien?.ordre != nouveau.ordre
                }

                // Synchroniser avec PocketBase
                misesAJour.forEach { compte ->
                    compteRepository.mettreAJourCompte(compte).onFailure { erreur ->
                        _uiState.update { it.copy(erreur = "Erreur déplacement: ${erreur.message}") }
                        return@launch
                    }
                }

                // Recharger les données
                chargerComptes()

                // 🔥 FORCER LA RECOMPOSITION DE L'INTERFACE
                _uiState.update { currentState ->
                    currentState.copy(versionUI = currentState.versionUI + 1)
                }

                // Notifier les autres ViewModels
                realtimeSyncService.declencherMiseAJourComptes()

            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur: ${e.message}") }
            }
        }
    }

    /**
     * Calcule les nouveaux ordres après déplacement d'un compte.
     */
    private fun calculerNouveauxOrdresComptes(
        comptesOrdonnes: List<Compte>,
        compteADeplacer: Compte,
        nouvellePosition: Int
    ): List<Compte> {
        val listeModifiable = comptesOrdonnes.toMutableList()

        // Retirer le compte de sa position actuelle
        val positionActuelle = listeModifiable.indexOfFirst { it.id == compteADeplacer.id }
        if (positionActuelle == -1) return comptesOrdonnes

        listeModifiable.removeAt(positionActuelle)

        // Insérer à la nouvelle position
        val positionCible = nouvellePosition.coerceIn(0, listeModifiable.size)
        listeModifiable.add(positionCible, compteADeplacer)

        // Recalculer tous les ordres
        return listeModifiable.mapIndexed { index, compte ->
            when (compte) {
                is CompteCheque -> compte.copy(ordre = index, collection = "comptes_cheques")
                is CompteCredit -> compte.copy(ordre = index, collection = "comptes_credits")
                is CompteDette -> compte.copy(ordre = index, collection = "comptes_dettes")
                is CompteInvestissement -> compte.copy(ordre = index, collection = "comptes_investissement")
            }
        }
    }
}
