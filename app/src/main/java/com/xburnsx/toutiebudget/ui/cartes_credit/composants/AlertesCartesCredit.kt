package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.services.AlerteCarteCredit
import com.xburnsx.toutiebudget.data.services.Priorite
import com.xburnsx.toutiebudget.data.services.TypeAlerte
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AlertesCartesCredit(
    alertes: List<AlerteCarteCredit>,
    onAlerteClick: (AlerteCarteCredit) -> Unit = {}
) {
    if (alertes.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alertes",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Alertes (${alertes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            alertes.forEach { alerte ->
                AlerteItem(
                    alerte = alerte,
                    onClick = { onAlerteClick(alerte) }
                )
                if (alerte != alertes.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AlerteItem(
    alerte: AlerteCarteCredit,
    onClick: () -> Unit
) {
    val couleur = when (alerte.priorite) {
        Priorite.CRITIQUE -> Color.Red
        Priorite.HAUTE -> Color(0xFFFF9800)
        Priorite.MOYENNE -> Color.Yellow
        Priorite.BASSE -> Color.Green
    }

    val icone = when (alerte.type) {
        TypeAlerte.ECHEANCE_PROCHE -> Icons.Default.Schedule
        TypeAlerte.PAIEMENT_EN_RETARD -> Icons.Default.Error
        TypeAlerte.UTILISATION_ELEVEE -> Icons.Default.TrendingUp
        TypeAlerte.LIMITE_ATTEINTE -> Icons.Default.Warning
        TypeAlerte.INTERETS_APPLIQUES -> Icons.Default.AttachMoney
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C1E)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = couleur,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = alerte.carte.nom,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = alerte.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Voir détails",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
} 

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun AlertesCartesCreditPreview() {
    val carteCredit = CompteCredit(
        id = "1",
        utilisateurId = "user1",
        nom = "Carte Visa",
        soldeUtilise = -2500.0,
        couleur = "#2196F3",
        estArchive = false,
        ordre = 1,
        limiteCredit = 10000.0,
        tauxInteret = 19.99
    )
    
    val alertes = listOf(
        AlerteCarteCredit(
            type = TypeAlerte.ECHEANCE_PROCHE,
            carte = carteCredit,
            message = "Échéance de paiement dans 3 jours",
            priorite = Priorite.HAUTE
        ),
        AlerteCarteCredit(
            type = TypeAlerte.UTILISATION_ELEVEE,
            carte = carteCredit,
            message = "Utilisation élevée: 85%",
            priorite = Priorite.MOYENNE
        )
    )
    
    AlertesCartesCredit(
        alertes = alertes,
        onAlerteClick = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun AlertesCartesCreditEmptyPreview() {
    AlertesCartesCredit(
        alertes = emptyList(),
        onAlerteClick = {}
    )
} 