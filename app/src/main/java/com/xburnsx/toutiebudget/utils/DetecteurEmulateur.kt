package com.xburnsx.toutiebudget.utils

import android.os.Build

/**
 * Énumération pour représenter le type d'environnement d'exécution.
 */
enum class TypeEnvironnement {
    EMULATEUR,
    DISPOSITIF_PHYSIQUE
}

/**
 * Utilitaire pour détecter si l'application s'exécute sur un émulateur ou un appareil physique.
 * Se base sur les propriétés du système Android.
 */
object DetecteurEmulateur {

    /**
     * Détermine le type d'environnement actuel.
     * @return [TypeEnvironnement.EMULATEUR] ou [TypeEnvironnement.DISPOSITIF_PHYSIQUE].
     */
    fun obtenirTypeEnvironnement(): TypeEnvironnement {
        return if (estEmulateur()) TypeEnvironnement.EMULATEUR else TypeEnvironnement.DISPOSITIF_PHYSIQUE
    }

    /**
     * Vérifie plusieurs propriétés système pour déterminer si l'appareil est un émulateur.
     */
    fun estEmulateur(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Retourne une chaîne de caractères contenant des informations de débogage sur l'environnement.
     */
    fun obtenirInfoEnvironnement(): String {
        return """
            Build.FINGERPRINT: ${Build.FINGERPRINT}
            Build.MODEL: ${Build.MODEL}
            Build.MANUFACTURER: ${Build.MANUFACTURER}
            Build.BRAND: ${Build.BRAND}
            Build.DEVICE: ${Build.DEVICE}
            Build.PRODUCT: ${Build.PRODUCT}
            Est Emulateur: ${estEmulateur()}
        """.trimIndent()
    }
} 