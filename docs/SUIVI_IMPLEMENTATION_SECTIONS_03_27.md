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
| 6. Prêt personnel | P3–P7, PP2, PP3, PP6 | **Codage traité** : types prêt/emprunt et « Archivés », détail stable et erreurs visibles, archiver/réactiver sans changer le solde, remboursement affiché dans détails, remboursements multi-dossiers validés avant écriture et sous transaction Room, journal inclus | Build Gradle / vérification Room non exécutés |
| 7. Virer argent | V1–V8, VA4–VA6 | **Codage traité** : mois choisi, allocations par ID/période, solde bancaire intact, transaction Room et journal obligatoires, sélecteurs qui se ferment, erreur/reprise, allocations distinctes pour nouvelle provenance, déficit volontaire conservé, récents/compatibilité/seuil de confirmation | Build et intégration sur appareil non exécutés |

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

## Section 6 — Revue de codage clôturée (P3–P7, PP2, PP3, PP6)

- **P3/P7** — Onglet « Archivés », vraies distinctions Prêt/Emprunt avec libellés de montant et couleurs selon le dossier.
- **P4** — État de chargement vidé lors du changement de dossier; réponses anciennes ignorées; détail maintenu lors d'un archivage même si l'onglet se vide; erreur affichée au lieu d'un spinner infini.
- **P5** — Les mouvements de compte, dossiers et transactions de remboursement sont enregistrés dans une opération Room atomique, avec la trace de remboursement dans la même transaction. Pas de réussite si la journalisation échoue.
- **P6** — Solde total restant de plusieurs dossiers d'un même tiers vérifié avant tout débit; revalidation de chaque solde dans la transaction Room; pas de remboursement excédentaire ni double comptage silencieux.
- **PP2/PP3** — Archivage manuel sur confirmation même si solde non nul; réactivation depuis les détails sans créer de transaction.
- **PP6** — Initial, remboursé et restant affichés dans Détails; transactions retrouvées par ID JSON exact, sans rapprochement par sous-chaîne.

**État : codage traité; compilation et E2E non exécutés.** Section 7 suivante.

## Section 7 — Revue de codage clôturée (V1–V8, VA4–VA6)

- **V1/V2** — Virements d'enveloppes sur mois sélectionné; recherche allocation par ID d'enveloppe et période, sans fusion destructive de lignes d'origines distinctes.
- **V3/V4** — Transferts internes dans une transaction Room avec journal obligatoire, sans modifier le solde bancaire; « Prêt à placer » par compte.
- **V5/V6** — Même sélecteur retouché = fermeture, clic extérieur = fermeture; erreurs visibles et verrou libéré en cas d'exception.
- **V7/V8** — Provenance obligatoire pour les sorties; ancien historique préservé via une allocation distincte lors d'un changement de compte; virement d'enveloppe autorisé même si la source passe négative, pour autant que son compte d'origine soit identifiable.
- **VA4–VA6** — Récents, compatibilité des destinations et confirmation facultative des gros virements déjà intégrés et conservés.

**État : codage traité; compilation et scénarios Android non exécutés.** Section 8 suivante.

## Section 8 — Comptes

| IDs | État du codage | Modifications de la reprise |
|---|---|---|
| CO1–CO9, CA3–CA5 | **Codage traité** : archivage non destructeur et confirmation, comptes archivés séparés, collection investissement harmonisée, réconciliation solde + prêt à placer sous transaction Room, champs manuels indépendants et journalisés atomiquement, rafraîchissements anti-réponses périmées, groupes/masquage zéro/aperçu | Build Gradle et vérification Android non réalisés |

### Revue de codage section 8 clôturée

- **CO1/CO2/CO7** : archivage confirmé, sans effacer compte ou historique; enveloppe d'une dette terminée archivée seulement quand elle ne contient plus de fonds; modifications ciblées.
- **CO3/CO8** : les listes des comptes actifs et archivés restent distinctes; requêtes périmées ignorées; rafraîchissements pilotés par événements, sans polling fixe de deux secondes.
- **CO4** : collection Room `comptes_investissement` vérifiée et aucune référence `comptes_investissements` détectée dans les fichiers indexés.
- **CO5** : relecture du compte et calcul de l'écart dans la même transaction Room que la correction et son journal (un seul ajustement).
- **CO6/CO9** : modifications manuelles du solde et du prêt à placer indépendantes; sauvegarde et journal atomiques; valeurs invalides refusées; formulaire conservé en échec.
- **CA3/CA4/CA5** : groupes repliables enregistrés, réglage Masquer les comptes à zéro relu depuis Paramètres, aperçu du solde réel et de l'écart avant confirmation.

