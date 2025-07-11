// chemin/simule: /ui/composants_communs/ClavierNumerique.kt
package com.xburnsx.toutiebudget.ui.composants_communs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClavierNumerique(onKeyPress: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "del")
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onKeyPress(key)
                        },
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) {
                        if (key == "del") {
                            Icon(Icons.Default.Backspace, contentDescription = "Effacer", modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onBackground)
                        } else {
                            Text(key, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
}
