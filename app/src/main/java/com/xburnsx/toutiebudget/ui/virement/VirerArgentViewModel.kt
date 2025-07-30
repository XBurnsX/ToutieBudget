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

    /**
     * Recharge les données depuis les repositories.
     * À appeler quand l'écran redevient visible ou après des modifications.
     */
    fun rechargerDonnees() {
        println("[DEBUG VIREMENT] === Rechargement manuel des données ===")
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
                println("[DEBUG VIREMENT] === Début chargement données ===")

                allComptes = compteRepository.recupererTousLesComptes()
                    .getOrThrow()
                    .filter { !it.estArchive }
                println("[DEBUG VIREMENT] Comptes chargés: ${allComptes.size}")
                allComptes.forEach { compte ->
                    println("[DEBUG VIREMENT] - Compte: ${compte.nom} (Type: ${compte::class.simpleName}, Archivé: ${compte.estArchive})")
                }

                allEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                    .getOrThrow()
                    .filter { !it.estArchive }
                println("[DEBUG VIREMENT] Enveloppes chargées: ${allEnveloppes.size}")
                allEnveloppes.forEach { env ->
                    println("[DEBUG VIREMENT] - Enveloppe: ${env.nom} (ID: ${env.id}, Archivée: ${env.estArchive})")
                }

                allAllocations = enveloppeRepository.recupererAllocationsPourMois(Date())
                    .getOrThrow()
                println("[DEBUG VIREMENT] Allocations chargées: ${allAllocations.size}")
                allAllocations.forEach { alloc ->
                    println("[DEBUG VIREMENT] - Allocation: EnvID=${alloc.enveloppeId}, solde=${alloc.solde}")
                }

                allCategories = categorieRepository.recupererToutesLesCategories()
                    .getOrThrow()
                println("[DEBUG VIREMENT] Catégories chargées: ${allCategories.size}")

                // Configurer les sources et destinations pour le mode initial
                configurerSourcesEtDestinationsPourMode()

            } catch (e: Exception) {
                println("[DEBUG VIREMENT] ERREUR lors du chargement: ${e.message}")
                e.printStackTrace()
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
        println("[DEBUG VIREMENT] === configurerPourModeEnveloppes ===")

        // Créer les items de comptes (seulement les comptes chèque avec prêt à placer disponible)
        val itemsComptes = allComptes
            .filterIsInstance<CompteCheque>()
            .filter { it.pretAPlacer > 0 } // Ne garder que les comptes avec un montant prêt à placer disponible
            .map { ItemVirement.CompteItem(it) }
        println("[DEBUG VIREMENT] Items comptes (CompteCheque): ${itemsComptes.size}")
        itemsComptes.forEach { item ->
            println("[DEBUG VIREMENT] - Compte item: ${item.compte.nom}")
        }

        // Créer les enveloppes UI
        val enveloppesUi = construireEnveloppesUi()
        println("[DEBUG VIREMENT] Enveloppes UI construites: ${enveloppesUi.size}")
        enveloppesUi.forEach { env ->
            println("[DEBUG VIREMENT] - Enveloppe UI: ${env.nom} (solde: ${env.solde})")
        }

        val enveloppesOrganisees = OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(allCategories, allEnveloppes)
        println("[DEBUG VIREMENT] Enveloppes organisées par catégorie: ${enveloppesOrganisees.size} catégories")
        enveloppesOrganisees.forEach { (nomCategorie, enveloppes) ->
            println("[DEBUG VIREMENT] - Catégorie: $nomCategorie (${enveloppes.size} enveloppes)")
        }

        // Grouper les sources (comptes + enveloppes avec argent)
        val sourcesEnveloppes = LinkedHashMap<String, List<ItemVirement>>()
        enveloppesOrganisees.forEach { (nomCategorie, enveloppes) ->
            val enveloppesAvecArgent = enveloppes
                .mapNotNull { env -> enveloppesUi.find { it.id == env.id } }
                .filter { it.solde > 0 }
                .map { ItemVirement.EnveloppeItem(it) }
            println("[DEBUG VIREMENT] - Catégorie $nomCategorie: ${enveloppesAvecArgent.size} enveloppes avec argent")
            if (enveloppesAvecArgent.isNotEmpty()) {
                sourcesEnveloppes[nomCategorie] = enveloppesAvecArgent
            }
        }

        val sources = LinkedHashMap<String, List<ItemVirement>>().apply {
            put("Prêt à placer", itemsComptes)
            putAll(sourcesEnveloppes)
        }
        println("[DEBUG VIREMENT] Sources finales: ${sources.size} catégories")
        sources.forEach { (categorie, items) ->
            println("[DEBUG VIREMENT] - Source catégorie: $categorie (${items.size} items)")
        }

        // Grouper les destinations (comptes + toutes les enveloppes)
        val destinationsEnveloppes = LinkedHashMap<String, List<ItemVirement>>()
        enveloppesOrganisees.forEach { (nomCategorie, enveloppes) ->
            val items = enveloppes
                .mapNotNull { env -> enveloppesUi.find { it.id == env.id } }
                .map { ItemVirement.EnveloppeItem(it) }
            println("[DEBUG VIREMENT] - Destination catégorie $nomCategorie: ${items.size} items")
            if (items.isNotEmpty()) {
                destinationsEnveloppes[nomCategorie] = items
            }
        }

        val destinations = LinkedHashMap<String, List<ItemVirement>>().apply {
            put("Prêt à placer", itemsComptes)
            putAll(destinationsEnveloppes)
        }
        println("[DEBUG VIREMENT] Destinations finales: ${destinations.size} catégories")
        destinations.forEach { (categorie, items) ->
            println("[DEBUG VIREMENT] - Destination catégorie: $categorie (${items.size} items)")
        }

        _uiState.update {
            it.copy(
                sourcesDisponibles = sources,
                destinationsDisponibles = destinations
            )
        }

        println("[DEBUG VIREMENT] UiState mis à jour avec sources et destinations")
    }

    /**
     * Configure les sources et destinations pour le virement entre comptes,
     * en les groupant par catégorie et en respectant l'ordre.
     * Les dettes sont exclues.
     */
    private fun configurerPourModeComptes() {
        println("[DEBUG VIREMENT] === configurerPourModeComptes ===")

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

        println("[DEBUG VIREMENT] Comptes groupés: ${comptesGroupes.size} catégories")
        comptesGroupes.forEach { (categorie, items) ->
            println("[DEBUG VIREMENT] - Catégorie: $categorie (${items.size} comptes)")
            items.forEach { item ->
                println("[DEBUG VIREMENT]   * ${item.compte.nom} (${item.compte::class.simpleName})")
            }
        }

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

        println("[DEBUG VIREMENT] Sources finales pour mode Comptes: ${sourcesFinales.size} catégories")
        sourcesFinales.forEach { (categorie, items) ->
            println("[DEBUG VIREMENT] - Catégorie finale: $categorie (${items.size} items)")
        }

        _uiState.update {
            it.copy(
                sourcesDisponibles = sourcesFinales,
                destinationsDisponibles = sourcesFinales,
                isLoading = false
            )
        }

        println("[DEBUG VIREMENT] UiState mis à jour pour mode Comptes")
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

                // Créer les items de comptes (seulement les comptes chèque avec prêt à placer disponible)
                val itemsComptes = allComptes
                    .filterIsInstance<CompteCheque>()
                    .filter { it.pretAPlacer > 0 } // Ne garder que les comptes avec un montant prêt à placer disponible
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
            
            // 🎨 RÉCUPÉRER LA VRAIE COULEUR DU COMPTE SOURCE (comme dans AjoutTransactionViewModel)
            val compteSource = allocation?.compteSourceId?.let { compteId ->
                allComptes.find { it.id == compteId }
            }
            
            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = allocation?.solde ?: 0.0,
                depense = allocation?.depense ?: 0.0,
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

        // DEBUG: Afficher les informations du virement
        println("🔍 DEBUG VIREMENT:")
        when (source) {
            is ItemVirement.CompteItem -> println("   Source: ${source.compte.nom}")
            is ItemVirement.EnveloppeItem -> println("   Source: ${source.enveloppe.nom}")
            null -> println("   Source: NULL")
        }
        when (destination) {
            is ItemVirement.CompteItem -> println("   Destination: ${destination.compte.nom}")
            is ItemVirement.EnveloppeItem -> println("   Destination: ${destination.enveloppe.nom}")
            null -> println("   Destination: NULL")
        }
        println("   Montant: $montantEnDollars")
        println("   Mode: ${state.mode}")

        // Validations
        if (source == null) {
            println("❌ ERREUR: Source null")
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.SOURCE_NULL) }
            return
        }
        
        if (destination == null) {
            println("❌ ERREUR: Destination null")
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.DESTINATION_NULL) }
            return
        }
        
        if (montantEnCentimes <= 0) {
            println("❌ ERREUR: Montant invalide: $montantEnCentimes")
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.MONTANT_INVALIDE) }
            return
        }

        // Vérifier que la source et destination ne sont pas identiques
        if (memeItem(source, destination)) {
            println("❌ ERREUR: Source et destination identiques")
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.SOURCE_DESTINATION_IDENTIQUES) }
            return
        }

        // Vérifier que la source a assez d'argent
        val soldeSource = obtenirSoldeItem(source)
        println("🔍 DEBUG SOLDE SOURCE:")
        when (source) {
            is ItemVirement.CompteItem -> {
                println("   Type: CompteItem")
                println("   Nom: ${source.compte.nom}")
                println("   Solde compte: ${source.compte.solde}")
                if (source.compte is CompteCheque) {
                    println("   Prêt à placer: ${source.compte.pretAPlacer}")
                }
            }
            is ItemVirement.EnveloppeItem -> {
                println("   Type: EnveloppeItem")
                println("   Nom: ${source.enveloppe.nom}")
                println("   ID: ${source.enveloppe.id}")
                println("   Solde enveloppe: ${source.enveloppe.solde}")
                println("   Est prêt à placer: ${estPretAPlacer(source.enveloppe)}")
                if (estPretAPlacer(source.enveloppe)) {
                    val compteId = extraireCompteIdDepuisPretAPlacer(source.enveloppe.id)
                    val compte = allComptes.find { it.id == compteId }
                    println("   Compte ID extrait: $compteId")
                    println("   Compte trouvé: ${compte?.nom}")
                    if (compte is CompteCheque) {
                        println("   Prêt à placer du compte: ${compte.pretAPlacer}")
                    }
                }
            }
        }
        println("   Solde calculé par obtenirSoldeItem: $soldeSource")
        
        if (soldeSource < montantEnDollars) {
            println("❌ ERREUR: Solde insuffisant: $soldeSource < $montantEnDollars")
            _uiState.update {
                it.copy(erreur = VirementErrorMessages.General.soldeInsuffisant(soldeSource))
            }
            return
        }

        println("✅ Validations basiques OK, validation de provenance...")

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, erreur = null) }

                // VALIDATION DE PROVENANCE SELON LE TYPE DE VIREMENT
                println("🔍 DEBUG VALIDATION DE PROVENANCE:")
                println("   Source: ${when(source) {
                    is ItemVirement.CompteItem -> "CompteItem(${source.compte.nom})"
                    is ItemVirement.EnveloppeItem -> "EnveloppeItem(${source.enveloppe.nom}, id=${source.enveloppe.id}, estPretAPlacer=${estPretAPlacer(source.enveloppe)})"
                }}")
                println("   Destination: ${when(destination) {
                    is ItemVirement.CompteItem -> "CompteItem(${destination.compte.nom})"
                    is ItemVirement.EnveloppeItem -> "EnveloppeItem(${destination.enveloppe.nom}, id=${destination.enveloppe.id}, estPretAPlacer=${estPretAPlacer(destination.enveloppe)})"
                }}")
                
                val validationResult = validerProvenanceVirement(source, destination)
                println("   Résultat validation: ${if (validationResult.isSuccess) "SUCCÈS" else "ÉCHEC: ${validationResult.exceptionOrNull()?.message}"}")

                if (validationResult.isFailure) {
                    val messageErreur = validationResult.exceptionOrNull()?.message ?: "Erreur de validation inconnue"
                    println("❌ ERREUR DE PROVENANCE: $messageErreur")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            erreur = messageErreur
                        )
                    }
                    return@launch
                }

                println("✅ Validation de provenance OK, exécution du virement...")

                // Effectuer le virement selon les types source/destination
                println("🔍 DEBUG EXÉCUTION VIREMENT:")
                println("   Source type: ${source::class.simpleName}")
                println("   Destination type: ${destination::class.simpleName}")
                
                val virementResult = when {
                    // Compte vers Compte - AUCUNE VALIDATION DE PROVENANCE NÉCESSAIRE
                    source is ItemVirement.CompteItem && destination is ItemVirement.CompteItem -> {
                        println("🔄 Virement Compte vers Compte...")
                        argentService.effectuerVirementEntreComptes(
                            compteSourceId = source.compte.id,
                            compteDestId = destination.compte.id,
                            montant = montantEnDollars,
                            nomCompteSource = source.compte.nom,
                            nomCompteDest = destination.compte.nom
                        )
                    }

                    // Compte vers Enveloppe (Prêt à placer vers enveloppe) - VALIDATION APPLIQUÉE
                    source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                        println(VirementErrorMessages.Debug.VIREMENT_COMPTE_VERS_ENVELOPPE)
                        val enveloppeDestination = allEnveloppes.find { it.id == destination.enveloppe.id }
                        if (enveloppeDestination == null) {
                            Result.failure(Exception(VirementErrorMessages.PretAPlacerVersEnveloppe.enveloppeIntrouvable(destination.enveloppe.nom)))
                        } else {
                            argentService.allouerArgentEnveloppe(
                                enveloppeId = destination.enveloppe.id,
                                compteSourceId = source.compte.id,
                                collectionCompteSource = source.compte.collection,
                                montant = montantEnDollars,
                                mois = Date()
                            )
                        }
                    }

                    // Enveloppe vers Compte (Enveloppe vers Prêt à placer) - VALIDATION APPLIQUÉE
                    source is ItemVirement.EnveloppeItem && destination is ItemVirement.CompteItem -> {
                        println(VirementErrorMessages.Debug.VIREMENT_ENVELOPPE_VERS_COMPTE)
                        val enveloppeSource = allEnveloppes.find { it.id == source.enveloppe.id }
                        if (enveloppeSource == null) {
                            Result.failure(Exception(VirementErrorMessages.EnveloppeVersPretAPlacer.enveloppeSourceIntrouvable(source.enveloppe.nom)))
                        } else {
                            argentService.effectuerVirementEnveloppeVersCompte(
                                enveloppe = enveloppeSource,
                                compte = destination.compte,
                                montant = montantEnDollars
                            )
                        }
                    }

                    // Enveloppe vers Enveloppe OU Enveloppe vers Prêt à placer - VALIDATION APPLIQUÉE
                    source is ItemVirement.EnveloppeItem && destination is ItemVirement.EnveloppeItem -> {
                        if (estPretAPlacer(source.enveloppe)) {
                            // 🎯 SOURCE EST UN PRÊT À PLACER - VIREMENT COMPTE VERS ENVELOPPE
                            println("🔄 Virement Prêt à placer vers Enveloppe")
                            val compteSourceId = extraireCompteIdDepuisPretAPlacer(source.enveloppe.id)
                            val compteSource = allComptes.find { it.id == compteSourceId }
                            
                            if (compteSource == null) {
                                Result.failure(Exception("Compte source introuvable pour le prêt à placer"))
                            } else {
                                // 🎯 UTILISER LA MÊME LOGIQUE QUI FONCTIONNE DANS LE BUDGET
                                
                                // 1. Mettre à jour le compte source (retirer de "prêt à placer")
                                val ancienPretAPlacer = (compteSource as CompteCheque).pretAPlacer
                                val nouveauPretAPlacer = ancienPretAPlacer - montantEnDollars
                                
                                println("💰 MISE À JOUR PRÊT À PLACER:")
                                println("   Ancien montant: ${ancienPretAPlacer}$")
                                println("   Montant du virement: ${montantEnDollars}$")
                                println("   Nouveau montant: ${nouveauPretAPlacer}$")
                                
                                val compteModifie = compteSource.copy(
                                    pretAPlacerRaw = nouveauPretAPlacer,
                                    collection = compteSource.collection ?: "comptes_cheque" // Assurer qu'on a une collection
                                )
                                
                                val resultCompte = compteRepository.mettreAJourCompte(compteModifie)
                                if (resultCompte.isSuccess) {
                                    println("✅ Compte mis à jour avec succès")
                                } else {
                                    println("❌ Erreur mise à jour compte: ${resultCompte.exceptionOrNull()?.message}")
                                }
                                if (resultCompte.isFailure) {
                                    Result.failure(Exception("Erreur lors de la mise à jour du compte: ${resultCompte.exceptionOrNull()?.message}"))
                                } else {
                                    // 2. Créer une nouvelle allocation mensuelle
                                    val nouvelleAllocation = AllocationMensuelle(
                                        id = "",
                                        utilisateurId = compteSource.utilisateurId,
                                        enveloppeId = destination.enveloppe.id,
                                        mois = Date(),
                                        solde = montantEnDollars,
                                        alloue = montantEnDollars,
                                        depense = 0.0,
                                        compteSourceId = compteSource.id,
                                        collectionCompteSource = compteSource.collection
                                    )
                                    
                                    val resultAllocation = enveloppeRepository.creerAllocationMensuelle(nouvelleAllocation)
                                    if (resultAllocation.isFailure) {
                                        Result.failure(Exception("Erreur lors de la création de l'allocation: ${resultAllocation.exceptionOrNull()?.message}"))
                                    } else {
                                        Result.success(Unit)
                                    }
                                }
                            }
                        } else if (estPretAPlacer(destination.enveloppe)) {
                            // Destination est un "Prêt à placer"
                            println(VirementErrorMessages.Debug.VIREMENT_ENVELOPPE_VERS_PRET_A_PLACER)
                            val compteId = extraireCompteIdDepuisPretAPlacer(destination.enveloppe.id)
                            val compteDestination = allComptes.find { it.id == compteId }
                            if (compteDestination == null) {
                                Result.failure(Exception(VirementErrorMessages.EnveloppeVersPretAPlacer.COMPTE_DESTINATION_INTROUVABLE))
                            } else {
                                argentService.effectuerVirementEnveloppeVersPretAPlacer(
                                    enveloppeId = source.enveloppe.id,
                                    compteId = compteDestination.id,
                                    montant = montantEnDollars
                                )
                            }
                        } else {
                            // Cas normal: Enveloppe vers Enveloppe
                            println(VirementErrorMessages.Debug.VIREMENT_ENVELOPPE_VERS_ENVELOPPE)
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
                                            // 2. Créer allocation NÉGATIVE pour l'enveloppe source (diminue solde + alloué)
                                            val allocationNegative = AllocationMensuelle(
                                                id = "",
                                                utilisateurId = allocationSource.utilisateurId,
                                                enveloppeId = source.enveloppe.id,
                                                mois = moisActuel,
                                                solde = -montantEnDollars,        // ← NÉGATIF (retire du solde)
                                                alloue = -montantEnDollars,       // ← NÉGATIF (retire de l'allocation)
                                                depense = 0.0,                    // ← PAS UNE DÉPENSE !
                                                compteSourceId = allocationSource.compteSourceId,
                                                collectionCompteSource = allocationSource.collectionCompteSource
                                            )
                                            
                                            val retraitResult = enveloppeRepository.creerAllocationMensuelle(allocationNegative)
                                            
                                            if (retraitResult.isFailure) {
                                                retraitResult
                                            } else {
                                                // 3. Créer allocation POSITIVE pour l'enveloppe destination (augmente solde + alloué)
                                                val allocationPositive = AllocationMensuelle(
                                                    id = "",
                                                    utilisateurId = allocationSource.utilisateurId,
                                                    enveloppeId = destination.enveloppe.id,
                                                    mois = moisActuel,
                                                    solde = montantEnDollars,        // ← POSITIF (ajoute au solde)
                                                    alloue = montantEnDollars,       // ← POSITIF (ajoute à l'allocation)
                                                    depense = 0.0,                   // ← PAS UNE DÉPENSE !
                                                    compteSourceId = allocationSource.compteSourceId, // ← MÊME PROVENANCE
                                                    collectionCompteSource = allocationSource.collectionCompteSource
                                                )
                                                
                                                enveloppeRepository.creerAllocationMensuelle(allocationPositive)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Result.failure(Exception(VirementErrorMessages.General.TYPE_VIREMENT_NON_SUPPORTE))
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

                println("🚀 VIREMENT TERMINÉ - NAVIGATION IMMÉDIATE")
                
                // 🔄 RECHARGER DONNÉES + MISE À JOUR BUDGET (EN PARALLÈLE, non-bloquant)
                launch {
                    println("🔄 Rechargement des données en arrière-plan...")
                    chargerDonneesInitiales().join()
                    println("✅ Données rechargées pour prochains virements")
                    
                    realtimeSyncService.declencherMiseAJourBudget()
                    println("✅ Mise à jour budget déclenchée")
                }

            } catch (e: Exception) {
                println("❌ EXCEPTION: ${e.message}")
                e.printStackTrace()
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
                println("🔍 Virement Compte vers Compte - Aucune validation de provenance nécessaire")
                Result.success(Unit)
            }

            // Compte vers Enveloppe (Prêt à placer vers enveloppe) - VALIDATION STRICTE
            source is ItemVirement.CompteItem && destination is ItemVirement.EnveloppeItem -> {
                println("🔍 Validation: Compte vers Enveloppe")
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
                    println("🔍 Validation: Prêt à placer vers Compte - Validation ignorée")
                    Result.success(Unit)
                } else {
                    // Enveloppe normale vers Prêt à placer
                    println("🔍 Validation: Enveloppe vers Prêt à placer")
                    // Extraire l'ID du vrai compte depuis l'ID prêt à placer virtuel
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
                    println("🔍 Validation: Prêt à placer vers Enveloppe")
                    // Extraire l'ID du VRAI compte depuis l'ID prêt à placer virtuel
                    val vraiCompteId = extraireCompteIdDepuisPretAPlacer(source.enveloppe.id)
                    validationProvenanceService.validerAjoutArgentEnveloppe(
                        enveloppeId = destination.enveloppe.id,
                        compteSourceId = vraiCompteId, // ← UTILISER L'ID DU VRAI COMPTE
                        mois = mois
                    )
                }
                } else if (estPretAPlacer(destination.enveloppe)) {
                    // Enveloppe normale vers Prêt à placer
                    println("🔍 Validation: Enveloppe vers Prêt à placer")
                    val compteId = extraireCompteIdDepuisPretAPlacer(destination.enveloppe.id)
                    validationProvenanceService.validerTransfertEnveloppeVersCompte(
                        enveloppeSourceId = source.enveloppe.id,
                        compteCibleId = compteId,
                        mois = mois
                    )
                } else {
                    // Enveloppe normale vers Enveloppe normale
                    println("🔍 Validation: Enveloppe vers Enveloppe")
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
        println("🧹 Page de virement nettoyée complètement")
    }

    // ===== VALIDATION =====

    /**
     * Valide les données de virement avant exécution.
     */
    fun validerVirement(): Boolean {
        val state = _uiState.value

        // Vérifications communes
        if (state.sourceSelectionnee == null) {
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.SOURCE_NULL) }
            return false
        }
        if (state.destinationSelectionnee == null) {
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.DESTINATION_NULL) }
            return false
        }
        if (state.montant.isBlank() || state.montant.toDoubleOrNull() ?: 0.0 <= 0) {
            _uiState.update { it.copy(erreur = VirementErrorMessages.General.MONTANT_INVALIDE) }
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
                // 🔄 RECHARGER DONNÉES + MISE À JOUR BUDGET (non-bloquant)
                launch {
                    println("🔄 Rechargement données (executerVirement)...")
                    chargerDonneesInitiales().join()
                    println("✅ Données rechargées")
                    
                    realtimeSyncService.declencherMiseAJourBudget()
                    println("✅ Mise à jour budget déclenchée")
                }
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
                println("🔍 DEBUG: Cas EnveloppeItem -> EnveloppeItem")
                println("   Source ID: ${source.enveloppe.id}")
                println("   Source nom: ${source.enveloppe.nom}")
                println("   Source est prêt à placer: ${estPretAPlacer(source.enveloppe)}")
                println("   Destination ID: ${destination.enveloppe.id}")
                println("   Destination nom: ${destination.enveloppe.nom}")
                println("   Destination est prêt à placer: ${estPretAPlacer(destination.enveloppe)}")
                
                if (estPretAPlacer(source.enveloppe)) {
                    // Source est un prêt à placer virtuel → C'est en fait un virement Compte vers Enveloppe
                    println("🔄 Virement Prêt à placer vers Enveloppe")
                    
                    val compteSourceId = extraireCompteIdDepuisPretAPlacer(source.enveloppe.id)
                    val compteSource = allComptes.find { it.id == compteSourceId }
                    
                    if (compteSource == null) {
                        return Result.failure(Exception("Compte source introuvable pour le prêt à placer"))
                    }
                    
                    // 🎯 UTILISER LA MÊME LOGIQUE QUI FONCTIONNE DANS LE BUDGET
                    
                    // 1. Mettre à jour le compte source (retirer de "prêt à placer")
                    val nouveauPretAPlacer = (compteSource as CompteCheque).pretAPlacer - montant
                    val compteModifie = compteSource.copy(
                        pretAPlacerRaw = nouveauPretAPlacer
                    )
                    
                    val resultCompte = compteRepository.mettreAJourCompte(compteModifie)
                    if (resultCompte.isFailure) {
                        return Result.failure(Exception("Erreur lors de la mise à jour du compte: ${resultCompte.exceptionOrNull()?.message}"))
                    }
                    
                    // 2. Créer une nouvelle allocation mensuelle
                    val nouvelleAllocation = AllocationMensuelle(
                        id = "",
                        utilisateurId = compteSource.utilisateurId,
                        enveloppeId = destination.enveloppe.id,
                        mois = Date(),
                        solde = montant,
                        alloue = montant,
                        depense = 0.0,
                        compteSourceId = compteSource.id,
                        collectionCompteSource = compteSource.collection
                    )
                    
                    val resultAllocation = enveloppeRepository.creerAllocationMensuelle(nouvelleAllocation)
                    if (resultAllocation.isFailure) {
                        return Result.failure(Exception("Erreur lors de la création de l'allocation: ${resultAllocation.exceptionOrNull()?.message}"))
                    }
                    
                    Result.success(Unit)
                } else {
                    // Les deux sont de vraies enveloppes - cas normal
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
            }

            // Enveloppe -> Prêt à placer (dans le mode enveloppes, c'est le seul cas "vers compte")
            source is ItemVirement.EnveloppeItem && destination is ItemVirement.CompteItem -> {
                println("🔍 DEBUG: Cas EnveloppeItem -> CompteItem")
                println("   Source ID: ${source.enveloppe.id}")
                println("   Source nom: ${source.enveloppe.nom}")
                println("   Source est prêt à placer: ${estPretAPlacer(source.enveloppe)}")
                println("   Destination ID: ${destination.compte.id}")
                println("   Destination nom: ${destination.compte.nom}")
                
                if (estPretAPlacer(source.enveloppe)) {
                    // ERREUR DANS LE COMMENTAIRE - Ce n'est PAS "prêt à placer vers prêt à placer"
                    // C'est "prêt à placer vers COMPTE" ce qui ne devrait pas arriver dans le mode Enveloppes !
                    println("❌ ERREUR: Prêt à placer -> Compte détecté - ça ne devrait pas arriver !")
                    Result.failure(Exception("Configuration invalide: Prêt à placer vers compte détecté"))
                } else {
                    // Enveloppe normale vers Prêt à placer
                    val enveloppeSource = allEnveloppes.find { it.id == source.enveloppe.id }

                    if (enveloppeSource != null) {
                        println("🔄 Virement Enveloppe vers Prêt à placer")

                        // Extraire l'ID du compte depuis l'ID "pret_a_placer_"
                        val compteId = extraireCompteIdDepuisPretAPlacer(destination.compte.id)
                        val compteDestination = allComptes.find { it.id == compteId }

                        if (compteDestination == null) {
                            return Result.failure(Exception("Compte destination introuvable pour le prêt à placer"))
                        }

                        // UTILISER LA MÉTHODE SPÉCIFIQUE POUR VIRER VERS PRÊT À PLACER
                        argentService.effectuerVirementEnveloppeVersPretAPlacer(
                            enveloppeId = source.enveloppe.id,
                            compteId = compteDestination.id,
                            montant = montant
                        )
                    } else {
                        Result.failure(Exception("Enveloppe source introuvable"))
                    }
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
