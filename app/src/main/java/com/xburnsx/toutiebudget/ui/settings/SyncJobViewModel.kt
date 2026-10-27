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
            else -> type
        }
    }
}
