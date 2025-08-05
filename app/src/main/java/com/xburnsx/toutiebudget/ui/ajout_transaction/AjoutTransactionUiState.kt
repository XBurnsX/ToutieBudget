// chemin/simule: /ui/ajout_transaction/AjoutTransactionUiState.kt
// Dépendances: Compte.kt, EnveloppeUi.kt, TypeTransaction.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionTransaction
import java.time.LocalDate

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
    val transactionModifiee: Boolean = false,
    
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
    val dateTransaction: LocalDate = LocalDate.now(),
    
    // --- Tiers ---
    val tiersDisponibles: List<Tiers> = emptyList(),
    val tiersSelectionne: Tiers? = null,
    val texteTiersSaisi: String = "",
    val isLoadingTiers: Boolean = false,

    // --- Données disponibles ---
    val comptesDisponibles: List<Compte> = emptyList(),
    val enveloppesDisponibles: List<EnveloppeUi> = emptyList(),
    val enveloppesFiltrees: Map<String, List<EnveloppeUi>> = emptyMap(), // Groupées par catégorie
    
    // --- Validation ---
    val peutSauvegarder: Boolean = false,
    val peutFractionner: Boolean = false,
    
    // --- Fractionnement ---
    val estEnModeFractionnement: Boolean = false,
    val fractionnementEffectue: Boolean = false,
    val fractionsSauvegardees: List<FractionTransaction> = emptyList()
) {
    
    /**
     * Vérifie si le formulaire est valide pour la sauvegarde et le fractionnement.
     */
    fun calculerValidite(): AjoutTransactionUiState {
        val montantEstValide = montant.toDoubleOrNull()?.let { it > 0 } ?: false
        val compteEstSelectionne = compteSelectionne != null
        val tiersEstRempli = texteTiersSaisi.isNotBlank()
        
        // Validation pour le bouton "Fractionner" : montant + compte + tiers (pas besoin d'enveloppe)
        val peutFractionner = montantEstValide && 
                             compteEstSelectionne && 
                             tiersEstRempli &&
                             !estEnTrainDeSauvegarder
        
        // Validation pour le bouton "Enregistrer" : montant + compte + tiers + enveloppe OU fractionnement effectué
        val enveloppeEstValideOuPasRequise = when (modeOperation) {
            "Standard" -> when (typeTransaction) {
                TypeTransaction.Depense -> enveloppeSelectionnee != null || fractionnementEffectue
                TypeTransaction.Revenu -> true
                else -> true
            }
            else -> true // Pas d'enveloppe requise pour les autres modes
        }
        
        val peutSauvegarde = montantEstValide && 
                            compteEstSelectionne && 
                            tiersEstRempli &&
                            enveloppeEstValideOuPasRequise &&
                            !estEnTrainDeSauvegarder

        return copy(
            montantValide = montantEstValide,
            peutSauvegarder = peutSauvegarde,
            peutFractionner = peutFractionner
        )
    }
}