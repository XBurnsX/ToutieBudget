package com.xburnsx.toutiebudget.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xburnsx.toutiebudget.BuildConfig
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Mises à jour de l'app sans Play Store.
 *
 * Les versions sont publiées dans la collection PocketBase `app_versions`
 * (version_code, version_name, notes, apk, sha256), lisible sans connexion.
 * L'APK doit être signé avec la même clé que l'app installée, sinon Android refuse l'installation.
 */
object MiseAJourService {

    private const val TAG = "MiseAJour"
    private const val COLLECTION = "app_versions"

    data class VersionDisponible(
        val versionCode: Int,
        val versionName: String,
        val notes: String?,
        val urlApk: String,
        val sha256: String?
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Retourne la version publiée la plus récente si elle est plus récente que celle installée. */
    suspend fun verifier(): VersionDisponible? = withContext(Dispatchers.IO) {
        try {
            val urlBase = UrlResolver.obtenirUrlActive().trimEnd('/')
            val request = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records?sort=-version_code&perPage=1")
                .build()
            val record = httpClient.newCall(request).execute().use { reponse ->
                if (!reponse.isSuccessful) return@withContext null
                JsonParser.parseString(reponse.body?.string() ?: "{}").asJsonObject
                    .getAsJsonArray("items")?.firstOrNull()?.asJsonObject
            } ?: return@withContext null

            val versionCode = record.get("version_code")?.asInt ?: return@withContext null
            if (versionCode <= BuildConfig.VERSION_CODE) return@withContext null

            val fichier = record.texte("apk") ?: return@withContext null
            VersionDisponible(
                versionCode = versionCode,
                versionName = record.texte("version_name") ?: versionCode.toString(),
                notes = record.texte("notes"),
                urlApk = "$urlBase/api/files/$COLLECTION/${record.texte("id")}/$fichier",
                sha256 = record.texte("sha256")?.lowercase()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Vérification des mises à jour impossible: ${e.message}")
            null
        }
    }

    /** Télécharge l'APK dans le cache et vérifie son SHA-256 s'il est fourni. */
    suspend fun telecharger(
        context: Context,
        version: VersionDisponible,
        onProgression: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dossier = File(context.cacheDir, "mises_a_jour").apply { mkdirs() }
        dossier.listFiles()?.forEach { it.delete() } // ne garder qu'un seul APK
        val fichier = File(dossier, "toutiebudget-${version.versionCode}.apk")

        val request = Request.Builder().url(version.urlApk).build()
        val digest = MessageDigest.getInstance("SHA-256")
        httpClient.newCall(request).execute().use { reponse ->
            if (!reponse.isSuccessful) throw Exception("Téléchargement refusé (HTTP ${reponse.code})")
            val corps = reponse.body ?: throw Exception("Réponse vide")
            val total = corps.contentLength()
            corps.byteStream().use { entree ->
                fichier.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    var lus = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        digest.update(tampon, 0, n)
                        lus += n
                        if (total > 0) onProgression(lus.toFloat() / total)
                    }
                }
            }
        }

        val obtenu = digest.digest().joinToString("") { "%02x".format(it) }
        if (version.sha256 != null && version.sha256 != obtenu) {
            fichier.delete()
            throw Exception("Fichier corrompu (SHA-256 différent), mise à jour annulée")
        }
        fichier
    }

    /** Android exige que l'utilisateur autorise une fois l'app à installer des applications. */
    fun peutInstaller(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun intentAutorisationInstallation(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Installe l'APK par-dessus l'app actuelle via PackageInstaller.
     * Sur Android 12+, une fois que l'app s'est installée elle-même une première fois,
     * les mises à jour suivantes peuvent se faire sans écran de confirmation.
     * Le résultat arrive dans [MiseAJourReceiver]. L'app est redémarrée par Android.
     */
    fun installer(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { entree ->
                session.openWrite("toutiebudget.apk", 0, apk.length()).use { sortie ->
                    entree.copyTo(sortie)
                    session.fsync(sortie)
                }
            }
            val intent = Intent(context, MiseAJourReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pendingIntent.intentSender)
        }
    }

    private fun JsonObject.texte(cle: String): String? =
        get(cle)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}
