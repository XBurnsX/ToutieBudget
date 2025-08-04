# 🚀 Plan de Migration Room + PocketBase - Étape par Étape

## 📋 Vue d'ensemble
Migration progressive et sécurisée vers Room + PocketBase. Chaque étape doit être **100% fonctionnelle** avant de passer à la suivante.

## 🎯 Objectifs
- ✅ Migration sécurisée sans risque de perte de données
- ✅ Test complet à chaque étape
- ✅ Performance améliorée progressivement
- ✅ Fonctionnement hors ligne par étapes

---

## 📊 Ordre de migration (du plus simple au plus complexe)

### 1. **Comptes** (base de l'application)
### 2. **Catégories** (structure des enveloppes)
### 3. **Enveloppes** (dépend des catégories)
### 4. **Tiers** (simple, indépendant)
### 5. **Allocations mensuelles** (complexe, dépend des enveloppes)
### 6. **Transactions** (le plus complexe, dépend de tout)

---

## 🏦 ÉTAPE 1 : COMPTES

### 1.1 Ajouter les dépendances Room
```kotlin
// app/build.gradle.kts
dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
}
```

### 1.2 Créer l'entité Compte
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/entities/CompteEntity.kt
@Entity(tableName = "comptes")
data class CompteEntity(
    @PrimaryKey val id: String,
    val nom: String,
    val solde: Double,
    val pretAPlacer: Double,
    val couleur: String,
    val type: String,
    val utilisateurId: String,
    val createdAt: Date,
    val updatedAt: Date,
    val syncStatus: String = "SYNCED"
)
```

### 1.3 Créer le DAO Compte
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/dao/CompteDao.kt
@Dao
interface CompteDao {
    @Query("SELECT * FROM comptes WHERE utilisateurId = :utilisateurId ORDER BY nom")
    suspend fun getAllComptes(utilisateurId: String): List<CompteEntity>
    
    @Query("SELECT * FROM comptes WHERE id = :compteId")
    suspend fun getCompteById(compteId: String): CompteEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompte(compte: CompteEntity)
    
    @Update
    suspend fun updateCompte(compte: CompteEntity)
    
    @Delete
    suspend fun deleteCompte(compte: CompteEntity)
    
    @Query("UPDATE comptes SET solde = solde + :variation WHERE id = :compteId")
    suspend fun updateSolde(compteId: String, variation: Double)
    
    @Query("UPDATE comptes SET pretAPlacer = pretAPlacer + :variation WHERE id = :compteId")
    suspend fun updatePretAPlacer(compteId: String, variation: Double)
}
```

### 1.4 Créer le mapper Compte
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/mappers/CompteMapper.kt
object CompteMapper {
    fun toEntity(compte: Compte): CompteEntity {
        return CompteEntity(
            id = compte.id,
            nom = compte.nom,
            solde = compte.solde,
            pretAPlacer = compte.pretAPlacer,
            couleur = compte.couleur,
            type = compte.type,
            utilisateurId = compte.utilisateurId,
            createdAt = compte.createdAt,
            updatedAt = compte.updatedAt,
            syncStatus = "SYNCED"
        )
    }
    
