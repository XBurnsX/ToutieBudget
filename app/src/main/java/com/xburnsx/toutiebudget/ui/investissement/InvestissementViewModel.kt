package com.xburnsx.toutiebudget.ui.investissement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.DetailsInvestissement
import com.xburnsx.toutiebudget.data.modeles.MouvementPlacement
import com.xburnsx.toutiebudget.data.modeles.PositionTitre
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeValorisation
import com.xburnsx.toutiebudget.data.modeles.Valorisation
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvestissementUiState(
    val compte: CompteInvestissement? = null,
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val enCours: Boolean = false,
    val erreurChargement: String? = null,
    val message: String? = null
)

/**
 * « Gérer le placement » (section 12). Voir la valeur, la performance et l'historique, et noter
 * les mouvements propres au placement. Les vrais transferts d'argent restent dans Virer argent.
 * Chaque action est relue puis enregistrée immédiatement ; une erreur est affichée, jamais ignorée.
 */
class InvestissementViewModel(
    private val compteRepository: CompteRepository,
    private val transactionRepository: TransactionRepository,
    private val historiqueAllocationService: HistoriqueAllocationService,
    private val realtimeSyncService: RealtimeSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvestissementUiState())
    val uiState: StateFlow<InvestissementUiState> = _uiState.asStateFlow()
    private var compteId: String? = null

    fun charger(id: String) {
        compteId = id
        _uiState.value = InvestissementUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val compte = compteRepository.getCompteById(id, "comptes_investissement") as? CompteInvestissement
                    ?: throw Exception("Compte de placement introuvable")
                val transactions = transactionRepository.recupererTransactionsPourCompte(id, "comptes_investissement")
                    .getOrElse { emptyList() }
                _uiState.update { it.copy(isLoading = false, compte = compte, transactions = transactions) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, erreurChargement = e.message) }
            }
        }
    }

    fun reessayer() { compteId?.let { charger(it) } }

    /** Modifie seulement les détails, sur la version relue (le solde n'est pas touché). */
    fun modifierDetails(changement: DetailsInvestissement.() -> DetailsInvestissement) {
        enregistrer { compte -> compte.copy(detailsInvestissementJson = compte.details.changement().versJson()) }
    }

    /**
     * IF2 / IF7 : nouvelle valeur datée. Marché = gain ou perte latent ; correction = erreur de solde.
     * Jamais un dépôt, un revenu, une dépense ni du Prêt à placer.
     */
    fun nouvelleValeur(date: String, valeur: Double, type: TypeValorisation, note: String?) {
        enregistrer(apres = { avant, apres ->
            historiqueAllocationService.enregistrerAjustementCompte(
                compte = apres,
                typeAction = if (type == TypeValorisation.MARCHE) "VALORISATION_MARCHE" else "CORRECTION_SOLDE",
                description = "${type.libelle} — ${apres.nom} : ${MoneyFormatter.formatAmount(avant.solde)} → ${MoneyFormatter.formatAmount(apres.solde)}" +
                    (note?.let { " ($it)" } ?: ""),
                montant = apres.solde - avant.solde,
                soldeAvant = avant.solde,
                soldeApres = apres.solde,
                pretAPlacerAvant = 0.0,
                pretAPlacerApres = 0.0
            )
        }) { compte ->
            val v = Valorisation(date = date, valeur = MoneyFormatter.roundAmount(valeur), ancienneValeur = compte.solde, type = type, note = note)
            compte.copy(
                solde = MoneyFormatter.roundAmount(valeur),
                detailsInvestissementJson = compte.details.copy(valorisations = compte.details.valorisations + v).versJson()
            )
        }
    }

    fun enregistrerPosition(position: PositionTitre) = modifierDetails {
        copy(positions = positions.filter { it.id != position.id } + position)
    }

    fun supprimerPosition(id: String) = modifierDetails { copy(positions = positions.filter { it.id != id }) }

    fun enregistrerMouvement(mouvement: MouvementPlacement) = modifierDetails {
        copy(mouvements = mouvements.filter { it.id != mouvement.id } + mouvement)
    }

    fun supprimerMouvement(id: String) = modifierDetails { copy(mouvements = mouvements.filter { it.id != id }) }

    private fun enregistrer(
        apres: suspend (CompteInvestissement, CompteInvestissement) -> Unit = { _, _ -> },
        transformation: (CompteInvestissement) -> CompteInvestissement
    ) {
        val id = compteId ?: return
        if (_uiState.value.enCours) return
        viewModelScope.launch {
            _uiState.update { it.copy(enCours = true) }
            try {
                val aJour = compteRepository.getCompteById(id, "comptes_investissement") as? CompteInvestissement
                    ?: throw Exception("Compte de placement introuvable")
                val modifie = transformation(aJour)
                compteRepository.mettreAJourCompte(modifie).getOrThrow()
                try { apres(aJour, modifie) } catch (e: Exception) {
                    android.util.Log.e("ToutieBudget", "Trace de placement non enregistrée: ${e.message}")
                }
                realtimeSyncService.declencherMiseAJourComptes()
                _uiState.update { it.copy(enCours = false, compte = modifie) }
            } catch (e: Exception) {
                _uiState.update { it.copy(enCours = false, message = "Échec : ${e.message}") }
            }
        }
    }

    fun effacerMessage() { _uiState.update { it.copy(message = null) } }
}
