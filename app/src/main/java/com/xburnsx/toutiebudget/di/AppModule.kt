/**
 * Chemin: app/src/main/java/com/xburnsx/toutiebudget/di/AppModule.kt
 * Dépendances: Tous les repositories, services, use cases et ViewModels
 */

 package com.xburnsx.toutiebudget.di

 import com.xburnsx.toutiebudget.data.repositories.*
 import com.xburnsx.toutiebudget.data.repositories.impl.*
 import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.data.services.InitialImportService
 import com.xburnsx.toutiebudget.data.utils.ObjectifCalculator
 import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
 import com.xburnsx.toutiebudget.data.banque.AliasBancaireRepository
 import com.xburnsx.toutiebudget.data.banque.BoiteOperationsBancairesRepository
 import com.xburnsx.toutiebudget.domain.services.*
 import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
 import com.xburnsx.toutiebudget.domain.services.Impl.RolloverServiceImpl
 import com.xburnsx.toutiebudget.domain.usecases.*
 import com.xburnsx.toutiebudget.utils.OperationAtomique
 import com.xburnsx.toutiebudget.utils.OperationAtomiqueRoom
 import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
 import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionViewModel
import com.xburnsx.toutiebudget.ui.ajout_transaction.ModifierTransactionViewModel
 import com.xburnsx.toutiebudget.ui.budget.BudgetViewModel
 import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesViewModel
 import com.xburnsx.toutiebudget.ui.comptes.ComptesViewModel
 import com.xburnsx.toutiebudget.ui.cartes_credit.CartesCreditViewModel
