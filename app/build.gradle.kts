// app/build.gradle.kts (Niveau Module :app)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // CORRECTION : Avec Kotlin 2.0, ce plugin existe et gère automatiquement Compose
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.xburnsx.toutiebudget"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xburnsx.toutiebudget"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("toutiebudget.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // URL PocketBase pour développement
            buildConfigField("String", "POCKETBASE_URL_LOCAL", "\"http://192.168.1.77:8090/\"")
            buildConfigField("String", "POCKETBASE_URL_PUBLIC", "\"http://toutiebudget.duckdns.org:8090/\"")
            buildConfigField("String", "POCKETBASE_URL_EMULATEUR", "\"http://10.0.2.2:8090/\"")
            buildConfigField("String", "POCKETBASE_URL_EMULATEUR_AVD", "\"http://10.0.2.15:8090/\"")

            // Client ID Web réel du google-services.json
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"1078578579569-eb1v1cre9rius8grrppg1sktal3bkbrl.apps.googleusercontent.com\"")

            buildConfigField("boolean", "EST_MODE_DEBUG", "true")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")

            // Configuration pour release
            buildConfigField("String", "POCKETBASE_URL_PUBLIC", "\"http://toutiebudget.duckdns.org:8090/\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"857272496129-adbtb0bltka9siqarvll7s36l697bcs2.apps.googleusercontent.com\"")
            buildConfigField("boolean", "EST_MODE_DEBUG", "false")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")

            // En production, désactiver le mode debug
            buildConfigField("boolean", "EST_MODE_DEBUG", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // CORRECTION : Avec le plugin Kotlin Compose, plus besoin de composeOptions
    // Le compilateur Compose est maintenant intégré dans Kotlin 2.0
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // CORRECTION : Mise à jour vers un BOM plus récent compatible avec Kotlin 2.0
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    implementation("io.ktor:ktor-client-okhttp:2.3.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}