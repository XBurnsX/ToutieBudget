package com.xburnsx.toutiebudget.data.banque

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.text.Normalizer
import java.util.Locale

/** Lecteur CSV conservateur : aucun débit n'est effectué pendant la lecture. */
object ImportCsvBancaire {
    data class Ligne(
        val idExterne: String,
        val date: String,
        val montantCentimes: Long,
        val libelleBrut: String,
        val statut: String
    )

    fun analyser(texte: String): List<Ligne> {
        require(texte.toByteArray(Charsets.UTF_8).size <= 5_000_000) { "CSV de plus de 5 Mo." }
        val source = texte.removePrefix("\uFEFF")
        val entete = source.lineSequence().firstOrNull().orEmpty()
        val delimiter = if (nombreDelimiteurs(entete, ';') > nombreDelimiteurs(entete, ',')) ';' else ','
        val lignes = decouper(source, delimiter).filter { it.any(String::isNotBlank) }
        require(lignes.size in 2..10001) { "CSV vide ou plus de 10 000 opérations." }
        val champs = lignes.first().map(::normaliserEntete)
        fun colonne(vararg candidats: String) = champs.indexOfFirst { it in candidats }
        val d = colonne("date", "transaction date", "posted date", "date operation")
        val m = colonne("amount", "montant", "total", "transaction amount")
        val l = colonne("description", "libelle", "merchant", "payee", "transaction", "tiers")
        val id = colonne("id", "transaction id", "reference", "external id")
        val status = colonne("status", "statut")
        val devise = colonne("currency", "devise")
        require(d >= 0 && m >= 0 && l >= 0) {
            "Colonnes nécessaires : date, amount/montant et description/libellé."
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8)).take(8)
            .joinToString("") { "%02x".format(it) }
        return lignes.drop(1).mapIndexed { i, cols ->
            val numero = i + 2
            require(cols.size == champs.size) { "Ligne " + numero + " : colonnes invalides." }
            fun champ(indice: Int) = if (indice < 0) "" else cols[indice].trim()
            val currency = champ(devise).uppercase(Locale.ROOT)
            require(currency == "" || currency == "CAD") {
                "Ligne " + numero + " : devise non prise en charge : " + currency
            }
            val libelle = champ(l)
            require(libelle.isNotBlank() && libelle.length <= 512) { "Ligne " + numero + " : libellé invalide." }
            val identifiant = champ(id).ifBlank { "csv-" + digest + "-ligne-" + numero }
            require(identifiant.length <= 256) { "Ligne " + numero + " : ID trop long." }
            val statut = when (champ(status).lowercase(Locale.ROOT)) {
                "", "posted", "completed", "final", "finalisee", "finalise", "finalisé", "finalisée" -> "FINALISEE"
                "pending", "en attente", "en_attente" -> "EN_ATTENTE"
                else -> throw IllegalArgumentException("Ligne " + numero + " : statut inconnu.")
            }
            Ligne(identifiant, date(champ(d), numero), cents(champ(m), numero), libelle, statut)
        }
    }

    private fun normaliserEntete(entete: String) =
        Normalizer.normalize(entete.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).replace(Regex("[_\\s]+"), " ")

    private fun date(valeur: String, n: Int): String {
        val texte = valeur.trim()
        val resultat = runCatching {
            when {
                texte.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) -> LocalDate.parse(texte.take(10))
                texte.matches(Regex("\\d{4}/\\d{2}/\\d{2}")) ->
                    LocalDate.parse(texte, DateTimeFormatter.ofPattern("uuuu/MM/dd")
                        .withResolverStyle(ResolverStyle.STRICT))
                texte.matches(Regex("\\d{2}/\\d{2}/\\d{4}")) -> {
                    val composants = texte.split("/")
                    val premier = composants[0].toInt()
                    val deuxieme = composants[1].toInt()
                    require(premier > 12 || deuxieme > 12) { "Date jj/mm ou mm/jj ambiguë." }
                    val patron = if (premier > 12) "dd/MM/uuuu" else "MM/dd/uuuu"
                    LocalDate.parse(texte, DateTimeFormatter.ofPattern(patron)
                        .withResolverStyle(ResolverStyle.STRICT))
                }
                else -> throw IllegalArgumentException("Format inconnu.")
            }
        }.getOrElse { throw IllegalArgumentException("Ligne " + n + " : date invalide (" + texte + ").") }
        return resultat.toString()
    }

    private fun cents(valeur: String, n: Int): Long {
        val brut = valeur.trim().replace("\u00A0", "").replace(" ", "")
            .replace("$", "").replace("CAD", "", ignoreCase = true)
        val parenthese = brut.startsWith("(") && brut.endsWith(")")
        var nombre = if (parenthese) brut.removePrefix("(").removeSuffix(")") else brut
        nombre = if (nombre.contains(',') && nombre.contains('.')) {
            if (nombre.lastIndexOf('.') > nombre.lastIndexOf(',')) nombre.replace(",", "")
            else nombre.replace(".", "").replace(",", ".")
        } else nombre.replace(",", ".")
        return try {
            val resultat = BigDecimal(nombre).setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2).longValueExact()
            if (parenthese) -resultat else resultat
        } catch (e: Exception) {
            throw IllegalArgumentException("Ligne " + n + " : montant invalide (" + valeur + ").", e)
        }
    }

    private fun nombreDelimiteurs(ligne: String, delimiteur: Char): Int {
        var dansGuillemets = false
        var nombre = 0
        ligne.forEach {
            if (it == '"') dansGuillemets = !dansGuillemets
            else if (!dansGuillemets && it == delimiteur) nombre++
        }
        return nombre
    }

    /** Traite les virgules et retours à la ligne protégés par guillemets RFC4180. */
    private fun decouper(texte: String, delimiteur: Char): List<List<String>> {
        val resultat = mutableListOf<List<String>>()
        val colonnes = mutableListOf<String>()
        val champ = StringBuilder()
        var dansGuillemets = false
        var i = 0
        fun fermerChamp() { colonnes.add(champ.toString()); champ.setLength(0) }
        fun fermerLigne() { fermerChamp(); resultat.add(colonnes.toList()); colonnes.clear() }
        while (i < texte.length) {
            val c = texte[i]
            when {
                c == '"' && dansGuillemets && i + 1 < texte.length && texte[i + 1] == '"' -> {
                    champ.append('"'); i++
                }
                c == '"' -> dansGuillemets = !dansGuillemets
                !dansGuillemets && c == delimiteur -> fermerChamp()
                !dansGuillemets && (c == '\n' || c == '\r') -> {
                    fermerLigne()
                    if (c == '\r' && i + 1 < texte.length && texte[i + 1] == '\n') i++
                }
                else -> champ.append(c)
            }
            i++
        }
        require(!dansGuillemets) { "Guillemet CSV non fermé." }
        if (champ.isNotEmpty() || colonnes.isNotEmpty()) fermerLigne()
        return resultat
    }
}
