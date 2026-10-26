package com.xburnsx.toutiebudget.ui.cartes_credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.DetailsCarte
import com.xburnsx.toutiebudget.data.modeles.FraisMensuel
import com.xburnsx.toutiebudget.data.modeles.ReleveCarte
import com.xburnsx.toutiebudget.data.repositories.CarteCreditRepository
import com.xburnsx.toutiebudget.data.services.GenerateurAlertesCarte
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Gérer la carte (section 10). Tous les chiffres viennent de MoteurCarteCredit (CC1).
 * Un vrai paiement passe toujours par une transaction (raccourci vers Transactions) ;
 * rien ici ne modifie le solde « en cachette ».
 */
class CartesCreditViewModel(
    private val carteCreditRepository: CarteCreditRepository,
    private val realtimeSyncService: RealtimeSyncService,
    private val historiqueAllocationService: HistoriqueAllocationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartesCreditUiState())
    val uiState: StateFlow<CartesCreditUiState> = _uiState.asStateFlow()

    private val _formulaire = MutableStateFlow(FormulaireCarteCredit())
    val formulaire: StateFlow<FormulaireCarteCredit> = _formulaire.asStateFlow()

    init {
        chargerCartesCredit()
        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase pour les cartes de crédit
        viewModelScope.launch {
            realtimeSyncService.comptesUpdated.collectLatest {
                chargerCartesCredit()
            }
        }
    }

    // ===== Chargement =====

    fun chargerCartesCredit() {
        viewModelScope.launch {
            carteCreditRepository.recupererCartesCredit()
                .onSuccess { cartes ->
                    val selection = _uiState.value.carteSelectionnee?.let { s -> cartes.find { it.id == s.id } }
                    _uiState.update { it.copy(cartesCredit = cartes, carteSelectionnee = selection ?: it.carteSelectionnee) }
                    selection?.let { chargerTransactions(it) }
                }
                .onFailure { erreur ->
                    _uiState.update { it.copy(messageErreur = "Erreur lors du chargement: ${erreur.message}") }
                }
        }
    }

    fun chargerCarteSpecifique(carteCreditId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(estEnChargement = true, messageErreur = null) }
            carteCreditRepository.recupererCartesCredit()
                .onSuccess { cartes ->
                    val carte = cartes.find { it.id == carteCreditId }
                    _uiState.update { it.copy(cartesCredit = cartes, carteSelectionnee = carte, estEnChargement = false) }
                    carte?.let { chargerTransactions(it) }
                }
                .onFailure { erreur ->
                    _uiState.update { it.copy(estEnChargement = false, messageErreur = "Erreur lors du chargement: ${erreur.message}") }
                }
        }
    }

    private suspend fun chargerTransactions(carte: CompteCredit) {
        val transactions = carteCreditRepository.recupererHistoriqueTransactions(carte.id).getOrElse { emptyList() }
        _uiState.update {
            it.copy(
                transactionsCarte = transactions,
                alertes = GenerateurAlertesCarte.generer(carte, transactions)
            )
        }
    }

    fun selectionnerCarte(carte: CompteCredit) {
        _uiState.update { it.copy(carteSelectionnee = carte) }
        viewModelScope.launch { chargerTransactions(carte) }
    }

    // ===== Modification de la carte (CC3, CC4, CC7, CC10) =====

    fun afficherDialogModification(carte: CompteCredit) {
        val d = carte.details
        fun texte(v: Double?) = v?.let { MoneyFormatter.roundAmount(it).toString() } ?: ""
        _formulaire.value = FormulaireCarteCredit(
            nom = carte.nom,
            emetteur = d.emetteur,
            soldeActuel = texte(carte.solde), // SIGNÉ tel quel : -400.0 = dette de 400 $
            limiteCredit = texte(carte.limiteCredit),
            tauxInteret = carte.tauxInteret?.toString() ?: "",
            tauxAvances = d.tauxAvances?.toString() ?: "",
            tauxTransferts = d.tauxTransferts?.toString() ?: "",
            tauxPromo = d.tauxPromo?.toString() ?: "",
            finPromo = d.finPromo ?: "",
            tauxRetard = d.tauxRetard?.toString() ?: "",
            jourCloture = d.jourCloture?.toString() ?: "",
            joursAvantEcheance = d.joursAvantEcheance?.toString() ?: "",
            pourcentageMinimum = d.pourcentageMinimum?.toString() ?: "",
            plancherMinimum = texte(d.plancherMinimum),
            paiementMinimum = texte(carte.paiementMinimum),
            couleur = carte.couleur
        )
        _uiState.update { it.copy(carteSelectionnee = carte, afficherDialogModification = true) }
    }

    /** Toute modification du formulaire passe par ici ; la validation est refaite à chaque fois. */
    fun modifierFormulaire(changement: FormulaireCarteCredit.() -> FormulaireCarteCredit) {
        val f = _formulaire.value.changement()
        _formulaire.value = f.copy(erreurs = valider(f))
    }

    private fun nombre(texte: String): Double? = texte.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()

    private fun valider(f: FormulaireCarteCredit): Map<String, String> {
        val erreurs = mutableMapOf<String, String>()
        if (f.nom.isBlank()) erreurs["nom"] = "Le nom est requis"
        if (nombre(f.soldeActuel) == null) erreurs["solde"] = "Montant invalide (ex. -400,00)"
        nombre(f.limiteCredit).let { if (it == null || it <= 0) erreurs["limite"] = "Limite positive requise" }
        listOf(
            "taux" to f.tauxInteret, "tauxAvances" to f.tauxAvances, "tauxTransferts" to f.tauxTransferts,
            "tauxPromo" to f.tauxPromo, "tauxRetard" to f.tauxRetard, "pourcentage" to f.pourcentageMinimum
        ).forEach { (cle, v) ->
            if (v.isNotBlank()) nombre(v).let { if (it == null || it < 0 || it > 100) erreurs[cle] = "Taux entre 0 et 100" }
        }
        if (f.jourCloture.isNotBlank() && f.jourCloture.trim().toIntOrNull() !in 1..31) erreurs["cloture"] = "Jour entre 1 et 31"
        if (f.joursAvantEcheance.isNotBlank() && f.joursAvantEcheance.trim().toIntOrNull() !in 1..60) erreurs["delai"] = "Entre 1 et 60 jours"
        if (f.finPromo.isNotBlank() && MoteurCarteCredit.lireDate(f.finPromo.trim()) == null) erreurs["finPromo"] = "Date AAAA-MM-JJ"
        listOf("plancher" to f.plancherMinimum, "minimum" to f.paiementMinimum).forEach { (cle, v) ->
            if (v.isNotBlank()) nombre(v).let { if (it == null || it < 0) erreurs[cle] = "Montant invalide" }
        }
        return erreurs
    }

    fun sauvegarderModificationsCarteCredit() {
        val carteOriginale = _uiState.value.carteSelectionnee ?: return
        val f = _formulaire.value.let { it.copy(erreurs = valider(it)) }
        _formulaire.value = f
        if (!f.estValide || _uiState.value.enregistrementEnCours) return

        viewModelScope.launch {
            _uiState.update { it.copy(enregistrementEnCours = true) }
            val details = carteOriginale.details.copy(
                emetteur = f.emetteur,
                tauxAvances = nombre(f.tauxAvances),
                tauxTransferts = nombre(f.tauxTransferts),
                tauxPromo = nombre(f.tauxPromo),
                finPromo = f.finPromo.trim().ifBlank { null },
                tauxRetard = nombre(f.tauxRetard),
                jourCloture = f.jourCloture.trim().toIntOrNull(),
                joursAvantEcheance = f.joursAvantEcheance.trim().toIntOrNull(),
                pourcentageMinimum = nombre(f.pourcentageMinimum),
                plancherMinimum = nombre(f.plancherMinimum)
            )
            val nouveauSolde = MoneyFormatter.roundAmount(nombre(f.soldeActuel) ?: carteOriginale.solde)
            val carteModifiee = carteOriginale.copy(
                nom = f.nom.trim(),
                soldeUtilise = nouveauSolde,
                limiteCredit = nombre(f.limiteCredit) ?: carteOriginale.limiteCredit,
                tauxInteret = nombre(f.tauxInteret),
                paiementMinimum = nombre(f.paiementMinimum),
                couleur = f.couleur,
                detailsCarteJson = details.versJson()
            )

            carteCreditRepository.mettreAJourCarteCredit(carteModifiee)
                .onSuccess {
                    // CC3 : une correction du solde est journalisée, sans inventer de paiement bancaire
                    if (abs(nouveauSolde - carteOriginale.solde) > 0.005) {
                        try {
                            historiqueAllocationService.enregistrerAjustementCompte(
                                compte = carteModifiee,
                                typeAction = "CORRECTION_SOLDE_CARTE",
                                description = "Correction du solde de ${carteModifiee.nom} : " +
                                    "${MoneyFormatter.formatAmount(carteOriginale.solde)} → ${MoneyFormatter.formatAmount(nouveauSolde)}",
                                montant = nouveauSolde - carteOriginale.solde,
                                soldeAvant = carteOriginale.solde,
                                soldeApres = nouveauSolde,
                                pretAPlacerAvant = 0.0,
                                pretAPlacerApres = 0.0
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("ToutieBudget", "Trace de correction de carte non enregistrée: ${e.message}")
                        }
                    }
                    _uiState.update { it.copy(afficherDialogModification = false, enregistrementEnCours = false, carteSelectionnee = carteModifiee) }
                    chargerCartesCredit()
                    realtimeSyncService.declencherMiseAJourComptes()
                }
                .onFailure { erreur ->
                    // CO6 : le formulaire reste ouvert avec les valeurs
                    _uiState.update { it.copy(enregistrementEnCours = false, messageErreur = "Erreur lors de la modification: ${erreur.message}") }
                }
        }
    }

    // ===== Relevés officiels (CC4, CC6) =====

    fun ouvrirReleve(releve: ReleveCarte?) {
        _uiState.update {
            it.copy(releveEnEdition = releve ?: ReleveCarte(dateEmission = MoteurCarteCredit.ecrireDate(java.util.Date()), soldeOfficiel = 0.0))
        }
    }

    fun fermerReleve() {
        _uiState.update { it.copy(releveEnEdition = null) }
    }

    fun sauvegarderReleve(releve: ReleveCarte) {
        val carte = _uiState.value.carteSelectionnee ?: return
        val d = carte.details
        val releves = d.releves.filter { it.id != releve.id } + releve
        // Un même relevé (même date de clôture) n'est jamais conservé en double
        if (releves.count { it.dateEmission == releve.dateEmission } > 1) {
            _uiState.update { it.copy(messageErreur = "Un relevé du ${releve.dateEmission} existe déjà.") }
            return
        }
        enregistrerDetails(carte, d.copy(releves = releves)) { it.copy(releveEnEdition = null) }
    }

    fun supprimerReleve(releveId: String) {
        val carte = _uiState.value.carteSelectionnee ?: return
        val d = carte.details
        enregistrerDetails(carte, d.copy(releves = d.releves.filter { it.id != releveId })) { it.copy(releveEnEdition = null) }
    }

    /**
     * CC6 : n'augmente la dette que d'un montant CONFIRMÉ par le relevé, une seule fois,
     * et seulement à la demande (le solde saisi peut déjà les inclure).
     */
    fun comptabiliserInterets(releveId: String) {
        val carte = _uiState.value.carteSelectionnee ?: return
        val releve = carte.details.releves.find { it.id == releveId } ?: return
        if (releve.interetsComptabilises || releve.interetsFactures <= 0.005) return

        viewModelScope.launch {
            val nouveauSolde = MoneyFormatter.roundAmount(carte.solde - releve.interetsFactures)
            val details = carte.details.copy(
                releves = carte.details.releves.map { if (it.id == releveId) it.copy(interetsComptabilises = true) else it }
            )
            val carteMaj = carte.copy(soldeUtilise = nouveauSolde, detailsCarteJson = details.versJson())
            carteCreditRepository.mettreAJourCarteCredit(carteMaj)
                .onSuccess {
                    try {
                        historiqueAllocationService.enregistrerAjustementCompte(
                            compte = carteMaj,
                            typeAction = "INTERETS_FACTURES",
                            description = "Intérêts facturés au relevé du ${releve.dateEmission} : ${MoneyFormatter.formatAmount(releve.interetsFactures)}",
                            montant = -releve.interetsFactures,
                            soldeAvant = carte.solde,
                            soldeApres = nouveauSolde,
                            pretAPlacerAvant = 0.0,
                            pretAPlacerApres = 0.0
                        )
                    } catch (_: Exception) { }
                    _uiState.update { it.copy(carteSelectionnee = carteMaj, messageInfo = "Intérêts ajoutés au solde de la carte.") }
                    chargerCartesCredit()
                    realtimeSyncService.declencherMiseAJourComptes()
                }
                .onFailure { e -> _uiState.update { it.copy(messageErreur = "Erreur : ${e.message}") } }
        }
    }

    private fun enregistrerDetails(
        carte: CompteCredit,
        details: DetailsCarte,
        apresSucces: (CartesCreditUiState) -> CartesCreditUiState = { it }
    ) {
        viewModelScope.launch {
            val carteMaj = carte.copy(detailsCarteJson = details.versJson())
            carteCreditRepository.mettreAJourCarteCredit(carteMaj)
                .onSuccess {
                    _uiState.update { apresSucces(it.copy(carteSelectionnee = carteMaj)) }
                    chargerTransactions(carteMaj)
                    realtimeSyncService.declencherMiseAJourComptes()
                }
                .onFailure { e -> _uiState.update { it.copy(messageErreur = "Erreur : ${e.message}") } }
        }
    }

    // ===== Frais (CC10) =====

    fun afficherDialogModificationFrais(carte: CompteCredit) {
        _formulaire.value = _formulaire.value.copy(nomFrais = "", montantFrais = "", typeFrais = com.xburnsx.toutiebudget.data.modeles.TypeFrais.MENSUEL, dateFrais = "")
        _uiState.update { it.copy(carteSelectionnee = carte, afficherDialogModificationFrais = true) }
    }

    /** Ajoute un frais déclaré ; il n'est JAMAIS facturé automatiquement sur la carte. */
    fun sauvegarderModificationFrais() {
        val carte = _uiState.value.carteSelectionnee ?: return
        val f = _formulaire.value
        val montant = nombre(f.montantFrais)
        val date = f.dateFrais.trim().ifBlank { null }
        when {
            f.nomFrais.isBlank() -> { _uiState.update { it.copy(messageErreur = "Le nom du frais est requis") }; return }
            montant == null || montant < 0 -> { _uiState.update { it.copy(messageErreur = "Montant de frais invalide") }; return }
            date != null && MoteurCarteCredit.lireDate(date) == null -> { _uiState.update { it.copy(messageErreur = "Date AAAA-MM-JJ") }; return }
        }
        // Déduplication : même nom + même type remplace l'ancien au lieu de l'ajouter en double
        val frais = carte.fraisMensuels.filterNot { it.nom.equals(f.nomFrais.trim(), true) && it.type == f.typeFrais } +
            FraisMensuel(nom = f.nomFrais.trim(), montant = montant!!, type = f.typeFrais, dateApplication = date)
        enregistrerFrais(carte, frais) { it.copy(afficherDialogModificationFrais = false) }
    }

    fun supprimerFraisMensuel(carte: CompteCredit, frais: FraisMensuel) {
        enregistrerFrais(carte, carte.fraisMensuels.filter { it.id != frais.id })
    }

    private fun enregistrerFrais(
        carte: CompteCredit,
        frais: List<FraisMensuel>,
        apresSucces: (CartesCreditUiState) -> CartesCreditUiState = { it }
    ) {
        viewModelScope.launch {
            val gson = com.google.gson.Gson()
            val json = if (frais.isEmpty()) null else gson.toJsonTree(frais.toTypedArray())
            val carteMaj = carte.copy(fraisMensuelsJson = json)
            carteCreditRepository.mettreAJourCarteCredit(carteMaj)
                .onSuccess {
                    _uiState.update { apresSucces(it.copy(carteSelectionnee = carteMaj)) }
                    chargerCartesCredit()
                    realtimeSyncService.declencherMiseAJourComptes()
                }
                .onFailure { e -> _uiState.update { it.copy(messageErreur = "Erreur lors de la modification des frais: ${e.message}") } }
        }
    }

    // ===== Divers =====

    fun fermerDialogs() {
        _uiState.update {
            it.copy(afficherDialogModification = false, afficherDialogModificationFrais = false, releveEnEdition = null)
        }
    }

    fun effacerErreur() {
        _uiState.update { it.copy(messageErreur = null, messageInfo = null) }
    }
}
