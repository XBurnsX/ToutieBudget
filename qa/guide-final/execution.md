# Exécution du guide final ToutieBudget

## Baseline

- Date: 2026-10-26, fuseau America/Toronto.
- Guide: `ToutieBudget_GUIDE_CODEX_100POURCENT_ETAPES_ET_BUGS_FIN_20261026.txt`.
- Dépôt: `D:\Toutie Budget`.
- Branche: `release/1.6.1`.
- Commit: `888d4dac7375bb84314229036718631c31a6c7f2`.
- APK: release 1.6.3, build 42.
- SHA-256 APK: `3FFAD486908842DF03D783A55AFD5EDEA06F59FD4C072D976A11938B198E8D40`.
- Installation: désinstallation complète, puis installation neuve directe avec ADB sur `ToutieBudget_Phone_Visible_API36`.

## Précontrôle d'isolation PocketBase — FAIL critique

Sans session ni en-tête `Authorization`, les endpoints de liste PocketBase ont répondu HTTP 200 et retourné au moins un enregistrement pour `users`, les quatre familles de comptes, `categories`, `enveloppes`, `allocations_mensuelles`, `transactions`, `tiers` et `user_preferences`.

Aucune valeur métier, adresse, ID ou donnée personnelle n'a été affichée ou conservée. Tant que les règles restent ouvertes, aucun reset global, DELETE brut ou test destructif inter-utilisateur ne sera exécuté.

## Étapes

### Étape 001 — PASS fonctionnel / FAIL sécurité séparé

- Action: installation neuve, ouverture, toucher « Se connecter avec Google », connexion automatique avec le compte déjà autorisé.
- Résultat: Budget s'ouvre; après synchronisation, aucune donnée d'un autre utilisateur n'est visible.
- Preuves: `qa/screenshots/guide-final-163-fresh-install.png`, `qa/screenshots/guide-final-step001-google.png`, `qa/screenshots/guide-final-step001-after-sync.png` et UI dumps correspondants.
- Limite: les règles PocketBase publiques font échouer le précontrôle d'isolation serveur documenté ci-dessus.

### Étape 002 — PASS

- Action: création de la catégorie `Vie courante`, puis de l'enveloppe `Epicerie`.
- Résultat: une seule catégorie et une seule enveloppe apparaissent; Budget affiche `0,00 $`.
- Preuves: `qa/screenshots/step002-category-created.png`, `qa/screenshots/step002-complete.png`, `qa/screenshots/step002-budget-verification.png` et UI dumps correspondants.
- Note: le nom est saisi sans accent conformément à l'instruction utilisateur; le scénario spécifique aux accents reste l'étape 008.

### Étape 003 — PASS

- Action: depuis Budget, ouverture successive de Comptes, Ajouter, Stats, Catégories et Paramètres, avec retour à Budget entre chaque écran.
- Résultat: les cinq écrans s'ouvrent; les retours fonctionnent; aucun chargement interminable ni écran vide masqué.
- Preuves: `qa/screenshots/step003-comptes.png`, `qa/screenshots/step003-ajouter.png`, `qa/screenshots/step003-stats.png`, `qa/screenshots/step003-categories.png`, `qa/screenshots/step003-settings.png` et UI dumps correspondants.

### Étape 004 — PASS

- Action: relevé de Budget et de Comptes avant la création du moindre compte.
- Résultat: Budget contient seulement `Vie courante / Epicerie / 0,00 $`; Comptes ne contient aucun compte fictif.
- Preuves: `qa/screenshots/step004-budget-before-accounts.png`, `qa/screenshots/step004-accounts-empty.png`, `qa/ui-dumps/step004-accounts-empty.xml`.

### Étape 005 — PASS

- `Essence` a été créée dans `Vie courante`; `Epicerie` et `Essence` restent chacune à `0,00 $`.
- Preuves: `qa/screenshots/step005-essence-created.png`, `qa/screenshots/step005-budget-verification.png`.

### Étape 006 — PASS

- `Maison / Loyer` a été créée; les enveloppes existantes n'ont pas bougé et les trois soldes restent à `0,00 $`.
- Preuves: `qa/screenshots/step006-maison-loyer.png`, `qa/ui-dumps/step006-budget-verification.xml`.

