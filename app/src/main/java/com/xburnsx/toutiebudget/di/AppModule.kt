// chemin/simule: /di/AppModule.kt
package com.xburnsx.toutiebudget.di

import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.data.repositories.impl.*
import com.xburnsx.toutiebudget.domain.services.*
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

    // Services
    private val argentService: ArgentService by lazy { ArgentServiceImpl(compteRepository, enveloppeRepository, transactionRepository) }
    private val rolloverService: RolloverService by lazy { RolloverServiceImpl(enveloppeRepository) }

    // Use Cases
    private val enregistrerDepenseUseCase: EnregistrerDepenseUseCase by lazy { EnregistrerDepenseUseCaseImpl(argentService) }
    private val enregistrerRevenuUseCase: EnregistrerRevenuUseCase by lazy { EnregistrerRevenuUseCaseImpl(argentService) }
    private val enregistrerPretAccordeUseCase: EnregistrerPretAccordeUseCase by lazy { EnregistrerPretAccordeUseCaseImpl(argentService) }
    private val enregistrerDetteContracteeUseCase: EnregistrerDetteContracteeUseCase by lazy { EnregistrerDetteContracteeUseCaseImpl(argentService) }
    private val enregistrerPaiementDetteUseCase: EnregistrerPaiementDetteUseCase by lazy { EnregistrerPaiementDetteUseCaseImpl(argentService) }
    private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase by lazy { VerifierEtExecuterRolloverUseCase(rolloverService, preferenceRepository) }

    // ViewModel Factories
    fun provideLoginViewModel(): LoginViewModel = LoginViewModel()
    fun provideBudgetViewModel(): BudgetViewModel = BudgetViewModel(compteRepository, enveloppeRepository, categorieRepository, verifierEtExecuterRolloverUseCase)
    fun provideComptesViewModel(): ComptesViewModel = ComptesViewModel(compteRepository)
    fun provideAjoutTransactionViewModel(): AjoutTransactionViewModel = AjoutTransactionViewModel(compteRepository, enveloppeRepository, categorieRepository, enregistrerDepenseUseCase, enregistrerRevenuUseCase, enregistrerPretAccordeUseCase, enregistrerDetteContracteeUseCase, enregistrerPaiementDetteUseCase)
    fun provideCategoriesEnveloppesViewModel(): CategoriesEnveloppesViewModel = CategoriesEnveloppesViewModel(enveloppeRepository, categorieRepository)
    fun provideVirerArgentViewModel(): VirerArgentViewModel = VirerArgentViewModel(compteRepository, enveloppeRepository, categorieRepository, argentService)
}
