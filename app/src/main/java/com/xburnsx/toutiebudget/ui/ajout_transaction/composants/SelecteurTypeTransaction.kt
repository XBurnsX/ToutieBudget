// chemin/simule: /ui/ajout_transaction/composants/SelecteurTypeTransaction.kt
// Dépendances: Jetpack Compose, Material3, TypeTransaction.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Bouton Dépense
            BoutonTypeTransaction(
                type = TypeTransaction.Depense,
                icone = Icons.Default.RemoveCircle,
                couleurPrincipale = Color(0xFFEF4444),
                estSelectionne = typeSelectionne == TypeTransaction.Depense,
                onClick = { onTypeChange(TypeTransaction.Depense) },
                modifier = Modifier.weight(1f)
            )
            
            // Bouton Revenu
            BoutonTypeTransaction(
                type = TypeTransaction.Revenu,
                icone = Icons.Default.AddCircle,
                couleurPrincipale = Color(0xFF10B981),
                estSelectionne = typeSelectionne == TypeTransaction.Revenu,
                onClick = { onTypeChange(TypeTransaction.Revenu) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Bouton individuel pour un type de transaction.
 */
@Composable
private fun BoutonTypeTransaction(
    type: TypeTransaction,
    icone: ImageVector,
    couleurPrincipale: Color,
    estSelectionne: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val couleurFond = if (estSelectionne) {
        couleurPrincipale.copy(alpha = 0.15f)
    } else {
        Color(0xFF1F1F1F)
    }
    
    val couleurBordure = if (estSelectionne) {
        couleurPrincipale
    } else {
        Color(0xFF404040)
    }
    
    val couleurTexte = if (estSelectionne) {
        couleurPrincipale
    } else {
        Color.White.copy(alpha = 0.7f)
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = couleurFond),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (estSelectionne) 2.dp else 1.dp,
            color = couleurBordure
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = couleurTexte,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = type.libelleAffiche,
                fontSize = 14.sp,
                fontWeight = if (estSelectionne) FontWeight.SemiBold else FontWeight.Normal,
                color = couleurTexte
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