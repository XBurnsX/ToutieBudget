// chemin/simule: /ui/budget/composants/RepartitionMultipleDialog.kt
// A2 : distribuer le « Prêt à placer » d'un compte vers plusieurs enveloppes en une opération.

package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import com.xburnsx.toutiebudget.ui.budget.CategorieEnveloppesUi
import com.xburnsx.toutiebudget.utils.MoneyFormatter

@Composable
fun RepartitionMultipleDialog(
    comptes: List<CompteBudget>,
    categories: List<CategorieEnveloppesUi>,
    enCours: Boolean,
    erreur: String?,
    onConfirmer: (compteId: String, montantsCentimes: Map<String, Long>) -> Unit,
    onFermer: () -> Unit
) {
    var compteId by remember { mutableStateOf(comptes.firstOrNull()?.id) }
    val saisies = remember { mutableStateMapOf<String, Long>() }
    // Enveloppe dont le montant est en cours de saisie avec ClavierNumerique
    var enSaisie by remember { mutableStateOf<String?>(null) }
    var recherche by remember { mutableStateOf("") }
    var apercu by remember { mutableStateOf(false) }

    val compte = comptes.find { it.id == compteId }
    val toutesEnveloppes = categories.flatMap { it.enveloppes }
    val saisieInvalide = saisies.values.any { it < 0L }
    val montantsValides = saisies.filterValues { it > 0L }.toMap()
    val totalCentimesOuErreur = runCatching {
        montantsValides.values.fold(0L) { total, valeur -> Math.addExact(total, valeur) }
    }.getOrNull()
    val totalCentimes = totalCentimesOuErreur ?: 0L
    val disponibleCentimes = compte?.let { Math.round(it.pretAPlacer * 100.0) } ?: 0L
    val resteCentimesOuErreur = runCatching {
        Math.subtractExact(disponibleCentimes, totalCentimes)
    }.getOrNull()
    val resteCentimes = resteCentimesOuErreur ?: Long.MIN_VALUE

    // Hauteur réelle de la barre de navigation de l'appareil, lue sur l'écran de l'app :
    // dans un dialogue plein écran, Android ne la transmet pas toujours (marge à 0, bouton caché).
    val contexte = androidx.compose.ui.platform.LocalContext.current
    val densite = androidx.compose.ui.platform.LocalDensity.current
    val barreNavigation = remember(contexte) {
        var c = contexte
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.window?.decorView?.let { vue ->
            androidx.core.view.ViewCompat.getRootWindowInsets(vue)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom
        } ?: 0
    }
    // Même marge du bas que le clavier de ComptesScreen (celle qui fonctionne sur les Pixel),
    // ou la barre de navigation rapportée par Android si elle est plus grande.
    val hauteurEcran = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val margeComptes = when {
        hauteurEcran in 800..850 -> 72.dp
        hauteurEcran > 850 -> 88.dp
        hauteurEcran < 800 -> 56.dp
        else -> 64.dp
    }
    val bas = maxOf(with(densite) { barreNavigation.toDp() }, margeComptes)

    Dialog(
        onDismissRequest = { if (!enCours) onFermer() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Le plus grand des deux : marges du système, ou barre de navigation de l'appareil
                    .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets(bottom = bas)))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (apercu) "Aperçu de la répartition" else "Répartir vers plusieurs enveloppes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFermer, enabled = !enCours) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                if (comptes.isEmpty()) {
                    Text(
                        "Aucun compte n'a d'argent prêt à placer.",
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    return@Column
                }

                if (!apercu) {
                    // Compte source : la provenance de chaque enveloppe sera ce compte
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(comptes, key = { it.id }) { c ->
                            FilterChip(
                                selected = c.id == compteId,
                                onClick = { compteId = c.id },
                                label = { Text("${c.nom} · ${MoneyFormatter.formatAmount(c.pretAPlacer)}") },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(Color(c.couleur.toColorInt()), CircleShape)
                                    )
                                }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = recherche,
                            onValueChange = { recherche = it },
                            placeholder = { Text("Rechercher") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            toutesEnveloppes
                                .filter { !it.estArchive && it.versementRecommande.isFinite() &&
                                    it.versementRecommande >= 0.01 }
                                .forEach { saisies[it.id] = Math.round(it.versementRecommande * 100) }
                        }) { Text("Selon objectifs") }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        categories.forEach { categorie ->
                            val visibles = categorie.enveloppes.filter {
                                !it.estArchive && (recherche.isBlank() || it.nom.contains(recherche.trim(), ignoreCase = true))
                            }
                            if (visibles.isNotEmpty()) {
                                item(key = "cat_${categorie.nomCategorie}") {
                                    Text(
                                        categorie.nomCategorie,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(visibles, key = { it.id }) { enveloppe ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(enveloppe.nom, fontWeight = FontWeight.Medium)
                                            Text(
                                                "Solde ${MoneyFormatter.formatAmount(enveloppe.solde)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (enveloppe.solde < -0.001) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        // Montant : touché → ClavierNumerique de l'app (jamais le clavier Android)
                                        OutlinedButton(
                                            onClick = { enSaisie = enveloppe.id },
                                            modifier = Modifier.width(130.dp)
                                        ) {
                                            Text(MoneyFormatter.formatAmountFromCents(saisies[enveloppe.id] ?: 0L))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Aperçu avant validation
                    Text(
                        "Depuis ${compte?.nom ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(montantsValides.entries.toList(), key = { it.key }) { (id, centimes) ->
                            val enveloppe = toutesEnveloppes.find { it.id == id }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(enveloppe?.nom ?: id, modifier = Modifier.weight(1f))
                                Text(MoneyFormatter.formatAmountFromCents(centimes), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Total", modifier = Modifier.weight(1f))
                    Text(totalCentimesOuErreur?.let(MoneyFormatter::formatAmountFromCents) ?: "—",
                        fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Reste prêt à placer", modifier = Modifier.weight(1f))
                    Text(
                        resteCentimesOuErreur?.let(MoneyFormatter::formatAmountFromCents) ?: "—",
                        fontWeight = FontWeight.Bold,
                        color = if (resteCentimes < 0) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }

                if (totalCentimesOuErreur == null || resteCentimesOuErreur == null) {
                    Text("Montants trop élevés pour une répartition valide.", color = MaterialTheme.colorScheme.error)
                }
                if (saisieInvalide) {
                    Text("Entre des montants positifs.",
                        color = MaterialTheme.colorScheme.error)
                }
                erreur?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                if (!apercu) {
                    Button(
                        onClick = { apercu = true },
                        enabled = compte != null && totalCentimesOuErreur != null && resteCentimesOuErreur != null && totalCentimes > 0 && resteCentimes >= 0 && !saisieInvalide && !enCours,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Aperçu") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { apercu = false },
                            enabled = !enCours,
                            modifier = Modifier.weight(1f)
                        ) { Text("Modifier") }
                        Button(
                            onClick = { compteId?.let { onConfirmer(it, montantsValides) } },
                            enabled = !enCours && compte != null && totalCentimesOuErreur != null && resteCentimesOuErreur != null && totalCentimes > 0 && resteCentimes >= 0 && !saisieInvalide,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (enCours) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Confirmer")
                            }
                        }
                    }
                }
            }
        }

        // Clavier de l'app, par-dessus la répartition (même affichage que dans Comptes)
        enSaisie?.let { id ->
            // Toucher à côté du clavier le ferme
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures { enSaisie = null } }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 30.dp, bottom = bas)
            ) {
                ClavierNumerique(
                    montantInitial = saisies[id] ?: 0L,
                    isMoney = true,
                    suffix = "",
                    onMontantChange = { saisies[id] = it },
                    onFermer = { enSaisie = null }
                )
            }
        }
        }
    }
}
