# ToutieBudget Android — testeur longue durée

**Statut réel : moteur E2E exécutable, mais couverture exhaustive NON atteinte.** Il ne serait pas honnête de prétendre vérifier toutes les fonctionnalités et quatre années calendaires sans instrumenter l'application et valider les sélecteurs sur un Android connecté.

## Démarrer

1. Installer Python 3 et Android SDK Platform-Tools (`adb`). Démarrer un émulateur Android Google Play ou brancher un téléphone en débogage USB.
2. Installer ToutieBudget `com.xburnsx.toutiebudget`, puis ouvrir la session **d'un autre compte Google réservé aux tests**. Ne pas lancer sur les données personnelles.
3. Dans PowerShell : `powershell -ExecutionPolicy Bypass -File .\Run-Tests.ps1 -Mode all -Months 48 -Fuzz 2000`
4. Ouvrir `resultats/<date>/rapport.html`, examiner `rapport.json`, `events.jsonl`, les captures et `logcat` dans `evidence/`.

## Ce qui est réellement testé

- Commandes ADB Android de l'application réelle, détection de sélecteurs sémantiques via UI Automator.
- Inventaire de contrôles des zones de l'app identifiées dans le dépôt (Budget, Comptes, Catégories, Transactions, Virements, Settings, Stats, Dette, Crédit, Archives).
- Scénarios métier avec assertions **obligatoires** après création, persistance et modification lorsque le sélecteur existe et le résultat est observable. Vérification d'un montant dans l'UI (condition nécessaire, pas suffisante pour affirmer une écriture financière correcte).
- 48 passages mensuels demandant explicitement le contrôle UI « mois suivant ». Si contrôle absent, signale `BLOCKED` — **n'affirme pas que le temps a avancé**.
- 2 000 mutations déterministes de champs pour tester les cas limites; elles ne valident pas arbitrairement un écran en cas de contrôle manquant.
- Sauvegarde après chaque cas, relance possible, rapports de couverture par fonction, preuves des blocages/échecs, reproduction avec `Seed` identique.

## Limites qu'il faudra résoudre pour atteindre 100 %

- Jetpack Compose : les composants de l'app inspectés n'exposent pas `testTag` stable. Plusieurs champs/dialogues sont introuvables par XML ADB; ils se retrouveront sous `BLOCKED`, pas sous `PASS`.
- Un test `money` n'est qu'une assertion d'affichage. Pour valider chaque écriture financière, il faut identifier ses bons soldes/enveloppes et créer un modèle de référence transactionnel pour le scénario. **Le moteur livré n'est pas encore cet oracle.**
- Les tests de récurrence basés sur date système exigent une horloge injectable côté Kotlin. Tourner dans les mois visibles n'avance pas la date système, et changer la date Android perturberait l'authentification Google.
- Les sélecteurs UI du catalogue sont des hypothèses tant qu'ils ne sont pas vérifiés sur l'APK réel. Une bonne partie des scénarios peuvent légitimement finir `BLOCKED`.
- Le fuzz générique saisit sans valider la sauvegarde. Il ne s'agit pas d'un test complet de robustesse financière.

**Sécurité :** l'outil n'utilise que ADB, ne fait aucun appel HTTP, ne lit ni ne modifie directement PocketBase et ne supprime pas les données locales. L'app elle-même reste libre de synchroniser les actions normales du compte Google de test. Les actions de suppression massive/déconnexion sont protégées.

## Modules ajoutés

- `Explore-Android.ps1` / `explore.py` : exploration en largeur des contrôles libellés accessibles Android, exclusion des actions de déconnexion/réinitialisation/suppression, journal JSON de chaque écran unique. Il **découvre**, il ne valide **pas** les résultats financiers.
- `finance_oracle.py` : calcul indépendant en cents, conservation des sommes des virements, modification/annulation réversibles, aucune requête réseau.
- `test_oracle.py` : vérifications automatiques de l'oracle.
- `TEST_STATUS.md` : preuve de ce qui a réellement été analysé, exécuté et de ce qui manque.

Pour exécuter les vérifications de l'oracle : `py -3 -m unittest discover -p "test_*.py" -v`.
Pour explorer les écrans : `powershell -ExecutionPolicy Bypass -File .\Explore-Android.ps1 -Depth 6 -MaxActions 4000`.

**Ne pas prétendre que ces modules sont un test E2E complet de quatre années.** Les scénarios complets de ToutieBudget exigent encore un mapping des composants sur une instance réelle et une horloge métier injectable afin de déclencher les processus temporels. Aucun test Android réel n'a été exécuté dans l'environnement de création.