### Étape 007 — PASS

- `Loisirs / Sorties` a été créée comme catégorie et enveloppe distinctes.
- Preuve: `qa/screenshots/step007-loisirs-sorties.png`.

### Étape 008 — PASS selon instruction utilisateur

- `Electricite & chauffage` a été créée sous `Maison` et le nom est resté identique après navigation et retour.
- Les accents ont été omis conformément à l'instruction directe de l'utilisateur; l'esperluette et les espaces ont été conservés.
- Preuves: `qa/screenshots/step008-created.png`, `qa/screenshots/step008-after-navigation.png`.

### Étape 009 — PASS

- Une enveloppe sans nom est refusée: le bouton `Créer` n'est pas cliquable et les cinq enveloppes existantes restent uniques.
- Preuve: `qa/screenshots/step009-empty-refused.png`.

### Étape 010 — PASS

- Une catégorie sans nom est refusée: le bouton `Créer` n'est pas cliquable et les trois catégories restent uniques.
- Preuve: `qa/screenshots/step010-empty-category-refused.png`.

### Étape 011 — PASS avec limite UX

- Une deuxième `Epicerie` dans `Vie courante` est refusée et l'originale reste intacte.
- Limite: aucun message explicatif n'est affiché; le dialogue reste simplement ouvert.
- Preuve: `qa/screenshots/step011-duplicate-result.png`.

### Étape 012 — NOT_IMPLEMENTED

- Une enveloppe `Epicerie` dans `Loisirs` est aussi refusée. L'application interdit donc actuellement les noms identiques entre catégories.
- Aucune fusion et aucun doublon n'ont été créés.
- Preuve: `qa/screenshots/step012-cross-category-duplicate.png`.

### Étape 013 — PASS

- La création de `Annulation` a été annulée; l'enveloppe n'existe pas ensuite.
- Preuves: `qa/screenshots/step013-before-cancel.png`, `qa/screenshots/step013-after-cancel.png`.

### Étape 014 — PASS

- `Vie courante` se replie; le repli persiste après navigation vers Catégories et retour; le redépli restaure `Epicerie` et `Essence`.
- Preuves: `qa/screenshots/step014-budget-collapsed.png`, `qa/screenshots/step014-budget-expanded.png`.

### Étape 015 — PASS

- `Maison` a été déplacée avant `Vie courante`; l'ordre persiste après sortie et retour.
- Preuve: `qa/screenshots/step015-final-persistence.png`.

### Étape 016 — PASS

- `Essence` a été déplacée avant `Epicerie` dans `Vie courante`; l'ordre persiste après sortie et retour.
- Preuve: `qa/screenshots/step016-final-persistence.png`.

### Étape 017 — PASS

- Objectif mensuel de `300,00 $` sauvegardé sur `Epicerie`.
- Budget affiche `300,00 $ pour 26 novembre`; le solde de l'enveloppe reste `0,00 $`, donc aucun argent n'a été créé.
- Preuves: `qa/screenshots/step017-save-result.png`, `qa/screenshots/step017-budget-no-money.png`.

### Étape 018 — PASS

- Objectif bihebdomadaire de `80,00 $` sauvegardé sur `Essence`, jour lundi, départ le 26/10/2026.
- Budget affiche la prochaine échéance au 9 novembre et une suggestion de `40,00 $`; le solde reste `0,00 $`.
- Preuves: `qa/screenshots/step018-saved.png`, `qa/screenshots/step018-budget-verification.png`.

### Étape 019 — PASS

- Objectif annuel de `1 200,00 $` sauvegardé sur `Loyer`, à partir du 26/10/2026.
- Budget affiche l'échéance du 26 octobre 2027; le solde reste `0,00 $`.
- Preuve: `qa/screenshots/step019-annual-saved.png` et `qa/ui-dumps/step019-budget-verification.xml`.

### Étape 020 — PASS

