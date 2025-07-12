// chemin/simule: /ui/categories/composants/DebugInfoComposant.kt
// Dépendances: Jetpack Compose, Modèles

package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

/**
 * Composant d'aide au debug pour vérifier les liens entre enveloppes et catégories.
 * À utiliser temporairement pendant le développement.
 */
@Composable
fun DebugInfoComposant(
    enveloppes: List<Enveloppe>,
    categories: Map<String, String> = emptyMap() // nom -> id
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔧 Debug - Liens Catégories/Enveloppes",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(
                        text = if (isExpanded) "▼" else "▶",
                        color = Color.Yellow
                    )
                }
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Informations sur les catégories
                Text(
                    text = "📁 Catégories (${categories.size}):",
                    color = Color.Cyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                categories.forEach { (nom, id) ->
                    Text(
                        text = "  • $nom → $id",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Informations sur les enveloppes
                Text(
                    text = "📄 Enveloppes (${enveloppes.size}):",
                    color = Color.Cyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                enveloppes.forEach { enveloppe ->
                    val categorieNom = categories.entries.find { it.value == enveloppe.categorieId }?.key ?: "❌ INCONNUE"
                    val couleurLien = if (categorieNom == "❌ INCONNUE") Color.Red else Color.Green
                    
                    Column(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                        Text(
                            text = "  • ${enveloppe.nom}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "    ID: ${enveloppe.id}",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "    CategorieID: ${enveloppe.categorieId}",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "    Catégorie: $categorieNom",
                            color = couleurLien,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Statistiques rapides
                Spacer(modifier = Modifier.height(8.dp))
                val enveloppesOrphelines = enveloppes.count { enveloppe ->
                    !categories.values.contains(enveloppe.categorieId)
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (enveloppesOrphelines > 0) Color.Red.copy(alpha = 0.2f) else Color.Green.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (enveloppesOrphelines > 0) {
                            "⚠️ $enveloppesOrphelines enveloppe(s) orpheline(s) détectée(s)"
                        } else {
                            "✅ Toutes les enveloppes sont correctement liées"
                        },
                        color = if (enveloppesOrphelines > 0) Color.Red else Color.Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}