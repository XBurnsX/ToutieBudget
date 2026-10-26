# État vérifié (2026-10-26)

## Dépôt consulté : XBurnsX/ToutieBudget

Fichiers lus par le connecteur GitHub :
- `app/build.gradle.kts` : package `com.xburnsx.toutiebudget`, version `1.6.2` / code `41`, Kotlin / Jetpack Compose
- `app/src/main/AndroidManifest.xml` : activité principale
- `ui/navigation/Navigation.kt` : démarrage, authentification, routes principales
- `ui/budget/BudgetScreen.kt` : budget et recherche
- `ui/comptes/ComptesScreen.kt` : comptes, réconciliation, archivage
- `ui/categories/CategoriesEnveloppesScreen.kt` : catégories
- `ui/ajout_transaction/AjoutTransactionScreen.kt` : dépenses, fractionnement, paiement
- `ui/virement/VirerArgentScreen.kt` : transferts comptes/enveloppes
- `ui/settings/SettingsScreen.kt` : paramètres
- `ui/statistiques/StatistiquesScreen.kt` : statistiques
- `ui/dette/DetteScreen.kt` : dette
- `ui/archives/ArchivesScreen.kt` : archives

**Le dépôt entier n'a pas été récupéré fichier par fichier.** Tentative de clonage échouée : le conteneur n'a pas de résolution réseau GitHub. Ne pas confondre ces inspections ciblées avec un audit complet.

## Ce qui a été vérifié localement

- Compilation syntaxique Python (`py_compile`) réussie.
- Tests unitaires de l'oracle financier (montants, modifications, annulations, transferts) réussis.
- 40 000 cycles de calculs aléatoires sur le modèle de référence réussis.

## NON réalisé / NON prouvé

- Aucune exécution de tests Android : `adb` absent de cet environnement et pas d'appareil connecté.
- Aucune preuve d'une création réelle de compte, d'enveloppe ou de transaction dans ToutieBudget.
- Aucun résultat PASS sur l'application Android.
- Pas de simulation réelle de 48 mois calendaires. Naviguer les mois affichés ne remplace pas l'écoulement du temps.
- Pas de couverture garantie de toutes les fonctions, contrôles ou cas destructeurs imaginables.

## Règle des résultats

Les résultats `DISCOVERED`, `BLOCKED` et `UNVERIFIED` **ne sont jamais assimilés à des tests métier réussis**. Chaque vérification de logique financière de ToutieBudget doit être liée à une action réelle et à une observation UI pertinente. Le modèle financier fourni est un début d'oracle et ne prétend pas modéliser toutes les règles budgétaires spécifiques à l'application.