**État : exigences codées et reprises. Pas de build ni de validation Android.**

## Section 10 — Cartes de crédit : CODAGE TRAITÉ (CC1–CC11)

- **CC1/CC3** — Calculs centralisés, solde signé, disponible non plafonné, montant minimum distinct du solde du relevé; corrections manuelles et journal dans une même transaction Room. Refus de NaN/Infinity.
- **CC2** — Paiement réel uniquement depuis Transactions, avec sorties/entrées liées, ajustements d'enveloppes, comptes et historiques dans une transaction Room. Le nom d'une personne ou d'un frais seul ne suffit pas à établir un lien avec une dette.
- **CC4** — Dates contractuelles de clôture et d'échéance, relevé officiel distinct de l'estimation; dates civiles et contrôle des échéances.
- **CC5** — Classifications explicites par ID (achat/avance/transfert), mises en file à la sauvegarde; ventilation du solde et intérêts futurs par taux réel déclaré, même en présence de plusieurs taux. Reconstruction du cycle basée sur le dernier relevé et vérification de rapprochement; calcul indisponible plutôt que faux chiffre lorsque les opérations, les règles de grâce ou l'origine des fonds sont insuffisantes. Taux promo pris selon dates et calendrier civil.
- **CC6** — Saisie et import volontaire de relevés CSV structurés, aperçu avant import, dédoublonnage par date, montants au cent; intérêts/frais officiels comptabilisés uniquement sur confirmation. Possibilité de marquer « déjà inclus », évitant de débiter deux fois.
- **CC7** — Minimum officiel prioritaire; estimation configurable à partir du solde de relevé et du profil (5 % de référence pour Desjardins/Québec). Montants manuels et estimations jamais présentés comme bancaires confirmés.
- **CC8** — Échéancier mois par mois, frais datés, minimum recalculé, taux multiples avec ventilation déclarée et hypothèse d'affectation du capital affichée. Stratégies fixe, pourcentage, minimum et date cible; protection contre les chiffres impossibles.
- **CC9** — Alertes fondées sur dates et mouvements enregistrés; échéance confirmée requise pour signaler un retard, paiement le jour de l'échéance reconnu et calculs indépendants du changement d'heure.
- **CC10** — Frais mensuels/annuels/ponctuels datés, sans facturation fictive; sauvegarde atomique qui ne remplace pas silencieusement des données concurrentes. Taux achats, avances, transferts, promotion datée et retard.
- **CC11** — Historique par relevé, achats/paiements locaux comparés aux montants officiels du cycle, intérêts réels versus estimations et cumul annuel; simulations accélérées versus minimum et tableau détaillé.

**Limites assumées du modèle** : un CSV structuré ne prouve pas qu'une opération a été reçue par la banque; les règles exactes de délai de grâce, d'imputation d'un paiement multi-taux, les contrats de financement et les relevés manquants ne peuvent pas être déduits automatiquement. Les simulations indiquent leurs hypothèses ou se déclarent indisponibles. Aucune écriture bancaire automatique n'a été ajoutée.

**État** : codage des exigences couvert, sans compilation Android, sans exécution des tests et sans validation sur de vrais relevés/Room/PocketBase. Section 11 suivante; ne pas interpréter ce statut comme « zéro bug garanti ».

## Sections restantes

| Sections | État |
|---|---|
| 9 | BUD-02 **À DISCUTER** — ne pas coder sans approbation. |
| 11 à 27 | Code antérieur présent pour plusieurs IDs, mais **audit exhaustif section par section non achevé**. Aucun ID n'est considéré comme vérifié par ce tableau. |

## Règles de suivi

- Ne jamais annoncer **terminé** tant que tous les IDs **[RETENU]** de la section n'ont pas été confrontés aux vrais fichiers Kotlin.
- Les éléments **[À DISCUTER]** et **refusés** ne sont pas à implémenter.
- Ne jamais utiliser les PASS du bot Android comme preuve de validité financière sans actions et assertions réelles.
- Aucun `git pull` demandé entre les corrections : livrer en un lot à la fin.
- Les changements sur GitHub ne prouvent pas une compilation; les erreurs Kotlin éventuelles restent à corriger avant livraison.