    fun toModel(entity: CompteEntity): Compte {
        return Compte(
            id = entity.id,
            nom = entity.nom,
            solde = entity.solde,
            pretAPlacer = entity.pretAPlacer,
            couleur = entity.couleur,
            type = entity.type,
            utilisateurId = entity.utilisateurId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
```

### 1.5 Créer la base de données temporaire (comptes uniquement)
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/CompteDatabase.kt
@Database(
    entities = [CompteEntity::class],
    version = 1
)
abstract class CompteDatabase : RoomDatabase() {
    abstract fun compteDao(): CompteDao
}
```

### 1.6 Créer le service PocketBase pour comptes
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/services/ComptePocketBaseService.kt
class ComptePocketBaseService(
    private val pocketBaseClient: PocketBaseClient
) {
    suspend fun getAllComptes(): List<Compte> {
        // Logique existante de récupération depuis PocketBase
        return pocketBaseClient.getAllComptes()
    }
    
    suspend fun updateCompte(compte: Compte): Result<Unit> {
        // Logique existante de mise à jour vers PocketBase
        return pocketBaseClient.updateCompte(compte)
    }
    
    suspend fun createCompte(compte: Compte): Result<Compte> {
        // Logique existante de création vers PocketBase
        return pocketBaseClient.createCompte(compte)
    }
    
    suspend fun deleteCompte(compteId: String): Result<Unit> {
        // Logique existante de suppression vers PocketBase
        return pocketBaseClient.deleteCompte(compteId)
    }
}
```

### 1.7 Créer le repository hybride pour comptes
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/repositories/impl/CompteRepositoryImpl.kt
class CompteRepositoryImpl(
    private val compteDao: CompteDao,
    private val pocketBaseService: ComptePocketBaseService
) : CompteRepository {
    
    override suspend fun recupererTousLesComptes(): Result<List<Compte>> {
        return try {
            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                ?: return Result.failure(Exception("Utilisateur non connecté"))
            
            val entities = compteDao.getAllComptes(utilisateurId)
            val comptes = entities.map { CompteMapper.toModel(it) }
            
            Result.success(comptes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun mettreAJourSoldeAvecVariationEtPretAPlacer(
        compteId: String,
        collectionCompte: String,
        variationSolde: Double,
        mettreAJourPretAPlacer: Boolean
    ): Result<Unit> {
        return try {
            // Mise à jour locale immédiate
            compteDao.updateSolde(compteId, variationSolde)
            
            if (mettreAJourPretAPlacer) {
                compteDao.updatePretAPlacer(compteId, variationSolde)
            }
            
            // Synchronisation en arrière-plan
            viewModelScope.launch {
                try {
                    // Logique de sync vers PocketBase
                    val compte = compteDao.getCompteById(compteId)
                    compte?.let { entity ->
                        val model = CompteMapper.toModel(entity)
                        pocketBaseService.updateCompte(model)
                    }
                } catch (e: Exception) {
                    println("❌ Échec sync solde: ${e.message}")
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Autres méthodes...
}
```

### 1.8 Mettre à jour AppModule (comptes uniquement)
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/di/AppModule.kt
object AppModule {
    
    // ===== ROOM DATABASE (comptes uniquement) =====
    private val compteDatabase: CompteDatabase by lazy {
        Room.databaseBuilder(
            context,
            CompteDatabase::class.java,
            "comptes.db"
        ).build()
    }
    
    // ===== DAOs =====
    private val compteDao: CompteDao by lazy { compteDatabase.compteDao() }
    
    // ===== SERVICES =====
    private val comptePocketBaseService: ComptePocketBaseService by lazy { 
        ComptePocketBaseService(PocketBaseClient) 
    }
    
    // ===== REPOSITORIES HYBRIDES =====
    private val compteRepository: CompteRepository by lazy { 
        CompteRepositoryImpl(compteDao, comptePocketBaseService)
    }
    
    // ===== REPOSITORIES EXISTANTS (inchangés) =====
    private val enveloppeRepository: EnveloppeRepository by lazy { EnveloppeRepositoryImpl() }
    private val categorieRepository: CategorieRepository by lazy { CategorieRepositoryImpl() }
    private val transactionRepository: TransactionRepository by lazy { TransactionRepositoryImpl() }
    // etc...
    
    // ===== GETTERS =====
    fun getCompteRepository(): CompteRepository = compteRepository
    // Autres getters...
}
```

### 1.9 Service de migration pour comptes
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/services/CompteMigrationService.kt
class CompteMigrationService(
    private val compteDao: CompteDao,
    private val pocketBaseService: ComptePocketBaseService
) {
    
    suspend fun migrateComptes() {
        try {
            println("🔄 Migration des comptes...")
            
            // Récupérer tous les comptes depuis PocketBase
            val comptes = pocketBaseService.getAllComptes()
            
            // Insérer en base locale
            comptes.forEach { compte ->
                val entity = CompteMapper.toEntity(compte)
                compteDao.insertCompte(entity)
            }
            
            println("✅ Migration des comptes terminée!")
            
        } catch (e: Exception) {
            println("❌ Erreur migration comptes: ${e.message}")
            throw e
        }
    }
}
```

### 1.10 Tests pour comptes
```kotlin
// app/src/test/java/com/xburnsx/toutiebudget/data/repositories/CompteRepositoryTest.kt
@RunWith(AndroidJUnit4::class)
class CompteRepositoryTest {
    
    private lateinit var database: CompteDatabase
    private lateinit var compteDao: CompteDao
    private lateinit var repository: CompteRepository
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CompteDatabase::class.java).build()
        compteDao = database.compteDao()
        repository = CompteRepositoryImpl(compteDao, mockPocketBaseService)
    }
    
    @Test
    fun testRecupererTousLesComptes() = runTest {
        // Test de récupération des comptes
        val result = repository.recupererTousLesComptes()
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun testMettreAJourSolde() = runTest {
        // Test de mise à jour du solde
        val result = repository.mettreAJourSoldeAvecVariationEtPretAPlacer(
            "compte1", "collection", 100.0, true
        )
        assertTrue(result.isSuccess)
    }
    
    @After
    fun cleanup() {
        database.close()
    }
}
```

### 1.11 Validation de l'étape 1
- [ ] **Test unitaire** : Tous les tests passent
- [ ] **Test d'intégration** : Création/modification/suppression de comptes
- [ ] **Test de performance** : Vérifier que les opérations sont rapides (< 10ms)
- [ ] **Test de synchronisation** : Vérifier que PocketBase est mis à jour
- [ ] **Test hors ligne** : Vérifier que l'app fonctionne sans internet

---

## 🏷️ ÉTAPE 2 : CATÉGORIES

### 2.1 Créer l'entité Categorie
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/entities/CategorieEntity.kt
@Entity(tableName = "categories")
data class CategorieEntity(
    @PrimaryKey val id: String,
    val nom: String,
    val couleur: String,
    val ordre: Int,
    val utilisateurId: String,
    val createdAt: Date,
    val updatedAt: Date,
    val syncStatus: String = "SYNCED"
)
```

### 2.2 Créer le DAO Categorie
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/dao/CategorieDao.kt
@Dao
interface CategorieDao {
    @Query("SELECT * FROM categories WHERE utilisateurId = :utilisateurId ORDER BY ordre")
    suspend fun getAllCategories(utilisateurId: String): List<CategorieEntity>
    
    @Query("SELECT * FROM categories WHERE id = :categorieId")
    suspend fun getCategorieById(categorieId: String): CategorieEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategorie(categorie: CategorieEntity)
    
    @Update
    suspend fun updateCategorie(categorie: CategorieEntity)
    
    @Delete
    suspend fun deleteCategorie(categorie: CategorieEntity)
}
```

### 2.3 Créer le mapper Categorie
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/mappers/CategorieMapper.kt
object CategorieMapper {
    fun toEntity(categorie: Categorie): CategorieEntity {
        return CategorieEntity(
            id = categorie.id,
            nom = categorie.nom,
            couleur = categorie.couleur,
            ordre = categorie.ordre,
            utilisateurId = categorie.utilisateurId,
            createdAt = categorie.createdAt,
            updatedAt = categorie.updatedAt,
            syncStatus = "SYNCED"
        )
    }
    
    fun toModel(entity: CategorieEntity): Categorie {
        return Categorie(
            id = entity.id,
            nom = entity.nom,
            couleur = entity.couleur,
            ordre = entity.ordre,
            utilisateurId = entity.utilisateurId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
```

### 2.4 Mettre à jour la base de données (ajouter catégories)
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/CompteCategorieDatabase.kt
@Database(
    entities = [CompteEntity::class, CategorieEntity::class],
    version = 1
)
abstract class CompteCategorieDatabase : RoomDatabase() {
    abstract fun compteDao(): CompteDao
    abstract fun categorieDao(): CategorieDao
}
```

### 2.5 Créer le repository hybride pour catégories
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/repositories/impl/CategorieRepositoryImpl.kt
class CategorieRepositoryImpl(
    private val categorieDao: CategorieDao,
    private val pocketBaseService: CategoriePocketBaseService
) : CategorieRepository {
    
    override suspend fun recupererToutesLesCategories(): Result<List<Categorie>> {
        return try {
            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id
                ?: return Result.failure(Exception("Utilisateur non connecté"))
            
            val entities = categorieDao.getAllCategories(utilisateurId)
            val categories = entities.map { CategorieMapper.toModel(it) }
            
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Autres méthodes...
}
```

### 2.6 Validation de l'étape 2
- [ ] **Test unitaire** : Tous les tests passent
- [ ] **Test d'intégration** : CRUD des catégories
- [ ] **Test de performance** : Opérations rapides
- [ ] **Test de synchronisation** : Sync avec PocketBase
- [ ] **Test hors ligne** : Fonctionnement sans internet

---

## 📁 ÉTAPE 3 : ENVELOPPES

### 3.1 Créer l'entité Enveloppe
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/entities/EnveloppeEntity.kt
@Entity(tableName = "enveloppes")
data class EnveloppeEntity(
    @PrimaryKey val id: String,
    val nom: String,
    val couleur: String,
    val categorieId: String,
    val utilisateurId: String,
    val createdAt: Date,
    val updatedAt: Date,
    val syncStatus: String = "SYNCED"
)
```

### 3.2 Créer le DAO Enveloppe
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/dao/EnveloppeDao.kt
@Dao
interface EnveloppeDao {
    @Query("SELECT * FROM enveloppes WHERE utilisateurId = :utilisateurId ORDER BY ordre")
    suspend fun getAllEnveloppes(utilisateurId: String): List<EnveloppeEntity>
    
    @Query("SELECT * FROM enveloppes WHERE id = :enveloppeId")
    suspend fun getEnveloppeById(enveloppeId: String): EnveloppeEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnveloppe(enveloppe: EnveloppeEntity)
    
    @Update
    suspend fun updateEnveloppe(enveloppe: EnveloppeEntity)
    
    @Delete
    suspend fun deleteEnveloppe(enveloppe: EnveloppeEntity)
}
```

### 3.3 Mettre à jour la base de données (ajouter enveloppes)
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/CompteCategorieEnveloppeDatabase.kt
@Database(
    entities = [CompteEntity::class, CategorieEntity::class, EnveloppeEntity::class],
    version = 1
)
abstract class CompteCategorieEnveloppeDatabase : RoomDatabase() {
    abstract fun compteDao(): CompteDao
    abstract fun categorieDao(): CategorieDao
    abstract fun enveloppeDao(): EnveloppeDao
}
```

### 3.4 Validation de l'étape 3
- [ ] **Test unitaire** : Tous les tests passent
- [ ] **Test d'intégration** : CRUD des enveloppes
- [ ] **Test de performance** : Opérations rapides
- [ ] **Test de synchronisation** : Sync avec PocketBase
- [ ] **Test hors ligne** : Fonctionnement sans internet

---

## 👥 ÉTAPE 4 : TIERS

### 4.1 Créer l'entité Tiers
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/entities/TiersEntity.kt
@Entity(tableName = "tiers")
data class TiersEntity(
    @PrimaryKey val id: String,
    val nom: String,
    val utilisateurId: String,
    val createdAt: Date,
    val updatedAt: Date,
    val syncStatus: String = "SYNCED"
)
```

### 4.2 Validation de l'étape 4
- [ ] **Test unitaire** : Tous les tests passent
- [ ] **Test d'intégration** : CRUD des tiers
- [ ] **Test de performance** : Opérations rapides
- [ ] **Test de synchronisation** : Sync avec PocketBase
- [ ] **Test hors ligne** : Fonctionnement sans internet

---

## 📊 ÉTAPE 5 : ALLOCATIONS MENSUELLES

### 5.1 Créer l'entité AllocationMensuelle
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/entities/AllocationMensuelleEntity.kt
@Entity(tableName = "allocations_mensuelles")
data class AllocationMensuelleEntity(
    @PrimaryKey val id: String,
    val utilisateurId: String,
    val enveloppeId: String,
    val mois: Date,
    val solde: Double,
    val alloue: Double,
    val depense: Double,
    val compteSourceId: String,
    val collectionCompteSource: String,
    val createdAt: Date,
    val updatedAt: Date,
    val syncStatus: String = "SYNCED"
)
```

### 5.2 Validation de l'étape 5
- [ ] **Test unitaire** : Tous les tests passent
- [ ] **Test d'intégration** : CRUD des allocations
- [ ] **Test de performance** : Opérations rapides
- [ ] **Test de synchronisation** : Sync avec PocketBase
- [ ] **Test hors ligne** : Fonctionnement sans internet

---

## 💰 ÉTAPE 6 : TRANSACTIONS

### 6.1 Créer l'entité Transaction
```kotlin
// app/src/main/java/com/xburnsx/toutiebudget/data/database/entities/TransactionEntity.kt
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val montant: Double,
    val date: Date,
    val note: String?,
    val compteId: String,
    val collectionCompte: String,
    val enveloppeId: String?,
    val allocationMensuelleId: String?,
    val tiers: String?,
    val utilisateurId: String,
    val createdAt: Date,
    val updatedAt: Date,
    val syncStatus: String = "SYNCED"
)
```

### 6.2 Validation de l'étape 6
- [ ] **Test unitaire** : Tous les tests passent
- [ ] **Test d'intégration** : CRUD des transactions
- [ ] **Test de performance** : Opérations rapides
- [ ] **Test de synchronisation** : Sync avec PocketBase
- [ ] **Test hors ligne** : Fonctionnement sans internet

---

## 🎯 RÈGLES DE VALIDATION À CHAQUE ÉTAPE

### ✅ Tests obligatoires
1. **Test unitaire** : Tous les tests passent
2. **Test d'intégration** : CRUD complet fonctionne
3. **Test de performance** : Opérations < 10ms
4. **Test de synchronisation** : PocketBase mis à jour
5. **Test hors ligne** : App fonctionne sans internet
6. **Test de régression** : Fonctionnalités existantes intactes

### 🚫 Interdictions
- ❌ **PASSER À L'ÉTAPE SUIVANTE** si tests échouent
- ❌ **MODIFIER** les étapes précédentes
- ❌ **DÉPLOYER** en production avant validation complète

### ✅ Critères de succès
- ✅ Tous les tests passent
- ✅ Performance améliorée
- ✅ Synchronisation fonctionnelle
- ✅ Fonctionnement hors ligne
- ✅ Interface utilisateur fluide

---

## 📅 Planning détaillé

### Semaine 1 : Comptes
- **Jour 1-2** : Implémentation Room pour comptes
- **Jour 3-4** : Tests et validation
- **Jour 5** : Correction des bugs

### Semaine 2 : Catégories + Enveloppes
- **Jour 1-2** : Implémentation catégories
- **Jour 3-4** : Implémentation enveloppes
- **Jour 5** : Tests et validation

### Semaine 3 : Tiers + Allocations
- **Jour 1-2** : Implémentation tiers
- **Jour 3-4** : Implémentation allocations
- **Jour 5** : Tests et validation

### Semaine 4 : Transactions
- **Jour 1-3** : Implémentation transactions
- **Jour 4-5** : Tests complets et optimisation

### Semaine 5 : Finalisation
- **Jour 1-2** : Tests d'intégration complets
- **Jour 3-4** : Optimisation performance
- **Jour 5** : Déploiement en production

---

## 🎯 Résultat final

Après 5 semaines, vous aurez :
- ✅ **Performance optimale** (16ms au lieu de 800ms)
- ✅ **Fonctionnement hors ligne complet**
- ✅ **Synchronisation transparente**
- ✅ **Interface utilisateur fluide**
- ✅ **Migration sécurisée** sans perte de données

Chaque étape est **indépendante et testée**, garantissant une migration sûre et progressive ! 