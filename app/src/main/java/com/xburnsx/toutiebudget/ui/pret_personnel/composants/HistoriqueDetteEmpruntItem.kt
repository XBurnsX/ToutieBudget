/*
 * ================================================================
 *  Fichier        : ui/pret_personnel/composants/HistoriqueDetteEmpruntItem.kt
 *  Projet         : ToutieBudget (Android / Jetpack Compose)
 * ---------------------------------------------------------------
 *  Dépendances    :
 *   - PretPersonnelUiState, PretTab, HistoriqueItem
 *       -> package com.xburnsx.toutiebudget.ui.pret_personnel
 *   - PretPersonnel, TypePretPersonnel
 *       -> package com.xburnsx.toutiebudget.data.modeles
 *   - MoneyFormatter
 *       -> package com.xburnsx.toutiebudget.utils
 *   - Material 3, Compose Foundation/Runtime
 * ---------------------------------------------------------------
 *  Connexions     :
 *   - Utilisé par l'écran Détails/Dialogue d'un prêt personnel
 *   - Consomme PretPersonnelUiState pour connaître le prêt ciblé et son historique
 *   - Émet un onDismiss() vers l'appelant pour fermer le dialogue
 * ---------------------------------------------------------------
 *  Note UX/UI     :
 *   - Palette 100% basée sur MaterialTheme.colorScheme (aucune couleur en dur)
 *   - Entête "hero" avec bandeau dégradé, avatar initiales, résumé clair
 *   - Montants colorés (positif/négatif) + icônes flèches
 *   - Liste à espacement confortable + séparateurs subtils
 *   - Sous-composants : HeaderCard, KeyValueRow, HistoryList, HistoryRow
 * ================================================================
 */

package com.xburnsx.toutiebudget.ui.pret_personnel.composants

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel
import com.xburnsx.toutiebudget.ui.pret_personnel.HistoriqueItem
import com.xburnsx.toutiebudget.ui.pret_personnel.PretPersonnelUiState
import com.xburnsx.toutiebudget.ui.pret_personnel.PretTab
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoriqueDetteEmpruntItem(
	visible: Boolean,
	nomTiers: String,
	uiState: PretPersonnelUiState,
    onDismiss: () -> Unit
) {
	if (!visible) return

	val cs = MaterialTheme.colorScheme

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(
				text = "Détails du prêt",
				style = MaterialTheme.typography.titleLarge,
				color = cs.onSurface
			)
		},
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 4.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				val d = uiState.detailPret
                HeaderCard(
                    nomTiers = nomTiers,
                    montantInitial = d?.montantInitial ?: 0.0,
                    solde = d?.solde ?: 0.0,
                    isPret = d?.type == TypePretPersonnel.PRET
                )

                val isPret = d?.type == TypePretPersonnel.PRET
                Text(
                    text = "Historique — " + if (isPret) "Prêt" else "Emprunt",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )

                when {
                    uiState.isLoadingHistorique -> {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 12.dp),
							horizontalArrangement = Arrangement.Center
						) { CircularProgressIndicator() }
					}
					uiState.historique.isEmpty() -> {
						Text(
							text = "Aucun mouvement",
							style = MaterialTheme.typography.bodyMedium,
							color = cs.onSurfaceVariant
						)
					}
					else -> {
                        HistoryList(items = uiState.historique, isPret = isPret, nomDette = nomTiers)
					}
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
	)
}

// ---------------------------------------------------------------
// Header (bandeau dégradé + avatar + infos clés)
// ---------------------------------------------------------------

@Composable
private fun HeaderCard(
	nomTiers: String,
	montantInitial: Double,
	solde: Double,
	isPret: Boolean,
	modifier: Modifier = Modifier
) {
	val cs = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                Column(modifier = Modifier.weight(1f)) {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						AssistChip(onClick = {}, label = {
							Text(if (isPret) "Prêt" else "Emprunt", style = MaterialTheme.typography.labelLarge)
						})
                        Text(nomTiers, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
					}
					Spacer(Modifier.height(6.dp))
                    KeyValueRow(cle = "Montant initial", valeur = MoneyFormatter.formatAmount(montantInitial))
                    KeyValueRow(cle = "Solde restant", valeur = MoneyFormatter.formatAmount(solde), valeurEmphase = true)
				}
        }
		Divider(color = cs.outline.copy(alpha = 0.25f))
	}
}

