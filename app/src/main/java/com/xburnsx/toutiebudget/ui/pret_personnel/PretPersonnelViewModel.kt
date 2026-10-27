package com.xburnsx.toutiebudget.ui.pret_personnel

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel
import com.xburnsx.toutiebudget.data.repositories.PretPersonnelRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Prêt personnel : consultation seulement. Les paiements et remboursements se font
 * exclusivement dans Transactions ; ici on peut seulement archiver ou réactiver un dossier.
 */
class PretPersonnelViewModel(
    private val pretPersonnelRepository: PretPersonnelRepository,
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouPret = com.xburnsx.toutiebudget.utils.VerrouOperation()

    private val _uiState = MutableStateFlow(PretPersonnelUiState(isLoading = true))
    val uiState: StateFlow<PretPersonnelUiState> = _uiState.asStateFlow()

    // P4 : dossier dont le détail est attendu ; une réponse d'un autre dossier est ignorée
    private var dossierDemande: String? = null

    init {
        // Ne rien faire ici; l'écran déclenchera charger() dans LaunchedEffect pour garantir un refresh à chaque ouverture
    }

    fun charger() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, erreur = null) }
            val result = pretPersonnelRepository.lister()
            result.onSuccess { entries ->
                // Un dossier actif reste visible même à 0 $ (ex. réactivé) ; l'archivage le retire
                val itemsPret = entries
                    .filter { it.type == TypePretPersonnel.PRET && !it.estArchive }
                    .map { it.toItem() }
                val itemsEmprunt = entries
                    .filter { it.type == TypePretPersonnel.DETTE && !it.estArchive }
                    .map { it.toItem() }
                val itemsArchives = entries
                    .filter { it.estArchive }
                    .map { it.toItem() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        erreur = null,
                        items = itemsPret + itemsEmprunt,
                        itemsPret = itemsPret,
                        itemsEmprunt = itemsEmprunt,
                        itemsArchives = itemsArchives
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, erreur = e.message) }
            }
        }
    }

    fun setTab(tab: PretTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun chargerHistoriquePourPret(pretId: String, nomTiers: String) {
        dossierDemande = pretId
        // P4 : vider le détail précédent AVANT de charger le nouveau
        _uiState.update {
            it.copy(isLoadingHistorique = true, historique = emptyList(), detailPret = null, erreurAction = null)
        }
        viewModelScope.launch {
            try {
            val txs = transactionRepository.recupererToutesLesTransactions().getOrThrow()
                .filter { t ->
                    (t.type == TypeTransaction.Pret || t.type == TypeTransaction.RemboursementRecu ||
                     t.type == TypeTransaction.Emprunt || t.type == TypeTransaction.RemboursementDonne) &&
                    (t.sousItems?.contains(pretId) == true)
                }
                .sortedByDescending { it.date }
            val items = txs.map { t ->
                val lib = when (t.type) {
                    TypeTransaction.Pret -> "Prêt accordé"
                    TypeTransaction.RemboursementRecu -> "Remboursement reçu"
                    TypeTransaction.Emprunt -> "Dette contractée"
                    TypeTransaction.RemboursementDonne -> "Remboursement donné"
                    else -> t.type.libelle
                }
                HistoriqueItem(
                    id = t.id,
                    date = t.date,
                    type = lib,
                    montant = t.montant
                )
            }
            val recordActif = pretPersonnelRepository.lister().getOrThrow()
                .firstOrNull { it.id == pretId }

            // Un autre dossier a été ouvert entre-temps : ne pas afficher ces données
            if (dossierDemande != pretId) return@launch

            // PP6 : montant déjà remboursé selon les remboursements liés à ce dossier
            val rembourse = txs
                .filter { it.type == TypeTransaction.RemboursementRecu || it.type == TypeTransaction.RemboursementDonne }
                .sumOf { kotlin.math.abs(it.montant) }

            _uiState.update {
                it.copy(
                    isLoadingHistorique = false,
                    historique = items,
                    detailPret = recordActif,
                    montantRembourse = rembourse
                )
            }
            } catch (e: Exception) {
                if (dossierDemande == pretId) {
                    _uiState.update { it.copy(
                        isLoadingHistorique = false,
                        historique = emptyList(),
                        detailPret = null,
                        erreurAction = "Historique indisponible : ${e.message}"
                    ) }
                }
            }
        }
    }

    fun clearHistorique() {
        dossierDemande = null
        _uiState.update {
            it.copy(historique = emptyList(), isLoadingHistorique = false, detailPret = null, erreurAction = null)
        }
    }

    /** PP2 / PP3 : archiver ou réactiver un dossier, sans toucher au solde ni à l'historique. */
    fun changerArchivage(pretId: String, archiver: Boolean) {
        viewModelScope.lancerUneFois(verrouPret) {
            val dossiers = pretPersonnelRepository.lister().getOrElse { e ->
                _uiState.update { it.copy(erreurAction = "Impossible de charger les dossiers : ${e.message}") }
                return@lancerUneFois
            }
            val dossier = dossiers.firstOrNull { it.id == pretId }
            if (dossier == null) {
                _uiState.update { it.copy(erreurAction = "Dossier introuvable") }
                return@lancerUneFois
            }
            pretPersonnelRepository.mettreAJour(dossier.copy(estArchive = archiver))
                .onSuccess {
                    if (dossierDemande == pretId) {
                        _uiState.update { it.copy(detailPret = dossier.copy(estArchive = archiver), erreurAction = null) }
                    }
                    charger()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(erreurAction = "Échec : ${e.message}") }
                }
        }
    }

    private fun PretPersonnel.toItem(): PretPersonnelItem = PretPersonnelItem(
        key = this.id,
        nomTiers = this.nomTiers ?: "",
        montantPrete = this.montantInitial,
        montantRembourse = 0.0,
        soldeRestant = this.solde,
        derniereDate = null,
        type = this.type
    )
}
