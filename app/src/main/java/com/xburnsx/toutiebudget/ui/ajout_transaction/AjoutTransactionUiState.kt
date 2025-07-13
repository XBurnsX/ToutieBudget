// chemin/simule: /ui/ajout_transaction/AjoutTransactionUiState.kt
// Dépendances: Compte.kt, EnveloppeUi.kt, TypeTransaction.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi

/**
 * État de l'interface utilisateur pour l'écran d'ajout de transaction.
 * Contient toutes les données nécessaires à l'affichage et à la saisie.
 */
data class AjoutTransactionUiState(
    // --- État de chargement ---
    val isLoading: Boolean = true,
    val messageErreur: String? = null,
    val estEnTrainDeSauvegarder: Boolean = false,
    val erreur: String? = null,
    val transactionReussie: Boolean = false,
    
    // --- Modes et types de transaction ---
    val modeOperation: String = "Standard", // Standard, Prêt, Dette, Paiement
    val typeTransaction: TypeTransaction = TypeTransaction.Depense, // Dépense, Revenu (pour mode Standard)
    val typePret: String = "Prêt accordé", // Prêt accordé, Remboursement reçu
    val typeDette: String = "Dette contractée", // Dette contractée, Remboursement donné
    
    // --- Montant ---
    val montant: String = "",
    val montantValide: Boolean = false,
    
    // --- Sélections ---
    val compteSelectionne: Compte? = null,
    val enveloppeSelectionnee: EnveloppeUi? = null,
    
    // --- Champs de saisie ---
    val tiers: String = "", // Payé à / Reçu de
    val note: String = "",
    
    // --- Données disponibles ---
    val comptesDisponibles: List<Compte> = emptyList(),
    val enveloppesDisponibles: List<EnveloppeUi> = emptyList(),
    val enveloppesFiltrees: Map<String, List<EnveloppeUi>> = emptyMap(), // Groupées par catégorie
    
    // --- Validation ---
    val peutSauvegarder: Boolean = false
) {
    
    /**
     * Vérifie si le formulaire est valide pour la sauvegarde.
     */
    fun calculerValidite(): AjoutTransactionUiState {
        val montantEstValide = montant.toDoubleOrNull()?.let { it > 0 } ?: false
        val compteEstSelectionne = compteSelectionne != null
        val enveloppeEstValideOuPasRequise = when (modeOperation) {
            "Standard" -> when (typeTransaction) {
                TypeTransaction.Depense -> enveloppeSelectionnee != null
                TypeTransaction.Revenu -> true
                else -> true
            }
            else -> true // Pas d'enveloppe requise pour les autres modes
        }
        
        val peutSauvegarde = montantEstValide && 
                            compteEstSelectionne && 
                            enveloppeEstValideOuPasRequise &&
                            !estEnTrainDeSauvegarder

        return copy(
            montantValide = montantEstValide,
            peutSauvegarder = peutSauvegarde
        )
    }
}