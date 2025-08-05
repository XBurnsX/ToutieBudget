package com.xburnsx.toutiebudget.ui.ajout_transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.domain.usecases.ModifierTransactionUseCase
import com.xburnsx.toutiebudget.domain.usecases.EnregistrerTransactionUseCase
import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

/**
 * ViewModel pour l'écran de modification de transaction.
 * Gère la logique de modification d'une transaction existante.
 */
class ModifierTransactionViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val categorieRepository: CategorieRepository,
    private val tiersRepository: TiersRepository,
    private val transactionRepository: TransactionRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val modifierTransactionUseCase: ModifierTransactionUseCase,
    private val enregistrerTransactionUseCase: EnregistrerTransactionUseCase,
    private val supprimerTransactionUseCase: SupprimerTransactionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AjoutTransactionUiState())
    val uiState: StateFlow<AjoutTransactionUiState> = _uiState.asStateFlow()

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
                val gson = com.google.gson.Gson()
                val jsonArray = com.google.gson.JsonParser.parseString(transaction.sousItems).asJsonArray
                jsonArray.mapNotNull { element ->
                    val obj = element.asJsonObject
                    val description = obj.get("description")?.asString ?: ""
                    val montant = obj.get("montant")?.asDouble ?: 0.0
                    val enveloppeId = obj.get("enveloppeId")?.asString ?: ""
                    val note = obj.get("note")?.asString ?: ""
                    
                    FractionTransaction(
                        id = "temp_${jsonArray.indexOf(element) + 1}",
                        enveloppeId = enveloppeId,
                        montant = montant, // Déjà en Double
                        note = note.ifBlank { description }
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        _uiState.update { state ->
            state.copy(
                typeTransaction = transaction.type,
                montant = (transaction.montant * 100).toLong().toString(), // Convertir en centimes
                compteSelectionne = allComptes.find { it.id == transaction.compteId },
                enveloppeSelectionnee = if (transaction.allocationMensuelleId != null && !transaction.estFractionnee) {
                    // Essayer de trouver l'allocation dans les allocations chargées
                    allAllocations.find { it.id == transaction.allocationMensuelleId }?.let { allocation ->
                        allEnveloppes.find { enveloppe -> enveloppe.id == allocation.enveloppeId }?.let { construireEnveloppeUi(it) }
                    }
                } else null,
                texteTiersSaisi = if (transaction.tiersId != null) {
                    allTiers.find { it.id == transaction.tiersId }?.nom ?: transaction.tiersId
                } else {
                    transaction.tiers ?: ""
                },
                tiers = if (transaction.tiersId != null) {
                    allTiers.find { it.id == transaction.tiersId }?.nom ?: transaction.tiersId
                } else {
                    transaction.tiers ?: ""
                },
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
        val montantEnDollars = montantEnCentimes / 100.0

        if (state.compteSelectionne == null) {
            _uiState.update { it.copy(messageErreur = "Veuillez sélectionner un compte") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(estEnTrainDeSauvegarder = true, messageErreur = null) }
            
            try {
                val collectionCompte = when (state.compteSelectionne) {
                    is com.xburnsx.toutiebudget.data.modeles.CompteCheque -> "comptes_cheques"
                    is com.xburnsx.toutiebudget.data.modeles.CompteCredit -> "comptes_credits"
                    is com.xburnsx.toutiebudget.data.modeles.CompteDette -> "comptes_dettes"
                    is com.xburnsx.toutiebudget.data.modeles.CompteInvestissement -> "comptes_investissements"
                    else -> "comptes_cheques"
                }
                
                // Convertir LocalDate en Date avec l'heure locale actuelle du téléphone
                val maintenant = java.time.LocalDateTime.now()
                val dateTransaction = Date.from(state.dateTransaction.atTime(maintenant.hour, maintenant.minute, maintenant.second).atZone(ZoneId.systemDefault()).toInstant())
                
                // ÉTAPE 1: Supprimer complètement l'ancienne transaction et rembourser les enveloppes
                if (transactionAModifier!!.estFractionnee && transactionAModifier!!.sousItems != null) {
                    // Rembourser les allocations des fractions existantes
                    try {
                        val gson = com.google.gson.Gson()
                        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                        val anciensSousItems = gson.fromJson<List<Map<String, Any>>>(
                            transactionAModifier!!.sousItems,
                            type
                        )
                        
                        // Rembourser les effets des anciennes allocations
                        for (ancienSousItem in anciensSousItems) {
                            val ancienneAllocationId = ancienSousItem["allocation_mensuelle_id"] as? String
                            val ancienMontantEnCentimes = (ancienSousItem["montant"] as? Double) ?: 0.0
                            
                            if (ancienneAllocationId != null) {
                                val ancienneAllocation = allocationMensuelleRepository.getAllocationById(ancienneAllocationId)
                                if (ancienneAllocation != null) {
                                    // Rembourser en soustrayant le montant (convertir centimes en dollars)
                                    val allocationRemboursee = ancienneAllocation.copy(
                                        depense = ancienneAllocation.depense - (ancienMontantEnCentimes / 100.0),
                                        solde = ancienneAllocation.solde + (ancienMontantEnCentimes / 100.0)
                                    )
                                    allocationMensuelleRepository.mettreAJourAllocation(allocationRemboursee)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("Erreur lors du remboursement des anciennes allocations: ${e.message}")
                    }
                } else if (transactionAModifier!!.allocationMensuelleId != null) {
                    // Rembourser l'allocation normale
                    val ancienneAllocation = allocationMensuelleRepository.getAllocationById(transactionAModifier!!.allocationMensuelleId!!)
                    if (ancienneAllocation != null) {
                        val montantAncien = transactionAModifier!!.montant / 100.0 // Convertir en dollars
                        val allocationRemboursee = ancienneAllocation.copy(
                            depense = ancienneAllocation.depense - montantAncien,
                            solde = ancienneAllocation.solde + montantAncien
                        )
                        allocationMensuelleRepository.mettreAJourAllocation(allocationRemboursee)
                    }
                }
                
                // Supprimer l'ancienne transaction AVANT de créer la nouvelle
                val resultSuppression = supprimerTransactionUseCase.executer(transactionAModifier!!.id)
                if (resultSuppression.isFailure) {
                    throw resultSuppression.exceptionOrNull() ?: Exception("Erreur lors de la suppression de l'ancienne transaction")
                }
                
                // ÉTAPE 2: Créer une nouvelle transaction avec les nouveaux détails
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
                                compteSourceId = state.compteSelectionne!!.id,
                                collectionCompteSource = collectionCompte
                            )
                            allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)
                        }
                        
                        // Récupérer le nom de l'enveloppe pour la description
                        val nomEnveloppe = allEnveloppes.find { it.id == fraction.enveloppeId }?.nom ?: "Enveloppe"
                        
                                                 mapOf(
                             "id" to "temp_${index + 1}",
                             "description" to nomEnveloppe,
                             "enveloppeId" to fraction.enveloppeId,
                             "montant" to montantEnDollars,
                             "allocation_mensuelle_id" to allocationActuelle.id,
                             "transactionParenteId" to null
                         )
                    }
                    
                    val gson = com.google.gson.Gson()
                    val sousItemsJson = gson.toJson(sousItems)

                    // Créer une nouvelle transaction avec estFractionnee = true
                    val result = enregistrerTransactionUseCase.executer(
                         typeTransaction = state.typeTransaction,
                         montant = montantEnDollars, // Utiliser montantEnDollars, pas montantEnCentimes
                         compteId = state.compteSelectionne!!.id,
                         collectionCompte = collectionCompte,
                         enveloppeId = null, // Pas d'enveloppe pour la transaction principale
                         tiersNom = state.texteTiersSaisi.takeIf { it.isNotBlank() } ?: "Transaction fractionnée",
                         note = state.note.takeIf { it.isNotBlank() },
                         date = dateTransaction,
                         estFractionnee = true,
                         sousItems = sousItemsJson
                     )

                    if (result.isSuccess) {
                        // Mettre à jour les allocations mensuelles en utilisant les données du JSON
                        for (sousItem in sousItems) {
                            val allocationId = sousItem["allocation_mensuelle_id"] as? String
                                                         val montantEnDollars = (sousItem["montant"] as? Double) ?: 0.0
                            
                            if (allocationId != null) {
                                // Récupérer l'allocation par son ID (frais depuis la base de données)
                                val resultAllocation = enveloppeRepository.recupererAllocationParId(allocationId)
                            
                                if (resultAllocation.isSuccess) {
                                    val allocationActuelle = resultAllocation.getOrThrow()
                                    // Mettre à jour l'allocation avec le montant en centimes (convertir en dollars)
                                                                         val nouvelleAllocation = allocationActuelle.copy(
                                         depense = allocationActuelle.depense + montantEnDollars,
                                         solde = allocationActuelle.solde - montantEnDollars
                                     )
                                    
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
                    } else {
                        _uiState.update { 
                            it.copy(
                                estEnTrainDeSauvegarder = false,
                                messageErreur = result.exceptionOrNull()?.message ?: "Erreur lors de la modification"
                            )
                        }
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
                        typeTransaction = state.typeTransaction,
                        montant = montantEnDollars,
                        compteId = state.compteSelectionne!!.id,
                        collectionCompte = collectionCompte,
                        enveloppeId = enveloppeId,
                        tiersNom = state.texteTiersSaisi.takeIf { it.isNotBlank() } ?: "Transaction",
                        note = state.note.takeIf { it.isNotBlank() },
                        date = dateTransaction,
                        estFractionnee = false,
                        sousItems = null
                    )

                    if (result.isSuccess) {
                        _uiState.update { 
                            it.copy(
                                estEnTrainDeSauvegarder = false,
                                messageErreur = null,
                                transactionModifiee = true
                            )
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                estEnTrainDeSauvegarder = false,
                                messageErreur = result.exceptionOrNull()?.message ?: "Erreur lors de la modification"
                            )
                        }
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

    /**
     * Réinitialise le flag transactionModifiee.
     */
    fun reinitialiserTransactionModifiee() {
        _uiState.update { it.copy(transactionModifiee = false) }
    }

    /**
     * Construit les enveloppes UI avec les allocations.
     */
    private suspend fun construireEnveloppesUi(): List<EnveloppeUi> {
        return allEnveloppes.filter { !it.estArchive }.map { enveloppe ->
            val categorie = allCategories.find { it.id == enveloppe.categorieId }
            val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
            
            // Récupérer la couleur du compte source depuis l'allocation
            val compteSource = allocation?.compteSourceId?.let { compteId ->
                allComptes.find { it.id == compteId }
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

    fun onEnveloppeChanged(nouvelleEnveloppe: EnveloppeUi?) {
        _uiState.update { state ->
            state.copy(enveloppeSelectionnee = nouvelleEnveloppe).calculerValidite()
        }
    }

    fun onTexteTiersSaisiChange(nouveauTexte: String) {
        _uiState.update { state ->
            state.copy(texteTiersSaisi = nouveauTexte).calculerValidite()
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
    }

    fun effacerMessageErreur() {
        _uiState.update { it.copy(messageErreur = null) }
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
            } catch (e: Exception) {
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
                } catch (e: Exception) {
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