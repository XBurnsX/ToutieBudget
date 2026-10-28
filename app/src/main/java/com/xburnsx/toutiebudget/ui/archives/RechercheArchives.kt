package com.xburnsx.toutiebudget.ui.archives

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import java.text.Normalizer
import java.util.Locale

/**
 * ARA1/ARA2 : recherche tolérante aux accents, indépendante des écrans
 * Budget/Comptes, sans mutations ni changement de filtre global.
 */
object RechercheArchives {
    private fun normaliser(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.CANADA_FRENCH)
            .trim()

    fun comptes(
        archives: List<Compte>, recherche: String, collection: String
    ): List<Compte> {
        val terme = normaliser(recherche)
        return archives.filter { compte ->
            compte.estArchive &&
                (collection.isBlank() || compte.collection == collection) &&
                (terme.isEmpty() ||
                    normaliser(compte.nom).contains(terme) ||
                    normaliser(compte.collection).contains(terme))
        }
    }

    fun enveloppes(
        archives: List<Enveloppe>,
        recherche: String,
        nomsCategories: Map<String, String>
    ): List<Enveloppe> {
        val terme = normaliser(recherche)
        return archives.filter { env ->
            env.estArchive && (terme.isEmpty() ||
                normaliser(env.nom).contains(terme) ||
                normaliser(nomsCategories[env.categorieId].orEmpty()).contains(terme))
        }
    }
}
