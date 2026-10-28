package com.xburnsx.toutiebudget.data.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Dernier prix connu d'un titre : le symbole réellement coté (ex. « XEQT.TO »), son prix et sa devise. */
data class Cotation(val symboleCotation: String, val prix: Double, val devise: String)

/** Prix des titres et taux de change en ligne. Une panne renvoie une erreur ; la saisie à la main reste possible. */
interface CoursEnLigne {
    suspend fun cotation(symbole: String, symboleConnu: String? = null): Result<Cotation>
    /** Combien de CAD vaut 1 unité de [devise] (ex. USD -> 1.37). */
    suspend fun tauxVersCad(devise: String): Result<Double>
}

/**
 * Cours publics de Yahoo Finance (gratuits, sans clé). Un symbole sans suffixe est d'abord cherché à la
 * Bourse de Toronto (« XEQT » -> « XEQT.TO »), puis tel quel (titres américains), puis NEO et TSX-V.
 */
class CoursYahoo(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
) : CoursEnLigne {

    override suspend fun cotation(symbole: String, symboleConnu: String?): Result<Cotation> = withContext(Dispatchers.IO) {
        val base = symbole.trim().uppercase()
        val essais = when {
            !symboleConnu.isNullOrBlank() -> listOf(symboleConnu)
            base.contains('.') || base.contains('-') || base.contains('=') -> listOf(base)
            else -> listOf("$base.TO", base, "$base.NE", "$base.V")
        }
        var derniereErreur: Exception? = null
        for (essai in essais) {
            try {
                return@withContext Result.success(lire(essai))
            } catch (e: Exception) {
                derniereErreur = e
            }
        }
        Result.failure(IllegalStateException("Cours de $base introuvable en ligne : ${derniereErreur?.message ?: "aucune réponse"}"))
    }

    override suspend fun tauxVersCad(devise: String): Result<Double> = withContext(Dispatchers.IO) {
        val d = devise.trim().uppercase()
        if (d == "CAD") return@withContext Result.success(1.0)
        runCatching { lire("${d}CAD=X").prix }
            .recoverCatching { throw IllegalStateException("Taux $d → CAD introuvable en ligne : ${it.message}") }
    }

    private fun lire(symbole: String): Cotation {
        val requete = Request.Builder()
            .url("https://query1.finance.yahoo.com/v8/finance/chart/${java.net.URLEncoder.encode(symbole, "UTF-8")}?range=1d&interval=1d")
            .header("User-Agent", "Mozilla/5.0 (Android) ToutieBudget")
            .build()
        client.newCall(requete).execute().use { reponse ->
            val corps = reponse.body?.string().orEmpty()
            if (!reponse.isSuccessful) throw IllegalStateException("réponse ${reponse.code}")
            return analyser(symbole, corps)
        }
    }

    companion object {
        /** Lit la réponse « chart » de Yahoo ; séparé pour être testé sans réseau. */
        fun analyser(symbole: String, json: String): Cotation {
            val chart = com.google.gson.JsonParser.parseString(json).asJsonObject.getAsJsonObject("chart")
            // « result »: null (symbole inconnu) doit donner un message, pas un plantage
            val resultat = chart?.get("result")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.takeIf { it.size() > 0 }?.get(0)?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IllegalStateException("symbole $symbole inconnu")
            val meta = resultat.getAsJsonObject("meta")
            val prix = meta.get("regularMarketPrice")?.takeIf { !it.isJsonNull }?.asDouble
                ?: throw IllegalStateException("prix absent pour $symbole")
            require(prix.isFinite() && prix > 0.0) { "prix invalide pour $symbole" }
            val devise = meta.get("currency")?.takeIf { !it.isJsonNull }?.asString?.uppercase() ?: "CAD"
            return Cotation(meta.get("symbol")?.asString ?: symbole, prix, devise)
        }
    }
}
