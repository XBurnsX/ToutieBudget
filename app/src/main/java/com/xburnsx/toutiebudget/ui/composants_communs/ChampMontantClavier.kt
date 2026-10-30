package com.xburnsx.toutiebudget.ui.composants_communs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Montant saisi avec ChampUniversel + ClavierNumerique, ouverts comme dans ComptesScreen :
 * ChampUniversel avec utiliserClavier = false, et le clavier dans sa propre fenêtre par-dessus
 * (fonctionne aussi depuis un dialogue). ChampUniversel et ClavierNumerique ne sont pas modifiés.
 */
@Composable
fun ChampMontantClavier(
    valeur: Long,
    onValeurChange: (Long) -> Unit,
    libelle: String,
    modifier: Modifier = Modifier
) {
    var showKeyboard by remember { mutableStateOf(false) }

    ChampUniversel(
        valeur = valeur,
        onValeurChange = onValeurChange,
        libelle = libelle,
        utiliserClavier = false,
        isMoney = true,
        onClicPersonnalise = { showKeyboard = true },
        modifier = modifier
    )

    if (showKeyboard) {
        val configuration = LocalConfiguration.current
        // Même marge du bas que le clavier de ComptesScreen
        val paddingBottom = when {
            configuration.screenHeightDp in 800..850 -> 72.dp
            configuration.screenHeightDp > 850 -> 88.dp
            configuration.screenHeightDp < 800 -> 56.dp
            else -> 64.dp
        }
        Dialog(
            onDismissRequest = { showKeyboard = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Zone de fond cliquable pour fermer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures { showKeyboard = false }
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(top = 30.dp, bottom = paddingBottom)
                ) {
                    ClavierNumerique(
                        montantInitial = valeur,
                        isMoney = true,
                        suffix = "",
                        onMontantChange = onValeurChange,
                        onFermer = { showKeyboard = false }
                    )
                }
            }
        }
    }
}
