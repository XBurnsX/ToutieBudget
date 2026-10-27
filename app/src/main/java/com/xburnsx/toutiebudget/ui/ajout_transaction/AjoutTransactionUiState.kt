// chemin/simule: /ui/ajout_transaction/AjoutTransactionUiState.kt
// Dépendances: Compte.kt, EnveloppeUi.kt, TypeTransaction.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionTransaction
import java.time.LocalDate

enum class StatutSynchronisationEcriture(val libelle: String) {
    ENREGISTREE_LOCALEMENT("Enregistrée localement"),
    SYNCHRONISEE("Synchronisée"),
    ECHEC("Échec de synchronisation")
}

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
    /** AA5 : état réel des SyncJobs créés pendant la dernière écriture. */
    val statutSynchronisation: StatutSynchronisationEcriture? = null,
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
    val dateFuture: Boolean = false,
    /** AA3 : dossiers actifs du tiers pour un remboursement ; dossier choisi (null = du plus ancien au plus récent). */
    val dossiersDisponibles: List<com.xburnsx.toutiebudget.data.modeles.PretPersonnel> = emptyList(),
    val dossierChoisiId: String? = null,
    /** AA2 : enveloppes déjà utilisées avec ce tiers, proposées seulement. */
    val suggestionsEnveloppes: List<EnveloppeUi> = emptyList(),
    /** AA1 : aperçu court du résultat (compte et enveloppe après l'opération). */
    val apercu: String? = null,
    /** MT4/MT8 (Modifier) : pourquoi cette écriture ne se modifie pas ici ; non null = sauvegarde désactivée. */
    val modificationBloquee: String? = null,
    /** MA3/MA5 (Modifier) : origine, liens et état de synchronisation de l'écriture. */
    val origineTransaction: String? = null
) {
    /** Somme des fractions = montant saisi, en cents exacts, chaque fraction positive et avec une enveloppe. */
    fun fractionsCorrespondentAuMontant(): Boolean {
        val totalCents = montant.toLongOrNull()?.takeIf { it > 0L } ?: return false
        if (fractionsSauvegardees.isEmpty()) return false
        var total = 0L
        for (fraction in fractionsSauvegardees) {
            val cents = fraction.montant
            if (!cents.isFinite() || cents <= 0.0 || cents != kotlin.math.floor(cents) ||
                cents >= Long.MAX_VALUE.toDouble() || fraction.enveloppeId.isBlank()
            ) return false
            total = try {
                Math.addExact(total, cents.toLong())
            } catch (_: ArithmeticException) {
                return false
            }
        }
        return total == totalCents
    }

    /**
     * AA1 : une ligne, sans encombrer : le compte (et l'enveloppe d'une dépense simple) avant → après.
     */
    private fun construireApercu(): String? {
        val dollars = (montant.toDoubleOrNull() ?: return null) / 100.0
        val compte = compteSelectionne ?: return null
        val sens = when {
            modeOperation == "Standard" && typeTransaction == TypeTransaction.Revenu -> 1.0
            modeOperation == "Prêt" && typePret == "Remboursement reçu" -> 1.0
            modeOperation == "Emprunt" && typeDette == "Dette contractée" -> 1.0
            else -> -1.0
        }
        val f = { v: Double -> com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(v) }
        val ligneCompte = "${compte.nom} : ${f(compte.solde)} → ${f(compte.solde + sens * dollars)}"
        val enveloppe = enveloppeSelectionnee
        val surCarte = compte is com.xburnsx.toutiebudget.data.modeles.CompteCredit
        val ciblePaiement = comptePaiementSelectionne?.let { cible ->
            " · ${cible.nom} : ${f(cible.solde)} → ${f(cible.solde + dollars)}"
        }.orEmpty()
        val detailEnveloppes = when {
            modeOperation != "Standard" || typeTransaction != TypeTransaction.Depense -> ""
            fractionnementEffectue -> fractionsSauvegardees.joinToString(", ", prefix = " · Enveloppes : ") { fraction ->
                enveloppesDisponibles.find { it.id == fraction.enveloppeId }?.nom ?: fraction.enveloppeId
            }
            enveloppe != null && !surCarte -> " · ${enveloppe.nom} : ${f(enveloppe.solde)} → ${f(enveloppe.solde - dollars)}"
            enveloppe != null -> " · Enveloppe : ${enveloppe.nom}"
            else -> ""
        }
        val libelleTiers = tiersUtiliser.trim().ifBlank { "Sans tiers" }
        return "${f(dollars)} · $libelleTiers · $dateTransaction · $ligneCompte$ciblePaiement$detailEnveloppes"
    }

    /**
     * Vérifie si le formulaire est valide pour la sauvegarde et le fractionnement.
     */
    fun calculerValidite(): AjoutTransactionUiState {
        val montantCentimes = montant.toLongOrNull()
        val montantEstValide = montantCentimes != null && montantCentimes > 0L
        val dettePayee = if (modeOperation == "Paiement") {
            comptePaiementSelectionne as? com.xburnsx.toutiebudget.data.modeles.CompteDette
        } else null
        // DF8 : signal immédiat sur le formulaire. Le service revalide aussi
        // sous transaction Room pour protéger contre un paiement concurrent.
        val paiementDetteDansLaLimite = dettePayee?.let { dette ->
            dette.soldeDette.isFinite() &&
                montantCentimes != null &&
                montantCentimes <= kotlin.math.round(
                    kotlin.math.max(0.0, -dette.soldeDette) * 100.0
                ).toLong()
        } ?: true
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
                            modificationBloquee == null &&
                            paiementDetteDansLaLimite &&
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
            dateFuture = dateTransaction.isAfter(LocalDate.now()),
            apercu = if (peutSauvegarde) construireApercu() else null
        )
    }
}
