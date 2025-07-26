// chemin/simule: /ui/budget/BudgetViewModel.kt
// Dépendances: Remplacez temporairement votre BudgetViewModel par cette version pour diagnostic

package com.xburnsx.toutiebudget.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.domain.usecases.VerifierEtExecuterRolloverUseCase
import com.xburnsx.toutiebudget.domain.services.ValidationProvenanceService
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BudgetViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase,
    private val realtimeSyncService: RealtimeSyncService,
    private val validationProvenanceService: ValidationProvenanceService
) : ViewModel() {

    // --- Cache en mémoire pour éviter les écrans de chargement ---
    private var cacheComptes: List<Compte> = emptyList()
    private var cacheEnveloppes: List<Enveloppe> = emptyList()
    private var cacheAllocations: List<AllocationMensuelle> = emptyList()
    private var cacheCategories: List<Categorie> = emptyList()
    
    // Garder en mémoire le mois sélectionné pour les rafraîchissements automatiques
    private var moisSelectionne: Date = Date()

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageChargement = "Vérification du budget...") }

            // 🔄 ROLLOVER AUTOMATIQUE : Seulement si on est le 1er du mois
            val aujourdhui = Calendar.getInstance()
            val estPremierDuMois = aujourdhui.get(Calendar.DAY_OF_MONTH) == 1

            if (estPremierDuMois) {
                println("[ROLLOVER] 📅 1er du mois détecté - Vérification du rollover automatique")
                verifierEtExecuterRolloverUseCase().onSuccess {
                    println("[ROLLOVER] ✅ Rollover automatique effectué")
                    chargerDonneesBudget(Date())
                }.onFailure { e ->
                    println("[ROLLOVER] ❌ Erreur rollover automatique: ${e.message}")
                    _uiState.update { it.copy(erreur = "Erreur de rollover: ${e.message}") }
                    chargerDonneesBudget(Date())
                }
            } else {
                println("[ROLLOVER] 📅 Pas le 1er du mois - Chargement normal sans rollover")
                chargerDonneesBudget(Date())
            }
        }

        // Abonnement à l'event bus pour rafraîchir le budget (si présent)
        viewModelScope.launch {
            try {
                BudgetEvents.refreshBudget.collectLatest {
                    chargerDonneesBudget(moisSelectionne)
                }
            } catch (e: Exception) {
                // BudgetEvents peut ne pas exister, on ignore cette erreur
            }
        }

        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase
        viewModelScope.launch {
            realtimeSyncService.budgetUpdated.collectLatest {
                println("[REALTIME] 🔄 Budget mis à jour automatiquement")
                chargerDonneesBudget(moisSelectionne)
            }
        }
    }

    /**
     * Charge les données du budget pour un mois spécifique.
     * Affiche les données EXACTES du mois sans rollover automatique.
     */
    fun chargerDonneesBudget(moisCible: Date = Date()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageChargement = "Chargement des données...") }
            
            try {
                // ⚠️ PLUS DE ROLLOVER AUTOMATIQUE ICI
                // Le rollover se fait seulement le 1er du mois dans init()

                // 1. Charger les comptes
                _uiState.update { it.copy(messageChargement = "Chargement des comptes...") }
                val resultComptes = compteRepository.recupererTousLesComptes()
                val comptes = resultComptes.getOrElse {
                    emptyList() 
                }
                cacheComptes = comptes

                // 2. Charger les enveloppes
                _uiState.update { it.copy(messageChargement = "Chargement des enveloppes...") }
                val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                val enveloppes = resultEnveloppes.getOrElse {
                    emptyList() 
                }
                cacheEnveloppes = enveloppes

                // 3. Charger les catégories
                _uiState.update { it.copy(messageChargement = "Chargement des catégories...") }
                val resultCategories = categorieRepository.recupererToutesLesCategories()
                val categories = resultCategories.getOrElse {
                    emptyList() 
                }
                cacheCategories = categories

                // 4. Charger les allocations EXACTES pour le mois spécifique UNIQUEMENT
                _uiState.update { it.copy(messageChargement = "Chargement des allocations mensuelles...") }
                val premierJourDuMois = obtenirPremierJourDuMois(moisCible)
                val dateFormatee = formatDatePourDebug(premierJourDuMois)
                val moisActuel = obtenirPremierJourDuMois(Date())

                // 🔍 DEBUG : Vérifier si on regarde un mois différent du mois actuel
                val regardeMoisDifferent = premierJourDuMois.time != moisActuel.time
                if (regardeMoisDifferent) {
                    println("[BUDGET] 🔍 Navigation vers un mois différent:")
                    println("[BUDGET] 📅 Mois sélectionné: $dateFormatee")
                    println("[BUDGET] 📅 Mois actuel: ${formatDatePourDebug(moisActuel)}")
                    println("[BUDGET] 💡 Affichage des données EXACTES du mois sélectionné")
                } else {
                    println("[BUDGET] 📅 Affichage du mois actuel: $dateFormatee")
                }

                val resultAllocations = enveloppeRepository.recupererAllocationsPourMois(premierJourDuMois)
                val allocations = resultAllocations.getOrElse {
                    emptyList() 
                }
                
                println("[BUDGET] 📊 ${allocations.size} allocations trouvées pour $dateFormatee")
                if (allocations.isEmpty()) {
                    println("[BUDGET] 💡 Aucune allocation = toutes les enveloppes à 0$ pour ce mois")
                } else {
                    println("[BUDGET] 💰 Allocations trouvées:")
                    allocations.forEach { allocation ->
                        println("[BUDGET]   • ${allocation.enveloppeId}: ${allocation.solde}$ (dépensé: ${allocation.depense}$)")
                    }
                }

                cacheAllocations = allocations

                // 5. Créer les bandeaux "Prêt à placer"
                val bandeauxPretAPlacer = creerBandeauxPretAPlacer(comptes)

                // 6. Créer les enveloppes UI avec les allocations DU MOIS SPÉCIFIQUE
                val enveloppesUi = creerEnveloppesUi(enveloppes, allocations, comptes)
                
                // Debug des enveloppes UI créées
                enveloppesUi.forEachIndexed { index, env ->
                }

                // 7. Grouper par catégories en utilisant la même logique que CategoriesEnveloppesScreen
                val enveloppesGroupees = OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(categories, enveloppes)

                // 8. Créer les CategorieEnveloppesUi en respectant l'ordre des catégories
                val categoriesEnveloppesUi = enveloppesGroupees.map { (nomCategorie, enveloppesCategorie) ->
                    // Filtrer les enveloppes UI pour ne garder que celles de cette catégorie
                    val enveloppesUiCategorie = enveloppesUi.filter { enveloppeUi ->
                        enveloppesCategorie.any { it.id == enveloppeUi.id }
                    }.sortedBy { enveloppeUi ->
                        // Trier selon l'ordre des enveloppes dans la catégorie
                        enveloppesCategorie.indexOfFirst { it.id == enveloppeUi.id }
                    }

                    CategorieEnveloppesUi(nomCategorie, enveloppesUiCategorie)
                }

                // 9. Mettre à jour l'état final
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bandeauxPretAPlacer = bandeauxPretAPlacer,
                        categoriesEnveloppes = categoriesEnveloppesUi,
                        messageChargement = null,
                        erreur = null
                    )
                }
                moisSelectionne = moisCible

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        erreur = "Erreur lors du chargement des données: ${e.message}",
                        messageChargement = null
                    ) 
                }
            }
        }
    }

    /**
     * Crée les enveloppes UI en combinant les données des enveloppes et allocations.
     * Version avec diagnostic détaillé pour identifier le problème de correspondance.
     */
    private fun creerEnveloppesUi(
        enveloppes: List<Enveloppe>,
        allocations: List<AllocationMensuelle>,
        comptes: List<Compte>
    ): List<EnveloppeUi> {
        
        // Créer une map des allocations par ID d'enveloppe pour un accès rapide
        val mapAllocations = allocations.associateBy { it.enveloppeId }

        mapAllocations.forEach { (enveloppeId, allocation) ->

        }
        
        // Créer une map des comptes par ID pour récupérer les couleurs
        val mapComptes = comptes.associateBy { it.id }


        val resultat = enveloppes.mapIndexed { index, enveloppe ->

            
            // Afficher les caractères de l'ID pour debug

            
            // Récupérer l'allocation mensuelle correspondante
            val allocation = mapAllocations[enveloppe.id]
            if (allocation != null) {

            } else {

                mapAllocations.keys.forEachIndexed { idx, key ->

                    
                    // Comparaison caractère par caractère si les longueurs sont différentes
                    if (enveloppe.id.length != key.length) {

                    } else {
                        // Comparaison caractère par caractère
                        val differences = mutableListOf<Int>()
                        enveloppe.id.forEachIndexed { charIndex, char ->
                            if (charIndex < key.length && char != key[charIndex]) {
                                differences.add(charIndex)
                            }
                        }
                        if (differences.isNotEmpty()) {

                            differences.forEach { pos ->

                            }
                        }
                    }
                }
            }
            
            // Récupérer le compte source pour la couleur
            val compteSource = allocation?.compteSourceId?.let { mapComptes[it] }
            if (allocation?.compteSourceId != null) {
                println("[DEBUG] Recherche compteSource - compteSourceId: ${allocation.compteSourceId}")
                println("[DEBUG] mapComptes contient: ${mapComptes.keys}")
                println("[DEBUG] compteSource trouvé: ${compteSource?.nom} - couleur: ${compteSource?.couleur}")
            }
            
            // Utiliser les valeurs de l'allocation ou 0.0 par défaut
            val solde = allocation?.solde ?: 0.0
            val depense = allocation?.depense ?: 0.0
            val objectif = enveloppe.objectifMontant

            
            // Calculer le statut de l'objectif
            val statut = when {
                objectif > 0 && solde >= objectif -> StatutObjectif.VERT
                solde > 0 -> StatutObjectif.JAUNE
                else -> StatutObjectif.GRIS
            }
            
            // Formater la date d'objectif si elle existe
            val dateObjectifFormatee = enveloppe.objectifDate?.let { date ->
                val format = SimpleDateFormat("dd", Locale.getDefault())
                val dateFormatee = format.format(date)
                println("[DEBUG] Enveloppe '${enveloppe.nom}' - Date objectif: $date -> formatée: $dateFormatee")
                dateFormatee
            }

            // Debug pour voir si la date est nulle
            if (enveloppe.objectifDate == null) {
                println("[DEBUG] Enveloppe '${enveloppe.nom}' - Pas de date d'objectif (null)")
            }

            val enveloppeUi = EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = solde,
                depense = depense,
                objectif = objectif,
                couleurProvenance = compteSource?.couleur,
                statutObjectif = statut,
                dateObjectif = dateObjectifFormatee // Ajouter la date d'objectif formatée
            )
            
            // Debug final pour voir ce qui est dans EnveloppeUi
            println("[DEBUG] EnveloppeUi créée - '${enveloppeUi.nom}' - dateObjectif: ${enveloppeUi.dateObjectif}")

            enveloppeUi
        }
        

        
        return resultat
    }

    /**
     * Crée les bandeaux "Prêt à placer" à partir des comptes chèque ayant un montant "prêt à placer" positif.
     */
    private fun creerBandeauxPretAPlacer(comptes: List<Compte>): List<PretAPlacerUi> {
        return comptes
            .filterIsInstance<CompteCheque>()
            .filter { it.pretAPlacer > 0 }
            .map { compte ->
                PretAPlacerUi(
                    compteId = compte.id,
                    nomCompte = compte.nom,
                    montant = compte.pretAPlacer,
                    couleurCompte = compte.couleur
                )
            }
    }

    /**
     * Obtient le premier jour du mois pour une date donnée.
     * Important pour la requête des allocations mensuelles.
     */
    private fun obtenirPremierJourDuMois(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Formate une date pour le debug.
     */
    private fun formatDatePourDebug(date: Date): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return format.format(date)
    }

    /**
     * Méthode publique pour rafraîchir les données depuis d'autres ViewModels.
     * Utilisée quand une transaction est créée pour mettre à jour l'affichage.
     */
    fun rafraichirDonnees() {

        chargerDonneesBudget(Date())
    }

    /**
     * Efface l'erreur actuelle.
     */
    fun effacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }

    /**
     * 💰 ASSIGNER DE L'ARGENT D'UN COMPTE VERS UNE ENVELOPPE
     *
     * Cette méthode effectue un virement interne :
     * 1. Retire l'argent du "prêt à placer" du compte source
     * 2. Ajoute l'argent au solde de l'enveloppe cible
     * 3. Met à jour la couleur de provenance de l'enveloppe
     *
     * @param enveloppeId ID de l'enveloppe qui recevra l'argent
     * @param compteSourceId ID du compte d'où vient l'argent
     * @param montantCentimes Montant en centimes à transférer
     */
    fun assignerArgentAEnveloppe(
        enveloppeId: String,
        compteSourceId: String,
        montantCentimes: Long
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, messageChargement = "Assignation de l'argent...") }

                val montantDollars = montantCentimes / 100.0

                println("[BUDGET] 💰 Assignation d'argent:")
                println("[BUDGET]   • Enveloppe: $enveloppeId")
                println("[BUDGET]   • Compte source: $compteSourceId")
                println("[BUDGET]   • Montant: ${montantDollars}$")

                // 1. Vérifier que le compte source existe et a assez d'argent "prêt à placer"
                val compteSource = cacheComptes.find { it.id == compteSourceId }
                if (compteSource !is CompteCheque) {
                    throw Exception("Compte source non trouvé ou n'est pas un compte chèque")
                }

                if (compteSource.pretAPlacer < montantDollars) {
                    throw Exception("Montant insuffisant dans le compte (${compteSource.pretAPlacer}$ disponible)")
                }

                // 2. Vérifier que l'enveloppe existe
                val enveloppe = cacheEnveloppes.find { it.id == enveloppeId }
                if (enveloppe == null) {
                    throw Exception("Enveloppe non trouvée")
                }

                // 🔒 VALIDATION DE PROVENANCE - Nouveau contrôle
                val moisActuelValidation = obtenirPremierJourDuMois(moisSelectionne)
                val validationResult = validationProvenanceService.validerAjoutArgentEnveloppe(
                    enveloppeId = enveloppeId,
                    compteSourceId = compteSourceId,
                    mois = moisActuelValidation
                )

                if (validationResult.isFailure) {
                    throw Exception(validationResult.exceptionOrNull()?.message ?: "Erreur de validation de provenance")
                }

                // 3. Mettre à jour le compte source (retirer de "prêt à placer")
                val nouveauPretAPlacer = compteSource.pretAPlacer - montantDollars
                val compteModifie = compteSource.copy(
                    pretAPlacerRaw = nouveauPretAPlacer,
                    collection = compteSource.collection ?: "comptes_cheque" // Assurer qu'on a une collection
                )

                val resultCompte = compteRepository.mettreAJourCompte(compteModifie)
                if (resultCompte.isFailure) {
                    throw Exception("Erreur lors de la mise à jour du compte: ${resultCompte.exceptionOrNull()?.message}")
                }

                // 4. Vérifier s'il existe déjà une allocation pour ce mois
                val moisActuel = obtenirPremierJourDuMois(moisSelectionne)
                val resultAllocationExistante = enveloppeRepository.recupererAllocationMensuelle(enveloppeId, moisActuel)

                if (resultAllocationExistante.isFailure) {
                    throw Exception("Erreur lors de la vérification de l'allocation: ${resultAllocationExistante.exceptionOrNull()?.message}")
                }

                val allocationExistante = resultAllocationExistante.getOrNull()

                // 5. Créer la nouvelle allocation avec le bon solde
                val nouveauSolde = if (allocationExistante != null) {
                    allocationExistante.solde + montantDollars
                } else {
                    montantDollars
                }

                val nouvelleAllocation = AllocationMensuelle(
                    id = "", // Sera généré par PocketBase
                    utilisateurId = compteSource.utilisateurId,
                    enveloppeId = enveloppeId,
                    mois = moisActuel,
                    solde = nouveauSolde,
                    alloue = montantDollars, // Le montant qu'on vient de placer
                    depense = allocationExistante?.depense ?: 0.0, // Garder les dépenses existantes
                    compteSourceId = compteSourceId,
                    collectionCompteSource = compteSource.collection ?: "comptes_cheque"
                )

                val resultAllocation = enveloppeRepository.creerAllocationMensuelle(nouvelleAllocation)

                if (resultAllocation.isFailure) {
                    throw Exception("Erreur lors de la création de l'allocation: ${resultAllocation.exceptionOrNull()?.message}")
                }

                println("[BUDGET] ✅ Assignation réussie!")
                println("[BUDGET]   • Nouveau prêt à placer du compte: ${nouveauPretAPlacer}$")
                println("[BUDGET]   • Nouveau solde de l'enveloppe: ${nouveauSolde}$")

                // 6. Recharger les données pour rafraîchir l'affichage
                chargerDonneesBudget(moisSelectionne)

            } catch (e: Exception) {
                println("[BUDGET] ❌ Erreur lors de l'assignation: ${e.message}")
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        erreur = "Erreur lors de l'assignation: ${e.message}",
                        messageChargement = null
                    )
                }
            }
        }
    }
}