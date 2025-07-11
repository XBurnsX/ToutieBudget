package com.xburnsx.toutiebudget.utils

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest

/**
 * Utilitaire pour diagnostiquer le keystore utilisé par l'application
 */
object KeystoreDiagnostic {

    /**
     * Affiche les informations détaillées sur le keystore utilisé
     */
    fun afficherDiagnosticKeystore(context: Context) {
        println("🔍 === DIAGNOSTIC KEYSTORE ===")
        
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            
            println("📦 Package: ${context.packageName}")
            println("🔧 Version: ${packageInfo.versionName}")
            println("📋 Signatures trouvées: ${packageInfo.signatures.size}")
            
            packageInfo.signatures.forEachIndexed { index, signature ->
                println("\n📋 Signature $index:")
                
                // SHA-1
                val mdSha1 = MessageDigest.getInstance("SHA1")
                mdSha1.update(signature.toByteArray())
                val digestSha1 = mdSha1.digest()
                val sha1Base64 = Base64.encodeToString(digestSha1, Base64.NO_WRAP)
                val sha1Hex = digestSha1.joinToString(":") { String.format("%02X", it) }
                
                println("   - SHA-1 (Base64): $sha1Base64")
                println("   - SHA-1 (Hex): $sha1Hex")
                
                // SHA-256
                val mdSha256 = MessageDigest.getInstance("SHA256")
                mdSha256.update(signature.toByteArray())
                val digestSha256 = mdSha256.digest()
                val sha256Base64 = Base64.encodeToString(digestSha256, Base64.NO_WRAP)
                val sha256Hex = digestSha256.joinToString(":") { String.format("%02X", it) }
                
                println("   - SHA-256 (Base64): $sha256Base64")
                println("   - SHA-256 (Hex): $sha256Hex")
            }
            
            println("\n💡 Instructions pour Google Cloud Console:")
            println("1. Allez sur https://console.cloud.google.com")
            println("2. Sélectionnez votre projet")
            println("3. Allez dans 'APIs & Services' > 'Credentials'")
            println("4. Trouvez votre Client ID Android")
            println("5. Ajoutez TOUS les SHA-1 listés ci-dessus")
            println("6. Attendez 5-10 minutes pour la propagation")
            
        } catch (e: Exception) {
            println("❌ Erreur lors du diagnostic keystore: ${e.message}")
            println("🔍 Type d'erreur: ${e.javaClass.simpleName}")
        }
        
        println("=== FIN DIAGNOSTIC KEYSTORE ===")
    }

    /**
     * Compare avec le SHA-1 standard de debug
     */
    fun comparerAvecDebugStandard() {
        println("🔍 === COMPARAISON SHA-1 ===")
        
        val sha1Standard = "36:1E:7A:02:6A:7F:43:B1:75:F0:4B:E4:88:45:E8:6E:57:38:7C:D5"
        val sha1App = "Nh56Amp/Q7F18EvkiEXoblc4fNU="
        
        println("📋 SHA-1 standard (keytool): $sha1Standard")
        println("📋 SHA-1 application: $sha1App")
        println("❓ Différents - Vérifiez quel keystore est utilisé")
        
        println("💡 Causes possibles:")
        println("   - Keystore de release au lieu de debug")
        println("   - Keystore personnalisé")
        println("   - Configuration Gradle différente")
        println("   - Émulateur avec keystore différent")
        
        println("=== FIN COMPARAISON ===")
    }
} 