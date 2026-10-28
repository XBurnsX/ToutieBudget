package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.utils.OperationAtomique
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * ARA6 : archivage automatique FACULTATIF (désactivé par défaut, réglage dans Paramètres).
 *
 * Seulement les comptes réellement soldés : solde à 0 au cent, aucun Prêt à placer, aucun titre,
 * aucune dette restante, et aucune transaction depuis [JOURS_INACTIVITE] jours. Un compte sans
 * aucune transaction n'est jamais touché (il vient peut-être d'être créé). Retour en arrière :
 * le compte garde son ID et tout son historique ; « Réactiver » dans Archives le remet tel quel.
 */
class ArchivageAutomatique(
    private val compteRepository: CompteRepository,
    private val transactionRepository: TransactionRepository,
    private val operationAtomique: OperationAtomique,
    private val realtimeSyncService: RealtimeSyncService
) {
    companion object {
        const val JOURS_INACTIVITE = 60L
        const val RAISON = "Archivé automatiquement : soldé et sans activité depuis 60 jours"

        /** Solde exactement nul au cent, sans argent placé ni dette restante. Jamais une dette encore due. */
        fun estSolde(compte: Compte): Boolean {
            fun zero(v: Double) = v.isFinite() && abs(v) < 0.005
            return when (compte) {
                is CompteCheque -> zero(compte.solde) && zero(compte.pretAPlacer)
                is CompteCredit -> zero(compte.soldeUtilise)
                is CompteDette -> zero(compte.soldeDette)
                is CompteInvestissement -> zero(compte.solde) && compte.details.positions.all { it.quantite <= 0.0 }
            }
        }

        /** Critères complets : soldé ET dernière activité assez ancienne (null = aucune activité = jamais). */
        fun estAArchiver(compte: Compte, derniereActivite: LocalDate?, aujourdHui: LocalDate): Boolean =
            !compte.estArchive && estSolde(compte) && derniereActivite != null &&
                !derniereActivite.isAfter(aujourdHui.minusDays(JOURS_INACTIVITE))
    }

    /** Archive les comptes qui remplissent tous les critères ; retourne leurs noms. */
    suspend fun executer(aujourdHui: LocalDate = LocalDate.now()): List<String> {
        val zone = ZoneId.systemDefault()
        val candidats = compteRepository.recupererTousLesComptes().getOrThrow().filter { !it.estArchive && estSolde(it) }
        val archives = mutableListOf<String>()
        for (compte in candidats) {
            val derniere = transactionRepository.recupererTransactionsPourCompte(compte.id, compte.collection)
                .getOrNull()?.maxOfOrNull { it.date }?.toInstant()?.atZone(zone)?.toLocalDate()
            if (!estAArchiver(compte, derniere, aujourdHui)) continue
            val fait = runCatching {
                operationAtomique.executer {
                    // Revérifié sur la version à jour, dans la transaction : rien d'archivé par erreur
                    val aJour = compteRepository.getCompteById(compte.id, compte.collection)
                        ?: return@executer false
                    if (!estAArchiver(aJour, derniere, aujourdHui)) return@executer false
                    val archive = when (aJour) {
                        is CompteCheque -> aJour.copy(estArchive = true, raisonArchivage = RAISON)
                        is CompteCredit -> aJour.copy(estArchive = true, raisonArchivage = RAISON)
                        is CompteDette -> aJour.copy(estArchive = true, raisonArchivage = RAISON)
                        is CompteInvestissement -> aJour.copy(estArchive = true, raisonArchivage = RAISON)
                    }
                    compteRepository.mettreAJourCompte(archive).getOrThrow()
                    true
                }
            }.getOrDefault(false)
            if (fait) archives += compte.nom
        }
        if (archives.isNotEmpty()) realtimeSyncService.declencherMiseAJourComptes()
        return archives
    }
}
