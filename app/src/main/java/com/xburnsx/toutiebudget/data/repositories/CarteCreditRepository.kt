package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.Transaction
import java.util.Date

/**
 * Interface du repository pour la gestion spécialisée des cartes de crédit.
 * Étend les fonctionnalités de base avec des calculs spécifiques aux cartes.
 */
interface CarteCreditRepository {

    /**
     * Récupère toutes les cartes de crédit de l'utilisateur connecté.
     * @return Result contenant la liste des cartes de crédit
     */
    suspend fun recupererCartesCredit(): Result<List<CompteCredit>>

    /**
     * Crée une nouvelle carte de crédit.
     * @param carteCredit La carte de crédit à créer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun creerCarteCredit(carteCredit: CompteCredit): Result<Unit>

    /**
     * Met à jour une carte de crédit existante.
     * @param carteCredit La carte avec les nouvelles données
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourCarteCredit(carteCredit: CompteCredit): Result<Unit>

    /**
     * Supprime une carte de crédit.
     * @param carteCreditId ID de la carte à supprimer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun supprimerCarteCredit(carteCreditId: String): Result<Unit>

    /**
     * Calcule les intérêts mensuels pour une carte de crédit.
     * @param carteCredit La carte de crédit
     * @return Montant des intérêts mensuels
     */
    fun calculerInteretsMensuels(carteCredit: CompteCredit): Double

    /**
     * Calcule le paiement minimum requis pour une carte de crédit.
     * @param carteCredit La carte de crédit
     * @return Montant du paiement minimum
     */
    fun calculerPaiementMinimum(carteCredit: CompteCredit): Double

    /**
     * Calcule le crédit disponible sur une carte.
     * @param carteCredit La carte de crédit
     * @return Montant du crédit disponible
     */
    fun calculerCreditDisponible(carteCredit: CompteCredit): Double

    /**
     * Calcule le taux d'utilisation de la carte de crédit.
     * @param carteCredit La carte de crédit
     * @return Pourcentage d'utilisation (0.0 à 1.0)
     */
    fun calculerTauxUtilisation(carteCredit: CompteCredit): Double

    /**
     * Calcule le temps nécessaire pour rembourser la dette avec un paiement mensuel fixe.
     * @param carteCredit La carte de crédit
     * @param paiementMensuel Montant du paiement mensuel
     * @return Nombre de mois pour rembourser (null si impossible)
     */
    fun calculerTempsRemboursement(carteCredit: CompteCredit, paiementMensuel: Double): Int?

    /**
     * Génère un plan de remboursement pour une carte de crédit.
     * @param carteCredit La carte de crédit
     * @param paiementMensuel Montant du paiement mensuel souhaité
     * @param nombreMoisMax Nombre maximum de mois à calculer
     * @return Liste des paiements mensuels avec détails
     */
    fun genererPlanRemboursement(
        carteCredit: CompteCredit,
        paiementMensuel: Double,
        nombreMoisMax: Int = 60
    ): List<PaiementPlanifie>

    /**
     * Applique les intérêts mensuels sur une carte de crédit.
     * @param carteCreditId ID de la carte de crédit
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun appliquerInteretsMensuels(carteCreditId: String): Result<Unit>

    /**
     * Calcule la prochaine date d'échéance pour une carte de crédit.
     * @param carteCredit La carte de crédit
     * @return Date de la prochaine échéance
     */
    fun calculerProchaineEcheance(carteCredit: CompteCredit): Date

    /**
     * Calcule la prochaine date de facturation pour une carte de crédit.
     * @param carteCredit La carte de crédit
     * @return Date de la prochaine facturation
     */
    fun calculerProchaineFacturation(carteCredit: CompteCredit): Date

    /**
     * Vérifie si une carte de crédit a des paiements en retard.
     * @param carteCredit La carte de crédit
     * @return true si en retard, false sinon
     */
    fun estEnRetard(carteCredit: CompteCredit): Boolean

    /**
     * Calcule les récompenses/cashback pour une carte de crédit.
     * @param carteCredit La carte de crédit
     * @param montantDepense Montant des dépenses
     * @return Montant des récompenses
     */
    fun calculerRecompenses(carteCredit: CompteCredit, montantDepense: Double): Double

    /**
     * Récupère l'historique des transactions pour une carte de crédit.
     * @param carteCreditId ID de la carte de crédit
     * @return Liste des transactions liées à cette carte
     */
    suspend fun recupererHistoriqueTransactions(carteCreditId: String): Result<List<Transaction>>
}

/**
 * Modèle représentant un paiement dans un plan de remboursement.
 */
data class PaiementPlanifie(
    val mois: Int,
    val date: Date,
    val paiementTotal: Double,
    val montantCapital: Double,
    val montantInterets: Double,
    val soldeRestant: Double
)
