// chemin/simule: /ui/budget/BudgetViewModel.kt
// Dépendances: Remplacez temporairement votre BudgetViewModel par cette version pour diagnostic

package com.xburnsx.toutiebudget.ui.budget

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.data.utils.ObjectifCalculator
import com.xburnsx.toutiebudget.domain.services.ValidationProvenanceService
import com.xburnsx.toutiebudget.domain.usecases.VerifierEtExecuterRolloverUseCase
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BudgetViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository, // ← AJOUT
    private val preferenceRepository: com.xburnsx.toutiebudget.data.repositories.PreferenceRepository,
    private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase,
    private val realtimeSyncService: RealtimeSyncService,
    private val validationProvenanceService: ValidationProvenanceService,
    private val objectifCalculator: ObjectifCalculator,
    private val historiqueAllocationService: com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouAssignation = com.xburnsx.toutiebudget.utils.VerrouOperation()
    private val mutexPreferencesBudget = Mutex()
    @Volatile private var generationPreferencesBudget = 0L

    // Service de reset automatique des objectifs bihebdomadaires
    private val objectifResetService = com.xburnsx.toutiebudget.data.services.ObjectifResetService(enveloppeRepository, allocationMensuelleRepository)

    // --- Cache en mémoire pour éviter les écrans de chargement ---
    private var cacheComptes: List<Compte> = emptyList()
    private var cacheEnveloppes: List<Enveloppe> = emptyList()
    private var cacheAllocations: List<AllocationMensuelle> = emptyList()
    private var cacheCategories: List<Categorie> = emptyList()
    private var generationChargementBudget = 0L
    
    // Garder en mémoire le mois sélectionné pour les rafraîchissements automatiques
    private var moisSelectionne: Date = Date()

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    // Affichage des archivés
    private var showArchived: Boolean = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, messageChargement = "Vérification du budget...") }

            // Charger la préférence locale pour figer les bandeaux
            try {
                val appContext = com.xburnsx.toutiebudget.di.AppModule::class.java
                // On n'a pas de contexte global ici; laisser au Settings de l'écrire dans l'UI state via setFigerPretAPlacer()
            } catch (_: Exception) {}

            // 🔄 RESET AUTOMATIQUE DES OBJECTIFS BIHEBDOMADAIRES
            objectifResetService.verifierEtResetterObjectifsBihebdomadaires().onSuccess { enveloppesResetees ->
                // Reset bihebdomadaire effectué
            }.onFailure { e ->
                // Erreur silencieuse
            }

            // 🔄 RESET AUTOMATIQUE DES OBJECTIFS ANNUELS
            objectifResetService.verifierEtResetterObjectifsAnnuels().onSuccess { enveloppesResetees ->
                // Reset annuel effectué
            }.onFailure { e ->
                // Erreur silencieuse
            }

            // 🔄 RESET AUTOMATIQUE DES OBJECTIFS D'ÉCHÉANCE
            objectifResetService.verifierEtResetterObjectifsEcheance().onSuccess { enveloppesResetees ->
                // Reset échéance effectué
            }.onFailure { e ->
                // Erreur silencieuse
            }

            // 🔄 ROLLOVER AUTOMATIQUE : À chaque connexion dans un nouveau mois (pas seulement le 1er !)
            verifierEtExecuterRolloverUseCase().onSuccess {
                chargerDonneesBudget(Date())
            }.onFailure { e ->
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
            } catch (_: Exception) {
                // BudgetEvents peut ne pas exister, on ignore cette erreur
            }
        }

        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase
        viewModelScope.launch {
            realtimeSyncService.budgetUpdated.collectLatest {
                // DEBUG: BudgetViewModel reçoit l'événement budgetUpdated
                // 🔄 FORCER UN RECHARGEMENT COMPLET après un virement
                // Vider le cache pour s'assurer d'avoir les données les plus récentes
                cacheComptes = emptyList()
                cacheEnveloppes = emptyList()
                cacheAllocations = emptyList()
                cacheCategories = emptyList()
                
                // DEBUG: Cache vidé, rechargement des données...
                // Recharger avec les données les plus récentes
                chargerDonneesBudget(moisSelectionne)
                // DEBUG: Données rechargées
            }
        }
    }

    // Mise à jour de la préférence UI: figer les bandeaux "Prêt à placer"
    fun setFigerPretAPlacer(enabled: Boolean) {
        _uiState.update { it.copy(figerPretAPlacer = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.xburnsx.toutiebudget.di.AppModule
                    .let { it }
                // Utiliser le repository de préférences via AppModule
                val repoField = com.xburnsx.toutiebudget.di.AppModule::class.java
                // Simple: appeler directement l'impl si accessible (simulé)
                preferenceRepository.setFigerPretAPlacer(enabled)
            } catch (_: Exception) {}
        }
    }

    /**
     * Charge les données du budget pour un mois spécifique.
     * Affiche les données EXACTES du mois sans rollover automatique.
     */
    fun chargerDonneesBudget(moisCible: Date = Date()) {
        val generation = ++generationChargementBudget
        // Le mois demandé doit aussi être celui des assignations pendant le chargement.
        moisSelectionne = moisCible
        // Bloquer immédiatement les assignations et aperçus sur les données du
        // mois précédent, avant même que la coroutine de lecture ne démarre.
        _uiState.update { it.copy(isLoading = true, messageChargement = "Chargement des données...") }
        viewModelScope.launch {
            try {
                // ⚠️ PLUS DE ROLLOVER AUTOMATIQUE ICI
                // Le rollover se fait seulement le 1er du mois dans init()

                // 1. Charger les comptes
                _uiState.update { it.copy(messageChargement = "Chargement des comptes...") }
                val resultComptes = compteRepository.recupererTousLesComptes()
                val comptes = resultComptes.getOrThrow().filter { showArchived || !it.estArchive }
                if (generation != generationChargementBudget) return@launch
                cacheComptes = comptes

                // 2. Charger les enveloppes
                _uiState.update { it.copy(messageChargement = "Chargement des enveloppes...") }
                val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                val enveloppes = resultEnveloppes.getOrThrow().filter { showArchived || !it.estArchive }
                if (generation != generationChargementBudget) return@launch
                cacheEnveloppes = enveloppes

                // 3. Charger les catégories
                _uiState.update { it.copy(messageChargement = "Chargement des catégories...") }
                val resultCategories = categorieRepository.recupererToutesLesCategories()
                val categories = resultCategories.getOrThrow()
                if (generation != generationChargementBudget) return@launch
                cacheCategories = categories

                // 4. Charger les allocations EXACTES pour le mois spécifique UNIQUEMENT
                _uiState.update { it.copy(messageChargement = "Chargement des allocations mensuelles...") }
                val premierJourDuMois = obtenirPremierJourDuMois(moisCible)

                // 🔍 DEBUG : Vérifier si on regarde un mois différent du mois actuel

                val resultAllocations = enveloppeRepository.recupererAllocationsPourMois(premierJourDuMois)
                val allocations = resultAllocations.getOrThrow()
                if (generation != generationChargementBudget) return@launch

                // 4b. Charger TOUTES les allocations passées pour les objectifs intelligents
                _uiState.update { it.copy(messageChargement = "Chargement de l'historique des allocations...") }
                val toutesAllocationsPassees = mutableListOf<AllocationMensuelle>()
                enveloppes.forEach { enveloppe ->
                    val allocationsEnveloppe = enveloppeRepository.recupererAllocationsEnveloppe(enveloppe.id).getOrThrow()
                    if (generation != generationChargementBudget) return@launch
                    toutesAllocationsPassees.addAll(allocationsEnveloppe)
                }
                


                cacheAllocations = allocations

                // 5. Créer les bandeaux "Prêt à placer"
                val bandeauxPretAPlacer = creerBandeauxPretAPlacer(comptes)

                // 6. Créer les enveloppes UI avec les allocations DU MOIS SPÉCIFIQUE
                val enveloppesUi = creerEnveloppesUi(enveloppes, allocations, comptes, toutesAllocationsPassees, moisCible)
                
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
                    // DF12 : une catégorie Dettes sans enveloppe active est masquée (jamais supprimée)
                    .filterNot { it.nomCategorie.equals("Dettes", ignoreCase = true) && it.enveloppes.isEmpty() }

                // 9. État des catégories ouvertes et favoris : l'appareil d'abord (C2), même hors ligne
                val categoriesOuvertes = preferenceRepository.recupererCategoriesOuvertes()
                val enveloppesFavorites = preferenceRepository.recupererEnveloppesFavorites()

                if (generation != generationChargementBudget) return@launch

                // 10. Mettre à jour l'état final
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bandeauxPretAPlacer = bandeauxPretAPlacer,
                        categoriesEnveloppes = categoriesEnveloppesUi,
                        categoriesOuvertes = categoriesOuvertes,
                        enveloppesFavorites = enveloppesFavorites,
                        messageChargement = null,
                        erreur = null
                    )
                }

                // 11. Échanger avec PocketBase sans bloquer l'affichage
                synchroniserPreferencesBudget()

            } catch (e: Exception) {
                if (generation != generationChargementBudget) return@launch
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

    fun setShowArchived(enabled: Boolean) {
        showArchived = enabled
        // rafraîchir pour appliquer le filtre
        chargerDonneesBudget(moisSelectionne)
    }

    /** ARC9 : réactive la même enveloppe, avec son ID, ses objectifs et tout son historique. */
    fun reactiverEnveloppe(enveloppeId: String) {
        viewModelScope.launch {
            try {
                val enveloppe = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                    .firstOrNull { it.id == enveloppeId } ?: throw Exception("Enveloppe archivée introuvable")
                enveloppeRepository.mettreAJourEnveloppe(enveloppe.copy(estArchive = false)).getOrThrow()
                chargerDonneesBudget(moisSelectionne)
                realtimeSyncService.declencherMiseAJourBudget()
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Réactivation impossible : ${e.message}") }
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
        toutesAllocationsPassees: List<AllocationMensuelle>,
        moisCible: Date
    ): List<EnveloppeUi> {
        
        // Grouper les allocations par ID d'enveloppe pour pouvoir les sommer
        val allocationsGroupees = allocations.groupBy { it.enveloppeId }

        // Créer une map des comptes par ID pour récupérer les couleurs
        val mapComptes = comptes.associateBy { it.id }

        // Associer l'ID de catégorie à son nom pour détecter la catégorie "Dettes"
        val nomCategorieParId = cacheCategories.associate { it.id to it.nom }

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
                toutesAllocationsPassees.filter { it.enveloppeId == enveloppe.id },
                moisCible
            )

            // 🎨 LOGIQUE SÉPARÉE POUR LES COULEURS :
            // 1. couleurProvenance : pour la bulle (reset à null si solde = 0)
            // 2. couleurObjectif : pour les barres (toujours garder couleur du compte si objectif atteint par dépense)
            val derniereAllocation = allocationsDeLEnveloppe.lastOrNull()
            
            // Utiliser les valeurs de l'allocation ou 0.0 par défaut
            val objectif = enveloppe.objectifMontant
            
            // LOGS POUR DÉBOGUER LA COULEUR DE PROVENANCE
            // === BUDGET VIEWMODEL - COULEUR PROVENANCE ===
            // Enveloppe: ${enveloppe.nom}
            // Solde total: $soldeTotal
            // Dépense totale: $depenseTotale
            // Objectif: $objectif
            // Type objectif: ${enveloppe.typeObjectif}
            // Dernière allocation compte source ID: ${derniereAllocation?.compteSourceId}
            // Compte source trouvé: ${derniereAllocation?.compteSourceId?.let { mapComptes[it] }}
            
            // 1. COULEUR POUR LA BULLE : reset à null si solde = 0
            val compteSource = if (soldeTotal > 0.001) {
                // Solde positif : utiliser la couleur du compte source
                // Solde positif - Utiliser couleur du compte source pour bulle
                derniereAllocation?.compteSourceId?.let { mapComptes[it] }
            } else {
                // Solde = 0 : pas de couleur pour la bulle (devient grise)
                // Solde = 0 - Pas de couleur pour bulle (devient grise)
                null
            }
            
            // 2. COULEUR POUR LES BARRES : toujours garder couleur du compte si objectif atteint par dépense
            val compteSourcePourBarres = if (derniereAllocation?.compteSourceId != null) {
                // Il y a un compte source : l'utiliser pour les barres
                // Compte source trouvé - Utiliser pour les barres
                mapComptes[derniereAllocation.compteSourceId]
            } else {
                // Pas de compte source : pas de couleur pour les barres
                // Pas de compte source - Pas de couleur pour les barres
                null
            }
            
            // Compte source pour bulle: $compteSource
            // Compte source trouvé - Utiliser pour les barres
            // Couleur bulle: ${compteSource?.couleur}
            // Couleur barres: ${compteSourcePourBarres?.couleur}

            // Pour les enveloppes des catégories "Dettes" et "Cartes de crédit", afficher le solde réel (alloué - dépenses)
            val nomCategorie = nomCategorieParId[enveloppe.categorieId]
            val estCategorieDettes = nomCategorie?.equals("Dettes", ignoreCase = true) == true
            val estCategorieCartesCredit = nomCategorie?.equals("Cartes de crédit", ignoreCase = true) == true
            val soldeAffichage = if (estCategorieDettes || estCategorieCartesCredit) {
                soldeTotal // Utiliser le solde réel au lieu de l'alloué
            } else soldeTotal

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
                toutesAllocationsPassees.filter { it.enveloppeId == enveloppe.id }
            )

            // Calculer le statut de l'objectif
            val statut = when {
                objectif > 0 && soldeTotal >= objectif -> StatutObjectif.VERT
                soldeTotal > 0 -> StatutObjectif.JAUNE
                else -> StatutObjectif.GRIS
            }
            
            // Chaîne de date objectif pour affichage
            val dateObjectifStr: String? = when {
                enveloppe.typeObjectif == com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel && enveloppe.objectifJour != null -> {
                    // Afficher simplement le jour du mois (ex: "25") si aucun calcul de date précis
                    enveloppe.objectifJour.toString()
                }
                else -> {
                    enveloppe.dateObjectif?.let { d ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
                    }
                }
            }

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = soldeAffichage,
                depense = depenseTotale,
                alloue = alloueTotal, // Total alloué ce mois
                alloueCumulatif = alloueCumulatif, // ← NOUVEAU : Total alloué depuis le début (pour barres de progression)
                objectif = objectif,
                couleurProvenance = compteSource?.couleur, // Pour la bulle (reset à null si solde = 0)
                couleurObjectif = compteSourcePourBarres?.couleur, // ← NOUVEAU : Pour les barres (toujours garder couleur du compte)
                statutObjectif = statut,
                dateObjectif = dateObjectifStr, // déjà une chaîne formatée ou un jour simple
                versementRecommande = versementRecommande,
                typeObjectif = enveloppe.typeObjectif, // Utiliser le nouveau nom de propriété
                estArchive = enveloppe.estArchive
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
     * Crée les bandeaux "Prêt à placer" à partir des comptes chèque ayant un montant "prêt à placer" (positif ou négatif).
     */
    private fun creerBandeauxPretAPlacer(comptes: List<Compte>): List<PretAPlacerUi> {
        return comptes
            .filterIsInstance<CompteCheque>()
            .filter { it.pretAPlacer != 0.0 }
            .map { compte ->
                val couleur = if (compte.pretAPlacer < 0) "#FF0000" else compte.couleur
                PretAPlacerUi(
                    compteId = compte.id,
                    nomCompte = compte.nom,
                    montant = compte.pretAPlacer,
                    couleurCompte = couleur
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
     * Méthode publique pour rafraîchir les données depuis d'autres ViewModels.
     * Utilisée quand une transaction est créée pour mettre à jour l'affichage.
     */
    fun rafraichirDonnees() {
        // 🔄 FORCER UN RECHARGEMENT COMPLET
        // Vider le cache pour s'assurer d'avoir les données les plus récentes
        cacheComptes = emptyList()
        cacheEnveloppes = emptyList()
        cacheAllocations = emptyList()
        cacheCategories = emptyList()
        
        chargerDonneesBudget(Date())
    }

    /**
     * Efface l'erreur actuelle.
     */
    fun effacerErreur() {
        _uiState.update { it.copy(erreur = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // Nettoyer explicitement les caches en mémoire pour éviter toute fuite
        cacheComptes = emptyList()
        cacheEnveloppes = emptyList()
        cacheAllocations = emptyList()
        cacheCategories = emptyList()
    }

    // C3 : signale la réussite d'UNE assignation (id de l'enveloppe) pour fermer SA fenêtre seulement
    private val _assignationReussie = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val assignationReussie: SharedFlow<String> = _assignationReussie.asSharedFlow()

    // A2 : résultat d'une répartition vers plusieurs enveloppes
    private val _resultatRepartition = MutableSharedFlow<ResultatRepartition>(extraBufferCapacity = 1)
    val resultatRepartition: SharedFlow<ResultatRepartition> = _resultatRepartition.asSharedFlow()

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
        viewModelScope.lancerUneFois(verrouAssignation) {
            try {
                _uiState.update { it.copy(isLoading = true, messageChargement = "Assignation de l'argent...") }

                if (montantCentimes <= 0L) throw IllegalArgumentException("Le montant doit être positif.")
                val montantDollars = montantCentimes / 100.0

                val mois = obtenirPremierJourDuMois(moisSelectionne)
                // A2 : relire fonds, cible et provenance sous le verrou Room.
                // Rien de financier ne peut changer entre ces contrôles et l'écriture.
                operationAtomique.executer {
                    val compteSource = compteRepository.recupererTousLesComptes().getOrThrow()
                        .firstOrNull { it.id == compteSourceId } as? CompteCheque
                        ?: throw IllegalStateException("Compte source introuvable")
                    if (compteSource.estArchive) throw IllegalStateException("Le compte source est archivé.")
                    if (!compteSource.pretAPlacer.isFinite()) throw IllegalStateException("Fonds disponibles invalides.")
                    if (Math.round(compteSource.pretAPlacer * 100.0) < montantCentimes) {
                        throw IllegalStateException(VirementErrorMessages.ClavierBudget.fondsInsuffisants(compteSource.pretAPlacer))
                    }
                    val enveloppe = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                        .firstOrNull { it.id == enveloppeId }
                        ?: throw IllegalStateException("Enveloppe cible introuvable")
                    if (enveloppe.estArchive) throw IllegalStateException("Cette enveloppe est archivée.")
                    validerProvenance(enveloppeId, compteSourceId, mois)
                    executerAssignation(compteSource, enveloppe, montantDollars, mois)
                }

                _assignationReussie.tryEmit(enveloppeId)
                rechargerApresModification()

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        erreur = messageErreurAssignation(e),
                        messageChargement = null
                    )
                }
            }
        }
    }

    /**
     * A2 : distribue le « Prêt à placer » d'UN compte vers plusieurs enveloppes en une opération.
     * Tout est validé (fonds du compte, provenance de chaque enveloppe) avant la première écriture,
     * et chaque enveloppe garde la provenance de ce compte.
     */
    fun assignerAPlusieursEnveloppes(compteSourceId: String, montantsCentimes: Map<String, Long>) {
        // A2 : sans réponse au formulaire, un clic pendant une autre assignation
        // laissait l'aperçu sur « en cours » indéfiniment.
        if (verrouAssignation.estPris) {
            _resultatRepartition.tryEmit(
                ResultatRepartition(emptyList(), "Une autre assignation est déjà en cours. Réessayez après sa fin.")
            )
            return
        }
        viewModelScope.lancerUneFois(verrouAssignation) {
            val reussies = mutableListOf<String>()
            try {
                if (montantsCentimes.isEmpty() || montantsCentimes.values.any { it <= 0L }) {
                    throw IllegalArgumentException("Chaque montant à répartir doit être positif.")
                }
                val montants = montantsCentimes

                _uiState.update { it.copy(isLoading = true, messageChargement = "Répartition de l'argent...") }

                // A2 : calcul en centimes AVANT l'écriture, puis validation intégrale
                // à l'intérieur de la transaction Room (pas de fenêtre de concurrence).
                val totalCentimes = montants.values.fold(0L) { total, montant ->
                    Math.addExact(total, montant)
                }
                val mois = obtenirPremierJourDuMois(moisSelectionne)
                operationAtomique.executer {
                    val compteSource = compteRepository.recupererTousLesComptes().getOrThrow()
                        .find { it.id == compteSourceId } as? CompteCheque
                        ?: throw IllegalStateException("Compte source introuvable")
                    if (compteSource.estArchive) throw IllegalStateException("Le compte source est archivé.")
                    if (!compteSource.pretAPlacer.isFinite()) throw IllegalStateException("Fonds disponibles invalides.")
                    if (Math.round(compteSource.pretAPlacer * 100.0) < totalCentimes) {
                        throw IllegalStateException(VirementErrorMessages.ClavierBudget.fondsInsuffisants(compteSource.pretAPlacer))
                    }
                    val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                        .associateBy { it.id }
                    montants.keys.forEach { enveloppeId ->
                        val enveloppe = enveloppes[enveloppeId]
                            ?: throw IllegalStateException("Enveloppe cible introuvable")
                        if (enveloppe.estArchive) throw IllegalStateException("L'enveloppe « ${enveloppe.nom} » est archivée.")
                        try {
                            validerProvenance(enveloppeId, compteSourceId, mois)
                        } catch (e: Exception) {
                            throw IllegalStateException("${enveloppe.nom} : ${e.message}", e)
                        }
                    }
                    var compteCourant: CompteCheque = compteSource
                    montants.forEach { (enveloppeId, centimes) ->
                        val enveloppe = enveloppes.getValue(enveloppeId)
                        compteCourant = executerAssignation(compteCourant, enveloppe, centimes / 100.0, mois)
                        reussies.add(enveloppe.nom)
                    }
                }

                _resultatRepartition.tryEmit(ResultatRepartition(reussies))
                rechargerApresModification()
            } catch (e: Exception) {
                val details = messageErreurAssignation(e)
                _resultatRepartition.tryEmit(ResultatRepartition(emptyList(), details))
                _uiState.update { it.copy(isLoading = false, messageChargement = null, erreur = details) }
                rechargerApresModification()
            }
        }
    }

    private suspend fun validerProvenance(enveloppeId: String, compteSourceId: String, mois: Date) {
        val validationResult = validationProvenanceService.validerAjoutArgentEnveloppe(
            enveloppeId = enveloppeId,
            compteSourceId = compteSourceId,
            mois = mois
        )
        if (validationResult.isFailure) {
            throw Exception(validationResult.exceptionOrNull()?.message ?: "Erreur de validation de provenance")
        }
    }

    /**
     * Retire le montant du « Prêt à placer » du compte et l'ajoute à l'allocation du mois.
     * Le solde bancaire du compte ne change pas. Retourne le compte à jour.
     */
    private suspend fun executerAssignation(
        compteSource: CompteCheque,
        enveloppe: Enveloppe,
        montantDollars: Double,
        mois: Date
    ): CompteCheque {
        // Préserver le compte en cas d'échec d'une étape ultérieure.
        // Une écriture Room unique restera nécessaire pour une vraie atomicité.
        if (!compteSource.pretAPlacer.isFinite() || !montantDollars.isFinite() || montantDollars <= 0.0) {
            throw IllegalArgumentException("Montant ou solde source invalide.")
        }
        // 1. Mettre à jour le compte source (retirer de "prêt à placer")
        val nouveauPretAPlacer = MoneyFormatter.roundAmount(compteSource.pretAPlacer - montantDollars)
        val compteModifie = compteSource.copy(
            pretAPlacerRaw = nouveauPretAPlacer,
            // Sécuriser la collection (évite crash si null dans des données anciennes)
            collection = "comptes_cheques"
        )

        val resultCompte = compteRepository.mettreAJourCompte(compteModifie)
        if (resultCompte.isFailure) {
            throw Exception("Erreur lors de la mise à jour du compte: ${resultCompte.exceptionOrNull()?.message}")
        }

        // 2. Garder la provenance historique : lorsqu'une allocation A est
        // épuisée et qu'on finance désormais depuis B, ne PAS réattribuer
        // les dépenses/alloués historiques de A à B. Ouvrir une ligne distincte.
        val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppe.id, mois)
        if (!allocationExistante.solde.isFinite() || !allocationExistante.alloue.isFinite() ||
            !allocationExistante.depense.isFinite()
        ) throw IllegalStateException("Allocation mensuelle invalide.")
        val contientHistorique = allocationExistante.compteSourceId != null ||
            kotlin.math.round(allocationExistante.alloue * 100.0).toLong() != 0L ||
            kotlin.math.round(allocationExistante.depense * 100.0).toLong() != 0L
        val nouvelleProvenance = contientHistorique &&
            allocationExistante.compteSourceId != compteSource.id &&
            kotlin.math.round(allocationExistante.solde * 100.0).toLong() <= 0L
        val allocationDeBase = if (nouvelleProvenance) {
            allocationMensuelleRepository.creerNouvelleAllocation(
                allocationExistante.copy(
                    id = "",
                    solde = 0.0,
                    alloue = 0.0,
                    depense = 0.0,
                    compteSourceId = compteSource.id,
                    collectionCompteSource = compteSource.collection
                )
            )
        } else allocationExistante
        val allocationFinale = allocationDeBase.copy(
            solde = MoneyFormatter.roundAmount(allocationDeBase.solde + montantDollars),
            alloue = MoneyFormatter.roundAmount(allocationDeBase.alloue + montantDollars),
            compteSourceId = if (allocationDeBase.solde <= 0.0) compteSource.id else allocationDeBase.compteSourceId,
            collectionCompteSource = if (allocationDeBase.solde <= 0.0) compteSource.collection else allocationDeBase.collectionCompteSource
        )
        if (!allocationFinale.solde.isFinite() || !allocationFinale.alloue.isFinite()) {
            throw IllegalStateException("Débordement des montants de l'allocation.")
        }
        allocationMensuelleRepository.mettreAJourAllocation(allocationFinale)

        // L'échec de journalisation doit aussi annuler la transaction Room.
        if (nouvelleProvenance) {
            historiqueAllocationService.enregistrerCreationAllocation(
                allocation = allocationFinale,
                compte = compteSource,
                enveloppe = enveloppe,
                montant = montantDollars,
                soldeAvant = compteSource.solde,
                soldeApres = compteSource.solde,
                pretAPlacerAvant = compteSource.pretAPlacer,
                pretAPlacerApres = nouveauPretAPlacer
            )
        } else {
            historiqueAllocationService.enregistrerModificationAllocation(
                allocationAvant = allocationDeBase,
                allocationApres = allocationFinale,
                compte = compteSource,
                enveloppe = enveloppe,
                montantModification = montantDollars,
                soldeAvant = compteSource.solde,
                soldeApres = compteSource.solde,
                pretAPlacerAvant = compteSource.pretAPlacer,
                pretAPlacerApres = nouveauPretAPlacer
            )
        }

        return compteModifie
    }

    private fun rechargerApresModification() {
        // 🔄 FORCER LA VISIBILITÉ DES SYNCJOB des allocations
        realtimeSyncService.declencherMiseAJourBudget()

        // Vider le cache pour s'assurer d'avoir les données les plus récentes
        cacheComptes = emptyList()
        cacheEnveloppes = emptyList()
        cacheAllocations = emptyList()
        cacheCategories = emptyList()

        chargerDonneesBudget(moisSelectionne)
    }

    private fun messageErreurAssignation(e: Exception): String = when {
        // Si c'est une erreur de provenance, utiliser le message tel quel
        VirementErrorMessages.estErreurProvenance(e.message ?: "") -> e.message ?: "Erreur inconnue"
        else -> "Erreur lors de l'assignation: ${e.message}"
    }

    /**
     * Ouvre ou ferme une catégorie d'enveloppes.
     * L'état est gardé sur l'appareil (fonctionne hors ligne) puis envoyé à PocketBase (C2).
     * @param nomCategorie Nom de la catégorie à basculer
     */
    fun toggleCategorie(nomCategorie: String) {
        generationPreferencesBudget++
        _uiState.update { currentState ->
            val categoriesOuvertes = currentState.categoriesOuvertes.toMutableMap()
            val estOuverte = categoriesOuvertes[nomCategorie] ?: true // Par défaut ouvert
            categoriesOuvertes[nomCategorie] = !estOuverte
            currentState.copy(categoriesOuvertes = categoriesOuvertes)
        }

        viewModelScope.launch {
            preferenceRepository.sauvegarderCategoriesOuvertes(_uiState.value.categoriesOuvertes)
            synchroniserPreferencesBudget()
        }
    }

    /** A4 : ajoute ou retire une enveloppe des favoris. */
    fun basculerFavori(enveloppeId: String) {
        generationPreferencesBudget++
        _uiState.update { currentState ->
            val favorites = currentState.enveloppesFavorites.toMutableSet()
            if (!favorites.add(enveloppeId)) favorites.remove(enveloppeId)
            currentState.copy(enveloppesFavorites = favorites)
        }

        viewModelScope.launch {
            preferenceRepository.sauvegarderEnveloppesFavorites(_uiState.value.enveloppesFavorites)
            synchroniserPreferencesBudget()
        }
    }

    /**
     * Échange les préférences de la page Budget avec PocketBase.
     * Un choix local pas encore envoyé passe avant la version du serveur : il n'est jamais perdu.
     * Hors ligne, rien ne change et l'envoi sera refait au prochain chargement.
     */
    private fun synchroniserPreferencesBudget() {
        val generationAuDebut = generationPreferencesBudget
        viewModelScope.launch(Dispatchers.IO) {
            mutexPreferencesBudget.withLock {
            try {
                if (preferenceRepository.preferencesBudgetEnAttenteEnvoi()) {
                    val categoriesEnvoyees = preferenceRepository.recupererCategoriesOuvertes()
                    val favoritesEnvoyes = preferenceRepository.recupererEnveloppesFavorites()
                    realtimeSyncService.mettreAJourPreferencesUtilisateur(
                        mapOf(
                            "categories_ouvertes" to categoriesEnvoyees,
                            "enveloppes_favorites" to favoritesEnvoyes.toList()
                        )
                    )
                    // A4/C2 : un clic effectué pendant la requête doit rester en attente.
                    if (preferenceRepository.recupererCategoriesOuvertes() == categoriesEnvoyees &&
                        preferenceRepository.recupererEnveloppesFavorites() == favoritesEnvoyes
                    ) preferenceRepository.marquerPreferencesBudgetEnvoyees()
                    return@launch
                }

                val preferences = realtimeSyncService.recupererPreferencesUtilisateur()
                val categoriesServeur = (preferences["categories_ouvertes"] as? Map<*, *>)
                    ?.entries?.associate { (cle, valeur) -> cle.toString() to (valeur as? Boolean ?: true) }
                val favoritesServeur = (preferences["enveloppes_favorites"] as? List<*>)
                    ?.map { it.toString() }?.toSet()

                // A4 : une saisie optimiste peut précéder sa sauvegarde en local.
                // Interdire qu'une vieille réponse serveur écrase le dernier clic.
                if (generationPreferencesBudget != generationAuDebut) return@launch
                if (preferenceRepository.preferencesBudgetEnAttenteEnvoi()) return@launch
                if (categoriesServeur == null && favoritesServeur == null) return@launch

                preferenceRepository.appliquerPreferencesBudgetServeur(categoriesServeur, favoritesServeur)
                _uiState.update {
                    it.copy(
                        categoriesOuvertes = categoriesServeur ?: it.categoriesOuvertes,
                        enveloppesFavorites = favoritesServeur ?: it.enveloppesFavorites
                    )
                }
            } catch (_: Exception) {
                // Hors ligne ou serveur indisponible : l'état local reste affiché
            }
            }
        }
    }
}