- Objectif à échéance de `500,00 $` sauvegardé sur `Sorties`, du 26/10/2026 au 26/11/2026.
- Budget affiche la date future et le solde reste `0,00 $`.
- Preuves: `qa/screenshots/step020-deadline-saved.png`, `qa/screenshots/step020-budget-verification.png`.

### Étape 021 — PASS

- L'objectif mensuel `Epicerie` est passé de `300,00 $` à exactement `350,00 $`.
- Un seul objectif est visible; l'ancienne valeur ne réapparaît pas; le solde reste `0,00 $`.
- Preuves: `qa/screenshots/step021-objective-corrected.png`, `qa/screenshots/step021-budget-corrected.png`.

### Étape 022 — FAIL partiel / BUG QA-UX-001

- Résultat financier correct: l'objectif `Sorties` est supprimé, tandis que l'enveloppe `Sorties` et son solde `0,00 $` subsistent.
- Écart: aucune confirmation n'est présentée; toucher `Supprimer l'objectif` supprime immédiatement l'objectif.
- Preuves: `qa/screenshots/step022-confirm.png`, `qa/screenshots/step022-budget-after-delete.png`.

### Étape 023 — PASS

- `CHEQUE BLEU` créé avec solde initial et prêt à placer de `1 000,00 $`; couleur exacte `#2196F3`.
- Preuves: `qa/screenshots/step023-filled-blue-correct.png`, `qa/screenshots/step023-cheque-bleu-created.png`.

### Étape 024 — PASS

- `CHEQUE ROUGE` créé avec solde initial et prêt à placer de `600,00 $`; couleur exacte `#E91E63`.
- Preuves: `qa/screenshots/step024-filled-red.png`, `qa/screenshots/step024-cheque-rouge-created.png`.

### Étape 025 — PASS

- `CHEQUE VERT` créé avec solde initial et prêt à placer de `400,00 $`; couleur exacte `#4CAF50`.
- Preuves: `qa/screenshots/step025-filled-green.png`, `qa/screenshots/step025-cheque-vert-created.png`.

### Étape 026 — PASS

- `VISA VIOLET` créé comme carte de crédit Desjardins: limite `7 000,00 $`, solde `-300,00 $`, taux `19,99 %`, couleur exacte `#9C27B0`.
- Preuves: `qa/screenshots/step026-filled-violet.png`, `qa/screenshots/step026-visa-violet-created.png`.

### Étape 027 — FAIL partiel / BUG QA-UI-002

- `DETTE ORANGE` est créée avec le montant attendu de `-500,00 $` et une progression de remboursement à `0 %` sur `500,00 $`.
- Écart: le formulaire Dette ne propose aucun sélecteur de couleur et la fiche force le rouge; la couleur orange `#FF9800` demandée est impossible.
- Preuves: `qa/screenshots/step027-debt-form.png`, `qa/screenshots/step027-dette-orange-created.png`, `qa/ui-dumps/step027-dette-orange-created.xml`.

### Étape 028 — PASS

- `PLACEMENT CYAN` créé comme investissement distinct, solde `700,00 $`, couleur exacte `#00BCD4`, type de placement laissé à `Non précisé` puisque le guide n'en impose aucun.
- Preuves: `qa/screenshots/step028-filled-cyan.png`, `qa/screenshots/step028-placement-cyan-created.png`.

### Étape 029 — FAIL / BUG QA-UI-002

- Les six fiches ont été ouvertes successivement et l'application a été fermée/réouverte; les comptes et leurs couleurs persistent.
- Bleu, rose-rouge, vert, violet et cyan restent distincts, mais `DETTE ORANGE` reste rouge. Les six couleurs exigées ne peuvent donc pas être obtenues.
- Preuves: `qa/screenshots/step029-cheque-bleu-open.png`, `qa/screenshots/step029-cheque-rouge-open.png`, `qa/screenshots/step029-cheque-vert-open.png`, `qa/screenshots/step029-visa-violet-open.png`, `qa/screenshots/step029-dette-orange-open.png`, `qa/screenshots/step029-placement-cyan-open.png`, `qa/screenshots/step029-after-reopen-all-types.png`.

### Étape 030 — PASS

