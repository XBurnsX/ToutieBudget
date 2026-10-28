# ToutieBudget — BANQ-01/02/03/06, avancement (28 octobre 2026)

## État honnête

Le code de préparation est présent dans `main` ; **aucune compilation Android/E2E/Room/PocketBase
n'a encore été validée pour cette série de commits**. Les exigences BANQ-01, BANQ-02,
BANQ-03, BANQ-04 et BANQ-06 restent **ROUGES**, car le parcours de confirmation
financière complet et le jumelage explicite ne sont pas terminés.

Les écrans **Dettes** et **Investissements**, ainsi que leurs services et leurs
tests spécifiques, sont exclus des changements. Les migrations Room sont additives
et préservent les tables existantes.

## Parcours effectivement codé

1. Dans **Paramètres > Alias bancaires**, ajouter ou modifier une règle qui
   associe le libellé BRUT (ex. `IRV 0421 QC`) à l'ID d'un **Tiers existant**.
   La règle spécifique au compte prime sur la règle globale expressément autorisée.
2. Tester la reconnaissance avec un compte et un libellé. La simple reconnaissance
   ne crée aucune transaction et ne modifie aucune enveloppe.
3. Dans **Paramètres > Transactions à confirmer**, choisir un compte chèque ou carte.
   Importer un CSV choisi par l'utilisateur via le sélecteur de fichiers Android.
4. Les lignes sont mises dans la table `operations_bancaires_attente`, avec identifiant
   externe stable pour un même CSV. **Aucun débit ou revenu n'est écrit**.
   Un import répété ne crée pas un deuxième enregistrement pour la même clé externe.
5. La boîte affiche les libellés bancaires d'origine, les montants et les tiers
   suggérés. Le **SelecteurTiers** EXISTANT permet de choisir un tiers ou d'en
   créer un avec le flux de l'application. L'option **Mémoriser pour les prochaines fois**
   est facultative et distincte de l'affectation à l'opération.
6. Une opération peut être ignorée, puis remise en attente. Une opération
   déjà ignorée ne doit pas réapparaître à cause d'un import identique.
7. Les règles d'alias et les opérations en attente sont équipées de SyncJobs et
   de collections PocketBase optionnelles. **Cela ne garantit pas encore
   une synchro réelle tant que le serveur n'a pas été migré et testé.**

## Format CSV sûr (repli ; pas une promesse de compatibilité universelle)

- Encodage UTF-8, virgule ou point-virgule, guillemets RFC4180.
- Colonnes obligatoires : `date`, `amount` ou `montant`, `description` ou `libellé`.
- Colonnes facultatives : `id`, `status`, `currency` (CAD seulement).
- Montant **signé** : `-5.00` = dépense de 5,00 $ ; `5.00` = entrée de 5,00 $.
  Aucune conversion USD/CAD implicite. Un ID de ligne de CSV est généré si absent,
  mais des exports différents qui se chevauchent peuvent produire deux opérations
  en attente : aucun débit n'est créé, et elles devront être examinées.
- Date ISO `2026-10-28` ; les dates jj/mm vs mm/jj ambiguës sont refusées.
- Limite : 5 Mo, 10 000 opérations par fichier.

## Déploiement PocketBase

Avant de faire une installation de production :
1. Sauvegarder l'instance et les collections existantes.
2. Vérifier la version PocketBase, les accès utilisateurs et les droits du serveur.
3. Installer **sur le serveur réel** les migrations :
   - `pb_migrations/1793188800_create_alias_bancaires.js`
   - `pb_migrations/1793188801_create_operations_bancaires_attente.js`
4. Sur un serveur de TEST séparé, contrôler création/lecture/mise à jour/suppression
   autorisées pour le propriétaire et interdites pour un autre utilisateur.
5. Tester deux téléphones, hors ligne → reconnecté, import répété et mise à jour
   attente → finalisé, puis Room/PocketBase et absence de double débit.

La présence des fichiers `pb_migrations` dans le dépôt **ne les exécute pas
sur PocketBase**. Les véritables flux Plaid/Wealthsimple et la validation bancaire
réelle ne sont pas encore raccordés.

## Travail restant (ne PAS colorer en vert)

- Confirmation du compte/enveloppe/fractions **depuis l'import** avec réutilisation
  des vrais formulaires et écritures atomiques existantes.
- Propositions de jumelage avec de vraies transactions déjà saisies, et réponse
  explicite Oui/Non/Plus tard sans jamais débiter deux fois.
- Rapprochement des opérations pending/posted quand les identifiants changent.
- Traitement des paiements Visa / virements internes / avoirs au bon registre.
- Tests Android instrumentés 100 % de l'inventaire de cette section, puis
  régressions et déploiement PocketBase sur un serveur isolé.
