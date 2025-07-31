package com.xburnsx.toutiebudget.ui.ajout_transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.domain.usecases.ModifierTransactionUseCase
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.utils.OrganisationEnveloppesUtils
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
    private val modifierTransactionUseCase: ModifierTransactionUseCase
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
                val resultAllocations = enveloppeRepository.recupererAllocationsPourMois(transactionAModifier!!.date)

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
                if (resultAllocations.isFailure) {
                    throw Exception("Erreur lors du chargement des allocations: ${resultAllocations.exceptionOrNull()?.message}")
                }

                // Stocker les données
                allComptes = resultComptes.getOrNull() ?: emptyList()
                allEnveloppes = resultEnveloppes.getOrNull() ?: emptyList()
                allCategories = resultCategories.getOrNull() ?: emptyList()
                allTiers = resultTiers.getOrNull() ?: emptyList()
                allAllocations = resultAllocations.getOrNull() ?: emptyList()

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
        println("DEBUG: Remplir formulaire avec transaction: ${transaction.id}")
        println("DEBUG: Transaction type: ${transaction.type}")
        println("DEBUG: Transaction montant: ${transaction.montant}")
        println("DEBUG: Transaction tiersId: ${transaction.tiersId}")
        println("DEBUG: Transaction tiers: ${transaction.tiers}")
        println("DEBUG: Transaction note: ${transaction.note}")
        println("DEBUG: Transaction allocationMensuelleId: ${transaction.allocationMensuelleId}")
        // Trouver le compte correspondant
        val compte = allComptes.find { it.id == transaction.compteId }
        println("DEBUG: Compte trouvé: ${compte?.nom}")
        
        // Trouver l'enveloppe si c'était une dépense
        val enveloppe = if (transaction.allocationMensuelleId != null) {
            val allocation = allAllocations.find { it.id == transaction.allocationMensuelleId }
            println("DEBUG: Allocation trouvée: ${allocation?.id}")
            allocation?.let { allEnveloppes.find { enveloppe -> enveloppe.id == it.enveloppeId } }
        } else null
        println("DEBUG: Enveloppe trouvée: ${enveloppe?.nom}")

        // Trouver le tiers correspondant
        val tiers = if (transaction.tiersId != null) {
            // tiersId peut être soit un ID soit directement le nom du tiers
            val tiersTrouve = allTiers.find { it.id == transaction.tiersId }
            if (tiersTrouve != null) {
                println("DEBUG: Tiers trouvé par ID: ${tiersTrouve.nom}")
                tiersTrouve.nom
            } else {
                // tiersId n'est pas un ID valide, utiliser directement comme nom
                println("DEBUG: Utilisation de tiersId comme nom: ${transaction.tiersId}")
                transaction.tiersId
            }
        } else {
            println("DEBUG: Utilisation du tiers direct: ${transaction.tiers}")
            transaction.tiers ?: ""
        }
        println("DEBUG: Tiers final: $tiers")
        println("DEBUG: Nombre de tiers disponibles: ${allTiers.size}")
        println("DEBUG: Montant original: ${transaction.montant}")
        println("DEBUG: Montant en centimes: ${(transaction.montant * 100).toLong()}")

        _uiState.update { state ->
            state.copy(
                typeTransaction = transaction.type,
                montant = (transaction.montant * 100).toLong().toString(), // Convertir en centimes
                compteSelectionne = compte,
                enveloppeSelectionnee = enveloppe?.let { construireEnveloppeUi(it) },
                texteTiersSaisi = tiers,
                tiers = tiers, // Ajouter aussi dans le champ tiers
                note = transaction.note ?: "",
                dateTransaction = transaction.date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
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
        
        println("DEBUG: modifierTransaction - Début")
        println("DEBUG: modifierTransaction - transactionAModifier: ${transactionAModifier?.id}")
        println("DEBUG: modifierTransaction - state.montant: ${state.montant}")
        println("DEBUG: modifierTransaction - state.compteSelectionne: ${state.compteSelectionne?.nom}")
        println("DEBUG: modifierTransaction - state.enveloppeSelectionnee: ${state.enveloppeSelectionnee?.nom}")
        println("DEBUG: modifierTransaction - state.texteTiersSaisi: ${state.texteTiersSaisi}")
        println("DEBUG: modifierTransaction - state.note: ${state.note}")
        
        if (transactionAModifier == null) {
            _uiState.update { it.copy(messageErreur = "Aucune transaction à modifier") }
            return
        }

        val montantEnCentimes = state.montant.toLongOrNull()
        println("DEBUG: modifierTransaction - montant en centimes: $montantEnCentimes")
        if (montantEnCentimes == null || montantEnCentimes <= 0) {
            _uiState.update { it.copy(messageErreur = "Montant invalide") }
            return
        }
        
        // Convertir les centimes en dollars pour le use case
        val montantEnDollars = montantEnCentimes / 100.0
        println("DEBUG: modifierTransaction - montant en dollars: $montantEnDollars")

        if (state.compteSelectionne == null) {
            _uiState.update { it.copy(messageErreur = "Veuillez sélectionner un compte") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, messageErreur = null) }
            
            try {
                val collectionCompte = when (state.compteSelectionne) {
                    is com.xburnsx.toutiebudget.data.modeles.CompteCheque -> "comptes_cheques"
                    is com.xburnsx.toutiebudget.data.modeles.CompteCredit -> "comptes_credits"
                    is com.xburnsx.toutiebudget.data.modeles.CompteDette -> "comptes_dettes"
                    is com.xburnsx.toutiebudget.data.modeles.CompteInvestissement -> "comptes_investissements"
                    else -> "comptes_cheques"
                }
                
                println("DEBUG: modifierTransaction - Paramètres envoyés:")
                println("DEBUG: modifierTransaction - transactionId: ${transactionAModifier!!.id}")
                println("DEBUG: modifierTransaction - typeTransaction: ${state.typeTransaction}")
                println("DEBUG: modifierTransaction - montant: $montantEnDollars")
                println("DEBUG: modifierTransaction - compteId: ${state.compteSelectionne!!.id}")
                println("DEBUG: modifierTransaction - collectionCompte: $collectionCompte")
                println("DEBUG: modifierTransaction - enveloppeId: ${if (state.typeTransaction == TypeTransaction.Depense) state.enveloppeSelectionnee?.id else null}")
                println("DEBUG: modifierTransaction - tiersNom: ${state.texteTiersSaisi.takeIf { it.isNotBlank() }}")
                println("DEBUG: modifierTransaction - note: ${state.note.takeIf { it.isNotBlank() }}")
                
                                 // Convertir LocalDate en Date pour le UseCase (même logique que AjoutTransactionViewModel)
                 val dateTransaction = state.dateTransaction.atStartOfDay(ZoneId.systemDefault()).toInstant().let { Date.from(it) }
                 
                 val result = modifierTransactionUseCase.executer(
                     transactionId = transactionAModifier!!.id,
                     typeTransaction = state.typeTransaction,
                     montant = montantEnDollars,
                     compteId = state.compteSelectionne!!.id,
                     collectionCompte = collectionCompte,
                     enveloppeId = if (state.typeTransaction == TypeTransaction.Depense) state.enveloppeSelectionnee?.id else null,
                     tiersNom = state.texteTiersSaisi.takeIf { it.isNotBlank() },
                     note = state.note.takeIf { it.isNotBlank() },
                     date = dateTransaction // Utiliser la date convertie
                 )

                println("DEBUG: modifierTransaction - Résultat du use case: $result")
                
                if (result.isSuccess) {
                    println("DEBUG: modifierTransaction - Succès de la modification")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            messageErreur = null,
                            transactionModifiee = true
                        )
                    }
                    
                    // Réinitialiser transactionModifiee après un délai pour permettre la navigation
                    kotlinx.coroutines.delay(100)
                    _uiState.update { 
                        it.copy(transactionModifiee = false)
                    }
                } else {
                    println("DEBUG: modifierTransaction - Échec de la modification: ${result.exceptionOrNull()?.message}")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            messageErreur = result.exceptionOrNull()?.message ?: "Erreur lors de la modification"
                        )
                    }
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
     * Construit les enveloppes UI avec les allocations.
     */
    private suspend fun construireEnveloppesUi(): List<EnveloppeUi> {
        println("[DEBUG] construireEnveloppesUi - Début avec ${allEnveloppes.size} enveloppes")

        return allEnveloppes.filter { !it.estArchive }.map { enveloppe ->
            val categorie = allCategories.find { it.id == enveloppe.categorieId }
            val allocation = allAllocations.find { it.enveloppeId == enveloppe.id }
            
            // Récupérer la couleur du compte source depuis l'allocation
            val compteSource = allocation?.compteSourceId?.let { compteId ->
                allComptes.find { it.id == compteId }
            }

            println("[DEBUG] construireEnveloppesUi - Enveloppe: ${enveloppe.nom} (ID: ${enveloppe.id})")
            println("[DEBUG] construireEnveloppesUi - Allocation trouvée: ${allocation?.id} pour enveloppeId: ${enveloppe.id}")
            println("[DEBUG] construireEnveloppesUi - Compte source: ${compteSource?.nom} - couleur: ${compteSource?.couleur}")

            EnveloppeUi(
                id = enveloppe.id,
                nom = enveloppe.nom,
                solde = allocation?.solde ?: 0.0,
                depense = allocation?.depense ?: 0.0,
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
        println("[DEBUG] ModifierTransactionViewModel.onEnveloppeChanged - Nouvelle enveloppe sélectionnée: ${nouvelleEnveloppe?.nom} (ID: ${nouvelleEnveloppe?.id})")
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
} 