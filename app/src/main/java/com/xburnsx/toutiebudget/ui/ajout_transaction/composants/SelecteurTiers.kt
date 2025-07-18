// chemin/simule: /ui/ajout_transaction/composants/SelecteurTiers.kt
// Dépendances: Jetpack Compose, Material3, Tiers.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Tiers

/**
 */
@Composable
fun SelecteurTiers(
    tiersDisponibles: List<Tiers>,
    tiersSelectionne: Tiers?,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

            tiersDisponibles
        } else {
            }
        }
    }

    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tiers",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box {
            OutlinedTextField(
                },
                modifier = Modifier
                    .fillMaxWidth()
                    },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1F1F1F),
                ),
                shape = RoundedCornerShape(12.dp),
            )

            DropdownMenu(
            ) {
                    DropdownMenuItem(
                        onClick = {
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                )
                                Text(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                    )

                    if (tiersFiltres.isNotEmpty()) {
                    }
                }

                tiersFiltres.forEach { tiers ->
                    DropdownMenuItem(
                        onClick = {
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                )
                                Text(
                                    text = tiers.nom,
                                    color = Color.White
                                )
                            }
                    )
                }

                // Message si aucun résultat
                    DropdownMenuItem(
                        onClick = { },
                        enabled = false,
                        text = {
                            Text(
                                text = "Aucun tiers trouvé",
                    )
                }
                        )
                    }
                    }
                }
            }
        }
    }