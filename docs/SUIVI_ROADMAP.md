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

## Fait — 26 oct. 2026, dans l'ordre du TXT des pages, branche `release/1.6.1`, pas encore publié

| ID | État | Portée réalisée |
|---|---|---|
| A2 | FAIT | Menu ☰ « Répartir l'argent » : un compte → plusieurs enveloppes, aperçu puis confirmation, provenance validée pour chaque enveloppe avant toute écriture |
| A3 | FAIT | Filtres à la demande (favorites, négatives, sans fonds, objectif à financer, avec objectif, solde disponible) |
| A4 | FAIT | Étoile dans le clavier d'enveloppe ; section « Favoris » repliable en tête, catégories inchangées |
| A5 | FAIT | Icône loupe : recherche par nom, rien d'affiché quand fermé |
| A7 | FAIT | Menu ☰ « Prévisions » : reste à prévoir avant la fin du mois ou une date choisie |
| A8 / BUD-01 | FAIT | Paramètres › « Refaire la répartition d'un mois » : rend l'assigné non dépensé au Prêt à placer de son compte, aperçu + confirmation ; rollover, autres mois et transactions intacts |
| C2 | FAIT | Catégories ouvertes/fermées gardées sur l'appareil (hors ligne), envoyées à PocketBase ensuite |
| C3 | FAIT | Le clavier se ferme seulement sur la réussite de SON assignation ; erreurs en dialogue, champs conservés |
| C4 | FAIT | « Gérer solde négatif » en $ CA |
| P3 | FAIT | Archivés : couleurs/libellés selon le vrai type |
| P4 | FAIT | Détail vidé avant chargement ; réponse d'un autre dossier ignorée |
| P5 | FAIT (partiel) | Double crédit (Dette contractée) et double débit (Remboursement donné) corrigés ; erreurs d'écriture remontées. Pas de transaction Room unique : la synchro immédiate fait du réseau pendant l'écriture |
| P6 | FAIT | Remboursement > total restant du tiers refusé avant toute modification de compte |
| P7 | FAIT | Onglet « Archivés » |
| PP2 / PP3 | FAIT | Archiver (confirmation, solde conservé) / Réactiver dans Détails |
| PP6 | FAIT | Montant déjà remboursé + progression dans Détails |

