# **Schéma de la base de données PocketBase pour Toutie Budget**

Ce document décrit la structure des collections (tables) pour le projet Toutie Budget. Chaque collection est conçue pour être simple et efficace.

### **Collection : users (par défaut dans PocketBase)**

C'est la collection standard des utilisateurs gérée par PocketBase. Nous l'utiliserons pour l'authentification. Chaque utilisateur aura un id unique que nous utiliserons pour lier toutes ses autres données.

### **Collections de Comptes**

Comme demandé, nous créons une collection distincte pour chaque type de compte. Cela permet de bien isoler les données et d'avoir des champs spécifiques si nécessaire plus tard.

#### **Collection : comptes\_cheque**

Stocke les comptes bancaires de type "Chèque".

| Champ | Type | Notes |
| :---- | :---- | :---- |
| utilisateur\_id | Relation (users) | Lie le compte à un utilisateur. **(Non nul)** |
| nom | Text | Nom du compte. **(Non nul)** |
| solde | Number | Solde actuel du compte. **(Non nul, défaut: 0\)** |
| couleur | Text | Code couleur hexadécimal. **(Non nul)** |
| est\_archive | Bool | true si le compte est archivé. **(Défaut: false)** |
| ordre | Number | Pour le tri manuel de l'affichage. |

#### **Collection : comptes\_credit**

Stocke les cartes de crédit.

| Champ | Type | Notes |
| :---- | :---- | :---- |
| utilisateur\_id | Relation (users) | Lie le compte à un utilisateur. **(Non nul)** |
| nom | Text | Nom de la carte. **(Non nul)** |
| solde | Number | Solde actuel (généralement négatif). **(Non nul, défaut: 0\)** |
| limite\_credit | Number | Limite de la carte de crédit. |
| interet | Number | Taux d'intérêt en %. |
| couleur | Text | Code couleur hexadécimal. **(Non nul)** |
| est\_archive | Bool | true si la carte est archivée. **(Défaut: false)** |
| ordre | Number | Pour le tri manuel de l'affichage. |

#### **Collection : comptes\_dette**

Stocke les prêts et autres dettes.

| Champ | Type | Notes |
| :---- | :---- | :---- |
| utilisateur\_id | Relation (users) | Lie le compte à un utilisateur. **(Non nul)** |
| nom | Text | Nom de la dette (ex: "Prêt auto"). **(Non nul)** |
| solde | Number | Montant restant à payer (négatif). **(Non nul, défaut: 0\)** |
| montant\_initial | Number | Le montant de départ de la dette. |
| interet | Number | Taux d'intérêt en %. |
| est\_archive | Bool | true si la dette est archivée. **(Défaut: false)** |
| ordre | Number | Pour le tri manuel de l'affichage. |

#### **Collection : comptes\_investissement**

Stocke les comptes d'investissement.

| Champ | Type | Notes |
| :---- | :---- | :---- |
| utilisateur\_id | Relation (users) | Lie le compte à un utilisateur. **(Non nul)** |
| nom | Text | Nom du compte (ex: "CELI Wealthsimple"). **(Non nul)** |
| solde | Number | Valeur actuelle du portefeuille. **(Non nul, défaut: 0\)** |
| couleur | Text | Code couleur hexadécimal. **(Non nul)** |
| est\_archive | Bool | true si le compte est archivé. **(Défaut: false)** |
| ordre | Number | Pour le tri manuel de l'affichage. |

### **Collection : categories**

Définit les catégories personnalisées de l'utilisateur pour regrouper les enveloppes.

| Champ | Type | Notes |
| :---- | :---- | :---- |
| utilisateur\_id | Relation (users) | Lie la catégorie à un utilisateur. **(Non nul)** |
| nom | Text | Nom de la catégorie (ex: "Dépenses obligatoires"). **(Non nul)** |

### **Collection : enveloppes**

Définit la structure de base d'une enveloppe (son nom, son objectif, etc.).

**Note importante :** Cette collection ne contient **pas** le solde de l'enveloppe. Le solde et les dépenses sont stockés dans la collection allocations\_mensuelles. C'est ce qui permet au système de rollover de fonctionner, en ayant un état séparé pour chaque mois.

| Champ | Type | Notes |
| :---- | :---- | :---- |
| utilisateur\_id | Relation (users) | Lie l'enveloppe à un utilisateur. **(Non nul)** |
| categorie\_id | Relation (categories) | Lie l'enveloppe à une de ses catégories. **(Non nul)** |
| nom | Text | Nom de l'enveloppe. **(Non nul)** |
| est\_archive | Bool | true si l'enveloppe est archivée. **(Défaut: false)** |
| ordre | Number | Pour le tri manuel de l'affichage. |
| objectif\_type | Select | **(MODIFIÉ)** Options: Aucun, Mensuel, Bihebdomadaire, Echeance, Annuel. **(Défaut: Aucun)** |
| objectif\_montant | Number | Montant cible pour l'objectif. **(Défaut: 0\)** |
| objectif\_date | Date | Date de référence pour certains objectifs. **(Peut être nul)** |
| objectif\_jour | Number | Jour du mois ou de la semaine. **(Peut être nul)** |

### **Collection : allocations\_mensuelles**

Représente l'état d'une enveloppe pour un mois donné. C'est ici que se trouvent le solde et les dépenses.

| Champ | Type | Notes |
| :---- | :---- | :---- |
| utilisateur\_id | Relation (users) | Lie l'allocation à un utilisateur. **(Non nul)** |
| enveloppe\_id | Relation (enveloppes) | Lie à l'enveloppe concernée. **(Non nul)** |
| mois | Date | Le premier jour du mois concerné. **(Non nul)** |
| solde | Number | Le montant restant dans l'enveloppe. **(Défaut: 0\)** |
| alloue | Number | Le total alloué ce mois-ci. **(Défaut: 0\)** |
| depense | Number | Le total dépensé ce mois-ci. **(Défaut: 0\)** |
| compte\_source\_id | Text | L'ID du compte d'où vient l'argent. **Peut être nul.** |
| collection\_compte\_source | Text | Le nom de la collection du compte source. **Peut être nul.** |

### **Collection : transactions**

Stocke chaque transaction individuelle.

| Champ | Type | Notes |
| :---- | :---- | :---- |
| utilisateur\_id | Relation (users) | Lie la transaction à un utilisateur. **(Non nul)** |
| type | Select | Options: Depense, Revenu, Pret, Emprunt. **(Non nul)** |
| montant | Number | Le montant de la transaction. **(Non nul)** |
| date | Date | La date et l'heure de la transaction. **(Non nul)** |
| note | Text | Une description ou une note. |
| compte\_id | Text | L'ID du compte affecté. **(Non nul)** |
| collection\_compte | Text | La collection du compte affecté. **(Non nul)** |
| allocation\_mensuelle\_id | Relation (allocations\_mensuelles) | Si c'est une dépense, lie à l'allocation du mois. **Peut être nul.** |

