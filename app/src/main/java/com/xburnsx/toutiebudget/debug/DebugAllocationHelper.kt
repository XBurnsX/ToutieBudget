// chemin/simule: /debug/DebugAllocationHelper.kt
// Dépendances: PocketBaseClient, Gson, Date

package com.xburnsx.toutiebudget.debug

import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.OkHttpClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Classe utilitaire pour diagnostiquer les problèmes d'allocations mensuelles.
 * À utiliser temporairement pour identifier pourquoi les enveloppes affichent 0$.
 */
class DebugAllocationHelper {
    
    private val client = PocketBaseClient
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    /**
     * Diagnostic complet des allocations mensuelles.
     * À appeler depuis votre BudgetViewModel pour comprendre le problème.
     */
    suspend fun diagnostiquerAllocations(): String = withContext(Dispatchers.IO) {
        val rapport = StringBuilder()
        rapport.appendLine("=== DIAGNOSTIC ALLOCATIONS MENSUELLES ===")
        rapport.appendLine()
        
        try {
            // 1. Vérifier la connexion
            if (!client.estConnecte()) {
                rapport.appendLine("❌ PROBLÈME: Client non connecté")
                return@withContext rapport.toString()
            }
            rapport.appendLine("✅ Client connecté")
            
            // 2. Récupérer l'utilisateur
            val utilisateur = client.obtenirUtilisateurConnecte()
            if (utilisateur == null) {
                rapport.appendLine("❌ PROBLÈME: Aucun utilisateur connecté")
                return@withContext rapport.toString()
            }
            rapport.appendLine("✅ Utilisateur connecté: ${utilisateur.id}")
            
            // 3. Vérifier le token
            val token = client.obtenirToken()
            if (token == null) {
                rapport.appendLine("❌ PROBLÈME: Token manquant")
                return@withContext rapport.toString()
            }
            rapport.appendLine("✅ Token présent")
            
            // 4. Tester les requêtes avec différents formats de dates
            val maintenant = Date()
            val premierJuillet = Calendar.getInstance().apply {
                set(2025, Calendar.JULY, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            rapport.appendLine()
            rapport.appendLine("📅 TESTS AVEC DIFFÉRENTS FORMATS DE DATES:")
            rapport.appendLine("Date actuelle: $maintenant")
            rapport.appendLine("Premier juillet 2025: $premierJuillet")
            rapport.appendLine()
            
            // Format 1: yyyy-MM-dd HH:mm:ss
            val format1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dateFormatee1 = format1.format(premierJuillet)
            rapport.appendLine("Format 1 (yyyy-MM-dd HH:mm:ss UTC): $dateFormatee1")
            val resultats1 = testerRequeteAllocations(utilisateur.id, dateFormatee1, "Format 1")
            rapport.appendLine(resultats1)
            rapport.appendLine()
            
            // Format 2: yyyy-MM-dd
            val format2 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateFormatee2 = format2.format(premierJuillet)
            rapport.appendLine("Format 2 (yyyy-MM-dd): $dateFormatee2")
            val resultats2 = testerRequeteAllocations(utilisateur.id, dateFormatee2, "Format 2")
            rapport.appendLine(resultats2)
            rapport.appendLine()
            
            // 5. Lister TOUTES les allocations de l'utilisateur
            rapport.appendLine("📋 TOUTES LES ALLOCATIONS DE L'UTILISATEUR:")
            val toutesAllocations = listerToutesLesAllocations(utilisateur.id)
            rapport.appendLine(toutesAllocations)
            
            // 6. Lister toutes les enveloppes
            rapport.appendLine()
            rapport.appendLine("📦 TOUTES LES ENVELOPPES DE L'UTILISATEUR:")
            val toutesEnveloppes = listerToutesLesEnveloppes(utilisateur.id)
            rapport.appendLine(toutesEnveloppes)
            
        } catch (e: Exception) {
            rapport.appendLine("❌ ERREUR DURANT LE DIAGNOSTIC: ${e.message}")
            e.printStackTrace()
        }
        
        rapport.toString()
    }

    /**
     * Teste une requête d'allocations avec un format de date spécifique.
     */
    private suspend fun testerRequeteAllocations(utilisateurId: String, dateFormatee: String, nomFormat: String): String {
        return try {
            val token = client.obtenirToken()!!
            val urlBase = client.obtenirUrlBaseActive()
            
            val filtreEncode = URLEncoder.encode(
                "utilisateur_id = '$utilisateurId' && mois = '$dateFormatee'", 
                "UTF-8"
            )
            val url = "$urlBase/api/collections/allocations_mensuelles/records?filter=$filtreEncode&perPage=500"
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            val corpsReponse = reponse.body?.string() ?: ""
            
            if (reponse.isSuccessful) {
                val jsonObject = gson.fromJson(corpsReponse, JsonObject::class.java)
                val items = jsonObject.getAsJsonArray("items")
                val totalItems = jsonObject.get("totalItems")?.asInt ?: 0
                
                "✅ $nomFormat: ${items.size()} allocations trouvées (total: $totalItems)\n" +
                "URL: $url\n" +
                "Réponse (200 premiers caractères): ${corpsReponse.take(200)}..."
            } else {
                "❌ $nomFormat: Erreur ${reponse.code}\n" +
                "URL: $url\n" +
                "Erreur: $corpsReponse"
            }
        } catch (e: Exception) {
            "❌ $nomFormat: Exception ${e.message}"
        }
    }

    /**
     * Liste toutes les allocations de l'utilisateur sans filtre de date.
     */
    private suspend fun listerToutesLesAllocations(utilisateurId: String): String {
        return try {
            val token = client.obtenirToken()!!
            val urlBase = client.obtenirUrlBaseActive()
            
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/allocations_mensuelles/records?filter=$filtreEncode&perPage=500"
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            val corpsReponse = reponse.body?.string() ?: ""
            
            if (reponse.isSuccessful) {
                val jsonObject = gson.fromJson(corpsReponse, JsonObject::class.java)
                val items = jsonObject.getAsJsonArray("items")
                val totalItems = jsonObject.get("totalItems")?.asInt ?: 0
                
                val details = StringBuilder()
                details.appendLine("Total: $totalItems allocations")
                
                items.forEach { item ->
                    val obj = item.asJsonObject
                    val id = obj.get("id")?.asString ?: "?"
                    val enveloppeId = obj.get("enveloppe_id")?.asString ?: "?"
                    val mois = obj.get("mois")?.asString ?: "?"
                    val solde = obj.get("solde")?.asDouble ?: 0.0
                    val depense = obj.get("depense")?.asDouble ?: 0.0
                    
                    details.appendLine("- ID: $id, EnveloppeID: $enveloppeId, Mois: $mois, Solde: $solde, Dépense: $depense")
                }
                
                details.toString()
            } else {
                "❌ Erreur ${reponse.code}: $corpsReponse"
            }
        } catch (e: Exception) {
            "❌ Exception: ${e.message}"
        }
    }

    /**
     * Liste toutes les enveloppes de l'utilisateur.
     */
    private suspend fun listerToutesLesEnveloppes(utilisateurId: String): String {
        return try {
            val token = client.obtenirToken()!!
            val urlBase = client.obtenirUrlBaseActive()
            
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/enveloppes/records?filter=$filtreEncode&perPage=500"
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            val corpsReponse = reponse.body?.string() ?: ""
            
            if (reponse.isSuccessful) {
                val jsonObject = gson.fromJson(corpsReponse, JsonObject::class.java)
                val items = jsonObject.getAsJsonArray("items")
                val totalItems = jsonObject.get("totalItems")?.asInt ?: 0
                
                val details = StringBuilder()
                details.appendLine("Total: $totalItems enveloppes")
                
                items.forEach { item ->
                    val obj = item.asJsonObject
                    val id = obj.get("id")?.asString ?: "?"
                    val nom = obj.get("nom")?.asString ?: "?"
                    val estArchive = obj.get("est_archive")?.asBoolean ?: false
                    
                    details.appendLine("- ID: $id, Nom: '$nom', Archivée: $estArchive")
                }
                
                details.toString()
            } else {
                "❌ Erreur ${reponse.code}: $corpsReponse"
            }
        } catch (e: Exception) {
            "❌ Exception: ${e.message}"
        }
    }
}