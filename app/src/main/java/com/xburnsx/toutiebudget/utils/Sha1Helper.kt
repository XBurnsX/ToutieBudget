package com.xburnsx.toutiebudget.utils

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Utilitaire pour obtenir le SHA-1 de debug et diagnostiquer les problèmes Google Sign-In
 */
object Sha1Helper {

    /**
     * Obtient le SHA-1 de debug de l'application
     */
    fun obtenirSha1Debug(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            
            for (signature in packageInfo.signatures) {
                val md = MessageDigest.getInstance("SHA1")
                md.update(signature.toByteArray())
                val digest = md.digest()
                return Base64.encodeToString(digest, Base64.NO_WRAP)
            }
            null
        } catch (e: Exception) {
            println("❌ Erreur lors de l'obtention du SHA-1: ${e.message}")
            null
        }
    }

    /**
     * Affiche les informations de diagnostic SHA-1
     */
    fun afficherDiagnosticSha1(context: Context) {
        println("🔍 === DIAGNOSTIC SHA-1 ===")
        
        val sha1 = obtenirSha1Debug(context)
        if (sha1 != null) {
            println("✅ SHA-1 de debug obtenu: $sha1")
            println("📋 Instructions pour Google Cloud Console:")
            println("1. Allez sur https://console.cloud.google.com")
            println("2. Sélectionnez votre projet")
            println("3. Allez dans 'APIs & Services' > 'Credentials'")
            println("4. Trouvez votre Client ID Android")
            println("5. Ajoutez ce SHA-1: $sha1")
            println("6. Attendez 5-10 minutes pour la propagation")
        } else {
            println("❌ Impossible d'obtenir le SHA-1 de debug")
            println("💡 Vérifiez que l'application est signée correctement")
        }
        
        println("🔧 Package name: ${context.packageName}")
        println("🔧 Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
        println("=== FIN DIAGNOSTIC SHA-1 ===")
    }

    /**
     * Vérifie si le SHA-1 correspond à celui attendu
     */
    fun verifierSha1Attendu(context: Context, sha1Attendu: String): Boolean {
        val sha1Actuel = obtenirSha1Debug(context)
        return sha1Actuel == sha1Attendu
    }
} 