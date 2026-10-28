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
        HistoriqueAllocation::class,
        AliasBancaire::class,
        OperationBancaireEnAttente::class
    ],
    version = 10, // 4 : SyncJobs ; 5 : cartes ; 6 : dettes ; 7 : placements ; 10 : date/raison d'archivage
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
    abstract fun aliasBancaireDao(): AliasBancaireDao
    abstract fun operationBancaireEnAttenteDao(): OperationBancaireEnAttenteDao
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10) // 🆕 AJOUT : Migrations
                // .fallbackToDestructiveMigration() // DÉSACTIVÉ : Utiliser les migrations pour préserver les données
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // BANQ-03 : file d'import bancaire purement informative, sans effet financier.
        // ARA4 : date et raison d'archivage ; GN3 : montant reporté. Colonnes ajoutées sans toucher aux données.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf("comptes_cheques", "comptes_credits", "comptes_dettes", "comptes_investissement", "enveloppes")
                    .forEach { table ->
                        database.execSQL("ALTER TABLE $table ADD COLUMN archive_le TEXT")
                        database.execSQL("ALTER TABLE $table ADD COLUMN raison_archivage TEXT")
                    }
                // GN3 : montant reporté au mois suivant, 0 pour les mois déjà passés
                database.execSQL("ALTER TABLE allocation_mensuelle ADD COLUMN montant_reporte REAL NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS operations_bancaires_attente (
                        id TEXT NOT NULL PRIMARY KEY,
                        utilisateur_id TEXT NOT NULL,
                        compte_id TEXT NOT NULL,
                        collection_compte TEXT NOT NULL,
                        source TEXT NOT NULL,
                        id_externe TEXT NOT NULL,
                        date_operation TEXT NOT NULL,
                        montant_centimes INTEGER NOT NULL,
                        libelle_brut TEXT NOT NULL,
                        statut_bancaire TEXT NOT NULL,
                        decision TEXT NOT NULL,
                        tiers_id TEXT,
                        verification_requise INTEGER NOT NULL,
                        montant_final_centimes INTEGER
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_operations_bancaires_attente_utilisateur_id_compte_id_source_id_externe " +
                    "ON operations_bancaires_attente (utilisateur_id, compte_id, source, id_externe)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_operations_bancaires_attente_utilisateur_id_decision " +
                    "ON operations_bancaires_attente (utilisateur_id, decision)"
                )
            }
        }

        // BANQ-02 : migration additive, sans modifier les comptes ni les écritures existantes.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS alias_bancaires (
                        id TEXT NOT NULL PRIMARY KEY,
                        utilisateur_id TEXT NOT NULL,
                        compte_id TEXT,
                        libelle_brut TEXT NOT NULL,
                        tiers_id TEXT NOT NULL,
                        active INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alias_bancaires_utilisateur_id_compte_id " +
                    "ON alias_bancaires (utilisateur_id, compte_id)"
                )
            }
        }

        // 🆕 MIGRATION : Version 1 vers 2 - Ajout du champ recordId
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Ajouter la colonne recordId à la table sync_jobs
                database.execSQL("ALTER TABLE sync_jobs ADD COLUMN recordId TEXT NOT NULL DEFAULT ''")
            }
        }
        
        // MIGRATION : Version 3 vers 4 - utilisateur et diagnostic des SyncJobs (SJ7, SJ10, SJ11)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sync_jobs ADD COLUMN utilisateurId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE sync_jobs ADD COLUMN tentatives INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sync_jobs ADD COLUMN derniereErreur TEXT")
                database.execSQL("ALTER TABLE sync_jobs ADD COLUMN codeHttp INTEGER")
                database.execSQL("ALTER TABLE sync_jobs ADD COLUMN derniereTentative INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sync_jobs ADD COLUMN bloque INTEGER NOT NULL DEFAULT 0")
            }
        }

        // MIGRATION : Version 4 vers 5 - profil, taux, cycle et relevés des cartes de crédit (section 10)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE comptes_credits ADD COLUMN details_carte_json TEXT")
            }
        }

        // MIGRATION : Version 5 vers 6 - mode, contrat, lien d'enveloppe et ajustements des dettes (section 11)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE comptes_dettes ADD COLUMN details_dette_json TEXT")
            }
        }

        // MIGRATION : Version 6 vers 7 - suivi des placements (section 12)
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE comptes_investissement ADD COLUMN details_investissement_json TEXT")
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
