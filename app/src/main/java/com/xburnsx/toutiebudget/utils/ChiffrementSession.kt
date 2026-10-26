package com.xburnsx.toutiebudget.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffre le jeton de session avec une clé AES gardée dans l'Android Keystore (LD4).
 * La clé ne quitte jamais l'appareil : un jeton restauré par une sauvegarde sur un autre
 * appareil ne se déchiffre pas, et l'utilisateur doit simplement se reconnecter.
 */
object ChiffrementSession {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "toutiebudget_session"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAILLE_IV = 12
    private const val TAILLE_TAG_BITS = 128

    fun chiffrer(texte: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, cle())
        val chiffre = cipher.doFinal(texte.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + chiffre, Base64.NO_WRAP)
    }

    /** Null si le texte ne peut pas être déchiffré (autre appareil, clé effacée, données abîmées). */
    fun dechiffrer(texteChiffre: String): String? = try {
        val donnees = Base64.decode(texteChiffre, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            cle(),
            GCMParameterSpec(TAILLE_TAG_BITS, donnees, 0, TAILLE_IV)
        )
        String(cipher.doFinal(donnees, TAILLE_IV, donnees.size - TAILLE_IV), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun cle(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generateur.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generateur.generateKey()
    }
}
