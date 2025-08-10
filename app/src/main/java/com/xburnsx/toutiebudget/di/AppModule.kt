/**
 * Chemin: app/src/main/java/com/xburnsx/toutiebudget/di/AppModule.kt
 * Dépendances: Tous les repositories, services, use cases et ViewModels
 */

 package com.xburnsx.toutiebudget.di

 import com.xburnsx.toutiebudget.data.repositories.*
 import com.xburnsx.toutiebudget.data.repositories.impl.*
 import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
 import com.xburnsx.toutiebudget.data.utils.ObjectifCalculator
 import com.xburnsx.toutiebudget.domain.services.*
 import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
 import com.xburnsx.toutiebudget.domain.services.Impl.RolloverServiceImpl
 import com.xburnsx.toutiebudget.domain.usecases.*
 import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
 import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionViewModel
import com.xburnsx.toutiebudget.ui.ajout_transaction.ModifierTransactionViewModel
 import com.xburnsx.toutiebudget.ui.budget.BudgetViewModel
 import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesViewModel
 import com.xburnsx.toutiebudget.ui.comptes.ComptesViewModel
 import com.xburnsx.toutiebudget.ui.cartes_credit.CartesCreditViewModel
import com.xburnsx.toutiebudget.ui.dette.DetteViewModel
import com.xburnsx.toutiebudget.ui.historique.HistoriqueCompteViewModel
import com.xburnsx.toutiebudget.ui.login.LoginViewModel
import com.xburnsx.toutiebudget.ui.startup.StartupViewModel
import com.xburnsx.toutiebudget.ui.virement.VirerArgentViewModel
import com.xburnsx.toutiebudget.ui.statistiques.StatistiquesViewModel
 import androidx.lifecycle.SavedStateHandle
