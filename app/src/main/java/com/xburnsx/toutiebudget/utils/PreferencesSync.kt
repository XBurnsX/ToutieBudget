package com.xburnsx.toutiebudget.utils

import android.content.Context
import androidx.core.content.edit
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * PR7/PR8/PR18 : préférences de Paramètres enregistrées tout de suite sur l'appareil, puis envoyées à
 * PocketBase dès que possible (hors ligne = « en attente », renvoyé à la prochaine ouverture).
 *
 * Règle de conflit par propriété : une préférence modifiée ici et pas encore confirmée par le serveur
 * n'est JAMAIS écrasée par la valeur serveur (plus ancienne). Les autres suivent le serveur.
 */
object PreferencesSync {
    enum class Etat(val libelle: String) {
        LOCAL("Enregistré sur cet appareil"),
        EN_ATTENTE("Enregistré sur cet appareil, en attente de synchronisation"),
        ENVOI("Synchronisation en cours…"),
        SYNCHRONISE("Synchronisé avec le serveur"),
        ERREUR("Enregistré sur cet appareil ; échec de synchronisation (nouvel essai plus tard)")
    }

    data class Statut(val etat: Etat, val detail: String? = null, val derniereSynchro: Long? = null)

    const val THEME = "theme"
    const val FIGER = "figer_pret_a_placer"
    const val NOTIFS = "notifications_enabled"
    const val JOURS_AVANT = "notif_obj_jours_avant"
    const val ENV_NEGATIF = "notif_enveloppe_negatif"

    private const val KEY_EN_ATTENTE = "cles_en_attente"
    private const val KEY_DERNIERE_SYNCHRO = "derniere_synchro"
    private const val KEY_ERREUR = "derniere_erreur"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _statut = MutableStateFlow(Statut(Etat.LOCAL))
    val statut: StateFlow<Statut> = _statut.asStateFlow()

    private fun etatPrefs(context: Context) = context.applicationContext.getSharedPreferences(
        "prefs_sync_" + (PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: "local"), Context.MODE_PRIVATE
    )

    private fun enAttente(context: Context): Set<String> =
        etatPrefs(context).getStringSet(KEY_EN_ATTENTE, emptySet()).orEmpty().toSet()

    /** Valeurs locales actuelles de toutes les préférences synchronisées. */
    private fun valeursLocales(context: Context): Map<String, Any?> = mapOf(
        THEME to (if (ThemePreferences.chargerCouleurTheme(context) == CouleurTheme.RED) "RED" else "PINK"),
        FIGER to PreferencesManager.getFigerPretAPlacer(context),
        NOTIFS to PreferencesManager.getNotificationsEnabled(context),
        JOURS_AVANT to PreferencesManager.getNotifObjJoursAvant(context),
        ENV_NEGATIF to PreferencesManager.getNotifEnveloppeNegative(context)
    )

    /** Une préférence vient d'être changée par l'utilisateur sur cet appareil. */
    fun marquerModifiee(context: Context, cle: String) {
        val app = context.applicationContext
        etatPrefs(app).edit { putStringSet(KEY_EN_ATTENTE, enAttente(app) + cle) }
        _statut.value = Statut(Etat.EN_ATTENTE, derniereSynchro = derniereSynchro(app))
        scope.launch { envoyer(app) }
    }

    fun derniereSynchro(context: Context): Long? =
        etatPrefs(context).getLong(KEY_DERNIERE_SYNCHRO, 0L).takeIf { it > 0L }

    /** Statut affiché à l'ouverture de Paramètres. */
    fun rafraichirStatut(context: Context) {
        val app = context.applicationContext
        val erreur = etatPrefs(app).getString(KEY_ERREUR, null)
        _statut.value = when {
            _statut.value.etat == Etat.ENVOI -> _statut.value
            enAttente(app).isNotEmpty() && erreur != null -> Statut(Etat.ERREUR, erreur, derniereSynchro(app))
            enAttente(app).isNotEmpty() -> Statut(Etat.EN_ATTENTE, derniereSynchro = derniereSynchro(app))
            derniereSynchro(app) != null -> Statut(Etat.SYNCHRONISE, derniereSynchro = derniereSynchro(app))
            else -> Statut(Etat.LOCAL)
        }
    }

