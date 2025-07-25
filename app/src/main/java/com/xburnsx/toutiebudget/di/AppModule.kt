/**
 * Chemin: app/src/main/java/com/xburnsx/toutiebudget/di/AppModule.kt
 * Dépendances: Tous les repositories, services, use cases et ViewModels
 */

 package com.xburnsx.toutiebudget.di

 import com.xburnsx.toutiebudget.data.repositories.*
 import com.xburnsx.toutiebudget.data.repositories.impl.*
 import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
 import com.xburnsx.toutiebudget.domain.services.*
 import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
 import com.xburnsx.toutiebudget.domain.services.Impl.RolloverServiceImpl
 import com.xburnsx.toutiebudget.domain.usecases.*
 import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionViewModel
 import com.xburnsx.toutiebudget.ui.budget.BudgetViewModel
 import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesViewModel
 import com.xburnsx.toutiebudget.ui.comptes.ComptesViewModel
 import com.xburnsx.toutiebudget.ui.historique.HistoriqueCompteViewModel
 import com.xburnsx.toutiebudget.ui.login.LoginViewModel
 import com.xburnsx.toutiebudget.ui.startup.StartupViewModel
 import com.xburnsx.toutiebudget.ui.virement.VirerArgentViewModel
 import androidx.lifecycle.SavedStateHandle

 /**
  * Module d'injection de dépendances pour l'application Toutie Budget.
  * Gère l'instanciation de tous les repositories, services, use cases et ViewModels.
  */
 object AppModule {
     
     // ===== REPOSITORIES =====
     private val compteRepository: CompteRepository by lazy { CompteRepositoryImpl() }
     private val enveloppeRepository: EnveloppeRepository by lazy { EnveloppeRepositoryImpl() }
     private val categorieRepository: CategorieRepository by lazy { CategorieRepositoryImpl() }
     private val transactionRepository: TransactionRepository by lazy { TransactionRepositoryImpl() }
     private val preferenceRepository: PreferenceRepository by lazy { PreferenceRepositoryImpl() }
     private val allocationMensuelleRepository: AllocationMensuelleRepository by lazy { AllocationMensuelleRepositoryImpl() }
     private val tiersRepository: TiersRepository by lazy { TiersRepositoryImpl() }

     // ===== SERVICES =====
     private val virementUseCase: VirementUseCase by lazy {
         VirementUseCase(compteRepository, allocationMensuelleRepository, transactionRepository, enveloppeRepository, validationProvenanceService)
     }
     private val argentService: ArgentService by lazy { ArgentServiceImpl(compteRepository, enveloppeRepository, transactionRepository, allocationMensuelleRepository, virementUseCase) }

     // Service de synchronisation temps réel
     private val realtimeSyncService: RealtimeSyncService by lazy { RealtimeSyncService() }
     private val rolloverService: RolloverService by lazy { RolloverServiceImpl(enveloppeRepository) }

     // Service de validation de provenance
     private val validationProvenanceService: ValidationProvenanceService by lazy {
         ValidationProvenanceService(allocationMensuelleRepository, enveloppeRepository, compteRepository)
     }

     // Service de vérification du serveur
     private val serverStatusService: com.xburnsx.toutiebudget.data.services.ServerStatusService by lazy { com.xburnsx.toutiebudget.data.services.ServerStatusService() }

     // ===== USE CASES EXISTANTS =====
     private val enregistrerDepenseUseCase: EnregistrerDepenseUseCase by lazy { EnregistrerDepenseUseCaseImpl(argentService) }
     private val enregistrerRevenuUseCase: EnregistrerRevenuUseCase by lazy { EnregistrerRevenuUseCaseImpl(argentService) }
     private val enregistrerPretAccordeUseCase: EnregistrerPretAccordeUseCase by lazy { EnregistrerPretAccordeUseCaseImpl(argentService) }
     private val enregistrerDetteContracteeUseCase: EnregistrerDetteContracteeUseCase by lazy { EnregistrerDetteContracteeUseCaseImpl(argentService) }
     private val enregistrerPaiementDetteUseCase: EnregistrerPaiementDetteUseCase by lazy { EnregistrerPaiementDetteUseCaseImpl(argentService) }
     private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase by lazy { VerifierEtExecuterRolloverUseCase(rolloverService, preferenceRepository) }
 
     // ===== NOUVEAU USE CASE POUR LES TRANSACTIONS =====
     private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase by lazy { 
         EnregistrerTransactionUseCase(transactionRepository, compteRepository, enveloppeRepository, allocationMensuelleRepository)
     }
 
     // ===== VIEWMODELS =====
     // CORRECTION : Ordre des paramètres corrigés selon les constructeurs
     private val budgetViewModel: BudgetViewModel by lazy {
         BudgetViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             verifierEtExecuterRolloverUseCase = verifierEtExecuterRolloverUseCase,
             realtimeSyncService = realtimeSyncService,
             validationProvenanceService = validationProvenanceService
         )
     }
     
     private val comptesViewModel: ComptesViewModel by lazy {
         ComptesViewModel(
             compteRepository = compteRepository,
             realtimeSyncService = realtimeSyncService
         )
     }
     
     private val ajoutTransactionViewModel: AjoutTransactionViewModel by lazy { 
         AjoutTransactionViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             tiersRepository = tiersRepository,
             enregistrerTransactionUseCase = enregistrerTransactionUseCase,
             realtimeSyncService = realtimeSyncService
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
             compteRepository = provideCompteRepository(),
             enveloppeRepository = provideEnveloppeRepository(),
             categorieRepository = provideCategorieRepository(),
             argentService = provideArgentService(),
             realtimeSyncService = provideRealtimeSyncService(),
             validationProvenanceService = provideValidationProvenanceService()
         )
     }
 
     // ===== FONCTIONS PUBLIQUES =====
     
     // Repositories
     fun provideCompteRepository(): CompteRepository = compteRepository
     fun provideEnveloppeRepository(): EnveloppeRepository = enveloppeRepository
     fun provideCategorieRepository(): CategorieRepository = categorieRepository
     fun provideTransactionRepository(): TransactionRepository = transactionRepository
     fun providePreferenceRepository(): PreferenceRepository = preferenceRepository
     fun provideAllocationMensuelleRepository(): AllocationMensuelleRepository = allocationMensuelleRepository
     fun provideTiersRepository(): TiersRepository = tiersRepository

     // Services
     fun provideArgentService(): ArgentService = argentService
     fun provideRolloverService(): RolloverService = rolloverService
     fun provideRealtimeSyncService(): RealtimeSyncService = realtimeSyncService
     fun provideValidationProvenanceService(): ValidationProvenanceService = validationProvenanceService
     fun provideServerStatusService(): com.xburnsx.toutiebudget.data.services.ServerStatusService = serverStatusService

     // Use Cases
     fun provideEnregistrerDepenseUseCase(): EnregistrerDepenseUseCase = enregistrerDepenseUseCase
     fun provideEnregistrerRevenuUseCase(): EnregistrerRevenuUseCase = enregistrerRevenuUseCase
     fun provideEnregistrerPretAccordeUseCase(): EnregistrerPretAccordeUseCase = enregistrerPretAccordeUseCase
     fun provideEnregistrerDetteContracteeUseCase(): EnregistrerDetteContracteeUseCase = enregistrerDetteContracteeUseCase
     fun provideEnregistrerPaiementDetteUseCase(): EnregistrerPaiementDetteUseCase = enregistrerPaiementDetteUseCase
     fun provideVerifierEtExecuterRolloverUseCase(): VerifierEtExecuterRolloverUseCase = verifierEtExecuterRolloverUseCase
     fun provideEnregistrerTransactionUseCase(): EnregistrerTransactionUseCase = enregistrerTransactionUseCase
 
     // ViewModels
     fun provideLoginViewModel(): LoginViewModel = LoginViewModel()
     fun provideStartupViewModel(): StartupViewModel = StartupViewModel()
     fun provideBudgetViewModel(): BudgetViewModel = budgetViewModel
     fun provideComptesViewModel(): ComptesViewModel = comptesViewModel
     fun provideAjoutTransactionViewModel(): AjoutTransactionViewModel = ajoutTransactionViewModel
     fun provideCategoriesEnveloppesViewModel(): CategoriesEnveloppesViewModel = categoriesEnveloppesViewModel
     fun provideVirerArgentViewModel(): VirerArgentViewModel = virerArgentViewModel
     fun provideHistoriqueCompteViewModel(savedStateHandle: SavedStateHandle): HistoriqueCompteViewModel {
         return HistoriqueCompteViewModel(
             transactionRepository = transactionRepository,
             enveloppeRepository = enveloppeRepository,
             tiersRepository = tiersRepository,
             savedStateHandle = savedStateHandle
         )
     }

     // ServerStatusViewModel
     fun provideServerStatusViewModel(context: android.content.Context): com.xburnsx.toutiebudget.ui.server.ServerStatusViewModel {
         return com.xburnsx.toutiebudget.ui.server.ServerStatusViewModel(
             serverStatusService = serverStatusService,
             context = context
         )
     }

     /**
      * Nettoie les singletons (pas nécessaire avec lazy mais gardé pour compatibilité).
      */
     fun nettoyerSingletons() {
         // Pas besoin de nettoyer car on utilise lazy
     }
 }