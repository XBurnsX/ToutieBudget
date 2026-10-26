# Rapport global QA ToutieBudget

## Baseline vérifiée

- Date de campagne: 25 octobre 2026, fuseau America/Toronto.
- Dépôt: `D:\Toutie Budget`.
- Branche: `release/1.6.1`.
- Commit: `1d5b399630865cfc332d671ce2c381d74fcb211d`.
- Application: `1.6.2`, build `41`.
- Spécification: `ToutieBudget_Codex_Tester_Repartage.txt`.
- Catalogue original: 491 scénarios racines dans 24 modules.
- Test exploratoire ajouté: `SEC-016`; dénominateur courant: 492.
- Émulateur visible actif: `ToutieBudget_Phone_Visible_API36`, Android 16, APK **release** installé directement par ADB sans Play Store.
- Signature release installée: SHA-1 `14:D6:F9:65:C3:5C:1F:33:6C:89:DD:74:89:B9:2E:93:8A:06:3C:C5`; APK installé identique octet pour octet à `app/build/outputs/apk/release/app-release.apk`.

## État de couverture

| Statut | Nombre |
|---|---:|
| PASS | 1 |
| FAIL | 2 |
| BLOCKED | 0 |
| NOT_IMPLEMENTED | 0 |
| NOT_APPLICABLE | 0 |
| NOT_RUN | 489 |
| Total | 492 |

Résultats valides actuels: `AUTH-015` PASS; `SEC-009` FAIL; `SEC-016` FAIL.

Les exécutions locales `BUD-001` et `BUD-003` ont été invalidées et restent `NOT_RUN`: leurs fixtures Room ne correspondaient pas à PocketBase. Elles ne sont pas comptées comme preuve fonctionnelle.

## Préflight technique

- `gradlew clean assembleDebug`: PASS.
- `gradlew clean assembleRelease`: PASS; version `1.6.2` build `41`, signature release vérifiée avant et après installation.
- Premier assemblage incrémental concurrent: APK incohérent et crash `NoClassDefFoundError`; disparu après reconstruction propre, donc non retenu comme anomalie produit.
- `gradlew testDebugUnitTest`: échec de compilation KAPT des tests existants (`NonExistentClass`), avec seulement deux fichiers de tests unitaires et aucune dépendance de test déclarée dans `app/build.gradle.kts`.
- Deux émulateurs ont été créés; le téléphone visible est opérationnel.
- 239 routes/contrôles Compose ont été découverts automatiquement dans `qa/inventaire_ui.csv`; cet inventaire source reste à valider dans l’APK.

## Blocages de laboratoire à lever

1. Aucun compte PocketBase `Tester A` authentifié dans l’émulateur.
2. Aucun `Tester B` distinct disponible pour les tests d’isolation.
3. Aucune URL PocketBase TEST distincte de l’URL courante.
4. Les fichiers vivants `ToutieBudget_Ajouts_A_Evaluer.txt` et `ToutieBudget_Plan_Execution_Complet_20261025.txt` référencés par la spécification ne sont pas présents dans le dépôt ni dans Downloads.
5. Les règles actuelles de plusieurs collections PocketBase autorisent une lecture sans authentification.
6. Google Sign-In release échoue avec `ApiException: 10` sur l’émulateur. Le client Web codé appartient au projet `1078578579569`, tandis que `google-services.json` appartient au projet `857272496129`; la connexion fonctionnerait sur le téléphone, dont la version et la signature installées restent à comparer.

## Prochaine reprise sûre

Authentifier dans l’émulateur visible un compte explicitement dédié aux tests. Après obtention de son véritable ID PocketBase, créer les fixtures des deux côtés avec des IDs stables, prendre un snapshot/restore vérifiable, puis exécuter les modules AUTH, BUD, PROV et VIR dans cet ordre. Ne pas utiliser un compte contenant des données financières réelles.
