# Suivi d'implémentation — roadmap des pages 3 à 27

Référence : `ToutieBudget_Roadmap_Complete_Repartage.txt`.
Ce suivi concerne le **CODAGE**, non les tests Android E2E (reportés par décision de l'utilisateur).
**Règle : terminer les exigences retenues de la section courante avant d'attaquer la suivante.**
Le statut **codé** ne signifie pas **compilé** ou **validé sur Android**.

## Reprise actuelle : SECTION 3 EXCLUSIVEMENT

**Périmètre des exigences retenues : A2, A3, A4, A5, A7.**
Revue des parcours Kotlin et correctifs de codage poussés, **sans compilation ni validation Android**.
Les sections 4 et suivantes ne sont pas reprises tant que la section 3 n'est pas considérée comme livrable.

| ID | Fonctions et fichiers confrontés | Modifications / points vérifiés | Statut |
|---|---|---|---|
| A2 | `BudgetScreen`, `RepartitionMultipleDialog`, `BudgetViewModel`, `ValidationProvenanceService`, `AllocationMensuelleRepositoryRoomImpl`, `OperationAtomiqueRoom` | Sélection source, aperçu, plusieurs enveloppes; total en centimes; compte et enveloppe relus sous transaction Room; journal dans transaction; erreur = rollback; interdiction de fusion destructrice ou de mélange de provenances; montant et champs financiers finis | **Codage revu, compilation non confirmée** |
| A3 | `BudgetScreen.FiltreEnveloppes` | Négatives, sans fonds, avec objectif, à financer, solde disponible, favorites; seuil au cent; filtres visibles seulement à la demande, catégorie d'origine conservée | **Codage revu, compilation non confirmée** |
| A4 | `BudgetScreen`, `BudgetViewModel.basculerFavori`, `PreferenceRepositoryImpl`, synchronisation des préférences | Favoris en tête sans réordonner les catégories; écritures locales, envoi PocketBase différé; sérialisation et protection contre réponse serveur périmée | **Codage revu, compilation non confirmée** |
| A5 | `BudgetScreen` | Recherche insensible à la casse par nom d'enveloppe, contrôle discret ouvrable et fermeture avec remise à zéro | **Codage revu, compilation non confirmée** |
| A7 | `BudgetScreen`, `PrevisionsDialog` | À la demande seulement; échéance mensuelle/fin de mois/date choisie; dates strictes, filtre des échéances et somme des versements recommandés | **Codage revu, compilation non confirmée** |

### Dernière révision exclusive de la section 3 (27 octobre)

- **A2** : propagation des erreurs Room, vérification de la provenance de toutes les allocations positives (pas seulement du compte dominant), transaction Room pour toutes les écritures et le journal, blocage des chiffres non finis et des centimes supplémentaires, prévention des dépassements et des clics concurrents. Les lignes historiques épuisées **ne sont plus fusionnées ou supprimées** et une nouvelle provenance financière crée une allocation distincte sans réaffecter l'ancienne. Les totaux impossibles affichent « — », pas un faux 0 $.
- **A3** : filtres et bannière des enveloppes négatives cohérents à la précision du cent; libellé Favoris en français.
- **A4** : la synchronisation des favoris est sérialisée, avec garde contre les réponses PocketBase tardives et compteurs partagés entre coroutines.
- **A5** : la recherche par nom est insensible à la casse et aux accents.
- **A7** : les prévisions restent à la demande; interdiction d'afficher des chiffres en cas de suggestions invalides; dates limitées au mois sélectionné et conversion correcte du jour civil pour le DatePicker Android.
- **Chargements communs** : le mois demandé est actif dès la demande, l'état de chargement est immédiat et les résultats d'anciennes requêtes ne remplacent plus les données du nouveau mois.

**Validation encore non fournie** : compilation complète Gradle/Android et comportement transactionnel réel sur Room/PocketBase. Aucun « PASS » E2E n'a été annoncé.

**Limite explicite :** cette revue et les commits ne démontrent pas un build Gradle réussi ni le comportement Room/PocketBase sur appareil. Ne pas afficher « 100 % vérifié » sans ces preuves.

## Sections 3 à 7 — reprise séquentielle

| Section | IDs retenus | État du codage | Vérifications restantes |
|---|---|---|---|
| 3. Accueil / Budget | A2, A3, A4, A5, A7 | Code présent. A2 révisé avec transaction Room; filtres au cent, favoris synchronisés en série; aperçus protégés | Compilation + validation des écritures Room/SyncJobs |
| 4. Paramètres / Plan Reset | A8 / BUD-01 | **Codage traité** : action volontaire dans Paramètres, aperçu par allocation/compte, confirmation explicite, sélection par ID, détection d'aperçu périmé, restauration Room atomique, aucune suppression de transaction ni modification des autres mois | Compilation Gradle et vérification Room/PocketBase sur appareil non exécutées |
| 5. Correctifs Budget | C2, C3, C4 | **Codage traité** : C2 préférences locales/priorité au dernier clic; C3 réussite liée à un identifiant de requête unique; C4 affichage MoneyFormatter $ CA sur écran soldes négatifs | Build Android / UI non vérifiés |
| 6. Prêt personnel | P3–P7, PP2, PP3, PP6 | Code présent. Erreurs des historiques et de l'archivage rendues explicites | Compilation + intégrité des remboursements multi-dossiers |
| 7. Virer argent | V1–V8, VA4–VA6 | Code présent. Transaction Room déjà injectée; journaux désormais obligatoires; gestion d'exception dans le ViewModel | Compilation + cas de provenance multi-allocation |

## Section 4 — Revue de codage clôturée (A8 / BUD-01)

- Fonction accessible dans Paramètres, sans reset automatique.
- Aperçu réel par allocation avec provenance **par compte** et choix de chaque ligne; les homonymes de comptes et les multiples allocations ne sont plus fusionnés par nom.
- Au moment de confirmer, vérification sous la même transaction Room : ID, compte d'origine, solde, alloué, dépensé et montant de chaque ligne. Si une donnée a changé, aucune opération n'est validée.
- Le remboursement retourne uniquement la portion assignée **et non dépensée**, plafonnée à l'alloué et au solde restant; aucune opération n'altère un autre mois, ni les transactions, ni le report.
- Comptes archivés et provenances inconnues signalés et laissés intacts.
- Erreur visible avec actualisation de l'aperçu; succès explicite; les SyncJobs sont créés avec les écritures Room.

**État : codage A8 traité; build et vérification d'exécution non réalisés.** La section 5 est la suivante pour le codage.

## Section 5 — Revue de codage clôturée (C2, C3, C4)

- **C2** — État ouvert/fermé dans préférences locales par utilisateur; envoi PocketBase différé hors ligne; pendant le chargement Room ou une réponse serveur tardive, le dernier clic reste prioritaire.
- **C3** — Un rafraîchissement du budget n'a aucun effet sur le clavier. Une réussite d'assignation porte maintenant l'ID **unique** de la requête; elle ne ferme plus un nouveau clavier ouvert sur la même enveloppe. Sur erreur, les champs restent présents.
- **C4** — Écran de gestion des soldes négatifs vérifié : valeurs en dollars canadiens via `MoneyFormatter.formatAmount`, pas de symbole euro.

**État : codage C2–C4 traité; build et validation Android non exécutés.** Section 6 suivante.

## Section 8 — Comptes

| IDs | État du codage | Modifications de la reprise |
|---|---|---|
| CO1–CO9, CA3–CA5 | Fonctionnalités présentes dans les écrans/moteurs; audit de cohérence toujours ouvert | CO5 : réconciliation sur solde relu, écritures Room et journal dans la même transaction; CO1/CO6 : archivage dette/enveloppe atomique et erreurs des repositories remontées |

## Sections restantes

| Sections | État |
|---|---|
| 9 | BUD-02 **À DISCUTER** — ne pas coder sans approbation. |
| 10 à 27 | Code antérieur présent pour plusieurs IDs, mais **audit exhaustif section par section non achevé**. Aucun ID n'est considéré comme vérifié par ce tableau. |

## Règles de suivi

- Ne jamais annoncer **terminé** tant que tous les IDs **[RETENU]** de la section n'ont pas été confrontés aux vrais fichiers Kotlin.
- Les éléments **[À DISCUTER]** et **refusés** ne sont pas à implémenter.
- Ne jamais utiliser les PASS du bot Android comme preuve de validité financière sans actions et assertions réelles.
- Aucun `git pull` demandé entre les corrections : livrer en un lot à la fin.
- Les changements sur GitHub ne prouvent pas une compilation; les erreurs Kotlin éventuelles restent à corriger avant livraison.
