package com.xburnsx.toutiebudget.ui.ajout_transaction

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.usecases.EnregistrerTransactionUseCase
import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionTransaction
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.historique.HistoriqueNavigationEvent
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
import java.util.Date

/**
 * ViewModel pour l'écran de modification de transaction.
 * Gère la logique de modification d'une transaction existante.
 */
@Suppress("REDUNDANT_ELSE_IN_WHEN")
class ModifierTransactionViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val tiersRepository: TiersRepository,
    private val transactionRepository: TransactionRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase,
    private val supprimerTransactionUseCase: SupprimerTransactionUseCase,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) : ViewModel() {

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouModification = com.xburnsx.toutiebudget.utils.VerrouOperation()

    private val _uiState = MutableStateFlow(AjoutTransactionUiState())
    val uiState: StateFlow<AjoutTransactionUiState> = _uiState.asStateFlow()

    // Événements de navigation
    private val _navigationEvents = MutableStateFlow<HistoriqueNavigationEvent?>(null)
    val navigationEvents: StateFlow<HistoriqueNavigationEvent?> = _navigationEvents.asStateFlow()

    // Cache des données
    private var allComptes: List<Compte> = emptyList()
    private var allEnveloppes: List<Enveloppe> = emptyList()
    private var allAllocations: List<AllocationMensuelle> = emptyList()
    private var allCategories: List<Categorie> = emptyList()
    private var allTiers: List<Tiers> = emptyList()
    private var transactionAModifier: Transaction? = null

    /**
     * Charge une transaction existante et remplit le formulaire.
     */
    fun chargerTransaction(transactionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageErreur = null) }
            
            try {
                // Charger la transaction
                val resultTransaction = transactionRepository.recupererTransactionParId(transactionId)
                if (resultTransaction.isFailure) {
                    throw Exception("Erreur lors du chargement de la transaction: ${resultTransaction.exceptionOrNull()?.message}")
                }
                
                transactionAModifier = resultTransaction.getOrNull()
                if (transactionAModifier == null) {
                    throw Exception("Transaction non trouvée")
                }

                // Charger toutes les données nécessaires
                val resultComptes = compteRepository.recupererTousLesComptes()
                val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                val resultCategories = categorieRepository.recupererToutesLesCategories()
                val resultTiers = tiersRepository.recupererTousLesTiers()
                
                // Charger les allocations pour le mois de la transaction ET le mois actuel
                val resultAllocationsTransaction = enveloppeRepository.recupererAllocationsPourMois(transactionAModifier!!.date)
                val resultAllocationsActuel = enveloppeRepository.recupererAllocationsPourMois(Date())

                // Vérifier les erreurs
                if (resultComptes.isFailure) {
                    throw Exception("Erreur lors du chargement des comptes: ${resultComptes.exceptionOrNull()?.message}")
                }
                if (resultEnveloppes.isFailure) {
                    throw Exception("Erreur lors du chargement des enveloppes: ${resultEnveloppes.exceptionOrNull()?.message}")
                }
                if (resultCategories.isFailure) {
                    throw Exception("Erreur lors du chargement des catégories: ${resultCategories.exceptionOrNull()?.message}")
                }
                if (resultAllocationsTransaction.isFailure) {
                    throw Exception("Erreur lors du chargement des allocations de la transaction: ${resultAllocationsTransaction.exceptionOrNull()?.message}")
                }
                if (resultAllocationsActuel.isFailure) {
                    throw Exception("Erreur lors du chargement des allocations actuelles: ${resultAllocationsActuel.exceptionOrNull()?.message}")
                }

                // Stocker les données
                allComptes = resultComptes.getOrNull() ?: emptyList()
                allEnveloppes = resultEnveloppes.getOrNull() ?: emptyList()
                allCategories = resultCategories.getOrNull() ?: emptyList()
                allTiers = resultTiers.getOrNull() ?: emptyList()
                
                // Combiner les allocations des deux mois
                val allocationsTransaction = resultAllocationsTransaction.getOrNull() ?: emptyList()
                val allocationsActuel = resultAllocationsActuel.getOrNull() ?: emptyList()
                allAllocations = (allocationsTransaction + allocationsActuel).distinctBy { it.id }

                // Construire les enveloppes UI
                val enveloppesUi = construireEnveloppesUi()
                
                val enveloppesGroupees = OrganisationEnveloppesUtils.organiserEnveloppesParCategorie(allCategories, allEnveloppes)
                val enveloppesFiltrees = enveloppesGroupees.mapValues { (_, enveloppesCategorie) ->
                    enveloppesUi.filter { enveloppeUi ->
                        enveloppesCategorie.any { it.id == enveloppeUi.id }
                    }.sortedBy { enveloppeUi ->
                        enveloppesCategorie.indexOfFirst { it.id == enveloppeUi.id }
                    }
                }

                // Remplir le formulaire avec les données de la transaction
                remplirFormulaireAvecTransaction(transactionAModifier!!)

                // Mettre à jour l'état
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        comptesDisponibles = allComptes,
                        enveloppesFiltrees = enveloppesFiltrees,
                        tiersDisponibles = allTiers,
                        messageErreur = null
                    )
                }
                
                // Si l'enveloppe n'a pas été trouvée, essayer de la charger séparément
                val allocationId = transactionAModifier!!.allocationMensuelleId
                if (allocationId != null && 
                    allAllocations.none { it.id == allocationId }) {
                    chargerEnveloppeManquante(allocationId)
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        messageErreur = e.message ?: "Erreur inconnue"
                    )
                }
            }
        }
    }

    /**
     * Remplit le formulaire avec les données de la transaction existante.
     */
    private fun remplirFormulaireAvecTransaction(transaction: Transaction) {
        // Parser les fractions si c'est une transaction fractionnée
        val fractionsInitiales = if (transaction.estFractionnee && !transaction.sousItems.isNullOrBlank()) {
            try {
                val jsonArray = com.google.gson.JsonParser.parseString(transaction.sousItems).asJsonArray
                jsonArray.mapNotNull { element ->
                    val obj = element.asJsonObject
                    val montantEnDollars = obj.get("montant")?.asDouble ?: 0.0
                    val montant = kotlin.math.round(montantEnDollars * 100).toDouble()
                    val enveloppeId = obj.get("enveloppeId")?.asString ?: ""
                    val note = obj.get("note")?.asString ?: ""
                    
                    FractionTransaction(
                        id = obj.get("id")?.asString ?: "fraction_${jsonArray.indexOf(element) + 1}",
                        enveloppeId = enveloppeId,
                        montant = montant, // Déjà en Double
                        note = note // MT17 : la note de la fraction, jamais remplacée par le nom de l'enveloppe
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        _uiState.update { state ->
            state.copy(
                typeTransaction = transaction.type,
                modeOperation = when (transaction.type) {
                    TypeTransaction.Pret, TypeTransaction.RemboursementRecu -> "Prêt"
                    TypeTransaction.Emprunt, TypeTransaction.RemboursementDonne -> "Emprunt"
                    TypeTransaction.Paiement, TypeTransaction.PaiementEffectue -> "Paiement"
                    else -> "Standard"
                },
                montant = Math.round(transaction.montant * 100).toString(), // GN5 : centimes exacts (19,99 $ -> 1999)
                compteSelectionne = allComptes.find { it.id == transaction.compteId },
                enveloppeSelectionnee = if (transaction.allocationMensuelleId != null && !transaction.estFractionnee) {
                    // Essayer de trouver l'allocation dans les allocations chargées
                    allAllocations.find { it.id == transaction.allocationMensuelleId }?.let { allocation ->
                        allEnveloppes.find { enveloppe -> enveloppe.id == allocation.enveloppeId }?.let { construireEnveloppeUi(it) }
                    }
                } else null,
                tiersUtiliser = transaction.tiersUtiliser ?: "",
                note = transaction.note ?: "",
                dateTransaction = transaction.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                estFractionnee = transaction.estFractionnee,
                fractionnementEffectue = transaction.estFractionnee,
                fractionsSauvegardees = fractionsInitiales
            ).calculerValidite() // ✅ Ajouter calculerValidite() ici
        }
    }

    /**
     * Construit une EnveloppeUi à partir d'une Enveloppe.
     */
    private fun construireEnveloppeUi(enveloppe: Enveloppe): EnveloppeUi {
        val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
        val compteSource = allocation?.compteSourceId?.let { compteId ->
            allComptes.find { it.id == compteId }
        }
        
        return EnveloppeUi(
            id = enveloppe.id,
            nom = enveloppe.nom,
            solde = allocation?.solde ?: 0.0,
            depense = allocation?.depense ?: 0.0,
            alloue = allocation?.alloue ?: 0.0, // Alloué ce mois
            alloueCumulatif = allocation?.alloue ?: 0.0, // ← NOUVEAU : Pour simplifier, on utilise la même valeur
            objectif = enveloppe.objectifMontant,
            couleurProvenance = compteSource?.couleur,
            statutObjectif = com.xburnsx.toutiebudget.ui.budget.StatutObjectif.GRIS
        )
    }

    /**
     * Modifie la transaction avec les nouvelles données.
     */
    fun modifierTransaction() {
        val state = _uiState.value
        
        if (transactionAModifier == null) {
            _uiState.update { it.copy(messageErreur = "Aucune transaction à modifier") }
            return
        }

        val montantEnCentimes = state.montant.toLongOrNull()
        if (montantEnCentimes == null || montantEnCentimes <= 0) {
            _uiState.update { it.copy(messageErreur = "Montant invalide") }
            return
        }
        
        // Convertir les centimes en dollars pour le use case
        val montantEnDollars = MoneyFormatter.roundAmount(montantEnCentimes / 100.0)

        if (state.compteSelectionne == null) {
            _uiState.update { it.copy(messageErreur = "Veuillez sélectionner un compte") }
            return
        }

        viewModelScope.lancerUneFois(verrouModification) {
            _uiState.update { it.copy(estEnTrainDeSauvegarder = true, messageErreur = null) }
            
            try {
                operationAtomique.executer {
                val collectionCompte = when (state.compteSelectionne) {
                    is com.xburnsx.toutiebudget.data.modeles.CompteCheque -> "comptes_cheques"
                    is com.xburnsx.toutiebudget.data.modeles.CompteCredit -> "comptes_credits"
                    is com.xburnsx.toutiebudget.data.modeles.CompteDette -> "comptes_dettes"
                    is com.xburnsx.toutiebudget.data.modeles.CompteInvestissement -> "comptes_investissement"
                    else -> "comptes_cheques"
                }
                
                val original = transactionAModifier!!
                // DF9/MT4 : interdire aussi la modification « note seulement »
                // d'une moitié d'un paiement lié; les deux écritures doivent rester ensemble.
                verifierModifiable(original)
                // MT18 : préserver l'heure d'origine, même quand seul le jour change.
                val originalZoned = original.date.toInstant().atZone(ZoneId.systemDefault())
                val dateTransaction = if (state.dateTransaction == originalZoned.toLocalDate()) original.date
                else Date.from(state.dateTransaction.atTime(originalZoned.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant())

                // MA2 : une correction de libellé/note ne déplace aucun dollar.
                val metadataSeulement = state.typeTransaction == original.type &&
                    kotlin.math.abs(montantEnDollars - original.montant) < 0.005 &&
                    state.compteSelectionne.id == original.compteId && dateTransaction == original.date &&
                    state.fractionnementEffectue == original.estFractionnee &&
                    (!original.estFractionnee || state.fractionsSauvegardees == parserFractions(original)) &&
                    allocationIdPour(state.enveloppeSelectionnee?.id, original.date) == original.allocationMensuelleId
                if (metadataSeulement) {
                    transactionRepository.mettreAJourTransaction(
                        original.copy(
                            tiersUtiliser = state.tiersUtiliser.takeIf { it.isNotBlank() },
                            note = state.note.takeIf { it.isNotBlank() },
                            updated = Date()
                        )
                    ).getOrThrow()
                    terminerModification()
                    return@executer
                }
                
                // ÉTAPE 1: Le remboursement est maintenant géré par le SupprimerTransactionUseCase
                // Plus besoin de rembourser manuellement les allocations

                // ÉTAPE 2: Supprimer l'ancienne transaction avec remboursement correct
                if (state.fractionnementEffectue) {
                    val fractions = state.fractionsSauvegardees
                    if (fractions.isEmpty() || fractions.any { it.enveloppeId.isBlank() || it.montant <= 0.0 }) {
                        throw Exception("Chaque fraction doit avoir une enveloppe et un montant positif.")
                    }
                    if (fractions.sumOf { kotlin.math.round(it.montant).toLong() } != montantEnCentimes) {
                        throw Exception("La somme des fractions doit être exactement égale au montant.")
                    }
                    fractions.forEach { fraction ->
                        val env = allEnveloppes.firstOrNull { it.id == fraction.enveloppeId }
                        if (env == null || env.estArchive) throw Exception("Une enveloppe du fractionnement n'est plus disponible.")
                    }
                }
                val resultSuppression = supprimerTransactionUseCase.executer(original.id)
                if (resultSuppression.isFailure) {
                    throw resultSuppression.exceptionOrNull() ?: Exception("Erreur lors de la suppression de l'ancienne transaction")
                }

                try {
                // ÉTAPE 3: Créer une nouvelle transaction avec les nouveaux détails
                if (state.fractionnementEffectue && state.fractionsSauvegardees.isNotEmpty()) {
                    // Créer une nouvelle transaction fractionnée
                    val fractions = state.fractionsSauvegardees
                    
                    // Convertir les fractions en JSON pour sousItems
                    val sousItems = fractions.mapIndexed { index, fraction ->
                        // Récupérer ou créer l'allocation mensuelle pour cette enveloppe
                        val allocationActuelle = allocationMensuelleRepository.recupererOuCreerAllocation(
                            enveloppeId = fraction.enveloppeId,
                            mois = dateTransaction
                        )
                        
                        // Le montant est en centimes, on le convertit en dollars pour le JSON
                        val montantEnDollars = fraction.montant / 100.0
                        
                        // Mettre à jour l'allocation avec le compte source si pas déjà défini
                        if (allocationActuelle.compteSourceId == null) {
                            val allocationMiseAJour = allocationActuelle.copy(
                                compteSourceId = state.compteSelectionne.id,
                                collectionCompteSource = collectionCompte
                            )
                            allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)
                        }
                        
                        // Récupérer le nom de l'enveloppe pour la description
                        val nomEnveloppe = allEnveloppes.find { it.id == fraction.enveloppeId }?.nom ?: "Enveloppe"
                        
                                                 mapOf(
                             "id" to fraction.id.ifBlank { "fraction_${index + 1}" },
                             "description" to nomEnveloppe,
                             "enveloppeId" to fraction.enveloppeId,
                             "enveloppe_id" to fraction.enveloppeId, // AT9 : clé canonique
                             "montant" to montantEnDollars,
                             "allocation_mensuelle_id" to allocationActuelle.id,
                             "transactionParenteId" to null,
                             "note" to fraction.note.trim() // AT10
                         )
                    }
                    
                    val gson = com.google.gson.Gson()
                    val sousItemsJson = gson.toJson(sousItems)

                    // Créer une nouvelle transaction avec estFractionnee = true
                    val result = enregistrerTransactionUseCase.executer(
                        id = original.id, // MT1 : la transaction garde son ID
                         typeTransaction = state.typeTransaction,
                         montant = montantEnDollars, // Utiliser montantEnDollars, pas montantEnCentimes
                         compteId = state.compteSelectionne.id,
                         collectionCompte = collectionCompte,
                         enveloppeId = null, // Pas d'enveloppe pour la transaction principale
                         tiersUtiliser = state.tiersUtiliser.takeIf { it.isNotBlank() } ?: "Transaction fractionnée",
                         note = state.note.takeIf { it.isNotBlank() },
                         date = dateTransaction,
                         estFractionnee = true,
                         sousItems = sousItemsJson
                     )

                    if (result.isSuccess) {
                        preserverMetadonneesOriginales(original)
                        // Mettre à jour les allocations mensuelles en utilisant les données du JSON
                        for (sousItem in sousItems) {
                            val allocationId = sousItem["allocation_mensuelle_id"] as? String
                                                         val montantEnDollars = (sousItem["montant"] as? Double) ?: 0.0
                            
                            if (allocationId != null) {
                                // Récupérer l'allocation par son ID (frais depuis la base de données)
                                val resultAllocation = enveloppeRepository.recupererAllocationParId(allocationId)
                            
                                // MT2/MT9 : une allocation introuvable ne peut pas être ignorée.
                                // L'opération atomique doit échouer plutôt que sauvegarder
                                // une transaction sans les bons montants d'enveloppes.
                                val allocationActuelle = resultAllocation.getOrThrow()
                                run {
                                    // AT8 : sur carte de crédit, le solde de l'enveloppe ne bouge pas (dépensé et alloué +montant)
                                    val nouvelleAllocation = if (collectionCompte == "comptes_credits") {
                                        allocationActuelle.copy(depense = allocationActuelle.depense + montantEnDollars, alloue = allocationActuelle.alloue + montantEnDollars)
                                    } else {
                                        allocationActuelle.copy(depense = allocationActuelle.depense + montantEnDollars, solde = allocationActuelle.solde - montantEnDollars)
                                    }
                                    
                                    allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocation)
                                }
                            }
                        }
                        
                        _uiState.update { 
                            it.copy(
                                estEnTrainDeSauvegarder = false,
                                messageErreur = null,
                                transactionModifiee = true
                            )
                        }
                        
                        // Déclencher l'événement de navigation pour indiquer que la transaction a été modifiée
                        _navigationEvents.value = HistoriqueNavigationEvent.TransactionModifiee
                    } else {
                        throw result.exceptionOrNull() ?: Exception("Erreur lors de la modification")
                    }
                } else {
                    // Créer une nouvelle transaction normale
                    // Pour les dépenses, vérifier qu'une enveloppe est sélectionnée
                    val enveloppeId = if (state.typeTransaction == TypeTransaction.Depense) {
                        val enveloppeSelectionnee = state.enveloppeSelectionnee
                        enveloppeSelectionnee?.id
                            ?: throw Exception("Aucune enveloppe sélectionnée pour la dépense")
                    } else {
                        null
                    }
                    
                    // Créer la nouvelle transaction
                    val result = enregistrerTransactionUseCase.executer(
                        id = original.id, // MT1 : la transaction garde son ID
                        typeTransaction = state.typeTransaction,
                        montant = montantEnDollars,
                        compteId = state.compteSelectionne.id,
                        collectionCompte = collectionCompte,
                        enveloppeId = enveloppeId,
                        tiersUtiliser = state.tiersUtiliser.takeIf { it.isNotBlank() } ?: "Transaction",
                        note = state.note.takeIf { it.isNotBlank() },
                        date = dateTransaction,
                        estFractionnee = false,
                        sousItems = null
                    )

                    if (result.isSuccess) {
                        preserverMetadonneesOriginales(original)
                        _uiState.update { 
                            it.copy(
                                estEnTrainDeSauvegarder = false,
                                messageErreur = null,
                                transactionModifiee = true
                            )
                        }
                        
                        // Déclencher l'événement de navigation pour indiquer que la transaction a été modifiée
                        _navigationEvents.value = HistoriqueNavigationEvent.TransactionModifiee
                    } else {
                        throw result.exceptionOrNull() ?: Exception("Erreur lors de la modification")
                    }
                }
                } catch (e: Exception) {
                    // MT2 : la nouvelle version n'a pas pu être écrite, l'original revient avec tous ses effets
                    restaurerOriginal(original)
                    throw e
                }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        estEnTrainDeSauvegarder = false,
                        messageErreur = e.message ?: "Erreur inconnue"
                    )
                }
            }
        }
    }

    private suspend fun preserverMetadonneesOriginales(original: Transaction) {
        val nouvelle = transactionRepository.recupererTransactionParId(original.id).getOrThrow()
        transactionRepository.mettreAJourTransaction(nouvelle.copy(created = original.created, updated = Date())).getOrThrow()
    }

    private fun terminerModification() {
        _uiState.update { it.copy(estEnTrainDeSauvegarder = false, messageErreur = null, transactionModifiee = true) }
        _navigationEvents.value = HistoriqueNavigationEvent.TransactionModifiee
    }

    private fun parserFractions(transaction: Transaction): List<FractionTransaction> {
        if (!transaction.estFractionnee || transaction.sousItems.isNullOrBlank()) return emptyList()
        return runCatching {
            com.google.gson.JsonParser.parseString(transaction.sousItems).asJsonArray.mapIndexed { index, element ->
                val obj = element.asJsonObject
                FractionTransaction(
                    id = obj.get("id")?.asString ?: "fraction_${index + 1}",
                    enveloppeId = (obj.get("enveloppe_id") ?: obj.get("enveloppeId"))
                        ?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    montant = kotlin.math.round((obj.get("montant")?.asDouble ?: 0.0) * 100.0),
                    note = obj.get("note")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun allocationIdPour(enveloppeId: String?, date: Date): String? = enveloppeId?.let { id ->
        allAllocations.firstOrNull { it.enveloppeId == id && memeMois(it.mois, date) }?.id
    }

    private fun memeMois(a: Date, b: Date): Boolean {
        val ca = java.util.Calendar.getInstance().apply { time = a }
        val cb = java.util.Calendar.getInstance().apply { time = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.MONTH) == cb.get(java.util.Calendar.MONTH)
    }

    /**
     * MT4 : cet écran ne sait modifier que les dépenses et revenus ordinaires. Un paiement, un virement,
     * un prêt ou un emprunt deviendrait une dépense ordinaire et perdrait son lien : on refuse avant d'écrire quoi que ce soit.
     */
    private fun verifierModifiable(transaction: Transaction) {
        val ordinaire = transaction.type == TypeTransaction.Depense || transaction.type == TypeTransaction.Revenu
        val lie = transaction.sousItems?.let {
            it.contains("pret_personnel_id") || it.contains(com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl.CLE_OPERATION_PAIEMENT)
        } ?: false
        if (!ordinaire || lie) {
            throw Exception(
                "Ce type d'opération (paiement, virement, prêt ou emprunt) ne se modifie pas ici : " +
                    "supprimez-le puis refaites-le. Rien n'a été changé."
            )
        }
    }

    /** MT2 : remet l'original (même ID, mêmes effets sur le compte et les enveloppes) après un échec. */
    private suspend fun restaurerOriginal(original: Transaction) {
        val enveloppeId = if (!original.estFractionnee) {
            original.allocationMensuelleId?.let { allocationMensuelleRepository.getAllocationById(it)?.enveloppeId }
        } else null
        enregistrerTransactionUseCase.executer(
            id = original.id,
            typeTransaction = original.type,
            montant = original.montant,
            compteId = original.compteId,
            collectionCompte = original.collectionCompte,
            enveloppeId = enveloppeId,
            tiersUtiliser = original.tiersUtiliser ?: "Transaction",
            note = original.note,
            date = original.date,
            estFractionnee = original.estFractionnee,
            sousItems = original.sousItems
        ).getOrThrow()
        if (original.estFractionnee && !original.sousItems.isNullOrBlank()) {
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            com.google.gson.Gson().fromJson<List<Map<String, Any>>>(original.sousItems, type).forEach { sousItem ->
                val allocation = (sousItem["allocation_mensuelle_id"] as? String)
                    ?.let { allocationMensuelleRepository.getAllocationById(it) } ?: return@forEach
                val montant = (sousItem["montant"] as? Double) ?: 0.0
                allocationMensuelleRepository.mettreAJourAllocation(
                    if (original.collectionCompte == "comptes_credits") allocation.copy(depense = allocation.depense + montant, alloue = allocation.alloue + montant)
                    else allocation.copy(depense = allocation.depense + montant, solde = allocation.solde - montant)
                )
            }
        }
    }

    /**
     * Efface les événements de navigation.
     */
    fun effacerNavigationEvent() {
        _navigationEvents.value = null
    }

    /**
     * Construit les enveloppes UI avec les allocations.
     */
    private fun construireEnveloppesUi(): List<EnveloppeUi> {
        return allEnveloppes.filter { !it.estArchive }.map { enveloppe ->
            val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
            
            // Récupérer la couleur du compte source depuis l'allocation
            // 🎨 CORRECTION : Reset la couleur de provenance quand solde = 0
            val compteSource = if ((allocation?.solde ?: 0.0) > 0.001) {
                allocation?.compteSourceId?.let { compteId ->
                    allComptes.find { it.id == compteId }
                }
            } else {
                null // Reset la couleur quand solde = 0
            }

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = allocation?.solde ?: 0.0,
                depense = allocation?.depense ?: 0.0,
                alloue = allocation?.alloue ?: 0.0, // Alloué ce mois
                alloueCumulatif = allocation?.alloue ?: 0.0, // ← NOUVEAU : Pour simplifier, on utilise la même valeur
                objectif = enveloppe.objectifMontant,
                couleurProvenance = compteSource?.couleur, // ✅ VRAIE COULEUR DU COMPTE
                statutObjectif = com.xburnsx.toutiebudget.ui.budget.StatutObjectif.GRIS
            )
        }.sortedBy { enveloppe ->
            val categorie = allCategories.find { cat -> 
                allEnveloppes.find { it.id == enveloppe.id }?.categorieId == cat.id 
            }
            categorie?.nom ?: "Sans catégorie"
        }
    }

    // Méthodes de mise à jour de l'état (identiques à AjoutTransactionViewModel)
    fun mettreAJourTypeTransaction(type: TypeTransaction) {
        _uiState.update { it.copy(typeTransaction = type) }
    }

    fun onMontantChanged(nouveauMontant: String) {
        _uiState.update { state ->
            state.copy(montant = nouveauMontant).calculerValidite()
        }
    }

    fun onCompteChanged(nouveauCompte: Compte?) {
        _uiState.update { state ->
            state.copy(compteSelectionne = nouveauCompte).calculerValidite()
        }
    }

    /**
     * Met à jour le compte de paiement sélectionné (pour le mode Paiement).
     */
    fun onComptePaiementChanged(nouveauCompte: Compte?) {
        _uiState.update { state ->
            val updated = state.copy(
                comptePaiementSelectionne = nouveauCompte,
                // Mettre automatiquement le nom du compte dans tiersUtiliser pour le mode Paiement
                tiersUtiliser = if (state.modeOperation == "Paiement" && nouveauCompte != null) {
                    nouveauCompte.nom
                } else {
                    state.tiersUtiliser
                }
            )
            val montantAuto = (nouveauCompte as? com.xburnsx.toutiebudget.data.modeles.CompteDette)?.paiementMinimum
            if (state.modeOperation == "Paiement" && montantAuto != null && montantAuto > 0) {
                val cents = kotlin.math.round(montantAuto * 100).toLong().toString()
                updated.copy(montant = cents).calculerValidite()
            } else {
                updated.calculerValidite()
            }
        }
    }

    fun onEnveloppeChanged(nouvelleEnveloppe: EnveloppeUi?) {
        _uiState.update { state ->
            state.copy(enveloppeSelectionnee = nouvelleEnveloppe).calculerValidite()
        }
    }

    fun onTexteTiersSaisiChange(nouveauTexte: String) {
        _uiState.update { state ->
            state.copy(tiersUtiliser = nouveauTexte).calculerValidite()
        }
    }

    /**
     * Sélectionne un tiers existant.
     */
    fun onTiersSelectionne(tiers: Tiers) {
        _uiState.update { state ->
            state.copy(
                tiersUtiliser = tiers.nom
            ).calculerValidite()
        }
    }

    fun onNoteChanged(nouvelleNote: String) {
        _uiState.update { state ->
            state.copy(note = nouvelleNote).calculerValidite()
        }
    }

    fun onDateChanged(nouvelleDate: LocalDate) {
        _uiState.update { state ->
            state.copy(dateTransaction = nouvelleDate).calculerValidite()
        }
        viewModelScope.launch {
            val date = Date.from(nouvelleDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
            enveloppeRepository.recupererAllocationsPourMois(date)
                .onSuccess { allocations ->
                    allAllocations = (allAllocations.filterNot { memeMois(it.mois, date) } + allocations).distinctBy { it.id }
                    _uiState.update { it.copy(enveloppesDisponibles = construireEnveloppesUi()) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(messageErreur = "Allocations du mois indisponibles : ${e.message}") }
                }
        }
    }

    /**
     * Charge une enveloppe manquante si elle n'a pas été trouvée dans les allocations initiales.
     */
    private fun chargerEnveloppeManquante(allocationId: String) {
        viewModelScope.launch {
            try {
                // Récupérer l'allocation directement par son ID
                val resultAllocation = enveloppeRepository.recupererAllocationParId(allocationId)
                if (resultAllocation.isSuccess) {
                    val allocation = resultAllocation.getOrNull()
                    if (allocation != null) {
                        // Ajouter l'allocation à la liste
                        allAllocations = allAllocations + allocation
                        
                        // Trouver l'enveloppe correspondante
                        val enveloppe = allEnveloppes.find { it.id == allocation.enveloppeId }
                        if (enveloppe != null) {
                            val enveloppeUi = construireEnveloppeUi(enveloppe)
                            
                            // Mettre à jour l'état avec l'enveloppe trouvée
                            _uiState.update { state ->
                                state.copy(enveloppeSelectionnee = enveloppeUi)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignorer l'erreur, l'enveloppe restera vide
            }
        }
    }

    /**
     * Ouvre l'interface de fractionnement de transaction.
     */
    fun ouvrirFractionnement() {
        // S'assurer que les enveloppes sont chargées
        if (_uiState.value.enveloppesDisponibles.isEmpty()) {
            // Recharger les données si les enveloppes ne sont pas disponibles
            viewModelScope.launch {
                try {
                    val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                    val resultCategories = categorieRepository.recupererToutesLesCategories()
                    val resultAllocations = enveloppeRepository.recupererAllocationsPourMois(Date())
                    
                    if (resultEnveloppes.isSuccess && resultCategories.isSuccess && resultAllocations.isSuccess) {
                        allEnveloppes = resultEnveloppes.getOrNull() ?: emptyList()
                        allCategories = resultCategories.getOrNull() ?: emptyList()
                        allAllocations = resultAllocations.getOrNull() ?: emptyList()
                        
                        val enveloppesUi = construireEnveloppesUi()
                        
                        _uiState.update { state ->
                            state.copy(enveloppesDisponibles = enveloppesUi)
                        }
                    }
                } catch (_: Exception) {
                    // Ignorer l'erreur, on continuera sans enveloppes
                }
            }
        }
        _uiState.update { it.copy(estEnModeFractionnement = true) }
    }

    /**
     * Ferme l'interface de fractionnement de transaction.
     */
    fun fermerFractionnement() {
        _uiState.update { it.copy(estEnModeFractionnement = false) }
    }

    /**
     * Confirme le fractionnement de la transaction.
     */
    fun confirmerFractionnement(fractions: List<FractionTransaction>) {
        // Les montants sont déjà en cents dans le dialog, pas besoin de conversion
        _uiState.update { 
            it.copy(
                estEnModeFractionnement = false,
                fractionnementEffectue = true, // Marquer que le fractionnement a été effectué
                fractionsSauvegardees = fractions // Sauvegarder les fractions pour les réutiliser
            ).calculerValidite() // Recalculer la validité pour activer le bouton Modifier
        }
    }
}
