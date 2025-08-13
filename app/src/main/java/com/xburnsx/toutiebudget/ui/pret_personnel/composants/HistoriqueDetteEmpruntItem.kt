package com.xburnsx.toutiebudget.ui.pret_personnel.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel
import com.xburnsx.toutiebudget.ui.pret_personnel.HistoriqueItem
import com.xburnsx.toutiebudget.ui.pret_personnel.PretPersonnelUiState
import com.xburnsx.toutiebudget.ui.pret_personnel.PretTab
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.Date

@Composable
fun HistoriqueDetteEmpruntItem(
	visible: Boolean,
	nomTiers: String,
	uiState: PretPersonnelUiState,
	onDismiss: () -> Unit
) {
	if (!visible) return

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Détails du prêt") },
		text = {
			Column(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				val d = uiState.detailPret
				androidx.compose.material3.Card(
					colors = androidx.compose.material3.CardDefaults.cardColors(
						containerColor = Color(0xFF232323)
					)
				) {
					Column(Modifier.padding(12.dp)) {
						Text(nomTiers, color = Color.White, fontWeight = FontWeight.Bold)
						Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
							Text("Montant initial", color = Color.LightGray)
							Text(MoneyFormatter.formatAmount(d?.montantInitial ?: 0.0), color = Color.White)
						}
						Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
							Text("Solde restant", color = Color.LightGray)
							Text(MoneyFormatter.formatAmount(d?.solde ?: 0.0), color = Color.White)
						}
						Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
							Text("Type", color = Color.LightGray)
							Text(if ((d?.solde ?: 0.0) >= 0) "Prêt" else "Emprunt", color = Color.White)
						}
					}
				}

				Text("Historique", color = Color.White, fontWeight = FontWeight.SemiBold)
				when {
					uiState.isLoadingHistorique -> {
						Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
							CircularProgressIndicator()
						}
					}
					uiState.historique.isEmpty() -> {
						Text("Aucun mouvement", color = Color.LightGray)
					}
					else -> {
						LazyColumn(modifier = Modifier.height(220.dp)) {
							items(uiState.historique) { h ->
								Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
									Column {
										Text(h.type, color = Color.White)
										Text(
											java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(h.date),
											color = Color.LightGray
										)
									}
									Text(
										MoneyFormatter.formatAmount(h.montant),
										color = Color.White,
										fontWeight = FontWeight.SemiBold
									)
								}
								Spacer(Modifier.height(8.dp))
							}
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) { Text("Fermer") }
		}
	)
}

@Preview(showBackground = true)
@Composable
private fun HistoriqueDetteEmpruntItemPreview() {
	val uiState = PretPersonnelUiState(
		isLoading = false,
		items = emptyList(),
		itemsPret = emptyList(),
		currentTab = PretTab.PRET,
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
			type = TypePretPersonnel.PRET,
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


