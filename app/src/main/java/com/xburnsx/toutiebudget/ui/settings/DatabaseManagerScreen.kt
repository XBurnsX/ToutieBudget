package com.xburnsx.toutiebudget.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.entities.*
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseManagerScreen(
    onBack: () -> Unit,
    couleurTheme: CouleurTheme = CouleurTheme.PINK
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Comptes", "Transactions", "Allocations", "Catégories", "Enveloppes", "Tiers", "Prêts")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestion Base de Données", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F0F))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }
            
            when (selectedTabIndex) {
                0 -> ComptesTab(couleurTheme = couleurTheme)
                1 -> TransactionsTab(couleurTheme = couleurTheme)
                2 -> AllocationsTab(couleurTheme = couleurTheme)
                3 -> CategoriesTab(couleurTheme = couleurTheme)
                4 -> EnveloppesTab(couleurTheme = couleurTheme)
                5 -> TiersTab(couleurTheme = couleurTheme)
                6 -> PretsTab(couleurTheme = couleurTheme)
            }
        }
    }
}

// ==================== ONGLET COMPTES ====================
@Composable
fun ComptesTab(couleurTheme: CouleurTheme) {
    var comptesCheque by remember { mutableStateOf<List<CompteCheque>>(emptyList()) }
    var comptesCredit by remember { mutableStateOf<List<CompteCredit>>(emptyList()) }
    var comptesDette by remember { mutableStateOf<List<CompteDette>>(emptyList()) }
    var comptesInvestissement by remember { mutableStateOf<List<CompteInvestissement>>(emptyList()) }
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf<String?>(null) }
    var editingItem by remember { mutableStateOf<Any?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val database = ToutieBudgetDatabase.getDatabase(context)
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                if (utilisateurId.isNotEmpty()) {
                    comptesCheque = database.compteChequeDao().getComptesByUtilisateur(utilisateurId).first()
                    comptesCredit = database.compteCreditDao().getComptesByUtilisateur(utilisateurId).first()
                    comptesDette = database.compteDetteDao().getComptesByUtilisateur(utilisateurId).first()
                    comptesInvestissement = database.compteInvestissementDao().getComptesByUtilisateur(utilisateurId).first()
                }
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Gestion des Comptes", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Comptes Chèques
        item {
            CompteSection(
                title = "Comptes Chèques (${comptesCheque.size})",
                isExpanded = expandedSection == "cheque",
                onToggle = { expandedSection = if (expandedSection == "cheque") null else "cheque" },
                onAdd = { showAddDialog = "cheque" },
                couleurTheme = couleurTheme
            ) {
                comptesCheque.forEach { compte ->
                    CompteChequeItem(
                        compte = compte,
                        onEdit = { editingItem = compte },
                        onDelete = { 
                            scope.launch {
                                try {
                                    val database = ToutieBudgetDatabase.getDatabase(context)
                                    database.compteChequeDao().deleteCompteById(compte.id)
                                    comptesCheque = database.compteChequeDao().getComptesByUtilisateur(
                                        PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                                    ).first()
                                } catch (e: Exception) {
                                    // Gérer l'erreur
                                }
                            }
                        },
                        couleurTheme = couleurTheme
                    )
                }
            }
        }
        
        // Comptes Crédit
        item {
            CompteSection(
                title = "Comptes Crédit (${comptesCredit.size})",
                isExpanded = expandedSection == "credit",
                onToggle = { expandedSection = if (expandedSection == "credit") null else "credit" },
                onAdd = { showAddDialog = "credit" },
                couleurTheme = couleurTheme
            ) {
                comptesCredit.forEach { compte ->
                    CompteCreditItem(
                        compte = compte,
                        onEdit = { editingItem = compte },
                        onDelete = { 
                            scope.launch {
                                try {
                                    val database = ToutieBudgetDatabase.getDatabase(context)
                                    database.compteCreditDao().deleteCompteById(compte.id)
                                    comptesCredit = database.compteCreditDao().getComptesByUtilisateur(
                                        PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                                    ).first()
                                } catch (e: Exception) {
                                    // Gérer l'erreur
                                }
                            }
                        },
                        couleurTheme = couleurTheme
                    )
                }
            }
        }
        
        // Comptes Dette
        item {
            CompteSection(
                title = "Comptes Dette (${comptesDette.size})",
                isExpanded = expandedSection == "dette",
                onToggle = { expandedSection = if (expandedSection == "dette") null else "dette" },
                onAdd = { showAddDialog = "dette" },
                couleurTheme = couleurTheme
            ) {
                comptesDette.forEach { compte ->
                    CompteDetteItem(
                        compte = compte,
                        onEdit = { editingItem = compte },
                        onDelete = { 
                            scope.launch {
                                try {
                                    val database = ToutieBudgetDatabase.getDatabase(context)
                                    database.compteDetteDao().deleteCompteById(compte.id)
                                    comptesDette = database.compteDetteDao().getComptesByUtilisateur(
                                        PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                                    ).first()
                                } catch (e: Exception) {
                                    // Gérer l'erreur
                                }
                            }
                        },
                        couleurTheme = couleurTheme
                    )
                }
            }
        }
        
        // Comptes Investissement
        item {
            CompteSection(
                title = "Comptes Investissement (${comptesInvestissement.size})",
                isExpanded = expandedSection == "investissement",
                onToggle = { expandedSection = if (expandedSection == "investissement") null else "investissement" },
                onAdd = { showAddDialog = "investissement" },
                couleurTheme = couleurTheme
            ) {
                comptesInvestissement.forEach { compte ->
                    CompteInvestissementItem(
                        compte = compte,
                        onEdit = { editingItem = compte },
                        onDelete = { 
                            scope.launch {
                                try {
                                    val database = ToutieBudgetDatabase.getDatabase(context)
                                    database.compteInvestissementDao().deleteCompteById(compte.id)
                                    comptesInvestissement = database.compteInvestissementDao().getComptesByUtilisateur(
                                        PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                                    ).first()
                                } catch (e: Exception) {
                                    // Gérer l'erreur
                                }
                            }
                        },
                        couleurTheme = couleurTheme
                    )
                }
            }
        }
    }
    
    // Dialogs d'ajout/édition
    when {
        showAddDialog == "cheque" -> AddEditCompteChequeDialog(
            compte = null,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val compteAvecUtilisateur = compte.copy(utilisateurId = utilisateurId)
                        database.compteChequeDao().insertCompte(compteAvecUtilisateur)
                        comptesCheque = database.compteChequeDao().getComptesByUtilisateur(utilisateurId).first()
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
                showAddDialog = null
            },
            onDismiss = { showAddDialog = null }
        )
        
        // 🆕 DIALOGS DE MODIFICATION DES COMPTES
        editingItem is CompteCheque -> AddEditCompteChequeDialog(
            compte = editingItem as CompteCheque,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        database.compteChequeDao().updateCompte(compte)
                        comptesCheque = database.compteChequeDao().getComptesByUtilisateur(
                            PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        ).first()
                        editingItem = null
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { editingItem = null }
        )
        showAddDialog == "credit" -> AddEditCompteCreditDialog(
            compte = null,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val compteAvecUtilisateur = compte.copy(utilisateurId = utilisateurId)
                        database.compteCreditDao().insertCompte(compteAvecUtilisateur)
                        comptesCredit = database.compteCreditDao().getComptesByUtilisateur(utilisateurId).first()
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
                showAddDialog = null
            },
            onDismiss = { showAddDialog = null }
        )
        showAddDialog == "dette" -> AddEditCompteDetteDialog(
            compte = null,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val compteAvecUtilisateur = compte.copy(utilisateurId = utilisateurId)
                        database.compteDetteDao().insertCompte(compteAvecUtilisateur)
                        comptesDette = database.compteDetteDao().getComptesByUtilisateur(utilisateurId).first()
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
                showAddDialog = null
            },
            onDismiss = { showAddDialog = null }
        )
        showAddDialog == "investissement" -> AddEditCompteInvestissementDialog(
            compte = null,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val compteAvecUtilisateur = compte.copy(utilisateurId = utilisateurId)
                        database.compteInvestissementDao().insertCompte(compteAvecUtilisateur)
                        comptesInvestissement = database.compteInvestissementDao().getComptesByUtilisateur(utilisateurId).first()
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
                showAddDialog = null
            },
            onDismiss = { showAddDialog = null }
        )
    }
    
    // Dialogs d'édition
    when (editingItem) {
        is CompteCheque -> AddEditCompteChequeDialog(
            compte = editingItem as CompteCheque,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val compteAvecUtilisateur = compte.copy(utilisateurId = utilisateurId)
                        database.compteChequeDao().updateCompte(compteAvecUtilisateur)
                        comptesCheque = database.compteChequeDao().getComptesByUtilisateur(utilisateurId).first()
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
        is CompteCredit -> AddEditCompteCreditDialog(
            compte = editingItem as CompteCredit,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val compteAvecUtilisateur = compte.copy(utilisateurId = utilisateurId)
                        database.compteCreditDao().updateCompte(compteAvecUtilisateur)
                        comptesCredit = database.compteCreditDao().getComptesByUtilisateur(utilisateurId).first()
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
        is CompteDette -> AddEditCompteDetteDialog(
            compte = editingItem as CompteDette,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val compteAvecUtilisateur = compte.copy(utilisateurId = utilisateurId)
                        database.compteDetteDao().updateCompte(compteAvecUtilisateur)
                        comptesDette = database.compteDetteDao().getComptesByUtilisateur(utilisateurId).first()
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
        is CompteInvestissement -> AddEditCompteInvestissementDialog(
            compte = editingItem as CompteInvestissement,
            onSave = { compte ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val compteAvecUtilisateur = compte.copy(utilisateurId = utilisateurId)
                        database.compteInvestissementDao().updateCompte(compteAvecUtilisateur)
                        comptesInvestissement = database.compteInvestissementDao().getComptesByUtilisateur(utilisateurId).first()
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }
}

@Composable
fun CompteSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    couleurTheme: CouleurTheme,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                Row {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, "Ajouter", tint = couleurTheme.couleur)
                    }
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Toggle",
                        tint = Color.White
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun CompteChequeItem(compte: CompteCheque, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(compte.nom, color = Color.White, fontWeight = FontWeight.Medium)
                Text("Solde: ${compte.solde}€ | Couleur: ${compte.couleur}", color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun CompteCreditItem(compte: CompteCredit, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(compte.nom, color = Color.White, fontWeight = FontWeight.Medium)
                Text("Limite: ${compte.limiteCredit}€ | Solde utilisé: ${compte.soldeUtilise}€ | Taux: ${compte.tauxInteret}%", 
                     color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun CompteDetteItem(compte: CompteDette, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(compte.nom, color = Color.White, fontWeight = FontWeight.Medium)
                Text("Solde dette: ${compte.soldeDette}€ | Montant initial: ${compte.montantInitial}€ | Taux: ${compte.tauxInteret}%", 
                     color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun CompteInvestissementItem(compte: CompteInvestissement, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(compte.nom, color = Color.White, fontWeight = FontWeight.Medium)
                Text("Solde: ${compte.solde}€", color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

// ==================== ONGLET TRANSACTIONS ====================
@Composable
fun TransactionsTab(couleurTheme: CouleurTheme) {
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val database = ToutieBudgetDatabase.getDatabase(context)
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                if (utilisateurId.isNotEmpty()) {
                    transactions = database.transactionDao().getTransactionsByUtilisateur(utilisateurId).first()
                }
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gestion des Transactions", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.background(couleurTheme.couleur, CircleShape)
                ) {
                    Icon(Icons.Default.Add, "Ajouter", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(transactions) { transaction ->
            TransactionItem(
                transaction = transaction,
                onEdit = { editingTransaction = transaction },
                onDelete = {
                    scope.launch {
                        try {
                            val database = ToutieBudgetDatabase.getDatabase(context)
                            database.transactionDao().deleteTransactionById(transaction.id)
                            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                            transactions = database.transactionDao().getTransactionsByUtilisateur(utilisateurId).first()
                        } catch (e: Exception) {
                            // Gérer l'erreur
                        }
                    }
                },
                couleurTheme = couleurTheme
            )
        }
    }
    
    // Dialog d'ajout
    if (showAddDialog) {
        AddEditTransactionDialog(
            transaction = null,
            onSave = { transaction ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val transactionAvecUtilisateur = transaction.copy(utilisateurId = utilisateurId)
                        database.transactionDao().insertTransaction(transactionAvecUtilisateur)
                        transactions = database.transactionDao().getTransactionsByUtilisateur(utilisateurId).first()
                        showAddDialog = false
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // Dialog d'édition
    editingTransaction?.let { transaction ->
        AddEditTransactionDialog(
            transaction = transaction,
            onSave = { updatedTransaction ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val transactionAvecUtilisateur = updatedTransaction.copy(utilisateurId = utilisateurId)
                        database.transactionDao().updateTransaction(transactionAvecUtilisateur)
                        transactions = database.transactionDao().getTransactionsByUtilisateur(utilisateurId).first()
                        editingTransaction = null
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { editingTransaction = null }
        )
    }
}

@Composable
fun TransactionItem(transaction: Transaction, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.tiersUtiliser ?: "Sans tiers", color = Color.White, fontWeight = FontWeight.Medium)
                Text("Montant: ${transaction.montant}€ | Date: ${transaction.date}", color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

// ==================== ONGLET ALLOCATIONS ====================
@Composable
fun AllocationsTab(couleurTheme: CouleurTheme) {
    var allocations by remember { mutableStateOf<List<AllocationMensuelle>>(emptyList()) }
    var enveloppes by remember { mutableStateOf<List<Enveloppe>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAllocation by remember { mutableStateOf<AllocationMensuelle?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val database = ToutieBudgetDatabase.getDatabase(context)
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                if (utilisateurId.isNotEmpty()) {
                    allocations = database.allocationMensuelleDao().getAllocationsByUtilisateur(utilisateurId).first()
                    enveloppes = database.enveloppeDao().getEnveloppesByUtilisateur(utilisateurId).first()
                }
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gestion des Allocations Mensuelles", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.background(couleurTheme.couleur, CircleShape)
                ) {
                    Icon(Icons.Default.Add, "Ajouter", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(allocations) { allocation ->
            AllocationItem(
                allocation = allocation,
                onEdit = { editingAllocation = allocation },
                onDelete = {
                    scope.launch {
                        try {
                            val database = ToutieBudgetDatabase.getDatabase(context)
                            database.allocationMensuelleDao().deleteAllocationById(allocation.id)
                            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                            allocations = database.allocationMensuelleDao().getAllocationsByUtilisateur(utilisateurId).first()
                        } catch (e: Exception) {
                            // Gérer l'erreur
                        }
                    }
                },
                couleurTheme = couleurTheme,
                enveloppes = enveloppes
            )
        }
    }
    
    // Dialog d'ajout
    if (showAddDialog) {
        AddEditAllocationDialog(
            allocation = null,
            onSave = { allocation ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val allocationAvecUtilisateur = allocation.copy(utilisateurId = utilisateurId)
                        database.allocationMensuelleDao().insertAllocation(allocationAvecUtilisateur)
                        allocations = database.allocationMensuelleDao().getAllocationsByUtilisateur(utilisateurId).first()
                        showAddDialog = false
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // Dialog d'édition
    editingAllocation?.let { allocation ->
        AddEditAllocationDialog(
            allocation = allocation,
            onSave = { updatedAllocation ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val allocationAvecUtilisateur = updatedAllocation.copy(utilisateurId = utilisateurId)
                        database.allocationMensuelleDao().updateAllocation(allocationAvecUtilisateur)
                        allocations = database.allocationMensuelleDao().getAllocationsByUtilisateur(utilisateurId).first()
                        editingAllocation = null
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { editingAllocation = null }
        )
    }
}

@Composable
fun AllocationItem(allocation: AllocationMensuelle, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme, enveloppes: List<Enveloppe>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Allocation ${allocation.mois}", color = Color.White, fontWeight = FontWeight.Medium)
                Text("Enveloppe: ${enveloppes.find { it.id == allocation.enveloppeId }?.nom ?: "Inconnue"}", color = Color.Gray, fontSize = 12.sp)
                Text("Solde: ${allocation.solde}€ | Alloué: ${allocation.alloue}€ | Dépense: ${allocation.depense}€", color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

// ==================== ONGLET CATÉGORIES ====================
@Composable
fun CategoriesTab(couleurTheme: CouleurTheme) {
    var categories by remember { mutableStateOf<List<Categorie>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategorie by remember { mutableStateOf<Categorie?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val database = ToutieBudgetDatabase.getDatabase(context)
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                if (utilisateurId.isNotEmpty()) {
                    categories = database.categorieDao().getCategoriesByUtilisateur(utilisateurId).first()
                }
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gestion des Catégories", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.background(couleurTheme.couleur, CircleShape)
                ) {
                    Icon(Icons.Default.Add, "Ajouter", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(categories) { categorie ->
            CategorieItem(
                categorie = categorie,
                onEdit = { editingCategorie = categorie },
                onDelete = {
                    scope.launch {
                        try {
                            val database = ToutieBudgetDatabase.getDatabase(context)
                            database.categorieDao().deleteCategorieById(categorie.id)
                            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                            categories = database.categorieDao().getCategoriesByUtilisateur(utilisateurId).first()
                        } catch (e: Exception) {
                            // Gérer l'erreur
                        }
                    }
                },
                couleurTheme = couleurTheme
            )
        }
    }
    
    // Dialog d'ajout
    if (showAddDialog) {
        AddEditCategorieDialog(
            categorie = null,
            onSave = { categorie ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val categorieAvecUtilisateur = categorie.copy(utilisateurId = utilisateurId)
                        database.categorieDao().insertCategorie(categorieAvecUtilisateur)
                        categories = database.categorieDao().getCategoriesByUtilisateur(utilisateurId).first()
                        showAddDialog = false
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // Dialog d'édition
    editingCategorie?.let { categorie ->
        AddEditCategorieDialog(
            categorie = categorie,
            onSave = { updatedCategorie ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val categorieAvecUtilisateur = updatedCategorie.copy(utilisateurId = utilisateurId)
                        database.categorieDao().updateCategorie(categorieAvecUtilisateur)
                        categories = database.categorieDao().getCategoriesByUtilisateur(utilisateurId).first()
                        editingCategorie = null
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { editingCategorie = null }
        )
    }
}

@Composable
fun CategorieItem(categorie: Categorie, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(categorie.nom, color = Color.White, fontWeight = FontWeight.Medium)
                Text("Ordre: ${categorie.ordre}", color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

// ==================== ONGLET ENVELOPPES ====================
@Composable
fun EnveloppesTab(couleurTheme: CouleurTheme) {
    var enveloppes by remember { mutableStateOf<List<Enveloppe>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEnveloppe by remember { mutableStateOf<Enveloppe?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val database = ToutieBudgetDatabase.getDatabase(context)
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                if (utilisateurId.isNotEmpty()) {
                    enveloppes = database.enveloppeDao().getEnveloppesByUtilisateur(utilisateurId).first()
                }
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gestion des Enveloppes", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.background(couleurTheme.couleur, CircleShape)
                ) {
                    Icon(Icons.Default.Add, "Ajouter", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(enveloppes) { enveloppe ->
            EnveloppeItem(
                enveloppe = enveloppe,
                onEdit = { editingEnveloppe = enveloppe },
                onDelete = {
                    scope.launch {
                        try {
                            val database = ToutieBudgetDatabase.getDatabase(context)
                            database.enveloppeDao().deleteEnveloppeById(enveloppe.id)
                            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                            enveloppes = database.enveloppeDao().getEnveloppesByUtilisateur(utilisateurId).first()
                        } catch (e: Exception) {
                            // Gérer l'erreur
                        }
                    }
                },
                couleurTheme = couleurTheme
            )
        }
    }
    
    // Dialog d'ajout
    if (showAddDialog) {
        AddEditEnveloppeDialog(
            enveloppe = null,
            onSave = { enveloppe ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val enveloppeAvecUtilisateur = enveloppe.copy(utilisateurId = utilisateurId)
                        database.enveloppeDao().insertEnveloppe(enveloppeAvecUtilisateur)
                        enveloppes = database.enveloppeDao().getEnveloppesByUtilisateur(utilisateurId).first()
                        showAddDialog = false
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // Dialog d'édition
    editingEnveloppe?.let { enveloppe ->
        AddEditEnveloppeDialog(
            enveloppe = enveloppe,
            onSave = { updatedEnveloppe ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val enveloppeAvecUtilisateur = updatedEnveloppe.copy(utilisateurId = utilisateurId)
                        database.enveloppeDao().updateEnveloppe(enveloppeAvecUtilisateur)
                        enveloppes = database.enveloppeDao().getEnveloppesByUtilisateur(utilisateurId).first()
                        editingEnveloppe = null
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { editingEnveloppe = null }
        )
    }
}

@Composable
fun EnveloppeItem(enveloppe: Enveloppe, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(enveloppe.nom, color = Color.White, fontWeight = FontWeight.Medium)
                Text("Catégorie: ${enveloppe.categorieId} | Objectif: ${enveloppe.objectifMontant}€", color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

// ==================== ONGLET TIERS ====================
@Composable
fun TiersTab(couleurTheme: CouleurTheme) {
    var tiers by remember { mutableStateOf<List<Tiers>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTiers by remember { mutableStateOf<Tiers?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val database = ToutieBudgetDatabase.getDatabase(context)
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                if (utilisateurId.isNotEmpty()) {
                    tiers = database.tiersDao().getTiersByUtilisateur(utilisateurId).first()
                }
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gestion des Tiers", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.background(couleurTheme.couleur, CircleShape)
                ) {
                    Icon(Icons.Default.Add, "Ajouter", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(tiers) { tier ->
            TiersItem(
                tiers = tier,
                onEdit = { editingTiers = tier },
                onDelete = {
                    scope.launch {
                        try {
                            val database = ToutieBudgetDatabase.getDatabase(context)
                            database.tiersDao().deleteTiersById(tier.id)
                            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                            tiers = database.tiersDao().getTiersByUtilisateur(utilisateurId).first()
                        } catch (e: Exception) {
                            // Gérer l'erreur
                        }
                    }
                },
                couleurTheme = couleurTheme
            )
        }
    }
    
    // Dialog d'ajout
    if (showAddDialog) {
        AddEditTiersDialog(
            tiers = null,
            onSave = { newTiers ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val tiersAvecUtilisateur = newTiers.copy(utilisateurId = utilisateurId)
                        database.tiersDao().insertTiers(tiersAvecUtilisateur)
                        tiers = database.tiersDao().getTiersByUtilisateur(utilisateurId).first()
                        showAddDialog = false
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // Dialog d'édition
    editingTiers?.let { tier ->
        AddEditTiersDialog(
            tiers = tier,
            onSave = { updatedTiers ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val tiersAvecUtilisateur = updatedTiers.copy(utilisateurId = utilisateurId)
                        database.tiersDao().updateTiers(tiersAvecUtilisateur)
                        tiers = database.tiersDao().getTiersByUtilisateur(utilisateurId).first()
                        editingTiers = null
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { editingTiers = null }
        )
    }
}

@Composable
fun TiersItem(tiers: Tiers, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tiers.nom, color = Color.White, fontWeight = FontWeight.Medium)
                Text("Collection: ${tiers.collectionName}", color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

// ==================== ONGLET PRÊTS ====================
@Composable
fun PretsTab(couleurTheme: CouleurTheme) {
    var prets by remember { mutableStateOf<List<PretPersonnel>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPret by remember { mutableStateOf<PretPersonnel?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val database = ToutieBudgetDatabase.getDatabase(context)
                val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                if (utilisateurId.isNotEmpty()) {
                    prets = database.pretPersonnelDao().getPretsByUtilisateur(utilisateurId).first()
                }
            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gestion des Prêts Personnels", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.background(couleurTheme.couleur, CircleShape)
                ) {
                    Icon(Icons.Default.Add, "Ajouter", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        items(prets) { pret ->
            PretItem(
                pret = pret,
                onEdit = { editingPret = pret },
                onDelete = {
                    scope.launch {
                        try {
                            val database = ToutieBudgetDatabase.getDatabase(context)
                            database.pretPersonnelDao().deletePretById(pret.id)
                            val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                            prets = database.pretPersonnelDao().getPretsByUtilisateur(utilisateurId).first()
                        } catch (e: Exception) {
                            // Gérer l'erreur
                        }
                    }
                },
                couleurTheme = couleurTheme
            )
        }
    }
    
    // Dialog d'ajout
    if (showAddDialog) {
        AddEditPretDialog(
            pret = null,
            onSave = { pret ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val pretAvecUtilisateur = pret.copy(utilisateurId = utilisateurId)
                        database.pretPersonnelDao().insertPret(pretAvecUtilisateur)
                        prets = database.pretPersonnelDao().getPretsByUtilisateur(utilisateurId).first()
                        showAddDialog = false
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // Dialog d'édition
    editingPret?.let { pret ->
        AddEditPretDialog(
            pret = pret,
            onSave = { updatedPret ->
                scope.launch {
                    try {
                        val database = ToutieBudgetDatabase.getDatabase(context)
                        val utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: ""
                        val pretAvecUtilisateur = updatedPret.copy(utilisateurId = utilisateurId)
                        database.pretPersonnelDao().updatePret(pretAvecUtilisateur)
                        prets = database.pretPersonnelDao().getPretsByUtilisateur(utilisateurId).first()
                        editingPret = null
                    } catch (e: Exception) {
                        // Gérer l'erreur
                    }
                }
            },
            onDismiss = { editingPret = null }
        )
    }
}

@Composable
fun PretItem(pret: PretPersonnel, onEdit: () -> Unit, onDelete: () -> Unit, couleurTheme: CouleurTheme) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pret.nomTiers ?: "Prêt personnel", color = Color.White, fontWeight = FontWeight.Medium)
                Text("Montant initial: ${pret.montantInitial}€ | Solde: ${pret.solde}€ | Type: ${pret.type}", color = Color.Gray, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = couleurTheme.couleur)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red)
                }
            }
        }
    }
}

// ==================== DIALOGS TRANSACTIONS ====================
@Composable
fun AddEditTransactionDialog(
    transaction: Transaction?,
    onSave: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf(transaction?.note ?: "") }
    var montant by remember { mutableStateOf(transaction?.montant?.toString() ?: "") }
    var date by remember { mutableStateOf(transaction?.date ?: "") }
    var type by remember { mutableStateOf(transaction?.type ?: "") }
    var compteId by remember { mutableStateOf(transaction?.compteId ?: "") }
    var collectionCompte by remember { mutableStateOf(transaction?.collectionCompte ?: "") }
    var allocationMensuelleId by remember { mutableStateOf(transaction?.allocationMensuelleId ?: "") }
    var estFractionnee by remember { mutableStateOf(transaction?.estFractionnee ?: false) }
    var sousItems by remember { mutableStateOf(transaction?.sousItems ?: "") }
    var tiersUtiliser by remember { mutableStateOf(transaction?.tiersUtiliser ?: "") }
    var created by remember { mutableStateOf(transaction?.created ?: "") }
    var updated by remember { mutableStateOf(transaction?.updated ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction == null) "Ajouter une transaction" else "Modifier la transaction") },
        text = {
            Column {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = montant,
                    onValueChange = { montant = it },
                    label = { Text("Montant") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = compteId,
                    onValueChange = { compteId = it },
                    label = { Text("ID Compte") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = collectionCompte,
                    onValueChange = { collectionCompte = it },
                    label = { Text("Collection Compte") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = allocationMensuelleId,
                    onValueChange = { allocationMensuelleId = it },
                    label = { Text("ID Allocation Mensuelle") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sousItems,
                    onValueChange = { sousItems = it },
                    label = { Text("Sous-items (JSON)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tiersUtiliser,
                    onValueChange = { tiersUtiliser = it },
                    label = { Text("Tiers à utiliser") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = created,
                    onValueChange = { created = it },
                    label = { Text("Date création") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = updated,
                    onValueChange = { updated = it },
                    label = { Text("Date modification") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (note.isNotBlank() && montant.isNotBlank() && date.isNotBlank() && type.isNotBlank()) {
                        val newTransaction = Transaction(
                            id = transaction?.id ?: "",
                            type = type,
                            montant = montant.toDoubleOrNull() ?: 0.0,
                            date = date,
                            note = note.takeIf { it.isNotBlank() },
                            compteId = compteId,
                            collectionCompte = collectionCompte,
                            allocationMensuelleId = allocationMensuelleId.takeIf { it.isNotBlank() },
                            estFractionnee = estFractionnee,
                            sousItems = sousItems.takeIf { it.isNotBlank() },
                            tiersUtiliser = tiersUtiliser.takeIf { it.isNotBlank() },
                            utilisateurId = "",
                            created = created.takeIf { it.isNotBlank() },
                            updated = updated.takeIf { it.isNotBlank() }
                        )
                        onSave(newTransaction)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

// ==================== DIALOGS ALLOCATIONS ====================
@Composable
fun AddEditAllocationDialog(
    allocation: AllocationMensuelle?,
    onSave: (AllocationMensuelle) -> Unit,
    onDismiss: () -> Unit
) {
    var mois by remember { mutableStateOf(allocation?.mois ?: "") }
    var solde by remember { mutableStateOf(allocation?.solde?.toString() ?: "") }
    var alloue by remember { mutableStateOf(allocation?.alloue?.toString() ?: "") }
    var depense by remember { mutableStateOf(allocation?.depense?.toString() ?: "") }
    var enveloppeId by remember { mutableStateOf(allocation?.enveloppeId ?: "") }
    var compteSourceId by remember { mutableStateOf(allocation?.compteSourceId ?: "") }
    var collectionCompteSource by remember { mutableStateOf(allocation?.collectionCompteSource ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (allocation == null) "Ajouter une allocation" else "Modifier l'allocation") },
        text = {
            Column {
                OutlinedTextField(
                    value = mois,
                    onValueChange = { mois = it },
                    label = { Text("Mois") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = solde,
                    onValueChange = { solde = it },
                    label = { Text("Solde") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = alloue,
                    onValueChange = { alloue = it },
                    label = { Text("Alloué") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = depense,
                    onValueChange = { depense = it },
                    label = { Text("Dépense") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = enveloppeId,
                    onValueChange = { enveloppeId = it },
                    label = { Text("ID Enveloppe") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = compteSourceId,
                    onValueChange = { compteSourceId = it },
                    label = { Text("ID Compte Source") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = collectionCompteSource,
                    onValueChange = { collectionCompteSource = it },
                    label = { Text("Collection Compte Source") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (mois.isNotBlank() && solde.isNotBlank() && alloue.isNotBlank() && depense.isNotBlank()) {
                        val newAllocation = AllocationMensuelle(
                            id = allocation?.id ?: "",
                            mois = mois,
                            solde = solde.toDoubleOrNull() ?: 0.0,
                            alloue = alloue.toDoubleOrNull() ?: 0.0,
                            depense = depense.toDoubleOrNull() ?: 0.0,
                            enveloppeId = enveloppeId,
                            utilisateurId = "",
                            compteSourceId = compteSourceId.takeIf { it.isNotBlank() },
                            collectionCompteSource = collectionCompteSource.takeIf { it.isNotBlank() }
                        )
                        onSave(newAllocation)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

// ==================== DIALOGS CATÉGORIES ====================
@Composable
fun AddEditCategorieDialog(
    categorie: Categorie?,
    onSave: (Categorie) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf(categorie?.nom ?: "") }
    var ordre by remember { mutableStateOf(categorie?.ordre?.toString() ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (categorie == null) "Ajouter une catégorie" else "Modifier la catégorie") },
        text = {
            Column {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ordre,
                    onValueChange = { ordre = it },
                    label = { Text("Ordre") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nom.isNotBlank() && ordre.isNotBlank()) {
                        val newCategorie = Categorie(
                            id = categorie?.id ?: "",
                            nom = nom,
                            ordre = ordre.toIntOrNull() ?: 0,
                            utilisateurId = ""
                        )
                        onSave(newCategorie)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

// ==================== DIALOGS ENVELOPPES ====================
@Composable
fun AddEditEnveloppeDialog(
    enveloppe: Enveloppe?,
    onSave: (Enveloppe) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf(enveloppe?.nom ?: "") }
    var categorieId by remember { mutableStateOf(enveloppe?.categorieId ?: "") }
    var estArchive by remember { mutableStateOf(enveloppe?.estArchive ?: false) }
    var ordre by remember { mutableStateOf(enveloppe?.ordre?.toString() ?: "") }
    var typeObjectif by remember { mutableStateOf(enveloppe?.typeObjectif ?: TypeObjectif.Aucun) }
    var objectifMontant by remember { mutableStateOf(enveloppe?.objectifMontant?.toString() ?: "") }
    var dateObjectif by remember { mutableStateOf(enveloppe?.dateObjectif ?: "") }
    var dateDebutObjectif by remember { mutableStateOf(enveloppe?.dateDebutObjectif ?: "") }
    var objectifJour by remember { mutableStateOf(enveloppe?.objectifJour?.toString() ?: "") }
    var resetApresEcheance by remember { mutableStateOf(enveloppe?.resetApresEcheance ?: false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (enveloppe == null) "Ajouter une enveloppe" else "Modifier l'enveloppe") },
        text = {
            Column {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categorieId,
                    onValueChange = { categorieId = it },
                    label = { Text("ID Catégorie") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ordre,
                    onValueChange = { ordre = it },
                    label = { Text("Ordre") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = objectifMontant,
                    onValueChange = { objectifMontant = it },
                    label = { Text("Montant objectif") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateObjectif,
                    onValueChange = { dateObjectif = it },
                    label = { Text("Date objectif") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateDebutObjectif,
                    onValueChange = { dateDebutObjectif = it },
                    label = { Text("Date début objectif") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = objectifJour,
                    onValueChange = { objectifJour = it },
                    label = { Text("Jour objectif") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nom.isNotBlank() && categorieId.isNotBlank()) {
                        val newEnveloppe = Enveloppe(
                            id = enveloppe?.id ?: "",
                            nom = nom,
                            categorieId = categorieId,
                            utilisateurId = "",
                            estArchive = estArchive,
                            ordre = ordre.toIntOrNull() ?: 0,
                            typeObjectif = typeObjectif,
                            objectifMontant = objectifMontant.toDoubleOrNull() ?: 0.0,
                            dateObjectif = dateObjectif.takeIf { it.isNotBlank() },
                            dateDebutObjectif = dateDebutObjectif.takeIf { it.isNotBlank() },
                            objectifJour = objectifJour.toIntOrNull(),
                            resetApresEcheance = resetApresEcheance
                        )
                        onSave(newEnveloppe)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

// ==================== DIALOGS TIERS ====================
@Composable
fun AddEditTiersDialog(
    tiers: Tiers?,
    onSave: (Tiers) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf(tiers?.nom ?: "") }
    var created by remember { mutableStateOf(tiers?.created ?: "") }
    var updated by remember { mutableStateOf(tiers?.updated ?: "") }
    var collectionId by remember { mutableStateOf(tiers?.collectionId ?: "") }
    var collectionName by remember { mutableStateOf(tiers?.collectionName ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tiers == null) "Ajouter un tiers" else "Modifier le tiers") },
        text = {
            Column {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = created,
                    onValueChange = { created = it },
                    label = { Text("Date création") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = updated,
                    onValueChange = { updated = it },
                    label = { Text("Date modification") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = collectionId,
                    onValueChange = { collectionId = it },
                    label = { Text("ID Collection") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it },
                    label = { Text("Nom Collection") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nom.isNotBlank()) {
                        val newTiers = Tiers(
                            id = tiers?.id ?: "",
                            nom = nom,
                            utilisateurId = "",
                            created = created,
                            updated = updated,
                            collectionId = collectionId,
                            collectionName = collectionName
                        )
                        onSave(newTiers)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

// ==================== DIALOGS PRÊTS ====================
@Composable
fun AddEditPretDialog(
    pret: PretPersonnel?,
    onSave: (PretPersonnel) -> Unit,
    onDismiss: () -> Unit
) {
    var nomTiers by remember { mutableStateOf(pret?.nomTiers ?: "") }
    var montantInitial by remember { mutableStateOf(pret?.montantInitial?.toString() ?: "") }
    var solde by remember { mutableStateOf(pret?.solde?.toString() ?: "") }
    var type by remember { mutableStateOf(pret?.type ?: "") }
    var estArchive by remember { mutableStateOf(pret?.estArchive ?: false) }
    var dateCreation by remember { mutableStateOf(pret?.dateCreation ?: "") }
    var created by remember { mutableStateOf(pret?.created ?: "") }
    var updated by remember { mutableStateOf(pret?.updated ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pret == null) "Ajouter un prêt" else "Modifier le prêt") },
        text = {
            Column {
                OutlinedTextField(
                    value = nomTiers,
                    onValueChange = { nomTiers = it },
                    label = { Text("Nom du tiers") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = montantInitial,
                    onValueChange = { montantInitial = it },
                    label = { Text("Montant initial") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = solde,
                    onValueChange = { solde = it },
                    label = { Text("Solde") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateCreation,
                    onValueChange = { dateCreation = it },
                    label = { Text("Date création") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = created,
                    onValueChange = { created = it },
                    label = { Text("Date création système") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = updated,
                    onValueChange = { updated = it },
                    label = { Text("Date modification") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (montantInitial.isNotBlank() && solde.isNotBlank() && type.isNotBlank()) {
                        val newPret = PretPersonnel(
                            id = pret?.id ?: "",
                            nomTiers = nomTiers.takeIf { it.isNotBlank() },
                            montantInitial = montantInitial.toDoubleOrNull() ?: 0.0,
                            solde = solde.toDoubleOrNull() ?: 0.0,
                            type = type,
                            utilisateurId = "",
                            estArchive = estArchive,
                            dateCreation = dateCreation.takeIf { it.isNotBlank() },
                            created = created.takeIf { it.isNotBlank() },
                            updated = updated.takeIf { it.isNotBlank() }
                        )
                        onSave(newPret)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

// ==================== DIALOGS COMPTES ====================
@Composable
fun AddEditCompteChequeDialog(
    compte: CompteCheque?,
    onSave: (CompteCheque) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf(compte?.nom ?: "") }
    var solde by remember { mutableStateOf(compte?.solde?.toString() ?: "") }
    var pretAPlacer by remember { mutableStateOf(compte?.pretAPlacerRaw?.toString() ?: "") }
    var couleur by remember { mutableStateOf(compte?.couleur ?: "") }
    var estArchive by remember { mutableStateOf(compte?.estArchive ?: false) }
    var ordre by remember { mutableStateOf(compte?.ordre?.toString() ?: "") }
    var collection by remember { mutableStateOf(compte?.collection ?: "comptes_cheques") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (compte == null) "Ajouter un compte chèque" else "Modifier le compte") },
        text = {
            Column {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom du compte") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = solde,
                    onValueChange = { solde = it },
                    label = { Text("Solde") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ordre,
                    onValueChange = { ordre = it },
                    label = { Text("Ordre") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = collection,
                    onValueChange = { collection = it },
                    label = { Text("Collection") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nom.isNotBlank() && solde.isNotBlank() && ordre.isNotBlank() && collection.isNotBlank()) {
                        val newCompte = CompteCheque(
                            id = compte?.id ?: "",
                            nom = nom,
                            solde = solde.toDoubleOrNull() ?: 0.0,
                            pretAPlacerRaw = pretAPlacer.toDoubleOrNull(),
                            utilisateurId = "",
                            couleur = couleur,
                            estArchive = estArchive,
                            ordre = ordre.toIntOrNull() ?: 0,
                            collection = collection
                        )
                        onSave(newCompte)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun AddEditCompteCreditDialog(
    compte: CompteCredit?,
    onSave: (CompteCredit) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf(compte?.nom ?: "") }
    var limiteCredit by remember { mutableStateOf(compte?.limiteCredit?.toString() ?: "") }
    var soldeUtilise by remember { mutableStateOf(compte?.soldeUtilise?.toString() ?: "") }
    var tauxInteret by remember { mutableStateOf(compte?.tauxInteret?.toString() ?: "") }
    var paiementMinimum by remember { mutableStateOf(compte?.paiementMinimum?.toString() ?: "") }
    var fraisMensuelsJson by remember { mutableStateOf(compte?.fraisMensuelsJson ?: "") }
    var estArchive by remember { mutableStateOf(compte?.estArchive ?: false) }
    var ordre by remember { mutableStateOf(compte?.ordre?.toString() ?: "") }
    var collection by remember { mutableStateOf(compte?.collection ?: "comptes_credits") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (compte == null) "Ajouter un compte crédit" else "Modifier le compte") },
        text = {
            Column {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom du compte") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = limiteCredit,
                    onValueChange = { limiteCredit = it },
                    label = { Text("Limite de crédit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = soldeUtilise,
                    onValueChange = { soldeUtilise = it },
                    label = { Text("Solde utilisé") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tauxInteret,
                    onValueChange = { tauxInteret = it },
                    label = { Text("Taux d'intérêt (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = paiementMinimum,
                    onValueChange = { paiementMinimum = it },
                    label = { Text("Paiement minimum") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fraisMensuelsJson,
                    onValueChange = { fraisMensuelsJson = it },
                    label = { Text("Frais mensuels (JSON)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ordre,
                    onValueChange = { ordre = it },
                    label = { Text("Ordre") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = collection,
                    onValueChange = { collection = it },
                    label = { Text("Collection") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nom.isNotBlank() && limiteCredit.isNotBlank() && soldeUtilise.isNotBlank() && 
                        tauxInteret.isNotBlank() && ordre.isNotBlank() && collection.isNotBlank()) {
                        val newCompte = CompteCredit(
                            id = compte?.id ?: "",
                            nom = nom,
                            limiteCredit = limiteCredit.toDoubleOrNull() ?: 0.0,
                            soldeUtilise = soldeUtilise.toDoubleOrNull() ?: 0.0,
                            tauxInteret = tauxInteret.toDoubleOrNull() ?: 0.0,
                            paiementMinimum = paiementMinimum.toDoubleOrNull(),
                            fraisMensuelsJson = fraisMensuelsJson.takeIf { it.isNotBlank() },
                            utilisateurId = "",
                            estArchive = estArchive,
                            ordre = ordre.toIntOrNull() ?: 0,
                            collection = collection
                        )
                        onSave(newCompte)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun AddEditCompteDetteDialog(
    compte: CompteDette?,
    onSave: (CompteDette) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf(compte?.nom ?: "") }
    var soldeDette by remember { mutableStateOf(compte?.soldeDette?.toString() ?: "") }
    var montantInitial by remember { mutableStateOf(compte?.montantInitial?.toString() ?: "") }
    var tauxInteret by remember { mutableStateOf(compte?.tauxInteret?.toString() ?: "") }
    var paiementMinimum by remember { mutableStateOf(compte?.paiementMinimum?.toString() ?: "") }
    var dureeMoisPret by remember { mutableStateOf(compte?.dureeMoisPret?.toString() ?: "") }
    var paiementEffectue by remember { mutableStateOf(compte?.paiementEffectue?.toString() ?: "") }
    var prixTotal by remember { mutableStateOf(compte?.prixTotal?.toString() ?: "") }
    var estArchive by remember { mutableStateOf(compte?.estArchive ?: false) }
    var ordre by remember { mutableStateOf(compte?.ordre?.toString() ?: "") }
    var collection by remember { mutableStateOf(compte?.collection ?: "comptes_dettes") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (compte == null) "Ajouter un compte dette" else "Modifier le compte") },
        text = {
            Column {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom du compte") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = soldeDette,
                    onValueChange = { soldeDette = it },
                    label = { Text("Solde dette") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = montantInitial,
                    onValueChange = { montantInitial = it },
                    label = { Text("Montant initial") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tauxInteret,
                    onValueChange = { tauxInteret = it },
                    label = { Text("Taux d'intérêt (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = paiementMinimum,
                    onValueChange = { paiementMinimum = it },
                    label = { Text("Paiement minimum") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dureeMoisPret,
                    onValueChange = { dureeMoisPret = it },
                    label = { Text("Durée en mois") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = paiementEffectue,
                    onValueChange = { paiementEffectue = it },
                    label = { Text("Paiements effectués") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prixTotal,
                    onValueChange = { prixTotal = it },
                    label = { Text("Prix total") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ordre,
                    onValueChange = { ordre = it },
                    label = { Text("Ordre") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = collection,
                    onValueChange = { collection = it },
                    label = { Text("Collection") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nom.isNotBlank() && soldeDette.isNotBlank() && montantInitial.isNotBlank() && 
                        tauxInteret.isNotBlank() && ordre.isNotBlank() && collection.isNotBlank()) {
                        val newCompte = CompteDette(
                            id = compte?.id ?: "",
                            nom = nom,
                            soldeDette = soldeDette.toDoubleOrNull() ?: 0.0,
                            montantInitial = montantInitial.toDoubleOrNull() ?: 0.0,
                            tauxInteret = tauxInteret.toDoubleOrNull() ?: 0.0,
                            paiementMinimum = paiementMinimum.toDoubleOrNull(),
                            dureeMoisPret = dureeMoisPret.toIntOrNull(),
                            paiementEffectue = paiementEffectue.toIntOrNull() ?: 0,
                            prixTotal = prixTotal.toDoubleOrNull(),
                            utilisateurId = "",
                            estArchive = estArchive,
                            ordre = ordre.toIntOrNull() ?: 0,
                            collection = collection
                        )
                        onSave(newCompte)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun AddEditCompteInvestissementDialog(
    compte: CompteInvestissement?,
    onSave: (CompteInvestissement) -> Unit,
    onDismiss: () -> Unit
) {
    var nom by remember { mutableStateOf(compte?.nom ?: "") }
    var solde by remember { mutableStateOf(compte?.solde?.toString() ?: "") }
    var estArchive by remember { mutableStateOf(compte?.estArchive ?: false) }
    var ordre by remember { mutableStateOf(compte?.ordre?.toString() ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (compte == null) "Ajouter un compte investissement" else "Modifier le compte") },
        text = {
            Column {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom du compte") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = solde,
                    onValueChange = { solde = it },
                    label = { Text("Solde") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ordre,
                    onValueChange = { ordre = it },
                    label = { Text("Ordre") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nom.isNotBlank() && solde.isNotBlank() && ordre.isNotBlank()) {
                        val newCompte = CompteInvestissement(
                            id = compte?.id ?: "",
                            nom = nom,
                            solde = solde.toDoubleOrNull() ?: 0.0,
                            utilisateurId = "",
                            estArchive = estArchive,
                            ordre = ordre.toIntOrNull() ?: 0
                        )
                        onSave(newCompte)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