@Composable
private fun KeyValueRow(
	cle: String,
	valeur: String,
	valeurEmphase: Boolean = false,
	modifier: Modifier = Modifier
) {
	val cs = MaterialTheme.colorScheme
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 10.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(text = cle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
		Text(
			text = valeur,
			style = if (valeurEmphase) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
			fontWeight = if (valeurEmphase) FontWeight.SemiBold else FontWeight.Normal,
			color = cs.onSurface
		)
	}
}

// ---------------------------------------------------------------
// Liste Historique
// ---------------------------------------------------------------

@Composable
private fun HistoryList(
    items: List<HistoriqueItem>,
    isPret: Boolean,
    nomDette: String = "",
	modifier: Modifier = Modifier
) {
	val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    LazyColumn(
        modifier = modifier.heightIn(max = 300.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp)
	) {
        items(items, key = { it.id }) { h ->
            HistoryRow(
                libelle = h.type,
                date = dateFmt.format(h.date),
                montant = h.montant,
                isPret = isPret,
                nomDette = nomDette
            )
			Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
		}
	}
}

@Composable
private fun HistoryRow(
	libelle: String,
	date: String,
	montant: Double,
    isPret: Boolean,
    nomDette: String = "",
	modifier: Modifier = Modifier
) {
	val cs = MaterialTheme.colorScheme
    // ✅ VOS RÈGLES : Argent qui RENTRE = VERT + flèche BAS, Argent qui SORT = ROUGE + flèche HAUT
    
    // DEBUG: Afficher le type pour diagnostiquer
    println("DEBUG HistoriqueDetteEmpruntItem: libelle='$libelle', isPret=$isPret, montant=$montant")
    
    val inflow = when (libelle) {
        "Prêt accordé" -> false      // Argent qui SORT → ROUGE + flèche vers le HAUT
        "Remboursement reçu" -> true // Argent qui RENTRE → VERT + flèche vers le BAS
        "Dette contractée" -> true   // Argent qui RENTRE → VERT + flèche vers le BAS
        "Remboursement donné" -> false // Argent qui SORT → ROUGE + flèche vers le HAUT
        "Transaction" -> false       // Transaction = Paiement → ROUGE + flèche vers le HAUT
        else -> {
            println("DEBUG: Type non reconnu '$libelle', fallback=false")
            false // Par défaut, considérer comme sortie
        }
    }
    
    println("DEBUG: inflow=$inflow, couleur=${if (inflow) "VERT" else "ROUGE"}")
    
    // ✅ FORCER DES COULEURS VRAIMENT VERTES ET ROUGES
    val amountColor = if (inflow) {
        androidx.compose.ui.graphics.Color(0xFF00FF00) // VERT PUR pour Remboursement reçu et Dette contractée
    } else {
        androidx.compose.ui.graphics.Color(0xFFFF0000) // ROUGE PUR pour Prêt accordé et Remboursement donné
    }

	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 4.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(Modifier.weight(1f)) {
			// Afficher "Paiement de [nom de la dette]" si le libellé est "Transaction"
			val texteAffiche = if (libelle == "Transaction" && nomDette.isNotBlank()) {
				"Paiement de $nomDette"
			} else {
				libelle
			}
			// Le texte est toujours en blanc (cs.onSurface), c'est le montant qui est coloré
			Text(texteAffiche, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
			Text(date, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
		}

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // ✅ VOS RÈGLES : inflow=true = flèche BAS, inflow=false = flèche HAUT
            val icon = if (inflow) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
			Icon(imageVector = icon, contentDescription = null, tint = amountColor)
			Text(
				text = MoneyFormatter.formatAmount(montant),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.SemiBold,
				color = amountColor
			)
		}
	}
}

// ---------------------------------------------------------------
// Aperçu
// ---------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun HistoriqueDetteEmpruntItemPreview() {
	val uiState = PretPersonnelUiState(
		isLoading = false,
		items = emptyList(),
		itemsPret = emptyList(),
		currentTab = PretTab.EMPRUNT,
		isLoadingHistorique = false,
		historique = listOf(
			HistoriqueItem(id = "1", date = Date(), type = "Prêt accordé", montant = 150.0),
			HistoriqueItem(id = "2", date = Date(), type = "Remboursement reçu", montant = -50.0)
		),
		detailPret = PretPersonnel(
			id = "pret1",
			utilisateurId = "user1",
			nomTiers = "Alice",
			montantInitial = 150.0,
			solde = 100.0,
			type = TypePretPersonnel.DETTE,
			estArchive = false
		)
	)

	ToutieBudgetTheme {
		HistoriqueDetteEmpruntItem(
			visible = true,
			nomTiers = "Alice",
            uiState = uiState,
			onDismiss = {}
		)
	}
}
