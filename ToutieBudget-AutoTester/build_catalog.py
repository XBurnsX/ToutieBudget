import json
from pathlib import Path
cases=[]
def C(fid,feature,steps):cases.append({'id':fid,'feature':feature,'steps':steps})
def N(name):return {'action':'navigate','target':name}
def T(name):return {'action':'tap','target':name}
def V(name):return {'action':'assert','kind':'visible','target':name}
def A():return {'action':'assert','kind':'app_alive'}
def I(name,value):return {'action':'input','target':name,'value':value}
def B():return {'action':'back'}
# App navigation + per-feature entry points confirmed from Kotlin screens and navigation.
for label in ['Budget','Comptes','Ajouter','Stats']:
 C('NAV-'+label.lower(),'navigation',[N(label),A(),V(label) if label in ['Budget','Comptes'] else A()])
sections={'budget':['Rechercher une enveloppe','Catégories','Paramètres','Menu','Favoris'],
          'comptes':['Ajouter un compte','Réconcilier','Modifier','Archiver','Gérer la carte','Gérer la dette','Gérer le placement'],
          'categories':['Ajouter une catégorie','Retour'],
          'virements':['Enveloppes','Comptes','Compte source','Compte destination','Effectuer le virement'],
          'transactions':['Fractionner','Effectuer le paiement'],
          'settings':['Déconnexion','Réinitialiser les données'],
          'stats':['Dépenses','Revenus','Net','Valeur Nette','Top 5 Enveloppes','Dépenses par catégorie','Dépenses par tiers']}
nav={'budget':'Budget','comptes':'Comptes','categories':'Catégories','virements':'Virement','transactions':'Ajouter','settings':'Paramètres','stats':'Stats'}
for feature,targets in sections.items():
 for j,target in enumerate(targets):
  # Probe DOES NOT CLAIM behavioral verification. Real assertion is visibility only.
  C(f'UI-{feature.upper()}-{j+1:03}',feature,[N(nav[feature]),{'action':'probe','target':target},A()])
# Stateful scenarios; do not assume a result, require a visible confirmation or block.
C('ACC-CREATE-001','accounts',[N('Comptes'),T('Ajouter un compte'),V('Nom'),I('Nom','TEST_AUTO_CHEQUES'),I('Solde','2500'),T(['Enregistrer','Créer','Ajouter']),{'action':'assert','kind':'text_contains','value':'TEST_AUTO_CHEQUES'}, {'action':'assert','kind':'money','cents':250000}])
C('ACC-CREATE-002','accounts',[N('Comptes'),T('Ajouter un compte'),V('Nom'),I('Nom','TEST_AUTO_EPARGNE'),I('Solde','500'),T(['Enregistrer','Créer','Ajouter']),{'action':'assert','kind':'text_contains','value':'TEST_AUTO_EPARGNE'}, {'action':'assert','kind':'money','cents':50000}])
C('ACC-RESTART-001','persistence',[N('Comptes'),{'action':'assert','kind':'text_contains','value':'TEST_AUTO_CHEQUES'},{'action':'restart'},N('Comptes'),{'action':'assert','kind':'text_contains','value':'TEST_AUTO_CHEQUES'}])
C('ACC-NEGATIVE-001','accounts',[N('Comptes'),T('Ajouter un compte'),I('Nom','TEST_AUTO_NEGATIF'),I('Solde','-1'),T(['Enregistrer','Créer','Ajouter']),A()])
C('CAT-CREATE-001','categories',[N('Budget'),T('Catégories'),T('Ajouter une catégorie'),I('Nom','TEST_AUTO_MAISON'),T(['Enregistrer','Créer','Ajouter']),{'action':'assert','kind':'text_contains','value':'TEST_AUTO_MAISON'}])
C('ENV-CREATE-001','envelopes',[N('Budget'),T('Catégories'),T(['Ajouter une enveloppe','Ajouter enveloppe']),I('Nom','TEST_AUTO_EPICERIE'),T(['Enregistrer','Créer','Ajouter']),{'action':'assert','kind':'text_contains','value':'TEST_AUTO_EPICERIE'}])
C('TX-EXPENSE-001','transactions',[N('Ajouter'),I('Montant','125'),T(['Dépense','Depense']),T(['Enregistrer','Ajouter Transaction','Sauvegarder']),{'action':'assert','kind':'money','cents':12500}])
C('TX-INCOME-001','transactions',[N('Ajouter'),I('Montant','2000'),T(['Revenu','Entrée']),T(['Enregistrer','Ajouter Transaction','Sauvegarder']),{'action':'assert','kind':'money','cents':200000}])
C('TX-SPLIT-001','transactions',[N('Ajouter'),I('Montant','150'),T('Fractionner'),A()])
C('TRANSFER-CHECK-001','transfers',[N('Budget'),T(['Virement','Virer de l’argent',"Virer de l'argent"]),V('Comptes'),T('Comptes'),V('Compte source'),V('Compte destination')])
C('ARCHIVES-CHECK-001','archives',[N('Paramètres'),T(['Archives','Voir les archives']),A()])
C('DEBT-CHECK-001','debts',[N('Comptes'),T('Gérer la dette'),A()])
C('CREDIT-CHECK-001','credit_cards',[N('Comptes'),T('Gérer la carte'),A()])
C('RECONCILE-CHECK-001','reconciliation',[N('Comptes'),T('Réconcilier'),V('Solde réel'),A()])
C('STATS-RESTART-001','stats',[N('Stats'),V('Valeur Nette'),{'action':'restart'},N('Stats'),V('Valeur Nette')])
# Invalid-entry checks: do not submit automatically; verify UI remains responsive.
for feature,where,target in [('accounts','Comptes','Solde'),('transactions','Ajouter','Montant'),('envelopes','Budget',"Nom de l'enveloppe")]:
 for name,val in [('zero','0'),('negative','-1'),('large','9999999999'),('decimal','0.01'),('empty','')]:
  C(f'BOUND-{feature}-{name}',feature,[N(where),{'action':'fuzz_input','target':target,'value':val},A(),B()])
Path(__file__).with_name('catalog.json').write_text(json.dumps({'cases':cases},ensure_ascii=False,indent=2),encoding='utf8')
print('catalog cases',len(cases))
