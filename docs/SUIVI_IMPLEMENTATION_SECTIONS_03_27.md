# Suivi d'implémentation — roadmap des pages 3 à 27

Référence : `ToutieBudget_Roadmap_Complete_Repartage.txt`.
Ce suivi concerne le **CODAGE**, non les tests Android E2E (reportés par décision de l'utilisateur).
**Règle : terminer les exigences retenues de la section courante avant d'attaquer la suivante.**
Le statut **codé** ne signifie pas **compilé** ou **validé sur Android**.

## Sections 3 à 7 — reprise séquentielle

| Section | IDs retenus | État du codage | Vérifications restantes |
|---|---|---|---|
| 3. Accueil / Budget | A2, A3, A4, A5, A7 | Code présent. A2 révisé avec transaction Room; filtres au cent, favoris synchronisés en série; aperçus protégés | Compilation + validation des écritures Room/SyncJobs |
| 4. Paramètres / Plan Reset | A8 / BUD-01 | Code présent. Transaction Room et prévisualisation multi-source sans confondre les comptes | Compilation + compatibilité allocation historique / rollover |
| 5. Correctifs Budget | C2, C3, C4 | Code présent : persistance locale/PocketBase, fermeture par succès propre à l'opération, dollars CAD | Compilation + intégration UI |
| 6. Prêt personnel | P3–P7, PP2, PP3, PP6 | Code présent. Erreurs des historiques et de l'archivage rendues explicites | Compilation + intégrité des remboursements multi-dossiers |
| 7. Virer argent | V1–V8, VA4–VA6 | Code présent. Transaction Room déjà injectée; journaux désormais obligatoires; gestion d'exception dans le ViewModel | Compilation + cas de provenance multi-allocation |

## Sections restantes

| Sections | État |
|---|---|
| 8 à 27 | Code antérieur présent pour plusieurs IDs, mais **audit exhaustif section par section non achevé**. Aucun ID n'est considéré comme vérifié par ce tableau. |

## Règles de suivi

- Ne jamais annoncer **terminé** tant que tous les IDs **[RETENU]** de la section n'ont pas été confrontés aux vrais fichiers Kotlin.
- Les éléments **[À DISCUTER]** et **refusés** ne sont pas à implémenter.
- Ne jamais utiliser les PASS du bot Android comme preuve de validité financière sans actions et assertions réelles.
- Aucun `git pull` demandé entre les corrections : livrer en un lot à la fin.
- Les changements sur GitHub ne prouvent pas une compilation; les erreurs Kotlin éventuelles restent à corriger avant livraison.
