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
import com.xburnsx.toutiebudget.ui.ajout_transaction.ModifierTransactionViewModel
import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesViewModel
import com.xburnsx.toutiebudget.ui.historique.HistoriqueCompteViewModel
import com.xburnsx.toutiebudget.ui.historique.HistoriqueEnveloppeViewModel
import androidx.lifecycle.SavedStateHandle
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

    /** Les ViewModels créés par le test ; fermés à la fin pour qu'aucun écouteur ne survive au test suivant. */
    private val ecrans = androidx.lifecycle.ViewModelStore()
    private fun <T : androidx.lifecycle.ViewModel> T.suivi(): T = also { ecrans.put(java.util.UUID.randomUUID().toString(), it) }
    fun fermer() = ecrans.clear()

    private val provenance = ValidationProvenanceService(b.depotEnveloppes, b.depotComptes)
    val argent = ArgentServiceImpl(
        b.depotComptes, b.depotTransactions, b.depotAllocations,
        VirementUseCase(b.depotComptes, b.depotAllocations, b.depotTransactions, b.depotEnveloppes, provenance, b.historiqueService),
        b.depotEnveloppes, b.depotCategories, b.historiqueService
    )
    val enregistrer = EnregistrerTransactionUseCase(b.depotTransactions, b.depotComptes, b.depotEnveloppes, b.depotAllocations, b.historiqueService)
    val supprimer = SupprimerTransactionUseCase(b.depotTransactions, b.depotComptes, b.depotEnveloppes, b.depotAllocations, autres.depotPrets)

    fun ajoutTransaction() = AjoutTransactionViewModel(
        b.depotComptes, b.depotEnveloppes, b.depotCategories, autres.depotTiers, b.depotAllocations, enregistrer,
        b.depotTransactions, autres.depotPrets, argent, realtime, b.historiqueService
    ).suivi()

    fun virer() = VirerArgentViewModel(
        b.depotComptes, b.depotEnveloppes, b.depotCategories, argent, realtime,
        VirementBudgetService(b.depotComptes, b.depotEnveloppes, b.depotAllocations, b.historiqueService)
    ).suivi()

    fun budget() = BudgetViewModel(
        b.depotComptes, b.depotEnveloppes, b.depotCategories, b.depotAllocations, autres.preferences,
        VerifierEtExecuterRolloverUseCase(RolloverServiceImpl(b.depotEnveloppes, b.depotAllocations), autres.preferences),
        realtime, provenance, ObjectifCalculator(), b.historiqueService
    ).suivi()

    fun comptes() = ComptesViewModel(b.depotComptes, realtime, b.depotCategories, b.depotEnveloppes, b.depotTransactions, b.historiqueService).suivi()
    fun cartes() = CartesCreditViewModel(CarteCreditRepositoryImpl(b.depotComptes, b.depotTransactions), realtime, b.historiqueService).suivi()
    fun dette() = DetteViewModel(b.depotComptes, b.depotTransactions, b.depotEnveloppes, b.depotCategories, b.historiqueService).suivi()
    fun placement() = InvestissementViewModel(b.depotComptes, b.depotTransactions, b.historiqueService, realtime).suivi()
    fun prets() = PretPersonnelViewModel(autres.depotPrets, b.depotTransactions, b.depotComptes).suivi()

    fun modifier() = ModifierTransactionViewModel(
        b.depotComptes, b.depotEnveloppes, b.depotCategories, autres.depotTiers, b.depotTransactions, b.depotAllocations, enregistrer, supprimer
    ).suivi()
    fun historiqueCompte(compteId: String, collection: String = "comptes_cheques") = HistoriqueCompteViewModel(
        b.depotTransactions, b.depotEnveloppes, autres.depotTiers, supprimer,
        SavedStateHandle(mapOf("compteId" to compteId, "collectionCompte" to collection, "nomCompte" to compteId))
    ).suivi()
    fun historiqueEnveloppe(enveloppeId: String) = HistoriqueEnveloppeViewModel(
        b.depotTransactions, b.depotEnveloppes, autres.depotTiers, supprimer,
        SavedStateHandle(mapOf("enveloppeId" to enveloppeId, "nomEnveloppe" to enveloppeId))
    ).suivi()
    fun categories() = CategoriesEnveloppesViewModel(b.depotEnveloppes, b.depotCategories, realtime).suivi()
    val rollover = VerifierEtExecuterRolloverUseCase(RolloverServiceImpl(b.depotEnveloppes, b.depotAllocations), autres.preferences)

    companion object {
        /** Simule une session ouverte (les services lisent l'utilisateur connecté). */
        fun connecterUtilisateurDeTest() {
            val champ = PocketBaseClient::class.java.getDeclaredField("utilisateurConnecte")
            champ.isAccessible = true
            champ.set(PocketBaseClient, PocketBaseClient.EnregistrementUtilisateur(id = "u", email = "tester@exemple.ca"))
        }
    }
}
