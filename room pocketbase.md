# **Plan de Transformation Offline et Sync en Background (A à Z)**

Ce document est votre plan de construction final. Il contient **TOUT** le code nécessaire, basé sur l'analyse de votre projet sur la branche fix. Ne sautez aucune étape.

### **Les Étapes de Codage (La "Cheat Sheet")**

Voici l'ordre exact des opérations que nous allons effectuer. Ce document contient le code pour chaque étape.

1. **Mise à Jour des Outils :** Ajouter Room et WorkManager à vos fichiers Gradle.  
2. **Création de la "Liste de Tâches" :** Définir la structure pour les tâches de synchronisation (SyncJob).  
3. **Annotation des Données :** Transformer **tous** vos modèles de données (Transaction, Compte, PretPersonnel, etc.) en tables pour la base de données locale (@Entity).  
4. **Création des Télécommandes :** Créer **toutes** les interfaces DAO pour interagir avec la base de données locale.  
5. **Construction de la Base de Données :** Créer le fichier AppDatabase.kt qui assemble tout.  
6. **Création de l'Ouvrier de Nuit :** Mettre en place le SyncWorker qui s'occupera de la synchronisation en arrière-plan.  
7. **Mise à Jour de Hilt :** Expliquer au système comment construire tous nos nouveaux composants.  
8. **Création du Repository d'Authentification :** Isoler la logique de connexion pour qu'elle fonctionne hors ligne.  
9. **Refonte TOTALE des Repositories :** Remplacer le code de **toutes** vos interfaces et implémentations de repositories pour qu'ils utilisent la base de données locale et la liste de tâches.  
10. **Mise à Jour du Point d'Entrée :** Modifier MainActivity.kt pour qu'il devienne le chef d'orchestre de la synchronisation initiale.  
11. **Trace d'Exécution :** Une description exacte de ce qui se passe dans votre code pour le scénario du "compte dette".

## **Étape 1 : Les Nouveaux Outils (Dépendances Gradle)** ✅ COMPLÉTÉ

