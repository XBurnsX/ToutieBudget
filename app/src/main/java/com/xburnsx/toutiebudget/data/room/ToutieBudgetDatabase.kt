package com.xburnsx.toutiebudget.data.room

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.xburnsx.toutiebudget.data.room.entities.*
import com.xburnsx.toutiebudget.data.room.daos.*
import com.xburnsx.toutiebudget.data.room.converters.DateStringConverter
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
    version = 2, // 🆕 INCREMENTÉ : Ajout du champ recordId
    exportSchema = false
)
@TypeConverters(DateStringConverter::class)
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
                .addMigrations(MIGRATION_1_2) // 🆕 AJOUT : Migration pour recordId
                .fallbackToDestructiveMigration() // En développement, on peut perdre les données
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // 🆕 MIGRATION : Version 1 vers 2 - Ajout du champ recordId
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Ajouter la colonne recordId à la table sync_jobs
                database.execSQL("ALTER TABLE sync_jobs ADD COLUMN recordId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
