package com.xburnsx.toutiebudget.ui.dette

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.AjustementDette
import com.xburnsx.toutiebudget.data.modeles.ChangementTaux
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.ModeDette
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeAjustementDette
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * « Gérer la dette » (section 11). L'écran informe, configure et projette ; il ne débite
 * jamais un compte : les paiements réels passent par Transactions.
 *
 * DF1 : les saisies restent dans un brouillon et sont enregistrées UNE fois.
 * DF2 / DF15 : à l'enregistrement, la dette est relue et seuls les paramètres sont fusionnés :
 *              le solde réel et le compteur de versements ne sont jamais réécrits.
 */
class DetteViewModel(
    private val compteRepository: CompteRepository,
    private val transactionRepository: TransactionRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val historiqueAllocationService: HistoriqueAllocationService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouDette = com.xburnsx.toutiebudget.utils.VerrouOperation()

    private val _uiState = MutableStateFlow(DetteUiState())
    val uiState: StateFlow<DetteUiState> = _uiState.asStateFlow()

    private var detteIdCourante: String? = null
    @Volatile private var generationChargementDette = 0L

    fun chargerDette(detteId: String) {
        val generation = ++generationChargementDette
        detteIdCourante = detteId
        // Tout de suite : l'écran ne doit pas réagir à l'état d'une visite précédente
        _uiState.update { DetteUiState(isLoading = true) }
        viewModelScope.launch {
            try {
                val dette = compteRepository.getCompteById(detteId, "comptes_dettes") as? CompteDette
                    ?: throw Exception("Dette non trouvée")
                val transactions = transactionRepository.recupererTransactionsPourCompte(detteId, "comptes_dettes")
                    .getOrThrow()
                val formulaire = FormulaireDette.depuis(dette, trouverEnveloppeLiee(dette))
                if (generation != generationChargementDette || detteIdCourante != detteId) return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dette = dette,
                        transactions = transactions,
                        formulaire = formulaire,
                        formulaireInitial = formulaire
                    )
                }
            } catch (e: Exception) {
                if (generation == generationChargementDette && detteIdCourante == detteId) {
                    _uiState.update { it.copy(isLoading = false, erreurChargement = "Erreur lors du chargement : ${e.message}") }
                }
            }
        }
    }

    /** DF13 : « Réessayer » relance vraiment le chargement. */
    fun reessayer() {
        detteIdCourante?.let { chargerDette(it) }
    }

    /** DF11 : lien existant, ou unique homonyme de la catégorie Dettes pour une ancienne dette (jamais ailleurs). */
    private suspend fun trouverEnveloppeLiee(dette: CompteDette): String? {
        val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
        val categories = categorieRepository.recupererToutesLesCategories().getOrThrow()
        dette.details.enveloppeId?.let { id ->
            val env = enveloppes.firstOrNull { it.id == id }
                ?: throw IllegalStateException("L'enveloppe associée à cette dette est introuvable.")
            if (env.utilisateurId != dette.utilisateurId ||
                categories.none { it.id == env.categorieId }
            ) throw IllegalStateException(
                "Lien dette/enveloppe invalide : propriétaire ou catégorie différent."
            )
            // Une enveloppe archivée reste valable pour consulter l'historique.
            return env.id
        }
        // Migration prudente d'un ancien lien sans ID : uniquement un homonyme
        // UNIQUE de la catégorie Dettes ET du même utilisateur.
        val catDettes = categories.firstOrNull {
            it.nom.equals("Dettes", ignoreCase = true)
        } ?: return null
        return enveloppes.singleOrNull {
            it.nom.equals(dette.nom, ignoreCase = true) &&
                it.categorieId == catDettes.id &&
                it.utilisateurId == dette.utilisateurId &&
                !it.estArchive
        }?.id
    }

    fun modifierFormulaire(changement: FormulaireDette.() -> FormulaireDette) {
        _uiState.update { it.copy(formulaire = it.formulaire.changement()) }
    }

    /** DF1 / DF6 / DF15 : validation complète puis UN enregistrement fusionné sur la version à jour. */
    fun sauvegarder() {
        val etat = _uiState.value
        val f = etat.formulaire
        val erreurs = f.valider()
        if (erreurs.isNotEmpty()) {
            _uiState.update { it.copy(formulaire = f.copy(erreurs = erreurs)) }
            return
        }
        if (etat.isSaving) return
        val id = etat.dette?.id ?: return

        viewModelScope.lancerUneFois(verrouDette) {
            _uiState.update { it.copy(isSaving = true, messageErreur = null) }
            try {
                // DF15 : lecture, détection de concurrence et écriture indivisibles en Room.
                val (aJour, aSauver) = operationAtomique.executer {
                val aJour = compteRepository.getCompteById(id, "comptes_dettes") as? CompteDette
                    ?: throw Exception("Dette introuvable")
                val avant = etat.dette ?: throw IllegalStateException("Dossier initial introuvable")
                // DF15 : les mouvements financiers d'un autre appareil restent prioritaires;
                // les changements CONCURRENTIELS de paramètres demandent une résolution
                // explicite, pas une écriture aveugle du brouillon périmé.
                val configModifieeAilleurs =
                    aJour.nom != avant.nom ||
                    aJour.montantInitial != avant.montantInitial ||
                    aJour.tauxInteret != avant.tauxInteret ||
                    aJour.dureeMoisPret != avant.dureeMoisPret ||
                    aJour.paiementMinimum != avant.paiementMinimum ||
                    aJour.details.mode != avant.details.mode ||
                    aJour.details.dateDebut != avant.details.dateDebut ||
                    aJour.details.dateFin != avant.details.dateFin ||
                    aJour.details.jourVersement != avant.details.jourVersement ||
                    aJour.details.enveloppeId != avant.details.enveloppeId ||
                    aJour.details.tauxVariable != avant.details.tauxVariable ||
                    aJour.details.changementsTaux != avant.details.changementsTaux
                if (configModifieeAilleurs) throw IllegalStateException(
                    "Les paramètres de cette dette ont été modifiés ailleurs. " +
                        "Recharge le dossier avant de sauvegarder; ton brouillon n'a pas été écrasé."
                )
                val details = aJour.details.copy(
                    mode = f.mode,
                    dateDebut = f.dateDebut.trim().ifBlank { null },
                    dateFin = f.dateFin.trim().ifBlank { null },
                    jourVersement = f.jourVersement.trim().toIntOrNull(),
                    enveloppeId = f.enveloppeId ?: aJour.details.enveloppeId,
                    tauxVariable = f.tauxVariable,
                    changementsTaux = f.changementsTaux
                )
                val aSauver = aJour.copy(
                    nom = f.nom.trim(),
                    montantInitial = nombre(f.montantInitial) ?: aJour.montantInitial,
                    tauxInteret = if (f.taux.isBlank()) null else nombre(f.taux),
                    dureeMoisPret = f.dureeMois.trim().toIntOrNull(),
                    paiementMinimum = nombre(f.versementPrevu),
                    detailsDetteJson = details.versJson()
                    // soldeDette, paiementEffectue et estArchive restent ceux de la version à jour
                )
                compteRepository.mettreAJourCompte(aSauver).getOrThrow()
                aJour to aSauver
                }
                com.xburnsx.toutiebudget.ui.budget.BudgetEvents.onCompteUpdated() // la page Comptes se recharge sur cet événement

                val avertissement = if (etat.dette != null && abs(aJour.soldeDette - etat.dette.soldeDette) > 0.005) {
                    "Le solde a changé entre-temps (paiement ailleurs) : le solde réel a été conservé."
                } else null
                val formulaire = FormulaireDette.depuis(aSauver, aSauver.details.enveloppeId)
                if (detteIdCourante != id) return@lancerUneFois
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSaved = true, // l'écriture a réussi et le solde financier à jour est préservé
                        dette = aSauver,
                        formulaire = formulaire,
                        formulaireInitial = formulaire,
                        messageInfo = avertissement
                    )
                }
            } catch (e: Exception) {
                if (detteIdCourante != id) return@lancerUneFois
                // DF13 : on reste sur la page avec les saisies
                _uiState.update { it.copy(isSaving = false, messageErreur = "Échec de l'enregistrement : ${e.message}") }
            }
        }
    }

    /**
     * DA5 / DF7 : solde officiel confirmé, ou intérêts/frais réellement ajoutés par le prêteur.
     * Tracé dans l'historique ; jamais compté comme un paiement ni comme un revenu.
     */
    fun ajusterSolde(type: TypeAjustementDette, montantSaisi: Double, date: String, note: String?) {
        val id = _uiState.value.dette?.id ?: return
        val montant = runCatching {
            java.math.BigDecimal.valueOf(montantSaisi)
                .setScale(2, java.math.RoundingMode.UNNECESSARY).toDouble()
        }.getOrNull()
        val dateValide = MoteurCarteCredit.lireDate(date.trim()) != null
        if (montant == null || !montant.isFinite() || montant < 0.0 ||
            (type != TypeAjustementDette.SOLDE_OFFICIEL && montant <= 0.0) || !dateValide
        ) {
            _uiState.update { it.copy(messageErreur =
                "Montant au cent positif et date AAAA-MM-JJ valide requis pour l'ajustement.") }
            return
        }
        if (_uiState.value.isSaving) return

        viewModelScope.lancerUneFois(verrouDette) {
            _uiState.update { it.copy(isSaving = true, messageErreur = null) }
            try {
                // DF10 / DA5 : solde officiel, trace et SyncJobs dans un seul
                // commit Room. Une erreur du journal annule l'ajustement.
                val aSauver = operationAtomique.executer {
                    val aJour = compteRepository.getCompteById(id, "comptes_dettes") as? CompteDette
                        ?: throw IllegalStateException("Dette introuvable.")
                    val soldeAvant = aJour.soldeDette
                    if (!soldeAvant.isFinite()) throw IllegalStateException("Solde de dette invalide.")
                    val soldeApres = MoneyFormatter.roundAmount(
                        when (type) {
                            TypeAjustementDette.SOLDE_OFFICIEL -> -montant
                            TypeAjustementDette.INTERETS, TypeAjustementDette.FRAIS -> soldeAvant - montant
                        }
                    )
                    if (!soldeApres.isFinite()) throw IllegalStateException("Montant de dette trop élevé.")
                    val ajustement = AjustementDette(
                        date = date.trim(), type = type, soldeAvant = soldeAvant,
                        soldeApres = soldeApres, note = note?.trim()?.ifBlank { null }
                    )
                    val details = aJour.details.copy(
                        ajustements = aJour.details.ajustements + ajustement
                    )
                    val actualisee = aJour.copy(
                        soldeDette = soldeApres,
                        detailsDetteJson = details.versJson()
                    )
                    compteRepository.mettreAJourCompte(actualisee).getOrThrow()
                    historiqueAllocationService.enregistrerAjustementCompte(
                        compte = actualisee,
                        typeAction = "AJUSTEMENT_DETTE",
                        description = "${type.libelle} — ${actualisee.nom} : " +
                            "${MoneyFormatter.formatAmount(soldeAvant)} → " +
                            "${MoneyFormatter.formatAmount(soldeApres)}" +
                            (note?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                        montant = soldeApres - soldeAvant,
                        soldeAvant = soldeAvant,
                        soldeApres = soldeApres,
                        pretAPlacerAvant = 0.0,
                        pretAPlacerApres = 0.0
                    )
                    actualisee
                }
                com.xburnsx.toutiebudget.ui.budget.BudgetEvents.onCompteUpdated()
                _uiState.update { it.copy(
                    isSaving = false, dette = aSauver,
                    messageInfo = "Solde de la dette et historique mis à jour.",
                    ajustementReussiCompteur = it.ajustementReussiCompteur + 1
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isSaving = false,
                    messageErreur = "Échec de l'ajustement (aucune écriture partielle) : ${e.message}"
                ) }
            }
        }
    }

    fun reinitialiser() {
        generationChargementDette++
        detteIdCourante = null
        _uiState.value = DetteUiState()
    }

    fun effacerMessages() {
        _uiState.update { it.copy(messageErreur = null, messageInfo = null) }
    }

    private fun nombre(texte: String): Double? =
        texte.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() }
}

