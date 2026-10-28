package com.xburnsx.toutiebudget.data.banque

import com.google.gson.Gson
import com.xburnsx.toutiebudget.data.services.ItemPocketBase
import com.xburnsx.toutiebudget.data.services.PocketBaseMapper
import com.xburnsx.toutiebudget.workers.SyncCollections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasBancaireSynchronisationTest {
    private val gson = Gson()

    @Test fun collectionDuSyncJobCorrespondALaCollectionPocketBase() {
        assertEquals("alias_bancaires", SyncCollections.collectionPourType("ALIAS_BANCAIRE"))
        assertTrue("alias_bancaires" in PocketBaseMapper.collectionsSynchronisees)
    }

    @Test fun libelleOriginalEtIdentifiantsSontConserves() {
        val item = gson.fromJson(
            """{"id":"alias1234567890","utilisateur_id":"tester","compte_id":"ws","libelle_brut":" IRV 0421 QC ","tiers_id":"tiers1","active":true}""",
            ItemPocketBase::class.java
        )
        val regle = PocketBaseMapper.versAliasBancaire(item)
        assertEquals(" IRV 0421 QC ", regle?.libelleBrut)
        assertEquals("tester", regle?.utilisateurId)
        assertEquals("tiers1", regle?.tiersId)
        assertEquals("ws", regle?.compteId)
        assertTrue(regle?.active == true)
    }

    @Test fun compteVideSignifieRegleGeneraleEtDesactivationEstRespectee() {
        val item = gson.fromJson(
            """{"id":"alias1234567890","utilisateur_id":"tester","compte_id":"","libelle_brut":"IRV","tiers_id":"tiers1","active":false}""",
            ItemPocketBase::class.java
        )
        val regle = PocketBaseMapper.versAliasBancaire(item)
        assertNull(regle?.compteId)
        assertFalse(regle?.active ?: true)
    }

    @Test fun reglesSansProprietaireOuTiersValideSontRefusees() {
        val sansUser = gson.fromJson(
            """{"id":"alias1234567890","libelle_brut":"IRV","tiers_id":"tiers1","active":true}""",
            ItemPocketBase::class.java
        )
        val sansTiers = gson.fromJson(
            """{"id":"alias1234567890","utilisateur_id":"tester","libelle_brut":"IRV","tiers_id":""}""",
            ItemPocketBase::class.java
        )
        assertNull(PocketBaseMapper.versAliasBancaire(sansUser))
        assertNull(PocketBaseMapper.versAliasBancaire(sansTiers))
    }
}
