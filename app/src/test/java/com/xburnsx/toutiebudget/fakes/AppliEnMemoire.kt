package com.xburnsx.toutiebudget.fakes

import com.xburnsx.toutiebudget.data.repositories.impl.CarteCreditRepositoryImpl
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.data.utils.ObjectifCalculator
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
import com.xburnsx.toutiebudget.domain.services.Impl.RolloverServiceImpl
import com.xburnsx.toutiebudget.domain.services.ValidationProvenanceService
import com.xburnsx.toutiebudget.domain.services.VirementBudgetService
import com.xburnsx.toutiebudget.domain.usecases.EnregistrerTransactionUseCase
import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
import com.xburnsx.toutiebudget.domain.usecases.VerifierEtExecuterRolloverUseCase
import com.xburnsx.toutiebudget.domain.usecases.VirementUseCase
import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionViewModel
import com.xburnsx.toutiebudget.ui.budget.BudgetViewModel
import com.xburnsx.toutiebudget.ui.cartes_credit.CartesCreditViewModel
import com.xburnsx.toutiebudget.ui.comptes.ComptesViewModel
import com.xburnsx.toutiebudget.ui.dette.DetteViewModel
import com.xburnsx.toutiebudget.ui.investissement.InvestissementViewModel
import com.xburnsx.toutiebudget.ui.pret_personnel.PretPersonnelViewModel
import com.xburnsx.toutiebudget.ui.virement.VirerArgentViewModel

/**
 * L'app « sans écran » : les VRAIS ViewModels et services, branchés sur la base en mémoire.
 * Un test appelle les mêmes fonctions qu'un appui sur un bouton.
 */
class AppliEnMemoire {
    val b = BanqueEnMemoire()
    val autres = AutresDepotsEnMemoire()
    val realtime = RealtimeSyncService()

    init { connecterUtilisateurDeTest() }

    private val provenance = ValidationProvenanceService(b.depotEnveloppes, b.depotComptes)
    val argent = ArgentServiceImpl(
        b.depotComptes, b.depotTransactions, b.depotAllocations,
        VirementUseCase(b.depotComptes, b.depotAllocations, b.depotTransactions, b.depotEnveloppes, provenance, b.historiqueService),
        b.depotEnveloppes, b.depotCategories, b.historiqueService
    )
    val enregistrer = EnregistrerTransactionUseCase(b.depotTransactions, b.depotComptes, b.depotEnveloppes, b.depotAllocations, b.historiqueService)
    val supprimer = SupprimerTransactionUseCase(b.depotTransactions, b.depotComptes, b.depotEnveloppes, b.depotAllocations)

    fun ajoutTransaction() = AjoutTransactionViewModel(
        b.depotComptes, b.depotEnveloppes, b.depotCategories, autres.depotTiers, b.depotAllocations, enregistrer,
        b.depotTransactions, autres.depotPrets, argent, realtime, b.historiqueService
    )

    fun virer() = VirerArgentViewModel(
        b.depotComptes, b.depotEnveloppes, b.depotCategories, argent, realtime,
        VirementBudgetService(b.depotComptes, b.depotEnveloppes, b.depotAllocations, b.historiqueService)
    )

    fun budget() = BudgetViewModel(
        b.depotComptes, b.depotEnveloppes, b.depotCategories, b.depotAllocations, autres.preferences,
        VerifierEtExecuterRolloverUseCase(RolloverServiceImpl(b.depotEnveloppes, b.depotAllocations), autres.preferences),
        realtime, provenance, ObjectifCalculator(), b.historiqueService
    )

    fun comptes() = ComptesViewModel(b.depotComptes, realtime, b.depotCategories, b.depotEnveloppes, b.depotTransactions, b.historiqueService)
    fun cartes() = CartesCreditViewModel(CarteCreditRepositoryImpl(b.depotComptes, b.depotTransactions), realtime, b.historiqueService)
    fun dette() = DetteViewModel(b.depotComptes, b.depotTransactions, b.depotEnveloppes, b.depotCategories, b.historiqueService)
    fun placement() = InvestissementViewModel(b.depotComptes, b.depotTransactions, b.historiqueService, realtime)
    fun prets() = PretPersonnelViewModel(autres.depotPrets, b.depotTransactions, b.depotComptes)

    companion object {
        /** Simule une session ouverte (les services lisent l'utilisateur connecté). */
        fun connecterUtilisateurDeTest() {
            val champ = PocketBaseClient::class.java.getDeclaredField("utilisateurConnecte")
            champ.isAccessible = true
            champ.set(PocketBaseClient, PocketBaseClient.EnregistrementUtilisateur(id = "u", email = "tester@exemple.ca"))
        }
    }
}