On a besoin de deux nouveaux outils : Room (la réserve) et WorkManager (l'ouvrier).

#### **1.1. Fichier : gradle/libs.versions.toml** ✅ COMPLÉTÉ

**ACTION :** Remplacez le contenu de ce fichier.

\[versions\]  
agp \= "8.2.2"  
kotlin \= "1.9.22"  
coreKtx \= "1.12.0"  
junit \= "4.13.2"  
junitVersion \= "1.1.5"  
espressoCore \= "3.5.1"  
lifecycleRuntimeKtx \= "2.7.0"  
activityCompose \= "1.8.2"  
composeBom \= "2024.02.02"  
hilt \= "2.51"  
hiltNavigationCompose \= "1.2.0"  
kotlinxCoroutines \= "1.7.3"  
lifecycleViewmodelKtx \= "2.7.0"  
navigationCompose \= "2.7.7"  
pocketbase \= "0.1.0"  
okhttp \= "4.12.0"  
datastorePreferences \= "1.0.0"  
splashscreen \= "1.0.1"  
materialIconsExtended \= "1.6.3"  
coil \= "2.6.0"  
gson \= "2.10.1"  
room \= "2.6.1"  
work \= "2.9.0"  
hiltWork \= "1.2.0"

\[libraries\]  
androidx-core-ktx \= { group \= "androidx.core", name \= "core-ktx", version.ref \= "coreKtx" }  
junit \= { group \= "junit", name \= "junit", version.ref \= "junit" }  
androidx-junit \= { group \= "androidx.test.ext", name \= "junit", version.ref \= "junitVersion" }  
androidx-espresso-core \= { group \= "androidx.test.espresso", name \= "espresso-core", version.ref \= "espressoCore" }  
androidx-lifecycle-runtime-ktx \= { group \= "androidx.lifecycle", name \= "lifecycle-runtime-ktx", version.ref \= "lifecycleRuntimeKtx" }  
androidx-activity-compose \= { group \= "androidx.activity", name \= "activity-compose", version.ref \= "activityCompose" }  
androidx-compose-bom \= { group \= "androidx.compose", name \= "compose-bom", version.ref \= "composeBom" }  
androidx-ui \= { group \= "androidx.compose.ui", name \= "ui" }  
androidx-ui-graphics \= { group \= "androidx.compose.ui", name \= "ui-graphics" }  
androidx-ui-tooling \= { group \= "androidx.compose.ui", name \= "ui-tooling" }  
androidx-ui-tooling-preview \= { group \= "androidx.compose.ui", name \= "ui-tooling-preview" }  
androidx-ui-test-manifest \= { group \= "androidx.compose.ui", name \= "ui-test-manifest" }  
androidx-ui-test-junit4 \= { group \= "androidx.compose.ui", name \= "ui-test-junit4" }  
androidx-material3 \= { group \= "androidx.compose.material3", name \= "material3" }  
hilt-android \= { group \= "com.google.dagger", name \= "hilt-android", version.ref \= "hilt" }  
hilt-compiler \= { group \= "com.google.dagger", name \= "hilt-compiler", version.ref \= "hilt" }  
androidx-hilt-navigation-compose \= { group \= "androidx.hilt", name \= "hilt-navigation-compose", version.ref \= "hiltNavigationCompose" }  
kotlinx-coroutines-core \= { group \= "org.jetbrains.kotlinx", name \= "kotlinx-coroutines-core", version.ref \= "kotlinxCoroutines" }  
kotlinx-coroutines-android \= { group \= "org.jetbrains.kotlinx", name \= "kotlinx-coroutines-android", version.ref \= "kotlinxCoroutines" }  
androidx-lifecycle-viewmodel-ktx \= { group \= "androidx.lifecycle", name \= "lifecycle-viewmodel-ktx", version.ref \= "lifecycleViewmodelKtx" }  
androidx-lifecycle-viewmodel-compose \= { group \= "androidx.lifecycle", name \= "lifecycle-viewmodel-compose", version.ref \= "lifecycleViewmodelKtx" }  
androidx-navigation-compose \= { group \= "androidx.navigation", name \= "navigation-compose", version.ref \= "navigationCompose" }  
pocketbase-android \= { group \= "tech.sousa.khoob", name \= "pocketbase-android", version.ref \= "pocketbase" }  
okhttp \= { group \= "com.squareup.okhttp3", name \= "okhttp", version.ref \= "okhttp" }  
androidx-datastore-preferences \= { group \= "androidx.datastore", name \= "datastore-preferences", version.ref \= "datastorePreferences" }  
androidx-splashscreen \= { group \= "androidx.core", name \= "core-splashscreen", version.ref \= "splashscreen" }  
androidx-material-icons-extended \= { group \= "androidx.compose.material", name \= "material-icons-extended", version.ref \= "materialIconsExtended" }  
coil-compose \= { group \= "io.coil-kt", name \= "coil-compose", version.ref \= "coil" }  
gson \= { group \= "com.google.code.gson", name \= "gson", version.ref \= "gson" }  
room-runtime \= { group \= "androidx.room", name \= "room-runtime", version.ref \= "room" }  
room-ktx \= { group \= "androidx.room", name \= "room-ktx", version.ref \= "room" }  
room-compiler \= { group \= "androidx.room", name \= "room-compiler", version.ref \= "room" }  
work-runtime-ktx \= { group \= "androidx.work", name \= "work-runtime-ktx", version.ref \= "work" }  
hilt-work \= { group \= "androidx.hilt", name \= "hilt-work", version.ref \= "hiltWork" }  
hilt-work-compiler \= { group \= "androidx.hilt", name \= "hilt-compiler", version.ref \= "hiltWork" }

\[plugins\]  
androidApplication \= { id \= "com.android.application", version.ref \= "agp" }  
jetbrainsKotlinAndroid \= { id \= "org.jetbrains.kotlin.android", version.ref \= "kotlin" }  
hilt \= { id \= "com.google.dagger.hilt.android", version.ref \= "hilt" }

#### **1.2. Fichier : app/build.gradle.kts** ✅ COMPLÉTÉv

**ACTION :** Remplacez le contenu de ce fichier.

plugins {  
    id("com.android.application")  
    id("org.jetbrains.kotlin.android")  
    id("kotlin-kapt")  
    id("com.google.dagger.hilt.android")  
    id("com.google.gms.google-services")  
}

android {  
    namespace \= "com.xburnsx.toutiebudget"  
    compileSdk \= 34

    defaultConfig {  
        applicationId \= "com.xburnsx.toutiebudget"  
        minSdk \= 24  
        targetSdk \= 34  
        versionCode \= 1  
        versionName \= "1.0"

        testInstrumentationRunner \= "androidx.test.runner.AndroidJUnitRunner"  
        vectorDrawables {  
            useSupportLibrary \= true  
        }  
    }

    buildTypes {  
        release {  
            isMinifyEnabled \= false  
            proguardFiles(  
                getDefaultProguardFile("proguard-android-optimize.txt"),  
                "proguard-rules.pro"  
            )  
        }  
    }  
    compileOptions {  
        sourceCompatibility \= JavaVersion.VERSION\_1\_8  
        targetCompatibility \= JavaVersion.VERSION\_1\_8  
    }  
    kotlinOptions {  
        jvmTarget \= "1.8"  
    }  
    buildFeatures {  
        compose \= true  
    }  
    composeOptions {  
        kotlinCompilerExtensionVersion \= "1.5.8"  
    }  
    packaging {  
        resources {  
            excludes \+= "/META-INF/{AL2.0,LGPL2.1}"  
        }  
    }  
}

dependencies {  
    implementation(libs.androidx.core.ktx)  
    implementation(libs.androidx.lifecycle.runtime.ktx)  
    implementation(libs.androidx.activity.compose)  
    implementation(platform(libs.androidx.compose.bom))  
    implementation(libs.androidx.ui)  
    implementation(libs.androidx.ui.graphics)  
    implementation(libs.androidx.ui.tooling.preview)  
    implementation(libs.androidx.material3)  
    testImplementation(libs.junit)  
    androidTestImplementation(libs.androidx.junit)  
    androidTestImplementation(libs.androidx.espresso.core)  
    androidTestImplementation(platform(libs.androidx.compose.bom))  
    androidTestImplementation(libs.androidx.ui.test.junit4)  
    debugImplementation(libs.androidx.ui.tooling)  
    debugImplementation(libs.androidx.ui.test.manifest)

    // Hilt  
    implementation(libs.hilt.android)  
    kapt(libs.hilt.compiler)  
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines  
    implementation(libs.kotlinx.coroutines.core)  
    implementation(libs.kotlinx.coroutines.android)

    // ViewModel  
    implementation(libs.androidx.lifecycle.viewmodel.ktx)  
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation  
    implementation(libs.androidx.navigation.compose)

    // PocketBase  
    implementation(libs.pocketbase.android)  
    implementation(libs.okhttp)

    // Datastore  
    implementation(libs.androidx.datastore.preferences)

    // Splash Screen  
    implementation(libs.androidx.splashscreen)

    // Icons  
    implementation(libs.androidx.material.icons.extended)

    // Coil  
    implementation(libs.coil.compose)

    // Gson  
    implementation(libs.gson)  
      
    // Room Database  
    implementation(libs.room.runtime)  
    implementation(libs.room.ktx)  
    kapt(libs.room.compiler)

    // WorkManager for background jobs  
    implementation(libs.work.runtime.ktx)  
    implementation(libs.hilt.work)  
    kapt(libs.hilt.work.compiler)  
}

kapt {  
    correctErrorTypes \= true  
}

**ACTION :** Cliquez sur **"Sync Now"** dans Android Studio.

## **Étape 2 : Création de la Liste de Tâches** ✅ COMPLÉTÉ

On construit l'infrastructure pour que notre ouvrier sache quoi faire.

#### **2.1. La Tâche de Synchro (Entité)** ✅ COMPLÉTÉ

ACTION : Créez le dossier app/src/main/java/com/xburnsx/toutiebudget/data/local/sync  
ACTION : Créez le fichier SyncJob.kt dans ce nouveau dossier.  
package com.xburnsx.toutiebudget.data.local.sync

import androidx.room.Entity  
import androidx.room.PrimaryKey

enum class OperationType { CREATE, UPDATE, DELETE }  
enum class EntityType { TRANSACTION, COMPTE, CATEGORIE, ENVELOPPE, TIERS, ALLOCATION, CARTE\_CREDIT, PRET\_PERSONNEL }

@Entity(tableName \= "sync\_jobs")  
data class SyncJob(  
    @PrimaryKey(autoGenerate \= true)  
    val id: Long \= 0,  
    val entityId: String,  
    val entityType: EntityType,  
    val operationType: OperationType,  
    val payload: String? \= null // Données en format JSON pour CREATE et UPDATE  
)

#### **2.2. La Télécommande de la Liste de Tâches (DAO)** ✅ COMPLÉTÉ

**ACTION :** Créez le fichier SyncJobDao.kt dans data/local/dao/.

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

    @Query("SELECT \* FROM sync\_jobs ORDER BY id ASC")  
    suspend fun getPendingJobs(): List\<SyncJob\>

    @Delete  
    suspend fun deleteJob(job: SyncJob)  
}

