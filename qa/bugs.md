# Anomalies confirmées — ToutieBudget 1.6.2 (build 41)

> Revalidation 1.6.3 build 42, commit `888d4dac7375bb84314229036718631c31a6c7f2`, le 26 octobre 2026: `QA-SEC-002` est toujours reproductible. Les listes sans authentification répondent HTTP 200 pour `users`, les quatre familles de comptes, `categories`, `enveloppes`, `allocations_mensuelles`, `transactions`, `tiers` et `user_preferences`.

Campagne: commit `1d5b399630865cfc332d671ce2c381d74fcb211d`, branche `release/1.6.1`.

## QA-SEC-001 — P0 — Le build QA contacte l’URL PocketBase de production

- Tests liés: `SEC-009`, laboratoire `00.01` et `00.09`.
- Précondition: APK debug construit depuis le dépôt, installation neuve sur émulateur isolé.
- Reproduction:
  1. Couper le réseau.
  2. Installer `app-debug.apk` directement avec ADB.
  3. Lancer `MainActivity`.
  4. Examiner le journal réseau expurgé.
- Attendu: une variante E2E distincte bloque toute adresse LIVE avant l’exécution.
- Observé: le client tente `https://pocketbase.toutieserver.com/api/health`.
- Impact: une campagne E2E pourrait lire ou modifier les données LIVE si elle poursuit avec un jeton valide.
- Preuve: `qa/logs/startup_clean_build.log`; URL codée dans `app/src/main/java/com/xburnsx/toutiebudget/di/UrlResolver.kt`.
- Contournement actuel: aucune opération financière E2E n’est comptée comme valide avant isolation du backend.

## QA-SEC-002 — P0 — Collections financières PocketBase lisibles sans authentification

- Test ajouté: `SEC-016`.
- Précondition: aucune session et aucun en-tête `Authorization`.
- Reproduction non destructive:
  1. Envoyer un GET avec `page=1`, `perPage=1` et `fields=id` vers les endpoints de liste.
  2. Vérifier uniquement le code HTTP et les métadonnées; ne pas conserver les IDs.
- Collections confirmées: `users`, `comptes_cheques`, `categories`, `enveloppes`, `transactions`.
- Attendu: HTTP 401 ou 403, sans enregistrement ni total.
- Observé: HTTP 200 pour les cinq collections et métadonnées de pagination accessibles.
- Impact: exposition potentielle de données d’identité et financières à un client non authentifié.
- Données protégées: aucun contenu métier ni ID retourné n’a été affiché ou conservé dans les artefacts QA.
- Action recommandée: corriger les règles PocketBase de liste/lecture par propriétaire, puis répéter `SEC-016`, `SEC-001` à `SEC-004` sur PocketBase TEST avant production.

## QA-AUTH-001 — P0 — Google Sign-In release échoue avec le code 10; configuration OAuth incohérente à départager avec l’APK du téléphone

- Test lié: préflight d’`AUTH-001` sur l’APK release signé.
- APK vérifié: `app-release.apk`, version `1.6.2` build `41`, installé directement par ADB.
- Package vérifié: `com.xburnsx.toutiebudget`.
- Certificat installé vérifié: SHA-1 `14:D6:F9:65:C3:5C:1F:33:6C:89:DD:74:89:B9:2E:93:8A:06:3C:C5`.
- Reproduction:
  1. Installer l’APK release signé sur l’émulateur propre.
  2. Ouvrir ToutieBudget et lancer Google Sign-In.
  3. Choisir le compte puis observer le retour à l’application.
- Attendu: Google renvoie un compte avec `serverAuthCode` et/ou `idToken`.
- Observé: `ApiException: 10` (`DEVELOPER_ERROR`) avant tout appel d’authentification PocketBase.
- Incohérence confirmée dans le build:
  - `google-services.json` et `default_web_client_id` utilisent le projet `toutiebudget-kotlin`, numéro `857272496129`;
  - `BuildConfig.GOOGLE_WEB_CLIENT_ID`, réellement utilisé par `LoginScreen`, pointe vers un autre projet, numéro `1078578579569`;
  - l’application ignore donc le `default_web_client_id` généré par Google Services.
- Observation supplémentaire: l’utilisateur indique que la connexion fonctionne sur son téléphone physique. Cela peut s’expliquer par une version/signature installée différente ou par une session Google déjà autorisée; le téléphone n’était pas visible en ADB au moment du contrôle, donc cette différence reste à mesurer.
- Impact: la connexion Google de l’APK release installé sur l’émulateur est bloquée. Il faut comparer l’APK réellement installé sur le téléphone avant d’attribuer définitivement le code 10 au seul écart de projet.
- Preuves: `app/build/generated/source/buildConfig/release/com/xburnsx/toutiebudget/BuildConfig.java`, `app/build/generated/res/processReleaseGoogleServices/values/values.xml`, `app/src/main/java/com/xburnsx/toutiebudget/ui/login/LoginScreen.kt`, journal `LoginScreen` à `2026-10-26 00:38:10`.
- Prochaine preuve requise: relever sur le téléphone la version, le hash de l’APK et le SHA-1 du certificat, puis les comparer à l’émulateur. Si les signatures sont identiques, vérifier que le client Android montré appartient au même projet que le client Web `1078578579569`; si elles diffèrent, enregistrer aussi le SHA du téléphone dans le projet OAuth voulu.

