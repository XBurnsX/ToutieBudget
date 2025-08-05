# Previews des Composants Cartes de Crédit

Ce document liste toutes les previews ajoutées pour les composants et dialogs liés aux cartes de crédit.

## 📱 Composants UI

### 1. CarteCreditDetailCard
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/composants/CarteCreditDetailCard.kt`
- **Preview**: `CarteCreditDetailCardPreview()`
- **Description**: Affiche les détails principaux d'une carte de crédit avec statistiques

### 2. FraisMensuelsCard
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/composants/FraisMensuelsCard.kt`
- **Previews**: 
  - `FraisMensuelsCardPreview()` - Avec frais configurés
  - `FraisMensuelsCardEmptyPreview()` - Sans frais configurés
- **Description**: Affiche les frais mensuels fixes (assurance, AccordD, etc.)

### 3. CalculateurPaiement
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/composants/CalculateurPaiement.kt`
- **Preview**: `CalculateurPaiementPreview()`
- **Description**: Permet de calculer des plans de remboursement

### 4. SimulateurRemboursement
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/composants/SimulateurRemboursement.kt`
- **Preview**: `SimulateurRemboursementPreview()`
- **Description**: Affiche différents scénarios de remboursement

### 5. AlertesCartesCredit
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/composants/AlertesCartesCredit.kt`
- **Previews**:
  - `AlertesCartesCreditPreview()` - Avec alertes
  - `AlertesCartesCreditEmptyPreview()` - Sans alertes
- **Description**: Affiche les alertes liées aux cartes de crédit

## 🗨️ Dialogs

### 1. ModifierFraisDialog
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/dialogs/ModifierFraisDialog.kt`
- **Preview**: `ModifierFraisDialogPreview()`
- **Description**: Dialog pour modifier les frais mensuels fixes

### 2. ModifierCarteCreditDialog
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/dialogs/ModifierCarteCreditDialog.kt`
- **Preview**: `ModifierCarteCreditDialogPreview()`
- **Description**: Dialog pour modifier les informations d'une carte de crédit

### 3. PlanRemboursementDialog
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/dialogs/PlanRemboursementDialog.kt`
- **Preview**: `PlanRemboursementDialogPreview()`
- **Description**: Dialog affichant un plan de remboursement détaillé

## 🖥️ Écrans

### 1. GestionCarteCreditScreen
- **Fichier**: `app/src/main/java/com/xburnsx/toutiebudget/ui/cartes_credit/CartesCreditScreen.kt`
- **Preview**: `GestionCarteCreditScreenPreview()`
- **Description**: Écran principal de gestion d'une carte de crédit

## 🎨 Utilisation des Previews

### Dans Android Studio
1. Ouvrir un fichier contenant une preview
2. Cliquer sur l'icône "Split" ou "Design" en haut à droite
3. La preview s'affichera dans le panneau de droite

### Avantages
- **Développement rapide** : Voir le rendu sans lancer l'app
- **Tests visuels** : Vérifier l'apparence des composants
- **Documentation** : Les previews servent d'exemples d'utilisation
- **Debugging** : Identifier rapidement les problèmes de layout

### Données de Test
Toutes les previews utilisent des données de test cohérentes :
- **Carte Visa** avec limite de 10 000€
- **Solde utilisé** : 2 500€ (dette)
- **Taux d'intérêt** : 19.99%
- **Frais mensuels** : 15.50€ (Assurance)

## 🔧 Configuration

### Background
Toutes les previews utilisent un background sombre (`0xFF121212`) pour correspondre au thème de l'application.

### Imports Requis
```kotlin
import androidx.compose.ui.tooling.preview.Preview
```

### Structure Standard
```kotlin
@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun NomComposantPreview() {
    // Données de test
    val carteCredit = CompteCredit(...)
    
    // Appel du composant
    NomComposant(
        // paramètres...
    )
}
```

## 📝 Notes

- Toutes les previews sont fonctionnelles et compilent sans erreur
- Les données de test sont réalistes et représentatives
- Les previews incluent les cas vides et avec données
- Chaque composant a sa propre preview indépendante 