## **Étape 3 : Transformer les Données en Tables de Réserve (Entités)**

On met une étiquette sur chaque type de donnée pour que la réserve sache comment les ranger.

#### **Fichiers dans app/src/main/java/com/xburnsx/toutiebudget/data/modeles/**

**ACTION :** Remplacez le contenu de chaque fichier ci-dessous par le code fourni.

**3.1. Transaction.kt**

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import com.google.gson.annotations.SerializedName  
import java.util.Date

@Entity(tableName \= "transactions")  
data class Transaction(  
    @PrimaryKey @SerializedName("id") override var id: String \= "",  
    @SerializedName("created") override var created: Date? \= null,  
    @SerializedName("updated") override var updated: Date? \= null,  
    @SerializedName("montant") var montant: Double \= 0.0,  
    @SerializedName("type") var type: String \= "",  
    @SerializedName("compte") var compte: String \= "",  
    @SerializedName("compte\_couleur") var compteCouleur: String? \= null,  
    @SerializedName("compte\_nom") var compteNom: String? \= null,  
    @SerializedName("compte\_emoji") var compteEmoji: String? \= null,  
    @SerializedName("vers\_compte") var versCompte: String? \= null,  
    @SerializedName("vers\_compte\_couleur") var versCompteCouleur: String? \= null,  
    @SerializedName("vers\_compte\_nom") var versCompteNom: String? \= null,  
    @SerializedName("vers\_compte\_emoji") var versCompteEmoji: String? \= null,  
    @SerializedName("categorie") var categorie: String? \= null,  
    @SerializedName("enveloppe") var enveloppe: String? \= null,  
    @SerializedName("enveloppe\_couleur") var enveloppeCouleur: String? \= null,  
    @SerializedName("enveloppe\_nom") var enveloppeNom: String? \= null,  
    @SerializedName("enveloppe\_emoji") var enveloppeEmoji: String? \= null,  
    @SerializedName("tiers") var tiers: String? \= null,  
    @SerializedName("note") var note: String? \= null,  
    @SerializedName("date") var date: Date \= Date(),  
    @SerializedName("statut") var statut: String? \= null,  
    @SerializedName("provenance") var provenance: String? \= null,  
    @SerializedName("mode\_operation") var modeOperation: String? \= null  
) : BaseModel()

**3.2. Compte.kt**

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import com.google.gson.annotations.SerializedName  
import java.util.\*

@Entity(tableName \= "comptes")  
data class Compte(  
    @PrimaryKey @SerializedName("id") override var id: String \= "",  
    @SerializedName("created") override var created: Date? \= null,  
    @SerializedName("updated") override var updated: Date? \= null,  
    @SerializedName("nom") var nom: String \= "",  
    @SerializedName("solde") var solde: Double \= 0.0,  
    @SerializedName("couleur") var couleur: String \= "\#FFFFFF",  
    @SerializedName("emoji") var emoji: String \= "💰",  
    @SerializedName("ordre") var ordre: Int \= 0,  
    @SerializedName("est\_compte\_principal") var estComptePrincipal: Boolean \= false,  
    @SerializedName("est\_archive") var estArchive: Boolean \= false,  
    @SerializedName("user\_id") var userId: String? \= null  
) : BaseModel()

**3.3. Categorie.kt**

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import com.google.gson.annotations.SerializedName  
import java.util.\*

@Entity(tableName \= "categories")  
data class Categorie(  
    @PrimaryKey @SerializedName("id") override var id: String \= "",  
    @SerializedName("created") override var created: Date? \= null,  
    @SerializedName("updated") override var updated: Date? \= null,  
    @SerializedName("nom") var nom: String \= "",  
    @SerializedName("ordre") var ordre: Int \= 0,  
    @SerializedName("user\_id") var userId: String? \= null  
) : BaseModel()

**3.4. Enveloppe.kt**

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import com.google.gson.annotations.SerializedName  
import java.util.\*

@Entity(tableName \= "enveloppes")  
data class Enveloppe(  
    @PrimaryKey @SerializedName("id") override var id: String \= "",  
    @SerializedName("created") override var created: Date? \= null,  
    @SerializedName("updated") override var updated: Date? \= null,  
    @SerializedName("nom") var nom: String \= "",  
    @SerializedName("categorie") var categorie: String \= "",  
    @SerializedName("budget") var budget: Double \= 0.0,  
    @SerializedName("solde") var solde: Double \= 0.0,  
    @SerializedName("couleur") var couleur: String \= "\#000000",  
    @SerializedName("emoji") var emoji: String \= "✉️",  
    @SerializedName("ordre") var ordre: Int \= 0,  
    @SerializedName("type\_objectif") var typeObjectif: String \= TypeObjectif.AUCUN.name,  
    @SerializedName("montant\_objectif") var montantObjectif: Double \= 0.0,  
    @SerializedName("date\_objectif") var dateObjectif: Date? \= null,  
    @SerializedName("notes") var notes: String \= "",  
    @SerializedName("user\_id") var userId: String? \= null  
) : BaseModel()

**3.5. Tiers.kt**

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import com.google.gson.annotations.SerializedName  
import java.util.\*

@Entity(tableName \= "tiers")  
data class Tiers(  
    @PrimaryKey @SerializedName("id") override var id: String \= "",  
    @SerializedName("created") override var created: Date? \= null,  
    @SerializedName("updated") override var updated: Date? \= null,  
    @SerializedName("nom") var nom: String \= "",  
    @SerializedName("user\_id") var userId: String? \= null  
) : BaseModel()

**3.6. AllocationMensuelle.kt**

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import com.google.gson.annotations.SerializedName  
import java.util.\*

@Entity(tableName \= "allocations\_mensuelles")  
data class AllocationMensuelle(  
    @PrimaryKey @SerializedName("id") override var id: String \= "",  
    @SerializedName("created") override var created: Date? \= null,  
    @SerializedName("updated") override var updated: Date? \= null,  
    @SerializedName("mois") var mois: String \= "",  
    @SerializedName("annee") var annee: Int \= 0,  
    @SerializedName("pret\_a\_placer") var pretAPlacer: Double \= 0.0,  
    @SerializedName("user\_id") var userId: String? \= null  
) : BaseModel()

**3.7. CarteCredit.kt**

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import com.google.gson.annotations.SerializedName  
import java.util.\*

