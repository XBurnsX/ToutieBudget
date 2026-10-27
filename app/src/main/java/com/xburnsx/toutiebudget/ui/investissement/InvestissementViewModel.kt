package com.xburnsx.toutiebudget.ui.investissement

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.DetailsInvestissement
import com.xburnsx.toutiebudget.data.modeles.MouvementPlacement
import com.xburnsx.toutiebudget.data.modeles.PositionTitre
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeValorisation
import com.xburnsx.toutiebudget.data.modeles.TypeMouvementPlacement
import com.xburnsx.toutiebudget.data.modeles.Valorisation
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
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
    val message: String? = null,
    val succesEcriture: Long = 0L
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
    private val realtimeSyncService: RealtimeSyncService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouPlacement = com.xburnsx.toutiebudget.utils.VerrouOperation()

    private val _uiState = MutableStateFlow(InvestissementUiState())
    val uiState: StateFlow<InvestissementUiState> = _uiState.asStateFlow()
    private var compteId: String? = null
    private var generationChargement = 0L

    fun charger(id: String) {
        val generation = ++generationChargement
        compteId = id
        _uiState.value = InvestissementUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val compte = compteRepository.getCompteById(id, "comptes_investissement") as? CompteInvestissement
                    ?: throw Exception("Compte de placement introuvable")
                val transactions = transactionRepository.recupererTransactionsPourCompte(id, "comptes_investissement")
                    .getOrThrow()
                if (generation != generationChargement || compteId != id) return@launch
                _uiState.update { it.copy(isLoading = false, compte = compte, transactions = transactions) }
            } catch (e: Exception) {
                if (generation == generationChargement && compteId == id)
                    _uiState.update { it.copy(isLoading = false, erreurChargement = e.message ?: "Lecture du placement impossible") }
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
        if (MoteurCarteCredit.lireDate(date.trim()) == null || !valeur.isFinite() || valeur < 0.0 ||
            !kotlin.math.abs(valeur).isFinite()
        ) {
            _uiState.update { it.copy(message = "Date de valorisation ou valeur invalide.") }
            return
        }
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

    fun enregistrerPosition(position: PositionTitre) {
        val coutAuCent = runCatching {
            java.math.BigDecimal.valueOf(position.coutTotal)
                .setScale(2, java.math.RoundingMode.UNNECESSARY)
        }.getOrNull()
        if (position.symbole.isBlank() || position.devise.isBlank() ||
            !position.quantite.isFinite() || position.quantite <= 0.0 ||
            coutAuCent == null || !position.coutTotal.isFinite() || position.coutTotal < 0.0 ||
            (position.cours != null && (!position.cours.isFinite() || position.cours < 0.0)) ||
            (position.dateCours != null && MoteurCarteCredit.lireDate(position.dateCours) == null) ||
            position.valeur?.isFinite() == false
        ) {
            _uiState.update { it.copy(message = "Position invalide : quantité, coût ou cours à corriger.") }
            return
        }
        modifierDetails { copy(positions = positions.filter { it.id != position.id } + position) }
    }

    fun supprimerPosition(id: String) = modifierDetails {
        copy(positions = positions.filter { it.id != id })
    }

    fun enregistrerMouvement(mouvement: MouvementPlacement) {
        val montantAuCent = runCatching {
            java.math.BigDecimal.valueOf(mouvement.montant)
                .setScale(2, java.math.RoundingMode.UNNECESSARY)
        }.getOrNull()
        if (MoteurCarteCredit.lireDate(mouvement.date) == null ||
            montantAuCent == null || !mouvement.montant.isFinite() || mouvement.montant < 0.0 ||
            (mouvement.quantite != null && (!mouvement.quantite.isFinite() || mouvement.quantite < 0.0)) ||
            (mouvement.gainRealise != null && !mouvement.gainRealise.isFinite()) ||
            (mouvement.type == TypeMouvementPlacement.VENTE && mouvement.gainRealise == null)
        ) {
            _uiState.update { it.copy(message = "Mouvement invalide : date, montant ou gain réalisé manquant.") }
            return
        }
        modifierDetails { copy(mouvements = mouvements.filter { it.id != mouvement.id } + mouvement) }
    }

    fun supprimerMouvement(id: String) = modifierDetails {
        copy(mouvements = mouvements.filter { it.id != id })
    }

    private fun enregistrer(
        apres: suspend (CompteInvestissement, CompteInvestissement) -> Unit = { _, _ -> },
        transformation: (CompteInvestissement) -> CompteInvestissement
    ) {
        val id = compteId ?: return
        if (_uiState.value.enCours) return
        viewModelScope.lancerUneFois(verrouPlacement) {
            _uiState.update { it.copy(enCours = true) }
            try {
                // IF2/IF7 : valeur et historique sont validés dans le même
                // commit Room. L'échec de trace annule également la valeur.
                val modifie = operationAtomique.executer {
                    val aJour = compteRepository.getCompteById(id, "comptes_investissement") as? CompteInvestissement
                        ?: throw IllegalStateException("Compte de placement introuvable")
                    if (aJour.estArchive) throw IllegalStateException("Compte archivé : réactive-le d'abord.")
                    val resultat = transformation(aJour)
                    val details = resultat.details
                    if (listOfNotNull(details.encaisse, details.retirableConnu, details.valeurDepart)
                            .any { !it.isFinite() || it < 0.0 } ||
                        (details.encaisse != null && details.retirableConnu != null &&
                         details.retirableConnu > details.encaisse + 0.000001)
                    ) throw IllegalStateException("Encaisse ou montant retirable invalide.")
                    if (!resultat.solde.isFinite() || resultat.solde < 0) {
                        throw IllegalStateException("Valeur de placement invalide.")
                    }
                    val details = resultat.details
                    if (listOfNotNull(details.valeurDepart, details.encaisse, details.retirableConnu)
                            .any { !it.isFinite() || it < 0.0 } ||
                        details.positions.any { position ->
                            !position.quantite.isFinite() || position.quantite < 0.0 ||
                                !position.coutTotal.isFinite() || position.coutTotal < 0.0 ||
                                (position.cours != null &&
                                    (!position.cours.isFinite() || position.cours < 0.0))
                        } || details.mouvements.any { mouvement ->
                            !mouvement.montant.isFinite() ||
                                (mouvement.quantite != null && !mouvement.quantite.isFinite()) ||
                                (mouvement.gainRealise != null && !mouvement.gainRealise.isFinite())
                        }
                    ) {
                        throw IllegalArgumentException("Montant, prix, quantité ou liquidité du placement invalide.")
                    }
                    compteRepository.mettreAJourCompte(resultat).getOrThrow()
                    apres(aJour, resultat)
                    resultat
                }
                realtimeSyncService.declencherMiseAJourComptes()
                _uiState.update { courant ->
                    if (compteId == id) courant.copy(enCours = false, compte = modifie,
                            message = null, succesEcriture = courant.succesEcriture + 1)
                    else courant.copy(enCours = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(enCours = false, message = "Échec : ${e.message}") }
            }
        }
    }

    fun effacerMessage() { _uiState.update { it.copy(message = null) } }
}
