// chemin/simule: /ui/comptes/ComptesScreen.kt
package com.xburnsx.toutiebudget.ui.comptes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xburnsx.toutiebudget.ui.comptes.composants.CompteItem
import com.xburnsx.toutiebudget.ui.comptes.dialogs.AjoutCompteDialog
import com.xburnsx.toutiebudget.ui.comptes.dialogs.ModifierCompteDialog
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ComptesScreen(
    viewModel: ComptesViewModel,
    onCompteClick: (String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // États pour le clavier numérique
    var showKeyboard by remember { mutableStateOf(false) }
    var montantClavierInitial by remember { mutableStateOf(0L) }
    var nomDialogClavier by remember { mutableStateOf("") }
    var onMontantChangeCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { Text("Comptes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212), titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = { viewModel.onOuvrirAjoutDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter un compte", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            uiState.comptesGroupes.forEach { (typeDeCompte, listeDeComptes) ->
                stickyHeader {
                    Text(
                        text = typeDeCompte,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF121212)).padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(listeDeComptes, key = { it.id }) { compte ->
                    CompteItem(
                        compte = compte,
                        onClick = {
                            // Corriger la valeur de collection pour correspondre à ce qui est stocké dans PocketBase
                            val collectionCompte = when (compte.javaClass.simpleName) {
                                "CompteCheque" -> "comptes_cheques"
                                "CompteCredit" -> "comptes_credits"
                                "CompteDette" -> "comptes_dettes"
                                "CompteInvestissement" -> "comptes_investissements"
                                else -> "comptes_cheques" // Fallback par défaut
                            }

                            // Vérifications de sécurité pour éviter les paramètres null
                            val compteId = compte.id ?: ""
                            val nomCompte = compte.nom ?: "Compte sans nom"

                            println("DEBUG CLICK: compteId=$compteId, collectionCompte=$collectionCompte, nomCompte=$nomCompte")

                            if (compteId.isNotEmpty() && collectionCompte.isNotEmpty()) {
                                onCompteClick(compteId, collectionCompte, nomCompte)
                            } else {
                                println("DEBUG CLICK: Paramètres invalides - compteId ou collectionCompte vide")
                            }
                        },
                        onLongClick = { viewModel.onCompteLongPress(compte) }
                    )
                }
            }
        }
    }

    // Dialogues
    if (uiState.isAjoutDialogVisible) {
        AjoutCompteDialog(
            formState = uiState.formState,
            onDismissRequest = { viewModel.onFermerTousLesDialogues() },
            onValueChange = viewModel::onFormValueChange,
            onSave = { viewModel.onSauvegarderCompte() },
            onOpenKeyboard = { montantActuel, onMontantChange ->
                montantClavierInitial = montantActuel
                nomDialogClavier = "Solde initial"
                onMontantChangeCallback = onMontantChange
                showKeyboard = true
            }
        )
    }

    if (uiState.isModificationDialogVisible) {
        ModifierCompteDialog(
            formState = uiState.formState,
            onDismissRequest = { viewModel.onFermerTousLesDialogues() },
            onValueChange = viewModel::onFormValueChange,
            onSave = { viewModel.onSauvegarderCompte() },
            onOpenKeyboard = { montantActuel, onMontantChange ->
                montantClavierInitial = montantActuel
                nomDialogClavier = "Solde actuel"
                onMontantChangeCallback = onMontantChange
                showKeyboard = true
            }
        )
    }

    // Clavier numérique par-dessus tout - utiliser Dialog pour garantir le z-index maximal
    if (showKeyboard) {
        Dialog(
            onDismissRequest = {
                showKeyboard = false
                onMontantChangeCallback = null
            }
        ) {
            // Le Dialog garantit que le clavier sera au-dessus de tout
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                ClavierNumerique(
                    montantInitial = montantClavierInitial,
                    isMoney = true,
                    suffix = "",
                    onMontantChange = { nouveauMontant ->
                        onMontantChangeCallback?.invoke(nouveauMontant)
                    },
                    onFermer = {
                        showKeyboard = false
                        onMontantChangeCallback = null
                    }
                )
            }
        }
    }
}