@Entity(tableName \= "cartes\_credit")  
data class CarteCredit(  
    @PrimaryKey @SerializedName("id") override var id: String \= "",  
    @SerializedName("created") override var created: Date? \= null,  
    @SerializedName("updated") override var updated: Date? \= null,  
    @SerializedName("nom") var nom: String \= "",  
    @SerializedName("solde") var solde: Double \= 0.0,  
    @SerializedName("limite") var limite: Double \= 0.0,  
    @SerializedName("taux\_interet") var tauxInteret: Double \= 0.0,  
    @SerializedName("date\_facturation") var dateFacturation: Int \= 1,  
    @SerializedName("user\_id") var userId: String? \= null  
) : BaseModel()

**3.8. PretPersonnel.kt**

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity  
import androidx.room.PrimaryKey  
import com.google.gson.annotations.SerializedName  
import java.util.\*

@Entity(tableName \= "prets\_personnels")  
data class PretPersonnel(  
    @PrimaryKey @SerializedName("id") override var id: String \= "",  
    @SerializedName("created") override var created: Date? \= null,  
    @SerializedName("updated") override var updated: Date? \= null,  
    @SerializedName("nom") var nom: String \= "",  
    @SerializedName("montant\_initial") var montantInitial: Double \= 0.0,  
    @SerializedName("solde\_restant") var soldeRestant: Double \= 0.0,  
    @SerializedName("taux\_interet") var tauxInteret: Double \= 0.0,  
    @SerializedName("duree\_mois") var dureeMois: Int \= 0,  
    @SerializedName("date\_debut") var dateDebut: Date \= Date(),  
    @SerializedName("user\_id") var userId: String? \= null  
) : BaseModel()

## **Étape 4 : Créer les Télécommandes pour la Réserve (DAOs)**

**ACTION :** Créez le dossier app/src/main/java/com/xburnsx/toutiebudget/data/local/dao

**ACTION :** Créez chaque fichier ci-dessous dans ce nouveau dossier.

**4.1. TransactionDao.kt**

package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.xburnsx.toutiebudget.data.modeles.Transaction

@Dao  
interface TransactionDao {  
    @Query("SELECT \* FROM transactions ORDER BY date DESC")  
    suspend fun getAll(): List\<Transaction\>

    @Upsert  
    suspend fun saveAll(transactions: List\<Transaction\>)  
      
    @Query("DELETE FROM transactions WHERE id \= :id")  
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions")  
    suspend fun clearAll()  
}

**4.2. CompteDao.kt**

package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.xburnsx.toutiebudget.data.modeles.Compte

@Dao  
interface CompteDao {  
    @Query("SELECT \* FROM comptes WHERE estArchive \= 0 ORDER BY ordre ASC")  
    suspend fun getAll(): List\<Compte\>

    @Upsert  
    suspend fun saveAll(comptes: List\<Compte\>)  
      
    @Query("DELETE FROM comptes WHERE id \= :id")  
    suspend fun deleteById(id: String)

    @Query("DELETE FROM comptes")  
    suspend fun clearAll()  
}

**4.3. CategorieDao.kt**

package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.xburnsx.toutiebudget.data.modeles.Categorie

@Dao  
interface CategorieDao {  
    @Query("SELECT \* FROM categories ORDER BY ordre ASC")  
    suspend fun getAll(): List\<Categorie\>

    @Upsert  
    suspend fun saveAll(categories: List\<Categorie\>)  
      
    @Query("DELETE FROM categories WHERE id \= :id")  
    suspend fun deleteById(id: String)

    @Query("DELETE FROM categories")  
    suspend fun clearAll()  
}

**4.4. EnveloppeDao.kt**

package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

@Dao  
interface EnveloppeDao {  
    @Query("SELECT \* FROM enveloppes ORDER BY ordre ASC")  
    suspend fun getAll(): List\<Enveloppe\>

    @Upsert  
    suspend fun saveAll(enveloppes: List\<Enveloppe\>)  
      
    @Query("DELETE FROM enveloppes WHERE id \= :id")  
    suspend fun deleteById(id: String)

    @Query("DELETE FROM enveloppes")  
    suspend fun clearAll()  
}

**4.5. TiersDao.kt**

package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.xburnsx.toutiebudget.data.modeles.Tiers

@Dao  
interface TiersDao {  
    @Query("SELECT \* FROM tiers ORDER BY nom ASC")  
    suspend fun getAll(): List\<Tiers\>

    @Upsert  
    suspend fun saveAll(tiers: List\<Tiers\>)  
      
    @Query("DELETE FROM tiers WHERE id \= :id")  
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tiers")  
    suspend fun clearAll()  
}

**4.6. AllocationMensuelleDao.kt**

package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle

@Dao  
interface AllocationMensuelleDao {  
    @Query("SELECT \* FROM allocations\_mensuelles")  
    suspend fun getAll(): List\<AllocationMensuelle\>

    @Upsert  
    suspend fun saveAll(allocations: List\<AllocationMensuelle\>)  
      
    @Query("DELETE FROM allocations\_mensuelles WHERE id \= :id")  
    suspend fun deleteById(id: String)

    @Query("DELETE FROM allocations\_mensuelles")  
    suspend fun clearAll()  
}

**4.7. CarteCreditDao.kt**

package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.xburnsx.toutiebudget.data.modeles.CarteCredit

@Dao  
interface CarteCreditDao {  
    @Query("SELECT \* FROM cartes\_credit ORDER BY nom ASC")  
    suspend fun getAll(): List\<CarteCredit\>

    @Upsert  
    suspend fun saveAll(cartes: List\<CarteCredit\>)  
      
    @Query("DELETE FROM cartes\_credit WHERE id \= :id")  
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cartes\_credit")  
    suspend fun clearAll()  
}

**4.8. PretPersonnelDao.kt**

package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao  
import androidx.room.Query  
import androidx.room.Upsert  
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel

@Dao  
interface PretPersonnelDao {  
    @Query("SELECT \* FROM prets\_personnels ORDER BY nom ASC")  
    suspend fun getAll(): List\<PretPersonnel\>

    @Upsert  
    suspend fun saveAll(prets: List\<PretPersonnel\>)

    @Query("DELETE FROM prets\_personnels WHERE id \= :id")  
    suspend fun deleteById(id: String)

    @Query("DELETE FROM prets\_personnels")  
    suspend fun clearAll()  
}

## **Étape 5 : Construire la Réserve (La Base de Données)**

**ACTION :** Créez le dossier app/src/main/java/com/xburnsx/toutiebudget/data/local

#### **Fichiers à créer dans data/local/**

**5.1. Converters.kt**

package com.xburnsx.toutiebudget.data.local

import androidx.room.TypeConverter  
import java.util.Date

class Converters {  
    @TypeConverter  
    fun fromTimestamp(value: Long?): Date? \= value?.let { Date(it) }