## QA-UX-001 — P2 — Suppression d'un objectif sans confirmation

- Version: 1.6.3 build 42, commit `888d4dac7375bb84314229036718631c31a6c7f2`.
- Test lié: étape 022 du guide final.
- Précondition: enveloppe `Sorties` avec objectif à échéance de 500 $.
- Reproduction:
  1. Ouvrir Catégories.
  2. Ouvrir le menu de `Sorties`.
  3. Toucher `Supprimer l'objectif`.
- Attendu: une confirmation permet d'annuler ou de confirmer la suppression.
- Observé: l'objectif est supprimé immédiatement, sans dialogue.
- Effets vérifiés: l'enveloppe `Sorties` subsiste et son solde reste `0,00 $`; aucun autre objectif n'est modifié.
- Preuves: `qa/screenshots/step022-confirm.png`, `qa/screenshots/step022-budget-after-delete.png`.

## QA-UI-002 — P1 — Les comptes Dette sont forcés rouges et ne peuvent pas conserver une couleur choisie

- Version: 1.6.3 build 42, commit `888d4dac7375bb84314229036718631c31a6c7f2`.
- Tests liés: étapes 027 et 029 du guide final.
- Précondition: créer les six comptes de référence avec des couleurs distinctes.
- Reproduction:
  1. Ouvrir `Comptes > Ajouter`.
  2. Choisir le type `Dette`.
  3. Chercher le sélecteur de couleur ou tenter de choisir `#FF9800`.
  4. Enregistrer `DETTE ORANGE` avec un montant de 500 $.
- Attendu: la dette utilise et conserve la couleur orange `#FF9800` comme demandé par le guide.
- Observé: aucun sélecteur de couleur n'est affiché pour le type Dette; la carte et son indicateur sont toujours rouges.
- Effets vérifiés: le montant est correctement stocké et affiché à `-500,00 $`; le défaut est limité à la couleur et empêche les six couleurs distinctes.
- Preuves: `qa/screenshots/step027-debt-form.png`, `qa/screenshots/step027-dette-orange-created.png`, `qa/screenshots/step029-after-reopen-all-types.png`.

## QA-UX-003 — P2 — Aucun message de validation pour un compte sans nom

- Version: 1.6.3 build 42, commit `888d4dac7375bb84314229036718631c31a6c7f2`.
- Test lié: étape 031 du guide final.
- Reproduction:
  1. Ouvrir `Comptes > Ajouter`.
  2. Laisser le nom vide et saisir un solde initial de 123 $.
  3. Tenter de sauvegarder.
- Attendu: aucun compte créé et une erreur utile indique que le nom est obligatoire.
- Observé: la création est correctement bloquée, mais Sauvegarder est simplement grisé; aucun message n'est affiché.
- Impact: l'utilisateur ne sait pas quel champ empêche la sauvegarde.
- Preuves: `qa/screenshots/step031-no-name-filled.png`, `qa/screenshots/step031-save-attempt.png`.

## QA-FIN-001 — P0 — Double appui exécute deux fois le même virement

- Version: 1.6.3 build 42, commit `888d4dac7375bb84314229036718631c31a6c7f2`.
- Test lié: étape 049 du guide final.
- Précondition: prêt à placer BLEU `790,00 $`, enveloppe Loyer `55,00 $`.
- Reproduction:
  1. Ouvrir `Budget > Virer argent`.
  2. Choisir BLEU comme source et Loyer comme destination.
  3. Saisir `5,00 $`.
  4. Toucher deux fois rapidement `Effectuer le virement`.
- Attendu: le bouton se verrouille dès le premier appui; une seule opération de 5 $ est créée; BLEU `785,00 $`, Loyer `60,00 $`.
- Observé: deux écritures de 5 $ sont enregistrées à 123 ms d'intervalle; BLEU devient `780,00 $` et Loyer `65,00 $`.
- Symptôme associé: l'écran Budget devient entièrement vide après le double appui; les données réapparaissent après navigation vers Comptes puis retour.
- Impact: mouvement financier dupliqué et soldes erronés sur une interaction humaine plausible.
- Preuves: `qa/screenshots/step049-ready-5.png`, `qa/screenshots/step049-after-double-tap.png`, `qa/screenshots/step049-accounts.png`, `qa/screenshots/step049-budget-return.png`; deux lignes `HISTORIQUE : Enregistrement modification allocation - Loyer - 5.00$` horodatées `16:52:37.893` et `16:52:38.016` dans logcat.
