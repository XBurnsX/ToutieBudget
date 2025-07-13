/**
 * Chemin: app/src/main/java/com/xburnsx/toutiebudget/di/AppModule.kt
 * Dépendances: Tous les repositories, services, use cases et ViewModels
 */

 package com.xburnsx.toutiebudget.di

 import com.xburnsx.toutiebudget.data.repositories.*
 import com.xburnsx.toutiebudget.data.repositories.impl.*
 import com.xburnsx.toutiebudget.domain.services.*
 import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
 import com.xburnsx.toutiebudget.domain.services.Impl.RolloverServiceImpl
 import com.xburnsx.toutiebudget.domain.usecases.*
 import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionViewModel
 import com.xburnsx.toutiebudget.ui.budget.BudgetViewModel
 import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesViewModel
 import com.xburnsx.toutiebudget.ui.comptes.ComptesViewModel
 import com.xburnsx.toutiebudget.ui.login.LoginViewModel
 import com.xburnsx.toutiebudget.ui.virement.VirerArgentViewModel
 
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
 
     // ===== SERVICES =====
     private val argentService: ArgentService by lazy { ArgentServiceImpl(compteRepository, enveloppeRepository, transactionRepository, allocationMensuelleRepository) }
     private val rolloverService: RolloverService by lazy { RolloverServiceImpl(enveloppeRepository) }
 
     // ===== USE CASES EXISTANTS =====
     private val enregistrerDepenseUseCase: EnregistrerDepenseUseCase by lazy { EnregistrerDepenseUseCaseImpl(argentService) }
     private val enregistrerRevenuUseCase: EnregistrerRevenuUseCase by lazy { EnregistrerRevenuUseCaseImpl(argentService) }
     private val enregistrerPretAccordeUseCase: EnregistrerPretAccordeUseCase by lazy { EnregistrerPretAccordeUseCaseImpl(argentService) }
     private val enregistrerDetteContracteeUseCase: EnregistrerDetteContracteeUseCase by lazy { EnregistrerDetteContracteeUseCaseImpl(argentService) }
     private val enregistrerPaiementDetteUseCase: EnregistrerPaiementDetteUseCase by lazy { EnregistrerPaiementDetteUseCaseImpl(argentService) }
     private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase by lazy { VerifierEtExecuterRolloverUseCase(rolloverService, preferenceRepository) }
 
     // ===== NOUVEAU USE CASE POUR LES TRANSACTIONS =====
     private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase by lazy { 
         EnregistrerTransactionUseCase(transactionRepository, compteRepository, enveloppeRepository) 
     }
 
     // ===== VIEWMODELS =====
     // CORRECTION : Ordre des paramètres corrigés selon les constructeurs
     private val budgetViewModel: BudgetViewModel by lazy { 
         BudgetViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             verifierEtExecuterRolloverUseCase = verifierEtExecuterRolloverUseCase
         ) 
     }
     
     private val comptesViewModel: ComptesViewModel by lazy { 
         ComptesViewModel(compteRepository = compteRepository) 
     }
     
     private val ajoutTransactionViewModel: AjoutTransactionViewModel by lazy { 
         AjoutTransactionViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             enregistrerTransactionUseCase = enregistrerTransactionUseCase
         ) 
     }
     
     private val categoriesEnveloppesViewModel: CategoriesEnveloppesViewModel by lazy { 
         CategoriesEnveloppesViewModel(
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository
         ) 
     }
     
     private val virerArgentViewModel: VirerArgentViewModel by lazy { 
         VirerArgentViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             argentService = argentService
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
 
     // Services
     fun provideArgentService(): ArgentService = argentService
     fun provideRolloverService(): RolloverService = rolloverService
 
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
     fun provideBudgetViewModel(): BudgetViewModel = budgetViewModel
     fun provideComptesViewModel(): ComptesViewModel = comptesViewModel
     fun provideAjoutTransactionViewModel(): AjoutTransactionViewModel = ajoutTransactionViewModel
     fun provideCategoriesEnveloppesViewModel(): CategoriesEnveloppesViewModel = categoriesEnveloppesViewModel
     fun provideVirerArgentViewModel(): VirerArgentViewModel = virerArgentViewModel
     
     /**
      * Nettoie les singletons (pas nécessaire avec lazy mais gardé pour compatibilité).
      */
     fun nettoyerSingletons() {
         // Pas besoin de nettoyer car on utilise lazy
     }
 }