// build.gradle.kts (Niveau Projet)
// Ce fichier configure les plugins pour l'ensemble du projet.

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.googleServices) apply false
}