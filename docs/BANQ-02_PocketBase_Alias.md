# BANQ-02 — Installation de la collection PocketBase « alias_bancaires »

Le client Android enregistre les règles d'alias dans Room et crée des SyncJobs
`ALIAS_BANCAIRE`. La table des tiers existante reste l'unique référence
des marchands ; `tiers_id` en conserve l'identifiant.

## Déploiement

1. **Sauvegarder** le PocketBase réel (fichier `pb_data/data.db` et fichiers associés).
2. Copier `pb_migrations/1793188800_create_alias_bancaires.js` dans le
   répertoire `pb_migrations` du **serveur PocketBase actif**.
   Le dossier du dépôt Android n'est pas automatiquement celui du serveur.
3. Exécuter `./pocketbase migrate up` (ou redémarrer PocketBase, si
   automigrate est actif). En cas de `migrate up` manuel, redémarrer le serveur.
4. Contrôler dans l'administration la présence de `alias_bancaires` et ses cinq
   règles d'accès. Authentifier deux utilisateurs tests distincts : chacun ne doit
   voir ou modifier que ses règles.
5. Sur **environnement de test isolé** : créer un tiers, mémoriser `IRV 0421 QC`
   pour ce tiers, redémarrer le téléphone, synchroniser un second téléphone, puis
   vérifier que l'ID du tiers reste le même. Tester une autre ville et un autre compte.

Champs PocketBase : `utilisateur_id` (propriétaire), `compte_id`
(optionnel, vide pour règle générale autorisée), `libelle_brut` (libellé bancaire
inaltéré), `tiers_id` (ID du tiers existant), `active` (activation).
Le nom du tiers n'est **jamais** réécrit lors d'une reconnaissance.

Le client saute l'import initial si le serveur répond 404 pour cette collection,
sans bloquer le reste du budget. Toutefois une tâche de synchronisation d'alias
ne pourra PAS aboutir tant que la collection manque. Le système ne doit donc pas
être annoncé comme opérationnel sur les deux appareils avant ce déploiement et
les tests UI/Room/PocketBase.

**Ne pas déployer cette migration sur un serveur de production avant d'avoir
validé les droits, les sauvegardes et la compatibilité de sa version PocketBase.**
Les modules Dette et Investissement ne sont pas concernés.
