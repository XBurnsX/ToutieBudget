// chemin/simule: /ui/budget/BudgetViewModel.kt
// Dépendances: Remplacez temporairement votre BudgetViewModel par cette version pour diagnostic

package com.xburnsx.toutiebudget.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.data.utils.ObjectifCalculator
import com.xburnsx.toutiebudget.domain.usecases.VerifierEtExecuterRolloverUseCase
import com.xburnsx.toutiebudget.domain.services.ValidationProvenanceService
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
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
    private val allocationMensuelleRepository: AllocationMensuelleRepository, // ← AJOUT
    private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase,
    private val realtimeSyncService: RealtimeSyncService,
    private val validationProvenanceService: ValidationProvenanceService,
    private val objectifCalculator: ObjectifCalculator
) : ViewModel() {

    // Service de reset automatique des objectifs bihebdomadaires
    private val objectifResetService = com.xburnsx.toutiebudget.data.services.ObjectifResetService(enveloppeRepository, allocationMensuelleRepository)

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

            // 🔄 RESET AUTOMATIQUE DES OBJECTIFS BIHEBDOMADAIRES
            objectifResetService.verifierEtResetterObjectifsBihebdomadaires().onSuccess { enveloppesResetees ->
                if (enveloppesResetees.isNotEmpty()) {
                    enveloppesResetees.forEach { enveloppe ->
                    }
                } else {
                }
            }.onFailure { e ->
            }

            // 🔄 ROLLOVER AUTOMATIQUE : À chaque connexion dans un nouveau mois (pas seulement le 1er !)
            println("[BUDGET_VM] 🔄 Vérification automatique du rollover...")
            verifierEtExecuterRolloverUseCase().onSuccess {
                println("[BUDGET_VM] ✅ Vérification rollover terminée")
                chargerDonneesBudget(Date())
            }.onFailure { e ->
                println("[BUDGET_VM] ❌ Erreur rollover: ${e.message}")
                _uiState.update { it.copy(erreur = "Erreur de rollover: ${e.message}") }
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

                val resultAllocations = enveloppeRepository.recupererAllocationsPourMois(premierJourDuMois)
                val allocations = resultAllocations.getOrElse {
                    emptyList() 
                }

                // 4b. Charger TOUTES les allocations passées pour les objectifs intelligents
                _uiState.update { it.copy(messageChargement = "Chargement de l'historique des allocations...") }
                val toutesAllocationsPassées = mutableListOf<AllocationMensuelle>()
                enveloppes.forEach { enveloppe ->
                    val allocationsEnveloppe = enveloppeRepository.recupererAllocationsEnveloppe(enveloppe.id).getOrElse { emptyList() }
                    toutesAllocationsPassées.addAll(allocationsEnveloppe)
                }
                


                cacheAllocations = allocations

                // 5. Créer les bandeaux "Prêt à placer"
                val bandeauxPretAPlacer = creerBandeauxPretAPlacer(comptes)

                // 6. Créer les enveloppes UI avec les allocations DU MOIS SPÉCIFIQUE
                val enveloppesUi = creerEnveloppesUi(enveloppes, allocations, comptes, toutesAllocationsPassées, moisCible)
                
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
        comptes: List<Compte>,
        toutesAllocationsPassées: List<AllocationMensuelle>,
        moisCible: Date
    ): List<EnveloppeUi> {
        
        // Grouper les allocations par ID d'enveloppe pour pouvoir les sommer
        val allocationsGroupees = allocations.groupBy { it.enveloppeId }

        // Créer une map des comptes par ID pour récupérer les couleurs
        val mapComptes = comptes.associateBy { it.id }

        return enveloppes.map { enveloppe ->

            // Récupérer toutes les allocations pour cette enveloppe pour le mois en cours
            val allocationsDeLEnveloppe = allocationsGroupees[enveloppe.id] ?: emptyList()

            // Calculer le solde, les dépenses ET le total alloué en faisant la SOMME de toutes les allocations concernées
            val soldeTotal = allocationsDeLEnveloppe.sumOf { it.solde }
            val depenseTotale = allocationsDeLEnveloppe.sumOf { it.depense }
            val alloueTotal = allocationsDeLEnveloppe.sumOf { it.alloue } // Total alloué ce mois
            
            // ✨ NOUVEAU : Calculer l'alloué cumulatif depuis le début de l'objectif (même logique qu'ObjectifCalculator)
            val alloueCumulatif = calculerAllocationCumulativeDepuisDebutObjectif(
                enveloppe, 
                toutesAllocationsPassées.filter { it.enveloppeId == enveloppe.id },
                moisCible
            )

            // Pour la couleur, on se base sur la provenance de la dernière allocation (la plus récente)
            val derniereAllocation = allocationsDeLEnveloppe.lastOrNull()
            val compteSource = derniereAllocation?.compteSourceId?.let { mapComptes[it] }

            // Utiliser les valeurs de l'allocation ou 0.0 par défaut
            val objectif = enveloppe.objectifMontant

            // 🎯 SOLUTION SIMPLE : soldeTotal contient déjà la bonne valeur du mois !
            val progresActuel = when (enveloppe.typeObjectif) {
                // Pour épargne/accumulation : utiliser soldeTotal (déjà correct pour ce mois)
                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Bihebdomadaire,
                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Echeance,
                com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Annuel -> soldeTotal
                
                // Pour objectifs de dépense : solde + dépenses du mois
                else -> soldeTotal + depenseTotale
            }
            
            val versementRecommande = objectifCalculator.calculerVersementRecommande(
                enveloppe, 
                progresActuel,
                moisCible,  // ← CORRECTION : utiliser moisCible au lieu de moisSelectionne
                toutesAllocationsPassées.filter { it.enveloppeId == enveloppe.id }
            )

            // Calculer le statut de l'objectif
            val statut = when {
                objectif > 0 && soldeTotal >= objectif -> StatutObjectif.VERT
                soldeTotal > 0 -> StatutObjectif.JAUNE
                else -> StatutObjectif.GRIS
            }
            
            // Formater la date d'objectif si elle existe
            val dateObjectifFormatee = enveloppe.dateObjectif?.let { dateString ->
                // dateObjectif est maintenant une String, donc pas besoin de formatter
                dateString
            }

            // Pour les objectifs bihebdomadaires, calculer la date limite dynamique
            val dateObjectifDynamique = if (enveloppe.typeObjectif == com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Bihebdomadaire) {
                // Note: La fonction calculerDateLimiteBihebdomadaire n'existe plus, utilisons dateObjectif directement
                enveloppe.dateObjectif ?: dateObjectifFormatee
            } else if (enveloppe.typeObjectif == com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel && enveloppe.objectifJour != null) {
                // Pour les objectifs mensuels, utiliser le jour du mois (ex: le 25 de chaque mois)
                enveloppe.objectifJour.toString()
            } else {
                dateObjectifFormatee
            }

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = soldeTotal,
                depense = depenseTotale,
                alloue = alloueTotal, // Total alloué ce mois
                alloueCumulatif = alloueCumulatif, // ← NOUVEAU : Total alloué depuis le début (pour barres de progression)
                objectif = objectif,
                couleurProvenance = compteSource?.couleur,
                statutObjectif = statut,
                dateObjectif = dateObjectifDynamique, // Utiliser la date dynamique
                versementRecommande = versementRecommande,
                typeObjectif = enveloppe.typeObjectif // Utiliser le nouveau nom de propriété
            )
        }
    }

    /**
     * Calcule l'allocation cumulative depuis le début de l'objectif (même logique qu'ObjectifCalculator).
     * Retourne le total alloué depuis le début de l'objectif jusqu'au mois sélectionné inclus.
     */
    private fun calculerAllocationCumulativeDepuisDebutObjectif(
        enveloppe: Enveloppe,
        allocations: List<AllocationMensuelle>,
        moisSelectionne: Date
    ): Double {
        val dateDebut = enveloppe.dateDebutObjectif ?: return allocations.filter { allocation ->
            val calendarAllocation = Calendar.getInstance()
            calendarAllocation.time = allocation.mois
            val calendarSelectionne = Calendar.getInstance()
            calendarSelectionne.time = moisSelectionne
            
            // Inclure toutes les allocations jusqu'au mois sélectionné inclus
            (calendarAllocation.get(Calendar.YEAR) < calendarSelectionne.get(Calendar.YEAR)) ||
            (calendarAllocation.get(Calendar.YEAR) == calendarSelectionne.get(Calendar.YEAR) && 
             calendarAllocation.get(Calendar.MONTH) <= calendarSelectionne.get(Calendar.MONTH))
        }.sumOf { it.alloue }

        val calendarDebut = Calendar.getInstance()
        calendarDebut.time = dateDebut
        val anneeDebut = calendarDebut.get(Calendar.YEAR)
        val moisDebut = calendarDebut.get(Calendar.MONTH)
        
        val calendarSelectionne = Calendar.getInstance()
        calendarSelectionne.time = moisSelectionne
        val anneeSelectionnee = calendarSelectionne.get(Calendar.YEAR)
        val moisSelectionneInt = calendarSelectionne.get(Calendar.MONTH)
        
        return allocations.filter { allocation ->
            val calendarAllocation = Calendar.getInstance()
            calendarAllocation.time = allocation.mois
            val anneeAllocation = calendarAllocation.get(Calendar.YEAR)
            val moisAllocation = calendarAllocation.get(Calendar.MONTH)
            
            // Inclure les allocations depuis dateDebut
            val apresDebut = (anneeAllocation > anneeDebut) || 
                           (anneeAllocation == anneeDebut && moisAllocation >= moisDebut)
            
            // Inclure jusqu'au mois sélectionné inclus
            val avantOuEgalSelection = (anneeAllocation < anneeSelectionnee) || 
                                     (anneeAllocation == anneeSelectionnee && moisAllocation <= moisSelectionneInt)
            
            apresDebut && avantOuEgalSelection
        }.sumOf { it.alloue }
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



                // 1. Vérifier que le compte source existe et a assez d'argent "prêt à placer"
                val compteSource = cacheComptes.find { it.id == compteSourceId }
                if (compteSource !is CompteCheque) {
                    throw Exception(VirementErrorMessages.PretAPlacerVersEnveloppe.enveloppeIntrouvable("Compte source"))
                }

                if (compteSource.pretAPlacer < montantDollars) {
                    throw Exception(VirementErrorMessages.ClavierBudget.fondsInsuffisants(compteSource.pretAPlacer))
                }

                // 2. Vérifier que l'enveloppe existe
                val enveloppe = cacheEnveloppes.find { it.id == enveloppeId }
                if (enveloppe == null) {
                    throw Exception(VirementErrorMessages.PretAPlacerVersEnveloppe.enveloppeIntrouvable("Enveloppe cible"))
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

                // 4. ✅ LOGIQUE CORRIGÉE : METTRE À JOUR l'allocation existante au lieu de créer un doublon
                val moisActuel = obtenirPremierJourDuMois(moisSelectionne)
                
                // ✅ FUSION COMPLÈTE : Récupérer l'allocation fusionnée ET l'augmenter directement
                val allocationFusionnee = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, moisActuel)
                
                // 🔧 DEBUG : Avant modification
                println("[DEBUG_FUSION] 🔍 AVANT: Allocation fusionnée: ID=${allocationFusionnee.id}, solde=${allocationFusionnee.solde}, compteSourceId=${allocationFusionnee.compteSourceId}")
                
                // ✅ MODIFIER DIRECTEMENT l'allocation fusionnée
                val allocationFinale = allocationFusionnee.copy(
                    solde = allocationFusionnee.solde + montantDollars,
                    alloue = allocationFusionnee.alloue + montantDollars,
                    // ✅ PROVENANCE : Changer seulement si solde était à 0
                    compteSourceId = if (allocationFusionnee.solde <= 0.01) compteSourceId else allocationFusionnee.compteSourceId,
                    collectionCompteSource = if (allocationFusionnee.solde <= 0.01) (compteSource.collection ?: "comptes_cheque") else allocationFusionnee.collectionCompteSource
                )
                
                // 🔧 DEBUG : Après modification
                println("[DEBUG_FUSION] 🔍 APRÈS: Allocation finale: ID=${allocationFinale.id}, solde=${allocationFinale.solde}, compteSourceId=${allocationFinale.compteSourceId}")
                
                // ✅ MISE À JOUR : Sauvegarder l'allocation unique
                allocationMensuelleRepository.mettreAJourAllocation(allocationFinale)

                // 6. Recharger les données pour rafraîchir l'affichage
                chargerDonneesBudget(moisSelectionne)

            } catch (e: Exception) {
                e.printStackTrace()

                // Utiliser les messages d'erreur appropriés selon le type d'erreur
                val messageErreur = when {
                    // Si c'est une erreur de provenance, utiliser le message tel quel
                    VirementErrorMessages.estErreurProvenance(e.message ?: "") -> e.message ?: "Erreur inconnue"
                    // Sinon, utiliser le préfixe générique
                    else -> "Erreur lors de l'assignation: ${e.message}"
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        erreur = messageErreur,
                        messageChargement = null
                    )
                }
            }
        }
    }
}