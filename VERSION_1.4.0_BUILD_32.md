# Version 1.4.0 Build 32

## Informations de Build
- **Version Name**: 1.4.0
- **Version Code**: 32
- **Date de Build**: 27 Août 2025
- **Type de Build**: Release
- **Format**: App Bundle (.aab)

## Fichier de Build
- **Nom**: `app-release.aab`
- **Taille**: ~15.8 MB
- **Emplacement**: `app/build/outputs/bundle/release/`

## Commandes de Build
```bash
# Mise à jour de la version dans build.gradle.kts
versionCode = 32
versionName = "1.4.0"

# Génération de l'App Bundle
./gradlew bundleRelease
```

## Notes
- Build signé avec la clé de release
- Compatible Android API 26+ (Android 8.0+)
- Target SDK 36 (Android 14)
- Compilé avec Kotlin et Jetpack Compose

## Corrections apportées

### 🐛 **Problème résolu : Rafraîchissement automatique des données dans VirerArgentScreen et ComptesScreen**
- **Problème** : Les enveloppes n'apparaissaient pas ou n'avaient pas de solde dans l'écran de virement
- **Cause** : Les données ne se rafraîchissaient pas automatiquement quand modifiées via `ClavierBudgetEnveloppe` ou virements
- **Solution** : Implémentation d'un rafraîchissement automatique toutes les 2 secondes pour détecter les changements

### 🔧 **Modifications techniques**
- `VirerArgentViewModel.kt` : Ajout de `demarrerRafraichissementAutomatique()`
- `ComptesViewModel.kt` : Ajout de `demarrerRafraichissementAutomatique()`
- Rafraîchissement automatique des données toutes les 2 secondes
- Détection automatique des changements et mise à jour de l'UI
- Maintien de la logique métier existante (filtrage des sources avec `solde > 0`)
- **ComptesScreen** : Les soldes et prêts à placer se mettent à jour automatiquement après virements

## Utilisation
Cet App Bundle peut être uploadé sur Google Play Console pour distribution.
