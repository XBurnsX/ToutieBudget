// chemin/simule: /di/AppModule.kt
package com.xburnsx.toutiebudget.di

import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.data.repositories.impl.*
import com.xburnsx.toutiebudget.domain.services.*
import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
import com.xburnsx.toutiebudget.domain.services.impl.*
import com.xburnsx.toutiebudget.domain.usecases.*
import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionViewModel
import com.xburnsx.toutiebudget.ui.budget.BudgetViewModel
import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesViewModel
import com.xburnsx.toutiebudget.ui.comptes.ComptesViewModel
import com.xburnsx.toutiebudget.ui.login.LoginViewModel
import com.xburnsx.toutiebudget.ui.virement.VirerArgentViewModel

object AppModule {
    // Repositories
    private val compteRepository: CompteRepository by lazy { CompteRepositoryImpl() }
    private val enveloppeRepository: EnveloppeRepository by lazy { EnveloppeRepositoryImpl() }
    private val categorieRepository: CategorieRepository by lazy { CategorieRepositoryImpl() }
    private val transactionRepository: TransactionRepository by lazy { TransactionRepositoryImpl() }
    private val preferenceRepository: PreferenceRepository by lazy { PreferenceRepositoryImpl() }
    private val allocationMensuelleRepository: AllocationMensuelleRepository by lazy { AllocationMensuelleRepositoryImpl() }

    // Services
    private val argentService: ArgentService by lazy { ArgentServiceImpl(compteRepository, transactionRepository, allocationMensuelleRepository) }
    private val rolloverService: RolloverService by lazy { RolloverServiceImpl(enveloppeRepository) }

    // Use Cases
    private val enregistrerDepenseUseCase: EnregistrerDepenseUseCase by lazy { EnregistrerDepenseUseCaseImpl(argentService) }
    private val enregistrerRevenuUseCase: EnregistrerRevenuUseCase by lazy { EnregistrerRevenuUseCaseImpl(argentService) }
    private val enregistrerPretAccordeUseCase: EnregistrerPretAccordeUseCase by lazy { EnregistrerPretAccordeUseCaseImpl(argentService) }
    private val enregistrerDetteContracteeUseCase: EnregistrerDetteContracteeUseCase by lazy { EnregistrerDetteContracteeUseCaseImpl(argentService) }
    private val enregistrerPaiementDetteUseCase: EnregistrerPaiementDetteUseCase by lazy { EnregistrerPaiementDetteUseCaseImpl(argentService) }
    private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase by lazy { VerifierEtExecuterRolloverUseCase(rolloverService, preferenceRepository) }

    // Singletons pour éviter la recréation et donc le rechargement visible lorsque l'utilisateur change d'onglet.
    private val budgetViewModel: BudgetViewModel by lazy {
        BudgetViewModel(compteRepository, enveloppeRepository, categorieRepository, verifierEtExecuterRolloverUseCase)
    }
    private val comptesViewModel: ComptesViewModel by lazy { ComptesViewModel(compteRepository) }
    private val categoriesEnveloppesViewModel: CategoriesEnveloppesViewModel by lazy { CategoriesEnveloppesViewModel(enveloppeRepository, categorieRepository) }
    private val ajoutTransactionViewModel: AjoutTransactionViewModel by lazy {
        AjoutTransactionViewModel(
            compteRepository,
            enveloppeRepository,
            categorieRepository,
            enregistrerDepenseUseCase,
            enregistrerRevenuUseCase,
            enregistrerPretAccordeUseCase,
            enregistrerDetteContracteeUseCase,
            enregistrerPaiementDetteUseCase
        )
    }
    private val virerArgentViewModel: VirerArgentViewModel by lazy { VirerArgentViewModel(compteRepository, enveloppeRepository, categorieRepository, argentService) }

    // ViewModel Factories
    fun provideLoginViewModel(): LoginViewModel = LoginViewModel()
    fun provideBudgetViewModel(): BudgetViewModel = budgetViewModel
    fun provideComptesViewModel(): ComptesViewModel = comptesViewModel
    fun provideAjoutTransactionViewModel(): AjoutTransactionViewModel = ajoutTransactionViewModel
    fun provideCategoriesEnveloppesViewModel(): CategoriesEnveloppesViewModel = categoriesEnveloppesViewModel
    fun provideVirerArgentViewModel(): VirerArgentViewModel = virerArgentViewModel
}