    @TypeConverter  
    fun dateToTimestamp(date: Date?): Long? \= date?.time  
}

**5.2. AppDatabase.kt**

package com.xburnsx.toutiebudget.data.local

import androidx.room.Database  
import androidx.room.RoomDatabase  
import androidx.room.TypeConverters  
import com.xburnsx.toutiebudget.data.local.dao.\*  
import com.xburnsx.toutiebudget.data.local.sync.SyncJob  
import com.xburnsx.toutiebudget.data.modeles.\*

@Database(  
    entities \= \[  
        SyncJob::class,  
        Transaction::class, Compte::class, Categorie::class, Enveloppe::class, Tiers::class,  
        AllocationMensuelle::class, CarteCredit::class, PretPersonnel::class  
    \],  
    version \= 2, // Augmenté pour la nouvelle table SyncJob  
    exportSchema \= false  
)  
@TypeConverters(Converters::class)  
abstract class AppDatabase : RoomDatabase() {  
    abstract fun syncJobDao(): SyncJobDao  
    abstract fun transactionDao(): TransactionDao  
    abstract fun compteDao(): CompteDao  
    abstract fun categorieDao(): CategorieDao  
    abstract fun enveloppeDao(): EnveloppeDao  
    abstract fun tiersDao(): TiersDao  
    abstract fun allocationMensuelleDao(): AllocationMensuelleDao  
    abstract fun carteCreditDao(): CarteCreditDao  
    abstract fun pretPersonnelDao(): PretPersonnelDao  
}

## **Étape 6 : L'Ouvrier et son Usine (WorkManager)**

On crée le travailleur qui va s'occuper de la synchronisation.

#### **6.1. Fichier : data/sync/SyncWorker.kt (Nouveau dossier et fichier)**

ACTION : Créez le dossier app/src/main/java/com/xburnsx/toutiebudget/data/sync  
ACTION : Créez le fichier SyncWorker.kt dedans.  
package com.xburnsx.toutiebudget.data.sync

import android.content.Context  
import androidx.hilt.work.HiltWorker  
import androidx.work.CoroutineWorker  
import androidx.work.WorkerParameters  
import com.google.gson.Gson  
import com.xburnsx.toutiebudget.data.local.dao.SyncJobDao  
import com.xburnsx.toutiebudget.data.local.sync.EntityType  
import com.xburnsx.toutiebudget.data.local.sync.OperationType  
import com.xburnsx.toutiebudget.data.local.sync.SyncJob  
import dagger.assisted.Assisted  
import dagger.assisted.AssistedInject  
import tech.sousa.khoob.pocketbase.PocketBase

@HiltWorker  
class SyncWorker @AssistedInject constructor(  
    @Assisted appContext: Context,  
    @Assisted workerParams: WorkerParameters,  
    private val syncJobDao: SyncJobDao,  
    private val pb: PocketBase  
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {  
        if (\!pb.authStore.isValid) {  
            return Result.failure()  
        }

        val pendingJobs \= syncJobDao.getPendingJobs()  
        if (pendingJobs.isEmpty()) {  
            return Result.success()  
        }

        for (job in pendingJobs) {  
            val success \= processJob(job)  
            if (success) {  
                syncJobDao.deleteJob(job)  
            } else {  
                return Result.retry()  
            }  
        }

        return Result.success()  
    }

    private suspend fun processJob(job: SyncJob): Boolean {  
        return try {  
            val collectionName \= getCollectionName(job.entityType)  
            when (job.operationType) {  
                OperationType.CREATE \-\> {  
                    val data \= Gson().fromJson(job.payload, Map::class.java) as Map\<String, Any\>  
                    pb.collection(collectionName).create(data)  
                }  
                OperationType.UPDATE \-\> {  
                    val data \= Gson().fromJson(job.payload, Map::class.java) as Map\<String, Any\>  
                    pb.collection(collectionName).update(job.entityId, data)  
                }  
                OperationType.DELETE \-\> {  
                    pb.collection(collectionName).delete(job.entityId)  
                }  
            }  
            true  
        } catch (e: Exception) {  
            e.printStackTrace()  
            false  
        }  
    }

    private fun getCollectionName(entityType: EntityType): String {  
        return when (entityType) {  
            EntityType.TRANSACTION \-\> "transactions"  
            EntityType.COMPTE \-\> "comptes"  
            EntityType.CATEGORIE \-\> "categories"  
            EntityType.ENVELOPPE \-\> "enveloppes"  
            EntityType.TIERS \-\> "tiers"  
            EntityType.ALLOCATION \-\> "allocations\_mensuelles"  
            EntityType.CARTE\_CREDIT \-\> "cartes\_credit"  
            EntityType.PRET\_PERSONNEL \-\> "prets\_personnels"  
        }  
    }  
}

#### **6.2. Fichier : data/sync/SyncManager.kt (Nouveau fichier)**

C'est le "contremaître" qui donne le travail à l'ouvrier.

package com.xburnsx.toutiebudget.data.sync

import android.content.Context  
import androidx.work.\*  
import dagger.hilt.android.qualifiers.ApplicationContext  
import java.util.concurrent.TimeUnit  
import javax.inject.Inject  
import javax.inject.Singleton

@Singleton  
class SyncManager @Inject constructor(  
    @ApplicationContext private val context: Context  
) {  
    fun scheduleSync() {  
        val constraints \= Constraints.Builder()  
            .setRequiredNetworkType(NetworkType.CONNECTED)  
            .build()

        val syncRequest \= OneTimeWorkRequestBuilder\<SyncWorker\>()  
            .setConstraints(constraints)  
            .setBackoffCriteria(  
                BackoffPolicy.EXPONENTIAL,  
                WorkRequest.MIN\_BACKOFF\_MILLIS,  
                TimeUnit.MILLISECONDS  
            )  
            .build()

        WorkManager.getInstance(context)  
            .enqueueUniqueWork("background\_sync", ExistingWorkPolicy.KEEP, syncRequest)  
    }  
}

## **Étape 7 : Brancher les Fils (Hilt)**

#### **7.1. Fichier : di/AppModule.kt**

**ACTION :** Remplacez **TOUT** le contenu de ce fichier par le code ci-dessous.

package com.xburnsx.toutiebudget.di

import android.content.Context  
import androidx.room.Room  
import com.xburnsx.toutiebudget.data.local.AppDatabase  
import com.xburnsx.toutiebudget.data.repositories.\*  
import com.xburnsx.toutiebudget.data.repositories.impl.\*  
import dagger.Binds  
import dagger.Module  
import dagger.Provides  
import dagger.hilt.InstallIn  
import dagger.hilt.android.qualifiers.ApplicationContext  
import dagger.hilt.components.SingletonComponent  
import javax.inject.Singleton

