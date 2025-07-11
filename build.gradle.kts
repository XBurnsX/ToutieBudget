// build.gradle.kts (Niveau Projet)
// Ce fichier configure les plugins pour l'ensemble du projet.

plugins {
    id("com.android.application") version "8.2.2" apply false
    // ⬇️ CHANGEMENT : Kotlin 1.9.22 → 2.0.20
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // ⬇️ AJOUT : Plugin Compose pour Kotlin 2.0
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
}