- `FAUX COMPTE`, 55 $ et couleur jaune ont été saisis, puis la création a été annulée; le compte est absent de la liste.
- Preuves: `qa/screenshots/step030-filled-before-cancel.png`, `qa/screenshots/step030-after-cancel.png`.

### Étape 031 — FAIL partiel / BUG QA-UX-003

- Le compte chèque sans nom et avec `123,00 $` n'est pas enregistrable: le bouton Sauvegarder reste désactivé et aucun compte parasite n'est créé.
- Écart: aucune erreur utile n'explique que le nom est obligatoire, même après tentative de toucher Sauvegarder.
- Preuves: `qa/screenshots/step031-no-name-filled.png`, `qa/screenshots/step031-save-attempt.png`, `qa/screenshots/step031-after-refusal.png`.

### Étape 032 — PASS

- `CHEQUE VERT` renommé `CHEQUE VERT EPARGNE`; solde et prêt à placer restent chacun à `400,00 $`, couleur verte inchangée.
- Preuves: `qa/screenshots/step032-name-edited.png`, `qa/screenshots/step032-renamed-saved.png`.

### Étape 033 — PASS

- Seule la couleur de `CHEQUE VERT EPARGNE` est passée à `#FFEB3B`; nom et montants restent inchangés, les autres comptes gardent leurs couleurs.
- Preuves: `qa/screenshots/step033-yellow-selected.png`, `qa/screenshots/step033-yellow-saved.png`.

### Étape 034 — PASS

- `CHEQUE ROUGE` déplacé devant `CHEQUE BLEU`; l'ordre persiste après navigation vers Budget et retour, sans altérer soldes, noms ni couleurs.
- Preuves: `qa/screenshots/step034-red-before-blue.png`, `qa/screenshots/step034-order-persisted.png`.

### Étape 035 — PASS

- Le groupe Comptes chèques se replie puis se redéplie; les autres familles restent visibles et l'ordre personnalisé est conservé.
- Preuves: `qa/screenshots/step035-cheques-collapsed.png`, `qa/screenshots/step035-cheques-expanded.png`.

### Étape 036 — PASS

- Budget montre trois sources de prêt à placer distinctes: BLEU `1 000,00 $`, ROUGE `600,00 $`, VERT ÉPARGNE `400,00 $`; aucun total global n'est utilisé.
- Preuve: `qa/screenshots/step036-separate-ready-to-assign.png`.

### Étape 037 — PASS

- Virement de `150,00 $` du prêt à placer BLEU vers Epicerie exécuté.
- Résultat: Epicerie `150,00 $`, prêt à placer BLEU `850,00 $`, solde bancaire BLEU `1 000,00 $`; ROUGE et VERT inchangés.
- Preuves: `qa/screenshots/step037-ready-150.png`, `qa/screenshots/step037-epicerie-150.png`, `qa/screenshots/step037-account-balance-unchanged.png`.

### Étape 038 — PASS

- Ajout de `50,00 $` encore depuis BLEU vers Epicerie.
- Résultat: Epicerie `200,00 $`, prêt à placer BLEU `800,00 $`, solde bancaire BLEU inchangé.
- Preuves: `qa/screenshots/step038-ready-50-correct.png`, `qa/screenshots/step038-after-transfer.png`, `qa/screenshots/step038-blue-800.png`.

### Étape 039 — PASS

- En choisissant ROUGE comme source, Epicerie financée par BLEU est désactivée dans le sélecteur; le virement de 50 $ ne peut pas être soumis.
- Les valeurs restent Epicerie `200,00 $`, BLEU `800,00 $`, ROUGE `600,00 $`.
- Preuves: `qa/screenshots/step039-epicerie-selected.png`, `qa/screenshots/step039-after-epicerie-tap.png`.

### Étape 040 — PASS

- Avec Epicerie comme source et `30,00 $`, ROUGE et VERT sont désactivés comme destinations avec le message `L'argent vient de CHEQUE BLEU`; BLEU reste sélectionnable.
- Aucun mauvais retour n'est enregistré.
- Preuves: `qa/screenshots/step040-destination-sheet.png`, `qa/screenshots/step040-red-refused-30.png`.