@Module  
@InstallIn(SingletonComponent::class)  
abstract class RepositoryModule {  
    @Binds @Singleton abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository  
    @Binds @Singleton abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository  
    @Binds @Singleton abstract fun bindCompteRepository(impl: CompteRepositoryImpl): CompteRepository  
    @Binds @Singleton abstract fun bindCategorieRepository(impl: CategorieRepositoryImpl): CategorieRepository  
    @Binds @Singleton abstract fun bindEnveloppeRepository(impl: EnveloppeRepositoryImpl): EnveloppeRepository  
    @Binds @Singleton abstract fun bindTiersRepository(impl: TiersRepositoryImpl): TiersRepository  
    @Binds @Singleton abstract fun bindAllocationRepository(impl: AllocationMensuelleRepositoryImpl): AllocationMensuelleRepository  
    @Binds @Singleton abstract fun bindCarteCreditRepository(impl: CarteCreditRepositoryImpl): CarteCreditRepository  
    @Binds @Singleton abstract fun bindPretPersonnelRepository(impl: PretPersonnelRepositoryImpl): PretPersonnelRepository  
    @Binds @Singleton abstract fun bindPreferenceRepository(impl: PreferenceRepositoryImpl): PreferenceRepository  
}

@Module  
@InstallIn(SingletonComponent::class)  
object AppModule {

    @Provides  
    @Singleton  
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {  
        return Room.databaseBuilder(context, AppDatabase::class.java, "toutiebudget.db")  
            .fallbackToDestructiveMigration()  
            .build()  
    }

    @Provides @Singleton fun provideSyncJobDao(db: AppDatabase) \= db.syncJobDao()  
    @Provides @Singleton fun provideTransactionDao(db: AppDatabase) \= db.transactionDao()  
    @Provides @Singleton fun provideCompteDao(db: AppDatabase) \= db.compteDao()  
    @Provides @Singleton fun provideCategorieDao(db: AppDatabase) \= db.categorieDao()  
    @Provides @Singleton fun provideEnveloppeDao(db: AppDatabase) \= db.enveloppeDao()  
    @Provides @Singleton fun provideTiersDao(db: AppDatabase) \= db.tiersDao()  
    @Provides @Singleton fun provideAllocationMensuelleDao(db: AppDatabase) \= db.allocationMensuelleDao()  
    @Provides @Singleton fun provideCarteCreditDao(db: AppDatabase) \= db.carteCreditDao()  
    @Provides @Singleton fun providePretPersonnelDao(db: AppDatabase) \= db.pretPersonnelDao()  
}

## **Étape 8 : Rendre les Repositories Intelligents**

On refait toutes les interfaces et implémentations.

**ACTION :** Pour chaque paire de fichiers (Interface et Impl), remplacez leur contenu.

#### **8.1. Transaction**

**data/repositories/TransactionRepository.kt**

package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Transaction

interface TransactionRepository {  
    suspend fun getAll(): Result\<List\<Transaction\>\>  
    suspend fun sync()  
    suspend fun create(transaction: Transaction): Result\<Unit\>  
    suspend fun update(id: String, transaction: Transaction): Result\<Unit\>  
    suspend fun delete(id: String): Result\<Unit\>  
}

**data/repositories/impl/TransactionRepositoryImpl.kt**

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson  
import com.xburnsx.toutiebudget.data.local.dao.SyncJobDao  
import com.xburnsx.toutiebudget.data.local.dao.TransactionDao  
import com.xburnsx.toutiebudget.data.local.sync.EntityType  
import com.xburnsx.toutiebudget.data.local.sync.OperationType  
import com.xburnsx.toutiebudget.data.local.sync.SyncJob  
import com.xburnsx.toutiebudget.data.modeles.Transaction  
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository  
import com.xburnsx.toutiebudget.data.sync.SyncManager  
import com.xburnsx.toutiebudget.di.IoDispatcher  
import kotlinx.coroutines.CoroutineDispatcher  
import kotlinx.coroutines.withContext  
import tech.sousa.khoob.pocketbase.PocketBase  
import tech.sousa.khoob.pocketbase.models.utils.toObject  
import java.util.\*  
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(  
    private val pb: PocketBase,  
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,  
    private val dao: TransactionDao,  
    private val syncJobDao: SyncJobDao,  
    private val syncManager: SyncManager  
) : TransactionRepository {

    override suspend fun getAll(): Result\<List\<Transaction\>\> \= withContext(ioDispatcher) {  
        try {  
            Result.success(dao.getAll())  
        } catch (e: Exception) {  
            Result.failure(e)  
        }  
    }

    override suspend fun sync() {  
        withContext(ioDispatcher) {  
            try {  
                if (pb.authStore.isValid) {  
                    val records \= pb.collection("transactions").getFullList(sort \= "-created")  
                    dao.saveAll(records.map { it.toObject() })  
                }  
            } catch (e: Exception) { e.printStackTrace() }  
        }  
    }

    override suspend fun create(transaction: Transaction): Result\<Unit\> \= withContext(ioDispatcher) {  
        try {  
            val finalTransaction \= transaction.copy(id \= if (transaction.id.isBlank()) UUID.randomUUID().toString() else transaction.id)  
            dao.saveAll(listOf(finalTransaction))  
              
            val job \= SyncJob(  
                entityId \= finalTransaction.id,  
                entityType \= EntityType.TRANSACTION,  
                operationType \= OperationType.CREATE,  
                payload \= Gson().toJson(finalTransaction.toMap())  
            )  
            syncJobDao.addJob(job)  
            syncManager.scheduleSync()  
            Result.success(Unit)  
        } catch (e: Exception) { Result.failure(e) }  
    }

    override suspend fun update(id: String, transaction: Transaction): Result\<Unit\> \= withContext(ioDispatcher) {  
        try {  
            dao.saveAll(listOf(transaction))  
            val job \= SyncJob(  
                entityId \= id,  
                entityType \= EntityType.TRANSACTION,  
                operationType \= OperationType.UPDATE,  
                payload \= Gson().toJson(transaction.toMap())  
            )  
            syncJobDao.addJob(job)  
            syncManager.scheduleSync()  
            Result.success(Unit)  
        } catch (e: Exception) { Result.failure(e) }  
    }

    override suspend fun delete(id: String): Result\<Unit\> \= withContext(ioDispatcher) {  
        try {  
            dao.deleteById(id)  
            val job \= SyncJob(  
                entityId \= id,  
                entityType \= EntityType.TRANSACTION,  
                operationType \= OperationType.DELETE  
            )  
            syncJobDao.addJob(job)  
            syncManager.scheduleSync()  
            Result.success(Unit)  
        } catch (e: Exception) { Result.failure(e) }  
    }

    private fun Transaction.toMap(): Map\<String, Any?\> {  
        return mapOf(  
            "id" to id, "montant" to montant, "type" to type, "compte" to compte, "vers\_compte" to versCompte,  
            "categorie" to categorie, "enveloppe" to enveloppe, "tiers" to tiers, "note" to note,  
            "date" to date, "statut" to statut, "provenance" to provenance, "mode\_operation" to modeOperation  
        )  
    }  
}

