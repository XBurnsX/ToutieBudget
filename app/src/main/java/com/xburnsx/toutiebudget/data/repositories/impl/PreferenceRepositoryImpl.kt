// chemin/simule: /data/repositories/impl/PreferenceRepositoryImpl.kt
package com.xburnsx.toutiebudget.data.repositories.impl

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.repositories.PreferenceRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import java.util.Date

class PreferenceRepositoryImpl(context: Context) : PreferenceRepository {
    private var dernierRollover: Date? = null
    private var figerPretAPlacer: Boolean = false

    // Préférences de la page Budget gardées sur l'appareil, une série par utilisateur
    private val prefs = context.getSharedPreferences("preferences_budget", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun cle(nom: String): String {
        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: "local"
        return "${nom}_$utilisateurId"
    }

    override suspend fun sauvegarderDernierRollover(date: Date) {
        dernierRollover = date
    }

    override suspend fun recupererDernierRollover(): Date? {
        return dernierRollover
    }

    override suspend fun setFigerPretAPlacer(enabled: Boolean) {
        figerPretAPlacer = enabled
    }

    override suspend fun getFigerPretAPlacer(): Boolean {
        return figerPretAPlacer
    }

    override suspend fun sauvegarderCategoriesOuvertes(categoriesOuvertes: Map<String, Boolean>) {
        prefs.edit {
            putString(cle("categories_ouvertes"), gson.toJson(categoriesOuvertes))
            putBoolean(cle("en_attente_envoi"), true)
        }
    }

    override suspend fun recupererCategoriesOuvertes(): Map<String, Boolean> {
        val json = prefs.getString(cle("categories_ouvertes"), null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, Boolean>>() {}.type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override suspend fun sauvegarderEnveloppesFavorites(ids: Set<String>) {
        prefs.edit {
            putStringSet(cle("enveloppes_favorites"), ids)
            putBoolean(cle("en_attente_envoi"), true)
        }
    }

    override suspend fun recupererEnveloppesFavorites(): Set<String> {
        return prefs.getStringSet(cle("enveloppes_favorites"), null)?.toSet() ?: emptySet()
    }

    override suspend fun preferencesBudgetEnAttenteEnvoi(): Boolean {
        return prefs.getBoolean(cle("en_attente_envoi"), false)
    }

    override suspend fun marquerPreferencesBudgetEnvoyees() {
        prefs.edit { putBoolean(cle("en_attente_envoi"), false) }
    }

    override suspend fun appliquerPreferencesBudgetServeur(
        categoriesOuvertes: Map<String, Boolean>?,
        favorites: Set<String>?
    ) {
        prefs.edit {
            if (categoriesOuvertes != null) putString(cle("categories_ouvertes"), gson.toJson(categoriesOuvertes))
            if (favorites != null) putStringSet(cle("enveloppes_favorites"), favorites)
        }
    }
}
