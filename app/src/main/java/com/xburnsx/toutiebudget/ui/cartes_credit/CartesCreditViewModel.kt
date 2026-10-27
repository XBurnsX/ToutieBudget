package com.xburnsx.toutiebudget.ui.cartes_credit

import com.xburnsx.toutiebudget.utils.lancerUneFois
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
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Gérer la carte (section 10). Tous les chiffres viennent de MoteurCarteCredit (CC1).
 * Un vrai paiement passe toujours par une transaction (raccourci vers Transactions) ;
 * rien ici ne modifie le solde « en cachette ».
 */
class CartesCreditViewModel(
    private val carteCreditRepository: CarteCreditRepository,
    private val realtimeSyncService: RealtimeSyncService,
    private val historiqueAllocationService: HistoriqueAllocationService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouCarte = com.xburnsx.toutiebudget.utils.VerrouOperation()
    // Une classification rapide de plusieurs mouvements ne doit pas perdre le second clic.
    private val mutexClassification = Mutex()
    @Volatile private var generationCartes = 0L
    @Volatile private var generationHistorique = 0L

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
        val generation = ++generationCartes
        viewModelScope.launch {
            val resultat = carteCreditRepository.recupererCartesCredit()
            if (generation != generationCartes) return@launch
            resultat.onSuccess { cartes ->
                val selectionId = _uiState.value.carteSelectionnee?.id
                val selection = cartes.firstOrNull { it.id == selectionId }
                _uiState.update { courant ->
                    courant.copy(
                        cartesCredit = cartes,
                        carteSelectionnee = selection,
                        messageErreur = if (selectionId != null && selection == null)
                            "La carte sélectionnée n'existe plus." else courant.messageErreur,
                        transactionsCarte = if (selection == null) emptyList() else courant.transactionsCarte,
                        alertes = if (selection == null) emptyList() else courant.alertes
                    )
                }
                selection?.let { viewModelScope.launch { chargerTransactions(it) } }
            }.onFailure { erreur ->
                _uiState.update { it.copy(messageErreur = "Erreur lors du chargement : ${erreur.message}") }
            }
        }
    }

    fun chargerCarteSpecifique(carteCreditId: String) {
        val generation = ++generationCartes
        // Les transactions/alertes de la carte précédemment ouverte disparaissent tout de suite.
        generationHistorique++
        _uiState.update { it.copy(
            estEnChargement = true, carteSelectionnee = null,
            transactionsCarte = emptyList(), alertes = emptyList(), messageErreur = null
        ) }
        viewModelScope.launch {
            val resultat = carteCreditRepository.recupererCartesCredit()
            if (generation != generationCartes) return@launch
            resultat.onSuccess { cartes ->
                val carte = cartes.firstOrNull { it.id == carteCreditId }
                _uiState.update { it.copy(
                    cartesCredit = cartes,
                    carteSelectionnee = carte,
                    estEnChargement = false,
                    messageErreur = if (carte == null) "Carte introuvable ou archivée" else null
                ) }
                carte?.let { viewModelScope.launch { chargerTransactions(it) } }
            }.onFailure { erreur ->
                _uiState.update { it.copy(
                    estEnChargement = false, messageErreur = "Erreur lors du chargement : ${erreur.message}"
                ) }
            }
        }
    }

    private suspend fun chargerTransactions(carte: CompteCredit) {
        val generation = ++generationHistorique
        val resultat = carteCreditRepository.recupererHistoriqueTransactions(carte.id)
        if (generation != generationHistorique ||
            _uiState.value.carteSelectionnee?.id != carte.id) return
        resultat.onSuccess { transactions ->
            _uiState.update { courant ->
                if (courant.carteSelectionnee?.id != carte.id) courant
                else courant.copy(
                    transactionsCarte = transactions,
                    alertes = GenerateurAlertesCarte.generer(carte, transactions)
                )
            }
        }.onFailure { erreur ->
            _uiState.update { courant ->
                if (courant.carteSelectionnee?.id != carte.id) courant
                else courant.copy(
                    messageErreur = "Historique de carte indisponible : ${erreur.message}",
                    transactionsCarte = emptyList(), alertes = emptyList()
                )
            }
        }
    }

    fun selectionnerCarte(carte: CompteCredit) {
        generationCartes++
        generationHistorique++
        _uiState.update { it.copy(
            carteSelectionnee = carte,
            transactionsCarte = emptyList(), alertes = emptyList()
        ) }
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
            debutPromo = d.debutPromo ?: "",
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

    private fun nombre(texte: String): Double? = texte.trim().replace(" ", "").replace(',', '.')
        .toDoubleOrNull()?.takeIf { it.isFinite() }

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
        if (f.debutPromo.isNotBlank() && MoteurCarteCredit.lireDate(f.debutPromo.trim()) == null) erreurs["debutPromo"] = "Date AAAA-MM-JJ"
        if (f.finPromo.isNotBlank() && MoteurCarteCredit.lireDate(f.finPromo.trim()) == null) erreurs["finPromo"] = "Date AAAA-MM-JJ"
        val debutPromo = MoteurCarteCredit.lireDate(f.debutPromo.trim())
        val finPromo = MoteurCarteCredit.lireDate(f.finPromo.trim())
        if (debutPromo != null && finPromo != null && debutPromo.after(finPromo)) {
            erreurs["debutPromo"] = "Le début doit précéder la fin de la promotion"
        }
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

        viewModelScope.lancerUneFois(verrouCarte) {
            _uiState.update { it.copy(enregistrementEnCours = true) }
            val details = carteOriginale.details.copy(
                emetteur = f.emetteur,
                tauxAvances = nombre(f.tauxAvances),
                tauxTransferts = nombre(f.tauxTransferts),
                tauxPromo = nombre(f.tauxPromo),
                debutPromo = f.debutPromo.trim().ifBlank { null },
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

            try {
                operationAtomique.executer {
                    val actuelle = carteCreditRepository.recupererCartesCredit().getOrThrow()
                        .firstOrNull { it.id == carteOriginale.id }
                        ?: throw IllegalStateException("Carte introuvable ou archivée")
                    if (actuelle != carteOriginale) {
                        throw IllegalStateException("La carte a changé depuis l'ouverture du formulaire : actualise ses données.")
                    }
                    carteCreditRepository.mettreAJourCarteCredit(carteModifiee).getOrThrow()
                    // CC3 : correction manuelle + journal doivent réussir ensemble.
                    if (abs(nouveauSolde - carteOriginale.solde) > 0.005) {
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
                    }
                }
                _uiState.update { it.copy(afficherDialogModification = false,
                    enregistrementEnCours = false, carteSelectionnee = carteModifiee, messageErreur = null) }
                chargerCartesCredit()
                realtimeSyncService.declencherMiseAJourComptes()
            } catch (erreur: Exception) {
                // CC3 : laisser ouverts les champs et montrer l'erreur.
                _uiState.update { it.copy(enregistrementEnCours = false,
                    messageErreur = "Erreur lors de la modification : ${erreur.message}") }
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
        val ancien = d.releves.firstOrNull { it.id == releve.id }
        // Un statut « comptabilisé » ne peut être forgé par une édition de relevé,
        // ni annulé en oubliant une écriture déjà envoyée dans Room.
        if ((ancien?.interetsComptabilises ?: false) != releve.interetsComptabilises ||
            (ancien?.fraisComptabilises ?: false) != releve.fraisComptabilises ||
            (ancien?.interetsDejaInclus ?: false) != releve.interetsDejaInclus ||
            (ancien?.fraisDejaInclus ?: false) != releve.fraisDejaInclus ||
            ((ancien?.interetsComptabilises == true || ancien?.interetsDejaInclus == true) &&
                (releve.interetsFactures != ancien.interetsFactures ||
                    releve.dateEmission != ancien.dateEmission)) ||
            ((ancien?.fraisComptabilises == true || ancien?.fraisDejaInclus == true) &&
                (releve.fraisFactures != ancien.fraisFactures ||
                    releve.dateEmission != ancien.dateEmission))
        ) {
            _uiState.update { it.copy(messageErreur =
                "Ce relevé comporte déjà des intérêts ou frais comptabilisés. " +
                    "Les montants facturés liés à une écriture doivent être corrigés via une opération comptable tracée.") }
            return
        }
        val emission = MoteurCarteCredit.lireDate(releve.dateEmission)
        val echeance = MoteurCarteCredit.lireDate(releve.dateEcheance)
        if (emission == null ||
            (releve.dateEcheance != null && echeance == null) ||
            (emission != null && echeance != null && echeance.before(emission)) ||
            listOfNotNull(releve.soldeOfficiel, releve.minimumOfficiel, releve.interetsFactures,
                releve.fraisFactures, releve.paiementsRecus).any { !it.isFinite() || it < 0.0 }
        ) {
            _uiState.update { it.copy(messageErreur = "Date ou montant officiel de relevé invalide.") }
            return
        }
        val releves = d.releves.filter { it.id != releve.id } + releve
        if (releves.count { it.dateEmission == releve.dateEmission } > 1) {
            _uiState.update { it.copy(messageErreur = "Un relevé du ${releve.dateEmission} existe déjà.") }
            return
        }
        enregistrerDetails(carte, d.copy(releves = releves)) { it.copy(releveEnEdition = null) }
    }

    fun supprimerReleve(releveId: String) {
        val carte = _uiState.value.carteSelectionnee ?: return
        val d = carte.details
        val ancien = d.releves.firstOrNull { it.id == releveId } ?: return
        if (ancien.interetsComptabilises || ancien.fraisComptabilises ||
            ancien.interetsDejaInclus || ancien.fraisDejaInclus) {
            _uiState.update { it.copy(messageErreur =
                "Suppression bloquée : ce relevé comporte des intérêts ou frais déjà comptabilisés.") }
            return
        }
        enregistrerDetails(carte, d.copy(releves = d.releves.filter { it.id != releveId })) {
            it.copy(releveEnEdition = null)
        }
    }

    /** CC6/CC10 : frais et intérêts sont enregistrés uniquement sur confirmation explicite. */
    fun comptabiliserInterets(releveId: String) = comptabiliserMontantReleve(releveId, estInteret = true)
    fun comptabiliserFrais(releveId: String) = comptabiliserMontantReleve(releveId, estInteret = false)
    fun marquerMontantDejaInclus(releveId: String, estInteret: Boolean) =
        comptabiliserMontantReleve(releveId, estInteret, dejaInclus = true)

    private fun comptabiliserMontantReleve(
        releveId: String,
        estInteret: Boolean,
        dejaInclus: Boolean = false
    ) {
        val carteSelectionnee = _uiState.value.carteSelectionnee ?: return
        viewModelScope.lancerUneFois(verrouCarte) {
            try {
                // CC6 : vérifier les flags et actualiser le solde et le journal
                // dans UNE transaction Room. Un deuxième clic échoue proprement.
                val carteMaj = operationAtomique.executer {
                    val carte = carteCreditRepository.recupererCartesCredit().getOrThrow()
                        .firstOrNull { it.id == carteSelectionnee.id }
                        ?: throw IllegalStateException("Carte introuvable")
                    val releve = carte.details.releves.firstOrNull { it.id == releveId }
                        ?: throw IllegalStateException("Relevé introuvable")
                    val deja = if (estInteret)
                        releve.interetsComptabilises || releve.interetsDejaInclus
                    else releve.fraisComptabilises || releve.fraisDejaInclus
                    if (deja) throw IllegalStateException(
                        if (estInteret) "Intérêts déjà comptabilisés." else "Frais déjà comptabilisés."
                    )
                    val montant = if (estInteret) releve.interetsFactures else releve.fraisFactures
                    if (!montant.isFinite() || montant <= 0.005) {
                        throw IllegalArgumentException("Aucun montant officiel valide à comptabiliser.")
                    }
                    if (!carte.solde.isFinite()) throw IllegalStateException("Solde de carte invalide")
                    val nouveauSolde = MoneyFormatter.roundAmount(
                        carte.solde - if (dejaInclus) 0.0 else montant
                    )
                    if (!nouveauSolde.isFinite()) throw IllegalStateException("Solde de carte invalide")
                    val details = carte.details.copy(
                        releves = carte.details.releves.map { r ->
                            if (r.id != releveId) r
                            else when {
                                estInteret && dejaInclus -> r.copy(interetsDejaInclus = true)
                                estInteret -> r.copy(interetsComptabilises = true)
                                dejaInclus -> r.copy(fraisDejaInclus = true)
                                else -> r.copy(fraisComptabilises = true)
                            }
                        }
                    )
                    val corrigee = carte.copy(soldeUtilise = nouveauSolde, detailsCarteJson = details.versJson())
                    carteCreditRepository.mettreAJourCarteCredit(corrigee).getOrThrow()
                    historiqueAllocationService.enregistrerAjustementCompte(
                        compte = corrigee,
                        typeAction = if (dejaInclus)
                            (if (estInteret) "INTERETS_DEJA_INCLUS" else "FRAIS_DEJA_INCLUS")
                        else (if (estInteret) "INTERETS_FACTURES" else "FRAIS_FACTURES"),
                        description = (if (estInteret) "Intérêts" else "Frais") +
                            " facturés au relevé du ${releve.dateEmission} : ${MoneyFormatter.formatAmount(montant)}" +
                            (if (dejaInclus) " (déjà inclus dans le solde, sans débit)" else ""),
                        montant = if (dejaInclus) 0.0 else -montant,
                        soldeAvant = carte.solde,
                        soldeApres = nouveauSolde,
                        pretAPlacerAvant = 0.0,
                        pretAPlacerApres = 0.0
                    )
                    corrigee
                }
                _uiState.update { courant ->
                    if (courant.carteSelectionnee?.id != carteMaj.id) courant else courant.copy(
                        carteSelectionnee = carteMaj,
                        messageInfo = if (dejaInclus)
                            "Montant identifié comme déjà inclus : aucun solde modifié."
                        else if (estInteret) "Intérêts officiels comptabilisés."
                        else "Frais officiels comptabilisés.",
                        messageErreur = null
                    )
                }
                chargerCartesCredit()
                realtimeSyncService.declencherMiseAJourComptes()
            } catch (erreur: Exception) {
                _uiState.update { it.copy(messageErreur = "Échec de comptabilisation : ${erreur.message}") }
            }
        }
    }

    private fun enregistrerDetails(
        carte: CompteCredit,
        details: DetailsCarte,
        apresSucces: (CartesCreditUiState) -> CartesCreditUiState = { it }
    ) {
        viewModelScope.lancerUneFois(verrouCarte) {
            try {
                val carteMaj = operationAtomique.executer {
                    val actuelle = carteCreditRepository.recupererCartesCredit().getOrThrow()
                        .firstOrNull { it.id == carte.id } ?: throw IllegalStateException("Carte introuvable")
                    if (actuelle != carte) {
                        throw IllegalStateException(
                            "La carte ou ses relevés ont changé. Recharge les données avant de sauvegarder."
                        )
                    }
                    val maj = actuelle.copy(detailsCarteJson = details.versJson())
                    carteCreditRepository.mettreAJourCarteCredit(maj).getOrThrow()
                    maj
                }
                _uiState.update { courant ->
                    if (courant.carteSelectionnee?.id != carteMaj.id) courant
                    else apresSucces(courant.copy(carteSelectionnee = carteMaj, messageErreur = null))
                }
                if (_uiState.value.carteSelectionnee?.id == carteMaj.id) chargerTransactions(carteMaj)
                realtimeSyncService.declencherMiseAJourComptes()
            } catch (e: Exception) {
                _uiState.update { it.copy(messageErreur = "Erreur : ${e.message}") }
            }
        }
    }

    // ===== Ventilation du solde courant pour les intérêts différenciés (CC5) =====

    fun enregistrerVentilationCourante(achatsTexte: String, avancesTexte: String, transfertsTexte: String) {
        val carte = _uiState.value.carteSelectionnee ?: return
        val montants = listOf(achatsTexte, avancesTexte, transfertsTexte)
            .map { nombre(it) }
        if (montants.any { it == null || it < 0.0 }) {
            _uiState.update { it.copy(messageErreur =
                "Les trois montants doivent être des nombres positifs ou zéro.") }
            return
        }
        val valeurs = montants.map { MoneyFormatter.roundAmount(it!!) }
        if (kotlin.math.abs(valeurs.sum() - MoteurCarteCredit.dette(carte)) > 0.005) {
            _uiState.update { it.copy(messageErreur =
                "Les montants ventilés doivent totaliser exactement " +
                    MoneyFormatter.formatAmount(MoteurCarteCredit.dette(carte)) +
                    ", soit le montant dû actuellement.") }
            return
        }
        val ventilation = com.xburnsx.toutiebudget.data.modeles.VentilationSoldeCarte(
            dateReference = MoteurCarteCredit.ecrireDate(java.util.Date()),
            achats = valeurs[0], avances = valeurs[1], transferts = valeurs[2]
        )
        enregistrerDetails(carte, carte.details.copy(ventilationCourante = ventilation))
    }

    fun effacerVentilationCourante() {
        val carte = _uiState.value.carteSelectionnee ?: return
        enregistrerDetails(carte, carte.details.copy(ventilationCourante = null))
    }

    // ===== Qualification des mouvements pour la simulation (CC5) =====

    /**
     * La banque peut appliquer des taux différents selon la nature du mouvement.
     * Ce choix ne modifie ni le paiement, ni le montant, ni le solde réel du compte.
     * Une ligne non identifiée ne doit jamais être traitée implicitement en avance de fonds.
     */
    fun definirNatureMouvement(
        transactionId: String,
        nature: com.xburnsx.toutiebudget.data.modeles.NatureMouvementCarte?
    ) {
        val carte = _uiState.value.carteSelectionnee ?: return
        val transaction = _uiState.value.transactionsCarte.firstOrNull { it.id == transactionId }
        if (transaction == null) {
            _uiState.update { it.copy(messageErreur = "Transaction introuvable : recharge l'historique.") }
            return
        }
        val carteId = carte.id
        viewModelScope.launch {
            mutexClassification.withLock {
                try {
                    val miseAJour = operationAtomique.executer {
                        val actuelle = carteCreditRepository.recupererCartesCredit().getOrThrow()
                            .firstOrNull { it.id == carteId }
                            ?: throw IllegalStateException("Carte introuvable.")
                        // Relire à chaque clic pour conserver aussi le classement précédent.
                        val natures = actuelle.details.naturesMouvements.toMutableMap()
                        if (nature == null) natures.remove(transactionId)
                        else natures[transactionId] = nature
                        val maj = actuelle.copy(
                            detailsCarteJson = actuelle.details.copy(naturesMouvements = natures).versJson()
                        )
                        carteCreditRepository.mettreAJourCarteCredit(maj).getOrThrow()
                        maj
                    }
                    _uiState.update { courant ->
                        if (courant.carteSelectionnee?.id == carteId)
                            courant.copy(carteSelectionnee = miseAJour, messageErreur = null)
                        else courant
                    }
                    realtimeSyncService.declencherMiseAJourComptes()
                } catch (e: Exception) {
                    _uiState.update { it.copy(messageErreur =
                        "Impossible de classer le mouvement : ${e.message}") }
                }
            }
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
        // CC10 : seul le même frais À LA MÊME DATE est un doublon.
        // Ne pas supprimer l'historique d'un frais au même nom, mais d'une autre période.
        val existant = carte.fraisMensuels.firstOrNull {
            it.nom.equals(f.nomFrais.trim(), true) && it.type == f.typeFrais && it.dateApplication == date
        }
        val frais = carte.fraisMensuels.filterNot { it.id == existant?.id } + FraisMensuel(
            id = existant?.id ?: com.xburnsx.toutiebudget.utils.IdGenerator.generateId(),
            nom = f.nomFrais.trim(), montant = montant!!, type = f.typeFrais, dateApplication = date
        )
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
        viewModelScope.lancerUneFois(verrouCarte) {
            try {
                val carteMaj = operationAtomique.executer {
                    val actuelle = carteCreditRepository.recupererCartesCredit().getOrThrow()
                        .firstOrNull { it.id == carte.id }
                        ?: throw IllegalStateException("Carte introuvable.")
                    if (actuelle != carte) {
                        throw IllegalStateException(
                            "Les frais ou la carte ont changé depuis l'ouverture; recharge avant de modifier."
                        )
                    }
                    val gson = com.google.gson.Gson()
                    val json = if (frais.isEmpty()) null else gson.toJsonTree(frais.toTypedArray())
                    val maj = actuelle.copy(fraisMensuelsJson = json)
                    carteCreditRepository.mettreAJourCarteCredit(maj).getOrThrow()
                    maj
                }
                _uiState.update { courant ->
                    if (courant.carteSelectionnee?.id != carteMaj.id) courant
                    else apresSucces(courant.copy(carteSelectionnee = carteMaj, messageErreur = null))
                }
                chargerCartesCredit()
                realtimeSyncService.declencherMiseAJourComptes()
            } catch (e: Exception) {
                _uiState.update { it.copy(messageErreur = "Erreur de modification des frais : ${e.message}") }
            }
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