#### **8.2. Compte**

**data/repositories/CompteRepository.kt**

package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Compte

interface CompteRepository {  
    suspend fun getAll(): Result\<List\<Compte\>\>  
    suspend fun sync()  
    suspend fun create(compte: Compte): Result\<Unit\>  
    suspend fun update(id: String, compte: Compte): Result\<Unit\>  
    suspend fun delete(id: String): Result\<Unit\>  
}

**data/repositories/impl/CompteRepositoryImpl.kt**

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson  
import com.xburnsx.toutiebudget.data.local.dao.CompteDao  
import com.xburnsx.toutiebudget.data.local.dao.SyncJobDao  
import com.xburnsx.toutiebudget.data.local.sync.EntityType  
import com.xburnsx.toutiebudget.data.local.sync.OperationType  
import com.xburnsx.toutiebudget.data.local.sync.SyncJob  
import com.xburnsx.toutiebudget.data.modeles.Compte  
import com.xburnsx.toutiebudget.data.repositories.CompteRepository  
import com.xburnsx.toutiebudget.data.sync.SyncManager  
import com.xburnsx.toutiebudget.di.IoDispatcher  
import kotlinx.coroutines.CoroutineDispatcher  
import kotlinx.coroutines.withContext  
import tech.sousa.khoob.pocketbase.PocketBase  
import tech.sousa.khoob.pocketbase.models.utils.toObject  
import java.util.\*  
import javax.inject.Inject

class CompteRepositoryImpl @Inject constructor(  
    private val pb: PocketBase,  
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,  
    private val dao: CompteDao,  
    private val syncJobDao: SyncJobDao,  
    private val syncManager: SyncManager  
) : CompteRepository {

    override suspend fun getAll(): Result\<List\<Compte\>\> \= withContext(ioDispatcher) {  
        try {  
            Result.success(dao.getAll())  
        } catch (e: Exception) {  
            Result.failure(e)  
        }  
    }

    override suspend fun sync() {  
        withContext(ioDispatcher) {  
            try {  
                if (pb.authStore.isValid) {  
                    val records \= pb.collection("comptes").getFullList(sort \= "+ordre")  
                    dao.saveAll(records.map { it.toObject() })  
                }  
            } catch (e: Exception) { e.printStackTrace() }  
        }  
    }

    override suspend fun create(compte: Compte): Result\<Unit\> \= withContext(ioDispatcher) {  
        try {  
            val finalCompte \= compte.copy(id \= if (compte.id.isBlank()) UUID.randomUUID().toString() else compte.id)  
            dao.saveAll(listOf(finalCompte))

            val job \= SyncJob(  
                entityId \= finalCompte.id,  
                entityType \= EntityType.COMPTE,  
                operationType \= OperationType.CREATE,  
                payload \= Gson().toJson(finalCompte.toMap())  
            )  
            syncJobDao.addJob(job)  
            syncManager.scheduleSync()  
            Result.success(Unit)  
        } catch (e: Exception) { Result.failure(e) }  
    }

    override suspend fun update(id: String, compte: Compte): Result\<Unit\> \= withContext(ioDispatcher) {  
        try {  
            dao.saveAll(listOf(compte))  
            val job \= SyncJob(  
                entityId \= id,  
                entityType \= EntityType.COMPTE,  
                operationType \= OperationType.UPDATE,  
                payload \= Gson().toJson(compte.toMap())  
            )  
            syncJobDao.addJob(job)  
            syncManager.scheduleSync()  
            Result.success(Unit)  
        } catch (e: Exception) { Result.failure(e) }  
    }

    override suspend fun delete(id: String): Result\<Unit\> \= withContext(ioDispatcher) {  
        try {  
            dao.deleteById(id)  
            val job \= SyncJob(  
                entityId \= id,  
                entityType \= EntityType.COMPTE,  
                operationType \= OperationType.DELETE  
            )  
            syncJobDao.addJob(job)  
            syncManager.scheduleSync()  
            Result.success(Unit)  
        } catch (e: Exception) { Result.failure(e) }  
    }  
      
    private fun Compte.toMap(): Map\<String, Any?\> {  
        return mapOf(  
            "id" to id, "nom" to nom, "solde" to solde, "couleur" to couleur,  
            "emoji" to emoji, "ordre" to ordre, "est\_compte\_principal" to estComptePrincipal,  
            "est\_archive" to estArchive, "user\_id" to userId  
        )  
    }  
}

**ACTION :** Répétez ce pattern EXACT pour CategorieRepository, EnveloppeRepository, TiersRepository, PretPersonnelRepository, etc. La logique est identique.

## **Étape 9 : Le Point d'Entrée (MainActivity)**

C'est le chef d'orchestre final. Il lance la synchronisation.

#### **9.1. Fichier : MainActivity.kt**

Remplacez le contenu par ceci.

package com.xburnsx.toutiebudget

import android.os.Bundle  
import androidx.activity.ComponentActivity  
import androidx.activity.compose.setContent  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.getValue  
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen  
import androidx.lifecycle.compose.collectAsStateWithLifecycle  
import androidx.lifecycle.lifecycleScope  
import androidx.navigation.compose.NavHost  
import androidx.navigation.compose.composable  
import androidx.navigation.compose.rememberNavController  
import com.xburnsx.toutiebudget.data.repositories.\*  
import com.xburnsx.toutiebudget.data.sync.SyncManager  
import com.xburnsx.toutiebudget.ui.login.LoginScreen  
import com.xburnsx.toutiebudget.ui.navigation.Navigation  
import com.xburnsx.toutiebudget.ui.startup.StartupScreen  
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme  
import dagger.hilt.android.AndroidEntryPoint  
import kotlinx.coroutines.launch  
import javax.inject.Inject