### Étape 041 — PASS

- Retour de `30,00 $` d'Epicerie au prêt à placer BLEU exécuté.
- Résultat: Epicerie `170,00 $`, prêt à placer BLEU `830,00 $`; solde bancaire BLEU reste `1 000,00 $`.
- Preuves: `qa/screenshots/step041-ready-return-30.png`, `qa/screenshots/step041-blue-830.png`, `qa/screenshots/step041-epicerie-170.png`.

### Étape 042 — PASS

- `100,00 $` financés depuis ROUGE vers Essence.
- Résultat: Essence `100,00 $`, prêt à placer ROUGE `500,00 $`; solde bancaire ROUGE reste `600,00 $`.
- Preuves: `qa/screenshots/step042-ready-100.png`, `qa/screenshots/step042-essence-100.png`, `qa/screenshots/step042-red-500.png`.

### Étape 043 — PASS

- Avec Epicerie comme source et `20,00 $`, Essence est désactivée comme destination avec l'indication qu'elle contient l'argent de CHEQUE ROUGE.
- Epicerie reste `170,00 $` et Essence `100,00 $`; aucune somme n'est perdue.
- Preuve: `qa/screenshots/step043-essence-refused-20.png`.

### Étape 044 — PASS

- Loyer financé de `40,00 $` depuis BLEU, puis virement de `15,00 $` d'Epicerie vers Loyer.
- Résultat final: Epicerie `155,00 $`, Loyer `55,00 $`, Essence `100,00 $`; provenance BLEU conservée et prêt à placer BLEU `790,00 $`.
- Preuves: `qa/screenshots/step044-ready-loyer-40.png`, `qa/screenshots/step044-loyer-40-funded.png`, `qa/screenshots/step044-ready-epicerie-to-loyer-15.png`, `qa/screenshots/step044-final-balances.png`.

### Étape 045 — PASS

- Couple valide Epicerie vers Loyer sélectionné avec montant `0,00 $`.
- Le bouton `Effectuer le virement` reste désactivé et aucun solde ne change.
- Preuve: `qa/screenshots/step045-zero-refused.png`.

### Étape 046 — PASS

- Un montant négatif de `-20,00 $` a été saisi avec le clavier monétaire dans un virement Epicerie vers Loyer.
- Le bouton `Effectuer le virement` reste désactivé; aucun solde ne change.
- Preuve: `qa/screenshots/step046-negative-refused.png`.

### Étape 047 — PASS

- Les lettres (`ABC`) et une saisie à décimales abusives (`1.2345`) ont été tentées dans le montant.
- Le clavier monétaire les bloque; le montant reste `0,00 $`, le bouton demeure désactivé et rien n'est sauvegardé.
- Preuves: `qa/screenshots/step047-nonmonetary-keypad.png`, `qa/screenshots/step047-nonmonetary-refused.png`.

### Étape 048 — PASS

- Virement BLEU vers Loyer préparé à `33,00 $`, puis écran fermé avant `Effectuer le virement`.
- Retour Budget sans variation: prêt à placer BLEU `790,00 $`, Loyer `55,00 $`.
- Preuves: `qa/screenshots/step048-prepared-33.png`, `qa/screenshots/step048-cancelled-budget.png`.

### Étape 049 — FAIL / BUG QA-FIN-001

- Double appui rapide sur `Effectuer le virement` pour `5,00 $` de BLEU vers Loyer.
- Deux opérations ont été exécutées: deux insertions d'historique `Loyer - 5.00$` à 123 ms d'intervalle; le prêt à placer BLEU passe de `790,00 $` à `780,00 $` et Loyer de `55,00 $` à `65,00 $`.
- Attendu: une seule opération, BLEU `785,00 $` et Loyer `60,00 $`.
- Symptôme associé: Budget est devenu vide après le double appui et ne s'est réaffiché qu'après navigation vers Comptes puis retour.
- Preuves: `qa/screenshots/step049-ready-5.png`, `qa/screenshots/step049-after-double-tap.png`, `qa/screenshots/step049-accounts.png`, `qa/screenshots/step049-budget-return.png`.
