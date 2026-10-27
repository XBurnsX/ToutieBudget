# Suivi d'implémentation — roadmap des pages 3 à 27

Référence : `ToutieBudget_Roadmap_Complete_Repartage.txt`.
Ce suivi concerne le **CODAGE**, non les tests Android E2E (reportés par décision de l'utilisateur).
**Règle : terminer les exigences retenues de la section courante avant d'attaquer la suivante.**
Le statut **codé** ne signifie pas **compilé** ou **validé sur Android**.

## Historique : SECTION 3 (A2, A3, A4, A5, A7)

Revue des parcours Kotlin et correctifs de codage précédemment poussés, **sans compilation ni validation Android**.
**Reprise actuelle : SECTION 13, Ajouter transaction (AT1–AT21, AA1–AA7), EN COURS.**
Les sections suivantes ne seront pas abordées avant la clôture de la section 13.

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

## Section 11 — Dettes : CODAGE REVU (DF1–DF15, DA1–DA7)

| Exigence | Travail présent dans le code |
|---|---|
| DF1 | Brouillon indépendant et sauvegarde unique; retour sans écriture et confirmation si changements |
| DF2 | Solde `soldeDette` conservé indépendamment du montant initial, taux, durée et simulation |
| DF3 | Paiements par date/ID/montant et équivalent mensuel clairement distingué du compteur brut |
| DF4 | Moteur Dette unique, sans valeur de taux ou durée inventée |
| DF5 | Dates réelles, échéance et fin de mois 29–31 selon contrat |
| DF6 | Montants au cent, taux finis (précision distincte), limites de durée, grands nombres rejetés |
| DF7 | Intérêts facturés distingués des intérêts futurs; intérêts payés marqués indisponibles si décomposition absente |
| DF8 | Contrôle du solde restant relu dans la transaction Room; maximum dû et bouton « Payer le solde » dans Transactions |
| DF9 | Débit/crédit sous ID partagé, suppression d'une contrepartie unique seulement, restauration des soldes/allocations/archives et refus de la modification isolée |
| DF10 | Paiement, ajustement, journal et SyncJobs sous transaction Room; les erreurs ne sont pas cachées |
| DF11 | Création compte dette + catégorie + enveloppe + lien par ID dans un seul commit Room; migration legacy uniquement par homonyme UNIQUE de la bonne catégorie/propriétaire |
| DF12 | Archivage non destructeur; toutes périodes/provenances vérifiées avant fermeture de l'enveloppe; catégorie Dettes sans enveloppe active masquée en Budget |
| DF13 | Rechargement réel, erreurs de lecture et d'écriture visibles, formulaire d'ajustement conservé en échec |
| DF14 | Date choisie dans Transactions utilisée sur les deux côtés du paiement et le mois d'allocation |
| DF15 | Configuration relue sous transaction Room, conflits détectés, version courante du solde conservée |
| DA1–DA7 | Deux modes de dette, échéanciers/simulations, échéance future, ajustement daté atomique, capital/intérêts clairement distingués et taux variable daté sans réécriture du passé |

**Statut : codage revu.** Compilation, exécution Android, intégration PocketBase multi-appareil et tests E2E reportés. L'atomicité locale Room ne suffit pas, à elle seule, à prouver une résolution de conflits distante. Ne jamais marquer ces scénarios « PASS » sans exécution.

## Section 11 — Dettes : codage repris (DF1–DF15 / DA1–DA7)

| Exigences | Parcours et garde-fous de codage |
|---|---|
| DF1/DF2/DF15 | Brouillon enregistré sur une seule confirmation; les soldes réels et paiements d'une autre écriture ne sont pas écrasés, et une configuration devenue différente déclenche une erreur explicite. |
| DF3/DA3 | Les versements réels proviennent des transactions datées. Le compteur des mensualités couvertes est plafonné aux échéances exigibles, puis recalculé sur ajout/annulation d'un paiement lié. Statut à venir = payé/partiel/non payé. Notifications d'action sur échéance impayée **opt-in**, sans alerte de progression. |
| DF4/DF5/DF6/DF14 | Moteur central sans taux ou durée inventés, dates contractuelles persistées, saisies au cent, taux valides, échéances des 29–31 adaptées aux fins de mois, et mois de budget tiré de la date de paiement sélectionnée. |
| DF7/DA6 | Intérêts et frais confirmés tirés des ajustements; intérêts futurs prévisionnels séparés; intérêts « réellement payés » déclarés indisponibles sans ventilation officielle. |
| DF8 | Solde restant affiché dans Transactions avec action « Payer le solde »; contrôle visuel, validation de l'état du formulaire et nouvelle vérification en centimes sous transaction avant tout débit. |
| DF9/DF10 | Création de la sortie et de l'entrée d'un paiement avec UUID commun; annulation en paire seulement, compte/dette/allocations/dossier restaurés dans transaction Room. Ancienne paire ambiguë refusée; pas de compensations produisant des SyncJobs contraires après erreur. |
| DF11/DF12 | Lien dette-enveloppe conservé par ID, catégorie et utilisateur; aucun homonyme arbitraire. À extinction, archives non destructives et enveloppe gardée active tant qu'il existe des fonds ou déficits, même dans un autre mois. |
| DF13 | Messages d'erreur et véritables relances de lecture, formulaire conservé si sauvegarde ou ajustement échouent. |
| DA1/DA2/DA4 | Dette simple et prêt financé distincts; échéancier et simulations à la demande, avec hypothèses explicites et calendrier contractuel, sans modifier le solde réel. |
| DA5 | Ajustement officiel daté et journalisé dans la même transaction Room, sans créer de faux paiement. |
| DA7 | Changements de taux datés et validés; taux anciens conservés sans les réappliquer quand l'option variable est désactivée. |

**État : codage de la section 11 repris et documenté; validation Gradle, intégration PocketBase et scénarios multi-appareils toujours non exécutés conformément aux instructions.** Ces tests pourraient révéler des modifications supplémentaires. La section suivante pour le codage est la 12.

## Section 12 — Investissements : codage repris (IF1–IF17 / IA1–IA10)

| Exigences | Revue et corrections dans le code |
|---|---|
| IF1/IF2/IF7 | Collection canonique `comptes_investissement`, valeur de marché/correction distinctes d'un revenu ou d'une dépense, valorisations datées avec ancienne valeur et journal Room. |
| IF3/IF4/IF8/IF9/IF12/IF13/IF14 | Apports/retraits représentés par virements liés et signés, deux soldes et deux mouvements sous une transaction Room; date choisie conservée. Montants invalides et total du patrimoine non finis refusés, sans fausses écritures de revenus. |
| IF5/IF6/IF11 | Repositories avec erreurs propagées, contrôles propriétaire/archives/liquidités disponibles et montant au cent; la valeur du portefeuille n'est pas assimilée à une encaisse retirable. |
| IF10/IA8 | Mécanisme BANQ commun conservé, sans prétendre que Plaid donne accès aux positions Wealthsimple ou qu'un import réel a été effectué. Rapprochements ambigus non validés automatiquement. |
| IF15/IF16/IF17 | Archivage/réactivation par même ID, comptes actifs distincts des archivés dans patrimoine courant; fermeture du formulaire seulement après écriture locale. Saisie de solde initial invalide refuse la création plutôt que d'inventer 0 $. |
| IA1–IA3 | Mode SIMPLE ou DÉTAILLÉ, types CELI/REER/CELIAPP, capital net versé avec valeur et date de départ connues, gains et performance distincts. Vente sans coût connu ≠ faux gain nul; flux du jour d'instantané et futurs ne sont pas comptés à tort. |
| IA4/IA5/IA6 | Courbe de points datés connus sans cours inventés, positions fractionnaires et devises, mouvements internes achats/ventes/dividendes/distributions/intérêts/frais. |
| IA7–IA10 | Type canadien comme métadonnée sans estimation fiscale; répartition uniquement si titres et encaisse rapprochent la valeur et sont convertibles; performances TWR ou TRI seulement avec données suffisantes, avec refus des rendements simplifiés trompeurs. |

**État : parcours et codage existant consolidés; build Android et validation réelle Room/PocketBase, banques et appareils non exécutés.** Les restrictions d'accès des connecteurs externes ne sont pas remplacées par une simulation de liaison bancaire.

## Section 12 — Investissements : CODAGE LOCAL REPRIS (IF1–IF17, IA1–IA10)

| IDs | Code revu / corrigé |
|---|---|
| IF1–IF3 | Collection canonique `comptes_investissement`; revalorisation différente d'un revenu; apports et retraits calculés sur les transferts réels depuis la date de départ |
| IF4–IF5 | Deux soldes + deux transactions liées + SyncJobs sous **une** transaction Room; aucune compensation qui créerait des écritures inverses; journal de valorisation obligatoire; formulaire maintenu en échec |
| IF6 | Suivi détaillé : encaisse et retrait autorisé distincts de la valeur; retrait limité aux espèces réellement retirables connues; dépôt n'est pas déclaré immédiatement retirable |
| IF7–IF9 | Valorisation datée et tracée, double sauvegarde bloquée, date de virement **saisissable** (jour exact) sur les deux transactions, ID de paire partagé |
| IF10, IA8 | **Dépendance externe non finalisée : BANQ-06 / fournisseur Wealthsimple-Plaid/MX autorisé.** Aucun accès positions ou import bancaire réel n'est affirmé. Maintien des entrées manuelles et mécanismes de confirmation à intégrer dès disponibilité du connecteur |
| IF11–IF13 | Comptes actifs du même propriétaire, montants au cent, balance des virements, positions à quantité fractionnaire et prix unitaires non tronqués; valeurs signées de portefeuille autorisées |
| IF14–IF17 | Protection Room contre écrasement concurrent des paramètres/valeurs, archivage et réactivation même ID; patrimoine courant n'inclut pas placements archivés; éditeurs fermés seulement sur succès |
| IA1–IA3 | Suivi simple/détaillé facultatif, capital net versé et gains distincts; manques de cours, coûts ou dates = « indisponible » |
| IA4–IA7 | Points de valeur datés, positions détaillées, achats/ventes/dividendes/frais internes, types CELI/REER/CELIAPP, sans présumer fiscalité |
| IA9–IA10 | Répartition des valeurs connues, blocage des comparaisons CAD/USD sans cours FX fiable; gains réalisés inconnus non convertis en 0 $; TWR et TRI sous hypothèses/documentation suffisantes |

**État : codage local repris; BANQ-06/IA8 restent conditionnés à l'accès d'un fournisseur bancaire.** Compilation Android, tests Room, multi-appareil et E2E reportés sur décision de l'utilisateur. Ce suivi ne prétend pas que la synchronisation Wealthsimple existe déjà.

## Section 13 — Ajouter transaction : REVUE DE CODAGE TRAITÉE (27 octobre 2026)

Référence : les **28 exigences** AT1–AT21 et AA1–AA7 du TXT maître.
Plusieurs corrections existaient déjà sur `main` avant cette reprise (HEAD initial : `6fb30b5`).
**Ce statut vise exclusivement la revue statique et le codage local.** Il ne signifie PAS que les 28 exigences ont réussi en Android, ni que le binaire compile, ni que les reprises après fermeture brutale ou conflits PocketBase ont été observées en conditions réelles. Les intégrations bancaires BANQ-01–06 restent reportées.

| IDs / chemin | Ce qui a été codé pendant cette reprise | Commit |
|---|---|---|
| AT4 / AT15, `EnregistrerTransactionUseCase` | Erreurs du journal principal ou fractionné propagées à la transaction Room; ne pas afficher le succès d'une écriture d'historique échouée | `26402c7` |
| AA5, `AjoutTransactionViewModel` | Pas de « Synchronisée » si aucun SyncJob nouveau ni preuve `COMPLETED`; conservation d'une confirmation de synchro déjà observée après purge | `afc8ede` |
| AT4 / AT12 / AT15, `AllocationMensuelleRepositoryRoomImpl` | Erreur de mise à jour/provenance non avalée; absence d'allocation = erreur plutôt que réussite silencieuse | `7a23280` |
| AT19 / AT20, `AjoutTransactionViewModel` | Relecture des comptes choisis; sélection supprimée si archivée/disparue; enveloppe retirée si non disponible | `479b0c2` |
| AT11 / AT12 / AT19, `EnregistrerTransactionUseCase` | Revalidation sous transaction Room du compte, enveloppe et financement de la dépense simple (alignement sur fractionnement) | `d0a53da` |
| AT9 / AT10 / AT11 / AT15, `EnregistrerTransactionUseCase` | Historique fractionné exact au cent, impossible de masquer une enveloppe introuvable, note individuelle propagée et échecs remontés | `70401eb` |
| AT4, `EnregistrerTransactionUseCase` | Événement de rafraîchissement du budget après commit, pas pendant transaction Room | `9e9efeb` |
| Tests de régression ajoutés (non exécutés) | Statut AA5, échec de journal AT15, provenance et archivage AT11/AT12/AT19, total de fractions discordant | `cf3d9e8`, `ef3e696`, `b5af8a3`, `80745a8`, `a2994ec` |

### Revue statique des IDs retenus (sans preuve E2E)

| ID | Parcours de codage revu |
|---|---|
| AT1 | Emprunt créé une fois, dossier/compte/prêt à placer liés, clé de brouillon stable |
| AT2 | Remboursement donné débite une fois, groupe FIFO et anti-rejeu |
| AT3 | Somme remboursable et vérification du dossier avant débit |
| AT4 | `OperationAtomiqueRoom`, transactions + SyncJobs; aucun retour inverse dans rollback Room |
| AT5 | Date choisie transmise au service paiement carte/dette et aux deux écritures |
| AT6 | Allocations chargées pour le mois sélectionné et génération de requête |
| AT7 | Invalidation des fractions après changement du contexte |
| AT8 | Débit unique et allocations individuelles, carte de crédit cohérente |
| AT9 | JSON `enveloppe_id` et ancienne clé `enveloppeId` dans création, modification, historique et statistiques |
| AT10 | Notes des fractions conservées en JSON, édition, historique |
| AT11 | Total exact en cents, enveloppes actives, compte et provenance relus sous Room |
| AT12 | Provenance vérifiée par allocation; aucune réattribution silencieuse d'historique |
| AT13 | Collection `comptes_investissement` canonique |
| AT14 | Verrou d'enregistrement et IDs d'opération stables pour dépense, fractions, prêts, remboursements et paiement |
| AT15 | Erreurs des résultats et du journal propagées pour empêcher faux succès |
| AT16 | Une trace globale de compte pour les remboursements ventilés par dossiers |
| AT17 | Un message, effacement et navigation contrôlés après succès |
| AT18 | Tiers normalisés, doublons évités dans le dropdown ET le repository |
| AT19 | Source distincte, cible carte/dette active, montant/dette revalidés |
| AT20 | Brouillon gardé après échec et sélections invalidées si archives |
| AT21 | Allocations illisibles bloquent la dépense et affichent une erreur |
| AA1 | Aperçu court compte/enveloppe/montant/date avant sauvegarde |
| AA2 | Suggestions du tiers exact, non imposées, sans assimilation arbitraire d'homonymes |
| AA3 | Dossier précis ou FIFO explicite |
| AA4 | Reprise de dépense/revenu en brouillon sans écriture automatique ni réemploi d'ID |
| AA5 | Statut des nouveaux SyncJobs de l'opération, avec tâches de compte déjà compactées |
| AA6 | Avertissement de date future, sans interdiction |
| AA7 | Clavier et insets système, sans espacement figé par hauteur d'écran |

**Nouveaux commits pendant la reprise** : `3c6791a`, `ff53b70`, `91906af`, `e121240`, `b9e0ec8`, `152745f`, `a00a9d7`, `275cb7b`, `0888f7a`, `33f3959`, `d644e2f`, `bf46da7`, `8338c8f`, `313af4e`, `20225c2`, `cd92e33`, `6c675dc`, `cf951b6`, `ffce171`, `affc65e`, `648edb1`, `ad0c370`. Ces commits ne constituent pas une preuve de compilation.

**Limites connues à tester** : retour réseau, suppression/nettoyage de SyncJobs pendant l'accusé de réception, multi-appareils et reprise d'un formulaire après *process kill*. Un nouvel enregistrement intentionnel après redémarrage reçoit un nouvel ID; la détection de correspondance avec l'import bancaire appartient à BANQ-06. Aucun test Android ni compilation n'a été exécuté au cours de cette reprise.

## Section 13 et rétablissement de la compilation (27 octobre 2026, soir)

`main` ne compilait plus depuis `fd9eade` (26 oct.). Corrigé dans `8de778c` : types de retour `Unit` du dépôt
d'allocations Room, import manquant d'`ArchivesScreen`, bloc dupliqué d'`InvestissementViewModel`.
Achat par carte de crédit dans une enveloppe financée par un compte chèque de nouveau permis (une carte ne
prend jamais l'argent de l'enveloppe ; le refus « financée par un autre compte » reste entre comptes chèque).
**Compilation release + 143 tests JVM verts, deux fois.**

## Section 14 — Statistiques (27 octobre 2026) — `33f4710`

**Compilation release + 154 tests JVM verts.** 11 tests passent par le vrai `StatistiquesViewModel`
(cas de vérification : Visa + paiement, emprunt, prêt, fractions, tiers, prévu/réel, filtre, comparaison, patrimoine, CSV).

| ID | État | Notes |
|---|---|---|
| ST1, ST4–ST19 | FAIT | ST4 : « Valeur nette actuelle (pas historique) » pour un mois passé. ST10 : pseudo-enveloppe retirée. ST18 : cents exacts, tiers normalisés (casse/espaces) |
| ST2, ST3 | PARTIEL | Paiements et capital exclus des dépenses ; mais intérêts et frais de carte comptabilisés ne créent pas de transaction, donc absents des dépenses |
| ST20 | PARTIEL | Réessayer et états d'erreur présents ; zoom / petits écrans non vérifiés |
| SA1 | FAIT | Filtre par compte affiché (tous ou sélection) |
| SA2, SA5 | FAIT | (autre outil) vues Budget / Flux, moyennes 7/30 jours/période |
| SA3 | FAIT | Période précédente et an dernier, à jours égaux ; base nulle = indisponible |
| SA4 | FAIT | Catégorie → enveloppes → transactions |
| SA6 | FAIT | Valeur nette notée chaque jour (mois courant, calcul complet), courbe des seuls points réels |
| SA7 | FAIT | Prévu (assigné moins achats carte) vs réel par enveloppe |
| SA8 | FAIT | Export CSV « ; », virgule décimale, BOM UTF-8 |

## Section 15 — Modifier transaction (27 octobre 2026) — `54985ae`

**Compilation release + 159 tests JVM verts.**

| ID | État | Notes |
|---|---|---|
| MT1, MT2 | FAIT | Effets défaits en gardant l'écriture, puis réécriture sur place (même ID, même création, un seul UPDATE), le tout dans la transaction Room |
| MT3, MT9, MT13, MT14, MT16–MT20 | FAIT | Prêt à placer, prévalidation sous Room, changement de mois, fractions au cent, cartes, notes, heure d'origine, centimes, collection canonique |
| MT8 | FAIT | Paiement / virement / prêt reconnu à l'ouverture, raison affichée, bouton désactivé |
| MT11, MT12, MT15, MT21 | FAIT | Route unique ; soldes du mois choisi ; simple ↔ fractionnée ; destination archivée refusée |
| MA1, MA2, MA3 | FAIT | Résumé des changements ; note/tiers sans mouvement d'argent ; origine + liens + état de synchro |
| MT4 | PARTIEL | Le vrai mode est reconnu et expliqué, mais pas de formulaire propre à ce mode |
| MT5, MT6, MT7 | FAIT (`9a85153`) | Paiement / virement : deux côtés défaits puis refaits par le même service, mêmes écritures et même ID d'opération ; prêt : écart appliqué au compte, Prêt à placer et dossier, refus des montants impossibles. Le compte ne change pas ici |
| MT10 | PARTIEL | Plus de DELETE + CREATE ; conflits entre appareils non traités |
| MT22 | PARTIEL | Double appui, brouillon gardé, succès après validation ; pas de contrôle de révision concurrente |
| MA5 | PARTIEL | Statut de synchro affiché à l'ouverture ; pas après l'enregistrement (l'écran se ferme) |

## Sections restantes

| Sections | État |
|---|---|
| 9 | BUD-02 **À DISCUTER** — ne pas coder sans approbation. |
| 12 | IF10, IA8 bloqués par BANQ (reporté). |
| 14 | ST2, ST3, ST20 partiels. |
| 15 | MT4, MT10, MT22, MA5 partiels (voir ci-dessus). |
| 16 | **PROCHAINE SECTION** — Historique d'un compte (HC1–HC20, HA1–HA6). |
| 17 à 25, 27 | Reprise exhaustive par ID non achevée. |

## Règles de suivi

- Ne jamais annoncer **terminé** tant que tous les IDs **[RETENU]** de la section n'ont pas été confrontés aux vrais fichiers Kotlin.
- Les éléments **[À DISCUTER]** et **refusés** ne sont pas à implémenter.
- Ne jamais utiliser les PASS du bot Android comme preuve de validité financière sans actions et assertions réelles.
- Aucun `git pull` demandé entre les corrections : livrer en un lot à la fin.
- Les changements sur GitHub ne prouvent pas une compilation; les erreurs Kotlin éventuelles restent à corriger avant livraison.
