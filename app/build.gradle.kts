import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xburnsx.toutiebudget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xburnsx.toutiebudget"
        minSdk = 26
        targetSdk = 36
        versionCode = 42
        versionName = "1.6.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"1078578579569-eb1v1cre9rius8grrppg1sktal3bkbrl.apps.googleusercontent.com\"")
    }

    // Les infos de signature viennent de keystore.properties (non versionné)
    // ou des variables d'environnement TOUTIE_KEYSTORE_*. Jamais dans le code.
    val keystoreProps = Properties().apply {
        val fichier = rootProject.file("keystore.properties")
        if (fichier.exists()) fichier.inputStream().use { load(it) }
    }
    fun signature(cle: String, env: String): String? =
        keystoreProps.getProperty(cle) ?: System.getenv(env)

    val keystoreFichier = signature("storeFile", "TOUTIE_KEYSTORE_FILE")
    val signatureDisponible = keystoreFichier != null && rootProject.file(keystoreFichier).exists()

    signingConfigs {
        if (signatureDisponible) {
            create("release") {
                storeFile = rootProject.file(keystoreFichier!!)
                storePassword = signature("storePassword", "TOUTIE_KEYSTORE_PASSWORD")
                keyAlias = signature("keyAlias", "TOUTIE_KEY_ALIAS")
                keyPassword = signature("keyPassword", "TOUTIE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signatureDisponible) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Les appels android.util.Log des services ne font pas échouer les tests sur le PC
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
        }
    }
}

dependencies {
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.2")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("javax.inject:javax.inject:1")
    
    // Splash screen natif Android
    implementation("androidx.core:core-splashscreen:1.0.1")
    debugImplementation(libs.ui.tooling)

    // WorkManager pour tâches planifiées
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Notifications compat
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core")
    
    // Room Database - Base de données locale
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Tests automatiques sur le PC (logique d'argent, sans téléphone ni serveur)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
