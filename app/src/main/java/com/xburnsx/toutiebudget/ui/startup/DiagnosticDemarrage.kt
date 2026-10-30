package com.xburnsx.toutiebudget.ui.startup

import android.content.Context
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.DiagnosticDonnees
import kotlinx.coroutines.flow.first

/**
 * LD13/LDA2/LDA5 : trois états séparés au démarrage — serveur, session et synchronisation —
 * et un diagnostic compréhensible (serveur indisponible, session expirée, import incomplet, conflit).
 * Aucun « à jour » n'est déduit d'un serveur joignable ou d'un jeton présent.
 */
data class DiagnosticDemarrage(
    val serveur: String,
    val session: String,
    val synchro: String,
    val compte: String?,
    val problemes: List<String>
) {
    companion object {
        suspend fun construire(context: Context, serveurJoignable: Boolean, session: PocketBaseClient.EtatSession): DiagnosticDemarrage {
            val jobs = runCatching { AppModule.provideSyncJobDao(context).getAllSyncJobs().first() }.getOrDefault(emptyList())
            val enAttente = jobs.count { it.status == "PENDING" || it.status == "IN_PROGRESS" }
            val conflits = jobs.count { it.status == "FAILED" && it.derniereErreur?.startsWith("Conflit") == true }
            val bloquees = jobs.count { it.status == "FAILED" && it.bloque } - conflits
            val importComplet = runCatching { AppModule.provideInitialImportService().importEstComplet() }.getOrDefault(true)
            val dernier = DiagnosticDonnees.formater(DiagnosticDonnees.dernierEchange(context))

            val problemes = buildList {
                if (!serveurJoignable) add("Serveur ToutieBudget indisponible : tes données de l'appareil restent utilisables.")
                if (session == PocketBaseClient.EtatSession.EXPIREE) add("Session expirée ou révoquée : reconnecte-toi. Rien n'est effacé sur l'appareil.")
                if (!importComplet) add("Import initial incomplet : il reprendra là où il s'est arrêté.")
                if (conflits > 0) add("$conflits modification(s) en conflit avec un autre appareil : à choisir dans Paramètres > Synchronisation.")
                if (bloquees > 0) add("$bloquees envoi(s) bloqué(s) : voir Paramètres > Synchronisation.")
            }
            return DiagnosticDemarrage(
                serveur = if (serveurJoignable) "Serveur : joignable" else "Serveur : injoignable",
                session = when (session) {
                    PocketBaseClient.EtatSession.VALIDE -> "Session : vérifiée"
                    PocketBaseClient.EtatSession.EXPIREE -> "Session : expirée"
                    PocketBaseClient.EtatSession.INVERIFIABLE -> "Session : non vérifiée (hors ligne)"
                    PocketBaseClient.EtatSession.ABSENTE -> "Session : aucune"
                },
                synchro = "Synchro : " + (if (enAttente > 0) "$enAttente modification(s) en attente" else "rien en attente") +
                    " • dernier échange réussi $dernier",
                compte = PocketBaseClient.obtenirUtilisateurConnecte()?.email,
                problemes = problemes
            )
        }
    }
}

/** LD6 : message affiché sur l'écran de connexion quand la session a expiré au démarrage. */
object MotifReconnexion {
    @Volatile private var message: String? = null
    fun definir(texte: String) { message = texte }
    fun consommer(): String? = message.also { message = null }
}
