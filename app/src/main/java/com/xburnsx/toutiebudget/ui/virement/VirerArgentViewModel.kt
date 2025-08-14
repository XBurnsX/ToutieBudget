// chemin/simule: /ui/virement/VirerArgentViewModel.kt
// Dépendances: ViewModel, Repositories, Services, Modèles de données

package com.xburnsx.toutiebudget.ui.virement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.domain.services.ArgentService
import com.xburnsx.toutiebudget.domain.services.ValidationProvenanceService
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Calendar

/**
 * ViewModel pour l'écran de virement d'argent.
 * Gère les virements entre comptes et enveloppes.
 */
class VirerArgentViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val categorieRepository: CategorieRepository,
    private val argentService: ArgentService,
    private val realtimeSyncService: RealtimeSyncService,
    private val validationProvenanceService: ValidationProvenanceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VirerArgentUiState())
    val uiState: StateFlow<VirerArgentUiState> = _uiState.asStateFlow()

    // Données mises en cache
    private var allComptes: List<Compte> = emptyList()
    private var allEnveloppes: List<Enveloppe> = emptyList()
    private var allAllocations: List<AllocationMensuelle> = emptyList()
    private var allCategories: List<Categorie> = emptyList()

    init {
        chargerDonneesInitiales()
    }

    /**
     * Recharge les données depuis les repositories.
     * À appeler quand l'écran redevient visible ou après des modifications.
     */
    fun rechargerDonnees() {
        chargerDonneesInitiales()
    }

    // ===== GESTION DU MODE =====

    /**
     * Change le mode de virement (Enveloppes ou Comptes) et réinitialise l'état.
     */
    fun changerMode(nouveauMode: VirementMode) {
        _uiState.update {
            it.copy(
                mode = nouveauMode,
                sourceSelectionnee = null,
                destinationSelectionnee = null,
                montant = "",
                erreur = null,
                isVirementButtonEnabled = false
            )
        }
        configurerSourcesEtDestinationsPourMode()
    }

    // ===== CHARGEMENT DES DONNÉES =====

    /**
     * Charge toutes les données nécessaires depuis les repositories une seule fois.
     */
    private fun chargerDonneesInitiales(): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {

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

                // Configurer les sources et destinations pour le mode initial
                configurerSourcesEtDestinationsPourMode()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        erreur = "Erreur de chargement: ${e.message}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Configure les listes de sources et de destinations en fonction du mode de virement actuel.
     */
    private fun configurerSourcesEtDestinationsPourMode() {
        when (_uiState.value.mode) {
            VirementMode.ENVELOPPES -> configurerPourModeEnveloppes()
            VirementMode.COMPTES -> configurerPourModeComptes()
        }
    }

    /**
     * Configure les sources et destinations pour le virement entre "Prêt à placer" et enveloppes.
     */
    private fun configurerPourModeEnveloppes() {

        // Créer les items de comptes (tous les comptes chèque)
        val itemsComptes = allComptes
            .filterIsInstance<CompteCheque>()
            .map { ItemVirement.CompteItem(it) }

        // Créer les enveloppes UI
        val enveloppesUi = construireEnveloppesUi()

        val enveloppesOrganisees = OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(allCategories, allEnveloppes)

        // Grouper les sources (comptes + enveloppes avec argent)
        val sourcesEnveloppes = LinkedHashMap<String, List<ItemVirement>>()
        enveloppesOrganisees.forEach { (nomCategorie, enveloppes) ->
            val enveloppesAvecArgent = enveloppes
                .mapNotNull { env -> enveloppesUi.find { it.id == env.id } }
                .filter { it.solde > 0 }
                .map { ItemVirement.EnveloppeItem(it) }
            if (enveloppesAvecArgent.isNotEmpty()) {
                sourcesEnveloppes[nomCategorie] = enveloppesAvecArgent
            }
        }

        val sources = LinkedHashMap<String, List<ItemVirement>>().apply {
            put("Prêt à placer", itemsComptes)
            putAll(sourcesEnveloppes)
        }

        // Grouper les destinations (comptes + toutes les enveloppes)
        val destinationsEnveloppes = LinkedHashMap<String, List<ItemVirement>>()
        enveloppesOrganisees.forEach { (nomCategorie, enveloppes) ->
            val items = enveloppes
                .mapNotNull { env -> enveloppesUi.find { it.id == env.id } }
                .map { ItemVirement.EnveloppeItem(it) }
            if (items.isNotEmpty()) {
                destinationsEnveloppes[nomCategorie] = items
            }
        }

        val destinations = LinkedHashMap<String, List<ItemVirement>>().apply {
            put("Prêt à placer", itemsComptes)
            putAll(destinationsEnveloppes)
        }

        _uiState.update {
            it.copy(
                sourcesDisponibles = sources,
                destinationsDisponibles = destinations
            )
        }
    }

    /**
     * Configure les sources et destinations pour le virement entre comptes,
     * en les groupant par catégorie et en respectant l'ordre.
     * Les dettes sont exclues.
     */
    private fun configurerPourModeComptes() {

        val comptesGroupes = allComptes
            .filter { it !is CompteDette } // Exclure les dettes
            .sortedBy { it.ordre }
            .groupBy(
                keySelector = { compte ->
                    // Clé de groupement : le nom de la catégorie du compte
                    when (compte) {
                        is CompteCheque -> "Comptes chèque"
                        is CompteCredit -> "Cartes de crédit"
                        is CompteInvestissement -> "Investissement"
                        else -> "Autres" // Une catégorie par défaut si nécessaire
                    }
                },
                valueTransform = { compte ->
                    // Transformation de la valeur : créer un ItemVirement
                    ItemVirement.CompteItem(compte)
                }
            )

        // Assurer que les catégories principales sont toujours présentes et dans le bon ordre
        val sourcesFinales = linkedMapOf<String, List<ItemVirement>>().apply {
            put("Comptes chèque", comptesGroupes["Comptes chèque"] ?: emptyList())
            put("Cartes de crédit", comptesGroupes["Cartes de crédit"] ?: emptyList())
            put("Investissement", comptesGroupes["Investissement"] ?: emptyList())
            // Ajouter d'autres groupes s'ils existent
            comptesGroupes.filterKeys { it !in this.keys }.forEach { (key, value) ->
                put(key, value)
            }
        }

        _uiState.update {
            it.copy(
                sourcesDisponibles = sourcesFinales,
                destinationsDisponibles = sourcesFinales,
                isLoading = false
            )
        }
    }

    /**
     * Construit la liste des enveloppes UI avec leurs allocations.
     * Même logique que dans AjoutTransactionViewModel.
     */
    private fun construireEnveloppesUi(): List<EnveloppeUi> {
        return allEnveloppes.filter { !it.estArchive }.map { enveloppe ->
            val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
            
            // 🎨 RÉCUPÉRER LA VRAIE COULEUR DU COMPTE SOURCE (comme dans AjoutTransactionViewModel)
            val compteSource = allocation?.compteSourceId?.let { compteId ->
                allComptes.find { it.id == compteId }
            }
            
            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = allocation?.solde ?: 0.0,
                depense = allocation?.depense ?: 0.0,
                alloue = allocation?.alloue ?: 0.0, // Alloué ce mois
                alloueCumulatif = allocation?.alloue ?: 0.0, // ← NOUVEAU : Pour simplifier, on utilise la même valeur
                objectif = enveloppe.objectifMontant,
                couleurProvenance = compteSource?.couleur, // ✅ VRAIE COULEUR DU COMPTE SOURCE
                statutObjectif = StatutObjectif.GRIS  // Simplifié pour le virement
            )
        }.sortedBy { enveloppe ->
            val categorie = allCategories.find { cat -> 
                allEnveloppes.find { it.id == enveloppe.id }?.categorieId == cat.id 
            }
            categorie?.nom ?: "Sans catégorie"
        }
    }

    // ===== GESTION DES SÉLECTEURS =====

    /**
     * Ouvre le sélecteur de source ou destination.
     */
    fun ouvrirSelecteur(type: SelecteurOuvert) { 
        _uiState.update { it.copy(selecteurOuvert = type) } 
    }

    /**
     * Sélectionne un item (source ou destination) selon le sélecteur ouvert.
     */
    fun onItemSelected(item: ItemVirement) {
        val currentSelecteur = _uiState.value.selecteurOuvert
        if (currentSelecteur == SelecteurOuvert.SOURCE) {
            _uiState.update {
                it.copy(
                    sourceSelectionnee = item,
                    erreur = null,
                    selecteurOuvert = SelecteurOuvert.AUCUN
                )
            }
        } else if (currentSelecteur == SelecteurOuvert.DESTINATION) {
            val source = _uiState.value.sourceSelectionnee
            if (source != null && memeItem(source, item)) {
                _uiState.update {
                    it.copy(erreur = "La source et la destination ne peuvent pas être identiques.")
                }
            } else {
                _uiState.update {
                    it.copy(
                        destinationSelectionnee = item,
                        erreur = null,
                        selecteurOuvert = SelecteurOuvert.AUCUN
                    )
                }
            }
        }
        updateVirementButtonState()
    }

    /**
     * Sélectionne une enveloppe pour la source ou la destination.
     */
    fun onEnveloppeSelected(enveloppeUi: EnveloppeUi, isSource: Boolean) {
        val item = ItemVirement.EnveloppeItem(enveloppeUi)
        
        if (isSource) {
            _uiState.update { 
                it.copy(
                    sourceSelectionnee = item,
                    erreur = null
                ) 
            }
        } else {
            // Vérifier qu'on ne vire pas vers la même source
            val source = _uiState.value.sourceSelectionnee
            if (source != null && memeItem(source, item)) {
                _uiState.update { 
                    it.copy(erreur = "La source et la destination ne peuvent pas être identiques.") 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        destinationSelectionnee = item,
                        erreur = null
                    ) 
                }
            }
        }
        updateVirementButtonState()
    }

    // ===== GESTION DU MONTANT =====

    /**
     * Met à jour le montant saisi par l'utilisateur.
     * Le montant est reçu en format centimes depuis ChampArgent.
     */
    fun onMontantChange(nouveauMontantEnCentimes: String) {
        // Limiter à 8 chiffres maximum pour éviter les débordements
        if (nouveauMontantEnCentimes.length <= 8) {
            _uiState.update {
                it.copy(
                    montant = nouveauMontantEnCentimes,
                    erreur = null  // Effacer les erreurs lors de la saisie
                )
            }
            updateVirementButtonState() // Mettre à jour l'état du bouton
        }
    }

    /**
     * Met à jour l'état d'activation du bouton de virement en fonction de l'état de l'UI.
     */
    private fun updateVirementButtonState() {
        val state = _uiState.value
        val montantValide = (state.montant.toLongOrNull() ?: 0L) > 0
        val isEnabled = state.sourceSelectionnee != null &&
                        state.destinationSelectionnee != null &&
                        montantValide

        _uiState.update { it.copy(isVirementButtonEnabled = isEnabled) }
    }


    // ===== EXÉCUTION DU VIREMENT =====

    /**
     * Exécute le virement d'argent entre source et destination.
     */
    fun onVirementExecute() {
        val state = _uiState.value
        val source = state.sourceSelectionnee
        val destination = state.destinationSelectionnee
        val montantEnCentimes = state.montant.toLongOrNull() ?: 0L
        val montantEnDollars = montantEnCentimes / 100.0

        // Validations
        if (source == null) {
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.SOURCE_NULL) }
            return
        }
        
        if (destination == null) {
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.DESTINATION_NULL) }
            return
        }
        
        if (montantEnCentimes <= 0) {
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.MONTANT_INVALIDE) }
            return
        }

        // Vérifier que la source et destination ne sont pas identiques
        if (memeItem(source, destination)) {
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.SOURCE_DESTINATION_IDENTIQUES) }
            return
        }

        // Vérifier que la source a assez d'argent (permettre les soldes négatifs)
        val soldeSource = obtenirSoldeItem(source)
        
        // Permettre les transferts même si le solde devient négatif
        // if (soldeSource < montantEnDollars) {
        //     _uiState.update {
        //         it.copy(erreur = VirementErrorMessages.General.soldeInsuffisant(soldeSource))
        //     }
        //     return
        // }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, erreur = null) }

                // VALIDATION DE PROVENANCE SELON LE TYPE DE VIREMENT
                val validationResult = validerProvenanceVirement(source, destination)

                if (validationResult.isFailure) {
                    val messageErreur = validationResult.exceptionOrNull()?.message ?: "Erreur de validation inconnue"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            erreur = messageErreur
                        )
                    }
                    return@launch
                }

                // Effectuer le virement selon les types source/destination
                val virementResult = when {
                    // Compte vers Compte - VIREMENT EXTERNE (crée des transactions)
                    source is ItemVirement.CompteItem && destination is ItemVirement.CompteItem -> {
                        argentService.effectuerVirementEntreComptes(
                            compteSourceId = source.compte.id,
                            compteDestId = destination.compte.id,
                            montant = montantEnDollars,
                            nomCompteSource = source.compte.nom,
                            nomCompteDest = destination.compte.nom
                        )
                    }

                    // Compte vers Enveloppe (Prêt à placer vers enveloppe) - VIREMENT INTERNE (pas de transaction)
                    source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                        val enveloppeDestination = allEnveloppes.find { it.id == destination.enveloppe.id }
                        if (enveloppeDestination == null) {
                            Result.failure(Exception(VirementErrorMessages.PretAPlacerVersEnveloppe.enveloppeIntrouvable(destination.enveloppe.nom)))
                        } else {
                            val result = argentService.allouerArgentEnveloppeSansTransaction(
                                enveloppeId = destination.enveloppe.id,
                                compteSourceId = source.compte.id,
                                collectionCompteSource = source.compte.collection,
                                montant = montantEnDollars,
                                mois = Date()
                            )
                            // 🔥 FORCER LA RE-FUSION APRÈS OPÉRATIONS ArgentService !
                            val moisActuel = Date()
                            try {
                                allocationMensuelleRepository.recupererOuCreerAllocation(destination.enveloppe.id, moisActuel)
                            } catch (_: Exception) {
                                // Erreur silencieuse
                            }
                            result
                        }
                    }

                    // Enveloppe vers Compte (Enveloppe vers Prêt à placer) - VIREMENT INTERNE (pas de transaction)
                    source is ItemVirement.EnveloppeItem && destination is ItemVirement.CompteItem -> {
                        val enveloppeSource = allEnveloppes.find { it.id == source.enveloppe.id }
                        if (enveloppeSource == null) {
                            Result.failure(Exception(VirementErrorMessages.EnveloppeVersPretAPlacer.enveloppeSourceIntrouvable(source.enveloppe.nom)))
                        } else {
                            val result = argentService.effectuerVirementEnveloppeVersCompteSansTransaction(
                                enveloppe = enveloppeSource,
                                compte = destination.compte,
                                montant = montantEnDollars
                            )
                            // 🔥 FORCER LA RE-FUSION APRÈS OPÉRATIONS ArgentService !
                            val moisActuel = Date()
                            try {
                                allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeSource.id, moisActuel)
                            } catch (_: Exception) {
                                // Erreur silencieuse
                            }
                            result
                        }
                    }

                    // Enveloppe vers Enveloppe OU Enveloppe vers Prêt à placer - VALIDATION APPLIQUÉE
                    source is ItemVirement.EnveloppeItem && destination is ItemVirement.EnveloppeItem -> {
                        if (estPretAPlacer(source.enveloppe)) {
                            // 🎯 SOURCE EST UN PRÊT À PLACER - VIREMENT COMPTE VERS ENVELOPPE
                            val compteSourceId = extraireCompteIdDepuisPretAPlacer(source.enveloppe.id)
                            val compteSource = allComptes.find { it.id == compteSourceId }
                            
                            if (compteSource == null) {
                                Result.failure(Exception("Compte source introuvable pour le prêt à placer"))
                            } else {
                                // 🎯 UTILISER LA MÊME LOGIQUE QUI FONCTIONNE DANS LE BUDGET
                                
                                // 1. Mettre à jour le compte source (retirer de "prêt à placer")
                                val ancienPretAPlacer = (compteSource as CompteCheque).pretAPlacer
                                val nouveauPretAPlacer = ancienPretAPlacer - montantEnDollars
                                
                                val compteModifie = compteSource.copy(
                                    pretAPlacerRaw = nouveauPretAPlacer,
                                    collection = compteSource.collection // Assurer qu'on a une collection
                                )
                                
                                val resultCompte = compteRepository.mettreAJourCompte(compteModifie)
                                if (resultCompte.isSuccess) {
                                } else {
                                }
                                if (resultCompte.isFailure) {
                                    Result.failure(Exception("Erreur lors de la mise à jour du compte: ${resultCompte.exceptionOrNull()?.message}"))
                                } else {
                                    // 2. ✅ CRÉER une allocation additive (évite les doublons)
                                    val calendrier = Calendar.getInstance().apply {
                                        time = Date()
                                        set(Calendar.DAY_OF_MONTH, 1)
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    val premierJourMois = calendrier.time

                                    // ✅ Récupérer ou créer l'allocation pour ce mois
                                    val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(destination.enveloppe.id, premierJourMois)
                                    
                                    // ✅ FUSIONNER : Mettre à jour l'allocation existante au lieu de créer un doublon
                                    val allocationMiseAJour = allocationExistante.copy(
                                        solde = allocationExistante.solde + montantEnDollars,
                                        alloue = allocationExistante.alloue + montantEnDollars,
                                        // ✅ PROVENANCE : TOUJOURS changer quand le solde était à 0 (nouveau départ)
                                        compteSourceId = if (allocationExistante.solde <= 0.01) compteSource.id else allocationExistante.compteSourceId,
                                        collectionCompteSource = if (allocationExistante.solde <= 0.01) compteSource.collection else allocationExistante.collectionCompteSource
                                    )
                                    
                                    // Logique de provenance
                                    
                                    try {
                                        allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)
                                        // 🔥 FORCER LA RE-FUSION APRÈS MODIFICATION POUR ÉVITER LES DOUBLONS !
                                        allocationMensuelleRepository.recupererOuCreerAllocation(destination.enveloppe.id, premierJourMois)
                                        Result.success(Unit)
                                    } catch (e: Exception) {
                                        Result.failure<Unit>(Exception("Erreur lors de la mise à jour de l'allocation: ${e.message}"))
                                    }
                                }
                            }
                        } else if (estPretAPlacer(destination.enveloppe)) {
                            // Destination est un "Prêt à placer" - VIREMENT INTERNE (pas de transaction)
                            val compteId = extraireCompteIdDepuisPretAPlacer(destination.enveloppe.id)
                            val compteDestination = allComptes.find { it.id == compteId }
                            if (compteDestination == null) {
                                Result.failure(Exception(VirementErrorMessages.EnveloppeVersPretAPlacer.COMPTE_DESTINATION_INTROUVABLE))
                            } else {
                                val result = argentService.effectuerVirementEnveloppeVersPretAPlacer(
                                    enveloppeId = source.enveloppe.id,
                                    compteId = compteDestination.id,
                                    montant = montantEnDollars
                                )
                                // 🔥 FORCER LA RE-FUSION APRÈS OPÉRATIONS ArgentService !
                                val moisActuel = Date()
                                try {
                                    allocationMensuelleRepository.recupererOuCreerAllocation(source.enveloppe.id, moisActuel)
                                } catch (_: Exception) {
                                    // Erreur silencieuse
                                }
                                result
                            }
                        } else {
                            // Cas normal: Enveloppe vers Enveloppe
                            val enveloppeSource = allEnveloppes.find { it.id == source.enveloppe.id }
                            val enveloppeDestination = allEnveloppes.find { it.id == destination.enveloppe.id }

                            when {
                                enveloppeSource == null -> Result.failure(Exception(VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_SOURCE_INTROUVABLE))
                                enveloppeDestination == null -> Result.failure(Exception(VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_DESTINATION_INTROUVABLE))
                                else -> {
                                    // 🎯 VIREMENT ENVELOPPE VERS ENVELOPPE AVEC RESPECT DE LA PROVENANCE
                                    val moisActuel = Date()
                                    
                                    // 1. Récupérer l'allocation de l'enveloppe source pour connaître la provenance
                                    val allocationSourceResult = enveloppeRepository.recupererAllocationMensuelle(source.enveloppe.id, moisActuel)
                                    
                                    if (allocationSourceResult.isFailure) {
                                        Result.failure(Exception("Impossible de récupérer l'allocation de l'enveloppe source"))
                                    } else {
                                        val allocationSource = allocationSourceResult.getOrNull()
                                        if (allocationSource == null) {
                                            Result.failure(Exception("Aucune allocation trouvée pour l'enveloppe source"))
                                        } else {
                                            // 2. ✅ S'assurer qu'une allocation de base existe pour la source

                                            // ✅ FUSIONNER : Mettre à jour l'allocation SOURCE (diminue solde + alloué)
                                            val allocationSourceMiseAJour = allocationSource.copy(
                                                solde = allocationSource.solde - montantEnDollars,        // ← RETIRE du solde
                                                alloue = allocationSource.alloue - montantEnDollars       // ← RETIRE de l'allocation
                                            )
                                            
                                            try {
                                                allocationMensuelleRepository.mettreAJourAllocation(allocationSourceMiseAJour)
                                                // 🔥 FORCER LA RE-FUSION SOURCE APRÈS MODIFICATION !
                                                allocationMensuelleRepository.recupererOuCreerAllocation(source.enveloppe.id, moisActuel)
                                                
                                                // 3. ✅ Récupérer ou créer l'allocation pour la destination
                                                val allocationDestExistante = allocationMensuelleRepository.recupererOuCreerAllocation(destination.enveloppe.id, moisActuel)
                                                
                                                // ✅ FUSIONNER : Mettre à jour l'allocation DESTINATION (augmente solde + alloué)
                                                val allocationDestMiseAJour = allocationDestExistante.copy(
                                                    solde = allocationDestExistante.solde + montantEnDollars,        // ← AJOUTE au solde
                                                    alloue = allocationDestExistante.alloue + montantEnDollars,       // ← AJOUTE à l'allocation
                                                    compteSourceId = allocationSource.compteSourceId, // ← MÊME PROVENANCE
                                                    collectionCompteSource = allocationSource.collectionCompteSource
                                                )
                                                
                                                allocationMensuelleRepository.mettreAJourAllocation(allocationDestMiseAJour)
                                                // 🔥 FORCER LA RE-FUSION DESTINATION APRÈS MODIFICATION !
                                                allocationMensuelleRepository.recupererOuCreerAllocation(destination.enveloppe.id, moisActuel)
                                                Result.success(Unit)
                                            } catch (e: Exception) {
                                                Result.failure<Unit>(Exception("Erreur lors du virement: ${e.message}"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Result.failure<Unit>(Exception(VirementErrorMessages.General.TYPE_VIREMENT_NON_SUPPORTE))
                    }
                }

                // Vérifier le résultat
                virementResult.getOrThrow()

                // ⚡ SUCCÈS IMMÉDIAT - NAVIGATION RAPIDE
                _uiState.update {
                    it.copy(
                        virementReussi = true,
                        erreur = null,
                        isLoading = false
                    )
                }

                // 🔄 RECHARGER DONNÉES + MISE À JOUR BUDGET (EN PARALLÈLE, non-bloquant)
                launch {
                    println("DEBUG: Virement réussi, rechargement des données...")
                    chargerDonneesInitiales().join()
                    
                    // ⏱️ Délai plus long pour s'assurer que les données sont bien sauvegardées
                    println("DEBUG: Attente de 1 seconde avant mise à jour du budget...")
                    kotlinx.coroutines.delay(1000)
                    
                    println("DEBUG: Déclenchement de la mise à jour du budget...")
                    realtimeSyncService.declencherMiseAJourBudget()
                    println("DEBUG: Mise à jour du budget déclenchée")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        virementReussi = false,
                        erreur = e.message ?: "Erreur inconnue lors du virement"
                    )
                }
            }
        }
    }

    /**
     * Valide la provenance selon le type de virement
     */
    private suspend fun validerProvenanceVirement(source: ItemVirement, destination: ItemVirement): Result<Unit> {
        val mois = Date()

        return when {
            // Compte vers Compte - AUCUNE VALIDATION NÉCESSAIRE
            source is ItemVirement.CompteItem && destination is ItemVirement.CompteItem -> {
                Result.success(Unit)
            }

            // Compte vers Enveloppe (Prêt à placer vers enveloppe) - VALIDATION STRICTE
            source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                if (estPretAPlacer(destination.enveloppe)) {
                    Result.failure(Exception("❌ ERREUR DE CONFIGURATION\n\nImpossible de virer d'un compte vers un prêt à placer.\nVeuillez sélectionner une enveloppe comme destination."))
                } else {
                    validationProvenanceService.validerAjoutArgentEnveloppe(
                        enveloppeId = destination.enveloppe.id,
                        compteSourceId = source.compte.id,
                        mois = mois
                    )
                }
            }

            // Enveloppe vers Compte - VALIDATION STRICTE
            source is ItemVirement.EnveloppeItem && destination is ItemVirement.CompteItem -> {
                if (estPretAPlacer(source.enveloppe)) {
                    // Source est un prêt à placer virtuel - Pas de validation de provenance nécessaire
                    Result.success(Unit)
                } else {
                    // Enveloppe normale vers Prêt à placer
                    val vraiCompteId = extraireCompteIdDepuisPretAPlacer(destination.compte.id)
                    validationProvenanceService.validerTransfertEnveloppeVersCompte(
                        enveloppeSourceId = source.enveloppe.id,
                        compteCibleId = vraiCompteId, // ← UTILISER L'ID DU VRAI COMPTE
                        mois = mois
                    )
                }
            }

            // Enveloppe vers Enveloppe (incluant vers Prêt à placer) - VALIDATION STRICTE
            source is ItemVirement.EnveloppeItem && destination is ItemVirement.EnveloppeItem -> {
                if (estPretAPlacer(source.enveloppe)) {
                    // Source est un prêt à placer virtuel
                    if (estPretAPlacer(destination.enveloppe)) {
                        // Prêt à placer vers Prêt à placer - cas impossible, mais on gère l'erreur
                        Result.failure(Exception("Impossible de virer d'un prêt à placer vers un autre prêt à placer"))
                                    } else {
                    // Prêt à placer vers Enveloppe - VALIDATION DE PROVENANCE REQUISE
                    val vraiCompteId = extraireCompteIdDepuisPretAPlacer(source.enveloppe.id)
                    validationProvenanceService.validerAjoutArgentEnveloppe(
                        enveloppeId = destination.enveloppe.id,
                        compteSourceId = vraiCompteId, // ← UTILISER L'ID DU VRAI COMPTE
                        mois = mois
                    )
                }
                } else if (estPretAPlacer(destination.enveloppe)) {
                    // Enveloppe normale vers Prêt à placer
                    val compteId = extraireCompteIdDepuisPretAPlacer(destination.enveloppe.id)
                    validationProvenanceService.validerTransfertEnveloppeVersCompte(
                        enveloppeSourceId = source.enveloppe.id,
                        compteCibleId = compteId,
                        mois = mois
                    )
                } else {
                    // Enveloppe normale vers Enveloppe normale
                    validationProvenanceService.validerTransfertEntreEnveloppes(
                        enveloppeSourceId = source.enveloppe.id,
                        enveloppeCibleId = destination.enveloppe.id,
                        mois = mois
                    )
                }
            }

            else -> {
                Result.failure(Exception("Type de virement non supporté"))
            }
        }
    }
    // ===== UTILITAIRES =====

    /**
     * Obtient le solde actuel d'un ItemVirement.
     * Pour les prêts à placer (EnveloppeUi virtuelles), extrait le montant du compte source.
     */
    private fun obtenirSoldeItem(item: ItemVirement): Double {
        return when (item) {
            is ItemVirement.CompteItem -> {
                // Pour les comptes chèque, utiliser le montant prêt à placer au lieu du solde total
                if (item.compte is CompteCheque) {
                    item.compte.pretAPlacer
                } else {
                    item.compte.solde
                }
            }
            is ItemVirement.EnveloppeItem -> {
                // Vérifier si c'est un prêt à placer virtuel (ID commence par "pret_a_placer_")
                if (estPretAPlacer(item.enveloppe)) {
                    // Extraire l'ID du compte et récupérer le montant prêt à placer
                    val compteId = extraireCompteIdDepuisPretAPlacer(item.enveloppe.id)
                    val compte = allComptes.find { it.id == compteId }
                    if (compte is CompteCheque) {
                        compte.pretAPlacer
                    } else {
                        item.enveloppe.solde // Fallback vers le solde de l'enveloppe virtuelle
                    }
                } else {
                    item.enveloppe.solde // Enveloppe normale
                }
            }
        }
    }

    /**
     * Vérifie si deux items sont identiques.
     */
    private fun memeItem(item1: ItemVirement, item2: ItemVirement): Boolean {
        return when {
            item1 is ItemVirement.CompteItem && item2 is ItemVirement.CompteItem ->
                item1.compte.id == item2.compte.id
            item1 is ItemVirement.EnveloppeItem && item2 is ItemVirement.EnveloppeItem ->
                item1.enveloppe.id == item2.enveloppe.id
            else -> false
        }
    }

    /**
     * Vérifie si une EnveloppeUi représente un "Prêt à placer".
     */
    private fun estPretAPlacer(enveloppe: EnveloppeUi): Boolean {
        return enveloppe.id.startsWith("pret_a_placer_")
    }

    /**
     * Extrait l'ID du compte depuis un ID "Prêt à placer".
     */
    private fun extraireCompteIdDepuisPretAPlacer(enveloppeId: String): String {
        return enveloppeId.removePrefix("pret_a_placer_")
    }

    /**
     * Efface l'erreur actuelle.
     */
    fun effacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }

    /**
     * Réinitialise l'état de succès du virement pour fermer l'écran.
     */
    fun onVirementReussiHandled() {
        // 🧹 NETTOYAGE COMPLET DE LA PAGE après virement réussi
        _uiState.update { 
            it.copy(
                virementReussi = false,
                montant = "",                           // ← VIDER le montant
                sourceSelectionnee = null,              // ← VIDER la source
                destinationSelectionnee = null,         // ← VIDER la destination
                erreur = null,                          // ← VIDER les erreurs
                selecteurOuvert = SelecteurOuvert.AUCUN, // ← FERMER les sélecteurs
                isVirementButtonEnabled = false         // ← DÉSACTIVER le bouton
            )
        }
    }

    // ===== VALIDATION =====

}
