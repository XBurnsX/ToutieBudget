// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/utils/DetecteurEmulateur.kt
// Dépendances: Android Build, System properties

package com.xburnsx.toutiebudget.utils

import android.os.Build

/**
 * Utilitaire pour détecter si l'application s'exécute sur un émulateur Android
 * Utilise plusieurs méthodes de détection pour une précision maximale
 */
object DetecteurEmulateur {

    /**
     * Détecte si l'application s'exécute sur un émulateur Android
     * @return true si c'est un émulateur, false sinon
     */
    fun estEmulateur(): Boolean {
        return verifierProprietesEmulateur() ||
                verifierModeleAppareil() ||
                verifierFingerprint() ||
                verifierArchitecture()
    }

    /**
     * Vérifie les propriétés système spécifiques aux émulateurs
     */
    private fun verifierProprietesEmulateur(): Boolean {
        val proprietesEmulateur = listOf(
            "ro.kernel.qemu" to "1",
            "ro.hardware" to "goldfish",
            "ro.hardware" to "ranchu",
            "ro.build.fingerprint" to "generic",
            "ro.product.model" to "sdk"
        )

        return proprietesEmulateur.any { (propriete, valeur) ->
            try {
                val valeurSysteme = System.getProperty(propriete) ?: ""
                valeurSysteme.contains(valeur, ignoreCase = true)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Vérifie les modèles d'appareils typiques des émulateurs
     */
    private fun verifierModeleAppareil(): Boolean {
        val modelesEmulateur = listOf(
            "sdk", "google_sdk", "emulator", "android sdk built for x86",
            "android sdk built for arm", "generic", "generic_x86", "generic_x86_64"
        )

        val modele = Build.MODEL.lowercase()
        val produit = Build.PRODUCT.lowercase()
        val materiel = Build.HARDWARE.lowercase()

        return modelesEmulateur.any { motif ->
            modele.contains(motif) || produit.contains(motif) || materiel.contains(motif)
        }
    }

    /**
     * Vérifie le fingerprint de build
     */
    private fun verifierFingerprint(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val motifEmulateur = listOf("generic", "unknown", "emulator", "sdk", "genymotion")

        return motifEmulateur.any { motif -> fingerprint.contains(motif) }
    }

    /**
     * Vérifie l'architecture pour détecter les émulateurs x86 sur ARM
     */
    private fun verifierArchitecture(): Boolean {
        return try {
            val abi = Build.SUPPORTED_ABIS[0].lowercase()
            // Les émulateurs utilisent souvent x86 même sur des machines ARM
            abi.contains("x86") && Build.HARDWARE.lowercase().contains("ranchu")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Obtient le type d'environnement détecté
     */
    fun obtenirTypeEnvironnement(): TypeEnvironnement {
        return when {
            estEmulateur() -> TypeEnvironnement.EMULATEUR
            Build.MODEL.contains("Pixel", ignoreCase = true) -> TypeEnvironnement.DISPOSITIF_PHYSIQUE
            else -> TypeEnvironnement.DISPOSITIF_PHYSIQUE
        }
    }

    /**
     * Obtient des informations détaillées sur l'environnement
     */
    fun obtenirInfoEnvironnement(): InfoEnvironnement {
        return InfoEnvironnement(
            estEmulateur = estEmulateur(),
            modele = Build.MODEL,
            produit = Build.PRODUCT,
            materiel = Build.HARDWARE,
            fingerprint = Build.FINGERPRINT,
            abis = Build.SUPPORTED_ABIS.toList()
        )
    }
}

/**
 * Types d'environnement d'exécution
 */
enum class TypeEnvironnement {
    EMULATEUR,
    DISPOSITIF_PHYSIQUE
}

/**
 * Informations détaillées sur l'environnement d'exécution
 */
data class InfoEnvironnement(
    val estEmulateur: Boolean,
    val modele: String,
    val produit: String,
    val materiel: String,
    val fingerprint: String,
    val abis: List<String>
) {
    override fun toString(): String {
        return """
            Environnement: ${if (estEmulateur) "Émulateur" else "Dispositif physique"}
            Modèle: $modele
            Produit: $produit
            Matériel: $materiel
            Fingerprint: $fingerprint
            ABIs: ${abis.joinToString(", ")}
        """.trimIndent()
    }
}