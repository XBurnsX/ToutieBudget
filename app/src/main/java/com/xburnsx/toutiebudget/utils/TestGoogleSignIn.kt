package com.xburnsx.toutiebudget.utils

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utilitaire pour tester Google Sign-In de manière isolée
 */
object TestGoogleSignIn {

    /**
     * Teste Google Sign-In avec différentes configurations
     */
    suspend fun testerGoogleSignIn(context: Context): String = withContext(Dispatchers.Main) {
        val rapport = StringBuilder()
        rapport.appendLine("🔍 === TEST GOOGLE SIGN-IN ISOLÉ ===")
        
        try {
            // Test 1 : Configuration simple
            rapport.appendLine("\n📋 Test 1 : Configuration simple")
            val configSimple = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            
            val clientSimple = GoogleSignIn.getClient(context, configSimple)
            rapport.appendLine("✅ Client simple créé avec succès")
            
            // Test 2 : Configuration avec Web Client ID
            rapport.appendLine("\n📋 Test 2 : Configuration avec Web Client ID")
            val webClientId = com.xburnsx.toutiebudget.BuildConfig.GOOGLE_WEB_CLIENT_ID
            val configWeb = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(webClientId)
                .build()
            
            val clientWeb = GoogleSignIn.getClient(context, configWeb)
            rapport.appendLine("✅ Client avec Web Client ID créé avec succès")
            
            // Test 3 : Configuration avec Server Auth Code
            rapport.appendLine("\n📋 Test 3 : Configuration avec Server Auth Code")
            val configServer = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(webClientId)
                .requestServerAuthCode(webClientId, true)
                .build()
            
            val clientServer = GoogleSignIn.getClient(context, configServer)
            rapport.appendLine("✅ Client avec Server Auth Code créé avec succès")
            
            // Test 4 : Vérifier le compte actuel
            rapport.appendLine("\n📋 Test 4 : Vérifier le compte actuel")
            val compteActuel = GoogleSignIn.getLastSignedInAccount(context)
            if (compteActuel != null) {
                rapport.appendLine("✅ Compte Google déjà connecté : ${compteActuel.email}")
                rapport.appendLine("   - ID: ${compteActuel.id}")
                rapport.appendLine("   - Display Name: ${compteActuel.displayName}")
                rapport.appendLine("   - Server Auth Code: ${compteActuel.serverAuthCode}")
                rapport.appendLine("   - ID Token: ${compteActuel.idToken}")
            } else {
                rapport.appendLine("⚠️ Aucun compte Google connecté")
            }
            
            // Test 5 : Vérifier les permissions
            rapport.appendLine("\n📋 Test 5 : Vérifier les permissions")
            val permissions = context.packageManager.getPackageInfo(context.packageName, 0).requestedPermissions
            if (permissions != null) {
                rapport.appendLine("📋 Permissions demandées :")
                permissions.forEach { permission ->
                    rapport.appendLine("   - $permission")
                }
            }
            
            // Recommandations
            rapport.appendLine("\n💡 Recommandations :")
            rapport.appendLine("1. Vérifiez que Google Play Services est à jour")
            rapport.appendLine("2. Vérifiez qu'un compte Google est configuré sur l'appareil")
            rapport.appendLine("3. Testez sur un appareil physique")
            rapport.appendLine("4. Redémarrez l'émulateur")
            rapport.appendLine("5. Vérifiez la connectivité réseau")
            
        } catch (e: Exception) {
            rapport.appendLine("❌ Erreur lors du test : ${e.message}")
            rapport.appendLine("🔍 Type d'erreur : ${e.javaClass.simpleName}")
            rapport.appendLine("📋 Stack trace : ${e.stackTrace.joinToString("\n")}")
        }
        
        rapport.appendLine("\n=== FIN TEST GOOGLE SIGN-IN ===")
        rapport.toString()
    }

    /**
     * Teste la configuration Google Play Services
     */
    fun testerGooglePlayServices(context: Context): String {
        val rapport = StringBuilder()
        rapport.appendLine("🔍 === TEST GOOGLE PLAY SERVICES ===")
        
        try {
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
            
            rapport.appendLine("📊 Code de résultat : $resultCode")
            
            when (resultCode) {
                com.google.android.gms.common.ConnectionResult.SUCCESS -> {
                    rapport.appendLine("✅ Google Play Services : Disponible et à jour")
                }
                com.google.android.gms.common.ConnectionResult.SERVICE_MISSING -> {
                    rapport.appendLine("❌ Google Play Services : Manquant")
                    rapport.appendLine("💡 Installez Google Play Services depuis le Play Store")
                }
                com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                    rapport.appendLine("⚠️ Google Play Services : Mise à jour requise")
                    rapport.appendLine("💡 Mettez à jour Google Play Services depuis le Play Store")
                }
                com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED -> {
                    rapport.appendLine("❌ Google Play Services : Désactivé")
                    rapport.appendLine("💡 Activez Google Play Services dans les paramètres")
                }
                else -> {
                    rapport.appendLine("❌ Google Play Services : Erreur $resultCode")
                    rapport.appendLine("💡 Vérifiez l'état de Google Play Services")
                }
            }
            
        } catch (e: Exception) {
            rapport.appendLine("❌ Erreur lors du test Google Play Services : ${e.message}")
        }
        
        rapport.appendLine("=== FIN TEST GOOGLE PLAY SERVICES ===")
        return rapport.toString()
    }
} 