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
        AllocationMensuelle::class,
        HistoriqueAllocation::class
    ],
    version = 3, // 🆕 INCREMENTÉ : Ajout de l'historique des allocations
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
    abstract fun historiqueAllocationDao(): HistoriqueAllocationDao
    
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // 🆕 AJOUT : Migrations
                // .fallbackToDestructiveMigration() // DÉSACTIVÉ : Utiliser les migrations pour préserver les données
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
        
        // 🆕 MIGRATION : Version 2 vers 3 - Ajout de la table historique_allocation
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    android.util.Log.d("ToutieBudget", "🔄 MIGRATION 2→3 : Début de la migration vers version 3")
                    
                    // Vérifier si la table n'existe pas déjà
                    val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='historique_allocation'")
                    val tableExists = cursor.count > 0
                    cursor.close()
                    
                    android.util.Log.d("ToutieBudget", "🔄 MIGRATION 2→3 : Table historique_allocation existe déjà: $tableExists")
                    
                    if (!tableExists) {
                        android.util.Log.d("ToutieBudget", "🔄 MIGRATION 2→3 : Création de la table historique_allocation")
                        
                        // Créer la table historique_allocation
                        database.execSQL("""
                            CREATE TABLE historique_allocation (
                                id TEXT NOT NULL PRIMARY KEY,
                                utilisateurId TEXT NOT NULL,
                                compteId TEXT NOT NULL,
                                collectionCompte TEXT NOT NULL,
                                enveloppeId TEXT NOT NULL,
                                enveloppeNom TEXT NOT NULL,
                                typeAction TEXT NOT NULL,
                                description TEXT NOT NULL,
                                montant REAL NOT NULL,
                                soldeAvant REAL NOT NULL,
                                soldeApres REAL NOT NULL,
                                pretAPlacerAvant REAL NOT NULL,
                                pretAPlacerApres REAL NOT NULL,
                                dateAction TEXT NOT NULL,
                                allocationId TEXT NOT NULL,
                                transactionId TEXT,
                                virementId TEXT,
                                details TEXT
                            )
                        """)
                        
                        android.util.Log.d("ToutieBudget", "✅ MIGRATION 2→3 : Table historique_allocation créée avec succès")
                    } else {
                        android.util.Log.d("ToutieBudget", "ℹ️ MIGRATION 2→3 : Table historique_allocation existe déjà, pas de création nécessaire")
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("ToutieBudget", "❌ MIGRATION 2→3 : Erreur lors de la migration: ${e.message}")
                    
                    // En cas d'erreur, créer la table quand même
                    try {
                        database.execSQL("""
                            CREATE TABLE IF NOT EXISTS historique_allocation (
                                id TEXT NOT NULL PRIMARY KEY,
                                utilisateurId TEXT NOT NULL,
                                compteId TEXT NOT NULL,
                                collectionCompte TEXT NOT NULL,
                                enveloppeId TEXT NOT NULL,
                                enveloppeNom TEXT NOT NULL,
                                typeAction TEXT NOT NULL,
                                description TEXT NOT NULL,
                                montant REAL NOT NULL,
                                soldeAvant REAL NOT NULL,
                                soldeApres REAL NOT NULL,
                                pretAPlacerAvant REAL NOT NULL,
                                pretAPlacerApres REAL NOT NULL,
                                dateAction TEXT NOT NULL,
                                allocationId TEXT NOT NULL,
                                transactionId TEXT,
                                virementId TEXT,
                                details TEXT
                            )
                        """)
                        android.util.Log.d("ToutieBudget", "✅ MIGRATION 2→3 : Table créée avec fallback")
                    } catch (fallbackError: Exception) {
                        android.util.Log.e("ToutieBudget", "❌ MIGRATION 2→3 : Erreur critique lors du fallback: ${fallbackError.message}")
                        throw fallbackError
                    }
                }
                
                android.util.Log.d("ToutieBudget", "✅ MIGRATION 2→3 : Migration terminée avec succès")
            }
        }
    }
}
