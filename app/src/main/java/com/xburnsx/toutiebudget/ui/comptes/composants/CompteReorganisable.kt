package com.xburnsx.toutiebudget.ui.comptes.composants

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Compte

/**
 * Composant pour afficher un compte en mode réorganisation avec des flèches pour déplacer.
 */
@SuppressLint("DefaultLocale")
@Composable
fun CompteReorganisable(
    compte: Compte,
    position: Int,
    totalComptes: Int,
    isModeReorganisation: Boolean,
    isEnDeplacement: Boolean,
    onDeplacerCompte: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnDeplacement) Color(0xFF2C2C2E) else Color(0xFF1C1C1E)
        ),
        border = if (isModeReorganisation)
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Informations du compte
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = compte.nom,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                
                Text(
                    text = "Solde: ${String.format("%.2f", compte.solde)} $",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }

            // Contrôles de réorganisation
            if (isModeReorganisation) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Flèche vers le haut
                    IconButton(
                        onClick = {
                            if (position > 0) {
                                onDeplacerCompte(compte.id, position - 1)
                            }
                        },
                        enabled = position > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Monter le compte",
                            tint = if (position > 0) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    // Flèche vers le bas
                    IconButton(
                        onClick = {
                            if (position < totalComptes - 1) {
                                onDeplacerCompte(compte.id, position + 1)
                            }
                        },
                        enabled = position < totalComptes - 1
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Descendre le compte",
                            tint = if (position < totalComptes - 1) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    
                }
            }
        }
    }
} 