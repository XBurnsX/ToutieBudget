package com.xburnsx.toutiebudget.ui.ecrans.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xburnsx.toutiebudget.ui.ecrans.budget.composants.ElementCompte
import com.xburnsx.toutiebudget.ui.ecrans.budget.composants.ElementEnveloppe

@Composable
fun EcranBudget(
    viewModel: ViewModelBudget = hiltViewModel()
) {
    val etat by viewModel.etat.collectAsStateWithLifecycle()

    if (etat.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text("Comptes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(etat.comptes) { compte ->
                ElementCompte(compte = compte)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Enveloppes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(etat.enveloppes) { enveloppeUi ->
                ElementEnveloppe(enveloppeUi = enveloppeUi)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}