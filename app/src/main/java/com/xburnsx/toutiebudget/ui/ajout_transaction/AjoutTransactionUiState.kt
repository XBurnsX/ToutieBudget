// chemin/simule: /ui/ajout_transaction/AjoutTransactionUiState.kt
// Dépendances: Modèles de données, EnveloppeUi

package com.xburnsx.toutiebudget.ui.ajout_transaction

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi

/**
 * État de l'interface utilisateur pour l'écran d'ajout de transaction.
 * Contient toutes les données nécessaires pour afficher et gérer le formulaire.
 */
data class AjoutTransactionUiState(
    // État de chargement
    val isLoading: Boolean = false,
    val erreur: String? = null,
    val transactionReussie: Boolean = false,
    
    // Données du formulaire
    val montant: String = "",  // Montant en centimes sous forme de string
    val modeOperation: String = "Standard",  // Standard, Prêt, Dette, Paiement
    val typeTransaction: String = "Dépense",  // Dépense, Revenu (pour mode Standard)
    val typePret: String = "Prêt accordé",  // Prêt accordé, Remboursement reçu
    val typeDette: String = "Dette contractée",  // Dette contractée, Remboursement donné
    val tiers: String = "",  // Payé à / Reçu de
    val note: String = "",  // Note optionnelle
    
    // Sélections
    val compteSelectionne: Compte? = null,
    val enveloppeSelectionnee: EnveloppeUi? = null,
    
    // Données disponibles
    val comptesDisponibles: List<Compte> = emptyList(),
    val enveloppesFiltrees: Map<String, List<EnveloppeUi>> = emptyMap()  // Groupées par catégorie
)