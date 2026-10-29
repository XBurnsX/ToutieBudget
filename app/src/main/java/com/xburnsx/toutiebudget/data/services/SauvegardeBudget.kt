package com.xburnsx.toutiebudget.data.services

import androidx.room.withTransaction
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.entities.AllocationMensuelle
import com.xburnsx.toutiebudget.data.room.entities.Categorie
import com.xburnsx.toutiebudget.data.room.entities.CompteCheque
import com.xburnsx.toutiebudget.data.room.entities.CompteCredit
import com.xburnsx.toutiebudget.data.room.entities.CompteDette
import com.xburnsx.toutiebudget.data.room.entities.CompteInvestissement
import com.xburnsx.toutiebudget.data.room.entities.Enveloppe
import com.xburnsx.toutiebudget.data.room.entities.PretPersonnel
import com.xburnsx.toutiebudget.data.room.entities.Tiers
import com.xburnsx.toutiebudget.data.room.entities.Transaction
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * PA3 : sauvegarde protégée et réimportable du budget de l'utilisateur connecté.
 *
 * Format du fichier (JSON, extension .toutiebudget.json) :
 * ```
 * { "format": "toutiebudget-sauvegarde", "version": 1,
 *   "chiffrement": "AES-256-GCM", "derivation": "PBKDF2WithHmacSHA256", "iterations": 210000,
 *   "sel": "<base64, 16 octets>", "iv": "<base64, 12 octets>", "donnees": "<base64 du contenu chiffré>" }
 * ```
 * Le contenu déchiffré est le JSON de [Contenu] : comptes (4 types), catégories, enveloppes,
 * allocations mensuelles, transactions, tiers et prêts personnels, tels qu'enregistrés dans Room.
 * GCM authentifie le fichier : un mauvais mot de passe ou un fichier modifié est refusé.
 * Les opérations bancaires importées (CSV) et leurs alias ne font pas partie de la sauvegarde.
 */
class SauvegardeBudget(private val db: ToutieBudgetDatabase) {

    data class Contenu(
        val version: Int = VERSION,
        val utilisateurId: String,
        val creeLe: Long,
        val comptesCheques: List<CompteCheque>,
        val comptesCredits: List<CompteCredit>,
        val comptesDettes: List<CompteDette>,
        val comptesInvestissement: List<CompteInvestissement>,
        val categories: List<Categorie>,
        val enveloppes: List<Enveloppe>,
        val allocations: List<AllocationMensuelle>,
        val transactions: List<Transaction>,
        val tiers: List<Tiers>,
        val pretsPersonnels: List<PretPersonnel>
    ) {
        /** Résumé lisible des quantités, montré avant toute restauration. */
        fun resume(): String = listOf(
            "comptes" to (comptesCheques.size + comptesCredits.size + comptesDettes.size + comptesInvestissement.size),
            "catégories" to categories.size, "enveloppes" to enveloppes.size, "allocations" to allocations.size,
            "transactions" to transactions.size, "tiers" to tiers.size, "prêts" to pretsPersonnels.size
        ).joinToString { (nom, n) -> "$n $nom" }

        val total: Int get() = comptesCheques.size + comptesCredits.size + comptesDettes.size + comptesInvestissement.size +
            categories.size + enveloppes.size + allocations.size + transactions.size + tiers.size + pretsPersonnels.size
    }

