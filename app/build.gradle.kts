plugins {  
    id("com.android.application")  
    id("org.jetbrains.kotlin.android")  
    id("com.google.devtools.ksp")  
    id("com.google.dagger.hilt.android")  
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
        vectorDrawables {
            useSupportLibrary = true
        }

        // BuildConfig fields CRITIQUES pour l'app
        buildConfigField("String", "POCKETBASE_URL_LOCAL", "\"http://192.168.1.77:8090/\"")
        buildConfigField("String", "POCKETBASE_URL_PUBLIC", "\"http://toutiebudget.duckdns.org:8090/\"")
        buildConfigField("String", "POCKETBASE_URL_EMULATEUR", "\"http://10.0.2.2:8090/\"")
        buildConfigField("String", "POCKETBASE_URL_EMULATEUR_AVD", "\"http://10.0.2.15:8090/\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"1078578579569-eb1v1cre9rius8grrppg1sktal3bkbrl.apps.googleusercontent.com\"")
        buildConfigField("boolean", "EST_MODE_DEBUG", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true  // CRITIQUE pour BuildConfig
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    ksp(libs.hilt.compiler)  
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

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
    ksp(libs.room.compiler)

        // WorkManager for background jobs  
    implementation(libs.work.runtime.ktx)  
    implementation(libs.hilt.work)  
    ksp(libs.hilt.work.compiler)

    // Google Play Services Auth (CRITIQUE pour Google Auth)
    implementation(libs.google.play.services.auth)

    // Reorderable (pour réorganiser les comptes/enveloppes)
    implementation(libs.reorderable)
}

ksp {  
    arg("room.schemaLocation", "$projectDir/schemas")  
}
