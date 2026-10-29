/// <reference path="../pb_data/types.d.ts" />

// PR3 : chaque utilisateur ne peut lire, modifier ou SUPPRIMER que ses propres fiches.
// Prudence : seule une règle actuellement OUVERTE À TOUS ("") est remplacée par la règle propriétaire.
// Une règle déjà définie (restrictive ou personnalisée) n'est jamais touchée.
migrate((app) => {
    const proprietaire = '@request.auth.id != "" && utilisateur_id = @request.auth.id';
    const creation = '@request.auth.id != "" && @request.body.utilisateur_id = @request.auth.id';
    const collections = [
        "comptes_cheques", "comptes_credits", "comptes_dettes", "comptes_investissement",
        "categories", "enveloppes", "allocations_mensuelles", "transactions", "tiers",
        "pret_personnel", "alias_bancaires", "operations_bancaires_attente", "user_preferences"
    ];
    for (const nom of collections) {
        let collection;
        try {
            collection = app.findCollectionByNameOrId(nom);
        } catch (_) {
            continue; // collection absente sur ce serveur
        }
        if (!collection.fields.getByName("utilisateur_id")) continue;
        let change = false;
        for (const regle of ["listRule", "viewRule", "updateRule", "deleteRule"]) {
            if (collection[regle] === "") { collection[regle] = proprietaire; change = true; }
        }
        if (collection.createRule === "") { collection.createRule = creation; change = true; }
        if (change) app.save(collection);
    }
}, (app) => {
    // Ne jamais rouvrir automatiquement les données à tout le monde.
});
