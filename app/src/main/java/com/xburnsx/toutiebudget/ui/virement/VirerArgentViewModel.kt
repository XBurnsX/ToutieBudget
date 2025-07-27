// chemin/simule: /ui/virement/VirerArgentViewModel.kt
// Dépendances: ViewModel, Repositories, Services, Modèles de données

package com.xburnsx.toutiebudget.ui.virement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
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

/**
 * ViewModel pour l'écran de virement d'argent.
 * Gère les virements entre comptes et enveloppes.
 */
class VirerArgentViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
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
    private fun chargerDonneesInitiales() {
        viewModelScope.launch {
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
        // Créer les items de comptes (seulement les comptes chèque pour l'instant)
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
     * Charge toutes les sources et destinations possibles pour les virements.
     */
    private fun chargerSourcesEtDestinations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Charger toutes les données
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

                // Créer les items de comptes (seulement les comptes chèque pour l'instant)
                val itemsComptes = allComptes
                    .filterIsInstance<CompteCheque>()
                    .map { ItemVirement.CompteItem(it) }

                // Créer les enveloppes UI avec le même système que AjoutTransactionViewModel
                val enveloppesUi = construireEnveloppesUi()

                // Utiliser OrganisationEnveloppesUtils pour maintenir l'ordre correct des catégories
                val enveloppesOrganisees = OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(allCategories, allEnveloppes)

                // Grouper les sources (comptes + enveloppes avec argent) dans le bon ordre
                val sourcesEnveloppes = LinkedHashMap<String, List<ItemVirement>>()
                enveloppesOrganisees.forEach { (nomCategorie, enveloppesCategorie) ->
                    val enveloppesAvecArgent = enveloppesCategorie
                        .mapNotNull { enveloppe ->
                            enveloppesUi.find { it.id == enveloppe.id }
                        }
                        .filter { it.solde > 0 }  // Seulement les enveloppes avec de l'argent
                        .map { ItemVirement.EnveloppeItem(it) }

                    if (enveloppesAvecArgent.isNotEmpty()) {
                        sourcesEnveloppes[nomCategorie] = enveloppesAvecArgent
                    }
                }

                val sources = LinkedHashMap<String, List<ItemVirement>>().apply {
                    put("Prêt à placer", itemsComptes)
                    putAll(sourcesEnveloppes)
                }

                // Grouper les destinations (comptes + toutes les enveloppes) dans le bon ordre
                val destinationsEnveloppes = LinkedHashMap<String, List<ItemVirement>>()
                enveloppesOrganisees.forEach { (nomCategorie, enveloppesCategorie) ->
                    val enveloppesCategorie = enveloppesCategorie
                        .mapNotNull { enveloppe ->
                            enveloppesUi.find { it.id == enveloppe.id }
                        }
                        .map { ItemVirement.EnveloppeItem(it) }

                    if (enveloppesCategorie.isNotEmpty()) {
                        destinationsEnveloppes[nomCategorie] = enveloppesCategorie
                    }
                }

                val destinations = LinkedHashMap<String, List<ItemVirement>>().apply {
                    put("Prêt à placer", itemsComptes)
                    putAll(destinationsEnveloppes)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sourcesDisponibles = sources,
                        destinationsDisponibles = destinations
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

    /**
     * Construit la liste des enveloppes UI avec leurs allocations.
     * Même logique que dans AjoutTransactionViewModel.
     */
    private fun construireEnveloppesUi(): List<EnveloppeUi> {
        return allEnveloppes.filter { !it.estArchive }.map { enveloppe ->
            val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
            
            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = allocation?.solde ?: 0.0,
                depense = allocation?.depense ?: 0.0,
                objectif = enveloppe.objectifMontant,
                couleurProvenance = "#6366F1",  // Couleur par défaut
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
     * Ferme le sélecteur ouvert.
     */
    fun fermerSelecteur() { 
        _uiState.update { it.copy(selecteurOuvert = SelecteurOuvert.AUCUN) } 
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
        val montantValide = state.montant.toLongOrNull() ?: 0L > 0
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
            _uiState.update { it.copy(erreur = "Veuillez sélectionner une source.") }
            return
        }
        
        if (destination == null) {
            _uiState.update { it.copy(erreur = "Veuillez sélectionner une destination.") }
            return
        }
        
        if (montantEnCentimes <= 0) {
            _uiState.update { it.copy(erreur = "Veuillez entrer un montant valide.") }
            return
        }

        // Vérifier que la source a assez d'argent
        val soldeSource = obtenirSoldeItem(source)
        if (soldeSource < montantEnDollars) {
            _uiState.update {
                it.copy(erreur = "Solde insuffisant dans la source sélectionnée.")
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // 🔒 VALIDATIONS DE PROVENANCE - Couvrir TOUS les cas comme ClavierBudgetEnveloppe
                val moisActuel = Date()

                when {
                    // 1. Compte vers Enveloppe - Vérifier conflit de provenance
                    source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                        val validationResult = validationProvenanceService.validerAjoutArgentEnveloppe(
                            enveloppeId = destination.enveloppe.id,
                            compteSourceId = source.compte.id,
                            mois = moisActuel
                        )
                        if (validationResult.isFailure) {
                            throw Exception(validationResult.exceptionOrNull()?.message ?: "Conflit de provenance détecté")
                        }
                    }

                    // 2. Prêt à placer vers Enveloppe - Vérifier conflit de provenance (CAS PRINCIPAL !)
                    source is ItemVirement.EnveloppeItem && estPretAPlacer(source.enveloppe) &&
                    destination is ItemVirement.EnveloppeItem && !estPretAPlacer(destination.enveloppe) -> {
                        val compteId = extraireCompteIdDepuisPretAPlacer(source.enveloppe.id)
                        val validationResult = validationProvenanceService.validerAjoutArgentEnveloppe(
                            enveloppeId = destination.enveloppe.id,
                            compteSourceId = compteId,
                            mois = moisActuel
                        )
                        if (validationResult.isFailure) {
                            throw Exception(validationResult.exceptionOrNull()?.message ?: "Conflit de provenance détecté")
                        }
                    }

                    // 3. Enveloppe vers Enveloppe - Vérifier transfert entre enveloppes
                    source is ItemVirement.EnveloppeItem && !estPretAPlacer(source.enveloppe) &&
                    destination is ItemVirement.EnveloppeItem && !estPretAPlacer(destination.enveloppe) -> {
                        val validationResult = validationProvenanceService.validerTransfertEntreEnveloppes(
                            enveloppeSourceId = source.enveloppe.id,
                            enveloppeCibleId = destination.enveloppe.id,
                            mois = moisActuel
                        )
                        if (validationResult.isFailure) {
                            throw Exception(validationResult.exceptionOrNull()?.message ?: "Conflit de provenance entre enveloppes")
                        }
                    }

                    // 4. Enveloppe vers Compte - Vérifier retour vers compte d'origine
                    source is ItemVirement.EnveloppeItem && !estPretAPlacer(source.enveloppe) &&
                    destination is ItemVirement.CompteItem -> {
                        val validationResult = validationProvenanceService.validerRetourVersCompte(
                            enveloppeId = source.enveloppe.id,
                            compteDestinationId = destination.compte.id,
                            mois = moisActuel
                        )
                        if (validationResult.isFailure) {
                            throw Exception(validationResult.exceptionOrNull()?.message ?: "L'argent ne peut retourner que vers son compte d'origine")
                        }
                    }

                    // 5. Enveloppe vers Prêt à placer - Vérifier retour vers compte d'origine
                    source is ItemVirement.EnveloppeItem && !estPretAPlacer(source.enveloppe) &&
                    destination is ItemVirement.EnveloppeItem && estPretAPlacer(destination.enveloppe) -> {
                        val compteId = extraireCompteIdDepuisPretAPlacer(destination.enveloppe.id)
                        val validationResult = validationProvenanceService.validerRetourVersCompte(
                            enveloppeId = source.enveloppe.id,
                            compteDestinationId = compteId,
                            mois = moisActuel
                        )
                        if (validationResult.isFailure) {
                            throw Exception(validationResult.exceptionOrNull()?.message ?: "L'argent ne peut retourner que vers son compte d'origine")
                        }
                    }
                }

                // Effectuer le virement selon les types source/destination
                when {
                    // Compte vers Compte
                    source is ItemVirement.CompteItem && destination is ItemVirement.CompteItem -> {
                        argentService.effectuerVirementCompteVersCompte(
                            compteSource = source.compte,
                            compteDestination = destination.compte,
                            montant = montantEnDollars
                        )
                    }
                    // Compte vers Enveloppe
                    source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                        argentService.allouerArgentEnveloppe(
                            enveloppeId = destination.enveloppe.id,
                            compteSourceId = source.compte.id,
                            collectionCompteSource = source.compte.collection,
                            montant = montantEnDollars,
                            mois = Date()
                        )
                    }
                    // Prêt à placer vers Enveloppe
                    source is ItemVirement.EnveloppeItem && estPretAPlacer(source.enveloppe) && destination is ItemVirement.EnveloppeItem && !estPretAPlacer(destination.enveloppe) -> {
                        val compteId = extraireCompteIdDepuisPretAPlacer(source.enveloppe.id)
                        argentService.effectuerVirementPretAPlacerVersEnveloppe(
                            compteId = compteId,
                            enveloppeId = destination.enveloppe.id,
                            montant = montantEnDollars
                        )
                    }
                    // Enveloppe vers Prêt à placer
                    source is ItemVirement.EnveloppeItem && !estPretAPlacer(source.enveloppe) && destination is ItemVirement.EnveloppeItem && estPretAPlacer(destination.enveloppe) -> {
                        val compteId = extraireCompteIdDepuisPretAPlacer(destination.enveloppe.id)
                        argentService.effectuerVirementEnveloppeVersPretAPlacer(
                            enveloppeId = source.enveloppe.id,
                            compteId = compteId,
                            montant = montantEnDollars
                        )
                    }
                    // Enveloppe vers Compte
                    source is ItemVirement.EnveloppeItem && !estPretAPlacer(source.enveloppe) && destination is ItemVirement.CompteItem -> {
                        // Logique pour retirer de l'enveloppe vers le compte
                        // À implémenter selon vos besoins
                    }
                    // Enveloppe vers Enveloppe (normale)
                    source is ItemVirement.EnveloppeItem && !estPretAPlacer(source.enveloppe) && destination is ItemVirement.EnveloppeItem && !estPretAPlacer(destination.enveloppe) -> {
                        // Logique pour virement entre enveloppes
                        // À implémenter selon vos besoins
                    }
                    // Cas non supportés
                    else -> {
                        throw IllegalArgumentException("Type de virement non supporté")
                    }
                }

                _uiState.update {
                    it.copy(
                        virementReussi = true,
                        erreur = null
                    )
                }

                // Recharger les données après le virement
                chargerSourcesEtDestinations()

                // Déclencher la mise à jour du budget en temps réel
                realtimeSyncService.declencherMiseAJourBudget()

            } catch (e: Exception) {
                val messageErreurFormate = formaterMessageErreur(e.message ?: "Erreur inconnue")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        virementReussi = false,
                        erreur = "Erreur lors du virement: $messageErreurFormate"
                    )
                }
            }
        }
    }

    // ===== UTILITAIRES =====

    /**
     * Formate un message d'erreur en remplaçant les IDs de comptes par leurs noms
     */
    private suspend fun formaterMessageErreur(messageOriginal: String): String {
        var messageFormate = messageOriginal

        // Rechercher les patterns d'ID de compte dans le message
        val regexId = Regex("""[a-zA-Z0-9]{15}""") // Pattern typique d'un ID PocketBase
        val idsFound = regexId.findAll(messageOriginal).map { it.value }.toSet()

        // Remplacer chaque ID trouvé par le nom du compte correspondant
        for (id in idsFound) {
            val nomCompte = obtenirNomCompteParId(id)
            if (nomCompte != "Compte inconnu") {
                messageFormate = messageFormate.replace(id, nomCompte)
            }
        }

        return messageFormate
    }

    /**
     * Récupère le nom d'un compte par son ID
     */
    private suspend fun obtenirNomCompteParId(compteId: String): String {
        return try {
            // Chercher dans tous les comptes chargés en cache
            allComptes.find { it.id == compteId }?.nom ?: "Compte inconnu"
        } catch (e: Exception) {
            "Compte inconnu"
        }
    }

    /**
     * Obtient le solde actuel d'un ItemVirement.
     */
    private fun obtenirSoldeItem(item: ItemVirement): Double {
        return when (item) {
            is ItemVirement.CompteItem -> item.compte.solde
            is ItemVirement.EnveloppeItem -> item.enveloppe.solde
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
     * Reset le flag de virement réussi pour éviter la navigation en boucle.
     */
    fun resetVirementReussi() {
        _uiState.update { it.copy(virementReussi = false) }
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
        _uiState.update { it.copy(virementReussi = false) }
    }

    // ===== VALIDATION =====

    /**
     * Valide les données de virement avant exécution.
     */
    fun validerVirement(): Boolean {
        val state = _uiState.value

        // Vérifications communes
        if (state.sourceSelectionnee == null) {
            _uiState.update { it.copy(erreur = "Source non sélectionnée.") }
            return false
        }
        if (state.destinationSelectionnee == null) {
            _uiState.update { it.copy(erreur = "Destination non sélectionnée.") }
            return false
        }
        if (state.montant.isBlank() || state.montant.toDoubleOrNull() ?: 0.0 <= 0) {
            _uiState.update { it.copy(erreur = "Montant invalide.") }
            return false
        }

        // Validations spécifiques au mode
        return when (state.mode) {
            VirementMode.ENVELOPPES -> validerPourModeEnveloppes(state)
            VirementMode.COMPTES -> validerPourModeComptes(state)
        }
    }

    /**
     * Valide les données pour le mode ENVELOPPES.
     */
    private fun validerPourModeEnveloppes(state: VirerArgentUiState): Boolean {
        // Exemple: Vérifier que la source est une enveloppe avec un solde suffisant
        val source = state.sourceSelectionnee as? ItemVirement.EnveloppeItem
        val montant = state.montant.toDoubleOrNull() ?: 0.0

        if (source != null && source.enveloppe.solde < montant) {
            _uiState.update { it.copy(erreur = "Solde insuffisant sur l'enveloppe source.") }
            return false
        }

        return true
    }

    /**
     * Valide les données pour le mode COMPTES.
     */
    private fun validerPourModeComptes(state: VirerArgentUiState): Boolean {
        // Exemple: Vérifier que les comptes source et destination sont différents
        val source = state.sourceSelectionnee as? ItemVirement.CompteItem
        val destination = state.destinationSelectionnee as? ItemVirement.CompteItem

        if (source != null && destination != null && source.compte.id == destination.compte.id) {
            _uiState.update { it.copy(erreur = "La source et la destination ne peuvent pas être identiques.") }
            return false
        }

        return true
    }

    /**
     * Valide la logique de provenance pour le virement.
     * Appelle la fonction de validation appropriée selon le mode de virement actuel.
     */
    private suspend fun validerProvenance(source: ItemVirement, destination: ItemVirement): Result<Unit> {
        val mois = Date()
        return when (_uiState.value.mode) {
            VirementMode.ENVELOPPES -> validerProvenanceEnveloppes(source, destination, mois)
            VirementMode.COMPTES -> validerProvenanceComptes(source, destination)
        }
    }

    /**
     * Valide la logique de provenance pour le mode ENVELOPPES.
     */
    private suspend fun validerProvenanceEnveloppes(source: ItemVirement, destination: ItemVirement, mois: Date): Result<Unit> {
        return when {
            // Cas 1: Prêt à placer (Compte) -> Enveloppe
            source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                validationProvenanceService.validerAjoutArgentEnveloppe(
                    enveloppeId = destination.enveloppe.id,
                    compteSourceId = source.compte.id,
                    mois = mois
                )
            }
            // Cas 2: Enveloppe -> Enveloppe
            source is ItemVirement.EnveloppeItem && destination is ItemVirement.EnveloppeItem -> {
                validationProvenanceService.validerTransfertEntreEnveloppes(
                    enveloppeSourceId = source.enveloppe.id,
                    enveloppeCibleId = destination.enveloppe.id,
                    mois = mois
                )
            }
            // Cas 3: Enveloppe -> Prêt à placer (Compte)
            source is ItemVirement.EnveloppeItem && destination is ItemVirement.CompteItem -> {
                validationProvenanceService.validerTransfertEnveloppeVersCompte(
                    enveloppeSourceId = source.enveloppe.id,
                    compteCibleId = destination.compte.id,
                    mois = mois
                )
            }
            // Autres cas (ne devrait pas arriver dans ce mode)
            else -> Result.failure(IllegalArgumentException("Type de virement non supporté pour les enveloppes."))
        }
    }

    /**
     * Valide la logique de provenance pour le mode COMPTES.
     */
    private fun validerProvenanceComptes(source: ItemVirement, destination: ItemVirement): Result<Unit> {
        return when {
            source is ItemVirement.CompteItem && destination is ItemVirement.CompteItem -> {
                if (source.compte.id == destination.compte.id) {
                    Result.failure(IllegalArgumentException("La source et la destination ne peuvent pas être identiques."))
                } else {
                    Result.success(Unit)
                }
            }
            // Autres cas invalides pour ce mode
            else -> Result.failure(IllegalArgumentException("Seuls les virements de compte à compte sont autorisés dans ce mode."))
        }
    }

    /**
     * Exécute le virement d'argent entre source et destination.
     */
    private fun executerVirement() {
        val state = _uiState.value
        val source = state.sourceSelectionnee
        val destination = state.destinationSelectionnee
        val montantEnCentimes = state.montant.toLongOrNull() ?: 0L
        val montantEnDollars = montantEnCentimes / 100.0

        // Validation que source et destination ne sont pas null
        if (source == null || destination == null) {
            _uiState.update {
                it.copy(erreur = "Source et destination doivent être sélectionnées.")
            }
            return
        }

        viewModelScope.launch {
            // Gérer les erreurs de manière globale
            runCatching {
                when (_uiState.value.mode) {
                    VirementMode.ENVELOPPES -> executerVirementEnveloppes(source, destination, montantEnDollars)
                    VirementMode.COMPTES -> executerVirementComptes(source, destination, montantEnDollars)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        virementReussi = true,
                        erreur = null
                    )
                }
                // Recharger les données après le virement
                chargerDonneesInitiales()
                // Déclencher la mise à jour du budget en temps réel
                realtimeSyncService.declencherMiseAJourBudget()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        virementReussi = false,
                        erreur = "Erreur lors du virement: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Exécute la logique de virement pour le mode ENVELOPPES.
     */
    private suspend fun executerVirementEnveloppes(source: ItemVirement, destination: ItemVirement, montant: Double): Result<Unit> {
        return when {
            // Prêt à placer (Compte) -> Enveloppe
            source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                argentService.allouerArgentEnveloppe(
                    enveloppeId = destination.enveloppe.id,
                    compteSourceId = source.compte.id,
                    collectionCompteSource = source.compte.collection,
                    montant = montant,
                    mois = Date()
                )
            }

            // Enveloppe -> Enveloppe - Utiliser les objets complets des enveloppes
            source is ItemVirement.EnveloppeItem && destination is ItemVirement.EnveloppeItem -> {
                // Récupérer les objets Enveloppe complets
                val enveloppeSource = allEnveloppes.find { it.id == source.enveloppe.id }
                val enveloppeDestination = allEnveloppes.find { it.id == destination.enveloppe.id }

                if (enveloppeSource != null && enveloppeDestination != null) {
                    argentService.effectuerVirementEnveloppeVersEnveloppe(
                        enveloppeSource = enveloppeSource,
                        enveloppeDestination = enveloppeDestination,
                        montant = montant
                    )
                } else {
                    Result.failure(Exception("Enveloppe source ou destination introuvable"))
                }
            }

            // Enveloppe -> Prêt à placer (Compte) - Utiliser les objets complets
            source is ItemVirement.EnveloppeItem && destination is ItemVirement.CompteItem -> {
                val enveloppeSource = allEnveloppes.find { it.id == source.enveloppe.id }

                if (enveloppeSource != null) {
                    argentService.effectuerVirementEnveloppeVersCompte(
                        enveloppe = enveloppeSource,
                        compte = destination.compte,
                        montant = montant
                    )
                } else {
                    Result.failure(Exception("Enveloppe source introuvable"))
                }
            }

            else -> Result.failure(Exception("Type de virement non supporté."))
        }
    }

    /**
     * Exécute la logique de virement pour le mode COMPTES.
     */
    private suspend fun executerVirementComptes(source: ItemVirement, destination: ItemVirement, montant: Double): Result<Unit> {
        if (source is ItemVirement.CompteItem && destination is ItemVirement.CompteItem) {
            return argentService.effectuerVirementEntreComptes(
                compteSourceId = source.compte.id,
                compteDestId = destination.compte.id,
                montant = montant,
                nomCompteSource = source.compte.nom,
                nomCompteDest = destination.compte.nom
            )
        }
        return Result.failure(IllegalArgumentException("Source ou destination invalide pour un virement entre comptes."))
    }
}
