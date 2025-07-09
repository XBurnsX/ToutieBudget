package com.xburnsx.toutiebudget.ui.ecrans.budget.composants

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.ecrans.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.ecrans.budget.StatutObjectif

@Composable
fun ElementEnveloppe(enveloppeUi: EnveloppeUi) {
    val enveloppe = enveloppeUi.enveloppeOriginale

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Icône enveloppe",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = enveloppe.nom,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${String.format("%.2f", enveloppeUi.etatMensuel.solde)} $",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(enveloppeUi.couleurProvenance)
                    )
                }

                if (enveloppeUi.statutObjectif != StatutObjectif.AUCUN) {
                    Spacer(Modifier.height(8.dp))
                    ContenuObjectif(enveloppeUi)
                }
            }

            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(if (enveloppeUi.statutObjectif != StatutObjectif.AUCUN) 80.dp else 50.dp)
                    .background(enveloppeUi.couleurStatutLateral)
            )
        }
    }
}

@Composable
private fun ContenuObjectif(enveloppeUi: EnveloppeUi) {
    val couleurBarre = when (enveloppeUi.statutObjectif) {
        StatutObjectif.DEPENSE -> enveloppeUi.couleurProvenance
        else -> Color(0xFF4CAF50)
    }

    Column {
        Text(
            text = enveloppeUi.texteObjectif,
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { enveloppeUi.pourcentage },
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(CircleShape),
                color = couleurBarre,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
            Spacer(Modifier.width(8.dp))

            AnimatedContent(
                targetState = enveloppeUi.statutObjectif,
                transitionSpec = { fadeIn(animationSpec = tween(220, 90)) togetherWith fadeOut(animationSpec = tween(90)) },
                label = "StatutObjectifAnimation"
            ) { statut ->
                if (statut == StatutObjectif.DEPENSE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Dépensé",
                            fontSize = 12.sp,
                            color = enveloppeUi.couleurProvenance,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Dépensé",
                            tint = enveloppeUi.couleurProvenance,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    val couleurTexte = if (enveloppeUi.statutObjectif == StatutObjectif.ATTEINT) Color(0xFF4CAF50) else Color.Gray
                    Text(
                        text = "${(enveloppeUi.pourcentage * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = couleurTexte
                    )
                }
            }
        }
    }
}