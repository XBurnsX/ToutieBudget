// chemin/simule: /ui/ajout_transaction/composants/SelecteurTypeTransaction.kt
// Dépendances: Jetpack Compose, Material3, TypeTransaction.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction

/**
 * Composant pour sélectionner le type de transaction (Dépense ou Revenu).
 * Affiche deux boutons avec icônes distinctives.
 */
@Composable
fun SelecteurTypeTransaction(
    typeSelectionne: TypeTransaction,
    onTypeChange: (TypeTransaction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Type de transaction",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Bouton Dépense
            BoutonTypeTransactionHarmonise(
                type = TypeTransaction.Depense,
                icone = Icons.Default.RemoveCircle,
                couleurSelection = Color(0xFFEF4444),
                estSelectionne = typeSelectionne == TypeTransaction.Depense,
                onClick = { onTypeChange(TypeTransaction.Depense) },
                modifier = Modifier.weight(1f)
            )
            // Bouton Revenu
            BoutonTypeTransactionHarmonise(
                type = TypeTransaction.Revenu,
                icone = Icons.Default.AddCircle,
                couleurSelection = Color(0xFF10B981),
                estSelectionne = typeSelectionne == TypeTransaction.Revenu,
                onClick = { onTypeChange(TypeTransaction.Revenu) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BoutonTypeTransactionHarmonise(
    type: TypeTransaction,
    icone: ImageVector,
    couleurSelection: Color,
    estSelectionne: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (estSelectionne) couleurSelection else Color(0xFF2A2A2A)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = type.libelleAffiche,
                color = if (estSelectionne) Color.White else Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (estSelectionne) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewSelecteurTypeTransaction() {
    SelecteurTypeTransaction(
        typeSelectionne = TypeTransaction.Depense,
        onTypeChange = { }
    )
}