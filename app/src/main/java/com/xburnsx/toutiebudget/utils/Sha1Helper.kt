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
            null
        }
    }

    /**
     * Affiche les informations de diagnostic SHA-1
     */
    fun afficherDiagnosticSha1(context: Context) {
        // Méthode silencieuse - les logs ont été supprimés
    }

    /**
     * Vérifie si le SHA-1 correspond à celui attendu
     */
    fun verifierSha1Attendu(context: Context, sha1Attendu: String): Boolean {
        val sha1Actuel = obtenirSha1Debug(context)
        return sha1Actuel == sha1Attendu
    }
} 