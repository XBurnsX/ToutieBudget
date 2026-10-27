package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.ReleveCarte
import java.util.Locale

/**
 * CC6 : import de relevés officiels au format CSV ToutieBudget.
 * Colonnes, séparées par « ; » ou « , » :
 * date_emission;date_echeance;solde_officiel;minimum_officiel;interets_factures;frais_factures;paiements_recus
 *
 * Aucun rapprochement bancaire n'est déduit d'un CSV non structuré.
 * Les montants du relevé sont positifs. Un import ne modifie PAS le solde bancaire.
 */
object ImportRelevesCarteCsv {
    private val colonnes = listOf(
        "date_emission", "date_echeance", "solde_officiel", "minimum_officiel",
        "interets_factures", "frais_factures", "paiements_recus"
    )

    private fun cellules(ligne: String, separateur: Char): List<String> {
        val resultat = mutableListOf<String>()
        val champ = StringBuilder()
        var guillemets = false
        var i = 0
        while (i < ligne.length) {
            val c = ligne[i]
            if (c == '"') {
                if (guillemets && i + 1 < ligne.length && ligne[i + 1] == '"') {
                    champ.append('"')
                    i++
                } else guillemets = !guillemets
            } else if (c == separateur && !guillemets) {
                resultat.add(champ.toString().trim())
                champ.clear()
            } else champ.append(c)
            i++
        }
        require(!guillemets) { "CSV : guillemets non fermés." }
        resultat.add(champ.toString().trim())
        return resultat
    }

    fun lire(texte: String): Result<List<ReleveCarte>> = runCatching {
        val lignes = texte.removePrefix("\uFEFF").lineSequence()
            .map { it.trim() }.filter { it.isNotBlank() }.toList()
        require(lignes.size in 2..1001) { "Le CSV doit contenir un en-tête et de 1 à 1000 relevés." }
        val separateur = if (lignes[0].count { it == ';' } > lignes[0].count { it == ',' }) ';' else ','
        val noms = cellules(lignes[0], separateur).map { it.lowercase(Locale.ROOT).trim() }
        require(noms.size == noms.distinct().size && colonnes.all { it in noms }) {
            "En-tête CSV requis : ${colonnes.joinToString(";")}"
        }
        fun get(cellules: List<String>, colonne: String) = cellules[noms.indexOf(colonne)]
        fun montant(ligne: List<String>, colonne: String, requis: Boolean = false): Double? {
            val valeur = get(ligne, colonne).replace(" ", "").replace(',', '.')
            if (valeur.isBlank() && !requis) return null
            val chiffre = valeur.toDoubleOrNull()
            require(chiffre != null && chiffre.isFinite() && chiffre >= 0.0) {
                "Montant invalide dans la colonne $colonne."
            }
            return chiffre
        }
        val resultat = lignes.drop(1).mapIndexed { indice, ligne ->
            try {
                val valeurs = cellules(ligne, separateur)
                require(valeurs.size == noms.size) { "Nombre de colonnes invalide." }
                val date = get(valeurs, "date_emission")
                val echeance = get(valeurs, "date_echeance").ifBlank { null }
                val debut = MoteurCarteCredit.lireDate(date)
                    ?: throw IllegalArgumentException("Date d'émission invalide.")
                val fin = MoteurCarteCredit.lireDate(echeance)
                require(echeance == null || (fin != null && !fin.before(debut))) {
                    "Date d'échéance invalide ou antérieure à l'émission."
                }
                ReleveCarte(
                    dateEmission = date,
                    dateEcheance = echeance,
                    soldeOfficiel = montant(valeurs, "solde_officiel", requis = true)!!,
                    minimumOfficiel = montant(valeurs, "minimum_officiel"),
                    interetsFactures = montant(valeurs, "interets_factures") ?: 0.0,
                    fraisFactures = montant(valeurs, "frais_factures") ?: 0.0,
                    paiementsRecus = montant(valeurs, "paiements_recus") ?: 0.0
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("Ligne CSV ${indice + 2} : ${e.message}", e)
            }
        }
        require(resultat.map { it.dateEmission }.distinct().size == resultat.size) {
            "Plusieurs lignes du CSV ont la même date de relevé."
        }
        resultat
    }
}
