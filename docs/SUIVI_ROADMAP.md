# Suivi de la roadmap ToutieBudget

Fichier de progression SÉPARÉ du TXT maître (`ToutieBudget_Roadmap_Complete_Repartage.txt`,
SHA-256 `e494e2bc518ca25eaa7ebf0e54c84bc49329c41a3c32eb75e801123f542128e6`).
Le maître n'est jamais modifié ni résumé ici : ce fichier indique seulement l'état des IDs.

## Décisions de l'utilisateur

- **25 oct. 2026** : la Phase 0 (protocole de tests T-00 à T-06) est mise de côté.
  Le travail des versions 1.6.1 et 1.6.2 est considéré **FAIT**.
- **25 oct. 2026 (soir)** : Plaid (BANQ-01 à BANQ-06) et le compte Tester sont reportés.
  On code le reste du maître, dans l'ordre du plan (Phase 0B puis Phase 2...).
- **25 oct. 2026 (soir)** : référence de travail = `ToutieBudget_Roadmap_Pages_Seulement_Sans_Integration_Bancaire_Ni_Bot.txt`
  (SHA-256 `349e3233cf67d4519af15341b34acce7a5c5b3c288958ae8e62465f180291355`), mêmes IDs que le maître.

## Fait — versions 1.6.1 / 1.6.2 (branche `release/1.6.1`)

| ID | État | Portée réalisée |
|---|---|---|
| SJ1 | FAIT | CREATE rejoué → PATCH si la fiche existe ; DELETE 404 = réussi |
| SJ2 | FAIT | Le worker s'arrête proprement entre deux tâches |
| SJ3 | FAIT | Un seul mapping type → collection (`SyncCollections`) pour le worker et la synchro immédiate |
| SJ9 | FAIT | Événements temps réel écrits dans Room ; modification locale en attente prioritaire |
| SJ15 | FAIT | Rattrapage incrémental (`updated`) limité à l'utilisateur, sans écrasement local |
| SJ18 | FAIT | Bearer partout ; collections uniformes |
| BD3 | FAIT | Plus d'id vide à la création dans le gestionnaire de base |
| BD9 | FAIT | Le gestionnaire de base crée des SyncJobs (format partagé `SyncPayloads`) |
| BD17 | FAIT | Rechargement après changement venu d'un autre appareil (temps réel → Room) |
| LD5 | FAIT | Plus de jeton, code OAuth ni réponse d'auth dans les logs |

Hors maître (demandé directement) :
- Secrets de signature hors du code (`keystore.properties`), clé retirée du suivi git.
- Mise à jour automatique de l'app via PocketBase (`app_versions`, `publier-mise-a-jour.ps1`) — 1.6.2 installée avec succès le 24 oct. 2026.
- Version réelle affichée dans Paramètres.

## Fait — 25 oct. 2026 (soir), branche `release/1.6.1`, pas encore publié

| ID | État | Portée réalisée |
|---|---|---|
| LD1 | FAIT | Plus de raccourci « 1 transaction = base complète » : import marqué complet par utilisateur |
| LD2 / SJ15 | FAIT | Fusion sans écraser les fiches avec modification locale en attente |
| LD8 / LD15 / SJ14 | FAIT | Une erreur HTTP/réseau/JSON fait échouer l'import (plus de liste vide « réussie ») |
| LD9 / LD10 | FAIT | Session mémorisée sans import complet : import repris au démarrage ; bouton Réessayer |
| LD14 | FAIT | Écran d'import : une seule navigation, même en double appui |
| LD19 | FAIT | « Continuer avec les données locales » seulement si elles existent |
| SJ13 | FAIT | Toutes les collections paginées (comptes, tiers, prêts limités à 30 avant) |
| SJ2 | FAIT | Plus de cancelUniqueWork/REPLACE ; verrou = un seul envoi à la fois |
| SJ5 | FAIT | Ordre par fiche : une tâche en échec retient les suivantes de la même fiche |
| SJ7 / LD7 | FAIT | Chaque tâche liée à son utilisateur ; envoyée seulement avec sa session |
| SJ10 | FAIT | Réseau/408/429/5xx réessayés avec délai croissant ; 400/403/404/422 bloquées ; 401 arrête l'envoi |
| SJ11 | FAIT | Essais, dernière erreur, code HTTP et date par tâche (migration Room 3 → 4) |
| SJ12 | FAIT | Réessayer manuel débloque la tâche et relance la synchro, sans effacer l'historique |
| SJ19 | FAIT | L'écran des tâches n'affiche plus le JSON |
| LD4 | FAIT | Jeton de session chiffré (Android Keystore, AES-GCM) ; ancien jeton en clair converti |
| LD5 | FAIT | Plus de courriel ni de nom dans les logs de connexion |

## En cours

| ID | État | Notes |
|---|---|---|
| BANQ-01 à 06 | REPORTÉ | Plaid remis à plus tard (décision du 25 oct. 2026) |
| T-00 à T-06 | REPORTÉ | Protocole de tests / compte Tester remis à plus tard |