    /** Envoie les préférences en attente. Le succès n'est affiché qu'après la réponse du serveur. */
    suspend fun envoyer(context: Context): Boolean = mutex.withLock {
        val app = context.applicationContext
        val cles = enAttente(app)
        if (cles.isEmpty()) {
            rafraichirStatut(app)
            return@withLock true
        }
        if (PocketBaseClient.obtenirUtilisateurConnecte() == null) {
            _statut.value = Statut(Etat.EN_ATTENTE, "Connecte-toi pour synchroniser.", derniereSynchro(app))
            return@withLock false
        }
        val envoi = valeursLocales(app).filterKeys { it in cles }
        _statut.value = Statut(Etat.ENVOI, derniereSynchro = derniereSynchro(app))
        try {
            AppModule.provideRealtimeSyncService().mettreAJourPreferencesUtilisateur(envoi)
            // Une valeur changée pendant l'envoi reste en attente pour le prochain envoi
            val actuelles = valeursLocales(app)
            val restantes = enAttente(app).filter { cle -> cle !in envoi || actuelles[cle] != envoi[cle] }.toSet()
            val maintenant = System.currentTimeMillis()
            DiagnosticDonnees.noterEchangeReussi(app, maintenant)
            etatPrefs(app).edit {
                putStringSet(KEY_EN_ATTENTE, restantes)
                putLong(KEY_DERNIERE_SYNCHRO, maintenant)
                remove(KEY_ERREUR)
            }
            _statut.value = if (restantes.isEmpty()) Statut(Etat.SYNCHRONISE, derniereSynchro = maintenant)
                else Statut(Etat.EN_ATTENTE, derniereSynchro = maintenant)
            restantes.isEmpty()
        } catch (e: Exception) {
            val message = e.message ?: "serveur injoignable"
            etatPrefs(app).edit { putString(KEY_ERREUR, message) }
            _statut.value = Statut(Etat.ERREUR, message, derniereSynchro(app))
            false
        }
    }

    fun envoyerEnArrierePlan(context: Context) {
        val app = context.applicationContext
        scope.launch { envoyer(app) }
    }

    /**
     * Applique les préférences lues sur PocketBase, sauf celles modifiées ici et pas encore envoyées (PR8).
     * Retourne le thème à afficher si le serveur l'impose, sinon null.
     */
    fun appliquerServeur(context: Context, serveur: Map<String, Any?>): CouleurTheme? {
        val app = context.applicationContext
        val locales = enAttente(app)
        var theme: CouleurTheme? = null
        if (THEME !in locales) {
            theme = when (serveur[THEME] as? String) {
                "RED" -> CouleurTheme.RED
                "PINK" -> CouleurTheme.PINK
                else -> null
            }
            theme?.let { ThemePreferences.sauvegarderCouleurTheme(app, it) }
        }
        if (FIGER !in locales) (serveur[FIGER] as? Boolean)?.let {
            PreferencesManager.setFigerPretAPlacer(app, it, depuisServeur = true)
            AppModule.provideBudgetViewModel().setFigerPretAPlacer(it)
        }
        if (NOTIFS !in locales) (serveur[NOTIFS] as? Boolean)?.let {
            PreferencesManager.setNotificationsEnabled(app, it, depuisServeur = true)
        }
        if (JOURS_AVANT !in locales) (serveur[JOURS_AVANT] as? Number)?.let {
            PreferencesManager.setNotifObjJoursAvant(app, it.toInt(), depuisServeur = true)
        }
        if (ENV_NEGATIF !in locales) (serveur[ENV_NEGATIF] as? Boolean)?.let {
            PreferencesManager.setNotifEnveloppeNegative(app, it, depuisServeur = true)
        }
        // Ce qui attendait d'être envoyé part maintenant
        if (locales.isNotEmpty()) envoyerEnArrierePlan(app)
        return theme
    }
}
