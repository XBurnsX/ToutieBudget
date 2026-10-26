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
    
    // --- Messages de confirmation ---
    val messageConfirmation: String? = null,
    val detteSoldee: Boolean = false,
    
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
    val comptePaiementSelectionne: Compte? = null, // Compte sélectionné dans SelecteurComptePaiement
    val enveloppeSelectionnee: EnveloppeUi? = null,
    
    // --- Champs de saisie ---
    val tiersUtiliser: String = "", // Nom du tiers utilisé dans la transaction
    val note: String = "",
    val dateTransaction: LocalDate = LocalDate.now(),
    
    // --- Tiers ---
    val tiersDisponibles: List<Tiers> = emptyList(), // Liste des tiers disponibles pour la sélection
    val isLoadingTiers: Boolean = false,
    
    // --- Fractionnement ---
    val estEnModeFractionnement: Boolean = false,
    val fractionnementEffectue: Boolean = false,
    val fractionsSauvegardees: List<FractionTransaction> = emptyList(),
    val estFractionnee: Boolean = false,
    
    // --- Validation ---
    val estValide: Boolean = false,
    val peutSauvegarder: Boolean = false,
    val peutFractionner: Boolean = false,
    
    // --- Données disponibles ---
    val comptesDisponibles: List<Compte> = emptyList(),
    val enveloppesDisponibles: List<EnveloppeUi> = emptyList(),
    val enveloppesFiltrees: Map<String, List<EnveloppeUi>> = emptyMap(), // Groupées par catégorie
    /** AT21 : les soldes du mois n'ont pas pu être lus ; ne pas les montrer comme 0 $. */
    val allocationsIndisponibles: Boolean = false,
    /** AT12 : l'argent de l'enveloppe vient d'un autre compte que celui qui paie (avertissement, pas un refus). */
    val avertissementProvenance: String? = null,
    /** AA6 : la date choisie est dans le futur (avertissement seulement). */
    val dateFuture: Boolean = false
) {
    /** Somme des fractions = montant saisi, en cents exacts, chaque fraction positive et avec une enveloppe. */
    fun fractionsCorrespondentAuMontant(): Boolean {
        val totalCents = montant.toDoubleOrNull()?.let { Math.round(it) } ?: return false
        if (fractionsSauvegardees.isEmpty()) return false
        if (fractionsSauvegardees.any { it.montant <= 0 || it.enveloppeId.isBlank() }) return false
        return fractionsSauvegardees.sumOf { Math.round(it.montant) } == totalCents
    }

    /**
     * Vérifie si le formulaire est valide pour la sauvegarde et le fractionnement.
     */
    fun calculerValidite(): AjoutTransactionUiState {
        val montantEstValide = montant.toDoubleOrNull()?.let { it > 0 } ?: false
        val compteEstSelectionne = compteSelectionne != null
        val tiersEstRempli = tiersUtiliser.isNotBlank()
        
        // Validation spéciale pour le mode Paiement
        val validationPaiement = if (modeOperation == "Paiement") {
            comptePaiementSelectionne != null
        } else {
            tiersEstRempli
        }
        
        // Validation pour le bouton "Fractionner" : montant + compte + tiers (pas besoin d'enveloppe)
        val peutFractionner = montantEstValide && 
                             compteEstSelectionne && 
                             validationPaiement
        
        // Validation pour le bouton "Enregistrer" : montant + compte + tiers + enveloppe OU fractionnement effectué
        val enveloppeEstValideOuPasRequise = when (modeOperation) {
            "Standard" -> when (typeTransaction) {
                TypeTransaction.Depense -> enveloppeSelectionnee != null || fractionnementEffectue
                TypeTransaction.Revenu -> true
                else -> true
            }
            "Paiement" -> true // Pas d'enveloppe requise pour le mode Paiement
            else -> true // Pas d'enveloppe requise pour les autres modes
        }
        
        // AT7 / AT11 : des fractions qui ne font plus exactement le total (au cent) bloquent l'enregistrement
        val fractionsValides = !fractionnementEffectue || fractionsCorrespondentAuMontant()

        // AT19 : un compte ne peut pas se payer lui-même
        val sourceDifferenteDeLaCible = modeOperation != "Paiement" ||
            comptePaiementSelectionne?.id != compteSelectionne?.id

        // AT21 : une dépense ne se décide pas sur des soldes d'enveloppes illisibles
        val soldesConnus = !(allocationsIndisponibles && modeOperation == "Standard" && typeTransaction == TypeTransaction.Depense)

        val peutSauvegarde = montantEstValide &&
                            soldesConnus &&
                            compteEstSelectionne &&
                            validationPaiement &&
                            enveloppeEstValideOuPasRequise &&
                            fractionsValides &&
                            sourceDifferenteDeLaCible
        
        val estValide = peutSauvegarde
        
        return copy(
            montantValide = montantEstValide,
            peutSauvegarder = peutSauvegarde,
            peutFractionner = peutFractionner,
            estValide = estValide,
            dateFuture = dateTransaction.isAfter(LocalDate.now())
        )
    }
}