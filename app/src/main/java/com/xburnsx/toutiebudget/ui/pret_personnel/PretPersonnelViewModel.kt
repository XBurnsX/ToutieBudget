package com.xburnsx.toutiebudget.ui.pret_personnel

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
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PretPersonnelViewModel(
    private val pretPersonnelRepository: PretPersonnelRepository,
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PretPersonnelUiState(isLoading = true))
    val uiState: StateFlow<PretPersonnelUiState> = _uiState.asStateFlow()

    init {
        // Ne rien faire ici; l'écran déclenchera charger() dans LaunchedEffect pour garantir un refresh à chaque ouverture
    }

    fun charger() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, erreur = null)
            val result = pretPersonnelRepository.lister()
            result.onSuccess { entries ->
                println("DEBUG: Total entries: ${entries.size}")
                println("DEBUG: Entries avec estArchive=true: ${entries.count { it.estArchive }}")
                entries.filter { it.estArchive }.forEach { 
                    println("DEBUG: Archive trouvé: ${it.nomTiers} - Solde: ${it.solde} - Type: ${it.type}")
                }
                
                val itemsPret = entries
                    .filter { it.type == TypePretPersonnel.PRET && !it.estArchive && it.solde > 0 }
                    .map { it.toItem() }
                val itemsEmprunt = entries
                    .filter { it.type == TypePretPersonnel.DETTE && !it.estArchive && it.solde < 0 }
                    .map { it.toItem() }
                val itemsArchives = entries
                    .filter { it.estArchive }
                    .map { it.toItem() }
                
                println("DEBUG: itemsArchives count: ${itemsArchives.size}")
                
                _uiState.value = PretPersonnelUiState(
                    isLoading = false,
                    items = itemsPret + itemsEmprunt,
                    itemsPret = itemsPret,
                    itemsEmprunt = itemsEmprunt,
                    itemsArchives = itemsArchives
                )
            }.onFailure { e ->
                _uiState.value = PretPersonnelUiState(isLoading = false, erreur = e.message)
            }
        }
    }

    fun setTab(tab: PretTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun chargerHistoriquePourPret(pretId: String, nomTiers: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistorique = true)
            val txs = transactionRepository.recupererToutesLesTransactions().getOrElse { emptyList() }
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
            val recordActif = pretPersonnelRepository.lister().getOrElse { emptyList() }
                .firstOrNull { it.id == pretId }

            // ✅ Ne plus ajouter l'événement initial car il est déjà dans les transactions
            val itemsTries = items.sortedByDescending { it.date }

            _uiState.value = _uiState.value.copy(
                isLoadingHistorique = false,
                historique = itemsTries,
                detailPret = recordActif
            )
        }
    }

    fun clearHistorique() {
        _uiState.value = _uiState.value.copy(historique = emptyList(), isLoadingHistorique = false)
    }

    fun enregistrerRemboursement(
        pretId: String,
        nomTiers: String,
        montant: Double,
        compteId: String? = null,
        collectionCompte: String? = null
    ) {
        if (montant <= 0) return
        viewModelScope.launch {
            try {
                val pret = pretPersonnelRepository.lister().getOrElse { emptyList() }.firstOrNull { it.id == pretId } ?: return@launch
                val typeTx = if (pret.type == com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET) TypeTransaction.RemboursementRecu else TypeTransaction.RemboursementDonne
                val variationSoldeCompte = if (typeTx == TypeTransaction.RemboursementRecu) montant else -montant

                // 0) Choisir le compte par défaut si non fourni
                val (compteUtiliseId, collectionUtilisee) = if (compteId.isNullOrBlank() || collectionCompte.isNullOrBlank()) {
                    val comptes = compteRepository.recupererTousLesComptes().getOrElse { emptyList() }
                    val compteParDefaut = comptes.firstOrNull { it.collection == "comptes_cheques" } ?: comptes.firstOrNull()
                    val coll = compteParDefaut?.collection ?: "comptes_cheques"
                    val id = compteParDefaut?.id ?: ""
                    id to coll
                } else compteId to collectionCompte

                // 1) Créer la transaction
                val utilisateurId = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
                    ?: throw Exception("Utilisateur non connecté")
                transactionRepository.creerTransaction(
                    com.xburnsx.toutiebudget.data.modeles.Transaction(
                        id = java.util.UUID.randomUUID().toString(),
                        utilisateurId = utilisateurId,
                        type = typeTx,
                        montant = montant,
                        date = java.util.Date(),
                        note = null,
                        compteId = compteUtiliseId,
                        collectionCompte = collectionUtilisee,
                        allocationMensuelleId = null,
                        estFractionnee = false,
                        sousItems = "{\"pret_personnel_id\":\"$pretId\"}",
                        tiers = nomTiers
                    )
                )

                // 2) Mettre à jour le compte (solde et prêt à placer si entrée)
                compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                    compteId = compteUtiliseId,
                    collectionCompte = collectionUtilisee,
                    variationSolde = variationSoldeCompte,
                    mettreAJourPretAPlacer = (typeTx == TypeTransaction.RemboursementRecu)
                )

                // 3) Mettre à jour le prêt
                val nouveauSolde = if (pret.type == com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET) {
                    (pret.solde - montant).coerceAtLeast(0.0)
                } else {
                    (pret.solde + montant).coerceAtMost(0.0)
                }
                val pretMaj = pret.copy(solde = nouveauSolde, estArchive = kotlin.math.abs(nouveauSolde) < 0.005)
                pretPersonnelRepository.mettreAJour(pretMaj)

                // 4) Rafraîchir l'historique
                chargerHistoriquePourPret(pretId, nomTiers)
            } catch (e: Exception) { 
                println("Erreur lors de l'enregistrement du remboursement: ${e.message}")
                e.printStackTrace()
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

    private fun parseDate(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(raw)
            } catch (_: ParseException) {
            }
        }
        return null
    }
}