import com.xburnsx.toutiebudget.ui.dette.DetteViewModel
import com.xburnsx.toutiebudget.ui.historique.HistoriqueCompteViewModel
import com.xburnsx.toutiebudget.ui.historique.HistoriqueEnveloppeViewModel
import com.xburnsx.toutiebudget.ui.login.LoginViewModel
import com.xburnsx.toutiebudget.ui.startup.StartupViewModel
import com.xburnsx.toutiebudget.ui.virement.VirerArgentViewModel
import com.xburnsx.toutiebudget.ui.statistiques.StatistiquesViewModel
import com.xburnsx.toutiebudget.ui.pret_personnel.PretPersonnelViewModel
import com.xburnsx.toutiebudget.ui.settings.SyncJobViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import android.content.Context

 /**
  * Module d'injection de dépendances pour l'application Toutie Budget.
  * Gère l'instanciation de tous les repositories, services, use cases et ViewModels.
  * SÉCURITÉ FINANCIÈRE : Validation complexe pour éviter les faux soldes
  * ROOM-FIRST : Utilise les repositories Room pour les opérations locales
  */
 object AppModule {
     
    // ===== PR6 : ViewModels liés à la session de l'utilisateur =====
    /**
     * Comme `lazy`, mais vidé à la déconnexion : les écrans partagés ne gardent jamais en mémoire
     * les soldes, l'historique ou le Prêt à placer du compte précédent.
     */
    private class ParSession<T : Any>(private val creer: () -> T) : kotlin.properties.ReadOnlyProperty<Any?, T> {
        @Volatile private var valeur: T? = null
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T =
            valeur ?: synchronized(this) { valeur ?: creer().also { valeur = it } }
        fun vider() {
            val ancienne = synchronized(this) { valeur.also { valeur = null } }
            // Arrêter ses chargements et écoutes en cours
            (ancienne as? androidx.lifecycle.ViewModel)?.viewModelScope?.cancel()
        }
    }
    private val objetsDeSession = java.util.concurrent.CopyOnWriteArrayList<ParSession<*>>()
    private fun <T : Any> parSession(creer: () -> T): ParSession<T> = ParSession(creer).also { objetsDeSession += it }

    /** PR6 : appelé à la déconnexion ou à l'effacement, avant toute autre session. */
    fun terminerSession() {
        objetsDeSession.forEach { it.vider() }
    }

    // ===== BASE DE DONNÉES ROOM =====
     private lateinit var database: ToutieBudgetDatabase
     private lateinit var appContext: Context

     private val operationAtomique: OperationAtomique by lazy { OperationAtomiqueRoom(database) }

     fun initializeDatabase(context: Context) {
         if (!::appContext.isInitialized) {
             appContext = context.applicationContext
         }
         if (!::database.isInitialized) {
             database = ToutieBudgetDatabase.getDatabase(context)
         }
     }

     /** PR4 : arrêter la synchro et vider la file AVANT d'effacer le serveur. */
     suspend fun arreterEtViderSynchronisation() {
         kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
             if (::appContext.isInitialized) com.xburnsx.toutiebudget.workers.SyncWorkManager.arreterSynchronisation(appContext)
             if (::database.isInitialized) database.syncJobDao().supprimerToutesLesTaches()
         }
     }

     suspend fun effacerToutesLesDonneesLocales() {
         kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
             if (::appContext.isInitialized) com.xburnsx.toutiebudget.workers.SyncWorkManager.arreterSynchronisation(appContext)
             if (::database.isInitialized) database.clearAllTables()
         }
     }
     
    // ===== REPOSITORIES ROOM-FIRST =====
     private val compteRepository: CompteRepository by lazy { 
         CompteRepositoryRoomImpl(
             database.compteChequeDao(),
             database.compteCreditDao(),
             database.compteDetteDao(),
             database.compteInvestissementDao(),
             database.syncJobDao()
         )
     }
     private val enveloppeRepository: EnveloppeRepository by lazy { 
         EnveloppeRepositoryRoomImpl(
             database.enveloppeDao(),
             database.allocationMensuelleDao(),
             database.syncJobDao(),
             operationAtomique,
             database.transactionDao(),
             database.categorieDao()
         )
     }
     private val categorieRepository: CategorieRepository by lazy { 
         CategorieRepositoryRoomImpl(
             database.categorieDao(),
             database.syncJobDao(),
             operationAtomique,
             database.enveloppeDao()
         )
     }
           private val transactionRepository: TransactionRepository by lazy { 
          TransactionRepositoryRoomImpl(
              database.transactionDao(),
              database.syncJobDao()
          )
      }
     private val preferenceRepository: PreferenceRepository by lazy { PreferenceRepositoryImpl(appContext) }
     private val allocationMensuelleRepository: AllocationMensuelleRepository by lazy { 
         AllocationMensuelleRepositoryRoomImpl(
             database.allocationMensuelleDao(),
             database.syncJobDao()
         )
     }
     private val tiersRepository: TiersRepository by lazy { 
         TiersRepositoryRoomImpl(
             database.tiersDao(),
             database.syncJobDao()
         )
     }
         private val pretPersonnelRepository: PretPersonnelRepository by lazy { 
        PretPersonnelRepositoryRoomImpl(
            database.pretPersonnelDao(),
            database.syncJobDao()
        )
    }
    
    private val historiqueAllocationRepository: HistoriqueAllocationRepository by lazy { 
        HistoriqueAllocationRepositoryRoomImpl(
            database.historiqueAllocationDao()
        )
    }

     // BANQ-02 : règles d'alias rattachées aux tiers existants (pas de marchand parallèle).
     private val aliasBancaireRepository: AliasBancaireRepository by lazy {
         AliasBancaireRepository(database, database.aliasBancaireDao(),
             database.compteChequeDao(), database.compteCreditDao(),
             database.tiersDao(), database.syncJobDao())
     }
     fun provideAliasBancaireRepository(): AliasBancaireRepository = aliasBancaireRepository

     // BANQ-03 : import bancaire en attente, strictement sans effet sur les soldes.
     private val boiteOperationsBancairesRepository: BoiteOperationsBancairesRepository by lazy {
         BoiteOperationsBancairesRepository(database, database.operationBancaireEnAttenteDao())
     }
     fun provideBoiteOperationsBancairesRepository(): BoiteOperationsBancairesRepository =
         boiteOperationsBancairesRepository

     // ===== SERVICES =====
     private val validationProvenanceService: ValidationProvenanceService by lazy {
         ValidationProvenanceService(enveloppeRepository, compteRepository)
     }
     
     private val historiqueAllocationService: com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService by lazy {
         com.xburnsx.toutiebudget.domain.services.Impl.HistoriqueAllocationServiceImpl(historiqueAllocationRepository)
     }
     private val virementUseCase: VirementUseCase by lazy {
         VirementUseCase(compteRepository, allocationMensuelleRepository, transactionRepository, enveloppeRepository, validationProvenanceService, historiqueAllocationService)
     }
     private val argentService: ArgentService by lazy { ArgentServiceImpl(compteRepository, transactionRepository, allocationMensuelleRepository, virementUseCase, enveloppeRepository, categorieRepository, historiqueAllocationService, operationAtomique) }
     private val realtimeSyncService: RealtimeSyncService by lazy { RealtimeSyncService() }
    
    private val initialImportService: InitialImportService by lazy {
        InitialImportService(
            database.compteChequeDao(),
            database.compteCreditDao(),
            database.compteDetteDao(),
            database.compteInvestissementDao(),
            database.transactionDao(),
            database.categorieDao(),
            database.enveloppeDao(),
            database.allocationMensuelleDao(),
            database.tiersDao(),
            database.aliasBancaireDao(),
            database.operationBancaireEnAttenteDao(),
            database.pretPersonnelDao(),
            database.syncJobDao()
        )
    }
     private val rolloverService: RolloverService by lazy { RolloverServiceImpl(enveloppeRepository, allocationMensuelleRepository, operationAtomique) }
     private val serverStatusService: com.xburnsx.toutiebudget.data.services.ServerStatusService by lazy { com.xburnsx.toutiebudget.data.services.ServerStatusService() }
     
    // ===== SERVICES D'OPTIMISATION (non utilisés) =====

     // ===== CALCULATEUR D'OBJECTIFS =====
     private val objectifCalculator: ObjectifCalculator by lazy { ObjectifCalculator() }

     private val verifierEtExecuterRolloverUseCase: VerifierEtExecuterRolloverUseCase by lazy { VerifierEtExecuterRolloverUseCase(rolloverService, preferenceRepository) }
     private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase by lazy {
         EnregistrerTransactionUseCase(transactionRepository, compteRepository, enveloppeRepository, allocationMensuelleRepository, historiqueAllocationService, operationAtomique)
     }
     private val supprimerTransactionUseCase: SupprimerTransactionUseCase by lazy {
         SupprimerTransactionUseCase(transactionRepository, compteRepository, enveloppeRepository, allocationMensuelleRepository, pretPersonnelRepository, operationAtomique)
     }
     private val modifierTransactionUseCase: ModifierTransactionUseCase by lazy {
         ModifierTransactionUseCase()
     }
 
     // ===== VIEWMODELS =====
     private val budgetViewModel: BudgetViewModel by parSession {
                 BudgetViewModel(
            compteRepository = compteRepository,
            enveloppeRepository = enveloppeRepository,
            categorieRepository = categorieRepository,
            allocationMensuelleRepository = allocationMensuelleRepository, // ← AJOUT
            preferenceRepository = preferenceRepository,
            verifierEtExecuterRolloverUseCase = verifierEtExecuterRolloverUseCase,
            realtimeSyncService = realtimeSyncService,
            validationProvenanceService = validationProvenanceService,
            objectifCalculator = objectifCalculator,
            historiqueAllocationService = historiqueAllocationService,
            operationAtomique = operationAtomique
        )
     }
     
     private val comptesViewModel: ComptesViewModel by parSession {
         ComptesViewModel(
             compteRepository = compteRepository,
             realtimeSyncService = realtimeSyncService,
             categorieRepository = categorieRepository,
             enveloppeRepository = enveloppeRepository,
             transactionRepository = transactionRepository,
             historiqueAllocationService = historiqueAllocationService,
             operationAtomique = operationAtomique
         )
     }

         private val cartesCreditRepository: CarteCreditRepository by lazy {
             CarteCreditRepositoryImpl(compteRepository, transactionRepository)
         }
         
         private val cartesCreditViewModel: CartesCreditViewModel by parSession {
        CartesCreditViewModel(cartesCreditRepository, realtimeSyncService, historiqueAllocationService, operationAtomique)
    }

     private val ajoutTransactionViewModel: AjoutTransactionViewModel by parSession {
         AjoutTransactionViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             tiersRepository = tiersRepository,
             allocationMensuelleRepository = allocationMensuelleRepository,
             enregistrerTransactionUseCase = enregistrerTransactionUseCase,
             transactionRepository = transactionRepository,
             pretPersonnelRepository = pretPersonnelRepository,
             argentService = argentService,
             realtimeSyncService = realtimeSyncService,
             historiqueAllocationService = historiqueAllocationService,
             syncJobDao = database.syncJobDao(),
             operationAtomique = operationAtomique
         )
     }
     
     private val modifierTransactionViewModel: ModifierTransactionViewModel by parSession { 
         ModifierTransactionViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             tiersRepository = tiersRepository,
             transactionRepository = transactionRepository,
             allocationMensuelleRepository = allocationMensuelleRepository,
             enregistrerTransactionUseCase = enregistrerTransactionUseCase,
             supprimerTransactionUseCase = supprimerTransactionUseCase,
             operationAtomique = operationAtomique,
             syncJobDao = database.syncJobDao(),
             modifierOperationLiee = com.xburnsx.toutiebudget.domain.usecases.ModifierOperationLieeUseCase(
                 transactionRepository, compteRepository, pretPersonnelRepository, supprimerTransactionUseCase, argentService, operationAtomique
             )
         )
     }
     
     private val categoriesEnveloppesViewModel: CategoriesEnveloppesViewModel by parSession {
         CategoriesEnveloppesViewModel(
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             realtimeSyncService = realtimeSyncService,
             transactionRepository = transactionRepository
         )
     }

     private val virerArgentViewModel: VirerArgentViewModel by parSession {
         VirerArgentViewModel(
             compteRepository = compteRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             argentService = argentService,
             realtimeSyncService = realtimeSyncService,
             virementBudgetService = virementBudgetService
         )
     }
     private val virementBudgetService: com.xburnsx.toutiebudget.domain.services.VirementBudgetService by lazy {
         com.xburnsx.toutiebudget.domain.services.VirementBudgetService(
             compteRepository, enveloppeRepository, allocationMensuelleRepository, historiqueAllocationService, operationAtomique
         )
     }
     
     private val detteViewModel: DetteViewModel by parSession {
         DetteViewModel(
             compteRepository = compteRepository,
             transactionRepository = transactionRepository,
             enveloppeRepository = enveloppeRepository,
             categorieRepository = categorieRepository,
             historiqueAllocationService = historiqueAllocationService,
             operationAtomique = operationAtomique
         )
     }

      private var statistiquesViewModel: StatistiquesViewModel? = null
      
      private fun createStatistiquesViewModel(): StatistiquesViewModel {
          if (statistiquesViewModel == null) {
              statistiquesViewModel = StatistiquesViewModel(
                  transactionRepository = transactionRepository,
                  enveloppeRepository = enveloppeRepository,
                  tiersRepository = tiersRepository,
                  categorieRepository = categorieRepository,
                  compteRepository = compteRepository,
                  pretPersonnelRepository = pretPersonnelRepository,
                  realtimeSyncService = realtimeSyncService,
                  historiquePatrimoine = com.xburnsx.toutiebudget.data.services.HistoriquePatrimoineLocal(appContext)
              )
          }
          return statistiquesViewModel!!
      }

      private val pretPersonnelViewModel: PretPersonnelViewModel by parSession {
          PretPersonnelViewModel(
              pretPersonnelRepository = pretPersonnelRepository,
              transactionRepository = transactionRepository,
              compteRepository = compteRepository
          )
      }
      
      private val syncJobViewModel: SyncJobViewModel by parSession {
          SyncJobViewModel(database.syncJobDao())
      }
 
     // ===== FONCTIONS PUBLIQUES =====
     
    // Initialisation de cache supprimée

     fun provideRealtimeSyncService(): RealtimeSyncService = realtimeSyncService
     private val planResetService: com.xburnsx.toutiebudget.domain.services.PlanResetService by lazy {
         com.xburnsx.toutiebudget.domain.services.PlanResetService(
             compteRepository, enveloppeRepository, allocationMensuelleRepository, historiqueAllocationService, operationAtomique
         )
     }
     fun providePlanResetService(): com.xburnsx.toutiebudget.domain.services.PlanResetService = planResetService
     
     fun provideSyncJobDao(context: Context): com.xburnsx.toutiebudget.data.room.daos.SyncJobDao {
         initializeDatabase(context)
         return database.syncJobDao()
     }
    
    fun provideInitialImportService(): InitialImportService = initialImportService
     fun provideLoginViewModel(): LoginViewModel = LoginViewModel()
     fun provideStartupViewModel(): StartupViewModel = StartupViewModel()
     fun provideBudgetViewModel(): BudgetViewModel = budgetViewModel
     fun provideCompteRepository(): CompteRepository = compteRepository
     fun provideEnveloppeRepository(): EnveloppeRepository = enveloppeRepository
     fun provideCategorieRepository(): CategorieRepository = categorieRepository
     fun provideTiersRepository(): TiersRepository = tiersRepository
    fun provideTransactionRepository(): TransactionRepository = transactionRepository
    fun provideComptesViewModel(): ComptesViewModel = comptesViewModel
     fun provideCartesCreditViewModel(): CartesCreditViewModel = cartesCreditViewModel
     fun provideAjoutTransactionViewModel(): AjoutTransactionViewModel = ajoutTransactionViewModel
     fun provideModifierTransactionViewModel(): ModifierTransactionViewModel = modifierTransactionViewModel
     fun provideCategoriesEnveloppesViewModel(): CategoriesEnveloppesViewModel = categoriesEnveloppesViewModel
     fun provideVirerArgentViewModel(): VirerArgentViewModel = virerArgentViewModel
     fun provideDetteViewModel(): DetteViewModel = detteViewModel
     /** PA3 : sauvegarde protégée du budget (lecture/écriture Room de l'utilisateur connecté). */
     fun provideSauvegardeBudget(): com.xburnsx.toutiebudget.data.services.SauvegardeBudget =
         com.xburnsx.toutiebudget.data.services.SauvegardeBudget(database)
     private val investissementViewModel by parSession {
         com.xburnsx.toutiebudget.ui.investissement.InvestissementViewModel(
             compteRepository, transactionRepository, historiqueAllocationService, realtimeSyncService,
             operationAtomique = operationAtomique,
             coursEnLigne = com.xburnsx.toutiebudget.data.services.CoursYahoo()
         )
     }
     fun provideInvestissementViewModel(): com.xburnsx.toutiebudget.ui.investissement.InvestissementViewModel = investissementViewModel
     private val actualisationCours by lazy {
         com.xburnsx.toutiebudget.data.services.ActualisationCours(
             compteRepository, operationAtomique, realtimeSyncService,
             com.xburnsx.toutiebudget.data.services.CoursYahoo()
         )
     }
     fun provideActualisationCours(): com.xburnsx.toutiebudget.data.services.ActualisationCours = actualisationCours
     private val archivageAutomatique by lazy {
         com.xburnsx.toutiebudget.data.services.ArchivageAutomatique(
             compteRepository, transactionRepository, operationAtomique, realtimeSyncService
         )
     }
     fun provideArchivageAutomatique(): com.xburnsx.toutiebudget.data.services.ArchivageAutomatique = archivageAutomatique
             fun provideStatistiquesViewModel(): StatistiquesViewModel = createStatistiquesViewModel()
      fun providePretPersonnelViewModel(): PretPersonnelViewModel = pretPersonnelViewModel
      fun provideSyncJobViewModel(): SyncJobViewModel = syncJobViewModel
     fun provideHistoriqueCompteViewModel(savedStateHandle: SavedStateHandle): HistoriqueCompteViewModel {
         return HistoriqueCompteViewModel(
             transactionRepository = transactionRepository,
             enveloppeRepository = enveloppeRepository,
             tiersRepository = tiersRepository,
             supprimerTransactionUseCase = supprimerTransactionUseCase,
             savedStateHandle = savedStateHandle
         )
     }
     
     fun provideHistoriqueEnveloppeViewModel(savedStateHandle: SavedStateHandle): HistoriqueEnveloppeViewModel {
         return HistoriqueEnveloppeViewModel(
             transactionRepository = transactionRepository,
             enveloppeRepository = enveloppeRepository,
             tiersRepository = tiersRepository,
             supprimerTransactionUseCase = supprimerTransactionUseCase,
             savedStateHandle = savedStateHandle,
             syncJobDao = database.syncJobDao()
         )
     }
     fun provideServerStatusViewModel(context: android.content.Context): com.xburnsx.toutiebudget.ui.server.ServerStatusViewModel {
         return com.xburnsx.toutiebudget.ui.server.ServerStatusViewModel(
             serverStatusService = serverStatusService,
             context = context
         )
     }
     
     // 🆕 NOUVEAU : Fournit le SyncWorkManager pour la planification de la synchronisation
     fun provideSyncWorkManager(context: Context): com.xburnsx.toutiebudget.workers.SyncWorkManager {
         return com.xburnsx.toutiebudget.workers.SyncWorkManager
     }
     
     // 🆕 NOUVEAU : Fournit le service de surveillance de la connectivité réseau
     fun provideNetworkConnectivityService(context: Context): com.xburnsx.toutiebudget.data.services.NetworkConnectivityService {
         return com.xburnsx.toutiebudget.data.services.NetworkConnectivityService(context)
     }
     
     // 🆕 NOUVEAU : Fournit le repository d'historique des allocations
     fun provideHistoriqueAllocationRepository(): HistoriqueAllocationRepository = historiqueAllocationRepository
     
     // 🆕 NOUVEAU : Fournit le service d'historique des allocations
     fun provideHistoriqueAllocationService(): com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService = historiqueAllocationService
}
