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
    val succesEcriture: Long = 0L,
    val actualisationEnCours: Boolean = false
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
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte,
    /** Prix en ligne pour « Actualiser » (null = seulement la saisie à la main, ex. tests). */
    private val coursEnLigne: com.xburnsx.toutiebudget.data.services.CoursEnLigne? = null
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
        // IF14 : protéger un formulaire qui part d'une ancienne version.
        // Un clic d'un autre appareil ne doit pas être écrasé par une copie
        // périmée du JSON d'encaisse, positions ou mouvements.
        val attendu = _uiState.value.compte?.details ?: run {
            _uiState.update { it.copy(message = "Placement non chargé; actualise avant de modifier.") }
            return
        }
        enregistrer { compte ->
            if (compte.details != attendu) throw IllegalStateException(
                "Les détails du placement ont changé sur un autre appareil. Actualise avant de sauvegarder."
            )
            compte.copy(detailsInvestissementJson = compte.details.changement().versJson())
        }
    }

    /**
     * IF2 / IF7 : nouvelle valeur datée. Marché = gain ou perte latent ; correction = erreur de solde.
     * Jamais un dépôt, un revenu, une dépense ni du Prêt à placer.
     */
    fun nouvelleValeur(date: String, valeur: Double, type: TypeValorisation, note: String?) {
        // IF11 : valeur signée possible pour un portefeuille sur marge.
        // Ce n'est pas automatiquement un gain ni un crédit retirable.
        if (MoteurCarteCredit.lireDate(date.trim()) == null || !valeur.isFinite()) {
            _uiState.update { it.copy(message = "Date de valorisation ou valeur invalide.") }
            return
        }
        val soldeObserve = _uiState.value.compte?.solde ?: run {
            _uiState.update { it.copy(message = "Placement non chargé; recharge sa valeur.") }
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
            if (kotlin.math.abs(compte.solde - soldeObserve) > 0.005) {
                throw IllegalStateException(
                    "La valeur du placement a changé depuis l'ouverture du formulaire. Recharge avant de confirmer."
                )
            }
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
            (mouvement.gainRealise != null && !mouvement.gainRealise.isFinite())
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
                    if (!resultat.solde.isFinite()) {
                        throw IllegalStateException("Valeur de placement invalide.")
                    }
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

    // ===== Suivi par titre (achats, ventes, cours) =====

    private fun aujourdhui() = MoteurCarteCredit.ecrireDate(java.util.Date())

    /**
     * Les titres décident de la valeur du compte : le solde devient la valeur du portefeuille (titres + argent
     * non placé) dès qu'elle est connue. Un changement de cours est noté comme valeur de marché datée (IF7).
     */
    private fun avecValeurDuPortefeuille(
        compte: CompteInvestissement,
        details: DetailsInvestissement,
        noterValeurDeMarche: Boolean
    ): CompteInvestissement {
        val valeur = com.xburnsx.toutiebudget.domain.services.Portefeuille.valeurCompte(details, compte.solde)
        val avecTrace = if (noterValeurDeMarche && valeur != null && kotlin.math.abs(valeur - compte.solde) > 0.005) {
            details.copy(valorisations = details.valorisations + Valorisation(
                date = aujourdhui(), valeur = valeur, ancienneValeur = compte.solde,
                type = TypeValorisation.MARCHE, note = "Cours des titres à jour"
            ))
        } else details
        return compte.copy(solde = valeur ?: compte.solde, detailsInvestissementJson = avecTrace.versJson())
    }

    fun acheter(symbole: String, quantite: Double, prix: Double, frais: Double, date: String, devise: String, payeAvecArgentNonPlace: Boolean) =
        enregistrer { compte ->
            val d = com.xburnsx.toutiebudget.domain.services.Portefeuille.acheter(
                compte.details, compte.solde, symbole, quantite, prix, frais, date.trim(), devise, payeAvecArgentNonPlace
            )
            avecValeurDuPortefeuille(compte, d, noterValeurDeMarche = false)
        }

    fun vendre(symbole: String, quantite: Double, prix: Double, frais: Double, date: String) =
        enregistrer { compte ->
            val d = com.xburnsx.toutiebudget.domain.services.Portefeuille.vendre(
                compte.details, compte.solde, symbole, quantite, prix, frais, date.trim()
            )
            avecValeurDuPortefeuille(compte, d, noterValeurDeMarche = false)
        }

    /** Saisie à la main (si le cours en ligne est introuvable, ou pour corriger). */
    fun mettreCoursManuel(symbole: String, prix: Double) =
        enregistrer { compte ->
            val d = com.xburnsx.toutiebudget.domain.services.Portefeuille.mettreCours(compte.details, symbole, prix, aujourdhui())
            avecValeurDuPortefeuille(compte, d, noterValeurDeMarche = true)
        }

    /** « Actualiser » : dernier prix de chaque titre et taux des devises ; ce qui échoue garde son ancien prix. */
    fun actualiserCours() {
        val service = coursEnLigne ?: run {
            _uiState.update { it.copy(message = "Cours en ligne indisponible : entre le prix à la main.") }
            return
        }
        val positions = _uiState.value.compte?.details?.positions.orEmpty()
        if (positions.isEmpty() || _uiState.value.actualisationEnCours) return
        _uiState.update { it.copy(actualisationEnCours = true) }
        viewModelScope.launch {
            val cotations = mutableMapOf<String, com.xburnsx.toutiebudget.data.services.Cotation>()
            val echecs = mutableListOf<String>()
            positions.forEach { p ->
                service.cotation(p.symbole, p.symboleCotation)
                    .onSuccess { cotations[p.symbole] = it }
                    .onFailure { echecs += p.symbole }
            }
            val taux = mutableMapOf<String, Double>()
            (cotations.values.map { it.devise } + positions.map { it.devise }).toSet().filter { it != "CAD" }.forEach { devise ->
                service.tauxVersCad(devise).onSuccess { taux[devise] = it }.onFailure { echecs += "taux $devise" }
            }
            _uiState.update { it.copy(actualisationEnCours = false) }
            if (cotations.isEmpty() && taux.isEmpty()) {
                _uiState.update { it.copy(message = "Aucun cours trouvé en ligne (${echecs.joinToString()}). Entre le prix à la main.") }
                return@launch
            }
            enregistrer { compte ->
                var d = compte.details.copy(tauxVersCad = compte.details.tauxVersCad.orEmpty() + taux)
                cotations.forEach { (symbole, c) ->
                    if (d.positions.any { it.symbole == symbole }) {
                        d = com.xburnsx.toutiebudget.domain.services.Portefeuille.mettreCours(d, symbole, c.prix, aujourdhui(), c.devise, c.symboleCotation)
                    }
                }
                avecValeurDuPortefeuille(compte, d, noterValeurDeMarche = true)
            }
            if (echecs.isNotEmpty()) {
                _uiState.update { it.copy(message = "Pas de cours en ligne pour : ${echecs.joinToString()}. Mets le prix à la main (touche le titre).") }
            }
        }
    }

    fun effacerMessage() { _uiState.update { it.copy(message = null) } }
}
