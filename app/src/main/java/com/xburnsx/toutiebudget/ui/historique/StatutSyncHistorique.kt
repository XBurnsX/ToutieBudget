package com.xburnsx.toutiebudget.ui.historique

import com.google.gson.JsonParser
import com.xburnsx.toutiebudget.data.room.entities.SyncJob

/**
 * EA6 : état OBSERVABLE de la file locale, sans déduire une
 * synchronisation distante lorsqu'une tâche a été purgée.
 */
object StatutSyncHistorique {
    fun libelle(transactionId: String, utilisateurId: String, jobs: List<SyncJob>): String {
        val applicables = jobs.filter { job ->
            job.type == "TRANSACTION" &&
                (job.utilisateurId.isBlank() || job.utilisateurId == utilisateurId) &&
                (job.recordId == transactionId || (
                    job.recordId.isBlank() && runCatching {
                        JsonParser.parseString(job.dataJson).asJsonObject
                            .get("id")?.asString == transactionId
                    }.getOrDefault(false)
                ))
        }
        if (applicables.isEmpty()) return "Non vérifié (aucune tâche conservée)"
        return when {
            applicables.any { it.status == "FAILED" } ->
                "Échec d'envoi local"
            applicables.any { it.status == "PENDING" } ->
                "En attente d'envoi"
            applicables.any { it.status == "IN_PROGRESS" } ->
                "En cours d'envoi"
            applicables.all { it.status == "COMPLETED" } ->
                "Envoi marqué terminé dans la file locale"
            else -> "État de la file non reconnu"
        }
    }
}
