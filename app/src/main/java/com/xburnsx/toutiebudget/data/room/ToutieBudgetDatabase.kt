package com.xburnsx.toutiebudget.data.room

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.xburnsx.toutiebudget.data.room.entities.*
import com.xburnsx.toutiebudget.data.room.daos.*
/**
 * Base de données Room principale pour ToutieBudget.
 * Cette base de données contiendra toutes les entités locales et la table SyncJob.
 */
@Database(
    entities = [
        SyncJob::class,
        CompteCheque::class,
        CompteCredit::class,
        CompteDette::class,
        CompteInvestissement::class,
        Transaction::class,
        Categorie::class,
        Enveloppe::class,
        Tiers::class,
        PretPersonnel::class,
        AllocationMensuelle::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ToutieBudgetDatabase : RoomDatabase() {
    
    // DAO pour la gestion des tâches de synchronisation
    abstract fun syncJobDao(): SyncJobDao
    
    // DAOs pour les entités principales
    abstract fun compteChequeDao(): CompteChequeDao
    abstract fun compteCreditDao(): CompteCreditDao
    abstract fun compteDetteDao(): CompteDetteDao
    abstract fun compteInvestissementDao(): CompteInvestissementDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categorieDao(): CategorieDao
    abstract fun enveloppeDao(): EnveloppeDao
    abstract fun tiersDao(): TiersDao
    abstract fun pretPersonnelDao(): PretPersonnelDao
    abstract fun allocationMensuelleDao(): AllocationMensuelleDao
    
    companion object {
        @Volatile
        private var INSTANCE: ToutieBudgetDatabase? = null
        
        fun getDatabase(context: Context): ToutieBudgetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ToutieBudgetDatabase::class.java,
                    "toutiebudget_database"
                )
                .fallbackToDestructiveMigration() // En développement, on peut perdre les données
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
