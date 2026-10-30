package com.xburnsx.toutiebudget.ui.navigation

import org.junit.Test
import java.io.File
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** NVA5 : inventaire des routes vérifié automatiquement (sans émulateur). */
class RoutesTest {

    private fun chemin(route: String) = route.substringBefore('?')

    @Test
    fun `chaque route typee a le meme nombre de segments que sa route declaree`() {
        Routes.inventaire.forEach { (modele, exemple) ->
            assertEquals(
                chemin(modele).split('/').size, chemin(exemple).split('/').size,
                "Segments différents pour $modele : $exemple"
            )
            assertTrue(chemin(exemple).split('/').none { it.isEmpty() }, "Segment vide dans $exemple")
        }
    }

    @Test
    fun `chaque route de l'inventaire est declaree dans le NavHost`() {
        val source = File("src/main/java/com/xburnsx/toutiebudget/ui/navigation/Navigation.kt").readText()
        Routes.inventaire.forEach { (modele, _) ->
            assertTrue(source.contains("\"$modele\""), "Route absente de Navigation.kt : $modele")
        }
    }

    @Test
    fun `noms avec barre oblique, point d'interrogation, diese, accents et espaces sont encodes`() {
        val route = Routes.historiqueEnveloppe("env1", "Auto / Réparations ?# l'été")
        val nomEncode = route.split('/').last()
        listOf('/', '?', '#', ' ').forEach { assertFalse(nomEncode.contains(it), "« $it » non encodé : $route") }
        assertEquals("Auto / Réparations ?# l'été", URLDecoder.decode(nomEncode, "UTF-8"))
    }

    @Test
    fun `un nom vide ne cree jamais de segment vide`() {
        val route = Routes.historiqueCompte("abc", "comptes_cheques", "")
        assertEquals("historique_compte/abc/comptes_cheques/${Routes.VIDE}", route)
    }

    @Test
    fun `identifiants de route invalides refuses`() {
        listOf(null, "", " ", Routes.VIDE, "a b", "a/b", "x".repeat(65)).forEach {
            assertFalse(Routes.idValide(it), "accepté à tort : $it")
        }
        assertTrue(Routes.idValide("k3j2h4g5f6d7s8a"))
    }
}