data class DetteUiState(
    val dette: CompteDette? = null,
    val transactions: List<Transaction> = emptyList(),
    val formulaire: FormulaireDette = FormulaireDette(),
    val formulaireInitial: FormulaireDette = FormulaireDette(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val erreurChargement: String? = null,
    val messageErreur: String? = null,
    val messageInfo: String? = null,
    val ajustementReussiCompteur: Int = 0
) {
    /** DF1 : changements non enregistrés (pour prévenir avant de quitter). */
    val aDesModifications: Boolean get() = formulaire.copy(erreurs = emptyMap()) != formulaireInitial.copy(erreurs = emptyMap())
}

/** Brouillon des paramètres (DF1). Les montants sont du texte saisi. */
data class FormulaireDette(
    val nom: String = "",
    val mode: ModeDette = ModeDette.SIMPLE,
    val montantInitial: String = "",
    val taux: String = "",
    val dureeMois: String = "",
    val versementPrevu: String = "",
    val dateDebut: String = "",
    val dateFin: String = "",
    val jourVersement: String = "",
    val tauxVariable: Boolean = false,
    val changementsTaux: List<ChangementTaux> = emptyList(),
    val enveloppeId: String? = null,
    val erreurs: Map<String, String> = emptyMap()
) {
    /** DF6 : aucune valeur négative, non numérique, NaN ou division par zéro n'est acceptée. */
    fun valider(): Map<String, String> {
        val e = mutableMapOf<String, String>()
        fun n(s: String) = s.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()
        if (nom.isBlank()) e["nom"] = "Nom requis"
        n(montantInitial).let { if (it == null || it < 0 || !it.isFinite()) e["montant"] = "Montant positif requis" }
        if (versementPrevu.isNotBlank()) n(versementPrevu).let { if (it == null || it < 0 || !it.isFinite()) e["versement"] = "Montant invalide" }
        if (mode == ModeDette.FINANCE) {
            n(taux).let { if (it == null || !it.isFinite() || it < 0 || it > 100) e["taux"] = "Taux entre 0 et 100 requis pour un prêt financé" }
            dureeMois.trim().toIntOrNull().let { if (it == null || it !in 1..600) e["duree"] = "Durée de 1 à 600 mois" }
        } else {
            if (taux.isNotBlank()) n(taux).let { if (it == null || it < 0 || it > 100) e["taux"] = "Taux entre 0 et 100" }
            if (dureeMois.isNotBlank() && dureeMois.trim().toIntOrNull() !in 1..600) e["duree"] = "Durée de 1 à 600 mois"
        }
        listOf("dateDebut" to dateDebut, "dateFin" to dateFin).forEach { (cle, v) ->
            if (v.isNotBlank() && MoteurCarteCredit.lireDate(v.trim()) == null) e[cle] = "Date AAAA-MM-JJ"
        }
        val debut = MoteurCarteCredit.lireDate(dateDebut.trim())
        val fin = MoteurCarteCredit.lireDate(dateFin.trim())
        if (debut != null && fin != null && fin.before(debut)) e["dateFin"] = "La fin précède le début"
        if (jourVersement.isNotBlank() && jourVersement.trim().toIntOrNull() !in 1..31) e["jour"] = "Jour entre 1 et 31"
        if (changementsTaux.any { c ->
                MoteurCarteCredit.lireDate(c.date) == null ||
                    !c.taux.isFinite() || c.taux !in 0.0..100.0
            } || changementsTaux.map { it.date }.distinct().size != changementsTaux.size) {
            e["taux"] = "Historique de taux invalide : vérifie chaque date et chaque taux (0 à 100 %)."
        }
        return e
    }

    companion object {
        fun depuis(dette: CompteDette, enveloppeId: String?): FormulaireDette {
            val d = dette.details
            fun t(v: Double?) = v?.let { MoneyFormatter.roundAmount(it).toString() } ?: ""
            return FormulaireDette(
                nom = dette.nom,
                mode = d.mode,
                montantInitial = t(dette.montantInitial),
                taux = dette.tauxInteret?.toString() ?: "",
                dureeMois = dette.dureeMoisPret?.toString() ?: "",
                versementPrevu = t(dette.paiementMinimum),
                dateDebut = d.dateDebut ?: "",
                dateFin = d.dateFin ?: "",
                jourVersement = d.jourVersement?.toString() ?: "",
                tauxVariable = d.tauxVariable,
                changementsTaux = d.changementsTaux,
                enveloppeId = enveloppeId
            )
        }
    }
}