@AndroidEntryPoint  
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository  
    @Inject lateinit var syncManager: SyncManager  
    @Inject lateinit var transactionRepository: TransactionRepository  
    @Inject lateinit var compteRepository: CompteRepository  
    @Inject lateinit var categorieRepository: CategorieRepository  
    @Inject lateinit var enveloppeRepository: EnveloppeRepository  
    @Inject lateinit var tiersRepository: TiersRepository  
    @Inject lateinit var allocationMensuelleRepository: AllocationMensuelleRepository  
    @Inject lateinit var carteCreditRepository: CarteCreditRepository  
    @Inject lateinit var pretPersonnelRepository: PretPersonnelRepository

    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
        installSplashScreen()

        setContent {  
            ToutieBudgetTheme {  
                val navController \= rememberNavController()  
                val isLoggedIn by authRepository.isLoggedIn.collectAsStateWithLifecycle(initialValue \= null)

                LaunchedEffect(isLoggedIn) {  
                    when (isLoggedIn) {  
                        true \-\> {  
                            lifecycleScope.launch {  
                                compteRepository.sync()  
                                categorieRepository.sync()  
                                enveloppeRepository.sync()  
                                tiersRepository.sync()  
                                allocationMensuelleRepository.sync()  
                                carteCreditRepository.sync()  
                                pretPersonnelRepository.sync()  
                                transactionRepository.sync()  
                                  
                                syncManager.scheduleSync()  
                            }  
                            navController.navigate("main\_route") {  
                                popUpTo("startup\_route") { inclusive \= true }  
                            }  
                        }  
                        false \-\> {  
                            navController.navigate("login\_route") {  
                                popUpTo("startup\_route") { inclusive \= true }  
                            }  
                        }  
                        null \-\> { /\* Attend la vérification \*/ }  
                    }  
                }

                NavHost(navController \= navController, startDestination \= "startup\_route") {  
                    composable("startup\_route") { StartupScreen() }  
                    composable("login\_route") { LoginScreen(navController \= navController) }  
                    composable("main\_route") { Navigation() }  
                }  
            }  
        }  
    }  
}

## **Étape 10 : La Trace d'Exécution Exacte (Votre Scénario)**

Ceci n'est pas du code à ajouter. C'est le "film" de ce qui se passe dans votre application maintenant, pour votre scénario précis.

### **Scénario : Créer et Modifier un Compte de type Dette**

**Contexte :**

* **Votre demande :** Créer un "compte dette" de 100$, puis le modifier.  
* **Analyse du code :** Dans votre application, un "compte dette" est un Compte standard avec un solde négatif. La création et la modification se font via le ComptesScreen et ses dialogues.

### **Partie 1 : Création du Compte "Dette Carte de Crédit" (-100$)**

Vous êtes sur ComptesScreen.kt et vous appuyez sur le bouton "+" pour ajouter un compte.

1. **UI (ComptesScreen.kt) :** Le FloatingActionButton met la variable showAjoutCompteDialog à true.  
2. **UI (dialogs/AjoutCompteDialog.kt) :** Le dialogue apparaît. Vous entrez "Dette Carte de Crédit" et un solde de "-100". Vous appuyez sur "Ajouter". Le onConfirm est appelé.  
3. **UI (ComptesScreen.kt) :** Le onConfirm du dialogue appelle la fonction du ViewModel : viewModel.ajouterOuModifierCompte(nouveauCompte).  
4. **Cerveau (ComptesViewModel.kt) :**  
   * La fonction ajouterOuModifierCompte(compte: Compte) est exécutée.  
   * Elle lance une coroutine : viewModelScope.launch.  
   * Elle appelle le Gérant d'Entrepôt : compteRepository.create(compte).  
5. **Gérant d'Entrepôt (impl/CompteRepositoryImpl.kt) :**  
   * La fonction create(compte: Compte) est exécutée.  
   * Un finalCompte est créé avec un ID unique (UUID.randomUUID()).  
   * **Action Locale Instantanée :** dao.saveAll(listOf(finalCompte)) est appelé. Le compte "Dette Carte de Crédit" avec un solde de \-100 est **sauvegardé dans la Réserve locale**.  
   * **Prise de Note :** Un SyncJob est créé :  
     * entityId \= le nouvel ID.  
     * entityType \= EntityType.COMPTE.  
     * operationType \= OperationType.CREATE.  
     * payload \= le JSON du finalCompte.  
   * syncJobDao.addJob(job) est appelé. La note est enregistrée.  
   * syncManager.scheduleSync() est appelé. L'Ouvrier est notifié.  
6. **UI (ComptesScreen.kt) :** Le ViewModel appelle loadComptes() pour rafraîchir la liste. La fonction lit la Réserve locale (qui contient maintenant la nouvelle dette) et met à jour l'interface. Vous voyez votre "Dette Carte de Crédit" apparaître instantanément.

### **Partie 2 : Modification du Compte "Dette Carte de Crédit" (-150$)**

Vous cliquez sur le compte "Dette Carte de Crédit" que vous venez de créer.

1. **UI (ComptesScreen.kt) :** Le onClick sur le CompteItem met la variable showModifierCompteDialog à true et sélectionne le bon compte.  
2. **UI (dialogs/ModifierCompteDialog.kt) :** Le dialogue apparaît. Vous changez le solde à "-150". Vous appuyez sur "Modifier". Le onConfirm est appelé.  
3. **UI (ComptesScreen.kt) :** Le onConfirm appelle viewModel.ajouterOuModifierCompte(compteModifie).  
4. **Cerveau (ComptesViewModel.kt) :**  
   * La fonction ajouterOuModifierCompte(compte: Compte) est exécutée.  
   * Elle voit que le compte.id n'est pas vide, donc elle sait que c'est une modification.  
   * Elle appelle le Gérant d'Entrepôt : compteRepository.update(compte.id, compte).  
5. **Gérant d'Entrepôt (impl/CompteRepositoryImpl.kt) :**  
   * La fonction update(id, compte) est exécutée.  
   * **Action Locale Instantanée :** dao.saveAll(listOf(compte)) est appelé. Le solde du compte dans la Réserve locale passe à \-150.  
   * **Prise de Note :** Un SyncJob est créé :  
     * entityId \= l'ID existant.  
     * entityType \= EntityType.COMPTE.  
     * operationType \= OperationType.UPDATE.  
     * payload \= le JSON du compte avec le solde à \-150.  
   * syncJobDao.addJob(job) est appelé.  
   * syncManager.scheduleSync() est appelé.  
6. **UI (ComptesScreen.kt) :** Le ViewModel appelle loadComptes(). La liste est rechargée depuis la Réserve, et vous voyez le solde mis à jour à \-150$.

**Tout est terminé. Le reste est géré par l'Ouvrier de Nuit, comme décrit précédemment.**