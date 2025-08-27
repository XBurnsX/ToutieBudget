package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.CarteCreditRepository
import com.xburnsx.toutiebudget.data.repositories.PaiementPlanifie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.*

class CarteCreditRepositoryImpl(
    private val compteRepository: CompteRepository,
    private val transactionRepository: com.xburnsx.toutiebudget.data.repositories.TransactionRepository
) : CarteCreditRepository {

    override suspend fun recupererCartesCredit(): Result<List<CompteCredit>> = withContext(Dispatchers.IO) {
        try {
            val tousLesComptes = compteRepository.recupererTousLesComptes().getOrThrow()
            val cartesCredit = tousLesComptes.filterIsInstance<CompteCredit>()
            Result.success(cartesCredit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerCarteCredit(carteCredit: CompteCredit): Result<Unit> {
        return compteRepository.creerCompte(carteCredit)
    }

    override suspend fun mettreAJourCarteCredit(carteCredit: CompteCredit): Result<Unit> {
        return compteRepository.mettreAJourCompte(carteCredit)
    }

    override suspend fun supprimerCarteCredit(carteCreditId: String): Result<Unit> {
        return compteRepository.supprimerCompte(carteCreditId, "comptes_credits")
    }

    override fun calculerInteretsMensuels(carteCredit: CompteCredit): Double {
        val taux = carteCredit.tauxInteret ?: 0.0 // Changé interet vers tauxInteret
        val tauxMensuel = taux / 100.0 / 12.0
        return abs(carteCredit.solde) * tauxMensuel
    }

    override fun calculerPaiementMinimum(carteCredit: CompteCredit): Double {
        // Si un paiement minimum personnalisé est défini, l'utiliser
        if (carteCredit.paiementMinimum == null || carteCredit.paiementMinimum <= 0) {
            // Sinon, calculer le paiement minimum standard
            val dette = abs(carteCredit.solde)
            val interetsMensuels = calculerInteretsMensuels(carteCredit)
            val fraisMensuels = carteCredit.totalFraisMensuels

            // Paiement minimum = 2% du solde ou 25$, le plus élevé des deux
            // Plus les intérêts du mois et les frais fixes
            val paiementBase = max(dette * 0.02, 25.0)
            return paiementBase + interetsMensuels + fraisMensuels
        }
        return carteCredit.paiementMinimum
    }

    override fun calculerCreditDisponible(carteCredit: CompteCredit): Double {
        val detteCourante = abs(carteCredit.solde)
        return max(0.0, carteCredit.limiteCredit - detteCourante)
    }

    override fun calculerTauxUtilisation(carteCredit: CompteCredit): Double {
        if (carteCredit.limiteCredit <= 0) return 0.0
        val detteCourante = abs(carteCredit.solde)
        return min(1.0, detteCourante / carteCredit.limiteCredit)
    }

    override fun calculerTempsRemboursement(carteCredit: CompteCredit, paiementMensuel: Double): Int? {
        val dette = abs(carteCredit.solde)
        val taux = carteCredit.tauxInteret ?: 0.0 // Changé interet vers tauxInteret
        val tauxMensuel = taux / 100.0 / 12.0
        val fraisMensuels = carteCredit.totalFraisMensuels

        if (dette <= 0) return 0
        if (paiementMensuel <= calculerInteretsMensuels(carteCredit) + fraisMensuels) return null // Impossible à rembourser

        if (tauxMensuel == 0.0) {
            // Sans intérêts, mais avec frais fixes
            val paiementNet = paiementMensuel - fraisMensuels
            if (paiementNet <= 0) return null
            return ceil(dette / paiementNet).toInt()
        }

        // Formule mathématique pour calculer le nombre de paiements avec frais fixes
        val paiementNet = paiementMensuel - fraisMensuels
        if (paiementNet <= 0) return null
        
        val numerateur = ln(1 + (dette * tauxMensuel) / paiementNet)
        val denominateur = ln(1 + tauxMensuel)

        return ceil(numerateur / denominateur).toInt()
    }

    override fun genererPlanRemboursement(
        carteCredit: CompteCredit,
        paiementMensuel: Double,
        nombreMoisMax: Int
    ): List<PaiementPlanifie> {
        val plan = mutableListOf<PaiementPlanifie>()
        var soldeRestant = abs(carteCredit.solde)
        val taux = carteCredit.tauxInteret ?: 0.0 // Changé interet vers tauxInteret
        val tauxMensuel = taux / 100.0 / 12.0

        val calendar = Calendar.getInstance()

        for (mois in 1..nombreMoisMax) {
            if (soldeRestant <= 0.01) break // Dette remboursée

            // Calculer les frais moyens pour cette durée estimée
            val dureeEstimee = nombreMoisMax - mois + 1
            val fraisMensuelsMoyens = carteCredit.calculerFraisMensuelsMoyens(dureeEstimee)

            val interetsMois = soldeRestant * tauxMensuel
            val paiementDisponible = paiementMensuel - fraisMensuelsMoyens
            val capitalMois = min(paiementDisponible - interetsMois, soldeRestant)
            val paiementReel = interetsMois + capitalMois + fraisMensuelsMoyens

            soldeRestant -= capitalMois

            calendar.add(Calendar.MONTH, 1)

            plan.add(
                PaiementPlanifie(
                    mois = mois,
                    date = calendar.time,
                    paiementTotal = paiementReel,
                    montantCapital = capitalMois,
                    montantInterets = interetsMois,
                    soldeRestant = max(0.0, soldeRestant)
                )
            )
        }

        return plan
    }

    override suspend fun appliquerInteretsMensuels(carteCreditId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val compte = compteRepository.getCompteById(carteCreditId, "comptes_credits") as? CompteCredit
                ?: return@withContext Result.failure(Exception("Carte de crédit non trouvée"))

            val interetsMensuels = calculerInteretsMensuels(compte)
            val nouveauSolde = compte.solde - interetsMensuels // Le solde devient plus négatif

            val compteAvecInterets = compte.copy(soldeUtilise = nouveauSolde)
            compteRepository.mettreAJourCompte(compteAvecInterets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun calculerProchaineEcheance(carteCredit: CompteCredit): Date? {
        // Non implémenté pour l'instant → retourner null de manière sûre
        return null
    }

    override fun calculerProchaineFacturation(carteCredit: CompteCredit): Date? {
        // Non implémenté pour l'instant → retourner null de manière sûre
        return null
    }

    override fun estEnRetard(carteCredit: CompteCredit): Boolean {
        // Implémentation prudente par défaut: pas en retard si aucune info d'échéance
        val dateEcheance = calculerProchaineEcheance(carteCredit) ?: return false
        return Date().after(dateEcheance)
    }

    override fun calculerRecompenses(carteCredit: CompteCredit, montantDepense: Double): Double {
        // Implémentation simple: 1% de cashback par défaut si le taux n'est pas défini
        if (montantDepense <= 0) return 0.0
        val tauxCashback = 0.01
        return montantDepense * tauxCashback
    }

    override suspend fun recupererHistoriqueTransactions(carteCreditId: String): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        try {
            // Récupérer les transactions du compte carte de crédit via la collection adéquate
            transactionRepository.recupererTransactionsPourCompte(carteCreditId, "comptes_credits")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