// import android.content.Context (supprimé)

 /**
  * Module d'injection de dépendances pour l'application Toutie Budget.
  * Gère l'instanciation de tous les repositories, services, use cases et ViewModels.
  * SÉCURITÉ FINANCIÈRE : Validation complexe pour éviter les faux soldes
  */
 object AppModule {
     
    // ===== SERVICES DE CACHE (supprimés) =====
     
     // ===== REPOSITORIES =====
     private val compteRepository: CompteRepository by lazy { CompteRepositoryImpl() }
     private val enveloppeRepository: EnveloppeRepository by lazy { EnveloppeRepositoryImpl() }
     private val categorieRepository: CategorieRepository by lazy { CategorieRepositoryImpl() }
     private val transactionRepository: TransactionRepository by lazy { TransactionRepositoryImpl() }
     private val preferenceRepository: PreferenceRepository by lazy { PreferenceRepositoryImpl() }
     private val allocationMensuelleRepository: AllocationMensuelleRepository by lazy { AllocationMensuelleRepositoryImpl() }
     private val tiersRepository: TiersRepository by lazy { TiersRepositoryImpl() }

     // ===== SERVICES =====
     private val validationProvenanceService: ValidationProvenanceService by lazy {
         ValidationProvenanceService(enveloppeRepository, compteRepository)
     }
     private val virementUseCase: VirementUseCase by lazy {
         VirementUseCase(compteRepository, allocationMensuelleRepository, transactionRepository, enveloppeRepository, validationProvenanceService)
     }
     private val argentService: ArgentService by lazy { ArgentServiceImpl(compteRepository, transactionRepository, allocationMensuelleRepository, virementUseCase) }
     private val realtimeSyncService: RealtimeSyncService by lazy { RealtimeSyncService() }
     private val rolloverService: RolloverService by lazy { RolloverServiceImpl(enveloppeRepository, allocationMensuelleRepository) }
     private val serverStatusService: com.xburnsx.toutiebudget.data.services.ServerStatusService by lazy { com.xburnsx.toutiebudget.data.services.ServerStatusService() }
     
    // ===== SERVICES D'OPTIMISATION (non utilisés) =====

     // ===== CALCULATEUR D'OBJECTIFS =====
     private val objectifCalculator: ObjectifCalculator by lazy { ObjectifCalculator() }

     private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase by lazy { VerifierEtExecuterRolloverUseCase(rolloverService, preferenceRepository) }
     private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase by lazy {
         EnregistrerTransactionUseCase(transactionRepository, compteRepository, enveloppeRepository, allocationMensuelleRepository)
     }
     private val supprimerTransactionUseCase: SupprimerTransactionUseCase by lazy {
         SupprimerTransactionUseCase(transactionRepository, compteRepository, enveloppeRepository, allocationMensuelleRepository)
     }
     private val modifierTransactionUseCase: ModifierTransactionUseCase by lazy {
         ModifierTransactionUseCase()
     }
 
     // ===== VIEWMODELS =====
     private val budgetViewModel: BudgetViewModel by lazy {
                 BudgetViewModel(
            compteRepository = compteRepository,
            enveloppeRepository = enveloppeRepository,
            categorieRepository = categorieRepository,
            allocationMensuelleRepository = allocationMensuelleRepository, // ← AJOUT
            verifierEtExecuterRolloverUseCase = verifierEtExecuterRolloverUseCase,
            realtimeSyncService = realtimeSyncService,
            validationProvenanceService = validationProvenanceService,
            objectifCalculator = objectifCalculator
        )
     }
     
     private val comptesViewModel: ComptesViewModel by lazy {
         ComptesViewModel(
             compteRepository = compteRepository,
             realtimeSyncService = realtimeSyncService
         )
     }
     
     private val cartesCreditViewModel: CartesCreditViewModel by lazy {
         CartesCreditViewModel()
     }

     private val ajoutTransactionViewModel: AjoutTransactionViewModel by lazy {
         AjoutTransactionViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             tiersRepository = tiersRepository,
             allocationMensuelleRepository = allocationMensuelleRepository,
             argentService = argentService,
             enregistrerTransactionUseCase = enregistrerTransactionUseCase,
             realtimeSyncService = realtimeSyncService
         )
     }
     
     private val modifierTransactionViewModel: ModifierTransactionViewModel by lazy { 
         ModifierTransactionViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             tiersRepository = tiersRepository,
             transactionRepository = transactionRepository,
             allocationMensuelleRepository = allocationMensuelleRepository,
             enregistrerTransactionUseCase = enregistrerTransactionUseCase,
             supprimerTransactionUseCase = supprimerTransactionUseCase
         )
     }
     
     private val categoriesEnveloppesViewModel: CategoriesEnveloppesViewModel by lazy {
         CategoriesEnveloppesViewModel(
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             realtimeSyncService = realtimeSyncService
         )
     }

     private val virerArgentViewModel: VirerArgentViewModel by lazy {
         VirerArgentViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             allocationMensuelleRepository = allocationMensuelleRepository,
             categorieRepository = categorieRepository,
             argentService = argentService,
             realtimeSyncService = realtimeSyncService,
             validationProvenanceService = validationProvenanceService
         )
     }
     
     private val detteViewModel: DetteViewModel by lazy {
         DetteViewModel(compteRepository = compteRepository)
     }

      private val statistiquesViewModel: StatistiquesViewModel by lazy {
          StatistiquesViewModel(
              transactionRepository = transactionRepository,
              enveloppeRepository = enveloppeRepository,
              tiersRepository = tiersRepository,
              realtimeSyncService = realtimeSyncService
          )
      }
 
     // ===== FONCTIONS PUBLIQUES =====
     
    // Initialisation de cache supprimée

     fun provideRealtimeSyncService(): RealtimeSyncService = realtimeSyncService
     fun provideLoginViewModel(): LoginViewModel = LoginViewModel()
     fun provideStartupViewModel(): StartupViewModel = StartupViewModel()
     fun provideBudgetViewModel(): BudgetViewModel = budgetViewModel
     fun provideComptesViewModel(): ComptesViewModel = comptesViewModel
     fun provideCartesCreditViewModel(): CartesCreditViewModel = cartesCreditViewModel
     fun provideAjoutTransactionViewModel(): AjoutTransactionViewModel = ajoutTransactionViewModel
     fun provideModifierTransactionViewModel(): ModifierTransactionViewModel = modifierTransactionViewModel
     fun provideCategoriesEnveloppesViewModel(): CategoriesEnveloppesViewModel = categoriesEnveloppesViewModel
     fun provideVirerArgentViewModel(): VirerArgentViewModel = virerArgentViewModel
     fun provideDetteViewModel(): DetteViewModel = detteViewModel
      fun provideStatistiquesViewModel(): StatistiquesViewModel = statistiquesViewModel
     fun provideHistoriqueCompteViewModel(savedStateHandle: SavedStateHandle): HistoriqueCompteViewModel {
         return HistoriqueCompteViewModel(
             transactionRepository = transactionRepository,
             enveloppeRepository = enveloppeRepository,
             supprimerTransactionUseCase = supprimerTransactionUseCase,
             savedStateHandle = savedStateHandle
         )
     }
     fun provideServerStatusViewModel(context: android.content.Context): com.xburnsx.toutiebudget.ui.server.ServerStatusViewModel {
         return com.xburnsx.toutiebudget.ui.server.ServerStatusViewModel(
             serverStatusService = serverStatusService,
             context = context
         )
     }
 }