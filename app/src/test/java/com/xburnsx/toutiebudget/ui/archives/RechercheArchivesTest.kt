package com.xburnsx.toutiebudget.ui.archives

import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RechercheArchivesTest {
    private fun compte(
        id: String, nom: String, archive: Boolean
    ) = CompteCheque(
        id = id, utilisateurId = "u", nom = nom, solde = 0.0,
        couleur = "#000000", estArchive = archive, ordre = 0
    )

    @Test fun rechercheAccentEtExclusionDesComptesActifs() {
        val archives = listOf(
            compte("a", "Épargne Noël", true),
            compte("b", "Épargne Noël", false),
            compte("c", "Vacances", true)
        )
        val resultats = RechercheArchives.comptes(archives, "epargne noel", "comptes_cheques")
        assertEquals(listOf("a"), resultats.map { it.id })
        assertTrue(RechercheArchives.comptes(
            archives, "epargne noel", "comptes_credits"
        ).isEmpty())
    }

    @Test fun rechercheParCategorieSansInventerLesCategoriesSupprimees() {
        val archives = listOf(
            Enveloppe(id = "a", utilisateurId = "u", nom = "Loyer",
                categorieId = "c1", estArchive = true),
            Enveloppe(id = "b", utilisateurId = "u", nom = "Électricité",
                categorieId = "c2", estArchive = true),
            Enveloppe(id = "c", utilisateurId = "u", nom = "Internet",
                categorieId = "c1", estArchive = false)
        )
        val resultats = RechercheArchives.enveloppes(
            archives, "habitation", mapOf("c1" to "Habitation", "c2" to "Factures")
        )
        assertEquals(listOf("a"), resultats.map { it.id })
        assertEquals(listOf("b"), RechercheArchives.enveloppes(
            archives, "electricite", emptyMap()
        ).map { it.id })
        assertTrue(RechercheArchives.enveloppes(archives, "c1", emptyMap()).isEmpty())
    }
}
