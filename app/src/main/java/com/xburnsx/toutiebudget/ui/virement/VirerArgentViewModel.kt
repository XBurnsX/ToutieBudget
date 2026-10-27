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
import com.xburnsx.toutiebudget.domain.services.VirementBudgetService
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
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
 * Mode « Enveloppes » : réorganisation du budget (Prêt à placer ↔ enveloppes), sans toucher aux soldes bancaires.
 * Mode « Comptes » : vrais transferts compte → compte, avec transactions.
 */
class VirerArgentViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val argentService: ArgentService,
    private val realtimeSyncService: RealtimeSyncService,
    private val virementBudgetService: VirementBudgetService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VirerArgentUiState())
    val uiState: StateFlow<VirerArgentUiState> = _uiState.asStateFlow()

    private var allComptes: List<Compte> = emptyList()
    private var allEnveloppes: List<Enveloppe> = emptyList()
    private var allAllocations: List<AllocationMensuelle> = emptyList()
    private var allCategories: List<Categorie> = emptyList()

    init {
        chargerDonneesInitiales()
    }

    /** Recharge tout ; sert aussi au bouton « Réessayer » (V6). */
    fun rechargerDonnees() {
        chargerDonneesInitiales()
    }

    // ===== GESTION DU MODE =====

    fun changerMode(nouveauMode: VirementMode) {
        _uiState.update {
            it.copy(
                mode = nouveauMode,
                sourceSelectionnee = null,
                destinationSelectionnee = null,
                montant = "",
                erreur = null,
                selecteurOuvert = SelecteurOuvert.AUCUN,
                isVirementButtonEnabled = false
            )
        }
        configurerSourcesEtDestinationsPourMode()
    }

    fun changerMois(nouveauMois: Date) {
        _uiState.update {
            it.copy(
                moisSelectionne = nouveauMois,
                sourceSelectionnee = null,
                destinationSelectionnee = null,
                erreur = null
            )
        }
        chargerAllocationsPourMois(nouveauMois)
    }

    // ===== CHARGEMENT DES DONNÉES (V6 : erreurs visibles, reprise possible) =====

    private fun chargerDonneesInitiales() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(erreurChargement = null) }
                allComptes = compteRepository.recupererTousLesComptes().getOrThrow().filter { !it.estArchive }
                allEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow().filter { !it.estArchive }
                allAllocations = enveloppeRepository.recupererAllocationsPourMois(_uiState.value.moisSelectionne).getOrThrow()
                allCategories = categorieRepository.recupererToutesLesCategories().getOrThrow()
                configurerSourcesEtDestinationsPourMode()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, erreurChargement = "Impossible de charger les comptes et enveloppes : ${e.message}")
                }
            }
        }
    }

    private fun chargerAllocationsPourMois(mois: Date) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(erreurChargement = null) }
                allAllocations = enveloppeRepository.recupererAllocationsPourMois(mois).getOrThrow()
                configurerSourcesEtDestinationsPourMode()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(erreurChargement = "Impossible de charger les allocations du mois : ${e.message}")
                }
            }
        }
    }

    private fun configurerSourcesEtDestinationsPourMode() {
        when (_uiState.value.mode) {
            VirementMode.ENVELOPPES -> configurerPourModeEnveloppes()
            VirementMode.COMPTES -> configurerPourModeComptes()
        }
    }

    private fun configurerPourModeEnveloppes() {
        val itemsComptes = allComptes
            .filterIsInstance<CompteCheque>()
            .map { ItemVirement.CompteItem(it) }

        val enveloppesUi = construireEnveloppesUi()
        val enveloppesOrganisees = OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(allCategories, allEnveloppes)

        // Sources : enveloppes avec de l'argent
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

        // Destinations : toutes les enveloppes (une négative peut être renflouée)
        val destinationsEnveloppes = LinkedHashMap<String, List<ItemVirement>>()
        enveloppesOrganisees.forEach { (nomCategorie, enveloppes) ->
            val items = enveloppes
                .mapNotNull { env -> enveloppesUi.find { it.id == env.id } }
                .map { ItemVirement.EnveloppeItem(it) }
            if (items.isNotEmpty()) {
                destinationsEnveloppes[nomCategorie] = items
            }
        }

        _uiState.update {
            it.copy(
                sourcesDisponibles = LinkedHashMap<String, List<ItemVirement>>().apply {
                    put("Prêt à placer", itemsComptes)
                    putAll(sourcesEnveloppes)
                },
                destinationsDisponibles = LinkedHashMap<String, List<ItemVirement>>().apply {
                    put("Prêt à placer", itemsComptes)
                    putAll(destinationsEnveloppes)
                },
                provenanceParEnveloppe = provenancesDuMois(),
                isLoading = false
            )
        }
    }

    /** Comptes groupés par catégorie, dans l'ordre ; les dettes sont exclues. */
    private fun configurerPourModeComptes() {
        val comptesGroupes = allComptes
            .filter { it !is CompteDette }
            .sortedBy { it.ordre }
            .groupBy(
                keySelector = { compte ->
                    when (compte) {
                        is CompteCheque -> "Comptes chèque"
                        is CompteCredit -> "Cartes de crédit"
                        is CompteInvestissement -> "Investissement"
                        else -> "Autres"
                    }
                },
                valueTransform = { compte -> ItemVirement.CompteItem(compte) }
            )

        val sourcesFinales = linkedMapOf<String, List<ItemVirement>>().apply {
            put("Comptes chèque", comptesGroupes["Comptes chèque"] ?: emptyList())
            put("Cartes de crédit", comptesGroupes["Cartes de crédit"] ?: emptyList())
            put("Investissement", comptesGroupes["Investissement"] ?: emptyList())
            comptesGroupes.filterKeys { it !in this.keys }.forEach { (key, value) -> put(key, value) }
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
     * V2 : une enveloppe peut avoir plusieurs allocations dans le mois ; on les additionne
     * au lieu de prendre la première trouvée.
     */
    private fun construireEnveloppesUi(): List<EnveloppeUi> {
        val allocationsParEnveloppe = allAllocations.groupBy { it.enveloppeId }
        val provenances = provenancesDuMois()
        return allEnveloppes.filter { !it.estArchive }.map { enveloppe ->
            val allocations = allocationsParEnveloppe[enveloppe.id].orEmpty()
            val solde = allocations.sumOf { it.solde }
            val compteSource = if (solde > 0.001) provenances[enveloppe.id]?.let { id -> allComptes.find { it.id == id } } else null

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = solde,
                depense = allocations.sumOf { it.depense },
                alloue = allocations.sumOf { it.alloue },
                alloueCumulatif = allocations.sumOf { it.alloue },
                objectif = enveloppe.objectifMontant,
                couleurProvenance = compteSource?.couleur,
                statutObjectif = StatutObjectif.GRIS
            )
        }
    }

    /** Compte d'origine de l'argent de chaque enveloppe qui en contient (VA5 / V7). */
    private fun provenancesDuMois(): Map<String, String?> =
        allAllocations.groupBy { it.enveloppeId }.mapValues { (_, allocations) ->
            if (allocations.sumOf { it.solde } > 0.01) {
                allocations.filter { it.solde > 0 && it.compteSourceId != null }
                    .maxByOrNull { it.solde }?.compteSourceId
                    ?: allocations.lastOrNull { it.compteSourceId != null }?.compteSourceId
            } else {
                null
            }
        }

    // ===== GESTION DES SÉLECTEURS =====

    fun ouvrirSelecteur(type: SelecteurOuvert) {
        _uiState.update { it.copy(selecteurOuvert = type) }
    }

    /** V5 : fermer explicitement (clic extérieur ou sur le sélecteur déjà ouvert). */
    fun fermerSelecteur() {
        _uiState.update { it.copy(selecteurOuvert = SelecteurOuvert.AUCUN) }
    }

    fun onItemSelected(item: ItemVirement) {
        val currentSelecteur = _uiState.value.selecteurOuvert
        if (currentSelecteur == SelecteurOuvert.SOURCE) {
            _uiState.update {
                it.copy(sourceSelectionnee = item, erreur = null, selecteurOuvert = SelecteurOuvert.AUCUN)
            }
        } else if (currentSelecteur == SelecteurOuvert.DESTINATION) {
            val source = _uiState.value.sourceSelectionnee
            if (source != null && memeItem(source, item)) {
                _uiState.update {
                    it.copy(erreur = VirementErrorMessages.General.SOURCE_DESTINATION_IDENTIQUES, selecteurOuvert = SelecteurOuvert.AUCUN)
                }
            } else {
                _uiState.update {
                    it.copy(destinationSelectionnee = item, erreur = null, selecteurOuvert = SelecteurOuvert.AUCUN)
                }
            }
        }
        updateVirementButtonState()
    }

    fun onEnveloppeSelected(enveloppeUi: EnveloppeUi, isSource: Boolean) {
        val item = ItemVirement.EnveloppeItem(enveloppeUi)

        if (isSource) {
            _uiState.update { it.copy(sourceSelectionnee = item, erreur = null) }
        } else {
            val source = _uiState.value.sourceSelectionnee
            if (source != null && memeItem(source, item)) {
                _uiState.update { it.copy(erreur = VirementErrorMessages.General.SOURCE_DESTINATION_IDENTIQUES) }
            } else {
                _uiState.update { it.copy(destinationSelectionnee = item, erreur = null) }
            }
        }
        updateVirementButtonState()
    }

    /** VA4 : reprendre un couple récent ; l'utilisateur saisit toujours le montant et confirme. */
    fun selectionnerCouple(source: ItemVirement, destination: ItemVirement) {
        _uiState.update {
            it.copy(sourceSelectionnee = source, destinationSelectionnee = destination, erreur = null)
        }
        updateVirementButtonState()
    }

    // ===== GESTION DU MONTANT =====

    /** Le montant est reçu en centimes depuis ChampUniversel. */
    fun onMontantChange(nouveauMontantEnCentimes: String) {
        if (nouveauMontantEnCentimes.length <= 8) {
            _uiState.update { it.copy(montant = nouveauMontantEnCentimes, erreur = null) }
            updateVirementButtonState()
        }
    }

    private fun updateVirementButtonState() {
        val state = _uiState.value
        val montantValide = (state.montant.toLongOrNull() ?: 0L) > 0
        _uiState.update {
            it.copy(isVirementButtonEnabled = state.sourceSelectionnee != null && state.destinationSelectionnee != null && montantValide)
        }
    }

    // ===== EXÉCUTION DU VIREMENT =====

    fun onVirementExecute() {
        val state = _uiState.value
        val source = state.sourceSelectionnee
        val destination = state.destinationSelectionnee
        val montantEnCentimes = state.montant.toLongOrNull() ?: 0L
        val montantEnDollars = montantEnCentimes / 100.0

        when {
            source == null -> { _uiState.update { it.copy(erreur = VirementErrorMessages.General.SOURCE_NULL) }; return }
            destination == null -> { _uiState.update { it.copy(erreur = VirementErrorMessages.General.DESTINATION_NULL) }; return }
            montantEnCentimes <= 0 -> { _uiState.update { it.copy(erreur = VirementErrorMessages.General.MONTANT_INVALIDE) }; return }
            memeItem(source, destination) -> {
                _uiState.update { it.copy(erreur = VirementErrorMessages.General.SOURCE_DESTINATION_IDENTIQUES) }
                return
            }
        }
        // AT14 : verrou pris AU MOMENT de l'appui (un deuxième appui 100 ms plus tard est ignoré).
        // Libéré sur échec, ou quand l'écran a traité la réussite (sélections vidées).
        if (!verrouVirement.prendre()) return
        _uiState.update { it.copy(isLoading = true, erreur = null) }

        viewModelScope.launch {
            try {
            val mois = state.moisSelectionne
            val sourcePretAPlacer = compteIdPretAPlacer(source!!)
            val destPretAPlacer = compteIdPretAPlacer(destination!!)

            val resultat = when {
                // Vrai transfert entre comptes (mode Comptes) : crée des transactions
                state.mode == VirementMode.COMPTES &&
                    source is ItemVirement.CompteItem && destination is ItemVirement.CompteItem ->
                    argentService.effectuerVirementEntreComptes(
                        compteSourceId = source.compte.id,
                        compteDestId = destination.compte.id,
                        montant = montantEnDollars,
                        nomCompteSource = source.compte.nom,
                        nomCompteDest = destination.compte.nom
                    )

                sourcePretAPlacer != null && destPretAPlacer != null ->
                    Result.failure(Exception("Choisissez une enveloppe comme destination."))

                sourcePretAPlacer != null && destination is ItemVirement.EnveloppeItem ->
                    virementBudgetService.pretAPlacerVersEnveloppe(sourcePretAPlacer, destination.enveloppe.id, montantEnDollars, mois)

                source is ItemVirement.EnveloppeItem && destPretAPlacer != null ->
                    virementBudgetService.enveloppeVersPretAPlacer(source.enveloppe.id, destPretAPlacer, montantEnDollars, mois)

                source is ItemVirement.EnveloppeItem && destination is ItemVirement.EnveloppeItem ->
                    virementBudgetService.enveloppeVersEnveloppe(source.enveloppe.id, destination.enveloppe.id, montantEnDollars, mois)

                else -> Result.failure(Exception(VirementErrorMessages.General.TYPE_VIREMENT_NON_SUPPORTE))
            }

            resultat
                .onSuccess {
                    // V4 / V6 : succès annoncé seulement après l'écriture locale validée
                    _uiState.update { it.copy(virementReussi = true, erreur = null, isLoading = false) }
                    BudgetEvents.onAllocationUpdated()
                    realtimeSyncService.declencherMiseAJourBudget()
                    chargerDonneesInitiales()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, virementReussi = false, erreur = e.message ?: "Erreur inconnue lors du virement")
                    }
                    verrouVirement.liberer()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, virementReussi = false,
                    erreur = e.message ?: "Erreur inattendue pendant le virement") }
                verrouVirement.liberer()
            }
        }
    }

    private val verrouVirement = com.xburnsx.toutiebudget.utils.VerrouOperation()

    // ===== UTILITAIRES =====

    /** Id du compte quand l'item représente un « Prêt à placer » (compte chèque ou enveloppe virtuelle). */
    private fun compteIdPretAPlacer(item: ItemVirement): String? = when (item) {
        is ItemVirement.CompteItem -> item.compte.id.takeIf { item.compte is CompteCheque && _uiState.value.mode == VirementMode.ENVELOPPES }
        is ItemVirement.EnveloppeItem -> if (estPretAPlacer(item.enveloppe)) extraireCompteIdDepuisPretAPlacer(item.enveloppe.id) else null
    }

    private fun memeItem(item1: ItemVirement, item2: ItemVirement): Boolean = when {
        item1 is ItemVirement.CompteItem && item2 is ItemVirement.CompteItem -> item1.compte.id == item2.compte.id
        item1 is ItemVirement.EnveloppeItem && item2 is ItemVirement.EnveloppeItem -> item1.enveloppe.id == item2.enveloppe.id
        else -> false
    }

    private fun estPretAPlacer(enveloppe: EnveloppeUi): Boolean = enveloppe.id.startsWith(PREFIXE_PRET_A_PLACER)

    private fun extraireCompteIdDepuisPretAPlacer(enveloppeId: String): String = enveloppeId.removePrefix(PREFIXE_PRET_A_PLACER)

    fun effacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }

    /** Nettoie la page après un virement réussi. */
    fun onVirementReussiHandled() {
        _uiState.update {
            it.copy(
                virementReussi = false,
                montant = "",
                sourceSelectionnee = null,
                destinationSelectionnee = null,
                erreur = null,
                selecteurOuvert = SelecteurOuvert.AUCUN,
                isVirementButtonEnabled = false
            )
        }
        verrouVirement.liberer()
    }

    companion object {
        const val PREFIXE_PRET_A_PLACER = "pret_a_placer_"
    }
}
