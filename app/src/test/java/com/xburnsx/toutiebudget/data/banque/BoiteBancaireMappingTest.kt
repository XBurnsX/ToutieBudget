package com.xburnsx.toutiebudget.data.banque

import com.google.gson.Gson
import com.xburnsx.toutiebudget.data.services.ItemPocketBase
import com.xburnsx.toutiebudget.data.services.PocketBaseMapper
import com.xburnsx.toutiebudget.workers.SyncCollections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoiteBancaireMappingTest {
    private val gson = Gson()

    @Test fun leTypeSyncJobPointeSurLaBonneCollection() {
        assertEquals("operations_bancaires_attente",
            SyncCollections.collectionPourType("OPERATION_BANCAIRE"))
        assertTrue("operations_bancaires_attente" in PocketBaseMapper.collectionsSynchronisees)
    }

    @Test fun importeDesCentimesSansConversionDouble() {
        val json = """{"id":"1234567890abcde","utilisateur_id":"tester","compte_id":"compte1","collection_compte":"comptes_cheques","source":"CSV","id_externe":"tx01","date_operation":"2026-10-28","montant_centimes":"-501","libelle_brut":"IRV 0421 QC","statut_bancaire":"FINALISEE","decision":"A_CONFIRMER","verification_requise":false}"""
        val fiche = gson.fromJson(json, ItemPocketBase::class.java)
        val result = PocketBaseMapper.versOperationBancaire(fiche)
        assertEquals(-501L, result?.montantCentimes)
        assertEquals("IRV 0421 QC", result?.libelleBrut)
        assertEquals("tx01", result?.idExterne)
        assertEquals("A_CONFIRMER", result?.decision)
        assertEquals("tester", result?.utilisateurId)
    }

    @Test fun refuseMontantNonEntierOuUtilisateurAbsent() {
        val json = """{"id":"1234567890abcde","utilisateur_id":"tester","compte_id":"compte1","collection_compte":"comptes_cheques","source":"CSV","id_externe":"tx01","date_operation":"2026-10-28","montant_centimes":"-5.01","libelle_brut":"IRV","statut_bancaire":"FINALISEE","decision":"A_CONFIRMER"}"""
        assertNull(PocketBaseMapper.versOperationBancaire(
            gson.fromJson(json, ItemPocketBase::class.java)))
        assertNull(PocketBaseMapper.versOperationBancaire(
            gson.fromJson(json.replace("\"tester\"", "\"\"")
                .replace("\"-5.01\"", "\"-501\""), ItemPocketBase::class.java)))
    }

    @Test fun refuseDecisionInconnueSansComptabiliser() {
        val json = """{"id":"1234567890abcde","utilisateur_id":"tester","compte_id":"compte1","collection_compte":"comptes_cheques","source":"CSV","id_externe":"tx01","date_operation":"2026-10-28","montant_centimes":"-501","libelle_brut":"IRV","statut_bancaire":"FINALISEE","decision":"CONFIRMEE"}"""
        assertNull(PocketBaseMapper.versOperationBancaire(
            gson.fromJson(json, ItemPocketBase::class.java)))
    }
}
