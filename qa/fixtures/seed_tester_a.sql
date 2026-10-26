PRAGMA foreign_keys=OFF;
BEGIN IMMEDIATE;

DELETE FROM historique_allocation;
DELETE FROM allocation_mensuelle;
DELETE FROM transactions;
DELETE FROM pret_personnel;
DELETE FROM tiers;
DELETE FROM enveloppes;
DELETE FROM categories;
DELETE FROM comptes_investissement;
DELETE FROM comptes_dettes;
DELETE FROM comptes_credits;
DELETE FROM comptes_cheques;
DELETE FROM sync_jobs;

INSERT INTO comptes_cheques
  (id, utilisateur_id, nom, solde, pret_a_placer, couleur, archive, ordre, collection)
VALUES
  ('ID_A', 'tester-a-local', 'Desjardins / Chèque', 1000.00, 1000.00, '#2E7D32', 0, 1, 'comptes_cheques'),
  ('ID_B', 'tester-a-local', 'Wealthsimple / Cash', 600.00, 600.00, '#1565C0', 0, 2, 'comptes_cheques'),
  ('ID_ZERO', 'tester-a-local', 'Zéro', 0.00, 0.00, '#616161', 0, 3, 'comptes_cheques');

INSERT INTO comptes_credits
  (id, utilisateur_id, nom, solde_utilise, couleur, archive, ordre, limite_credit,
   taux_interet, paiement_minimum, frais_mensuels_json, collection)
VALUES
  ('ID_VISA', 'tester-a-local', 'Visa Desjardins', 400.00, '#C62828', 0, 1,
   2000.00, 19.99, 25.00, NULL, 'comptes_credits');

INSERT INTO comptes_dettes
  (id, utilisateur_id, nom, solde_dette, archive, ordre, montant_initial,
   taux_interet, paiement_minimum, duree_mois_pret, paiement_effectue, prix_total, collection)
VALUES
  ('ID_DETTE', 'tester-a-local', 'Dette test', 1000.00, 0, 1, 1000.00,
   8.50, 50.00, 24, 0, 1100.00, 'comptes_dettes');

INSERT INTO comptes_investissement
  (id, utilisateur_id, nom, solde, couleur, archive, ordre, collection)
VALUES
  ('ID_INV', 'tester-a-local', 'Investissement test', 1500.00, '#6A1B9A', 0, 1,
   'comptes_investissement');

INSERT INTO categories (id, utilisateur_id, nom, ordre) VALUES
  ('CAT_VIE', 'tester-a-local', 'Vie courante', 1),
  ('CAT_TRANSPORT', 'tester-a-local', 'Transport', 2),
  ('CAT_EPARGNE', 'tester-a-local', 'Épargne', 3),
  ('CAT_DETTES', 'tester-a-local', 'Dettes', 4),
  ('CAT_LOISIRS', 'tester-a-local', 'Loisirs', 5);

INSERT INTO enveloppes
  (id, utilisateur_id, nom, categorie_Id, est_archive, ordre, frequence_objectif,
   montant_objectif, date_objectif, date_debut_objectif, objectif_jour, reset_apres_echeance)
VALUES
  ('ENV_EPICERIE', 'tester-a-local', 'Épicerie', 'CAT_VIE', 0, 1, 'Aucun', 0, NULL, NULL, NULL, 0),
  ('ENV_LOYER', 'tester-a-local', 'Loyer', 'CAT_VIE', 0, 2, 'Mensuel', 600, NULL, NULL, 1, 0),
  ('ENV_DEPANNEUR', 'tester-a-local', 'Dépanneur', 'CAT_VIE', 0, 3, 'Aucun', 0, NULL, NULL, NULL, 0),
  ('ENV_ESSENCE', 'tester-a-local', 'Essence', 'CAT_TRANSPORT', 0, 1, 'Aucun', 0, NULL, NULL, NULL, 0),
  ('ENV_REPARATIONS', 'tester-a-local', 'Réparations', 'CAT_TRANSPORT', 0, 2, 'Aucun', 0, NULL, NULL, NULL, 0),
  ('ENV_VACANCES', 'tester-a-local', 'Vacances', 'CAT_EPARGNE', 0, 1, 'Annuel', 1200, '2027-07-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z', NULL, 0),
  ('ENV_PROJET_2027', 'tester-a-local', 'Projet 2027', 'CAT_EPARGNE', 0, 2, 'Echeance', 2400, '2027-12-31T00:00:00.000Z', '2026-10-01T00:00:00.000Z', NULL, 0),
  ('ENV_DETTE_A', 'tester-a-local', 'Dette A', 'CAT_DETTES', 0, 1, 'Aucun', 0, NULL, NULL, NULL, 0),
  ('ENV_VIDE', 'tester-a-local', 'Enveloppe vide', 'CAT_LOISIRS', 0, 1, 'Aucun', 0, NULL, NULL, NULL, 0),
  ('ENV_DEFICIT', 'tester-a-local', 'Déficit', 'CAT_LOISIRS', 0, 2, 'Aucun', 0, NULL, NULL, NULL, 0);

INSERT INTO allocation_mensuelle
  (id, utilisateur_id, enveloppeId, mois, solde, alloue, depense, compteSourceId, collectionCompteSource)
VALUES
  ('ALLOC_EPICERIE_2026_10', 'tester-a-local', 'ENV_EPICERIE', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_LOYER_2026_10', 'tester-a-local', 'ENV_LOYER', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_DEPANNEUR_2026_10', 'tester-a-local', 'ENV_DEPANNEUR', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_ESSENCE_2026_10', 'tester-a-local', 'ENV_ESSENCE', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_REPARATIONS_2026_10', 'tester-a-local', 'ENV_REPARATIONS', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_VACANCES_2026_10', 'tester-a-local', 'ENV_VACANCES', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_PROJET_2027_2026_10', 'tester-a-local', 'ENV_PROJET_2027', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_DETTE_A_2026_10', 'tester-a-local', 'ENV_DETTE_A', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_VIDE_2026_10', 'tester-a-local', 'ENV_VIDE', '2026-10', 0.00, 0.00, 0.00, NULL, NULL),
  ('ALLOC_DEFICIT_2026_10', 'tester-a-local', 'ENV_DEFICIT', '2026-10', -20.00, 0.00, 20.00, 'ID_A', 'comptes_cheques');

INSERT INTO tiers
  (id, utilisateur_id, nom, created, updated, collectionId, collectionName)
VALUES
  ('T_IRVING', 'tester-a-local', 'Irving Port-Cartier', '2026-10-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z', 'tiers', 'tiers'),
  ('T_IGA', 'tester-a-local', 'IGA', '2026-10-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z', 'tiers', 'tiers'),
  ('T_HYDRO', 'tester-a-local', 'Hydro-Québec', '2026-10-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z', 'tiers', 'tiers'),
  ('T_EMPLOYEUR', 'tester-a-local', 'Employeur', '2026-10-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z', 'tiers', 'tiers'),
  ('T_VISA', 'tester-a-local', 'Visa', '2026-10-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z', 'tiers', 'tiers');

INSERT INTO pret_personnel
  (id, utilisateur_id, nom_tiers, montant_initial, solde, type, archive, date_creation, created, updated)
VALUES
  ('PRET_A', 'tester-a-local', 'Personne test', 1000.00, 1000.00, 'PRETE', 0,
   '2026-10-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z');

COMMIT;
PRAGMA foreign_keys=ON;
