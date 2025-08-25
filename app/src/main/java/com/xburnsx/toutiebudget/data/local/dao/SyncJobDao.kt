package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.xburnsx.toutiebudget.data.local.sync.SyncJob

@Dao
interface SyncJobDao {
    @Insert
    suspend fun addJob(job: SyncJob)

    @Query("SELECT * FROM sync_jobs ORDER BY id ASC")
    suspend fun getPendingJobs(): List<SyncJob>

    @Delete
    suspend fun deleteJob(job: SyncJob)
}