| V1 / V2 / V3 | FAIT | Virements de budget toujours dans le mois choisi, allocation par enveloppe ET mois (fusionnée), solde bancaire intact (avant : Prêt à placer → enveloppe baissait le solde bancaire via l'ancien service) |
| V4 | FAIT (compensation) | Validation complète avant écriture ; la 1re écriture est annulée si la 2e échoue ; succès seulement après écriture |
| V5 | FAIT | Menu des comptes : se ferme au clic extérieur ou sur lui-même |
| V6 | FAIT | Erreur de chargement visible + « Réessayer » ; plus d'erreurs avalées |
| V7 | FAIT | Provenance suivie ; mélanges de comptes refusés |
| V8 | FAIT | Enveloppe peut passer sous 0 $ (déficit annoncé) ; Prêt à placer jamais négatif |
| VA4 | FAIT | Couples récents (4) proposés tant que rien n'est choisi |
| VA5 | FAIT | Destinations compatibles d'abord ; impossibles grisées avec la raison ; négatives toujours permises |
| VA6 | FAIT | Paramètres › « Confirmer les gros virements » (désactivé par défaut, seuil 100 à 2 500 $) |

| CO1 | FAIT | Dette archivée : historique gardé ; son enveloppe n'est plus SUPPRIMÉE (ni la catégorie Dettes) — archivée seulement si la dette est terminée et l'enveloppe vide, sinon laissée active avec un message |
| CO2 | FAIT | Archivage et modification par `copy()` : taux, minimums, frais, durée, valeurs initiales conservés |
| CO3 | FAIT | Archives : liste séparée (`comptesArchives`), non écrasée par les rafraîchissements |
| CO4 | FAIT | « comptes_investissement » (singulier) partout |
| CO5 | FAIT | Écart appliqué au solde (+ Prêt à placer pour un chèque) ; trace « RECONCILIATION » dans l'historique au lieu d'un faux Revenu/Dépense ; compte remis comme avant si la trace échoue |
| CO6 | FAIT | Erreurs de création/modification/archivage affichées ; le dialogue reste ouvert avec les valeurs |
| CO7 | FAIT | Confirmation avant archivage (solde, Prêt à placer, sort de l'enveloppe de dette) |
| CO8 | FAIT | Sondage toutes les 2 s supprimé ; rechargement sur événements et à l'ouverture de l'écran |
| CO9 | FAIT | Solde et Prêt à placer indépendants ; changement tracé « MODIFICATION_COMPTE » ; arrondi au cent corrigé dans le dialogue |
| CA3 | FAIT | Groupes de comptes repliables (mémorisé sur l'appareil) |
| CA4 | FAIT | Paramètres › « Masquer les comptes à zéro » |
| CA5 | FAIT | Réconciliation : solde actuel, solde réel, différence, mention du Prêt à placer avant de confirmer |

| CC1 | FAIT | `MoteurCarteCredit` : un seul moteur pour carte, détails, minimum, simulateur et tableau ; dette = max(0, -solde), avoir jamais traité comme dette ; utilisation non plafonnée |
| CC2 | FAIT | Paiement : plus de remboursement d'une dette parce qu'un frais porte son nom ; côté carte « Paiement » au lieu d'« Emprunt » (comptait comme revenu) ; enveloppe cherchée dans « Cartes de crédit »/« Dettes » et provenance vérifiée ; enveloppe de dette archivée, plus supprimée ; compte payeur remis si l'écriture sur la carte échoue |
| CC3 | FAIT | Solde SIGNÉ affiché et saisi tel quel (création et modification) ; correction tracée « CORRECTION_SOLDE_CARTE » sans faux paiement ; création : émetteur, solde, limite, taux |
| CC4 | FAIT | Jour de clôture + délai d'échéance ; prochaine clôture/échéance ; relevé distinct du solde courant |
| CC5 | FAIT | Intérêts estimés jour par jour depuis le début du cycle (soldes reconstitués avec les transactions datées, délai de grâce si relevé payé en entier) ; plus jamais ajoutés au solde automatiquement |
| CC6 | FAIT | Relevés officiels (solde, minimum, échéance, intérêts, frais, paiements) ; doublon de date refusé ; intérêts facturés ajoutés au solde seulement sur confirmation, une fois |
| CC7 | FAIT | Profil Desjardins 5 % (configurable, plancher optionnel) ; minimum officiel du relevé prioritaire ; estimation sur le solde du RELEVÉ |
| CC8 | FAIT | Simulateur : minimum dégressif, montant fixe, % du solde, date cible ; tableau mensuel (début, intérêts, frais, paiement, capital, fin) ; avertit quand le versement ne couvre pas intérêts + frais |
| CC9 | FAIT | Alertes réelles : limite dépassée, crédit faible, échéance proche, échéance passée sans paiement trouvé, intérêts facturés, prochain relevé |
| CC10 | FAIT | Taux avances / transferts / promo (+ fin) / retard ; frais mensuels, annuels, ponctuels avec date ; même nom + type = remplacé ; jamais facturés automatiquement |
| CC11 | FAIT | Par relevé : intérêts facturés vs estimés ; cumul réel par année ; gain d'intérêts et de temps par rapport au minimum |

À savoir : les nouveaux champs `comptes_credits.details_carte_json` et `user_preferences.enveloppes_favorites`
doivent être ajoutés à PocketBase avec `migrer-pocketbase.ps1` (identifiants admin saisis par l'utilisateur).
Base Room passée en version 5 (migration sans perte).

À savoir : les favoris s'envoient à PocketBase dans `user_preferences.enveloppes_favorites` ; tant que ce champ
(JSON) n'existe pas dans la collection, ils restent seulement sur l'appareil.

## En cours

| ID | État | Notes |
|---|---|---|
| BANQ-01 à 06 | REPORTÉ | Plaid remis à plus tard (décision du 25 oct. 2026) |
| T-00 à T-06 | REPORTÉ | Protocole de tests / compte Tester remis à plus tard |
