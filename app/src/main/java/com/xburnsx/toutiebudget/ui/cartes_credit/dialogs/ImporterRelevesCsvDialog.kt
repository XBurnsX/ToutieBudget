package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.domain.services.ImportRelevesCarteCsv

/**
 * CC6 : import local volontaire d'un CSV structuré de relevés mensuels.
 * Prévisualisation et validation explicite avant la moindre écriture Room.
 */
@Composable
fun ImporterRelevesCsvDialog(
    carte: CompteCredit,
    onImporter: (String) -> Unit,
    onFermer: () -> Unit
) {
    val context = LocalContext.current
    var texte by remember(carte.id) { mutableStateOf("") }
    var erreurLecture by remember(carte.id) { mutableStateOf<String?>(null) }
    val selecteur = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText().take(250_001)
                } ?: throw IllegalStateException("Impossible de lire le fichier.")
            }.onSuccess { contenu ->
                texte = contenu
                erreurLecture = if (contenu.length > 250_000) "Fichier trop volumineux (250 Ko maximum)." else null
            }.onFailure {
                erreurLecture = "Lecture du CSV impossible : ${it.message}"
            }
        }
    }
    val resultat = remember(texte) { ImportRelevesCarteCsv.lire(texte) }
    val releves = resultat.getOrNull().orEmpty()
    val datesExistantes = carte.details.releves.map { it.dateEmission }.toSet()
    val nouveaux = releves.count { it.dateEmission !in datesExistantes }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Importer des relevés CSV") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Format ToutieBudget : une ligne par relevé officiel; aucune transaction bancaire " +
                        "ni aucun intérêt ne sera ajouté au solde par cet import."
                )
                Text("Colonnes : date_emission;date_echeance;solde_officiel;minimum_officiel;interets_factures;frais_factures;paiements_recus")
                TextButton(onClick = { selecteur.launch(arrayOf("*/*")) }) {
                    Text("Choisir un fichier CSV sur l'appareil")
                }
                OutlinedTextField(
                    value = texte,
                    onValueChange = { texte = it; erreurLecture = null },
                    modifier = Modifier,
                    label = { Text("Ou coller le contenu CSV") },
                    minLines = 4, maxLines = 7
                )
                erreurLecture?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                if (texte.isNotBlank()) {
                    resultat.onSuccess {
                        Text("${releves.size} relevés valides · $nouveaux nouveaux · " +
                            "${releves.size - nouveaux} déjà présents (ignorés).")
                    }.onFailure {
                        Text(it.message ?: "Format CSV invalide.",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                }
                Text("Import limité à 1000 lignes. Les relevés dupliqués par date ne sont pas écrasés.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = erreurLecture == null && nouveaux > 0 && resultat.isSuccess,
                onClick = { onImporter(texte) }
            ) { Text("Importer $nouveaux relevés") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } }
    )
}
