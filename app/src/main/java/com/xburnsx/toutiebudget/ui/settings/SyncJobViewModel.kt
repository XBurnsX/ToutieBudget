package com.xburnsx.toutiebudget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.room.daos.SyncJobDao
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncJobViewModel(
    private val syncJobDao: SyncJobDao
) : ViewModel() {
    
    private val _syncJobs = MutableStateFlow<List<SyncJob>>(emptyList())
    val syncJobs: StateFlow<List<SyncJob>> = _syncJobs.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    
    init {
        // SJ16 : l'interface suit les changements de Room, sans polling.
        viewModelScope.launch {
            _isLoading.value = true
            try {
                syncJobDao.getAllSyncJobs().collectLatest { jobs ->
                    _syncJobs.value = jobs
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Impossible d'observer la synchronisation : ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun loadSyncJobs() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val jobs = syncJobDao.getAllSyncJobs().first()
                _syncJobs.value = jobs
                
            } catch (e: Exception) {
                _error.value = "Erreur lors du chargement: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearCompletedJobs() {
        viewModelScope.launch {
            try {
                syncJobDao.deleteCompletedSyncJobs()
                loadSyncJobs() // Recharger la liste
                
            } catch (e: Exception) {
                _error.value = "Erreur lors de la suppression: ${e.message}"
            }
        }
    }
    
    fun clearAllJobs() {
        viewModelScope.launch {
            try {
                // Supprimer toutes les tâches une par une
                val allJobs = syncJobDao.getAllSyncJobs().first()
                // SJ8 : supprimer les tâches non terminées perdrait des
                // écritures hors ligne et pourrait ressusciter des données.
                if (allJobs.any { it.status != "COMPLETED" }) {
                    _error.value = "Effacement refusé : des opérations ne sont pas encore synchronisées."
                    return@launch
                }
                allJobs.forEach { job ->
                    syncJobDao.deleteSyncJob(job)
                }
                loadSyncJobs() // Recharger la liste
                
            } catch (e: Exception) {
                _error.value = "Erreur lors de la suppression: ${e.message}"
            }
        }
    }
    
    /**
     * Retente toutes les tâches échouées en les remettant en statut PENDING
     */
    fun retryFailedJobs() {
        viewModelScope.launch {
            try {
                val failedJobs = syncJobDao.getAllSyncJobs().first().filter { it.status == "FAILED" }
                failedJobs.forEach { job ->
                    syncJobDao.reactiver(job.id)
                }
                com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
                loadSyncJobs() // Recharger la liste
                
            } catch (e: Exception) {
                _error.value = "Erreur lors de la tentative de retry: ${e.message}"
            }
        }
    }
    
    /**
     * Retente une tâche échouée spécifique en la remettant en statut PENDING
     */
    fun retrySingleFailedJob(jobId: String) {
        viewModelScope.launch {
            try {
                syncJobDao.reactiver(jobId)
                com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
                loadSyncJobs() // Recharger la liste
                
            } catch (e: Exception) {
                _error.value = "Erreur lors de la tentative de retry: ${e.message}"
            }
        }
    }
    
    /** SJA5 : résultat de la comparaison Room ↔ PocketBase (lecture seule). */
    private val _comparaison = MutableStateFlow<String?>(null)
    val comparaison: StateFlow<String?> = _comparaison.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun effacerMessage() { _message.value = null }

    /** SJA1 : relance la file sans annuler le worker en cours (APPEND_OR_REPLACE). */
    fun synchroniserMaintenant(context: android.content.Context) {
        com.xburnsx.toutiebudget.workers.SyncWorkManager.demarrerSynchronisation(context)
        _message.value = if (com.xburnsx.toutiebudget.utils.DiagnosticDonnees.estEnLigne(context))
            "Synchronisation demandée." else "Hors ligne : elle partira au retour d'Internet."
    }

    /** SJ6 : conflit — envoyer quand même la version de cet appareil. */
    fun utiliserMaVersion(context: android.content.Context, job: SyncJob) {
        viewModelScope.launch {
            com.xburnsx.toutiebudget.workers.EtatFichesSync.forcerTache(context, job.id)
            syncJobDao.reactiver(job.id)
            com.xburnsx.toutiebudget.workers.SyncWorkManager.demarrerSynchronisation(context)
            _message.value = "Ta version va remplacer celle du serveur."
        }
    }

    /** SJ6 : conflit — abandonner le changement local et reprendre la fiche du serveur. */
    fun garderVersionServeur(context: android.content.Context, job: SyncJob) {
        viewModelScope.launch {
            val collection = com.xburnsx.toutiebudget.workers.SyncCollections.collectionPourType(job.type)
            syncJobDao.supprimerTache(job.id)
            com.xburnsx.toutiebudget.workers.EtatFichesSync.oublierTache(context, job.id)
            _message.value = com.xburnsx.toutiebudget.di.AppModule.provideRealtimeSyncService()
                .reappliquerDepuisServeur(collection, job.recordId)
                .fold({ "La version du serveur est maintenant sur cet appareil." },
                    { "Changement local abandonné, mais la fiche du serveur n'a pas pu être relue : ${it.message}" })
        }
    }

    private companion object { const val SAUT = "\n" }

    fun comparerAvecServeur(context: android.content.Context) {
        _comparaison.value = "Comparaison en cours…"
        viewModelScope.launch {
            _comparaison.value = com.xburnsx.toutiebudget.data.services.ComparaisonServeur.comparer(context).fold({ lignes ->
                lignes.joinToString(SAUT) { l ->
                    (if (l.coherente) "✓ " else "⚠ ") + "${l.collection} : ${l.ici} ici, ${l.serveur} serveur" +
                        (if (l.enAttente > 0) ", ${l.enAttente} en attente d'envoi" else "") +
                        (if (l.absentesDuServeur > 0) ", ${l.absentesDuServeur} absente(s) du serveur" else "") +
                        (if (l.absentesIci > 0) ", ${l.absentesIci} absente(s) de cet appareil" else "")
                } + SAUT + "Rien n'a été modifié."
            }, { "Comparaison impossible : ${it.message}" })
        }
    }

    /** Aperçu de la mise à jour depuis le serveur, en attente de confirmation (null = rien à confirmer). */
    private val _apercuServeur = MutableStateFlow<String?>(null)
    val apercuServeur: StateFlow<String?> = _apercuServeur.asStateFlow()

    /**
     * Deux sens : d'abord envoyer ce qui attend, puis préparer la réception de la version du serveur.
     * Rien ne change avant la confirmation.
     */
    fun preparerMiseAJourDepuisServeur(context: android.content.Context) {
        com.xburnsx.toutiebudget.workers.SyncWorkManager.demarrerSynchronisation(context)
        _comparaison.value = "Lecture du serveur…"
        viewModelScope.launch {
            com.xburnsx.toutiebudget.di.AppModule.provideRealtimeSyncService()
                .mettreAJourDepuisServeur(appliquer = false)
                .onSuccess { bilans ->
                    _comparaison.value = null
                    val retraits = bilans.filter { it.aRetirerIci > 0 }
                    val ignores = bilans.filter { it.retraitsIgnores }
                    _apercuServeur.value = buildString {
                        append("Les fiches du serveur vont remplacer celles de cet appareil ")
                        append("(sauf les changements pas encore envoyés).")
                        if (retraits.isEmpty()) {
                            append(SAUT + SAUT + "Rien à retirer de cet appareil.")
                        } else {
                            append(SAUT + SAUT + "Supprimées sur le serveur, donc retirées d'ici :")
                            retraits.forEach { append(SAUT + "• ${it.collection} : ${it.aRetirerIci}") }
                        }
                        if (ignores.isNotEmpty()) {
                            append(SAUT + SAUT + "Par sécurité, rien n'est retiré de : " +
                                ignores.joinToString { it.collection } + " (serveur vide pour cette collection).")
                        }
                    }
                }
                .onFailure { _comparaison.value = "Mise à jour impossible : ${it.message}. Rien n'a été modifié." }
        }
    }

    fun annulerMiseAJourDepuisServeur() { _apercuServeur.value = null }

    fun confirmerMiseAJourDepuisServeur() {
        _apercuServeur.value = null
        _comparaison.value = "Mise à jour en cours…"
        viewModelScope.launch {
            com.xburnsx.toutiebudget.di.AppModule.provideRealtimeSyncService()
                .mettreAJourDepuisServeur(appliquer = true)
                .onSuccess { bilans ->
                    val retirees = bilans.sumOf { it.aRetirerIci }
                    _comparaison.value = null
                    _message.value = "Cet appareil est à jour avec le serveur" +
                        (if (retirees > 0) " ($retirees fiche(s) supprimée(s) sur le serveur retirée(s) d'ici)." else ".")
                }
                .onFailure { _comparaison.value = "Mise à jour interrompue : ${it.message}" }
        }
    }

    fun formatDate(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp))
    }
    
    fun getStatusColor(status: String): androidx.compose.ui.graphics.Color {
        return when (status) {
            "PENDING" -> androidx.compose.ui.graphics.Color(0xFFFFA500) // Orange
            "IN_PROGRESS" -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Bleu
            "COMPLETED" -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Vert
            "FAILED" -> androidx.compose.ui.graphics.Color(0xFFF44336) // Rouge
            else -> androidx.compose.ui.graphics.Color.Gray
        }
    }
    
    fun getStatusText(status: String): String {
        return when (status) {
            "PENDING" -> "En attente"
            "IN_PROGRESS" -> "En cours"
            "COMPLETED" -> "Terminé"
            "FAILED" -> "Échoué"
            else -> status
        }
    }
    
    fun getActionText(action: String): String {
        return when (action) {
            "CREATE" -> "Créer"
            "UPDATE" -> "Modifier"
            "DELETE" -> "Supprimer"
            else -> action
        }
    }
    
    fun getTypeText(type: String): String {
        return when (type) {
            "TRANSACTION" -> "Transaction"
            "COMPTE" -> "Compte"
            "CATEGORIE" -> "Catégorie"
            "ENVELOPPE" -> "Enveloppe"
            "TIERS" -> "Tiers"
            "PRET_PERSONNEL" -> "Prêt personnel"
            "ALLOCATION_MENSUELLE" -> "Allocation mensuelle"
            "COMPTE_CHEQUE" -> "Compte chèque"
            "COMPTE_CREDIT" -> "Carte de crédit"
            "COMPTE_DETTE" -> "Dette"
            "COMPTE_INVESTISSEMENT" -> "Placement"
            "HISTORIQUE_ALLOCATION" -> "Journal du budget"
            "ALIAS_BANCAIRE" -> "Alias bancaire"
            "OPERATION_BANCAIRE" -> "Opération bancaire"
            else -> type
        }
    }
}
