package com.xburnsx.toutiebudget.donnees.mappeur

import com.xburnsx.toutiebudget.domaine.modele.Compte
import com.xburnsx.toutiebudget.domaine.modele.Enveloppe
import com.xburnsx.toutiebudget.domaine.modele.EtatEnveloppeMensuel
import com.xburnsx.toutiebudget.donnees.temporaire.Record
import java.util.Date

// Fonctions de mapping temporaires pour éviter les erreurs de compilation

fun Record.versCompte(): Compte {
    return Compte(
        id = this.id,
        nom = this.getString("nom"),
        solde = this.getDouble("solde"),
        pretAPlacer = this.getDouble("pret_a_placer"),
        couleur = this.getLong("couleur"),
        type = this.getString("type"),
        estArchive = this.getBoolean("est_archive"),
        ordre = this.getInt("ordre")
    )
}

fun Record.versEnveloppe(): Enveloppe {
    return Enveloppe(
        id = this.id,
        nom = this.getString("nom"),
        objectifMontant = this.getDouble("objectif_montant"),
        frequenceObjectif = this.getString("frequence_objectif"),
        dateCibleObjectif = null, // Valeur temporaire pour la compilation
        jourObjectif = this.getInt("jour_objectif"),
        estArchivee = this.getBoolean("est_archivee"),
        ordre = this.getInt("ordre")
    )
}

fun Record.versEtatEnveloppeMensuel(): EtatEnveloppeMensuel {
    return EtatEnveloppeMensuel(
        id = this.id,
        idEnveloppe = this.getString("id_enveloppe"),
        mois = this.getString("mois"),
        solde = this.getDouble("solde"),
        depensesDuMois = this.getDouble("depenses_du_mois"),
        idCompteProvenance = this.getString("id_compte_provenance")
    )
}