    companion object {
        const val FORMAT = "toutiebudget-sauvegarde"
        const val VERSION = 1
        private const val ITERATIONS = 210_000
        private val gson = GsonBuilder().serializeNulls().create()

        private fun cle(motDePasse: CharArray, sel: ByteArray, iterations: Int): SecretKeySpec {
            val spec = PBEKeySpec(motDePasse, sel, iterations, 256)
            try {
                val octets = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                return SecretKeySpec(octets, "AES")
            } finally {
                spec.clearPassword()
            }
        }

        fun chiffrer(contenu: Contenu, motDePasse: CharArray): ByteArray {
            require(motDePasse.size >= 8) { "Mot de passe de 8 caractères minimum." }
            val aleatoire = SecureRandom()
            val sel = ByteArray(16).also { aleatoire.nextBytes(it) }
            val iv = ByteArray(12).also { aleatoire.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, cle(motDePasse, sel, ITERATIONS), GCMParameterSpec(128, iv))
            val chiffre = cipher.doFinal(gson.toJson(contenu).toByteArray(Charsets.UTF_8))
            val enveloppe = JsonObject().apply {
                addProperty("format", FORMAT)
                addProperty("version", VERSION)
                addProperty("chiffrement", "AES-256-GCM")
                addProperty("derivation", "PBKDF2WithHmacSHA256")
                addProperty("iterations", ITERATIONS)
                addProperty("sel", Base64.getEncoder().encodeToString(sel))
                addProperty("iv", Base64.getEncoder().encodeToString(iv))
                addProperty("donnees", Base64.getEncoder().encodeToString(chiffre))
            }
            return gson.toJson(enveloppe).toByteArray(Charsets.UTF_8)
        }

        /** Déchiffre et valide un fichier ; lève une erreur claire si le fichier ou le mot de passe ne convient pas. */
        fun lire(fichier: ByteArray, motDePasse: CharArray): Contenu {
            val enveloppe = runCatching {
                com.google.gson.JsonParser.parseString(String(fichier, Charsets.UTF_8)).asJsonObject
            }.getOrElse { throw IllegalArgumentException("Ce fichier n'est pas une sauvegarde ToutieBudget.") }
            require(enveloppe.get("format")?.asString == FORMAT) { "Ce fichier n'est pas une sauvegarde ToutieBudget." }
            val version = enveloppe.get("version")?.asInt ?: 0
            require(version in 1..VERSION) { "Version de sauvegarde non prise en charge ($version)." }
            val sel = Base64.getDecoder().decode(enveloppe.get("sel").asString)
            val iv = Base64.getDecoder().decode(enveloppe.get("iv").asString)
            val donnees = Base64.getDecoder().decode(enveloppe.get("donnees").asString)
            val iterations = enveloppe.get("iterations")?.asInt ?: ITERATIONS
            val clair = runCatching {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, cle(motDePasse, sel, iterations), GCMParameterSpec(128, iv))
                cipher.doFinal(donnees)
            }.getOrElse { throw IllegalArgumentException("Mot de passe incorrect ou fichier endommagé.") }
            val contenu = gson.fromJson(String(clair, Charsets.UTF_8), Contenu::class.java)
                ?: throw IllegalArgumentException("Sauvegarde vide.")
            require(!contenu.utilisateurId.isNullOrBlank()) { "Sauvegarde sans propriétaire." }
            return contenu
        }
    }

    /** Lit tout le budget de l'utilisateur connecté dans Room. */
    suspend fun contenuActuel(): Contenu {
        val uid = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: throw IllegalStateException("Connexion requise.")
        return Contenu(
            utilisateurId = uid,
            creeLe = System.currentTimeMillis(),
            comptesCheques = db.compteChequeDao().getComptesByUtilisateur(uid).first(),
            comptesCredits = db.compteCreditDao().getComptesByUtilisateur(uid).first(),
            comptesDettes = db.compteDetteDao().getComptesByUtilisateur(uid).first(),
            comptesInvestissement = db.compteInvestissementDao().getComptesByUtilisateur(uid).first(),
            categories = db.categorieDao().getCategoriesByUtilisateur(uid).first(),
            enveloppes = db.enveloppeDao().getEnveloppesByUtilisateur(uid).first(),
            allocations = db.allocationMensuelleDao().getAllocationsByUtilisateur(uid).first(),
            transactions = db.transactionDao().getTransactionsByUtilisateur(uid).first(),
            tiers = db.tiersDao().getTiersByUtilisateur(uid).first(),
            pretsPersonnels = db.pretPersonnelDao().getPretsByUtilisateur(uid).first()
        )
    }

    /** Fichier chiffré du budget actuel. */
    suspend fun exporter(motDePasse: CharArray): Pair<ByteArray, Contenu> {
        val contenu = contenuActuel()
        return chiffrer(contenu, motDePasse) to contenu
    }

    /**
     * Restaure une sauvegarde dans un budget VIDE du MÊME utilisateur (jamais de mélange ni de doublon).
     * Tout est écrit dans une seule transaction Room avec ses SyncJobs CREATE : rien de partiel.
     */
    suspend fun restaurer(contenu: Contenu): Int {
        val uid = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: throw IllegalStateException("Connexion requise.")
        require(contenu.utilisateurId == uid) { "Cette sauvegarde appartient à un autre compte : restauration refusée." }
        require(contenuActuel().total == 0) {
            "Le budget actuel n'est pas vide. Utilise d'abord « Commencer un nouveau budget vide », puis restaure."
        }
        val jobs = db.syncJobDao()
        db.withTransaction {
            suspend fun envoyer(entite: Any, id: String) = SyncPayloads.enregistrer(jobs, "CREATE", entite, id)
            contenu.comptesCheques.forEach { db.compteChequeDao().insertCompte(it); envoyer(it, it.id) }
            contenu.comptesCredits.forEach { db.compteCreditDao().insertCompte(it); envoyer(it, it.id) }
            contenu.comptesDettes.forEach { db.compteDetteDao().insertCompte(it); envoyer(it, it.id) }
            contenu.comptesInvestissement.forEach { db.compteInvestissementDao().insertCompte(it); envoyer(it, it.id) }
            contenu.categories.forEach { db.categorieDao().insertCategorie(it); envoyer(it, it.id) }
            contenu.enveloppes.forEach { db.enveloppeDao().insertEnveloppe(it); envoyer(it, it.id) }
            contenu.tiers.forEach { db.tiersDao().insertTiers(it); envoyer(it, it.id) }
            contenu.pretsPersonnels.forEach { db.pretPersonnelDao().insertPret(it); envoyer(it, it.id) }
            contenu.allocations.forEach { db.allocationMensuelleDao().insertAllocation(it); envoyer(it, it.id) }
            contenu.transactions.forEach { db.transactionDao().insertTransaction(it); envoyer(it, it.id) }
        }
        return contenu.total
    }
}
