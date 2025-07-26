// build.gradle.kts (Niveau Projet)
// Ce fichier configure les plugins pour l'ensemble du projet.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}